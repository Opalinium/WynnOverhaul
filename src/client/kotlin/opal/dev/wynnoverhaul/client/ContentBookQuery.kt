package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.WynnOverhaul
import java.util.Optional
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ContentBookQuery {
    private const val CHANGE_VIEW_SLOT = 66
    private const val NEXT_PAGE_SLOT = 69
    private const val CONTAINER_SIZE = 54
    private const val LORE_HASH_FROM = 64
    private const val LORE_HASH_TO = 70
    private const val MAX_FILTERS = 14
    private const val MAX_PAGES_PER_FILTER = 30
    private const val STABLE_TICKS_REQUIRED = 3
    private const val FILTER_RETRIES = 40
    private const val CHANGE_WAIT_TICKS = 40
    private const val QUIET_MILLIS = 80L
    private const val TIMEOUT_TICKS = 120
    private const val ACTIVE_FILTER_COLOR = 0xFFFFFF

    private enum class Mode { ENUMERATE, FIND_AND_CLICK }

    private enum class Phase { NAVIGATE, REWIND, SCAN }

    private class Filters(val names: List<String>, val active: String?)

    private class Location(val filter: String, val page: Int, val slot: Int)

    private var active = false
    private var mode = Mode.ENUMERATE
    private var menu: AbstractContainerMenu? = null
    private var onComplete: ((List<ActivityInfo>) -> Unit)? = null
    private var onProgress: ((List<ActivityInfo>) -> Unit)? = null
    private var onFailed: (() -> Unit)? = null
    private var onFound: ((AbstractContainerMenu) -> Unit)? = null

    private var targetType: ActivityType? = null
    private var targetName: String? = null

    private val results = LinkedHashMap<Pair<ActivityType, String>, ActivityInfo>()
    private val seenThisRun = HashSet<Pair<ActivityType, String>>()

    private var phase = Phase.NAVIGATE
    private var queue: List<String>? = null
    private var queueReady = false
    private var filterIndex = 0
    private var navClicks = 0
    private var rewindClicks = 0
    private var pageCount = 0

    private var lastSnapshotHash = 0
    private var stableCount = 0
    private var ticksSinceAction = 0
    private var awaitingClick = false
    private var baselineHash = 0
    private var awaitingChange = false

    private var parsedItems = 0
    private var pagesSeen = 0
    private val similar = LinkedHashSet<String>()
    private var lastFilters: Filters? = null

    private val locations = HashMap<Pair<ActivityType, String>, Location>()
    private val completedFilters = LinkedHashSet<String>()
    private var bookMenu: AbstractContainerMenu? = null
    private var bookFilter: String? = null
    private var bookPage = -1
    private var targetLocation: Location? = null
    private var usedLocation = false
    private var startNanos = 0L
    private var clickNanos = 0L
    private var clicks = 0
    private var packetGeneration = 0
    private var filterRetries = 0
    private var changeSeenHash = 0
    private var changeStable = 0

    private val settler by lazy {
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "WynnOverhaul book settle").also { it.isDaemon = true } }
    }

    val isActive: Boolean get() = active
    val isEnumerating: Boolean get() = active && mode == Mode.ENUMERATE

    fun exportLocations(): Map<String, List<String>> =
        locations.entries.associate { (key, location) -> "${key.first.name}|${key.second}" to listOf(location.filter, location.page.toString(), location.slot.toString()) }

    fun importLocations(data: Map<String, List<String>>?) {
        locations.clear()
        data?.forEach { (key, value) ->
            val type = runCatching { ActivityType.valueOf(key.substringBefore('|')) }.getOrNull() ?: return@forEach
            val page = value.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val slot = value.getOrNull(2)?.toIntOrNull() ?: return@forEach
            locations[type to key.substringAfter('|')] = Location(value[0], page, slot)
        }
    }

    private fun locationOf(type: ActivityType, name: String): Location? =
        locations[type to name] ?: if (type.isQuest) locations.entries.firstOrNull { it.key.second == name && it.key.first.isQuest }?.value else null

    fun start(menu: AbstractContainerMenu, seed: List<ActivityInfo> = emptyList(), resume: Boolean = false, onProgress: (List<ActivityInfo>) -> Unit, onComplete: (List<ActivityInfo>) -> Unit, onFailed: () -> Unit) {
        reset(menu, Mode.ENUMERATE, onFailed)
        if (!resume) completedFilters.clear()
        for (info in seed) results[info.type to info.name] = info
        this.onProgress = onProgress
        this.onComplete = onComplete
    }

    fun startTrackToggle(menu: AbstractContainerMenu, type: ActivityType, name: String, onFound: (AbstractContainerMenu) -> Unit, onFailed: () -> Unit) {
        reset(menu, Mode.FIND_AND_CLICK, onFailed)
        this.targetType = type
        this.targetName = name
        this.onFound = onFound
        targetLocation = locationOf(type, name)
        usedLocation = targetLocation != null
    }

    private fun reset(menu: AbstractContainerMenu, mode: Mode, onFailed: () -> Unit) {
        if (menu !== bookMenu) {
            bookMenu = menu
            bookFilter = null
            bookPage = -1
            completedFilters.clear()
        }
        targetLocation = null
        usedLocation = false
        startNanos = System.nanoTime()
        clicks = 0
        filterRetries = 0
        this.menu = menu
        this.mode = mode
        this.onComplete = null
        this.onProgress = null
        this.onFound = null
        this.onFailed = onFailed
        results.clear()
        seenThisRun.clear()
        phase = Phase.NAVIGATE
        queue = null
        queueReady = false
        filterIndex = 0
        navClicks = 0
        rewindClicks = 0
        pageCount = 0
        stableCount = 0
        ticksSinceAction = 0
        lastSnapshotHash = 0
        awaitingClick = true
        awaitingChange = false
        baselineHash = 0
        parsedItems = 0
        pagesSeen = 0
        similar.clear()
        lastFilters = null
        active = true
    }

    fun cancel() {
        active = false
        menu = null
        onComplete = null
        onFailed = null
        onFound = null
    }

    fun onContainerPacket(containerId: Int) {
        if (!active || !awaitingChange) return
        if (menu?.containerId != containerId) return
        val generation = ++packetGeneration
        settler.schedule({ Minecraft.getInstance().execute { settled(generation) } }, QUIET_MILLIS, TimeUnit.MILLISECONDS)
    }

    private fun settled(generation: Int) {
        if (generation != packetGeneration || !active || !awaitingChange) return
        val client = Minecraft.getInstance()
        val m = menu ?: return
        val player = client.player ?: return
        if (player.containerMenu !== m) return
        val hash = snapshotHash(m)
        if (hash == baselineHash) return
        if (mode == Mode.FIND_AND_CLICK) {
            WynnOverhaul.LOGGER.info("content book click {} settled by packets in {} ms", clicks, (System.nanoTime() - clickNanos) / 1_000_000)
        }
        awaitingChange = false
        lastSnapshotHash = hash
        stableCount = STABLE_TICKS_REQUIRED
        if (!awaitingClick) return
        awaitingClick = false
        step(client, m, player)
    }

    fun tick(client: Minecraft) {
        if (!active) return
        val m = menu ?: return fail("no menu")
        val player = client.player
        if (player == null || player.containerMenu !== m) {
            fail("container changed")
            return
        }

        ticksSinceAction++
        if (ticksSinceAction > TIMEOUT_TICKS) {
            fail("timeout in phase=$phase filter=${currentFilter()} pages=$pageCount")
            return
        }

        val hash = snapshotHash(m)
        if (awaitingChange) {
            if (hash == baselineHash && ticksSinceAction < CHANGE_WAIT_TICKS) return
            if (hash != changeSeenHash) {
                changeSeenHash = hash
                changeStable = 0
                return
            }
            changeStable++
            if (changeStable < STABLE_TICKS_REQUIRED && ticksSinceAction < CHANGE_WAIT_TICKS) return
            if (mode == Mode.FIND_AND_CLICK) {
                WynnOverhaul.LOGGER.info("content book click {} settled by ticks in {} ms ({} ticks)", clicks, (System.nanoTime() - clickNanos) / 1_000_000, ticksSinceAction)
            }
            awaitingChange = false
            lastSnapshotHash = hash
            stableCount = STABLE_TICKS_REQUIRED
        }
        if (hash == lastSnapshotHash) {
            stableCount++
        } else {
            stableCount = 0
            lastSnapshotHash = hash
        }
        if (stableCount < STABLE_TICKS_REQUIRED) return
        if (!awaitingClick) return
        awaitingClick = false

        step(client, m, player)
    }

    private fun step(client: Minecraft, m: AbstractContainerMenu, player: Player) {
        val filters = readFilters(m)
        lastFilters = filters
        if (filters == null && !queueReady && filterRetries < FILTER_RETRIES) {
            filterRetries++
            awaitingClick = true
            return
        }
        if (filters == null && !queueReady) {
            WynnOverhaul.LOGGER.warn("content book: no filter item found after {} retries, control slots: {}", filterRetries, describeControls(m))
        }
        if (!queueReady) {
            queueReady = true
            queue = when {
                filters == null || filters.active == null -> null
                mode == Mode.ENUMERATE -> rotate(filters.names, filters.active).filter { name -> completedFilters.none { it.equals(name, ignoreCase = true) } }
                else -> targetType?.filterName?.let { wanted -> filters.names.firstOrNull { it.equals(wanted, ignoreCase = true) } }?.let { listOf(it) }
            }
            if (mode == Mode.FIND_AND_CLICK && queue == null && filters != null && filters.active != null) {
                fail("filter ${targetType?.filterName} not in book filters ${filters.names}")
                return
            }
            if (mode == Mode.ENUMERATE && queue?.isEmpty() == true) {
                finish()
                return
            }
        }

        if (phase == Phase.NAVIGATE) {
            val wanted = queue?.getOrNull(filterIndex)
            if (wanted != null && filters?.active?.equals(wanted, ignoreCase = true) != true) {
                if (navClicks >= MAX_FILTERS) {
                    fail("could not switch to filter $wanted, active=${filters?.active}")
                    return
                }
                navClicks++
                bookPage = -1
                bookFilter = null
                click(client, m, player, CHANGE_VIEW_SLOT)
                return
            }
            navClicks = 0
            phase = Phase.REWIND
        }

        if (phase == Phase.REWIND) {
            val known = targetLocation != null && bookMenu === m && bookPage >= 0 && bookFilter.equals(filters?.active, ignoreCase = true)
            if (known) {
                pageCount = bookPage
                phase = Phase.SCAN
            } else {
                val up = scrollUpSlot(m)
                if (up != null && rewindClicks < MAX_PAGES_PER_FILTER) {
                    rewindClicks++
                    bookPage = -1
                    click(client, m, player, up)
                    return
                }
                rewindClicks = 0
                pageCount = 0
                bookPage = 0
                bookFilter = filters?.active
                phase = Phase.SCAN
            }
        }

        val location = targetLocation
        if (mode == Mode.FIND_AND_CLICK && location != null) {
            if (pageCount < location.page && canGoNextPage(m)) {
                pageCount++
                bookPage = pageCount
                click(client, m, player, NEXT_PAGE_SLOT)
                return
            }
            if (pageCount > location.page) {
                val up = scrollUpSlot(m)
                if (up != null) {
                    pageCount--
                    bookPage = pageCount
                    click(client, m, player, up)
                    return
                }
            }
            if (pageCount == location.page && matchesTarget(m.slots.getOrNull(location.slot)?.item)) {
                clickTarget(client, m, player, location.slot)
                return
            }
            locations.remove(targetType!! to targetName!!)
            targetLocation = null
            usedLocation = false
            phase = Phase.REWIND
            rewindClicks = 0
            bookPage = -1
            step(client, m, player)
            return
        }

        pagesSeen++
        if (mode == Mode.FIND_AND_CLICK) {
            recordLocations(m)
            val targetSlot = findTargetSlot(m)
            if (targetSlot != null) {
                clickTarget(client, m, player, targetSlot)
                return
            }
        } else {
            capturePage(m)
            onProgress?.invoke(results.values.toList())
        }

        if (canGoNextPage(m) && pageCount < MAX_PAGES_PER_FILTER) {
            pageCount++
            bookPage = pageCount
            click(client, m, player, NEXT_PAGE_SLOT)
            return
        }

        val remaining = queue?.size ?: 0
        if (mode == Mode.ENUMERATE && filterIndex + 1 < remaining) {
            queue?.getOrNull(filterIndex)?.let { completedFilters.add(it) }
            filterIndex++
            phase = Phase.NAVIGATE
            step(client, m, player)
            return
        }
        if (mode == Mode.ENUMERATE) {
            finish()
        } else {
            fail("not found in filter ${currentFilter()}: target=$targetType/$targetName, parsed=$parsedItems, pages=$pagesSeen, filters=${filters?.names}, similar=$similar")
        }
    }

    private fun recordLocations(m: AbstractContainerMenu) {
        val filter = currentFilter() ?: return
        for (i in 0 until minOf(CONTAINER_SIZE, m.slots.size)) {
            val info = ActivityItemParser.parse(m.slots[i].item) ?: continue
            locations[info.type to info.name] = Location(filter, pageCount, i)
        }
    }

    private fun matchesTarget(stack: ItemStack?): Boolean {
        val type = targetType ?: return false
        val info = ActivityItemParser.parse(stack ?: return false) ?: return false
        return info.name == targetName && (info.type == type || (type.isQuest && info.type.isQuest))
    }

    private fun clickTarget(client: Minecraft, m: AbstractContainerMenu, player: Player, slot: Int) {
        client.gameMode?.handleContainerInput(m.containerId, slot, 0, ContainerInput.PICKUP, player)
        WynnOverhaul.LOGGER.info("content book tracked {} after {} clicks in {} ms (known location: {})", targetName, clicks, (System.nanoTime() - startNanos) / 1_000_000, usedLocation)
        active = false
        menu = null
        val cb = onFound
        onFound = null
        onFailed = null
        cb?.invoke(m)
    }

    private fun currentFilter(): String? = queue?.getOrNull(filterIndex) ?: lastFilters?.active

    private fun rotate(names: List<String>, first: String?): List<String> {
        val at = names.indexOfFirst { it.equals(first, ignoreCase = true) }
        if (at <= 0) return names
        return names.drop(at) + names.take(at)
    }

    private fun describeControls(m: AbstractContainerMenu): String =
        (CONTAINER_SIZE until minOf(m.slots.size, CONTAINER_SIZE + 24)).joinToString("; ") { i ->
            val stack = m.slots[i].item
            if (stack.isEmpty) "$i=empty" else "$i='${stack.hoverName.string}' lore=${stack.get(DataComponents.LORE)?.lines()?.take(4)?.joinToString("|") { it.string }}"
        }

    private fun readFilters(m: AbstractContainerMenu): Filters? {
        val stack = m.slots.getOrNull(CHANGE_VIEW_SLOT)?.item ?: return null
        if (stack.isEmpty || !stack.hoverName.string.contains("Filter", ignoreCase = true)) return null
        val lore = stack.get(DataComponents.LORE)?.lines() ?: return null
        val names = ArrayList<String>()
        var activeName: String? = null
        for (line in lore) {
            var text = ""
            var firstColor: Int? = null
            line.visit(
                FormattedText.StyledContentConsumer<Unit> { style, string ->
                    if (string.isNotEmpty()) {
                        if (text.isEmpty()) firstColor = style.color?.value
                        text += string
                    }
                    Optional.empty()
                },
                Style.EMPTY,
            )
            val cleaned = TextClean.clean(text)
            if (!cleaned.startsWith("- ")) continue
            val name = cleaned.removePrefix("- ").trim()
            if (name.isEmpty()) continue
            names.add(name)
            if (firstColor == ACTIVE_FILTER_COLOR || text.startsWith("§f")) activeName = name
        }
        return if (names.isEmpty()) null else Filters(names, activeName)
    }

    private fun findTargetSlot(m: AbstractContainerMenu): Int? {
        val type = targetType ?: return null
        val name = targetName ?: return null
        for (i in 0 until minOf(CONTAINER_SIZE, m.slots.size)) {
            val info = ActivityItemParser.parse(m.slots[i].item) ?: continue
            parsedItems++
            if (info.name.contains(name, ignoreCase = true) || name.contains(info.name, ignoreCase = true)) similar.add("${info.name}/${info.type}")
            if (info.name == name && (info.type == type || (type.isQuest && info.type.isQuest))) return i
        }
        return null
    }

    private fun click(client: Minecraft, m: AbstractContainerMenu, player: Player, slot: Int) {
        clickNanos = System.nanoTime()
        clicks++
        baselineHash = snapshotHash(m)
        changeSeenHash = baselineHash
        changeStable = 0
        awaitingChange = true
        client.gameMode?.handleContainerInput(m.containerId, slot, 0, ContainerInput.PICKUP, player)
        ticksSinceAction = 0
        stableCount = 0
        lastSnapshotHash = 0
        awaitingClick = true
    }

    private fun scrollUpSlot(m: AbstractContainerMenu): Int? {
        for (i in CONTAINER_SIZE until m.slots.size) {
            val stack = m.slots[i].item
            if (!stack.isEmpty && stack.hoverName.string.contains("Scroll Up")) return i
        }
        return null
    }

    private fun canGoNextPage(m: AbstractContainerMenu): Boolean {
        val stack = m.slots.getOrNull(NEXT_PAGE_SLOT)?.item ?: return false
        return !stack.isEmpty && stack.hoverName.string.contains("Scroll Down")
    }

    private fun capturePage(m: AbstractContainerMenu) {
        for (i in 0 until minOf(CONTAINER_SIZE, m.slots.size)) {
            val stack = m.slots[i].item
            val info = ActivityItemParser.parse(stack) ?: continue
            val key = info.type to info.name
            results[key] = info
            seenThisRun.add(key)
            currentFilter()?.let { locations[key] = Location(it, pageCount, i) }
        }
    }

    private fun snapshotHash(m: AbstractContainerMenu): Int {
        var h = 7
        val limit = minOf(CONTAINER_SIZE + 20, m.slots.size)
        for (i in 0 until limit) {
            val stack = m.slots[i].item
            h = h * 31 + stack.hoverName.string.hashCode()
            h = h * 31 + stack.count
            if (i in LORE_HASH_FROM..LORE_HASH_TO) {
                stack.get(DataComponents.LORE)?.lines()?.forEach { h = h * 31 + loreHash(it) }
            }
        }
        return h
    }

    private fun loreHash(line: Component): Int {
        var h = 17
        line.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                h = h * 31 + (style.color?.value ?: -1)
                h = h * 31 + string.hashCode()
                Optional.empty()
            },
            Style.EMPTY,
        )
        return h
    }

    private fun finish() {
        active = false
        completedFilters.clear()
        bookFilter = currentFilter()
        bookPage = pageCount
        val seenTypes = seenThisRun.mapTo(HashSet()) { it.first }
        results.keys.retainAll { it in seenThisRun || it.first !in seenTypes }
        val list = results.values.toList()
        menu = null
        val cb = onComplete
        onComplete = null
        onProgress = null
        onFailed = null
        cb?.invoke(list)
    }

    private fun fail(reason: String) {
        WynnOverhaul.LOGGER.warn("WynnOverhaul content book query failed: {} (mode={})", reason, mode)
        active = false
        menu = null
        val cb = onFailed
        onComplete = null
        onProgress = null
        onFailed = null
        onFound = null
        cb?.invoke()
    }
}

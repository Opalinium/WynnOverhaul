package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import opal.dev.wynnoverhaul.WynnOverhaul
import java.util.Optional

object ContentBookQuery {
    private const val CHANGE_VIEW_SLOT = 66
    private const val NEXT_PAGE_SLOT = 69
    private const val CONTAINER_SIZE = 54
    private const val LORE_HASH_FROM = 64
    private const val LORE_HASH_TO = 70
    private const val MAX_FILTERS = 14
    private const val MAX_PAGES_PER_FILTER = 30
    private const val STABLE_TICKS_REQUIRED = 3
    private const val CHANGE_WAIT_TICKS = 40
    private const val TIMEOUT_TICKS = 120
    private const val ACTIVE_FILTER_COLOR = 0xFFFFFF

    private enum class Mode { ENUMERATE, FIND_AND_CLICK }

    private enum class Phase { NAVIGATE, REWIND, SCAN }

    private class Filters(val names: List<String>, val active: String?)

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

    val isActive: Boolean get() = active
    val isEnumerating: Boolean get() = active && mode == Mode.ENUMERATE

    fun start(menu: AbstractContainerMenu, seed: List<ActivityInfo> = emptyList(), onProgress: (List<ActivityInfo>) -> Unit, onComplete: (List<ActivityInfo>) -> Unit, onFailed: () -> Unit) {
        reset(menu, Mode.ENUMERATE, onFailed)
        for (info in seed) results[info.type to info.name] = info
        this.onProgress = onProgress
        this.onComplete = onComplete
    }

    fun startTrackToggle(menu: AbstractContainerMenu, type: ActivityType, name: String, onFound: (AbstractContainerMenu) -> Unit, onFailed: () -> Unit) {
        reset(menu, Mode.FIND_AND_CLICK, onFailed)
        this.targetType = type
        this.targetName = name
        this.onFound = onFound
    }

    private fun reset(menu: AbstractContainerMenu, mode: Mode, onFailed: () -> Unit) {
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
            awaitingChange = false
            lastSnapshotHash = hash
            stableCount = 0
            return
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
        if (!queueReady) {
            queueReady = true
            queue = when {
                filters == null || filters.active == null -> null
                mode == Mode.ENUMERATE -> rotate(filters.names, filters.active)
                else -> targetType?.filterName?.let { wanted -> filters.names.firstOrNull { it.equals(wanted, ignoreCase = true) } }?.let { listOf(it) }
            }
            if (mode == Mode.FIND_AND_CLICK && queue == null && filters != null && filters.active != null) {
                fail("filter ${targetType?.filterName} not in book filters ${filters.names}")
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
                click(client, m, player, CHANGE_VIEW_SLOT)
                return
            }
            navClicks = 0
            phase = Phase.REWIND
        }

        if (phase == Phase.REWIND) {
            val up = scrollUpSlot(m)
            if (up != null && rewindClicks < MAX_PAGES_PER_FILTER) {
                rewindClicks++
                click(client, m, player, up)
                return
            }
            rewindClicks = 0
            pageCount = 0
            phase = Phase.SCAN
        }

        pagesSeen++
        if (mode == Mode.FIND_AND_CLICK) {
            val targetSlot = findTargetSlot(m)
            if (targetSlot != null) {
                client.gameMode?.handleContainerInput(m.containerId, targetSlot, 0, ContainerInput.PICKUP, player)
                active = false
                menu = null
                val cb = onFound
                onFound = null
                onFailed = null
                cb?.invoke(m)
                return
            }
        } else {
            capturePage(m)
            onProgress?.invoke(results.values.toList())
        }

        if (canGoNextPage(m) && pageCount < MAX_PAGES_PER_FILTER) {
            pageCount++
            click(client, m, player, NEXT_PAGE_SLOT)
            return
        }

        val remaining = queue?.size ?: 0
        if (mode == Mode.ENUMERATE && filterIndex + 1 < remaining) {
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

    private fun currentFilter(): String? = queue?.getOrNull(filterIndex) ?: lastFilters?.active

    private fun rotate(names: List<String>, first: String?): List<String> {
        val at = names.indexOfFirst { it.equals(first, ignoreCase = true) }
        if (at <= 0) return names
        return names.drop(at) + names.take(at)
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
            if (!text.startsWith("- ")) continue
            val name = text.removePrefix("- ").trim()
            if (name.isEmpty()) continue
            names.add(name)
            if (firstColor == ACTIVE_FILTER_COLOR) activeName = name
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
        baselineHash = snapshotHash(m)
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

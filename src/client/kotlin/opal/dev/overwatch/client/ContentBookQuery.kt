package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

object ContentBookQuery {

    private const val CHANGE_VIEW_SLOT = 66
    private const val NEXT_PAGE_SLOT = 69
    private const val CONTAINER_SIZE = 54
    private const val MAX_FILTERS = 14
    private const val MAX_PAGES_PER_FILTER = 20
    private const val STABLE_TICKS_REQUIRED = 2
    private const val TIMEOUT_TICKS = 100

    private enum class Mode { ENUMERATE, FIND_AND_CLICK }

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
    private val seenFilterSignatures = HashSet<String>()
    private var filterCount = 0
    private var pageCount = 0

    private var lastSnapshotHash = 0
    private var stableCount = 0
    private var ticksSinceAction = 0
    private var awaitingClick = false

    val isActive: Boolean get() = active
    val isEnumerating: Boolean get() = active && mode == Mode.ENUMERATE
    fun currentResults(): List<ActivityInfo> = results.values.toList()

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
        seenFilterSignatures.clear()
        filterCount = 0
        pageCount = 0
        stableCount = 0
        ticksSinceAction = 0
        lastSnapshotHash = 0
        awaitingClick = true
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
        val m = menu ?: return fail()
        val player = client.player
        if (player == null || player.containerMenu !== m) {
            fail()
            return
        }

        ticksSinceAction++
        if (ticksSinceAction > TIMEOUT_TICKS) {
            fail()
            return
        }

        val hash = snapshotHash(m)
        if (hash == lastSnapshotHash) {
            stableCount++
        } else {
            stableCount = 0
            lastSnapshotHash = hash
        }
        if (stableCount < STABLE_TICKS_REQUIRED) return
        if (!awaitingClick) return
        awaitingClick = false

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

        if (mode == Mode.ENUMERATE) {
            finish()
            return
        }

        val signature = filterSignature(m)
        if (signature != null && !seenFilterSignatures.add(signature)) {
            fail()
            return
        }
        if (filterCount >= MAX_FILTERS) {
            fail()
            return
        }
        filterCount++
        pageCount = 0
        click(client, m, player, CHANGE_VIEW_SLOT)
    }

    private fun findTargetSlot(m: AbstractContainerMenu): Int? {
        val type = targetType ?: return null
        val name = targetName ?: return null
        for (i in 0 until minOf(CONTAINER_SIZE, m.slots.size)) {
            val info = ActivityItemParser.parse(m.slots[i].item) ?: continue
            if (info.name == name && (info.type == type || (type.isQuest && info.type.isQuest))) return i
        }
        return null
    }

    private fun click(client: Minecraft, m: AbstractContainerMenu, player: Player, slot: Int) {
        client.gameMode?.handleContainerInput(m.containerId, slot, 0, ContainerInput.PICKUP, player)
        ticksSinceAction = 0
        stableCount = 0
        lastSnapshotHash = 0
        awaitingClick = true
    }

    private fun canGoNextPage(m: AbstractContainerMenu): Boolean {
        val stack = m.slots.getOrNull(NEXT_PAGE_SLOT)?.item ?: return false
        return !stack.isEmpty && stack.hoverName.string.contains("Scroll Down")
    }

    private fun filterSignature(m: AbstractContainerMenu): String? {
        val stack = m.slots.getOrNull(CHANGE_VIEW_SLOT)?.item ?: return null
        if (stack.isEmpty) return null
        val lore = stack.get(DataComponents.LORE)?.lines()?.joinToString("|") { it.string } ?: ""
        return stack.hoverName.string + "|" + lore
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
        for (i in 0 until minOf(CONTAINER_SIZE + 20, m.slots.size)) {
            val stack = m.slots[i].item
            h = h * 31 + stack.hoverName.string.hashCode()
            h = h * 31 + stack.count
        }
        return h
    }

    private fun finish() {
        active = false
        results.keys.retainAll(seenThisRun)
        val list = results.values.toList()
        menu = null
        val cb = onComplete
        onComplete = null
        onProgress = null
        onFailed = null
        cb?.invoke(list)
    }

    private fun fail() {
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

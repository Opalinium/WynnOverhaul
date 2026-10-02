package opal.dev.wynnoverhaul.client

import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import java.util.TreeMap

object BankSession {
    class PageData(val stacks: List<ItemStack>)

    class Pending(val page: Int, val slot: Int, val button: Int, val kind: ContainerInput, val needsItem: Boolean)

    private enum class Scan { OFF, FORWARD_A, TO_FIRST, FORWARD_B }

    var search = ""
    var navTarget: Int? = null
        private set
    var awaitingFrom = -1
        private set

    var pending: Pending? = null
        private set
    var version = 0
        private set
    var attempts = 0
        private set
    private val autoScanned = HashSet<String>()
    private val lockedAfter = HashMap<String, Int>()
    private var navSetAtNanos = 0L
    private var clickedAtNanos = 0L
    private var clickedHash = 0
    private var scan = Scan.OFF
    private var scanStart = 1
    private var scanStartedNanos = 0L
    private var scanVisited = 0
    private val pages = HashMap<String, TreeMap<Int, PageData>>()

    val scanning: Boolean get() = scan != Scan.OFF

    fun pagesOf(storage: String): TreeMap<Int, PageData> = pages.getOrPut(storage) { TreeMap() }

    fun record(storage: String, page: Int, stacks: List<ItemStack>) {
        if (scan != Scan.OFF) scanVisited++
        pagesOf(storage)[page] = PageData(stacks.map { it.copy() })
        version++
    }

    fun clearPages(storage: String) {
        pagesOf(storage).clear()
        version++
    }

    fun lockedPageAfter(storage: String): Int? = lockedAfter[storage]

    fun noteLock(storage: String, page: Int, locked: Boolean) {
        if (locked) lockedAfter[storage] = page
        else if (lockedAfter[storage] == page) lockedAfter.remove(storage)
    }

    fun shouldAutoScan(storage: String): Boolean = autoScanned.add(storage)

    fun act(pending: Pending) {
        this.pending = pending
        goTo(pending.page)
    }

    fun consumePending(): Pending? = pending.also { pending = null }

    fun goTo(page: Int) {
        scan = Scan.OFF
        setTarget(page)
    }

    fun startScan(currentPage: Int) {
        scanStart = currentPage
        scanStartedNanos = System.nanoTime()
        scanVisited = 0
        scan = Scan.FORWARD_A
        setTarget(null)
    }

    fun scanMillisPerPage(): Long =
        if (scanVisited <= 1) 0L else (System.nanoTime() - scanStartedNanos) / 1_000_000L / (scanVisited - 1)

    fun cancel() {
        pending = null
        attempts = 0
        scan = Scan.OFF
        navTarget = null
        awaitingFrom = -1
    }

    fun clear() {
        cancel()
        search = ""
        pages.clear()
        autoScanned.clear()
        lockedAfter.clear()
        version++
    }

    fun markClicked(fromPage: Int, hash: Int) {
        attempts = if (awaitingFrom == fromPage) attempts + 1 else 1
        awaitingFrom = fromPage
        clickedHash = hash
        clickedAtNanos = System.nanoTime()
        navSetAtNanos = clickedAtNanos
    }

    fun navigating(): Boolean =
        scan != Scan.OFF || navTarget != null || pending != null ||
            (awaitingFrom != -1 && System.nanoTime() - clickedAtNanos < NAVIGATING_NANOS)

    fun waiting(page: Int, hash: Int): Boolean =
        awaitingFrom == page && hash == clickedHash && System.nanoTime() - clickedAtNanos < CLICK_WAIT_NANOS

    fun arrived(page: Int) {
        if (navTarget == page) navTarget = null
        if (awaitingFrom != page) {
            awaitingFrom = -1
            attempts = 0
        }
    }

    fun advanceScan(page: Int, nextAvailable: Boolean) {
        if (navTarget != null) return
        when (scan) {
            Scan.OFF -> {}
            Scan.FORWARD_A -> when {
                nextAvailable -> setTarget(page + 1)
                scanStart > 1 -> {
                    scan = Scan.TO_FIRST
                    setTarget(1)
                }
                else -> scan = Scan.OFF
            }
            Scan.TO_FIRST -> if (page == 1) {
                scan = Scan.FORWARD_B
                advanceScan(page, nextAvailable)
            } else {
                setTarget(1)
            }
            Scan.FORWARD_B -> if (page + 1 >= scanStart || !nextAvailable) scan = Scan.OFF else setTarget(page + 1)
        }
    }

    fun expired(): Boolean = navTarget != null && System.nanoTime() - navSetAtNanos > NAV_TIMEOUT_NANOS

    private fun setTarget(page: Int?) {
        navTarget = page
        awaitingFrom = -1
        navSetAtNanos = System.nanoTime()
    }

    private const val NAVIGATING_NANOS = 2_000_000_000L
    private const val CLICK_WAIT_NANOS = 700_000_000L
    private const val NAV_TIMEOUT_NANOS = 8_000_000_000L
}

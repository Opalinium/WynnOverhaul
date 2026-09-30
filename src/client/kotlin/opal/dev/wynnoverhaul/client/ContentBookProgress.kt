package opal.dev.wynnoverhaul.client

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object ContentBookProgress {
    data class Entry(val label: String, val done: Int, val total: Int, val percent: Int)

    private val LINE = Regex("""^[-\s]*([A-Za-z][A-Za-z ]*):\s*(\d+)/(\d+)\s*\[(\d+)%]""")
    private const val CACHE_NANOS = 1_000_000_000L

    private var cached: List<Entry> = emptyList()
    private var cachedAtNanos = 0L

    fun read(player: Player): List<Entry> {
        val now = System.nanoTime()
        if (now - cachedAtNanos < CACHE_NANOS) return cached
        cachedAtNanos = now
        val stack = findBook(player)
        if (stack != null) {
            val parsed = parse(stack)
            if (parsed.isNotEmpty()) cached = parsed
        }
        return cached
    }

    fun clear() {
        cached = emptyList()
        cachedAtNanos = 0L
    }

    private fun findBook(player: Player): ItemStack? {
        if (ContentBookInterceptor.isContentBook(player.mainHandItem)) return player.mainHandItem
        if (ContentBookInterceptor.isContentBook(player.offhandItem)) return player.offhandItem
        return player.inventory.nonEquipmentItems.firstOrNull { ContentBookInterceptor.isContentBook(it) }
    }

    private fun parse(stack: ItemStack): List<Entry> =
        WynnItemRarity.loreLines(stack).mapNotNull { line ->
            val m = LINE.find(line.trim()) ?: return@mapNotNull null
            Entry(m.groupValues[1].trim(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt())
        }
}

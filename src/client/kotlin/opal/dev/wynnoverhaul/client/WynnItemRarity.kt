package opal.dev.wynnoverhaul.client

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import java.util.IdentityHashMap
import java.util.Optional

enum class WynnRarity(val colorRgb: Int, val displayName: String) {
    NORMAL(0xFFFFFF, "Normal"),
    UNIQUE(0xFFFF55, "Unique"),
    RARE(0xFF55FF, "Rare"),
    LEGENDARY(0x55FFFF, "Legendary"),
    FABLED(0xFF5555, "Fabled"),
    MYTHIC(0xAA00AA, "Mythic"),
}

object WynnItemRarity {
    private val byColor = WynnRarity.entries.associateBy { it.colorRgb }

    fun of(stack: ItemStack): WynnRarity? {
        if (stack.isEmpty) return null
        val color = firstStyledColor(stack.hoverName) ?: return null
        return byColor[color]
    }

    private class LoreEntry(val lore: ItemLore, val lines: List<String>)

    private val loreCache = IdentityHashMap<ItemStack, LoreEntry>()

    fun loreLines(stack: ItemStack): List<String> {
        if (stack.isEmpty) return emptyList()
        val lore = stack.get(DataComponents.LORE) ?: return emptyList()
        synchronized(loreCache) {
            val hit = loreCache[stack]
            if (hit != null && hit.lore === lore) return hit.lines
        }
        val lines = lore.lines().map { TextClean.clean(it.string) }
        synchronized(loreCache) {
            if (loreCache.size >= LORE_CACHE_LIMIT) loreCache.clear()
            loreCache[stack] = LoreEntry(lore, lines)
        }
        return lines
    }

    fun clearCaches() {
        synchronized(loreCache) { loreCache.clear() }
    }

    private const val LORE_CACHE_LIMIT = 512

    private fun firstStyledColor(text: FormattedText): Int? {
        var found: Int? = null
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, _ ->
                if (found == null) found = style.color?.value
                Optional.empty()
            },
            Style.EMPTY,
        )
        return found
    }
}

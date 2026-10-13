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

    private class RarityEntry(val data: Any?, val style: Any?, val custom: Any?, val itemName: Any?, val result: WynnRarity?)

    private val rarityCache = IdentityHashMap<ItemStack, RarityEntry>()

    fun of(stack: ItemStack): WynnRarity? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_MODEL_DATA)
        val style = stack.get(DataComponents.TOOLTIP_STYLE)
        val custom = stack.get(DataComponents.CUSTOM_NAME)
        val itemName = stack.get(DataComponents.ITEM_NAME)
        synchronized(rarityCache) {
            val hit = rarityCache[stack]
            if (hit != null && hit.data === data && hit.style === style && hit.custom === custom && hit.itemName === itemName) return hit.result
        }
        val result = compute(stack)
        synchronized(rarityCache) {
            if (rarityCache.size >= LORE_CACHE_LIMIT) rarityCache.clear()
            rarityCache[stack] = RarityEntry(data, style, custom, itemName, result)
        }
        return result
    }

    private fun compute(stack: ItemStack): WynnRarity? {
        tierFromModelData(stack)?.let { return it }
        tierFromTooltipStyle(stack)?.let { return it }
        return firstStyledColor(stack.hoverName)?.let { byColor[it] }
    }

    private fun tierFromTooltipStyle(stack: ItemStack): WynnRarity? {
        val path = stack.get(DataComponents.TOOLTIP_STYLE)?.toString()?.substringAfterLast(':') ?: return null
        if (path == "uncommon") return WynnRarity.UNIQUE
        return WynnRarity.entries.firstOrNull { it.name == path.uppercase() }
    }

    private fun tierFromModelData(stack: ItemStack): WynnRarity? {
        val data = stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: return null
        val tier = data.strings().firstOrNull { it.startsWith(TIER_PREFIX) } ?: return null
        return WynnRarity.entries.firstOrNull { it.name == tier.substring(TIER_PREFIX.length).uppercase() }
    }

    private const val TIER_PREFIX = "item_tier_"

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
        synchronized(rarityCache) { rarityCache.clear() }
    }

    private const val LORE_CACHE_LIMIT = 512

    private fun firstStyledColor(text: FormattedText): Int? {
        var found: Int? = null
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, segment ->
                if (found == null && segment.any { ch -> ch.isLetterOrDigit() }) found = style.color?.value
                Optional.empty()
            },
            Style.EMPTY,
        )
        return found
    }
}

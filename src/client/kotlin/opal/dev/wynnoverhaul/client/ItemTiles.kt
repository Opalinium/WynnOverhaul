package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.HOVER_BORDER
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE
import opal.dev.wynnoverhaul.client.WynnOverhaulInventoryScreen.Companion.TILE_BG_EMPTY

object ItemTiles {
    private const val GLOW_TOP = 0x6E000000
    private const val GLOW_BOTTOM = 0x1C000000
    private const val SHINE = 0x30FFFFFF
    private const val HOVER_WASH = 0x24FFFFFF
    private const val BADGE_BACK = 0xC0000000.toInt()

    fun tile(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, stack: ItemStack, hovered: Boolean, locked: Boolean = false) {
        val rarity = if (stack.isEmpty) null else WynnItemRarity.of(stack)
        val rgb = rarity?.colorRgb
        if (rgb == null) {
            graphics.fill(x, y, x + TILE, y + TILE, TILE_BG_EMPTY)
        } else {
            graphics.fillGradient(x, y, x + TILE, y + TILE, GLOW_TOP or rgb, GLOW_BOTTOM or rgb)
            graphics.fill(x + 1, y + 1, x + TILE - 1, y + 2, SHINE)
        }
        if (hovered) graphics.fill(x, y, x + TILE, y + TILE, HOVER_WASH)
        item(graphics, font, x, y, stack, rarity, locked)
        val border = if (rgb == null) OwTheme.TILE_BORDER else 0xFF000000.toInt() or rgb
        graphics.outline(x, y, TILE, TILE, border)
        if (hovered) graphics.outline(x - 1, y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    fun loose(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, stack: ItemStack, hovered: Boolean, locked: Boolean = false) {
        if (stack.isEmpty) return
        if (hovered) graphics.fill(x, y, x + TILE, y + TILE, HOVER_WASH)
        item(graphics, font, x, y, stack, WynnItemRarity.of(stack), locked)
        if (hovered) graphics.outline(x - 1, y - 1, TILE + 2, TILE + 2, HOVER_BORDER)
    }

    private fun item(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int, stack: ItemStack, rarity: WynnRarity?, locked: Boolean) {
        if (stack.isEmpty) return
        graphics.item(stack, x + 2, y + 2)
        graphics.itemDecorations(font, stack, x + 2, y + 2)
        if (rarity != null && rarity != WynnRarity.NORMAL && WynnOverhaulConfig.current.slotRarityMarkers) {
            val letter = rarity.displayName.substring(0, 1)
            graphics.fill(x + 1, y + 1, x + 2 + font.width(letter), y + 10, BADGE_BACK)
            graphics.text(font, letter, x + 2, y + 2, 0xFF000000.toInt() or rarity.colorRgb)
        }
        if (locked) padlock(graphics, x + TILE - 8, y + TILE - 9)
    }

    private fun padlock(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        graphics.fill(x - 1, y - 1, x + 7, y + 8, BADGE_BACK)
        graphics.fill(x + 1, y, x + 5, y + 1, OwTheme.ACCENT)
        graphics.fill(x + 1, y + 1, x + 2, y + 3, OwTheme.ACCENT)
        graphics.fill(x + 4, y + 1, x + 5, y + 3, OwTheme.ACCENT)
        graphics.fill(x, y + 3, x + 6, y + 7, OwTheme.ACCENT)
        graphics.fill(x + 2, y + 4, x + 4, y + 6, BADGE_BACK)
    }
}

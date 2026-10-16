package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

object ScreenSkin {
    private const val OPAQUE = 0xFF000000.toInt()
    private const val HANDLE_W = 6

    @JvmStatic
    fun active(): Boolean {
        if (!WynnOverhaulConfig.current.customMenusEnabled) return false
        val screen: Screen = Minecraft.getInstance().gui.screen() ?: return false
        if (screen is AbstractContainerScreen<*>) return false
        val name = screen.javaClass.name
        return screen is OwScreen || name.startsWith("net.minecraft.") || name.startsWith("com.mojang.realmsclient.")
    }

    private fun outline(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        graphics.fill(x, y, x + w, y + 1, color)
        graphics.fill(x, y + h - 1, x + w, y + h, color)
        graphics.fill(x, y, x + 1, y + h, color)
        graphics.fill(x + w - 1, y, x + w, y + h, color)
    }

    @JvmStatic
    fun drawButton(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, active: Boolean, hovered: Boolean) {
        val fill = if (!active) OwTheme.TILE_BG else if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG
        val border = if (!active) OwTheme.TILE_BORDER else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE
        graphics.fill(x, y, x + w, y + h, fill or OPAQUE)
        outline(graphics, x, y, w, h, border)
    }

    @JvmStatic
    fun drawSlider(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, value: Double, message: Component, active: Boolean, hovered: Boolean) {
        val font = Minecraft.getInstance().font
        graphics.fill(x, y, x + w, y + h, OwTheme.TILE_BG or OPAQUE)
        val travel = w - HANDLE_W
        val handleX = x + (travel * value.coerceIn(0.0, 1.0)).toInt()
        graphics.fill(x + 1, y + 1, handleX + HANDLE_W / 2, y + h - 1, if (active) 0xFF4A3A18.toInt() else 0xFF2A2418.toInt())
        outline(graphics, x, y, w, h, if (!active) OwTheme.TILE_BORDER else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        graphics.fill(handleX, y + 1, handleX + HANDLE_W, y + h - 1, if (!active) OwTheme.ACCENT_DIM else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.ACCENT)
        graphics.centeredText(font, truncateToWidth(font, message.string, w - 8), x + w / 2, y + (h - 8) / 2, if (active) OwTheme.TEXT else OwTheme.TEXT_FAINT)
    }

    @JvmStatic
    fun drawCheckbox(graphics: GuiGraphicsExtractor, x: Int, y: Int, message: Component, selected: Boolean, active: Boolean, hovered: Boolean) {
        val font = Minecraft.getInstance().font
        val box = Checkbox.getBoxSize(font)
        graphics.fill(x, y, x + box, y + box, OwTheme.TILE_BG or OPAQUE)
        outline(graphics, x, y, box, box, if (!active) OwTheme.TILE_BORDER else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        if (selected) graphics.fill(x + 4, y + 4, x + box - 4, y + box - 4, if (active) OwTheme.ACCENT else OwTheme.ACCENT_DIM)
        graphics.text(font, message, x + box + 4, y + (box - 8) / 2 + 1, if (active) OwTheme.TEXT else OwTheme.TEXT_FAINT)
    }

    @JvmStatic
    fun drawListBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xE0100B07.toInt())
        graphics.fill(x, y, x + w, y + 1, OwTheme.HAIRLINE)
        graphics.fill(x, y + h - 1, x + w, y + h, OwTheme.HAIRLINE)
    }

    @JvmStatic
    fun drawMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        if (Minecraft.getInstance().level != null) {
            graphics.fill(x, y, x + w, y + h, 0xD00C0906.toInt())
        } else {
            graphics.fillGradient(x, y, x + w, y + h, 0xFF14100B.toInt(), 0xFF080604.toInt())
        }
    }
}

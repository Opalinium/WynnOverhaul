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

    @JvmStatic
    fun drawButton(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, active: Boolean, hovered: Boolean) {
        val border = if (!active) OwTheme.TILE_BORDER else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE
        val pressed = active && hovered && Minecraft.getInstance().mouseHandler.isLeftPressed
        OwShape.raised(graphics, x, y, w, h, OwTheme.TILE_BG or OPAQUE, border, hovered = active && hovered, pressed = pressed, shadow = h >= 14)
    }

    @JvmStatic
    fun drawSlider(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, value: Double, message: Component, active: Boolean, hovered: Boolean) {
        val font = Minecraft.getInstance().font
        OwShape.well(graphics, x, y, w, h, 1, OwTheme.TILE_BG or OPAQUE, if (!active) OwTheme.TILE_BORDER else if (hovered) OwTheme.BORDER_BRIGHT else OwShape.WELL_EDGE)
        val travel = w - HANDLE_W
        val handleX = x + (travel * value.coerceIn(0.0, 1.0)).toInt()
        val body = if (active) 0xFF4A3A18.toInt() else 0xFF2A2418.toInt()
        graphics.fillGradient(x + 1, y + 1, handleX + HANDLE_W / 2, y + h - 1, HudStyle.lighten(body, 0.15f), HudStyle.darken(body, 0.3f))
        val knob = if (!active) OwTheme.ACCENT_DIM else if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.ACCENT
        OwShape.raised(graphics, handleX, y + 1, HANDLE_W, h - 2, HudStyle.darken(knob, 0.25f), knob, cut = 1, hovered = hovered, shadow = false)
        graphics.centeredText(font, truncateToWidth(font, message.string, w - 8), x + w / 2, y + (h - 8) / 2, if (active) OwTheme.TEXT else OwTheme.TEXT_FAINT)
    }

    @JvmStatic
    fun drawCheckbox(graphics: GuiGraphicsExtractor, x: Int, y: Int, message: Component, selected: Boolean, active: Boolean, hovered: Boolean) {
        val font = Minecraft.getInstance().font
        val box = Checkbox.getBoxSize(font)
        OwShape.checkbox(graphics, x, y, box, box, selected, hovered, active)
        graphics.text(font, message, x + box + 4, y + (box - 8) / 2 + 1, if (active) OwTheme.TEXT else OwTheme.TEXT_FAINT)
    }

    @JvmStatic
    fun drawListBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xE0100B07.toInt())
        graphics.fillGradient(x, y + 1, x + w, y + 9, 0x80000000.toInt(), 0x00000000)
        graphics.fillGradient(x, y + h - 9, x + w, y + h - 1, 0x00000000, 0x80000000.toInt())
        graphics.fill(x, y, x + w, y + 1, OwTheme.HAIRLINE)
        graphics.fill(x, y + h - 1, x + w, y + h, OwTheme.HAIRLINE)
    }

    @JvmStatic
    fun drawMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
        if (Minecraft.getInstance().level != null) {
            graphics.fillGradient(x, y, x + w, y + h, 0xD2120D08.toInt(), 0xE0070504.toInt())
        } else {
            graphics.fillGradient(x, y, x + w, y + h, 0xFF17120C.toInt(), 0xFF060403.toInt())
        }
        graphics.fillGradient(x, y, x + w, y + h / 3, 0x0FD4AF37, 0x00D4AF37)
    }
}

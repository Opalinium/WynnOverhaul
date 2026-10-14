package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OwTextField(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) : EditBox(font, x, y, width, height, Component.literal("")), OwCenteredTextField {
    var clearable = false

    init {
        setBordered(false)
        setTextColor(OwTheme.TEXT)
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(x, y, x + width, y + height, OwTheme.TILE_BG)
        graphics.outline(x, y, width, height, if (isFocused) OwTheme.ACCENT else OwTheme.HAIRLINE)
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick)
        if (clearable && value.isNotEmpty()) {
            val hovered = mouseX >= x + width - CLEAR_W && mouseX < x + width && mouseY >= y && mouseY < y + height
            graphics.centeredText(Minecraft.getInstance().font, "x", x + width - CLEAR_W / 2, y + (height - 8) / 2, if (hovered) OwTheme.TEXT else OwTheme.TEXT_DIM)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (clearable && value.isNotEmpty() && active && visible && event.x() >= x + width - CLEAR_W && event.x() < x + width && event.y() >= y && event.y() < y + height) {
            setValue("")
            return true
        }
        return super.mouseClicked(event, doubled)
    }

    private companion object {
        const val CLEAR_W = 12
    }
}

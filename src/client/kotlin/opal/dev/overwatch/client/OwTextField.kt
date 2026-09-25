package opal.dev.overwatch.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

class OwTextField(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) : EditBox(font, x, y, width, height, Component.literal("")), OwCenteredTextField {
    init {
        setBordered(false)
        setTextColor(OwTheme.TEXT)
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(x, y, x + width, y + height, OwTheme.TILE_BG)
        graphics.outline(x, y, width, height, if (isFocused) OwTheme.ACCENT else OwTheme.HAIRLINE)
        super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick)
    }
}

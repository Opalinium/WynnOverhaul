package opal.dev.overwatch.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class OwScrollPanel(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
) : AbstractScrollArea(x, y, width, height, Component.literal(""), AbstractScrollArea.defaultSettings(OwTheme.SCROLL_RATE)) {

    var totalContentHeight: Int = 0
    var onReposition: (() -> Unit)? = null

    override fun contentHeight(): Int = totalContentHeight

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        onReposition?.invoke()
        graphics.fill(x, y, x + width, y + height, OwTheme.PANEL_ALT)
        graphics.outline(x, y, width, height, OwTheme.BORDER)
        extractScrollbar(graphics, mouseX, mouseY)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

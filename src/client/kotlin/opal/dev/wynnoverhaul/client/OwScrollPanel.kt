package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractScrollArea
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
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

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean = isOverScrollbar(mouseX, mouseY)

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!isOverScrollbar(event.x(), event.y())) return false
        updateScrolling(event)
        return true
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        onReposition?.invoke()
        OwShape.well(graphics, x, y, width, height, 2)
        extractScrollbar(graphics, mouseX, mouseY)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

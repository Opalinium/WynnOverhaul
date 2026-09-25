package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class OwLabel(x: Int, y: Int, width: Int, height: Int, text: String, private val color: Int = OwTheme.TEXT) :
    AbstractWidget(x, y, width, height, Component.literal(text)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val text = truncateToWidth(font, message.string, width)
        graphics.text(font, text, x, y + (height - 8) / 2, color)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

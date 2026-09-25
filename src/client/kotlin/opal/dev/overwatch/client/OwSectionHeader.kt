package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class OwSectionHeader(x: Int, y: Int, width: Int, text: String) :
    AbstractWidget(x, y, width, 14, Component.literal(text)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val text = truncateToWidth(font, message.string.uppercase(), width)
        graphics.text(font, text, x, y + 3, OwTheme.ACCENT)
        graphics.fill(x, y + height - 1, x + width, y + height, OwTheme.HAIRLINE)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

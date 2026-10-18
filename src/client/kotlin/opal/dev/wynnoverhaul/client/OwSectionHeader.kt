package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class OwSectionHeader(x: Int, y: Int, width: Int, text: String) :
    AbstractWidget(x, y, width, 14, Component.literal(text)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        HudStyle.header(graphics, font, x, y + 2, width, truncateToWidth(font, message.string, width - 12))
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

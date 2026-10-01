package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OwListRow(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: String,
    private val badge: String,
    private val badgeColor: Int,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, width, height, Component.literal(label)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        graphics.fill(x, y, x + width, y + height, if (isHovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG)
        graphics.outline(x, y, width, height, if (isHovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, if (isHovered) OwTheme.ACCENT else OwTheme.ACCENT_DIM)
        val badgeW = font.width(badge)
        graphics.text(font, truncateToWidth(font, message.string, width - badgeW - 22), x + 9, y + (height - 8) / 2, OwTheme.TEXT)
        graphics.text(font, badge, x + width - badgeW - 7, y + (height - 8) / 2, badgeColor)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false
        onPress()
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}

class OwStatRow(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: String,
    private val value: String,
    private val valueColor: Int = OwTheme.TEXT,
    private val stripe: Boolean = false,
) : AbstractWidget(x, y, width, height, Component.literal(label)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        if (stripe) graphics.fill(x, y, x + width, y + height, STRIPE)
        val labelW = font.width(message.string)
        graphics.text(font, message.string, x + 4, y + (height - 8) / 2, OwTheme.TEXT_DIM)
        val room = (width - labelW - 16).coerceAtLeast(20)
        val shown = truncateToWidth(font, value, room)
        graphics.text(font, shown, x + width - font.width(shown) - 4, y + (height - 8) / 2, valueColor)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}

    private companion object {
        const val STRIPE = 0x16FFFFFF
    }
}

class OwLinkRow(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: String,
    private val color: Int,
    private val header: Boolean = false,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, width, height, Component.literal(label)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        val text = if (header) message.string.uppercase() else message.string
        val shown = truncateToWidth(font, text, width - 4)
        val textColor = if (isHovered) OwTheme.TEXT else color
        graphics.text(font, shown, x + 2, y + (height - 8) / 2, textColor)
        if (header) {
            graphics.fill(x, y + height - 1, x + width, y + height, OwTheme.HAIRLINE)
        } else if (isHovered) {
            graphics.fill(x + 2, y + height - 2, x + 2 + font.width(shown), y + height - 1, textColor)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false
        onPress()
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}

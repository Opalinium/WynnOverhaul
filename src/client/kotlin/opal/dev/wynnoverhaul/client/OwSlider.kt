package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component

class OwSlider(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val min: Double,
    private val max: Double,
    private val decimals: Int,
    initial: Double,
    private val label: String,
    private val onChange: (Double) -> Unit,
) : AbstractSliderButton(x, y, width, height, Component.literal(""), ((initial - min) / (max - min)).coerceIn(0.0, 1.0)), ModifiedMark {
    override var modified = false

    init {
        updateMessage()
    }

    private fun current(): Double = min + (max - min) * value

    override fun updateMessage() {
        val v = current()
        val text = if (decimals <= 0) v.toLong().toString() else "%.${decimals}f".format(v)
        message = Component.literal("$label: $text")
    }

    fun setNormalized(v: Double) {
        value = v.coerceIn(0.0, 1.0)
        updateMessage()
    }

    override fun applyValue() {
        onChange(current())
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        OwShape.well(graphics, x, y, width, height, 1, OwTheme.TILE_BG, if (isHovered) OwTheme.BORDER_BRIGHT else OwShape.WELL_EDGE)
        val fillWidth = ((width - 2) * value).toInt().coerceIn(0, width - 2)
        if (fillWidth > 0) {
            val body = if (isHovered) OwTheme.ACCENT else OwTheme.ACCENT_DIM
            graphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + height - 1, HudStyle.lighten(body, 0.12f), HudStyle.darken(body, 0.35f))
            graphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + 2, OwShape.HIGHLIGHT)
            graphics.fill(x + fillWidth, y + 1, x + 1 + fillWidth, y + height - 1, HudStyle.lighten(body, 0.5f))
        }
        val font = Minecraft.getInstance().font
        val text = truncateToWidth(font, message.string, width - 6)
        graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, OwTheme.TEXT)
        if (modified) drawModifiedMark(graphics, x, y, width, height)
    }
}

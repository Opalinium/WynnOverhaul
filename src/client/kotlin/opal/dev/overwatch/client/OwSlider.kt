package opal.dev.overwatch.client

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
) : AbstractSliderButton(x, y, width, height, Component.literal(""), ((initial - min) / (max - min)).coerceIn(0.0, 1.0)) {
    init {
        updateMessage()
    }

    private fun current(): Double = min + (max - min) * value

    override fun updateMessage() {
        val v = current()
        val text = if (decimals <= 0) v.toLong().toString() else "%.${decimals}f".format(v)
        message = Component.literal("$label: $text")
    }

    override fun applyValue() {
        onChange(current())
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(x, y, x + width, y + height, OwTheme.TILE_BG)
        val fillWidth = (width * value).toInt().coerceIn(0, width)
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + height, if (isHovered) OwTheme.ACCENT else OwTheme.ACCENT_DIM)
        }
        graphics.outline(x, y, width, height, if (isHovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE)
        val font = Minecraft.getInstance().font
        val text = truncateToWidth(font, message.string, width - 6)
        graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, OwTheme.TEXT)
    }
}

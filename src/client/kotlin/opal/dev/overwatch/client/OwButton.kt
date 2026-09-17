package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OwButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: Component,
    private val accent: Boolean = false,
    private val textColor: Int? = null,
    private val enabled: () -> Boolean = { true },
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, width, height, label) {

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val active = enabled()
        val hovered = isHovered && active
        val bg = when {
            !active -> OwTheme.PANEL
            accent && hovered -> OwTheme.ACCENT
            accent -> OwTheme.ACCENT_DIM
            hovered -> OwTheme.PANEL_RAISED
            else -> OwTheme.PANEL_ALT
        }
        graphics.fill(x, y, x + width, y + height, bg)
        graphics.outline(x, y, width, height, if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.BORDER)
        val color = if (!active) OwTheme.TEXT_FAINT else (textColor ?: OwTheme.TEXT)
        val font = Minecraft.getInstance().font
        val text = truncateToWidth(font, message.string, width - 6)
        graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, color)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        if (enabled()) onPress()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible || !enabled()) return false
        if (!isMouseOver(event.x(), event.y())) return false
        onPress()
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}

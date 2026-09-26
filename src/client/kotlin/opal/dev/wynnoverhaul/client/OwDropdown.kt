package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OwDropdown(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val prefix: String,
    private val options: List<OwDropdownOverlay.Option>,
    private val selected: () -> String,
    private val onSelect: (String) -> Unit,
) : AbstractWidget(x, y, width, height, Component.literal(prefix)) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val hovered = isHovered
        val fade = getAlpha()
        graphics.fill(x, y, x + width, y + height, HudStyle.alpha(if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG, fade))
        val border = if (hovered) OwTheme.BORDER_BRIGHT else OwTheme.HAIRLINE
        graphics.outline(x, y, width, height, HudStyle.alpha(border, fade))
        val font = Minecraft.getInstance().font
        val current = options.firstOrNull { it.id == selected() }?.label.orEmpty()
        val text = if (prefix.isEmpty()) current else "$prefix: $current"
        graphics.text(font, truncateToWidth(font, text, width - CARET_W - 10), x + 6, y + (height - 8) / 2, HudStyle.alpha(OwTheme.TEXT, fade))
        val cx = x + width - CARET_W / 2 - 4
        val cy = y + height / 2
        for (row in 0 until 3) graphics.fill(cx - 3 + row, cy - 1 + row, cx + 4 - row, cy + row, HudStyle.alpha(if (hovered) OwTheme.ACCENT else OwTheme.TEXT_DIM, fade))
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) return false
        openList()
        return true
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        openList()
    }

    private fun openList() {
        OwDropdownOverlay.open(x, y, width, height, options, selected(), { visible }, onSelect)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }

    private companion object {
        const val CARET_W = 10
    }
}

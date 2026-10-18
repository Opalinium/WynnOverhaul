package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class OwCheckbox(
    x: Int,
    y: Int,
    width: Int,
    label: Component,
    private var checked: Boolean,
    height: Int = OwTheme.ROW_H - 2,
    private val onChange: (Boolean) -> Unit,
) : AbstractWidget(x, y, width, height, label), ModifiedMark {
    override var modified = false

    companion object {
        private const val BOX = 14
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val boxY = y + (height - BOX) / 2
        OwShape.checkbox(graphics, x, boxY, BOX, BOX, checked, isHovered)
        val font = Minecraft.getInstance().font
        val textX = x + BOX + 8
        val text = truncateToWidth(font, message.string, x + width - textX)
        graphics.text(font, text, textX, y + (height - 8) / 2, OwTheme.TEXT)
        if (modified) drawModifiedMark(graphics, x, y, width, height)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        checked = !checked
        onChange(checked)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!active || !visible) return false
        if (!isMouseOver(event.x(), event.y())) return false
        checked = !checked
        onChange(checked)
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        defaultButtonNarrationText(output)
    }
}

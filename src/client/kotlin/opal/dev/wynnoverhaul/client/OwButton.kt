package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

class OwButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    label: Component,
    private val accent: Boolean = false,
    private val textColor: Int? = null,
    private val icon: ItemStack? = null,
    private val iconScale: Float = 1f,
    private val enabled: () -> Boolean = { true },
    private val swatch: Int? = null,
    private val onPress: () -> Unit,
) : AbstractWidget(x, y, width, height, label) {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val active = enabled()
        val hovered = isHovered && active
        val fill = if (hovered) OwTheme.TILE_HOVER else OwTheme.TILE_BG
        val border = when {
            !active -> OwTheme.TILE_BORDER
            hovered -> OwTheme.BORDER_BRIGHT
            accent -> OwTheme.ACCENT
            else -> OwTheme.HAIRLINE
        }
        graphics.fill(x, y, x + width, y + height, fill)
        graphics.fill(x, y, x + width, y + 1, border)
        graphics.fill(x, y + height - 1, x + width, y + height, border)
        graphics.fill(x, y, x + 1, y + height, border)
        graphics.fill(x + width - 1, y, x + width, y + height, border)
        val color = when {
            !active -> OwTheme.TEXT_FAINT
            accent -> OwTheme.ACCENT
            else -> textColor ?: OwTheme.TEXT
        }
        val font = Minecraft.getInstance().font
        if (swatch != null) {
            val inset = SWATCH_INSET
            val left = x + inset
            val top = y + inset
            val right = x + width - inset
            val bottom = y + height - inset
            graphics.fill(left - 1, top - 1, right + 1, bottom + 1, SWATCH_OUTLINE)
            graphics.fill(left, top, right, bottom, swatch or SWATCH_OPAQUE)
            return
        }
        val hasIcon = icon != null && !icon.isEmpty
        val iconW = if (hasIcon) ICON_SIZE + ICON_GAP else 0
        val text = truncateToWidth(font, message.string, width - 6 - iconW)
        if (hasIcon) {
            if (iconScale != 1f) {
                val cx = x + ICON_PAD + ICON_SIZE / 2
                val cy = y + height / 2
                graphics.pose().pushMatrix()
                graphics.pose().translate(cx.toFloat(), cy.toFloat())
                graphics.pose().scale(iconScale)
                graphics.item(icon, -ICON_SIZE / 2, -ICON_SIZE / 2)
                graphics.pose().popMatrix()
            } else {
                graphics.item(icon, x + ICON_PAD, y + (height - ICON_SIZE) / 2)
            }
            graphics.text(font, text, x + ICON_PAD + iconW, y + (height - 8) / 2, color)
        } else {
            graphics.centeredText(font, text, x + width / 2, y + (height - 8) / 2, color)
        }
    }

    private companion object {
        const val ICON_SIZE = 16
        const val SWATCH_INSET = 5
        const val SWATCH_OUTLINE = 0xFF000000.toInt()
        const val SWATCH_OPAQUE = 0xFF000000.toInt()
        const val ICON_PAD = 4
        const val ICON_GAP = 4
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

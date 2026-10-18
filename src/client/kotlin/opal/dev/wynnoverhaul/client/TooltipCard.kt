package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.util.FormattedCharSink
import opal.dev.wynnoverhaul.mixin.client.ClientTextTooltipAccessor

object TooltipCard {
    private const val PAD = 5
    private const val FIRST_LINE_H = 10
    private const val MIN_RULED_H = 14

    private var accent = OwTheme.ACCENT_DIM

    fun begin(components: List<ClientTooltipComponent>) {
        accent = nameColor(components.firstOrNull()) ?: OwTheme.ACCENT_DIM
    }

    fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!WynnOverhaulConfig.current.customTooltipsEnabled) return false
        val left = x - PAD
        val top = y - PAD
        val w = width + PAD * 2
        val h = height + PAD * 2
        val tint = 0xFF000000.toInt() or (accent and 0xFFFFFF)
        OwShape.shadow(graphics, left, top, w, h, 2, 3)
        OwShape.gradient(graphics, left, top, w, h, 2, 0xF21C150E.toInt(), 0xF20A0806.toInt())
        graphics.fillGradient(left + 1, top + 1, left + w - 1, top + 1 + minOf(h / 2, 22), HudStyle.alpha(tint, 0.22f), HudStyle.alpha(tint, 0f))
        graphics.fillGradient(left + 1, top + h - 1 - minOf(h / 4, 12), left + w - 1, top + h - 1, 0x00000000, 0x40000000)
        graphics.fill(left + 3, top + 1, left + w - 3, top + 2, OwShape.HIGHLIGHT)
        OwShape.frame(graphics, left + 1, top + 1, w - 2, h - 2, 1, HudStyle.alpha(HudStyle.darken(tint, 0.35f), 0.9f))
        OwShape.frame(graphics, left, top, w, h, 2, tint)
        HudStyle.diamond(graphics, left + 3, top + 3, 1, tint)
        HudStyle.diamond(graphics, left + w - 4, top + 3, 1, tint)
        HudStyle.diamond(graphics, left + 3, top + h - 4, 1, HudStyle.alpha(tint, 0.6f))
        HudStyle.diamond(graphics, left + w - 4, top + h - 4, 1, HudStyle.alpha(tint, 0.6f))
        if (height >= MIN_RULED_H) HudStyle.fadeRule(graphics, x, y + FIRST_LINE_H, width, HudStyle.alpha(tint, 0.65f))
        return true
    }

    private fun nameColor(component: ClientTooltipComponent?): Int? {
        val text = (component as? ClientTextTooltipAccessor)?.`wynnoverhaul$getText`() ?: return null
        var color: Int? = null
        text.accept(FormattedCharSink { _, style, codePoint ->
            if (Character.isWhitespace(codePoint)) return@FormattedCharSink true
            color = style.color?.value
            false
        })
        return color?.takeIf { it != 0xFFFFFF }
    }
}

package opal.dev.overwatch.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object HudBars {
    const val STYLE_CLASSIC = "CLASSIC"
    const val STYLE_ELDEN = "ELDEN"
    const val STYLE_MINIMAL = "MINIMAL"

    val STYLES = listOf(STYLE_CLASSIC, STYLE_ELDEN, STYLE_MINIMAL)

    fun styleLabel(style: String): String = when (style) {
        STYLE_ELDEN -> "Elden Ring"
        STYLE_MINIMAL -> "Minimal"
        else -> "Classic"
    }

    fun drawBar(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fraction: Float,
        fillArgb: Int,
        label: String,
        style: String = OverwatchConfig.current.hudBarStyle,
    ) {
        when (style) {
            STYLE_ELDEN -> drawElden(graphics, font, x, y, w, h, fraction, fillArgb, label)
            STYLE_MINIMAL -> drawMinimal(graphics, font, x, y, w, h, fraction, fillArgb, label)
            else -> drawClassic(graphics, font, x, y, w, h, fraction, fillArgb, label)
        }
    }

    private fun drawClassic(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fraction: Float,
        fillArgb: Int,
        label: String,
    ) {
        OwTheme.hudPanel(graphics, x, y, w, h)
        val trackX = x + PAD
        val trackY = y + 1
        val trackW = (w - PAD * 2).coerceAtLeast(1)
        val trackH = (h - 2).coerceAtLeast(1)
        HudStyle.bar(graphics, trackX, trackY, trackW, trackH, fraction, fillArgb)
        graphics.text(font, label, x + (w - font.width(label)) / 2, trackY + (trackH - font.lineHeight) / 2 + 1, OwTheme.TEXT, true)
    }

    private fun drawElden(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fraction: Float,
        fillArgb: Int,
        label: String,
    ) {
        HudStyle.fadeRule(graphics, x, y, w, OwTheme.ACCENT_DIM, leftSolid = true)
        HudStyle.fadeRule(graphics, x, y + h - 1, w, OwTheme.ACCENT_DIM, leftSolid = false)
        val trackY = y + 2
        val trackH = (h - 4).coerceAtLeast(1)
        HudStyle.bar(graphics, x, trackY, w, trackH, fraction, fillArgb, notches = false, framed = false)
        HudStyle.diamond(graphics, x, y + h / 2, 2, OwTheme.ACCENT)
        HudStyle.diamond(graphics, x + w - 1, y + h / 2, 2, OwTheme.ACCENT)
        graphics.text(font, label, x + (w - font.width(label)) / 2, trackY + (trackH - font.lineHeight) / 2 + 1, OwTheme.TEXT, true)
    }

    private fun drawMinimal(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fraction: Float,
        fillArgb: Int,
        label: String,
    ) {
        val trackX = x + PAD
        val trackY = y + 3
        val trackW = (w - PAD * 2).coerceAtLeast(1)
        val trackH = (h - 6).coerceAtLeast(2)
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, TRACK_BG_DIM)
        val fillW = (trackW * fraction.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) graphics.fillGradient(trackX, trackY, trackX + fillW, trackY + trackH, HudStyle.lighten(fillArgb, 0.2f), HudStyle.darken(fillArgb, 0.2f))
        graphics.text(font, label, x + (w - font.width(label)) / 2, y + (h - font.lineHeight) / 2 + 1, OwTheme.TEXT_DIM, true)
    }

    private const val PAD = 3
    private const val TRACK_BG_DIM = 0x80140E08.toInt()
}

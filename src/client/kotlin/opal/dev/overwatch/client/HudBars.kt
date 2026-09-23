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
        val trackY = y + 2
        val trackW = (w - PAD * 2).coerceAtLeast(1)
        val trackH = (h - 4).coerceAtLeast(1)
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, TRACK_BG)
        val fillW = (trackW * fraction.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, fillArgb)
        graphics.text(font, label, x + (w - font.width(label)) / 2, trackY + (trackH - font.lineHeight) / 2, OwTheme.TEXT, true)
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
        graphics.fill(x, y, x + w, y + 1, OwTheme.ACCENT_DIM)
        graphics.fill(x, y + h - 1, x + w, y + h, OwTheme.ACCENT_DIM)
        val trackY = y + 2
        val trackH = (h - 4).coerceAtLeast(1)
        graphics.fill(x, trackY, x + w, trackY + trackH, TRACK_BG_DIM)
        val fillW = (w * fraction.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) graphics.fill(x, trackY, x + fillW, trackY + trackH, fillArgb)
        graphics.text(font, label, x + (w - font.width(label)) / 2, trackY + (trackH - font.lineHeight) / 2, OwTheme.TEXT, true)
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
        val trackY = y + 2
        val trackW = (w - PAD * 2).coerceAtLeast(1)
        val trackH = (h - 4).coerceAtLeast(1)
        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, TRACK_BG_DIM)
        val fillW = (trackW * fraction.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, fillArgb)
        graphics.text(font, label, x + (w - font.width(label)) / 2, trackY + (trackH - font.lineHeight) / 2, OwTheme.TEXT_DIM, true)
    }

    private const val PAD = 3
    private const val TRACK_BG = 0xFF140E08.toInt()
    private const val TRACK_BG_DIM = 0x80140E08.toInt()
}

package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.abs
import kotlin.math.roundToInt

object HudStyle {
    const val GLASS = 0xBC110E0A.toInt()
    const val GLASS_DEEP = 0xD80B0906.toInt()
    const val EDGE = 0xFF5A4A28.toInt()
    const val EDGE_SOFT = 0x90C9A227.toInt()
    const val SHEEN = 0x16FFFFFF
    const val TRACK = 0xE60B0806.toInt()
    const val TRACK_EDGE = 0xFF2E2313.toInt()
    const val NOTCH = 0x55000000

    fun alpha(argb: Int, factor: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * factor.coerceIn(0f, 1f)).roundToInt()
        return (argb and 0xFFFFFF) or (a shl 24)
    }

    fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0xFFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    fun wrap(font: Font, text: String, maxWidth: Int): List<String> {
        val lines = ArrayList<String>()
        var current = StringBuilder()
        for (word in text.split(' ')) {
            if (word.isEmpty()) continue
            if (current.isEmpty()) {
                current.append(word)
            } else if (font.width("$current $word") <= maxWidth) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * f).roundToInt()
        val g = (ag + (bg - ag) * f).roundToInt()
        val bl = (ab + (bb - ab) * f).roundToInt()
        return (a and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
    }

    fun lighten(argb: Int, t: Float): Int = mix(argb, 0xFFFFFFFF.toInt(), t)

    fun darken(argb: Int, t: Float): Int = mix(argb, 0xFF000000.toInt(), t)

    fun plate(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, accent: Int = OwTheme.ACCENT, fade: Float = 1f) {
        if (w <= 0 || h <= 0 || fade <= 0f) return
        g.fill(x, y, x + w, y + h, alpha(GLASS, fade))
        g.fill(x, y, x + w, y + 1, alpha(EDGE, fade))
        g.fill(x, y + h - 1, x + w, y + h, alpha(EDGE, fade))
        g.fill(x, y, x + 1, y + h, alpha(EDGE, fade))
        g.fill(x + w - 1, y, x + w, y + h, alpha(EDGE, fade))
        g.fill(x + 1, y + 1, x + w - 1, y + 2, alpha(SHEEN, fade))
        brackets(g, x, y, w, h, alpha(accent, fade))
    }

    fun brackets(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int, len: Int = 4) {
        g.fill(x, y, x + len, y + 1, color)
        g.fill(x, y, x + 1, y + len, color)
        g.fill(x + w - len, y, x + w, y + 1, color)
        g.fill(x + w - 1, y, x + w, y + len, color)
        g.fill(x, y + h - 1, x + len, y + h, color)
        g.fill(x, y + h - len, x + 1, y + h, color)
        g.fill(x + w - len, y + h - 1, x + w, y + h, color)
        g.fill(x + w - 1, y + h - len, x + w, y + h, color)
    }

    fun diamond(g: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        if (r < 0 || (color ushr 24) == 0) return
        for (dy in -r..r) {
            val half = r - abs(dy)
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color)
        }
    }

    fun fadeRule(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, color: Int, leftSolid: Boolean = true) {
        if (w <= 0 || (color ushr 24) == 0) return
        val slices = (w / 3).coerceIn(1, 12)
        val sliceW = w.toFloat() / slices
        var runStart = x
        var runColor = 0
        for (i in 0..slices) {
            var c = 0
            var x1 = x + w
            if (i < slices) {
                val t = (i + 0.5f) / slices
                c = alpha(color, if (leftSolid) 1f - t * t else t * t)
                x1 = x + (i * sliceW).roundToInt()
            }
            if (i == 0) {
                runColor = c
                continue
            }
            if (c != runColor) {
                if ((runColor ushr 24) != 0 && x1 > runStart) g.fill(runStart, y, x1, y + 1, runColor)
                runStart = x1
                runColor = c
            }
        }
    }

    fun header(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, title: String, right: String = "", accent: Int = OwTheme.ACCENT, fade: Float = 1f): Int {
        diamond(g, x + 3, y + font.lineHeight / 2, 2, alpha(accent, fade))
        val label = title.uppercase()
        g.text(font, label, x + 10, y, alpha(accent, fade), true)
        val textEnd = x + 10 + font.width(label) + 4
        var ruleEnd = x + w
        if (right.isNotEmpty()) {
            val rw = font.width(right)
            g.text(font, right, x + w - rw, y, alpha(OwTheme.TEXT_DIM, fade), true)
            ruleEnd = x + w - rw - 4
        }
        if (ruleEnd - textEnd > 6) fadeRule(g, textEnd, y + font.lineHeight / 2, ruleEnd - textEnd, alpha(OwTheme.HAIRLINE, fade))
        return font.lineHeight + 3
    }

    fun keycap(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, label: String, fade: Float = 1f): Int {
        val tw = font.width(label)
        val w = tw + 8
        val h = font.lineHeight + 3
        g.fill(x, y, x + w, y + h, alpha(0xFF1B150E.toInt(), fade))
        g.fill(x, y, x + w, y + 1, alpha(OwTheme.ACCENT_DIM, fade))
        g.fill(x, y + h - 2, x + w, y + h - 1, alpha(0xFF0A0705.toInt(), fade))
        g.fill(x, y + h - 1, x + w, y + h, alpha(OwTheme.ACCENT_DIM, fade))
        g.fill(x, y, x + 1, y + h, alpha(OwTheme.ACCENT_DIM, fade))
        g.fill(x + w - 1, y, x + w, y + h, alpha(OwTheme.ACCENT_DIM, fade))
        g.text(font, label, x + 4, y + 2, alpha(OwTheme.TEXT, fade), false)
        return w
    }

    fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, fraction: Float, fillArgb: Int, notches: Boolean = true, framed: Boolean = true) {
        if (w <= 2 || h <= 2) return
        var ix = x
        var iy = y
        var iw = w
        var ih = h
        if (framed) {
            g.fill(x, y, x + w, y + h, TRACK_EDGE)
            ix = x + 1
            iy = y + 1
            iw = w - 2
            ih = h - 2
        }
        g.fill(ix, iy, ix + iw, iy + ih, TRACK)
        val fillW = (iw * fraction.coerceIn(0f, 1f)).roundToInt()
        if (fillW > 0) {
            val top = lighten(fillArgb, 0.28f)
            val bottom = darken(fillArgb, 0.28f)
            g.fillGradient(ix, iy, ix + fillW, iy + ih, top, bottom)
            g.fill(ix, iy, ix + fillW, iy + 1, alpha(lighten(fillArgb, 0.6f), 0.85f))
            if (fillW < iw) g.fill(ix + fillW - 1, iy, ix + fillW, iy + ih, alpha(lighten(fillArgb, 0.55f), 0.9f))
        }
        if (notches && iw >= 40) {
            for (i in 1..3) {
                val nx = ix + iw * i / 4
                g.fill(nx, iy, nx + 1, iy + ih, NOTCH)
            }
        }
    }
}

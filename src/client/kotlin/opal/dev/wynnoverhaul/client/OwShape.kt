package opal.dev.wynnoverhaul.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

object OwShape {
    const val OUTLINE = 0xFF050403.toInt()
    const val WELL_EDGE = 0xFF2A2012.toInt()
    const val HIGHLIGHT = 0x26FFE9B0
    const val SHADE = 0x5A000000
    const val LIP = 0x12FFE9B0
    const val PAGE_TOP = 0xF01D1710.toInt()
    const val PAGE_BOTTOM = 0xF00C0907.toInt()
    const val GLASS_TOP = 0xB81B150E.toInt()
    const val GLASS_BOTTOM = 0xCC0B0806.toInt()
    const val WARM = 0xFFFFD9A0.toInt()
    private const val SHADOW_LAYER = 0x15
    private const val ORNAMENT_LEN = 9

    fun lerp(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * f).roundToInt()
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun clampCut(cut: Int, w: Int, h: Int): Int = cut.coerceIn(0, minOf(w, h) / 2)

    fun fill(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cut: Int, color: Int) {
        if (w <= 0 || h <= 0 || (color ushr 24) == 0) return
        val c = clampCut(cut, w, h)
        if (c == 0) {
            g.fill(x, y, x + w, y + h, color)
            return
        }
        for (i in 0 until c) {
            val inset = c - i
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color)
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color)
        }
        g.fill(x, y + c, x + w, y + h - c, color)
    }

    fun gradient(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cut: Int, top: Int, bottom: Int) {
        if (w <= 0 || h <= 0) return
        val c = clampCut(cut, w, h)
        for (i in 0 until c) {
            val inset = c - i
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, lerp(top, bottom, (i + 0.5f) / h))
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, lerp(top, bottom, (h - 0.5f - i) / h))
        }
        g.fillGradient(x, y + c, x + w, y + h - c, lerp(top, bottom, c.toFloat() / h), lerp(top, bottom, (h - c).toFloat() / h))
    }

    fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cut: Int, color: Int) {
        if (w <= 0 || h <= 0 || (color ushr 24) == 0) return
        val c = clampCut(cut, w, h)
        if (c == 0) {
            g.outline(x, y, w, h, color)
            return
        }
        g.fill(x + c, y, x + w - c, y + 1, color)
        g.fill(x + c, y + h - 1, x + w - c, y + h, color)
        g.fill(x, y + c, x + 1, y + h - c, color)
        g.fill(x + w - 1, y + c, x + w, y + h - c, color)
        for (i in 1 until c) {
            g.fill(x + c - i, y + i, x + c - i + 1, y + i + 1, color)
            g.fill(x + w - c + i - 1, y + i, x + w - c + i, y + i + 1, color)
            g.fill(x + c - i, y + h - 1 - i, x + c - i + 1, y + h - i, color)
            g.fill(x + w - c + i - 1, y + h - 1 - i, x + w - c + i, y + h - i, color)
        }
    }

    fun shadow(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cut: Int, depth: Int = 3, strength: Float = 1f) {
        if (strength <= 0f) return
        val layer = ((SHADOW_LAYER * strength).toInt().coerceIn(1, 0xFF) shl 24)
        for (k in depth downTo 1) fill(g, x - k + 1, y + 2, w + 2 * (k - 1), h + k - 1, cut, layer)
    }

    fun raised(
        g: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        fill: Int,
        border: Int,
        cut: Int = 2,
        hovered: Boolean = false,
        pressed: Boolean = false,
        glow: Int = 0,
        fade: Float = 1f,
        shadow: Boolean = true,
        hover: Float = -1f,
    ) {
        if (w <= 2 || h <= 2) return
        val c = clampCut(cut, w, h)
        val lift = if (hover >= 0f) hover else if (hovered) 1f else 0f
        val edge = if (hover >= 0f) lerp(border, OwTheme.BORDER_BRIGHT, hover) else border
        if (shadow && !pressed) shadow(g, x, y, w, h, c, 2, fade)
        val top = if (pressed) HudStyle.darken(fill, 0.3f) else HudStyle.mix(fill, WARM, 0.1f + 0.1f * lift)
        val bottom = if (pressed) HudStyle.lighten(fill, 0.04f) else HudStyle.darken(fill, 0.32f)
        gradient(g, x, y, w, h, c, HudStyle.alpha(top, fade), HudStyle.alpha(bottom, fade))
        if (glow != 0) g.fillGradient(x + 1, y + h / 2, x + w - 1, y + h - 1, HudStyle.alpha(glow, 0f), HudStyle.alpha(glow, (0.16f + 0.14f * lift) * fade))
        if (pressed) {
            g.fill(x + c, y + 1, x + w - c, y + 2, HudStyle.alpha(SHADE, fade))
            g.fill(x + 1, y + c, x + 2, y + h - c, HudStyle.alpha(0x30000000, fade))
        } else {
            g.fill(x + c, y + 1, x + w - c, y + 2, HudStyle.alpha(HIGHLIGHT, fade))
            g.fill(x + c, y + h - 2, x + w - c, y + h - 1, HudStyle.alpha(SHADE, fade))
        }
        frame(g, x, y, w, h, c, HudStyle.alpha(edge, fade))
    }

    fun well(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, cut: Int = 1, fill: Int = OwTheme.TILE_BG, border: Int = WELL_EDGE) {
        if (w <= 2 || h <= 2) return
        val c = clampCut(cut, w, h)
        gradient(g, x, y, w, h, c, HudStyle.darken(fill, 0.3f), fill)
        g.fillGradient(x + 1, y + 1, x + w - 1, y + minOf(h - 1, 5), 0x70000000, 0x00000000)
        g.fill(x + 1, y + c, x + 2, y + h - c, 0x2A000000)
        g.fill(x + c, y + h - 2, x + w - c, y + h - 1, LIP)
        frame(g, x, y, w, h, c, border)
    }

    fun panel(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, glass: Boolean = false, cut: Int = 4, accent: Int = OwTheme.ACCENT_DIM, fade: Float = 1f) {
        if (w <= 8 || h <= 8 || fade <= 0f) return
        val c = clampCut(cut, w, h)
        shadow(g, x, y, w, h, c, 4, fade)
        val top = if (glass) GLASS_TOP else PAGE_TOP
        val bottom = if (glass) GLASS_BOTTOM else PAGE_BOTTOM
        gradient(g, x, y, w, h, c, HudStyle.alpha(top, fade), HudStyle.alpha(bottom, fade))
        g.fillGradient(x + 2, y + 3, x + w - 2, y + 3 + h / 4, HudStyle.alpha(0x12D4AF37, fade), 0x00D4AF37)
        g.fillGradient(x + 2, y + h - 2 - h / 6, x + w - 2, y + h - 2, 0x00000000, HudStyle.alpha(0x34000000, fade))
        frame(g, x + 1, y + 1, w - 2, h - 2, (c - 1).coerceAtLeast(0), HudStyle.alpha(OwTheme.HAIRLINE, fade))
        g.fill(x + c + 1, y + 2, x + w - c - 1, y + 3, HudStyle.alpha(HIGHLIGHT, fade))
        frame(g, x, y, w, h, c, HudStyle.alpha(OUTLINE, fade))
        ornaments(g, x, y, w, h, c, HudStyle.alpha(accent, fade))
    }

    private fun ornaments(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, c: Int, color: Int) {
        val len = ORNAMENT_LEN.coerceAtMost(w / 3).coerceAtMost(h / 3)
        g.fill(x + c, y, x + c + len, y + 1, color)
        g.fill(x + w - c - len, y, x + w - c, y + 1, color)
        g.fill(x + c, y + h - 1, x + c + len, y + h, color)
        g.fill(x + w - c - len, y + h - 1, x + w - c, y + h, color)
        g.fill(x, y + c, x + 1, y + c + len, color)
        g.fill(x + w - 1, y + c, x + w, y + c + len, color)
        g.fill(x, y + h - c - len, x + 1, y + h - c, color)
        g.fill(x + w - 1, y + h - c - len, x + w, y + h - c, color)
        for (i in 1 until c) {
            g.fill(x + c - i, y + i, x + c - i + 1, y + i + 1, color)
            g.fill(x + w - c + i - 1, y + i, x + w - c + i, y + i + 1, color)
            g.fill(x + c - i, y + h - 1 - i, x + c - i + 1, y + h - i, color)
            g.fill(x + w - c + i - 1, y + h - 1 - i, x + w - c + i, y + h - i, color)
        }
        if (c >= 2) {
            HudStyle.diamond(g, x + c + 1, y + c + 1, 1, color)
            HudStyle.diamond(g, x + w - c - 2, y + c + 1, 1, color)
            HudStyle.diamond(g, x + c + 1, y + h - c - 2, 1, color)
            HudStyle.diamond(g, x + w - c - 2, y + h - c - 2, 1, color)
        }
    }

    fun checkbox(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, checked: Boolean, hovered: Boolean, active: Boolean = true) {
        val border = when {
            !active -> OwTheme.TILE_BORDER
            hovered -> OwTheme.BORDER_BRIGHT
            checked -> OwTheme.ACCENT_DIM
            else -> WELL_EDGE
        }
        well(g, x, y, w, h, 1, OwTheme.TILE_BG, border)
        if (!checked) return
        val gem = if (active) OwTheme.ACCENT else OwTheme.ACCENT_DIM
        val reach = (minOf(w, h) - 6).coerceAtLeast(2)
        centeredDiamond(g, x, y, w, h, reach, gem)
        centeredDiamond(g, x, y - 1, w, h, (reach - 4).coerceAtLeast(1), HudStyle.lighten(gem, 0.45f))
    }

    private fun centeredDiamond(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, reach: Int, color: Int) {
        val cx2 = 2 * x + w
        val cy2 = 2 * y + h
        for (row in y until y + h) {
            val spread = reach - Math.abs(2 * row + 1 - cy2)
            if (spread < 0) continue
            g.fill(Math.floorDiv(cx2 - spread, 2), row, Math.floorDiv(cx2 + spread - 1, 2) + 1, row + 1, color)
        }
    }

    fun thumb(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, hot: Boolean = false) {
        if (w <= 0 || h <= 0) return
        val body = if (hot) OwTheme.ACCENT else OwTheme.ACCENT_DIM
        g.fillGradient(x, y, x + w, y + h, HudStyle.lighten(body, 0.2f), HudStyle.darken(body, 0.35f))
        g.fill(x, y, x + w, y + 1, HIGHLIGHT)
        g.outline(x - 1, y - 1, w + 2, h + 2, OUTLINE)
    }

    fun groove(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, fade: Float = 1f) {
        if (w <= 0) return
        g.fill(x, y, x + w, y + 1, HudStyle.alpha(OwTheme.HAIRLINE, fade))
        g.fill(x, y + 1, x + w, y + 2, HudStyle.alpha(SHADE, fade))
        g.fill(x, y + 2, x + w, y + 3, HudStyle.alpha(LIP, fade))
    }

    fun ornamentRule(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, color: Int) {
        if (w < 12) return
        val half = (w - 7) / 2
        HudStyle.fadeRule(g, x, y, half, color, leftSolid = false)
        HudStyle.fadeRule(g, x + w - half, y, half, color, leftSolid = true)
        HudStyle.diamond(g, x + w / 2, y, 2, color)
    }

    fun titleBand(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, title: String, fade: Float = 1f) {
        val h = OwTheme.TITLE_BAR_H
        g.fillGradient(x + 2, y + 3, x + w - 2, y + h - 3, HudStyle.alpha(0x22D4AF37, fade), HudStyle.alpha(0x04D4AF37, fade))
        groove(g, x + 2, y + h - 3, w - 4, fade)
        val label = title.uppercase()
        val tw = font.width(label)
        val cx = x + w / 2
        val ty = y + (h - 3 - 8) / 2
        g.text(font, label, cx - tw / 2, ty, HudStyle.alpha(OwTheme.TEXT, fade), true)
        val rule = (w / 6).coerceIn(16, 56)
        val mid = ty + 4
        val gold = HudStyle.alpha(OwTheme.ACCENT_DIM, fade)
        val leftEnd = cx - tw / 2 - 9
        val rightStart = cx + tw / 2 + 9
        HudStyle.fadeRule(g, leftEnd - rule, mid, rule, gold, leftSolid = false)
        HudStyle.fadeRule(g, rightStart, mid, rule, gold, leftSolid = true)
        HudStyle.diamond(g, leftEnd + 3, mid, 2, HudStyle.alpha(OwTheme.ACCENT, fade))
        HudStyle.diamond(g, rightStart - 4, mid, 2, HudStyle.alpha(OwTheme.ACCENT, fade))
    }
}

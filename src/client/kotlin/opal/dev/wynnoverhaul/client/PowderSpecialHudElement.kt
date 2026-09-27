package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.wynnoverhaul.WynnOverhaul
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class PowderSpecialHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Powder special HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return
        if (!WynnOverhaulConfig.current.powderSpecialHudEnabled) return
        val live = WynnPowderSpecialTracker.visible(System.currentTimeMillis()) &&
            WynnPowderSpecialTracker.element != null
        if (!live && !isHudDesignerOpen()) return
        val element = WynnPowderSpecialTracker.element ?: WynnPowderSpecialTracker.Element.EARTH
        val charge = if (live) WynnPowderSpecialTracker.charge else PREVIEW_CHARGE
        val glyph = (if (live) WynnPowderSpecialTracker.glyph?.toString() else null)
            ?: PREVIEW_GLYPH.toString()
        val style = WynnOverhaulConfig.current.powderSpecialStyle.takeIf { it in STYLES } ?: STYLE_GLYPH

        val font = Minecraft.getInstance().font
        val (contentW, contentH) = when (style) {
            STYLE_GLYPH -> CONTENT_GLYPH_W to CONTENT_GLYPH_H
            else -> CONTENT_RING to CONTENT_RING
        }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, contentH)
        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        when (style) {
            STYLE_CIRCLE -> drawRing(graphics, ox, oy, boxW, boxH, charge, element.fillArgb, full = true)
            STYLE_HALF -> drawRing(graphics, ox, oy, boxW, boxH, charge, element.fillArgb, full = false)
            STYLE_PILLAR -> drawPillar(graphics, ox, oy, boxW, boxH, charge, element.fillArgb)
            STYLE_DIAMOND -> drawDiamond(graphics, ox, oy, boxW, boxH, charge, element.fillArgb)
            else -> drawGlyph(graphics, font, ox, oy, boxW, boxH, glyph, element.fillArgb)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun drawGlyph(
        graphics: GuiGraphicsExtractor,
        font: Font,
        ox: Int,
        oy: Int,
        boxW: Int,
        boxH: Int,
        glyph: String,
        color: Int,
    ) {
        graphics.text(font, glyph, ox + (boxW - font.width(glyph)) / 2, oy + (boxH - font.lineHeight) / 2, color, true)
    }

    private fun drawRing(
        graphics: GuiGraphicsExtractor,
        ox: Int,
        oy: Int,
        boxW: Int,
        boxH: Int,
        charge: Float,
        color: Int,
        full: Boolean,
    ) {
        val r = (minOf(boxW, boxH) / 2 - 2).coerceAtLeast(4)
        val cx = ox + boxW / 2
        val cy = oy + boxH / 2
        val startDeg = if (full) -90f else 180f
        val sweepDeg = if (full) 360f else 180f
        arc(graphics, cx, cy, r, startDeg, sweepDeg, RING_TRACK)
        arc(graphics, cx, cy, r, startDeg, sweepDeg * charge.coerceIn(0f, 1f), color)
    }

    private fun drawPillar(
        graphics: GuiGraphicsExtractor,
        ox: Int,
        oy: Int,
        boxW: Int,
        boxH: Int,
        charge: Float,
        color: Int,
    ) {
        val pw = minOf(10, (boxW - 4).coerceAtLeast(4))
        val px = ox + (boxW - pw) / 2
        val py = oy + 2
        val ph = (boxH - 4).coerceAtLeast(4)
        graphics.fill(px, py, px + pw, py + ph, RING_TRACK)
        val fh = (ph * charge.coerceIn(0f, 1f)).toInt()
        if (fh > 0) graphics.fill(px, py + ph - fh, px + pw, py + ph, color)
    }

    private fun drawDiamond(
        graphics: GuiGraphicsExtractor,
        ox: Int,
        oy: Int,
        boxW: Int,
        boxH: Int,
        charge: Float,
        color: Int,
    ) {
        val r = (minOf(boxW, boxH) / 2 - 1).coerceAtLeast(3)
        val cx = ox + boxW / 2
        val cy = oy + boxH / 2
        val edge = ArrayList<Pair<Int, Int>>(r * 8)
        val steps = (r * 8).coerceAtLeast(16)
        for (i in 0 until steps) {
            val t = 2.0 * Math.PI * i / steps
            val dx = sin(t)
            val dy = -cos(t)
            val s = r / (abs(dx) + abs(dy))
            val px = (cx + s * dx).roundToInt()
            val py = (cy + s * dy).roundToInt()
            if (edge.isEmpty() || edge.last() != (px to py)) edge.add(px to py)
        }
        for ((px, py) in edge) graphics.fill(px, py, px + 2, py + 2, RING_TRACK)
        val n = (edge.size * charge.coerceIn(0f, 1f)).toInt()
        for (i in 0 until n) {
            val (px, py) = edge[i]
            graphics.fill(px, py, px + 2, py + 2, color)
        }
    }

    private fun arc(
        graphics: GuiGraphicsExtractor,
        cx: Int,
        cy: Int,
        r: Int,
        startDeg: Float,
        sweepDeg: Float,
        color: Int,
    ) {
        if (r < 2 || sweepDeg <= 0f) return
        var a = startDeg
        val end = startDeg + sweepDeg
        val step = 360f / (r * 8).coerceAtLeast(16)
        while (a < end) {
            val rad = Math.toRadians(a.toDouble())
            val px = (cx + cos(rad) * r).toInt()
            val py = (cy + sin(rad) * r).toInt()
            graphics.fill(px, py, px + 2, py + 2, color)
            a += step
        }
    }

    companion object {
        const val ID = "powder_special"
        const val STYLE_GLYPH = "GLYPH"
        const val STYLE_CIRCLE = "CIRCLE"
        const val STYLE_HALF = "HALF"
        const val STYLE_PILLAR = "PILLAR"
        const val STYLE_DIAMOND = "DIAMOND"

        val STYLES = listOf(STYLE_GLYPH, STYLE_CIRCLE, STYLE_HALF, STYLE_PILLAR, STYLE_DIAMOND)

        fun styleLabel(style: String): String = when (style) {
            STYLE_CIRCLE -> "Circle"
            STYLE_HALF -> "Half circle"
            STYLE_PILLAR -> "Mini pillar"
            STYLE_DIAMOND -> "Elden diamond"
            else -> "Server glyph"
        }

        private const val CONTENT_GLYPH_W = 18
        private const val CONTENT_GLYPH_H = 18
        private const val CONTENT_RING = 22
        private const val RING_TRACK = 0x804A3117.toInt()
        private const val PREVIEW_CHARGE = 0.7f

        private val PREVIEW_GLYPH = 0xE035.toChar()
    }
}

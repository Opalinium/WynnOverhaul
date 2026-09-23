package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch
import org.joml.Matrix3x2fStack
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class TrackerEspHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            if (!OverwatchGate.inGame) return
            val config = OverwatchConfig.current
            val waypoints = TrackerEsp.waypoints
            if (waypoints.isEmpty()) return

            val font = Minecraft.getInstance().font
            val halfW = graphics.guiWidth() * 0.5f
            val halfH = graphics.guiHeight() * 0.5f
            val scale = config.trackerWaypointScale.toFloat().coerceIn(0.5f, 2.5f)
            val pose = graphics.pose()

            for (wp in waypoints.asReversed()) {
                val rawX = wp.ndcX * halfW
                val rawY = -wp.ndcY * halfH
                val distText = "${wp.distance.roundToInt()}m"

                if (wp.onScreen) {
                    val px = halfW + rawX
                    val py = halfH + rawY
                    val lookedAt = hypot(rawX, rawY) <= halfH * LOOK_RADIUS
                    drawMarker(graphics, pose, font, px, py, wp.label, distText, wp.argb, scale, lookedAt)
                } else {
                    val edgeHalfW = halfW - EDGE_MARGIN
                    val edgeHalfH = halfH - EDGE_MARGIN
                    val dx: Float
                    val dy: Float
                    if (rawX == 0f && rawY == 0f) {
                        dx = 0f
                        dy = -edgeHalfH
                    } else {
                        val sx = if (rawX != 0f) edgeHalfW / abs(rawX) else Float.MAX_VALUE
                        val sy = if (rawY != 0f) edgeHalfH / abs(rawY) else Float.MAX_VALUE
                        val s = minOf(sx, sy)
                        dx = rawX * s
                        dy = rawY * s
                    }
                    drawArrow(graphics, pose, font, halfW + dx, halfH + dy, atan2(dy, dx), distText, wp.argb, scale)
                }
            }
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Entity tracker ESP HUD failed", t)
            }
        }
    }

    private fun drawMarker(
        g: GuiGraphicsExtractor,
        pose: Matrix3x2fStack,
        font: Font,
        px: Float,
        py: Float,
        label: String,
        dist: String,
        argb: Int,
        scale: Float,
        lookedAt: Boolean,
    ) {
        pose.pushMatrix()
        pose.translate(px, py)
        if (scale != 1f) pose.scale(scale)

        val rgb = argb and 0x00FFFFFF
        g.fill(ICON_LEFT, ICON_TOP, ICON_RIGHT, 0, ICON_BORDER_ALPHA or shade(rgb, ICON_BORDER_SHADE))
        g.fill(ICON_LEFT + 1, ICON_TOP + 1, ICON_RIGHT - 1, -1, ICON_FILL_ALPHA or rgb)
        val initial = initialOf(label)
        g.text(font, initial, -font.width(initial) / 2, ICON_TOP + 1, TEXT_COLOR)

        if (lookedAt) {
            var y = LABEL_GAP
            y = drawLabel(g, font, label, y)
            drawLabel(g, font, dist, y)
        }

        pose.popMatrix()
    }

    private fun drawLabel(g: GuiGraphicsExtractor, font: Font, text: String, y: Int): Int {
        val tw = font.width(text)
        val left = -tw / 2
        g.fill(left - LABEL_PAD_X, y, left + tw + LABEL_PAD_X, y + LABEL_H, LABEL_BG)
        g.text(font, text, left, y + 1, TEXT_COLOR)
        return y + LABEL_H + LABEL_GAP
    }

    private fun initialOf(label: String): String {
        val c = label.firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return c.uppercaseChar().toString()
    }

    private fun shade(rgb: Int, factor: Float): Int {
        val r = (((rgb shr 16) and 0xFF) * factor).toInt()
        val gr = (((rgb shr 8) and 0xFF) * factor).toInt()
        val b = ((rgb and 0xFF) * factor).toInt()
        return (r shl 16) or (gr shl 8) or b
    }

    private fun drawArrow(
        g: GuiGraphicsExtractor,
        pose: Matrix3x2fStack,
        font: Font,
        px: Float,
        py: Float,
        angle: Float,
        dist: String,
        argb: Int,
        scale: Float,
    ) {
        pose.pushMatrix()
        pose.translate(px, py)
        if (scale != 1f) pose.scale(scale)

        pose.pushMatrix()
        pose.rotate(angle)
        triangle(g, argb)
        pose.popMatrix()

        val tw = font.width(dist)
        g.text(font, dist, -tw / 2, ARROW_R + 2, TEXT_COLOR)

        pose.popMatrix()
    }

    private fun triangle(g: GuiGraphicsExtractor, argb: Int) {
        for (i in 0..ARROW_R) {
            val half = (ARROW_R - i) / 2
            g.fill(i, -half, i + 1, half + 1, argb)
        }
    }

    private companion object {
        const val ICON_LEFT = -5
        const val ICON_RIGHT = 4
        const val ICON_TOP = -9
        const val ICON_FILL_ALPHA = 0xFF000000.toInt()
        const val ICON_BORDER_ALPHA = 0xFF000000.toInt()
        const val ICON_BORDER_SHADE = 0.52f
        const val LABEL_H = 9
        const val LABEL_GAP = 2
        const val LABEL_PAD_X = 3
        const val LABEL_BG = 0x5A000000
        const val LOOK_RADIUS = 0.3f
        const val ARROW_R = 7
        const val EDGE_MARGIN = 14f
        const val TEXT_COLOR = -1
    }
}

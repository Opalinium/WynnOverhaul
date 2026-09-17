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
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TrackerEspHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
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
                    if (wp.isChest) drawPath(graphics, pose, halfW, halfH * 2f, px, py, wp.argb)
                    drawTag(graphics, pose, font, px, py, wp.label, distText, wp.argb, scale)
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

    private fun drawTag(
        g: GuiGraphicsExtractor,
        pose: Matrix3x2fStack,
        font: Font,
        px: Float,
        py: Float,
        label: String,
        dist: String,
        argb: Int,
        scale: Float,
    ) {
        pose.pushMatrix()
        pose.translate(px, py)
        if (scale != 1f) pose.scale(scale)

        diamond(g, DIAMOND_R, argb)

        val text = "$label  $dist"
        val tw = font.width(text)
        val tx = -tw / 2
        val ty = -DIAMOND_R - TEXT_GAP - LINE_H
        g.fill(tx - PAD, ty - PAD, tx + tw + PAD, ty + LINE_H, BG_COLOR)
        g.text(font, text, tx, ty, TEXT_COLOR)

        pose.popMatrix()
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

    private fun drawPath(g: GuiGraphicsExtractor, pose: Matrix3x2fStack, screenCenterX: Float, screenBottom: Float, px: Float, py: Float, argb: Int) {
        val dx = px - screenCenterX
        val dy = py - screenBottom
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        val color = (argb and 0x00FFFFFF) or PATH_ALPHA
        pose.pushMatrix()
        pose.translate(screenCenterX, screenBottom)
        pose.rotate(atan2(dy, dx))
        var t = 0f
        while (t < len) {
            val end = (t + PATH_DASH_LEN).coerceAtMost(len)
            g.fill(t.roundToInt(), 0, end.roundToInt(), 1, color)
            t += PATH_DASH_LEN + PATH_GAP_LEN
        }
        pose.popMatrix()
    }

    private fun diamond(g: GuiGraphicsExtractor, r: Int, argb: Int) {
        for (i in -r..r) {
            val half = r - abs(i)
            g.fill(-half, i, half + 1, i + 1, argb)
        }
    }

    private fun triangle(g: GuiGraphicsExtractor, argb: Int) {
        for (i in 0..ARROW_R) {
            val half = (ARROW_R - i) / 2
            g.fill(i, -half, i + 1, half + 1, argb)
        }
    }

    private companion object {
        const val DIAMOND_R = 4
        const val ARROW_R = 7
        const val TEXT_GAP = 2
        const val LINE_H = 9
        const val PAD = 2
        const val EDGE_MARGIN = 14f
        const val PATH_DASH_LEN = 6f
        const val PATH_GAP_LEN = 5f
        const val PATH_ALPHA = 0x70000000
        const val BG_COLOR = 0xB0000000.toInt()
        const val TEXT_COLOR = 0xFFEEEEEE.toInt()
    }
}

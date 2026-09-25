package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
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
                    drawMarker(graphics, pose, font, px, py, wp.label, distText, wp.argb, wp.icon, scale, lookedAt)
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
        icon: ItemStack,
        scale: Float,
        lookedAt: Boolean,
    ) {
        pose.pushMatrix()
        pose.translate(px, py)
        if (scale != 1f) pose.scale(scale)

        val rgb = argb and 0x00FFFFFF
        val ring = ICON_ALPHA or rgb
        if (icon.isEmpty) {
            drawDiamond(g, ring)
        } else {
            plate(g, -BADGE_HALF, BADGE_TOP, BADGE_HALF, BADGE_BOTTOM, ring)
            plate(g, -BADGE_HALF + 1, BADGE_TOP + 1, BADGE_HALF - 1, BADGE_BOTTOM - 1, BADGE_BG)
            pose.pushMatrix()
            pose.translate(-ITEM_SIZE / 2f, BADGE_TOP + 3f)
            pose.scale(ITEM_SIZE / 16f)
            g.item(icon, 0, 0)
            pose.popMatrix()
            for (i in 0 until POINTER_H) {
                val half = POINTER_H - 1 - i
                g.fill(-half, BADGE_BOTTOM + i, half + 1, BADGE_BOTTOM + i + 1, ring)
            }
        }

        if (lookedAt) drawCard(g, font, label, dist, ring)

        pose.popMatrix()
    }

    private fun plate(g: GuiGraphicsExtractor, l: Int, t: Int, r: Int, b: Int, color: Int) {
        g.fill(l + 1, t, r - 1, b, color)
        g.fill(l, t + 1, r, b - 1, color)
    }

    private fun drawDiamond(g: GuiGraphicsExtractor, color: Int) {
        for (i in -DIAMOND_R..DIAMOND_R) {
            val half = DIAMOND_R - abs(i)
            g.fill(-half, -DIAMOND_R - 2 + i, half + 1, -DIAMOND_R - 1 + i, color)
        }
    }

    private fun drawCard(g: GuiGraphicsExtractor, font: Font, name: String, dist: String, accent: Int) {
        val lines = if (name.isBlank()) 1 else 2
        val tw = maxOf(font.width(name), font.width(dist))
        val left = -tw / 2 - CARD_PAD
        val right = -tw / 2 + tw + CARD_PAD
        val top = CARD_GAP
        val bottom = top + lines * CARD_LINE + 2
        g.fill(left, top, right, bottom, CARD_BG)
        g.fill(left, top, left + 1, bottom, accent)
        var y = top + 2
        if (name.isNotBlank()) {
            g.text(font, name, -font.width(name) / 2, y, TEXT_COLOR)
            y += CARD_LINE
        }
        g.text(font, dist, -font.width(dist) / 2, y, CARD_DIM)
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
        const val ICON_ALPHA = 0xFF000000.toInt()
        const val BADGE_HALF = 10
        const val BADGE_TOP = -30
        const val BADGE_BOTTOM = -10
        const val BADGE_BG = 0xE0121016.toInt()
        const val ITEM_SIZE = 12
        const val POINTER_H = 5
        const val DIAMOND_R = 4
        const val CARD_GAP = 3
        const val CARD_PAD = 4
        const val CARD_LINE = 10
        const val CARD_BG = 0xC0121016.toInt()
        const val CARD_DIM = 0xFFB0B4BC.toInt()
        const val LOOK_RADIUS = 0.3f
        const val ARROW_R = 7
        const val EDGE_MARGIN = 14f
        const val TEXT_COLOR = -1
    }
}

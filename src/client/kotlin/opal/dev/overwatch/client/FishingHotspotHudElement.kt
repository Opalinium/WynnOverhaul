package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class FishingHotspotHudElement : HudElement {

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val config = OverwatchConfig.current
        if (!config.fishingHotspotHudEnabled || !FishingHudState.active) return

        val centerX = graphics.guiWidth() / 2
        val centerY = graphics.guiHeight() / 2

        val hotspotType = FishingHudState.hotspotType
        if (hotspotType != null) {
            drawRing(graphics, centerX, centerY, RING_RADIUS, RING_COLOR)
            drawSmallCenteredText(graphics, centerX, centerY + TEXT_OFFSET_Y, "Hotspot", TEXT_COLOR)
            if (!hotspotType.equals("Hotspot", ignoreCase = true)) {
                val color = colorForHotspotType(hotspotType)
                drawSmallCenteredText(graphics, centerX, centerY + TEXT_OFFSET_Y + LINE_HEIGHT, hotspotType, color)
            }
        } else {
            drawX(graphics, centerX + X_OFFSET, centerY + X_OFFSET, X_SIZE, X_COLOR)
        }
    }

    private fun drawX(graphics: GuiGraphicsExtractor, x: Int, y: Int, size: Int, color: Int) {
        for (i in 0 until size) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, color)
            graphics.fill(x + (size - 1 - i), y + i, x + (size - i), y + i + 1, color)
        }
    }

    private fun drawRing(graphics: GuiGraphicsExtractor, centerX: Int, centerY: Int, radius: Int, color: Int) {
        for (i in 0 until RING_SEGMENTS) {
            val angle = (2.0 * Math.PI * i) / RING_SEGMENTS
            val px = centerX + (radius * cos(angle)).roundToInt()
            val py = centerY + (radius * sin(angle)).roundToInt()
            graphics.fill(px - 1, py - 1, px + 1, py + 1, color)
        }
    }

    private fun colorForHotspotType(type: String): Int = when (type.lowercase()) {
        "trophy fish chance" -> COLOR_GOLD
        "treasure chance" -> COLOR_GOLD
        "fishing speed" -> COLOR_CYAN
        "sea creature chance" -> COLOR_DARK_BLUE
        "double hook chance" -> COLOR_DARK_BLUE
        else -> TEXT_COLOR
    }

    private fun drawSmallCenteredText(graphics: GuiGraphicsExtractor, x: Int, y: Int, text: String, color: Int) {
        val font = Minecraft.getInstance().font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(TEXT_SCALE)
        graphics.centeredText(font, text, 0, 0, color)
        pose.popMatrix()
    }

    private companion object {
        const val X_OFFSET = 10
        const val X_SIZE = 7
        const val X_COLOR = 0xB0FF5555.toInt()

        const val RING_RADIUS = 11
        const val RING_SEGMENTS = 32
        const val RING_COLOR = 0xA0AA55FF.toInt()

        const val TEXT_OFFSET_Y = 16
        const val LINE_HEIGHT = 7
        const val TEXT_SCALE = 0.6f
        const val TEXT_COLOR = 0xFFCC88FF.toInt()
        const val COLOR_GOLD = 0xFFFFAA00.toInt()
        const val COLOR_CYAN = 0xFF00FFFF.toInt()
        const val COLOR_DARK_BLUE = 0xFF0000AA.toInt()
    }
}

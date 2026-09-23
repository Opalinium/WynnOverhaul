package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.Font
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.Overwatch
import kotlin.math.atan2

class WynnCompassHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Compass HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val player = Minecraft.getInstance().player ?: return
        val font = Minecraft.getInstance().font

        val look = player.lookAngle
        if (look.x == 0.0 && look.z == 0.0) return
        val heading = Math.toDegrees(atan2(look.x, -look.z))

        val playerPos = player.position()
        val markers = ArrayList<Marker>()
        for (match in QuestBeaconTracker.current) {
            val c = match.center()
            markers.add(Marker(c, match.label, match.colorArgb, horizontalDistance(playerPos, c)))
        }
        if (LootrunModel.state != LootrunModel.State.NOT_RUNNING) {
            LootrunParticleFeature.taskCenter?.let { c ->
                markers.add(Marker(c, "Lootrun Task", MARKER_LOOTRUN, horizontalDistance(playerPos, c)))
            }
        }
        val nearest = markers.minByOrNull { it.distance }

        val region = WynnRegionBarTracker.active?.name.orEmpty()
        val markerLine = nearest?.let { "${it.label}  ${it.distance.toInt()}m" }.orEmpty()
        var contentW = MIN_W
        if (region.isNotEmpty()) contentW = maxOf(contentW, font.width(region) + PAD * 2)
        if (markerLine.isNotEmpty()) contentW = maxOf(contentW, font.width(markerLine) + PAD * 2)
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, CONTENT_H)

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

        if (region.isNotEmpty()) {
            val name = region.uppercase(java.util.Locale.ROOT)
            graphics.text(font, name, ox + (boxW - font.width(name)) / 2, oy + PAD, OwTheme.ACCENT, true)
        }
        val stripX = ox + PAD
        val stripW = boxW - PAD * 2
        val stripY = oy + PAD + NAME_H
        drawStrip(graphics, font, stripX, stripY, stripW, heading, markers, playerPos)

        if (markerLine.isNotEmpty()) {
            graphics.text(font, markerLine, ox + (boxW - font.width(markerLine)) / 2, stripY + MARKER_LANE_H + LABEL_H + MARKER_GAP, OwTheme.TEXT_DIM, true)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun drawStrip(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Int,
        y: Int,
        w: Int,
        heading: Double,
        markers: List<Marker>,
        playerPos: Vec3,
    ) {
        val cx = x + w / 2
        val halfW = w / 2f
        val labelY = y + MARKER_LANE_H
        val lineY = y + MARKER_LANE_H + LABEL_H
        graphics.fill(x, lineY, x + w, lineY + 1, OwTheme.HAIRLINE)
        var deg = -180
        while (deg < 180) {
            val rel = norm180(deg - heading)
            if (rel >= -HALF_RANGE && rel <= HALF_RANGE) {
                val tx = (cx + rel / HALF_RANGE * halfW).toInt()
                graphics.fill(tx, lineY - TICK_H, tx + 1, lineY, OwTheme.HAIRLINE)
            }
            deg += TICK_STEP
        }
        for ((windDeg, wind) in WINDS) {
            val rel = norm180(windDeg - heading)
            if (rel >= -HALF_RANGE && rel <= HALF_RANGE) {
                val tx = (cx + rel / HALF_RANGE * halfW).toInt()
                val color = when (wind) {
                    "N" -> OwTheme.ACCENT
                    "E", "S", "W" -> OwTheme.TEXT
                    else -> OwTheme.TEXT_DIM
                }
                graphics.text(font, wind, tx - font.width(wind) / 2, labelY + 1, color, true)
            }
        }
        diamond(graphics, cx, lineY, 3, OwTheme.ACCENT)
        for (marker in markers) {
            val bearing = Math.toDegrees(
                atan2(marker.pos.x - playerPos.x, -(marker.pos.z - playerPos.z)),
            )
            val rel = norm180(bearing - heading)
            if (rel >= -HALF_RANGE && rel <= HALF_RANGE) {
                val mx = (cx + rel / HALF_RANGE * halfW).toInt()
                diamond(graphics, mx, y + MARKER_LANE_H / 2, 2, marker.colorArgb)
            }
        }
    }

    private fun diamond(graphics: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        for (dy in -r..r) {
            val hw = r - kotlin.math.abs(dy)
            graphics.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color)
        }
    }

    private data class Marker(val pos: Vec3, val label: String, val colorArgb: Int, val distance: Double)

    private fun horizontalDistance(a: Vec3, b: Vec3): Double {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    private fun norm180(angle: Double): Double {
        var v = (angle + 180.0) % 360.0
        if (v < 0) v += 360.0
        return v - 180.0
    }

    private companion object {
        const val ID = "compass"
        const val MIN_W = 200
        const val CONTENT_H = 48
        const val PAD = 3
        const val NAME_H = 11
        const val MARKER_LANE_H = 7
        const val LABEL_H = 12
        const val MARKER_GAP = 2
        const val TICK_H = 3
        const val TICK_STEP = 15
        const val HALF_RANGE = 75.0
        const val MARKER_LOOTRUN = 0xFFC9A227.toInt()
        val WINDS = listOf(
            0.0 to "N",
            45.0 to "NE",
            90.0 to "E",
            135.0 to "SE",
            180.0 to "S",
            -135.0 to "SW",
            -90.0 to "W",
            -45.0 to "NW",
        )
    }
}

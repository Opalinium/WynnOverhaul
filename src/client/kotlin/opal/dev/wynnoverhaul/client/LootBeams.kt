package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.sqrt

object LootBeams {
    private class Beam(val x: Double, val y: Double, val z: Double) {
        var baseX = 0f
        var baseY = 0f
        var topX = 0f
        var topY = 0f
        var onScreen = false
        var fade = 1f
    }

    private var beams: List<Beam> = emptyList()
    private var nextScanNanos = 0L
    private val clip = Vector4f()

    fun clear() {
        beams = emptyList()
    }

    fun tick(client: Minecraft) {
        if (!WynnOverhaulConfig.current.lootBeamEnabled || !WynnOverhaulGate.inGame) {
            if (beams.isNotEmpty()) beams = emptyList()
            return
        }
        val now = System.nanoTime()
        if (now < nextScanNanos) return
        nextScanNanos = now + SCAN_INTERVAL_NANOS
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            if (beams.isNotEmpty()) beams = emptyList()
            return
        }
        beams = scan(level, player.x, player.y, player.z)
    }

    fun capture(ctx: LevelRenderContext) {
        if (beams.isEmpty()) return
        val player = Minecraft.getInstance().player ?: return
        val cam = ctx.levelState().cameraRenderState
        val view = cam.viewRotationMatrix
        val viewProjection = Matrix4f(cam.projectionMatrix).mul(view)
        val origin = player.position()
        for (beam in beams) {
            val base = WorldProjection.project(cam.pos, viewProjection, view, clip, beam.x, beam.y, beam.z)
            val top = WorldProjection.project(cam.pos, viewProjection, view, clip, beam.x, beam.y + HEIGHT, beam.z)
            beam.onScreen = base.onScreen || top.onScreen
            beam.baseX = base.x
            beam.baseY = base.y
            beam.topX = top.x
            beam.topY = top.y
            val dx = beam.x - origin.x
            val dy = beam.y - origin.y
            val dz = beam.z - origin.z
            beam.fade = (1f - (sqrt(dx * dx + dy * dy + dz * dz) / RANGE).toFloat()).coerceIn(0f, 1f)
        }
    }

    fun draw(graphics: GuiGraphicsExtractor) {
        if (beams.isEmpty() || !WynnOverhaulGate.inGame) return
        val intensity = WynnOverhaulConfig.current.lootBeamIntensity.toFloat().coerceIn(0.2f, 1.5f)
        val halfW = graphics.guiWidth() * 0.5f
        val halfH = graphics.guiHeight() * 0.5f
        for (beam in beams) {
            if (!beam.onScreen || beam.fade <= 0f) continue
            val bx = halfW + beam.baseX * halfW
            val by = halfH - beam.baseY * halfH
            val tx = halfW + beam.topX * halfW
            val ty = halfH - beam.topY * halfH
            for (i in GROUND_RADII.indices) {
                val r = GROUND_RADII[i]
                val glow = HudStyle.alpha(RGB, GROUND_ALPHA[i] * beam.fade * intensity)
                graphics.fill((bx - r).toInt(), (by - r).toInt(), (bx + r).toInt(), (by + r).toInt(), glow)
            }
            val dx = tx - bx
            val dy = ty - by
            val len = sqrt(dx * dx + dy * dy)
            if (len < 2f) continue
            val steps = (len / STEP_PX).toInt().coerceIn(4, 64)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val fade = (1f - t) * BEAM_ALPHA * beam.fade * intensity
                if (fade <= 0.01f) continue
                val half = WIDTH * (1f - t * TAPER) + 0.5f
                val x = bx + dx * t
                val y = by + dy * t
                graphics.fill((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt(), HudStyle.alpha(RGB, fade))
            }
        }
    }

    private fun scan(level: ClientLevel, px: Double, py: Double, pz: Double): List<Beam> {
        val box = AABB(px - RANGE, py - RANGE, pz - RANGE, px + RANGE, py + RANGE, pz + RANGE)
        val entities = level.getEntitiesOfClass(ItemEntity::class.java, box) { entity -> entity.isAlive }
        if (entities.isEmpty()) return emptyList()
        val out = ArrayList<Beam>()
        for (entity in entities) {
            if (entity.item.isEmpty) continue
            val dx = entity.x - px
            val dy = entity.y - py
            val dz = entity.z - pz
            if (sqrt(dx * dx + dy * dy + dz * dz) > RANGE) continue
            out.add(Beam(entity.x, entity.y + BASE_LIFT, entity.z))
            if (out.size >= MAX_BEAMS) break
        }
        return out
    }

    private const val RGB = 0xFFEAA544.toInt()
    private const val HEIGHT = 2.2f
    private const val WIDTH = 2f
    private const val BEAM_ALPHA = 0.28f
    private val GROUND_RADII = floatArrayOf(9f, 7f, 5f, 3f)
    private val GROUND_ALPHA = floatArrayOf(0.05f, 0.09f, 0.14f, 0.20f)
    private const val RANGE = 48.0
    private const val MAX_BEAMS = 24
    private const val SCAN_INTERVAL_NANOS = 250_000_000L
    private const val STEP_PX = 2f
    private const val TAPER = 0.5f
    private const val BASE_LIFT = 0.1
}

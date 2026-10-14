package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.tan

object ProjectedReticle {
    private const val FRESH_NANOS = 3_000_000L
    private const val NEAR_PLANE = 0.05
    private const val EDGE = 8f
    private const val SMOOTH_RATE = 40.0

    private var stamp = 0L
    private var valid = false
    private var pushed = false
    private var smoothed = false
    private var lastNanos = 0L

    var screenX = 0f
        private set
    var screenY = 0f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    @JvmStatic
    fun update(mc: Minecraft, graphics: GuiGraphicsExtractor): Boolean {
        val now = System.nanoTime()
        if (now - stamp < FRESH_NANOS) return valid
        stamp = now
        valid = compute(mc, graphics, now)
        if (!valid) smoothed = false
        return valid
    }

    @JvmStatic
    fun begin(mc: Minecraft, graphics: GuiGraphicsExtractor) {
        pushed = false
        if (!update(mc, graphics)) return
        graphics.pose().pushMatrix()
        graphics.pose().translate(offsetX, offsetY)
        pushed = true
    }

    @JvmStatic
    fun end(graphics: GuiGraphicsExtractor) {
        if (!pushed) return
        pushed = false
        graphics.pose().popMatrix()
    }

    private fun compute(mc: Minecraft, graphics: GuiGraphicsExtractor, now: Long): Boolean {
        if (!WynnOverhaulConfig.current.soulsCameraProjectReticle || !SoulsCamera.isActive()) return false
        val player = mc.player ?: return false
        if (mc.level == null) return false
        val camera = mc.gameRenderer.mainCamera()
        val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)

        val eye = player.getEyePosition(partial)
        val end = eye.add(SoulsCamera.aimDirection(player, partial).scale(SoulsCamera.focusReach()))
        val point = traceAim(mc, eye, end)

        val rel = point.subtract(camera.position())
        val forward = camera.forwardVector()
        val left = camera.leftVector()
        val up = camera.upVector()
        val depth = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z()
        if (depth < NEAR_PLANE) return false
        val across = rel.x * left.x() + rel.y * left.y() + rel.z * left.z()
        val rise = rel.x * up.x() + rel.y * up.y() + rel.z * up.z()

        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        val tanHalf = tan(Math.toRadians(camera.getFov() / 2.0))
        val ndcX = -across / depth / (tanHalf * width / height)
        val ndcY = rise / depth / tanHalf
        var x = (width / 2f + ndcX * width / 2f).toFloat().coerceIn(EDGE, width - EDGE)
        var y = (height / 2f - ndcY * height / 2f).toFloat().coerceIn(EDGE, height - EDGE)

        val dt = ((now - lastNanos) / 1.0e9).coerceIn(0.0, 0.1)
        lastNanos = now
        if (smoothed) {
            val a = MathX.expApproach(dt, SMOOTH_RATE).toFloat()
            x = screenX + (x - screenX) * a
            y = screenY + (y - screenY) * a
        }
        smoothed = true
        screenX = x
        screenY = y
        offsetX = x - width / 2f
        offsetY = y - height / 2f
        return true
    }

    private fun traceAim(mc: Minecraft, eye: Vec3, end: Vec3): Vec3 {
        val player = mc.player ?: return end
        val level = mc.level ?: return end
        var best = end
        var bestSq = eye.distanceToSqr(end)
        val block = level.clip(ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        if (block.type == HitResult.Type.BLOCK) {
            best = block.location
            bestSq = eye.distanceToSqr(best)
        }
        val box = AABB(eye, end).inflate(1.0)
        for (entity in level.getEntities(player, box) { !it.isSpectator && it.isPickable }) {
            val hit = entity.boundingBox.inflate(entity.pickRadius.toDouble()).clip(eye, end).orElse(null) ?: continue
            val sq = eye.distanceToSqr(hit)
            if (sq < bestSq) {
                best = hit
                bestSq = sq
            }
        }
        return best
    }
}

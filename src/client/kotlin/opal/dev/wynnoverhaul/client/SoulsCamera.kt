package opal.dev.wynnoverhaul.client

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

object SoulsCamera {
    private const val PITCH_MIN = -80f
    private const val PITCH_MAX = 70f
    private const val DEFAULT_PITCH = 15f
    private const val FOCUS_DISTANCE = 30.0
    private const val MAX_LAG_DEGREES = 75f
    private const val TELEPORT_DISTANCE_SQ = 64.0

    class Pose(
        val yaw: Float,
        val pitch: Float,
        val x: Double,
        val y: Double,
        val z: Double,
        val distance: Float,
    )

    class Move(val vector: Vec2, val presses: Input)

    private class SavedRotation(val yaw: Float, val pitch: Float, val yawOld: Float, val pitchOld: Float)

    private var active = false
    private var camYaw = 0f
    private var camPitch = DEFAULT_PITCH
    private var pivot: Vec3? = null
    private var lastFrameNanos = 0L
    private var lastActionNanos = 0L
    private var saved: SavedRotation? = null

    private fun eligible(mc: Minecraft): Boolean {
        if (!WynnOverhaulConfig.current.soulsCameraEnabled) return false
        val player = mc.player ?: return false
        if (mc.level == null) return false
        if (mc.options.cameraType != CameraType.THIRD_PERSON_BACK) return false
        if (mc.cameraEntity !== player) return false
        if (player.isPassenger || player.isFallFlying || player.isSwimming || player.isSleeping || player.isDeadOrDying) return false
        return true
    }

    @JvmStatic
    fun refresh(mc: Minecraft): Boolean {
        val now = eligible(mc)
        if (now && !active) {
            val player = mc.player!!
            camYaw = Mth.wrapDegrees(player.yRot)
            camPitch = player.xRot.coerceIn(PITCH_MIN, PITCH_MAX)
            pivot = null
        }
        if (!now) pivot = null
        active = now
        return now
    }

    @JvmStatic
    fun showsReticle(): Boolean = active && WynnOverhaulConfig.current.soulsCameraReticle

    @JvmStatic
    fun recenter(mc: Minecraft) {
        val player = mc.player ?: return
        if (!refresh(mc)) return
        camYaw = Mth.wrapDegrees(player.yRot)
        camPitch = DEFAULT_PITCH
    }

    @JvmStatic
    fun onMouseTurn(mc: Minecraft, dx: Double, dy: Double): Boolean {
        if (!refresh(mc)) return false
        val scale = 0.15 * WynnOverhaulConfig.current.soulsCameraSensitivity
        camYaw = Mth.wrapDegrees(camYaw + (dx * scale).toFloat())
        camPitch = (camPitch + (dy * scale).toFloat()).coerceIn(PITCH_MIN, PITCH_MAX)
        return true
    }

    @JvmStatic
    fun pose(entity: Entity, eyeHeightOld: Float, eyeHeight: Float, partial: Float): Pose? {
        val mc = Minecraft.getInstance()
        if (entity !== mc.player || !refresh(mc)) return null
        val config = WynnOverhaulConfig.current

        val target = Vec3(
            Mth.lerp(partial.toDouble(), entity.xo, entity.x),
            Mth.lerp(partial.toDouble(), entity.yo, entity.y) + Mth.lerp(partial, eyeHeightOld, eyeHeight) + config.soulsCameraHeight,
            Mth.lerp(partial.toDouble(), entity.zo, entity.z),
        )

        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1.0e9).coerceIn(0.0, 0.1)
        lastFrameNanos = now

        val previous = pivot
        val smoothed = if (previous == null || config.soulsCameraSmoothing <= 0.01 || previous.distanceToSqr(target) > TELEPORT_DISTANCE_SQ) {
            target
        } else {
            val rate = Mth.lerp(config.soulsCameraSmoothing, 40.0, 4.0)
            val a = 1.0 - exp(-dt * rate)
            previous.add(target.subtract(previous).scale(a))
        }
        pivot = smoothed

        val yawRad = Math.toRadians(camYaw.toDouble())
        val shoulder = config.soulsCameraShoulder
        return Pose(
            camYaw,
            camPitch,
            smoothed.x - cos(yawRad) * shoulder,
            smoothed.y,
            smoothed.z - sin(yawRad) * shoulder,
            config.soulsCameraDistance.toFloat(),
        )
    }

    private fun aimAngles(player: LocalPlayer, partial: Float): Pair<Float, Float> {
        val config = WynnOverhaulConfig.current
        val yawRad = Math.toRadians(camYaw.toDouble())
        val pitchRad = Math.toRadians(camPitch.toDouble())
        val forward = Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
        val left = Vec3(cos(yawRad), 0.0, sin(yawRad))
        val eye = player.getEyePosition(partial)
        val cameraPos = eye
            .add(0.0, config.soulsCameraHeight, 0.0)
            .subtract(left.scale(config.soulsCameraShoulder))
            .subtract(forward.scale(config.soulsCameraDistance))
        val direction = cameraPos.add(forward.scale(config.soulsCameraDistance + FOCUS_DISTANCE)).subtract(eye).normalize()
        val yaw = Math.toDegrees(atan2(-direction.x, direction.z)).toFloat()
        val pitch = Math.toDegrees(-asin(direction.y.coerceIn(-1.0, 1.0))).toFloat()
        return yaw to pitch
    }

    @JvmStatic
    fun beginPick(mc: Minecraft, partial: Float) {
        saved = null
        val player = mc.player ?: return
        if (!refresh(mc)) return
        saved = SavedRotation(player.yRot, player.xRot, player.yRotO, player.xRotO)
        val (yaw, pitch) = aimAngles(player, partial)
        player.yRot = yaw
        player.xRot = pitch
        player.yRotO = yaw
        player.xRotO = pitch
    }

    @JvmStatic
    fun endPick(mc: Minecraft) {
        val restore = saved ?: return
        saved = null
        val player = mc.player ?: return
        player.yRot = restore.yaw
        player.xRot = restore.pitch
        player.yRotO = restore.yawOld
        player.xRotO = restore.pitchOld
    }

    private fun faceAim(player: LocalPlayer) {
        val (yaw, pitch) = aimAngles(player, 1f)
        player.yRot = yaw
        player.xRot = pitch
        player.yRotO = yaw
        player.xRotO = pitch
        player.yHeadRot = yaw
        player.yHeadRotO = yaw
        player.yBodyRot = yaw
        player.yBodyRotO = yaw
    }

    @JvmStatic
    fun onCombatAction(mc: Minecraft) {
        val player = mc.player ?: return
        if (!refresh(mc)) return
        lastActionNanos = System.nanoTime()
        val before = player.yRot
        val beforePitch = player.xRot
        faceAim(player)
        if (abs(Mth.wrapDegrees(player.yRot - before)) > 0.01f || abs(player.xRot - beforePitch) > 0.01f) {
            player.connection.send(
                ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, player.onGround(), player.horizontalCollision),
            )
        }
    }

    private fun inCombatHold(mc: Minecraft): Boolean {
        if (mc.gui.screen() == null && (mc.options.keyAttack.isDown || mc.options.keyUse.isDown)) return true
        val holdNanos = (WynnOverhaulConfig.current.soulsCameraFaceHoldMs * 1.0e6).toLong()
        return System.nanoTime() - lastActionNanos < holdNanos
    }

    @JvmStatic
    fun remap(mc: Minecraft, presses: Input, move: Vec2): Move? {
        if (!refresh(mc)) return null
        val player = mc.player ?: return null

        if (inCombatHold(mc)) {
            faceAim(player)
            return null
        }
        if (move.x == 0f && move.y == 0f) return null

        val camRad = Math.toRadians(camYaw.toDouble())
        var dx = move.y * -sin(camRad) + move.x * cos(camRad)
        var dz = move.y * cos(camRad) + move.x * sin(camRad)
        val length = sqrt(dx * dx + dz * dz)
        if (length < 1.0e-4) return null
        dx /= length
        dz /= length

        val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val diff = Mth.wrapDegrees(targetYaw - player.yRot)
        val factor = WynnOverhaulConfig.current.soulsCameraTurnSpeed.toFloat()
        var step = diff * factor
        if (abs(diff - step) > MAX_LAG_DEGREES) step = diff - sign(diff) * MAX_LAG_DEGREES
        player.yRot = player.yRot + step

        val bodyRad = Math.toRadians(player.yRot.toDouble())
        val localForward = dx * -sin(bodyRad) + dz * cos(bodyRad)
        val localLeft = dx * cos(bodyRad) + dz * sin(bodyRad)

        return Move(
            Vec2(localLeft.toFloat(), localForward.toFloat()),
            Input(true, false, false, false, presses.jump(), presses.shift(), presses.sprint()),
        )
    }
}

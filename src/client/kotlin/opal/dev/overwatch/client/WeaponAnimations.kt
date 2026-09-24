package opal.dev.overwatch.client

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.ThreadLocalRandom

object WeaponAnimations {

    private const val RAD_TO_DEG = 57.29578f
    private const val MIN_SECONDS = 0.18f
    private const val GAP_FRACTION = 0.95f
    private const val HIT_SECONDS = 0.14f
    private const val HIT_SHARE = 0.5f
    private const val AMP_MIN = 0.45f
    private const val CHAIN_SKIP = 0.7f
    private const val HOLD_SECONDS = 0.12f
    private const val RELAX_SECONDS = 0.22f
    private const val MIN_GAP_FRACTION = 0.75f
    private const val MIN_GAP_LOW = 0.12f
    private const val MIN_GAP_HIGH = 0.9f
    private const val FALLBACK_GAP = 0.25f
    private const val BLEND_SECONDS = 0.09f
    private const val SPELL_BLEND_SECONDS = 0.12f
    private const val IDLE_TAU = 0.16f
    private const val COMBO_RESET_SECONDS = 1.6f
    private const val COMBO_PITCH_STEP = 0.035f
    private const val PLAYER_SCALE = 0.9375f
    private const val MODEL_UNIT = 16f
    private const val MODEL_LIFT = 1.501f
    private const val READY_NANOS = 3_500_000_000L
    private const val BREATH_NANOS = 3_400_000_000L
    private const val BREATH_AMP = 0.025f

    private const val PITCH = 0
    private const val ACROSS = 1
    private const val ABDUCT = 2
    private const val OFF_PITCH = 3
    private const val OFF_ACROSS = 4
    private const val OFF_ABDUCT = 5
    private const val TURN = 6
    private const val REACH = 7
    private const val HEAD_A = 8
    private const val HEAD_Y = 9
    private const val TWIST = 10
    private const val GRIP = 11
    private const val LEAN = 12
    private const val STEP = 13
    private const val CHANNELS = 14
    private const val TURN_BOOST = 1.4f
    private const val LEAN_HEAD_SHARE = 0.3f
    private const val HIP_Y = 12f
    private const val STEP_REACH = 2.2f
    private const val STEP_LEG_PITCH = 0.55f
    private const val STEP_BACK_PITCH = 0.25f
    private const val STEP_BACK_REACH = 0.6f
    private const val GRIP_Y = 10f
    private const val GRIP_Z = -2f
    private const val SHOULDER_X = 5f
    private const val BODY_HALF_WIDTH = 4.5f
    private const val BODY_FRONT = -4.2f
    private const val BODY_BOTTOM = 14f
    private const val CLIP_STEP = 0.1f
    private const val CLIP_OUTWARD = 0.05f
    private const val CLIP_STEPS = 9
    private val GRIP_RELAX = floatArrayOf(1f, 0.8f, 0.6f, 0.4f, 0.2f, 0f)
    private val GUARD_POINTS = floatArrayOf(0.6f, 0.85f, 1f)
    private const val GRIP_LEN_SQ = GRIP_Y * GRIP_Y + GRIP_Z * GRIP_Z

    private val REST_BASE = floatArrayOf(-0.3f, 0f, 0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.3f, 0f, 0f, 0f, 0f, 0f)

    private val B = Float.NaN

    private class Stance(val carry: FloatArray, val ready: FloatArray, val gripOffset: Float = 7f)

    private fun stance(carry: FloatArray, ready: FloatArray, gripOffset: Float = 7f) = Stance(carry, ready, gripOffset)

    private val STANCES: Map<String, Stance> = mapOf(
        "SPEAR" to stance(
            floatArrayOf(-0.9f, 0.3f, 0.08f, -0.8f, 0.4f, 0.05f, 0.1f, 0f, -0.85f, 0.2f, 0f, 1f, 0f, 0f),
            floatArrayOf(-1.3f, 0.3f, 0.1f, -1.1f, 0.5f, 0.05f, 0.2f, -0.5f, -0.35f, 0.2f, 0f, 1f, 0f, 0f),
        ),
        "WAND:STAFF" to stance(
            floatArrayOf(-0.7f, 0.2f, 0.05f, -0.7f, 0.4f, 0.05f, 0.05f, 0f, -1.35f, -0.2f, 0f, 1f, 0f, 0f),
            floatArrayOf(-1.1f, 0.2f, 0.08f, -1.0f, 0.5f, 0.05f, 0.1f, -0.3f, -1.1f, -0.2f, 0f, 1f, 0f, 0f),
        ),
        "DAGGER" to stance(
            floatArrayOf(-0.6f, 0.15f, 0.1f, -0.3f, 0.1f, 0.2f, 0.15f, 0f, -0.15f, 0.15f, 0f, 0f, 0f, 0f),
            floatArrayOf(-0.9f, 0.3f, 0.15f, -0.4f, 0.15f, 0.35f, 0.3f, 0f, -0.3f, 0.35f, 0f, 0f, 0f, 0f),
        ),
        "WAND" to stance(
            floatArrayOf(-0.55f, 0.1f, 0.05f, -0.2f, 0.05f, 0.1f, 0.05f, 0f, -0.4f, 0.05f, 0f, 0f, 0f, 0f),
            floatArrayOf(-1.0f, 0.15f, 0.1f, -0.3f, 0.1f, 0.15f, 0.1f, 0f, -0.5f, 0.1f, 0f, 0f, 0f, 0f),
        ),
        "RELIK" to stance(
            floatArrayOf(-0.6f, 0.2f, 0.05f, -0.4f, 0.25f, 0.05f, 0.08f, 0f, -0.3f, 0.1f, 0f, 0f, 0f, 0f),
            floatArrayOf(-1.0f, 0.35f, 0.1f, -0.7f, 0.5f, 0.1f, 0.15f, 0f, -0.4f, 0.2f, 0f, 0f, 0f, 0f),
        ),
        "BOW" to stance(
            floatArrayOf(-0.8f, 0.15f, 0f, -0.6f, 0.3f, 0f, 0.15f, 0f, -0.1f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(-1.45f, 0.25f, 0f, -1.5f, 0.7f, 0f, 0.35f, -0.6f, -0.1f, 0f, 0f, 0f, 0f, 0f),
        ),
        "BOW:FIREARM" to stance(
            floatArrayOf(-0.9f, 0.35f, 0f, -0.9f, 0.4f, 0f, 0.1f, 0f, -0.5f, 0f, 0f, 1f, 0f, 0f),
            floatArrayOf(-1.2f, 0.45f, 0f, -1.3f, 0.6f, 0f, 0.15f, -0.8f, -0.05f, 0f, 0f, 1f, 0f, 0f),
            9f,
        ),
    )

    private fun stanceFor(key: String?): Stance? =
        if (key == null) null else STANCES[key] ?: STANCES[key.substringBefore(':')]

    private class Curve(private val times: FloatArray, private val values: FloatArray) {
        private fun value(i: Int, base: Float): Float = values[i].let { if (it.isNaN()) base else it }

        private fun slope(i: Int, base: Float): Float =
            if (i == 0 || i == times.lastIndex) 0f
            else (value(i + 1, base) - value(i - 1, base)) / (times[i + 1] - times[i - 1])

        fun negated(): Curve = Curve(times, FloatArray(values.size) { -values[it] })

        fun scaled(f: Float): Curve = Curve(times, FloatArray(values.size) { values[it] * f })

        fun at(t: Float, base: Float): Float {
            if (t <= times.first()) return value(0, base)
            for (i in 1 until times.size) {
                if (t <= times[i]) {
                    val h = times[i] - times[i - 1]
                    val u = (t - times[i - 1]) / h
                    val u2 = u * u
                    val u3 = u2 * u
                    return (2f * u3 - 3f * u2 + 1f) * value(i - 1, base) +
                        (u3 - 2f * u2 + u) * h * slope(i - 1, base) +
                        (-2f * u3 + 3f * u2) * value(i, base) +
                        (u3 - u2) * h * slope(i, base)
                }
            }
            return value(times.lastIndex, base)
        }
    }

    private fun curve(vararg keys: Pair<Float, Float>) =
        Curve(FloatArray(keys.size) { keys[it].first }, FloatArray(keys.size) { keys[it].second })

    private val KEEP = curve(0f to B, 1f to B)
    private val ZERO = curve(0f to 0f, 1f to 0f)

    private class Pose(
        pitch: Curve = KEEP,
        across: Curve = KEEP,
        abduct: Curve = KEEP,
        offPitch: Curve = KEEP,
        offAcross: Curve = KEEP,
        offAbduct: Curve = KEEP,
        turn: Curve = ZERO,
        reach: Curve = ZERO,
        headA: Curve = KEEP,
        headY: Curve = KEEP,
        twist: Curve = ZERO,
        grip: Curve = KEEP,
        lean: Curve = KEEP,
        step: Curve = KEEP,
    ) {
        var seconds = 0.36f
        var hit = 0.3f
        var holdEnd = false
        var spin = false
        var gripOffset = 7f
        var key = ""
        fun mirrored(): Pose = Pose(
            pitch = curves[PITCH],
            across = curves[ACROSS].negated(),
            abduct = curves[ABDUCT],
            offPitch = curves[OFF_PITCH],
            offAcross = curves[OFF_ACROSS].negated(),
            offAbduct = curves[OFF_ABDUCT],
            turn = curves[TURN].negated(),
            reach = curves[REACH],
            headA = curves[HEAD_A],
            headY = curves[HEAD_Y].negated(),
            twist = curves[TWIST].negated(),
            grip = curves[GRIP],
            lean = curves[LEAN],
            step = curves[STEP],
        ).also {
            it.seconds = seconds
            it.hit = hit
            it.holdEnd = holdEnd
            it.spin = spin
            it.gripOffset = gripOffset
        }

        val curves: Array<Curve> = arrayOf(pitch, across, abduct, offPitch, offAcross, offAbduct, turn, reach, headA, headY, twist, grip, lean, step)
    }

    private val POSES: Map<String, Pose> = mapOf(
        "SPEAR" to Pose(
            pitch = curve(0f to B, 0.14f to -0.1f, 0.3f to -1.25f, 0.5f to -1.15f, 1f to B),
            across = curve(0f to B, 0.14f to -0.15f, 0.3f to 0.1f, 1f to B),
            abduct = curve(0f to B, 0.14f to 0.2f, 0.3f to 0.05f, 1f to B),
            offPitch = curve(0f to B, 0.14f to -0.9f, 0.3f to -1.3f, 0.5f to -1.2f, 1f to B),
            offAcross = curve(0f to B, 0.14f to 0.5f, 0.3f to 0.6f, 0.5f to 0.55f, 1f to B),
            turn = curve(0f to B, 0.14f to -0.3f, 0.3f to 0.45f, 0.5f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.14f to 1.5f, 0.3f to -3.5f, 0.5f to -2.5f, 1f to B),
            headA = curve(0f to B, 0.14f to 0.1f, 0.3f to -0.05f, 0.5f to 0f, 1f to B),
            headY = curve(0f to B, 0.14f to -0.1f, 0.3f to 0f, 0.5f to 0f, 1f to B),
        ),
        "SPEAR:SWEEP" to Pose(
            pitch = curve(0f to B, 0.16f to -1.0f, 0.42f to -1.1f, 0.72f to -0.8f, 1f to B),
            across = curve(0f to B, 0.16f to -0.9f, 0.42f to 0.9f, 0.72f to 0.5f, 1f to B),
            abduct = curve(0f to B, 0.16f to 0.3f, 0.42f to -0.1f, 1f to B),
            offPitch = curve(0f to B, 0.16f to -0.9f, 0.42f to -1.0f, 0.72f to -0.7f, 1f to B),
            offAcross = curve(0f to B, 0.16f to 0.2f, 0.42f to 0.9f, 0.72f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.16f to -0.5f, 0.42f to 0.6f, 0.72f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.42f to -1.8f, 1f to B),
            headA = curve(0f to B, 0.16f to -0.15f, 0.42f to -0.1f, 0.72f to -0.15f, 1f to B),
            headY = curve(0f to B, 0.16f to -0.8f, 0.42f to 0.9f, 0.72f to 0.6f, 1f to B),
            twist = curve(0f to B, 0.16f to 0.5f, 0.42f to 1.2f, 0.72f to 0.6f, 1f to B),
        ),
        "SPEAR:SCYTHE" to Pose(
            pitch = curve(0f to B, 0.2f to -2.2f, 0.42f to -1.2f, 0.72f to -0.7f, 1f to B),
            across = curve(0f to B, 0.2f to -0.5f, 0.42f to 0.6f, 0.72f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.2f to 0.4f, 0.42f to -0.1f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -1.8f, 0.42f to -1.0f, 0.72f to -0.7f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.3f, 0.42f to 0.7f, 0.72f to 0.4f, 1f to B),
            turn = curve(0f to B, 0.2f to -0.4f, 0.42f to 0.5f, 0.72f to 0.25f, 1f to B),
            reach = curve(0f to B, 0.2f to 0.5f, 0.42f to -1.8f, 1f to B),
            headA = curve(0f to B, 0.2f to -2.0f, 0.42f to 0.6f, 0.72f to 0.2f, 1f to B),
            headY = curve(0f to B, 0.2f to -0.3f, 0.42f to 0.5f, 0.72f to 0.3f, 1f to B),
            twist = curve(0f to B, 0.2f to -0.4f, 0.42f to 1.1f, 0.72f to 0.6f, 1f to B),
        ),
        "SPEAR:SLAM" to Pose(
            pitch = curve(0f to B, 0.22f to -2.5f, 0.38f to -1.0f, 0.55f to -0.9f, 1f to B),
            across = curve(0f to B, 0.22f to -0.1f, 0.38f to 0.2f, 0.55f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.22f to -2.2f, 0.38f to -0.9f, 0.55f to -0.85f, 1f to B),
            offAcross = curve(0f to B, 0.22f to 0.4f, 0.38f to 0.5f, 0.55f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.22f to -0.25f, 0.38f to 0.3f, 0.55f to 0.25f, 1f to B),
            reach = curve(0f to B, 0.22f to 1.5f, 0.38f to -2.2f, 0.55f to -1.8f, 1f to B),
            headA = curve(0f to B, 0.22f to -2.3f, 0.38f to 1.2f, 0.55f to 1.0f, 1f to B),
            headY = curve(0f to B, 0.22f to 0f, 0.38f to 0.1f, 0.55f to 0.1f, 1f to B),
        ),
        "SPEAR:TWIRL" to Pose(
            pitch = curve(0f to B, 0.14f to -1.0f, 0.5f to -0.95f, 0.86f to -1.0f, 1f to B),
            across = curve(0f to B, 0.14f to 0.3f, 0.5f to 0.35f, 0.86f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.14f to 0.1f, 1f to B),
            offPitch = curve(0f to B, 0.14f to -1.1f, 0.5f to -1.0f, 0.86f to -1.1f, 1f to B),
            offAcross = curve(0f to B, 0.14f to 0.5f, 0.5f to 0.5f, 0.86f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.14f to -0.6f, 0.5f to 0.2f, 0.86f to 0.9f, 1f to B),
            reach = curve(0f to B, 0.5f to -1f, 1f to B),
            headA = curve(0f to B, 0.14f to -0.15f, 0.86f to -0.15f, 1f to B),
            headY = curve(0f to B, 0.14f to -0.7f, 0.5f to 3.3f, 0.86f to 6.0f, 1f to 6.2832f),
            twist = curve(0f to B, 0.14f to 0.3f, 0.5f to 0.6f, 0.86f to 0.3f, 1f to B),
        ),
        "DAGGER" to Pose(
            pitch = curve(0f to B, 0.1f to -1.1f, 0.28f to -1.3f, 0.55f to -0.8f, 1f to B),
            across = curve(0f to B, 0.1f to -0.7f, 0.28f to 0.8f, 0.55f to 0.4f, 1f to B),
            abduct = curve(0f to B, 0.1f to 0.25f, 0.28f to -0.1f, 1f to B),
            offPitch = curve(0f to B, 0.1f to -0.3f, 0.28f to 0.2f, 0.55f to -0.2f, 1f to B),
            offAcross = curve(0f to B, 0.1f to 0.1f, 0.28f to -0.3f, 1f to B),
            offAbduct = curve(0f to B, 0.1f to 0.3f, 0.28f to 0.35f, 1f to B),
            turn = curve(0f to B, 0.1f to -0.4f, 0.28f to 0.55f, 0.55f to 0.25f, 1f to B),
            reach = curve(0f to B, 0.28f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.1f to -0.1f, 0.28f to -0.1f, 0.55f to -0.3f, 1f to B),
            headY = curve(0f to B, 0.1f to -0.9f, 0.28f to 0.9f, 0.55f to 0.4f, 1f to B),
            twist = curve(0f to B, 0.1f to -0.6f, 0.28f to 0.8f, 0.55f to 0.3f, 1f to B),
        ),
        "DAGGER:STAB" to Pose(
            pitch = curve(0f to B, 0.08f to -0.2f, 0.2f to -1.35f, 0.42f to -1.2f, 1f to B),
            across = curve(0f to B, 0.2f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.08f to 0.1f, 0.2f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.08f to -0.2f, 0.2f to 0.4f, 0.42f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.08f to 1f, 0.2f to -3.2f, 0.42f to -2f, 1f to B),
            headA = curve(0f to B, 0.08f to 0.2f, 0.2f to 0f, 0.42f to 0f, 1f to B),
            headY = curve(0f to B, 0.2f to 0.1f, 1f to B),
        ),
        "DAGGER:WHIRL" to Pose(
            pitch = curve(0f to B, 0.12f to -1.2f, 0.7f to -1.2f, 1f to B),
            across = curve(0f to B, 0.12f to -0.9f, 0.38f to 0.9f, 0.62f to -0.5f, 0.85f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.12f to 0.2f, 1f to B),
            offAcross = curve(0f to B, 0.38f to -0.3f, 1f to B),
            turn = curve(0f to B, 0.12f to -0.4f, 0.38f to 0.5f, 0.62f to -0.2f, 1f to B),
            headA = curve(0f to B, 0.12f to -0.2f, 0.7f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.12f to -0.9f, 0.38f to 0.9f, 0.62f to -0.6f, 0.85f to 0.4f, 1f to B),
        ),
        "WAND" to Pose(
            pitch = curve(0f to B, 0.14f to -0.8f, 0.3f to -1.45f, 0.6f to -1.25f, 1f to B),
            across = curve(0f to B, 0.14f to -0.15f, 0.3f to 0.15f, 1f to B),
            abduct = curve(0f to B, 0.14f to 0.1f, 1f to B),
            offPitch = curve(0f to B, 0.14f to -0.2f, 0.3f to -0.1f, 1f to B),
            turn = curve(0f to B, 0.14f to -0.1f, 0.3f to 0.15f, 1f to B),
            reach = curve(0f to B, 0.3f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.14f to -0.5f, 0.3f to -0.1f, 0.6f to -0.15f, 1f to B),
        ),
        "WAND:STAFF" to Pose(
            pitch = curve(0f to B, 0.14f to -2.0f, 0.32f to -0.9f, 0.6f to -0.85f, 1f to B),
            offPitch = curve(0f to B, 0.14f to -1.7f, 0.32f to -0.8f, 0.6f to -0.8f, 1f to B),
            offAcross = curve(0f to B, 0.14f to 0.35f, 0.32f to 0.4f, 1f to B),
            turn = curve(0f to B, 0.14f to -0.15f, 0.32f to 0.2f, 1f to B),
            reach = curve(0f to B, 0.14f to 0.8f, 0.32f to -2f, 1f to B),
            headA = curve(0f to B, 0.14f to -1.4f, 0.32f to -1.5f, 0.6f to -1.5f, 1f to B),
        ),
        "RELIK" to Pose(
            pitch = curve(0f to B, 0.2f to -1.1f, 0.65f to -1.05f, 1f to B),
            across = curve(0f to B, 0.2f to 0.5f, 0.5f to 0f, 0.75f to -0.4f, 1f to B),
            abduct = curve(0f to B, 0.2f to 0.15f, 0.75f to -0.15f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -0.6f, 0.7f to -0.6f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.4f, 0.75f to -0.1f, 1f to B),
            turn = curve(0f to B, 0.2f to 0.15f, 0.75f to -0.15f, 1f to B),
            reach = curve(0f to B, 0.2f to -1.2f, 0.7f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.2f to -0.3f, 0.5f to -0.1f, 0.75f to -0.3f, 1f to B),
            headY = curve(0f to B, 0.2f to 0.4f, 0.5f to 0f, 0.75f to -0.4f, 1f to B),
        ),
        "BOW" to Pose(
            pitch = curve(0f to B, 0.16f to -1.45f, 0.7f to -1.45f, 0.84f to -1.5f, 1f to B),
            across = curve(0f to B, 0.16f to 0.25f, 0.72f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.16f to -1.5f, 0.7f to -1.5f, 0.84f to -1.45f, 1f to B),
            offAcross = curve(0f to B, 0.16f to 0.7f, 0.7f to -0.1f, 0.84f to -0.5f, 1f to B),
            turn = curve(0f to B, 0.16f to 0.35f, 0.7f to 0.35f, 0.84f to 0.1f, 1f to B),
            reach = curve(0f to B, 0.16f to -0.6f, 0.7f to -0.6f, 1f to B),
        ),
        "BOW:FIREARM" to Pose(
            pitch = curve(0f to B, 0.1f to -1.3f, 0.2f to -1.6f, 0.4f to -1.3f, 0.8f to -1.3f, 1f to B),
            across = curve(0f to B, 0.1f to 0.3f, 0.8f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.1f to -1.3f, 0.2f to -1.5f, 0.8f to -1.3f, 1f to B),
            offAcross = curve(0f to B, 0.1f to 0.6f, 0.8f to 0.6f, 1f to B),
            turn = curve(0f to B, 0.1f to 0.2f, 0.2f to 0.05f, 0.8f to 0.15f, 1f to B),
            reach = curve(0f to B, 0.1f to -0.8f, 0.2f to 0.7f, 0.4f to -0.8f, 0.8f to -0.8f, 1f to B),
            headA = curve(0f to B, 0.1f to -0.1f, 0.2f to -0.35f, 0.4f to -0.1f, 0.8f to -0.1f, 1f to B),
            headY = curve(0f to B, 0.1f to 0.15f, 0.8f to 0.15f, 1f to B),
        ),
        "DAGGER:UPPER" to Pose(
            pitch = curve(0f to B, 0.1f to -0.3f, 0.28f to -1.5f, 0.55f to -1.2f, 1f to B),
            across = curve(0f to B, 0.1f to 0.6f, 0.28f to -0.5f, 0.55f to -0.2f, 1f to B),
            abduct = curve(0f to B, 0.1f to 0.1f, 0.28f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.1f to -0.6f, 0.28f to -0.9f, 1f to B),
            offAcross = curve(0f to B, 0.1f to 0.3f, 0.28f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.1f to 0.4f, 0.28f to -0.5f, 0.55f to -0.2f, 1f to B),
            reach = curve(0f to B, 0.1f to 0.5f, 0.28f to -1.6f, 1f to B),
            headA = curve(0f to B, 0.1f to 0.5f, 0.28f to -0.9f, 0.55f to -0.5f, 1f to B),
            headY = curve(0f to B, 0.1f to 0.5f, 0.28f to -0.4f, 0.55f to -0.2f, 1f to B),
            twist = curve(0f to B, 0.1f to 0.6f, 0.28f to -0.8f, 0.55f to -0.3f, 1f to B),
        ),
        "DAGGER:CROSS" to Pose(
            pitch = curve(0f to B, 0.1f to -0.9f, 0.26f to -0.8f, 0.5f to -0.7f, 1f to B),
            across = curve(0f to B, 0.1f to -1.0f, 0.26f to 1.0f, 0.5f to 0.5f, 1f to B),
            abduct = curve(0f to B, 0.1f to 0.4f, 0.26f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.1f to -0.9f, 0.26f to -0.9f, 0.5f to -0.6f, 1f to B),
            offAcross = curve(0f to B, 0.1f to 1.0f, 0.26f to -0.7f, 0.5f to -0.3f, 1f to B),
            offAbduct = curve(0f to B, 0.1f to 0.4f, 0.26f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.1f to -0.6f, 0.26f to 0.7f, 0.5f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.26f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.1f to -0.2f, 0.26f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.1f to -1.1f, 0.26f to 1.1f, 0.5f to 0.5f, 1f to B),
            twist = curve(0f to B, 0.1f to -0.8f, 0.26f to 1.0f, 0.5f to 0.4f, 1f to B),
        ),
        "WAND:FLICK" to Pose(
            pitch = curve(0f to B, 0.12f to -0.9f, 0.26f to -1.25f, 0.55f to -1.0f, 1f to B),
            across = curve(0f to B, 0.12f to 0.2f, 0.26f to -0.1f, 1f to B),
            offPitch = curve(0f to B, 0.12f to -0.3f, 0.26f to -0.5f, 1f to B),
            offAcross = curve(0f to B, 0.26f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.12f to 0.1f, 0.26f to -0.1f, 1f to B),
            reach = curve(0f to B, 0.26f to -0.8f, 1f to B),
            headA = curve(0f to B, 0.12f to -1.1f, 0.26f to 0.2f, 0.55f to 0f, 1f to B),
        ),
        "WAND:ORBIT" to Pose(
            pitch = curve(0f to B, 0.15f to -1.3f, 0.7f to -1.35f, 1f to B),
            across = curve(0f to B, 0.15f to -0.5f, 0.42f to 0.5f, 0.7f to 0.1f, 1f to B),
            abduct = curve(0f to B, 0.15f to 0.25f, 0.7f to 0.15f, 1f to B),
            offPitch = curve(0f to B, 0.15f to -0.9f, 0.7f to -0.9f, 1f to B),
            offAcross = curve(0f to B, 0.15f to 0.7f, 0.42f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.15f to -0.2f, 0.42f to 0.25f, 0.7f to 0.1f, 1f to B),
            reach = curve(0f to B, 0.42f to -1.0f, 1f to B),
            headA = curve(0f to B, 0.15f to -0.3f, 0.42f to -0.9f, 0.7f to -0.6f, 1f to B),
            headY = curve(0f to B, 0.15f to -0.7f, 0.42f to 0.7f, 0.7f to 0.2f, 1f to B),
            twist = curve(0f to B, 0.15f to -0.5f, 0.42f to 0.8f, 1f to B),
        ),
        "WAND:STAFF_SWEEP" to Pose(
            pitch = curve(0f to B, 0.18f to -1.0f, 0.42f to -1.05f, 0.7f to -0.8f, 1f to B),
            across = curve(0f to B, 0.18f to -0.8f, 0.42f to 0.8f, 0.7f to 0.4f, 1f to B),
            offPitch = curve(0f to B, 0.18f to -1.0f, 0.42f to -1.0f, 0.7f to -0.8f, 1f to B),
            offAcross = curve(0f to B, 0.18f to 0.2f, 0.42f to 0.8f, 0.7f to 0.4f, 1f to B),
            turn = curve(0f to B, 0.18f to -0.5f, 0.42f to 0.6f, 0.7f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.42f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.18f to -1.2f, 0.42f to -0.7f, 0.7f to -0.9f, 1f to B),
            headY = curve(0f to B, 0.18f to -0.8f, 0.42f to 0.9f, 0.7f to 0.5f, 1f to B),
        ),
        "WAND:STAFF_RAISE" to Pose(
            pitch = curve(0f to B, 0.22f to -2.4f, 0.4f to -2.0f, 0.65f to -1.6f, 1f to B),
            offPitch = curve(0f to B, 0.22f to -2.1f, 0.4f to -1.8f, 0.65f to -1.5f, 1f to B),
            offAcross = curve(0f to B, 0.22f to 0.3f, 0.4f to 0.4f, 1f to B),
            turn = curve(0f to B, 0.22f to 0.1f, 0.4f to -0.1f, 1f to B),
            reach = curve(0f to B, 0.22f to 0.5f, 0.4f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.22f to -1.6f, 0.4f to -0.4f, 0.65f to -0.8f, 1f to B),
        ),
        "RELIK:PULSE" to Pose(
            pitch = curve(0f to B, 0.2f to -1.3f, 0.6f to -1.3f, 1f to B),
            across = curve(0f to B, 0.2f to 0.3f, 0.6f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.2f to 0.1f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -1.3f, 0.6f to -1.3f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.6f, 0.6f to 0.6f, 1f to B),
            turn = curve(0f to B, 0.12f to -0.15f, 0.3f to 0.05f, 1f to B),
            reach = curve(0f to B, 0.1f to 0.8f, 0.3f to -1.6f, 0.6f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.2f to -0.2f, 0.6f to -0.2f, 1f to B),
        ),
        "RELIK:WAVE" to Pose(
            pitch = curve(0f to B, 0.22f to -0.4f, 0.55f to -1.7f, 0.8f to -1.4f, 1f to B),
            across = curve(0f to B, 0.22f to -0.7f, 0.55f to 0.6f, 0.8f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.22f to 0.5f, 0.55f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.22f to -0.3f, 0.55f to -1.4f, 1f to B),
            offAcross = curve(0f to B, 0.22f to -0.4f, 0.55f to 0.7f, 1f to B),
            turn = curve(0f to B, 0.22f to -0.4f, 0.55f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.55f to -1.0f, 1f to B),
            headA = curve(0f to B, 0.22f to 0.4f, 0.55f to -0.6f, 1f to B),
            headY = curve(0f to B, 0.22f to -0.6f, 0.55f to 0.5f, 1f to B),
        ),
        "SPELL:BASH" to Pose(
            pitch = curve(0f to B, 0.3f to -2.6f, 0.5f to -0.9f, 0.8f to -0.85f, 1f to B),
            across = curve(0f to B, 0.3f to -0.1f, 0.5f to 0.2f, 0.8f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -2.3f, 0.5f to -0.85f, 0.8f to -0.8f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.4f, 0.5f to 0.5f, 0.8f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.3f to -0.2f, 0.5f to 0.25f, 0.8f to 0.2f, 1f to B),
            reach = curve(0f to B, 0.3f to 1.5f, 0.5f to -2.5f, 0.8f to -2f, 1f to B),
            headA = curve(0f to B, 0.3f to -2.4f, 0.5f to 1.3f, 0.8f to 1.0f, 1f to B),
        ),
        "SPELL:CHARGE" to Pose(
            pitch = curve(0f to B, 0.25f to -1.1f, 0.55f to -1.3f, 0.85f to -1.2f, 1f to B),
            across = curve(0f to B, 0.25f to 0.3f, 0.55f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -1.1f, 0.55f to -1.2f, 0.85f to -1.15f, 1f to B),
            offAcross = curve(0f to B, 0.25f to 0.5f, 0.55f to 0.55f, 1f to B),
            turn = curve(0f to B, 0.25f to -0.3f, 0.55f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.25f to 1.5f, 0.55f to -3.5f, 0.85f to -3f, 1f to B),
            headA = curve(0f to B, 0.25f to 0.1f, 0.55f to 0f, 1f to B),
            lean = curve(0f to B, 0.25f to -0.2f, 0.55f to 0.9f, 0.85f to 0.7f, 1f to B),
            step = curve(0f to B, 0.25f to -0.3f, 0.55f to 1.2f, 0.85f to 1f, 1f to B),
        ),
        "SPELL:UPPERCUT" to Pose(
            pitch = curve(0f to B, 0.25f to -0.3f, 0.5f to -2.2f, 0.8f to -1.6f, 1f to B),
            across = curve(0f to B, 0.25f to 0.5f, 0.5f to -0.4f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -0.4f, 0.5f to -1.9f, 0.8f to -1.4f, 1f to B),
            offAcross = curve(0f to B, 0.25f to 0.5f, 0.5f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.25f to 0.3f, 0.5f to -0.3f, 1f to B),
            reach = curve(0f to B, 0.5f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.25f to 0.8f, 0.5f to -1.6f, 0.8f to -1.0f, 1f to B),
            lean = curve(0f to B, 0.25f to 0.1f, 0.5f to 0.25f, 1f to B),
            step = curve(0f to B, 0.25f to -0.2f, 0.5f to 0.8f, 0.8f to 0.4f, 1f to B),
        ),
        "SPELL:WAR_SCREAM" to Pose(
            pitch = curve(0f to B, 0.3f to -2.9f, 0.75f to -2.9f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -2.7f, 0.75f to -2.7f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.4f, 0.75f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.3f to 0.5f, 0.75f to 0.5f, 1f to B),
            headA = curve(0f to B, 0.3f to -1.5f, 0.75f to -1.5f, 1f to B),
            lean = curve(0f to B, 0.3f to -0.3f, 0.75f to -0.3f, 1f to B),
        ),
        "SPELL:HEAL" to Pose(
            pitch = curve(0f to B, 0.35f to -2.0f, 0.7f to -1.7f, 1f to B),
            abduct = curve(0f to B, 0.35f to 0.9f, 0.7f to 0.4f, 1f to B),
            offPitch = curve(0f to B, 0.35f to -2.0f, 0.7f to -1.7f, 1f to B),
            offAbduct = curve(0f to B, 0.35f to 0.9f, 0.7f to 0.4f, 1f to B),
            headA = curve(0f to B, 0.35f to -1.3f, 0.7f to -1.0f, 1f to B),
            lean = curve(0f to B, 0.35f to -0.2f, 0.7f to -0.1f, 1f to B),
        ),
        "SPELL:TELEPORT" to Pose(
            pitch = curve(0f to B, 0.2f to -0.8f, 0.4f to -1.5f, 0.75f to -1.4f, 1f to B),
            across = curve(0f to B, 0.4f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -0.6f, 0.4f to -0.5f, 1f to B),
            turn = curve(0f to B, 0.2f to -0.3f, 0.4f to 0.3f, 1f to B),
            reach = curve(0f to B, 0.4f to -2f, 1f to B),
            headA = curve(0f to B, 0.2f to -0.5f, 0.4f to -0.1f, 1f to B),
            lean = curve(0f to B, 0.4f to 0.3f, 1f to B),
            step = curve(0f to B, 0.4f to 0.6f, 1f to B),
        ),
        "SPELL:METEOR" to Pose(
            pitch = curve(0f to B, 0.4f to -2.8f, 0.6f to -1.2f, 0.85f to -1.1f, 1f to B),
            offPitch = curve(0f to B, 0.4f to -2.4f, 0.6f to -1.0f, 0.85f to -1.0f, 1f to B),
            offAcross = curve(0f to B, 0.4f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.4f to -0.15f, 0.6f to 0.15f, 1f to B),
            reach = curve(0f to B, 0.4f to 0.5f, 0.6f to -2.2f, 0.85f to -1.8f, 1f to B),
            headA = curve(0f to B, 0.4f to -1.6f, 0.6f to 0.6f, 0.85f to 0.4f, 1f to B),
            lean = curve(0f to B, 0.4f to -0.3f, 0.6f to 0.5f, 0.85f to 0.3f, 1f to B),
        ),
        "SPELL:ICE_SNAKE" to Pose(
            pitch = curve(0f to B, 0.2f to -1.0f, 0.45f to -1.5f, 0.8f to -1.4f, 1f to B),
            across = curve(0f to B, 0.2f to -0.5f, 0.45f to 0.4f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -0.6f, 0.45f to -1.2f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.3f, 0.45f to 0.6f, 1f to B),
            turn = curve(0f to B, 0.2f to -0.3f, 0.45f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.45f to -2.5f, 0.8f to -2f, 1f to B),
            headA = curve(0f to B, 0.2f to -0.3f, 0.45f to -0.1f, 1f to B),
            lean = curve(0f to B, 0.45f to 0.3f, 1f to B),
        ),
        "SPELL:ARCANE_TRANSFER" to Pose(
            pitch = curve(0f to B, 0.3f to -1.4f, 0.6f to -1.6f, 1f to B),
            across = curve(0f to B, 0.3f to 0.3f, 0.6f to 0.4f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -1.4f, 0.6f to -1.6f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.8f, 0.6f to 0.9f, 1f to B),
            reach = curve(0f to B, 0.6f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.3f to -0.4f, 0.6f to -0.2f, 1f to B),
            lean = curve(0f to B, 0.6f to 0.3f, 1f to B),
        ),
        "SPELL:OPHANIM" to Pose(
            pitch = curve(0f to B, 0.3f to -2.3f, 0.8f to -2.2f, 1f to B),
            abduct = curve(0f to B, 0.3f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -1.5f, 0.8f to -1.5f, 1f to B),
            offAbduct = curve(0f to B, 0.3f to 0.6f, 1f to B),
            headA = curve(0f to B, 0.3f to -1.0f, 0.8f to -1.0f, 1f to B),
            headY = curve(0f to B, 0.3f to 1.0f, 0.8f to 5.5f, 1f to 6.2832f),
            lean = curve(0f to B, 0.3f to -0.15f, 1f to B),
        ),
        "SPELL:ARROW_STORM" to Pose(
            pitch = curve(0f to B, 0.15f to -1.5f, 0.85f to -1.5f, 1f to B),
            across = curve(0f to B, 0.15f to 0.25f, 0.85f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.15f to -1.5f, 0.85f to -1.5f, 1f to B),
            offAcross = curve(0f to B, 0.15f to 0.7f, 0.85f to 0.7f, 1f to B),
            turn = curve(0f to B, 0.15f to 0.35f, 0.85f to 0.35f, 1f to B),
            reach = curve(0f to B, 0.15f to -0.6f, 0.25f to 0.4f, 0.35f to -0.6f, 0.45f to 0.4f, 0.55f to -0.6f, 0.65f to 0.4f, 0.75f to -0.6f, 0.85f to -0.6f, 1f to B),
            headA = curve(0f to B, 0.15f to -0.5f, 0.85f to -0.5f, 1f to B),
            lean = curve(0f to B, 0.15f to -0.1f, 0.85f to -0.1f, 1f to B),
        ),
        "SPELL:ESCAPE" to Pose(
            pitch = curve(0f to B, 0.3f to -0.8f, 0.6f to -1.0f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -0.7f, 0.6f to -0.9f, 1f to B),
            turn = curve(0f to B, 0.3f to 0.4f, 0.6f to 0.2f, 1f to B),
            headA = curve(0f to B, 0.3f to 0.4f, 0.6f to 0.1f, 1f to B),
            lean = curve(0f to B, 0.3f to -0.5f, 0.6f to -0.2f, 1f to B),
            step = curve(0f to B, 0.3f to -0.8f, 0.6f to -0.4f, 1f to B),
        ),
        "SPELL:ARROW_BOMB" to Pose(
            pitch = curve(0f to B, 0.25f to -1.7f, 0.55f to -1.9f, 0.7f to -1.7f, 1f to B),
            across = curve(0f to B, 0.25f to 0.25f, 0.7f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -1.5f, 0.55f to -1.7f, 0.7f to -1.5f, 1f to B),
            offAcross = curve(0f to B, 0.25f to 0.7f, 0.55f to -0.1f, 0.7f to -0.4f, 1f to B),
            turn = curve(0f to B, 0.25f to 0.35f, 0.7f to 0.1f, 1f to B),
            reach = curve(0f to B, 0.25f to -0.6f, 0.55f to -0.6f, 1f to B),
            headA = curve(0f to B, 0.25f to -0.5f, 0.55f to -0.7f, 1f to B),
            lean = curve(0f to B, 0.55f to -0.2f, 1f to B),
        ),
        "SPELL:ARROW_SHIELD" to Pose(
            pitch = curve(0f to B, 0.3f to -1.2f, 0.6f to -1.4f, 1f to B),
            across = curve(0f to B, 0.3f to 0.8f, 0.6f to 0f, 1f to B),
            abduct = curve(0f to B, 0.6f to 0.5f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -1.2f, 0.6f to -1.4f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.8f, 0.6f to 0f, 1f to B),
            offAbduct = curve(0f to B, 0.6f to 0.5f, 1f to B),
            headA = curve(0f to B, 0.3f to -0.9f, 0.6f to -0.5f, 1f to B),
        ),
        "SPELL:PHANTOM_RAY" to Pose(
            pitch = curve(0f to B, 0.2f to -1.5f, 0.9f to -1.5f, 1f to B),
            across = curve(0f to B, 0.2f to 0.25f, 0.9f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -1.5f, 0.9f to -1.5f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.7f, 0.9f to 0.7f, 1f to B),
            turn = curve(0f to B, 0.2f to 0.35f, 0.9f to 0.35f, 1f to B),
            reach = curve(0f to B, 0.2f to -0.6f, 0.9f to -0.6f, 1f to B),
            lean = curve(0f to B, 0.3f to 0.15f, 0.9f to 0.15f, 1f to B),
            step = curve(0f to B, 0.3f to 0.2f, 0.9f to 0.2f, 1f to B),
        ),
        "SPELL:GRAPPLING_HOOK" to Pose(
            pitch = curve(0f to B, 0.2f to -1.5f, 0.45f to -1.5f, 0.8f to -1.2f, 1f to B),
            across = curve(0f to B, 0.2f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.2f to -1.5f, 0.45f to -1.3f, 1f to B),
            offAcross = curve(0f to B, 0.2f to 0.7f, 0.7f to 0.4f, 1f to B),
            turn = curve(0f to B, 0.2f to 0.3f, 0.7f to 0.2f, 1f to B),
            reach = curve(0f to B, 0.2f to -0.8f, 0.45f to -1.5f, 0.7f to 0.5f, 1f to B),
            lean = curve(0f to B, 0.45f to 0.1f, 0.7f to 0.4f, 1f to B),
            step = curve(0f to B, 0.7f to 0.6f, 1f to B),
        ),
        "SPELL:GUARDIAN_ANGELS" to Pose(
            pitch = curve(0f to B, 0.35f to -2.2f, 0.8f to -2.1f, 1f to B),
            abduct = curve(0f to B, 0.35f to 0.9f, 0.8f to 0.9f, 1f to B),
            offPitch = curve(0f to B, 0.35f to -2.2f, 0.8f to -2.1f, 1f to B),
            offAbduct = curve(0f to B, 0.35f to 0.9f, 0.8f to 0.9f, 1f to B),
            headA = curve(0f to B, 0.35f to -1.0f, 0.8f to -1.0f, 1f to B),
            lean = curve(0f to B, 0.35f to -0.2f, 0.8f to -0.2f, 1f to B),
        ),
        "SPELL:SPIN_ATTACK" to Pose(
            pitch = curve(0f to B, 0.12f to -1.2f, 0.85f to -1.2f, 1f to B),
            across = curve(0f to B, 0.12f to -0.6f, 0.5f to 0.4f, 0.85f to 0.2f, 1f to B),
            abduct = curve(0f to B, 0.12f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.12f to -0.6f, 0.85f to -0.6f, 1f to B),
            offAbduct = curve(0f to B, 0.12f to 0.6f, 0.85f to 0.6f, 1f to B),
            turn = curve(0f to B, 0.12f to -0.3f, 0.5f to 0.3f, 1f to B),
            headA = curve(0f to B, 0.12f to -0.2f, 0.85f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.12f to -0.6f, 0.5f to 3.2f, 0.85f to 6.0f, 1f to 6.2832f),
            twist = curve(0f to B, 0.12f to 0.3f, 0.5f to 0.6f, 0.85f to 0.3f, 1f to B),
            lean = curve(0f to B, 0.12f to 0.15f, 0.85f to 0.15f, 1f to B),
        ),
        "SPELL:DASH" to Pose(
            pitch = curve(0f to B, 0.3f to 0.3f, 0.6f to 0.1f, 1f to B),
            offPitch = curve(0f to B, 0.3f to 0.3f, 0.6f to 0.1f, 1f to B),
            headA = curve(0f to B, 0.3f to 0.6f, 0.6f to 0.3f, 1f to B),
            lean = curve(0f to B, 0.3f to 0.7f, 0.6f to 0.4f, 1f to B),
            step = curve(0f to B, 0.3f to 1.2f, 0.6f to 0.8f, 1f to B),
        ),
        "SPELL:MULTIHIT" to Pose(
            pitch = curve(0f to B, 0.1f to -1.2f, 0.9f to -1.2f, 1f to B),
            across = curve(0f to B, 0.15f to -0.8f, 0.3f to 0.8f, 0.45f to -0.8f, 0.6f to 0.8f, 0.75f to -0.5f, 1f to B),
            abduct = curve(0f to B, 0.1f to 0.25f, 1f to B),
            offPitch = curve(0f to B, 0.1f to -0.5f, 0.9f to -0.5f, 1f to B),
            turn = curve(0f to B, 0.15f to -0.4f, 0.3f to 0.4f, 0.45f to -0.4f, 0.6f to 0.4f, 0.75f to -0.2f, 1f to B),
            reach = curve(0f to B, 0.3f to -1.2f, 0.6f to -1.2f, 1f to B),
            headA = curve(0f to B, 0.1f to -0.2f, 0.9f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.15f to -0.8f, 0.3f to 0.8f, 0.45f to -0.8f, 0.6f to 0.8f, 0.75f to -0.4f, 1f to B),
            lean = curve(0f to B, 0.15f to 0.15f, 0.75f to 0.15f, 1f to B),
        ),
        "SPELL:SMOKE_BOMB" to Pose(
            pitch = curve(0f to B, 0.25f to -2.0f, 0.5f to -0.8f, 0.8f to -0.9f, 1f to B),
            across = curve(0f to B, 0.5f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -0.5f, 1f to B),
            turn = curve(0f to B, 0.25f to -0.3f, 0.5f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.5f to -1f, 1f to B),
            headA = curve(0f to B, 0.25f to -1.0f, 0.5f to 0.8f, 0.8f to 0.5f, 1f to B),
            lean = curve(0f to B, 0.25f to -0.2f, 0.5f to 0.6f, 0.8f to 0.4f, 1f to B),
        ),
        "SPELL:LACERATE" to Pose(
            pitch = curve(0f to B, 0.12f to -1.1f, 0.85f to -1.1f, 1f to B),
            across = curve(0f to B, 0.12f to -0.9f, 0.4f to 0.9f, 0.7f to -0.5f, 0.9f to 0.3f, 1f to B),
            abduct = curve(0f to B, 0.12f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.12f to -0.5f, 0.85f to -0.5f, 1f to B),
            offAbduct = curve(0f to B, 0.12f to 0.5f, 0.85f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.12f to -0.4f, 0.4f to 0.5f, 0.7f to -0.3f, 1f to B),
            headA = curve(0f to B, 0.12f to -0.2f, 0.85f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.12f to -0.9f, 0.4f to 3.0f, 0.7f to 5.0f, 1f to 6.2832f),
            twist = curve(0f to B, 0.12f to -0.6f, 0.4f to 0.8f, 1f to B),
            lean = curve(0f to B, 0.12f to 0.2f, 0.85f to 0.2f, 1f to B),
        ),
        "SPELL:BACKSTAB" to Pose(
            pitch = curve(0f to B, 0.25f to -0.9f, 0.5f to 0.2f, 0.8f to 0f, 1f to B),
            across = curve(0f to B, 0.25f to -0.3f, 0.5f to 0.3f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -0.6f, 0.5f to -0.4f, 1f to B),
            turn = curve(0f to B, 0.25f to 0.4f, 0.5f to 0.8f, 0.8f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.5f to 1.5f, 1f to B),
            headA = curve(0f to B, 0.25f to -0.5f, 0.5f to -0.3f, 1f to B),
            lean = curve(0f to B, 0.5f to 0.3f, 1f to B),
            step = curve(0f to B, 0.25f to -0.3f, 0.5f to 0.8f, 1f to B),
        ),
        "SPELL:BAMBOOZLE" to Pose(
            pitch = curve(0f to B, 0.15f to -0.9f, 0.4f to -0.8f, 0.7f to -0.7f, 1f to B),
            across = curve(0f to B, 0.15f to -1.0f, 0.4f to 1.0f, 0.7f to 0.5f, 1f to B),
            abduct = curve(0f to B, 0.15f to 0.4f, 0.4f to 0.2f, 1f to B),
            offPitch = curve(0f to B, 0.15f to -0.9f, 0.4f to -0.9f, 0.7f to -0.6f, 1f to B),
            offAcross = curve(0f to B, 0.15f to 1.0f, 0.4f to -0.7f, 0.7f to -0.3f, 1f to B),
            offAbduct = curve(0f to B, 0.15f to 0.4f, 0.4f to 0.3f, 1f to B),
            turn = curve(0f to B, 0.15f to -0.5f, 0.4f to 0.6f, 0.7f to 0.2f, 1f to B),
            headA = curve(0f to B, 0.15f to -0.2f, 0.4f to -0.2f, 1f to B),
            headY = curve(0f to B, 0.15f to -1.1f, 0.4f to 1.1f, 0.7f to 0.5f, 1f to B),
            twist = curve(0f to B, 0.15f to -0.8f, 0.4f to 1.0f, 0.7f to 0.4f, 1f to B),
        ),
        "SPELL:TOTEM" to Pose(
            pitch = curve(0f to B, 0.3f to -1.8f, 0.55f to -0.8f, 0.85f to -0.9f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -1.5f, 0.55f to -0.9f, 0.85f to -0.9f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.5f, 0.55f to 0.5f, 1f to B),
            reach = curve(0f to B, 0.55f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.3f to -0.6f, 0.55f to 0.8f, 0.85f to 0.6f, 1f to B),
            lean = curve(0f to B, 0.3f to -0.1f, 0.55f to 0.7f, 0.85f to 0.5f, 1f to B),
            step = curve(0f to B, 0.55f to 0.5f, 1f to B),
        ),
        "SPELL:HAUL" to Pose(
            pitch = curve(0f to B, 0.25f to -1.5f, 0.5f to -1.0f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -1.5f, 0.6f to -1.0f, 1f to B),
            offAcross = curve(0f to B, 0.25f to 0.3f, 0.6f to 0.7f, 1f to B),
            reach = curve(0f to B, 0.25f to -2.5f, 0.6f to 1.0f, 1f to B),
            headA = curve(0f to B, 0.25f to -0.2f, 0.6f to -0.4f, 1f to B),
            lean = curve(0f to B, 0.25f to 0.3f, 0.6f to -0.3f, 1f to B),
            step = curve(0f to B, 0.25f to 0.5f, 0.6f to -0.4f, 1f to B),
        ),
        "SPELL:AURA" to Pose(
            pitch = curve(0f to B, 0.3f to -2.0f, 0.5f to -1.9f, 0.7f to -2.0f, 1f to B),
            abduct = curve(0f to B, 0.3f to 0.7f, 0.5f to 0.5f, 0.7f to 0.7f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -2.0f, 0.5f to -1.9f, 0.7f to -2.0f, 1f to B),
            offAbduct = curve(0f to B, 0.3f to 0.7f, 0.5f to 0.5f, 0.7f to 0.7f, 1f to B),
            headA = curve(0f to B, 0.3f to -1.2f, 0.5f to -1.0f, 0.7f to -1.2f, 1f to B),
            lean = curve(0f to B, 0.3f to -0.15f, 0.7f to -0.15f, 1f to B),
        ),
        "SPELL:UPROOT" to Pose(
            pitch = curve(0f to B, 0.3f to -0.3f, 0.6f to -2.2f, 0.85f to -1.8f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -0.4f, 0.6f to -1.9f, 0.85f to -1.6f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.4f, 0.6f to 0.4f, 1f to B),
            reach = curve(0f to B, 0.6f to -1f, 1f to B),
            headA = curve(0f to B, 0.3f to 0.9f, 0.6f to -1.5f, 0.85f to -1.0f, 1f to B),
            lean = curve(0f to B, 0.3f to 0.5f, 0.6f to -0.2f, 1f to B),
        ),
        "SPELL:SWITCH_MASKS" to Pose(
            pitch = curve(0f to B, 0.3f to -2.4f, 0.65f to -2.4f, 1f to B),
            across = curve(0f to B, 0.3f to 0.9f, 0.65f to 0.9f, 1f to B),
            offPitch = curve(0f to B, 0.3f to -2.0f, 0.65f to -2.0f, 1f to B),
            offAcross = curve(0f to B, 0.3f to 0.5f, 1f to B),
            headA = curve(0f to B, 0.3f to -1.2f, 0.65f to -1.2f, 1f to B),
            lean = curve(0f to B, 0.3f to -0.1f, 0.65f to -0.1f, 1f to B),
        ),
        "SPELL:CAST" to Pose(
            pitch = curve(0f to B, 0.25f to -1.8f, 0.5f to -1.5f, 0.8f to -1.4f, 1f to B),
            offPitch = curve(0f to B, 0.25f to -1.4f, 0.5f to -1.2f, 1f to B),
            offAcross = curve(0f to B, 0.25f to 0.4f, 0.5f to 0.5f, 1f to B),
            turn = curve(0f to B, 0.25f to -0.2f, 0.5f to 0.2f, 1f to B),
            reach = curve(0f to B, 0.25f to 0.5f, 0.5f to -2f, 0.8f to -1.5f, 1f to B),
            headA = curve(0f to B, 0.25f to -0.9f, 0.5f to -0.2f, 1f to B),
            lean = curve(0f to B, 0.5f to 0.3f, 1f to B),
        ),
    )

    private val SECONDS = mapOf(
        "SPEAR" to 0.3f,
        "SPEAR:SWEEP" to 0.36f,
        "SPEAR:SCYTHE" to 0.38f,
        "SPEAR:SLAM" to 0.42f,
        "SPEAR:TWIRL" to 0.44f,
        "DAGGER" to 0.26f,
        "DAGGER:STAB" to 0.24f,
        "DAGGER:WHIRL" to 0.36f,
        "WAND" to 0.3f,
        "WAND:STAFF" to 0.36f,
        "RELIK" to 0.34f,
        "BOW" to 0.4f,
        "BOW:FIREARM" to 0.34f,
        "DAGGER:UPPER" to 0.28f,
        "DAGGER:CROSS" to 0.3f,
        "WAND:FLICK" to 0.26f,
        "WAND:ORBIT" to 0.4f,
        "WAND:STAFF_SWEEP" to 0.38f,
        "WAND:STAFF_RAISE" to 0.4f,
        "RELIK:PULSE" to 0.36f,
        "RELIK:WAVE" to 0.4f,
        "SPELL:BASH" to 0.75f,
        "SPELL:CHARGE" to 0.7f,
        "SPELL:UPPERCUT" to 0.7f,
        "SPELL:WAR_SCREAM" to 0.9f,
        "SPELL:HEAL" to 0.9f,
        "SPELL:TELEPORT" to 0.55f,
        "SPELL:METEOR" to 0.9f,
        "SPELL:ICE_SNAKE" to 0.65f,
        "SPELL:ARCANE_TRANSFER" to 0.8f,
        "SPELL:OPHANIM" to 1.0f,
        "SPELL:ARROW_STORM" to 0.9f,
        "SPELL:ESCAPE" to 0.6f,
        "SPELL:ARROW_BOMB" to 0.8f,
        "SPELL:ARROW_SHIELD" to 0.7f,
        "SPELL:PHANTOM_RAY" to 1.0f,
        "SPELL:GRAPPLING_HOOK" to 0.8f,
        "SPELL:GUARDIAN_ANGELS" to 0.9f,
        "SPELL:SPIN_ATTACK" to 0.7f,
        "SPELL:DASH" to 0.55f,
        "SPELL:MULTIHIT" to 0.8f,
        "SPELL:SMOKE_BOMB" to 0.65f,
        "SPELL:LACERATE" to 0.8f,
        "SPELL:BACKSTAB" to 0.6f,
        "SPELL:BAMBOOZLE" to 0.7f,
        "SPELL:TOTEM" to 0.8f,
        "SPELL:HAUL" to 0.75f,
        "SPELL:AURA" to 0.9f,
        "SPELL:UPROOT" to 0.8f,
        "SPELL:SWITCH_MASKS" to 0.8f,
        "SPELL:CAST" to 0.7f,
    )

    private val HITS = mapOf(
        "SPEAR" to 0.3f,
        "SPEAR:SWEEP" to 0.42f,
        "SPEAR:SCYTHE" to 0.42f,
        "SPEAR:SLAM" to 0.38f,
        "SPEAR:TWIRL" to 0.5f,
        "DAGGER" to 0.28f,
        "DAGGER:STAB" to 0.2f,
        "DAGGER:WHIRL" to 0.38f,
        "WAND" to 0.3f,
        "WAND:STAFF" to 0.32f,
        "RELIK" to 0.2f,
        "BOW" to 0.84f,
        "BOW:FIREARM" to 0.2f,
        "DAGGER:UPPER" to 0.28f,
        "DAGGER:CROSS" to 0.26f,
        "WAND:FLICK" to 0.26f,
        "WAND:ORBIT" to 0.42f,
        "WAND:STAFF_SWEEP" to 0.42f,
        "WAND:STAFF_RAISE" to 0.4f,
        "RELIK:PULSE" to 0.3f,
        "RELIK:WAVE" to 0.55f,
        "SPELL:BASH" to 0.5f,
        "SPELL:CHARGE" to 0.55f,
        "SPELL:UPPERCUT" to 0.5f,
        "SPELL:WAR_SCREAM" to 0.3f,
        "SPELL:HEAL" to 0.35f,
        "SPELL:TELEPORT" to 0.4f,
        "SPELL:METEOR" to 0.6f,
        "SPELL:ICE_SNAKE" to 0.45f,
        "SPELL:ARCANE_TRANSFER" to 0.6f,
        "SPELL:OPHANIM" to 0.3f,
        "SPELL:ARROW_STORM" to 0.15f,
        "SPELL:ESCAPE" to 0.3f,
        "SPELL:ARROW_BOMB" to 0.55f,
        "SPELL:ARROW_SHIELD" to 0.6f,
        "SPELL:PHANTOM_RAY" to 0.2f,
        "SPELL:GRAPPLING_HOOK" to 0.45f,
        "SPELL:GUARDIAN_ANGELS" to 0.35f,
        "SPELL:SPIN_ATTACK" to 0.5f,
        "SPELL:DASH" to 0.3f,
        "SPELL:MULTIHIT" to 0.3f,
        "SPELL:SMOKE_BOMB" to 0.5f,
        "SPELL:LACERATE" to 0.4f,
        "SPELL:BACKSTAB" to 0.5f,
        "SPELL:BAMBOOZLE" to 0.4f,
        "SPELL:TOTEM" to 0.55f,
        "SPELL:HAUL" to 0.25f,
        "SPELL:AURA" to 0.3f,
        "SPELL:UPROOT" to 0.6f,
        "SPELL:SWITCH_MASKS" to 0.3f,
        "SPELL:CAST" to 0.5f,
    )

    private val BODY_MOTION: Map<String, Pair<Curve, Curve>> = mapOf(
        "SPEAR" to (curve(0f to B, 0.14f to -0.12f, 0.3f to 0.3f, 0.5f to 0.22f, 1f to B) to curve(0f to B, 0.14f to -0.15f, 0.3f to 1f, 0.5f to 0.8f, 1f to B)),
        "SPEAR:SWEEP" to (curve(0f to B, 0.16f to -0.1f, 0.42f to 0.2f, 0.72f to 0.12f, 1f to B) to curve(0f to B, 0.16f to -0.1f, 0.42f to 0.6f, 0.72f to 0.4f, 1f to B)),
        "SPEAR:SCYTHE" to (curve(0f to B, 0.2f to -0.25f, 0.42f to 0.25f, 0.72f to 0.15f, 1f to B) to curve(0f to B, 0.2f to -0.1f, 0.42f to 0.5f, 0.72f to 0.3f, 1f to B)),
        "SPEAR:TWIRL" to (curve(0f to B, 0.14f to -0.05f, 0.5f to 0.1f, 0.86f to 0.1f, 1f to B) to curve(0f to B, 0.5f to 0.4f, 0.86f to 0.3f, 1f to B)),
        "SPEAR:SLAM" to (curve(0f to B, 0.22f to -0.4f, 0.38f to 0.7f, 0.55f to 0.6f, 1f to B) to curve(0f to B, 0.22f to -0.2f, 0.38f to 0.9f, 0.55f to 0.8f, 1f to B)),
        "DAGGER" to (curve(0f to B, 0.1f to -0.1f, 0.28f to 0.2f, 0.55f to 0.1f, 1f to B) to curve(0f to B, 0.1f to -0.1f, 0.28f to 0.5f, 0.55f to 0.3f, 1f to B)),
        "DAGGER:STAB" to (curve(0f to B, 0.08f to -0.1f, 0.2f to 0.4f, 0.42f to 0.3f, 1f to B) to curve(0f to B, 0.08f to -0.2f, 0.2f to 1f, 0.42f to 0.8f, 1f to B)),
        "DAGGER:WHIRL" to (curve(0f to B, 0.12f to 0.1f, 0.7f to 0.1f, 1f to B) to KEEP),
        "WAND" to (curve(0f to B, 0.14f to -0.1f, 0.3f to 0.15f, 0.6f to 0.1f, 1f to B) to curve(0f to B, 0.3f to 0.3f, 0.6f to 0.2f, 1f to B)),
        "WAND:STAFF" to (curve(0f to B, 0.14f to -0.3f, 0.32f to 0.5f, 0.6f to 0.4f, 1f to B) to curve(0f to B, 0.14f to -0.2f, 0.32f to 0.8f, 0.6f to 0.7f, 1f to B)),
        "RELIK" to (curve(0f to B, 0.2f to -0.05f, 0.5f to 0.1f, 0.75f to 0.05f, 1f to B) to KEEP),
        "BOW" to (curve(0f to B, 0.16f to -0.08f, 0.7f to -0.12f, 0.84f to 0.05f, 1f to B) to curve(0f to B, 0.16f to 0.6f, 0.7f to 0.6f, 0.84f to 0.3f, 1f to B)),
        "BOW:FIREARM" to (curve(0f to B, 0.1f to -0.03f, 0.2f to -0.3f, 0.4f to -0.05f, 0.8f to -0.03f, 1f to B) to KEEP),
    )

    init {
        BODY_MOTION.forEach { (key, motion) ->
            POSES[key]?.curves?.let {
                it[LEAN] = motion.first
                it[STEP] = motion.second
            }
        }
        POSES["SPEAR:TWIRL"]?.spin = true
        listOf("SPELL:SPIN_ATTACK", "SPELL:LACERATE", "SPELL:OPHANIM").forEach { POSES[it]?.spin = true }
        POSES.forEach { (key, value) -> if (!key.startsWith("BOW") && !key.startsWith("SPELL:")) value.curves[TURN] = value.curves[TURN].scaled(TURN_BOOST) }
        SECONDS.forEach { (key, value) -> POSES[key]?.seconds = value }
        HITS.forEach { (key, value) -> POSES[key]?.hit = value }
        POSES.forEach { (key, value) -> value.key = key }
        val gripOn = curve(0f to B, 0.12f to 1f, 0.65f to 1f, 1f to B)
        listOf("SPELL:BASH", "SPELL:CHARGE", "SPELL:UPPERCUT", "SPELL:WAR_SCREAM", "SPEAR", "SPEAR:SWEEP", "SPEAR:SCYTHE", "SPEAR:SLAM", "SPEAR:TWIRL", "WAND:STAFF", "WAND:STAFF_SWEEP", "WAND:STAFF_RAISE", "BOW:FIREARM").forEach { key ->
            POSES[key]?.curves?.set(GRIP, gripOn)
        }
        POSES["BOW:FIREARM"]?.gripOffset = 9f
    }

    private class ComboStep(val key: String, val mirror: Boolean = false, val finisher: Boolean = false)

    private fun step(key: String, mirror: Boolean = false, finisher: Boolean = false) = ComboStep(key, mirror, finisher)

    private val COMBOS: Map<String, List<ComboStep>> = mapOf(
        "SPEAR" to listOf(step("SPEAR"), step("SPEAR:SWEEP"), step("SPEAR", true), step("SPEAR:SLAM", finisher = true)),
        "SPEAR:SWEEP" to listOf(step("SPEAR:SWEEP"), step("SPEAR:SWEEP", true), step("SPEAR"), step("SPEAR:TWIRL", finisher = true)),
        "SPEAR:SCYTHE" to listOf(step("SPEAR:SCYTHE"), step("SPEAR:SCYTHE", true), step("SPEAR:SLAM"), step("SPEAR:TWIRL", finisher = true)),
        "DAGGER" to listOf(step("DAGGER"), step("DAGGER", true), step("DAGGER:UPPER"), step("DAGGER:WHIRL", finisher = true)),
        "DAGGER:STAB" to listOf(step("DAGGER:STAB"), step("DAGGER:STAB", true), step("DAGGER:CROSS"), step("DAGGER:UPPER", finisher = true)),
        "DAGGER:WHIRL" to listOf(step("DAGGER:WHIRL"), step("DAGGER:CROSS"), step("DAGGER:UPPER", true), step("DAGGER:WHIRL", true, finisher = true)),
        "WAND" to listOf(step("WAND:FLICK"), step("WAND"), step("WAND:FLICK", true), step("WAND:ORBIT", finisher = true)),
        "WAND:STAFF" to listOf(step("WAND:STAFF"), step("WAND:STAFF_SWEEP"), step("WAND:STAFF_SWEEP", true), step("WAND:STAFF_RAISE", finisher = true)),
        "RELIK" to listOf(step("RELIK"), step("RELIK:PULSE"), step("RELIK", true), step("RELIK:WAVE", finisher = true)),
    )

    private val mirrorCache = HashMap<String, Pose>()

    private fun stepPose(step: ComboStep): Pose? {
        val base = POSES[step.key] ?: return null
        if (!step.mirror) return base
        return mirrorCache.getOrPut(step.key) { base.mirrored().also { it.key = base.key } }
    }

    private class Cue(val at: Float, val sound: SoundEvent, val pitch: Float, val volume: Float)

    private fun cue(at: Float, sound: SoundEvent, pitch: Float, volume: Float) = Cue(at, sound, pitch, volume)

    private val SFX: Map<String, List<Cue>> by lazy {
        mapOf(
            "SPEAR" to listOf(cue(0.1f, SoundEvents.SPEAR_ATTACK.value(), 1.2f, 0.55f)),
            "SPEAR:SWEEP" to listOf(cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f)),
            "SPEAR:SCYTHE" to listOf(cue(0.12f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.8f)),
            "SPEAR:SLAM" to listOf(cue(0.15f, SoundEvents.MACE_SMASH_AIR, 1.0f, 0.5f), cue(0.38f, SoundEvents.MACE_SMASH_GROUND, 1.1f, 0.5f)),
            "SPEAR:TWIRL" to listOf(
                cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f),
                cue(0.5f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f),
                cue(0.8f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.35f, 0.5f),
            ),
            "DAGGER" to listOf(cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f)),
            "DAGGER:STAB" to listOf(cue(0.08f, SoundEvents.SPEAR_ATTACK.value(), 1.7f, 0.45f)),
            "DAGGER:WHIRL" to listOf(cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f), cue(0.5f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f)),
            "WAND" to listOf(cue(0.14f, SoundEvents.WIND_CHARGE_THROW, 1.6f, 0.35f), cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.7f, 0.35f)),
            "WAND:STAFF" to listOf(cue(0.2f, SoundEvents.MACE_SMASH_GROUND, 1.5f, 0.35f), cue(0.32f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.4f)),
            "RELIK" to listOf(cue(0.15f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.55f), cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.35f)),
            "DAGGER:UPPER" to listOf(cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.7f, 0.5f)),
            "DAGGER:CROSS" to listOf(cue(0.1f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f), cue(0.26f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.45f)),
            "WAND:FLICK" to listOf(cue(0.12f, SoundEvents.WIND_CHARGE_THROW, 1.9f, 0.3f)),
            "WAND:ORBIT" to listOf(cue(0.15f, SoundEvents.WIND_CHARGE_THROW, 1.3f, 0.35f), cue(0.42f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.4f)),
            "WAND:STAFF_SWEEP" to listOf(cue(0.18f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.55f)),
            "WAND:STAFF_RAISE" to listOf(cue(0.22f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.45f), cue(0.4f, SoundEvents.MACE_SMASH_GROUND, 1.3f, 0.4f)),
            "RELIK:PULSE" to listOf(cue(0.12f, SoundEvents.WIND_CHARGE_THROW, 1.0f, 0.4f), cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.35f)),
            "RELIK:WAVE" to listOf(cue(0.22f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.55f), cue(0.55f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1f, 0.4f)),
            "BOW" to listOf(cue(0.84f, SoundEvents.CROSSBOW_SHOOT, 1.4f, 0.45f)),
            "BOW:FIREARM" to listOf(cue(0.18f, SoundEvents.CROSSBOW_SHOOT, 0.85f, 0.5f)),
            "SPELL:BASH" to listOf(cue(0.3f, SoundEvents.MACE_SMASH_AIR, 0.9f, 0.5f), cue(0.5f, SoundEvents.MACE_SMASH_GROUND, 0.9f, 0.6f)),
            "SPELL:CHARGE" to listOf(cue(0.35f, SoundEvents.WIND_CHARGE_THROW, 0.8f, 0.6f)),
            "SPELL:UPPERCUT" to listOf(cue(0.4f, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f)),
            "SPELL:WAR_SCREAM" to listOf(cue(0.3f, SoundEvents.RAVAGER_ROAR, 1.1f, 0.35f)),
            "SPELL:HEAL" to listOf(cue(0.35f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f)),
            "SPELL:TELEPORT" to listOf(cue(0.4f, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.4f)),
            "SPELL:METEOR" to listOf(cue(0.6f, SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f)),
            "SPELL:ICE_SNAKE" to listOf(cue(0.45f, SoundEvents.WIND_CHARGE_THROW, 1.3f, 0.5f)),
            "SPELL:ARCANE_TRANSFER" to listOf(cue(0.5f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f)),
            "SPELL:OPHANIM" to listOf(cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.5f)),
            "SPELL:ARROW_STORM" to listOf(cue(0.2f, SoundEvents.CROSSBOW_SHOOT, 1.6f, 0.35f), cue(0.4f, SoundEvents.CROSSBOW_SHOOT, 1.5f, 0.35f), cue(0.6f, SoundEvents.CROSSBOW_SHOOT, 1.4f, 0.35f)),
            "SPELL:ESCAPE" to listOf(cue(0.3f, SoundEvents.WIND_CHARGE_THROW, 1.4f, 0.5f)),
            "SPELL:ARROW_BOMB" to listOf(cue(0.55f, SoundEvents.CROSSBOW_SHOOT, 0.9f, 0.5f)),
            "SPELL:ARROW_SHIELD" to listOf(cue(0.5f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.45f)),
            "SPELL:PHANTOM_RAY" to listOf(cue(0.2f, SoundEvents.CROSSBOW_SHOOT, 1.2f, 0.4f)),
            "SPELL:GRAPPLING_HOOK" to listOf(cue(0.2f, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.4f)),
            "SPELL:GUARDIAN_ANGELS" to listOf(cue(0.35f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.45f)),
            "SPELL:SPIN_ATTACK" to listOf(cue(0.15f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f), cue(0.5f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f)),
            "SPELL:DASH" to listOf(cue(0.25f, SoundEvents.WIND_CHARGE_THROW, 1.5f, 0.5f)),
            "SPELL:MULTIHIT" to listOf(cue(0.15f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f), cue(0.3f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.7f, 0.4f), cue(0.45f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6f, 0.4f), cue(0.6f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.7f, 0.4f)),
            "SPELL:SMOKE_BOMB" to listOf(cue(0.5f, SoundEvents.FIRECHARGE_USE, 1.3f, 0.4f)),
            "SPELL:LACERATE" to listOf(cue(0.15f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.6f), cue(0.6f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f)),
            "SPELL:BACKSTAB" to listOf(cue(0.5f, SoundEvents.SPEAR_ATTACK.value(), 1.6f, 0.5f)),
            "SPELL:BAMBOOZLE" to listOf(cue(0.15f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f), cue(0.4f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.5f)),
            "SPELL:TOTEM" to listOf(cue(0.55f, SoundEvents.MACE_SMASH_GROUND, 1.2f, 0.5f)),
            "SPELL:HAUL" to listOf(cue(0.25f, SoundEvents.WIND_CHARGE_THROW, 0.9f, 0.5f)),
            "SPELL:AURA" to listOf(cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.5f)),
            "SPELL:UPROOT" to listOf(cue(0.6f, SoundEvents.MACE_SMASH_GROUND, 0.9f, 0.45f)),
            "SPELL:SWITCH_MASKS" to listOf(cue(0.3f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.4f)),
            "SPELL:CAST" to listOf(cue(0.5f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.4f)),
        )
    }

    private val FINISHER_CUE: Cue by lazy { cue(0f, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 0.5f) }

    private val TRAIL_SPANS = mapOf(
        "SPEAR" to (-6f to 32f),
        "SPEAR:SCYTHE" to (-6f to 34f),
        "WAND:STAFF" to (-6f to 32f),
        "WAND:STAFF_SWEEP" to (-6f to 32f),
        "WAND:STAFF_RAISE" to (-6f to 32f),
        "DAGGER" to (-2f to 12f),
        "WAND" to (-3f to 16f),
        "RELIK" to (-3f to 18f),
    )

    private fun trailSpan(key: String): Pair<Float, Float>? = TRAIL_SPANS[key] ?: TRAIL_SPANS[key.substringBefore(':')]

    private val LABELS = mapOf(
        "SPEAR" to "Spear: Thrust",
        "SPEAR:SWEEP" to "Spear: Sweep",
        "SPEAR:SCYTHE" to "Spear: Scythe reap",
        "SPEAR:SLAM" to "Spear: Overhead slam",
        "SPEAR:TWIRL" to "Spear: Twirl",
        "DAGGER" to "Dagger: Slash",
        "DAGGER:STAB" to "Dagger: Stab",
        "DAGGER:WHIRL" to "Dagger: Whirl",
        "WAND" to "Wand: Cast",
        "WAND:STAFF" to "Wand: Staff plant",
        "RELIK" to "Relik: Sweep",
        "DAGGER:UPPER" to "Dagger: Rising slash",
        "DAGGER:CROSS" to "Dagger: Cross slash",
        "WAND:FLICK" to "Wand: Flick",
        "WAND:ORBIT" to "Wand: Orbit",
        "WAND:STAFF_SWEEP" to "Wand: Staff sweep",
        "WAND:STAFF_RAISE" to "Wand: Staff raise",
        "RELIK:PULSE" to "Relik: Pulse",
        "RELIK:WAVE" to "Relik: Wave",
        "BOW" to "Bow: Draw and release",
        "BOW:FIREARM" to "Bow: Firearm recoil",
    )

    private val CLASS_KINDS = mapOf(
        "warrior" to GearSlotKind.SPEAR,
        "knight" to GearSlotKind.SPEAR,
        "assassin" to GearSlotKind.DAGGER,
        "ninja" to GearSlotKind.DAGGER,
        "mage" to GearSlotKind.WAND,
        "darkwizard" to GearSlotKind.WAND,
        "archer" to GearSlotKind.BOW,
        "hunter" to GearSlotKind.BOW,
        "shaman" to GearSlotKind.RELIK,
        "skyseer" to GearSlotKind.RELIK,
    )

    private val CLASS_NAME = Regex("(warrior|knight|assassin|ninja|mage|dark ?wizard|archer|hunter|shaman|skyseer)", RegexOption.IGNORE_CASE)


    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private object SwingClock {
        var pose: Pose? = null
        var right = true
        var suppress = false
        private var previewing = false
        private var previewStack: ItemStack? = null
        private var previewCached: Pose? = null
        private var startNanos = 0L
        private var totalSeconds = 1f
        private var startP = 0f
        private var blendSeconds = BLEND_SECONDS
        private var hitSeconds = 0.09f
        private var amp = 1f
        private var carry: FloatArray? = null
        private var lastBeginNanos = 0L
        private var comboKey: String? = null
        private var comboIndex = 0
        private var cues: List<Cue> = emptyList()
        private var cueFired = BooleanArray(0)
        private var cueFinisher = false
        var trailStrength = 1f
        private val raw = FloatArray(CHANNELS)
        private val idleCur = FloatArray(CHANNELS)
        private var idleTable: Stance? = null
        private var idleAmt = 0f
        private var idleLast = 0L
        private var readyUntil = 0L
        private var idleStack: ItemStack? = null
        private var idleStackKey: String? = null
        var idleRight = true

        fun blendIdle(base: FloatArray) {
            val amt = idleAmt
            if (amt <= 0.0001f) return
            for (i in 0 until CHANNELS) base[i] += (idleCur[i] - base[i]) * amt
        }

        fun idleGripOffset(): Float? = idleTable?.takeIf { idleAmt > 0.5f && it.ready[GRIP] > 0f }?.gripOffset

        fun idleActive(): Boolean = idleAmt > 0.01f

        fun breath(): Float {
            val phase = (System.nanoTime() % BREATH_NANOS).toDouble() / BREATH_NANOS
            return (sin(phase * 2.0 * Math.PI) * BREATH_AMP * idleAmt).toFloat()
        }

        fun idleStep() {
            val now = System.nanoTime()
            val dt = if (idleLast == 0L) 0f else ((now - idleLast) / 1_000_000_000f).coerceIn(0f, 0.1f)
            idleLast = now
            val a = 1f - exp(-dt / IDLE_TAU)
            val table = idleTable
            if (table != null) {
                val r = if (now < readyUntil) 1f else 0f
                val snap = idleAmt < 0.01f
                for (i in 0 until CHANNELS) {
                    val goal = table.carry[i] + (table.ready[i] - table.carry[i]) * r
                    idleCur[i] = if (snap) goal else idleCur[i] + (goal - idleCur[i]) * a
                }
            }
            idleAmt += ((if (table == null) 0f else 1f) - idleAmt) * a
        }

        private fun refreshIdle(player: LocalPlayer) {
            idleRight = player.mainArm == HumanoidArm.RIGHT
            if (!enabled() || !OverwatchConfig.current.weaponIdleEnabled || player.isUsingItem) {
                idleTable = null
                return
            }
            val stack = player.mainHandItem
            if (stack !== idleStack) {
                idleStack = stack
                idleStackKey = resolveKey(stack)
            }
            idleTable = stanceFor(idleStackKey)
        }

        fun tick(client: Minecraft) {
            val player = client.player
            if (player == null) {
                pose = null
                carry = null
                suppress = false
                idleTable = null
                return
            }
            if (!player.swinging) suppress = false
            refreshIdle(player)
            val config = OverwatchConfig.current
            if (config.weaponAnimationPreview && enabled()) {
                val stack = player.mainHandItem
                pose = if (stack.isEmpty) null else previewPose(stack)
                right = player.mainArm == HumanoidArm.RIGHT
                carry = null
                amp = 1f
                previewing = true
                if (debugLine.isNotEmpty()) player.sendOverlayMessage(Component.literal(debugLine))
                return
            }
            if (previewing) {
                previewing = false
                pose = null
            }
        }

        fun begin(player: LocalPlayer) {
            if (!enabled()) {
                pose = null
                carry = null
                suppress = false
                return
            }
            if (SpellComboGuard.isSuspended() || (pose?.key?.startsWith("SPELL:") == true && computeProgress() >= 0f)) {
                suppress = true
                return
            }
            val hand = player.swingingArm ?: InteractionHand.MAIN_HAND
            val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
            val stack = player.getItemInHand(hand)
            val styleKey = resolveKey(stack)
            val styleBase = styleKey?.let { POSES[it] }
            if (styleKey == null || styleBase == null) {
                pose = null
                carry = null
                suppress = false
                return
            }
            val now = System.nanoTime()
            val gap = if (lastBeginNanos == 0L) Float.MAX_VALUE else (now - lastBeginNanos) / 1_000_000_000f
            val aps = WynnWeapons.attacksPerSecond(stack)
            val minGap = if (aps != null) (MIN_GAP_FRACTION / aps.toFloat()).coerceIn(MIN_GAP_LOW, MIN_GAP_HIGH) else FALLBACK_GAP
            suppress = true
            readyUntil = now + READY_NANOS
            if (gap < minGap) return
            val config = OverwatchConfig.current
            val combo = advanceCombo(styleKey, if (config.weaponAnimationCombo) COMBOS[styleKey] else null, gap)
            val standard = stepPose(combo) ?: styleBase
            val chosen = standard
            cues = buildList {
                addAll(SFX[standard.key] ?: emptyList())
                if (combo.finisher) add(Cue(chosen.hit, FINISHER_CUE.sound, FINISHER_CUE.pitch, FINISHER_CUE.volume))
            }
            cueFired = BooleanArray(cues.size)
            cueFinisher = combo.finisher
            trailStrength = if (combo.finisher) 1.25f else 1f
            WeaponTrail.setWeapon(stack)
            val previous = pose
            val previousT = progress()
            carry = if (previous != null && previousT >= 0f) {
                val relax = relaxWeight()
                FloatArray(CHANNELS).also { out ->
                    sampleRaw(previous, previousT, REST_BASE, out)
                    for (i in 0 until CHANNELS) out[i] = REST_BASE[i] + (out[i] - REST_BASE[i]) * relax
                }
            } else {
                null
            }
            val chained = carry != null
            pose = chosen
            right = arm == HumanoidArm.RIGHT
            startNanos = now
            startP = if (chained) chosen.hit * CHAIN_SKIP else 0f
            lastBeginNanos = now
            val total = minOf(chosen.seconds, maxOf(MIN_SECONDS, gap * GAP_FRACTION))
            totalSeconds = total
            hitSeconds = minOf(HIT_SECONDS, total * HIT_SHARE)
            blendSeconds = if (chained) hitSeconds else BLEND_SECONDS
            val span = chosen.seconds - MIN_SECONDS
            amp = if (span <= 0f) 1f else AMP_MIN + (1f - AMP_MIN) * ((total - MIN_SECONDS) / span).coerceIn(0f, 1f)
        }

        fun beginSpell(player: LocalPlayer, chosen: Pose) {
            if (!enabled()) return
            val now = System.nanoTime()
            readyUntil = now + READY_NANOS
            cues = SFX[chosen.key] ?: emptyList()
            cueFired = BooleanArray(cues.size)
            cueFinisher = false
            trailStrength = 1f
            val previous = pose
            val previousT = progress()
            carry = if (previous != null && previousT >= 0f) {
                FloatArray(CHANNELS).also { out ->
                    sampleRaw(previous, previousT, REST_BASE, out)
                    for (i in 0 until CHANNELS) out[i] = REST_BASE[i] + (out[i] - REST_BASE[i]) * relaxWeight()
                }
            } else {
                null
            }
            pose = chosen
            right = player.mainArm == HumanoidArm.RIGHT
            startNanos = now
            startP = 0f
            lastBeginNanos = now
            totalSeconds = chosen.seconds
            hitSeconds = chosen.seconds * chosen.hit
            blendSeconds = if (carry != null) SPELL_BLEND_SECONDS else BLEND_SECONDS
            amp = 1f
        }

        private fun advanceCombo(key: String, steps: List<ComboStep>?, gap: Float): ComboStep {
            if (steps == null) {
                comboKey = null
                comboIndex = 0
                return ComboStep(key)
            }
            if (key != comboKey || gap > COMBO_RESET_SECONDS) {
                comboKey = key
                comboIndex = 0
            } else {
                comboIndex = (comboIndex + 1) % steps.size
            }
            return steps[comboIndex]
        }

        private fun fireCues(t: Float) {
            if (cues.isEmpty()) return
            val config = OverwatchConfig.current
            val client = Minecraft.getInstance()
            val player = client.player
            val level = client.level
            for (i in cues.indices) {
                if (cueFired[i] || t < cues[i].at) continue
                cueFired[i] = true
                if (!config.weaponAnimationSfx || player == null || level == null) continue
                val cue = cues[i]
                val jitter = 0.97f + ThreadLocalRandom.current().nextFloat() * 0.06f
                val pitch = cue.pitch * (1f + comboIndex * COMBO_PITCH_STEP) * jitter
                val volume = cue.volume * config.weaponAnimationSfxVolume.toFloat() * (if (cueFinisher) 1.15f else 1f)
                level.playLocalSound(player.x, player.y, player.z, cue.sound, SoundSource.PLAYERS, volume, pitch, false)
            }
        }

        private fun relaxWeight(): Float {
            if (previewing) return 1f
            val extra = elapsedSeconds() - totalSeconds - HOLD_SECONDS
            return if (extra <= 0f) 1f else 1f - smooth(extra / RELAX_SECONDS)
        }

        private fun elapsedSeconds(): Float = (System.nanoTime() - startNanos) / 1_000_000_000f

        private fun previewPose(stack: ItemStack): Pose? {
            if (stack !== previewStack) {
                previewStack = stack
                val assigned = WeaponAnimationRegistry.styleFor(stack)
                previewCached = if (assigned != null) POSES[assigned] else autoStyleKey(stack)?.let { POSES[it] }
            }
            return previewCached
        }

        fun progress(): Float {
            val t = computeProgress()
            if (t >= 0f && !previewing) fireCues(t)
            return t
        }

        private fun computeProgress(): Float {
            if (pose == null) return -1f
            if (previewing) return OverwatchConfig.current.weaponAnimationPreviewT.toFloat().coerceIn(0f, 0.999f)
            val current = pose ?: return -1f
            val tau = elapsedSeconds()
            if (tau >= totalSeconds) {
                if (!current.holdEnd || tau >= totalSeconds + HOLD_SECONDS + RELAX_SECONDS) {
                    pose = null
                    return -1f
                }
                return 1f
            }
            return if (tau < hitSeconds) {
                startP + tau / hitSeconds * (current.hit - startP)
            } else {
                current.hit + (tau - hitSeconds) / (totalSeconds - hitSeconds) * (1f - current.hit)
            }
        }

        private fun sampleRaw(p: Pose, t: Float, base: FloatArray, out: FloatArray) {
            for (i in 0 until CHANNELS) {
                out[i] = if (i == HEAD_Y && p.spin) base[i] + p.curves[i].at(t, 0f) else p.curves[i].at(t, base[i])
            }
        }

        fun sample(p: Pose, t: Float, base: FloatArray, k: Float, out: FloatArray) {
            sampleRaw(p, t, base, raw)
            val from = carry
            if (from != null) {
                val w = smooth(elapsedSeconds() / blendSeconds)
                for (i in 0 until CHANNELS) raw[i] = from[i] + (raw[i] - from[i]) * w
            }
            val scale = k * amp * relaxWeight()
            for (i in 0 until CHANNELS) {
                val sc = if (i == HEAD_Y && p.spin) 1f else scale
                out[i] = base[i] + (raw[i] - base[i]) * sc
            }
        }
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(SwingClock::tick)
        WeaponTrail.init()
    }

    @JvmStatic
    fun suppressVanilla(): Boolean = SwingClock.suppress

    @JvmStatic
    fun onSwing(player: LocalPlayer) {
        if (OverwatchConfig.current.weaponAnimationPreview) return
        SwingClock.begin(player)
    }

    private val SPELL_KEYS = mapOf(
        "bash" to "SPELL:BASH", "charge" to "SPELL:CHARGE", "uppercut" to "SPELL:UPPERCUT", "warscream" to "SPELL:WAR_SCREAM",
        "heal" to "SPELL:HEAL", "teleport" to "SPELL:TELEPORT", "meteor" to "SPELL:METEOR", "icesnake" to "SPELL:ICE_SNAKE",
        "arcanetransfer" to "SPELL:ARCANE_TRANSFER", "ophanim" to "SPELL:OPHANIM",
        "arrowstorm" to "SPELL:ARROW_STORM", "escape" to "SPELL:ESCAPE", "arrowbomb" to "SPELL:ARROW_BOMB", "bombarrow" to "SPELL:ARROW_BOMB",
        "arrowshield" to "SPELL:ARROW_SHIELD", "phantomray" to "SPELL:PHANTOM_RAY", "grapplinghook" to "SPELL:GRAPPLING_HOOK",
        "guardianangels" to "SPELL:GUARDIAN_ANGELS",
        "spinattack" to "SPELL:SPIN_ATTACK", "dash" to "SPELL:DASH", "multihit" to "SPELL:MULTIHIT", "smokebomb" to "SPELL:SMOKE_BOMB",
        "lacerate" to "SPELL:LACERATE", "backstab" to "SPELL:BACKSTAB", "bamboozle" to "SPELL:BAMBOOZLE",
        "totem" to "SPELL:TOTEM", "haul" to "SPELL:HAUL", "aura" to "SPELL:AURA", "uproot" to "SPELL:UPROOT", "switchmasks" to "SPELL:SWITCH_MASKS",
    )

    @JvmStatic
    fun onSpellCast(name: String) {
        val config = OverwatchConfig.current
        if (!config.weaponAnimationSpells || config.weaponAnimationPreview) return
        val player = Minecraft.getInstance().player ?: return
        val key = SPELL_KEYS[name.lowercase().filter { it.isLetter() }] ?: "SPELL:CAST"
        val chosen = POSES[key] ?: return
        SwingClock.beginSpell(player, chosen)
    }

    fun styleKeys(): List<String> = listOf(WeaponAnimationRegistry.AUTO) + POSES.keys.filter { !it.startsWith("SPELL:") }

    fun styleLabel(key: String): String =
        if (key == WeaponAnimationRegistry.AUTO) "Auto" else LABELS[key] ?: key

    private fun classKind(stack: ItemStack): GearSlotKind? {
        if (WynnWeapons.attacksPerSecond(stack) == null) return null
        val line = WynnItemRarity.loreLines(stack).firstOrNull { it.contains("Class Type", ignoreCase = true) } ?: return null
        val name = CLASS_NAME.find(line)?.value?.lowercase()?.replace(" ", "") ?: return null
        return CLASS_KINDS[name]
    }

    private fun autoKind(stack: ItemStack): GearSlotKind? =
        WynnGearKind.of(stack)?.takeIf { it.name in POSES } ?: classKind(stack)

    fun autoStyleKey(stack: ItemStack): String? = autoKind(stack)?.name

    fun startsSpellWithLeft(stack: ItemStack): Boolean = autoStyleKey(stack)?.startsWith("BOW") == true

    private fun enabled(): Boolean =
        OverwatchConfig.current.weaponAnimationsEnabled && OverwatchGate.isInGame()

    private fun resolveKey(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val assigned = WeaponAnimationRegistry.styleFor(stack) ?: autoStyleKey(stack)
        return assigned?.takeIf { it in POSES }
    }

    private fun resolvePose(stack: ItemStack): Pose? = resolveKey(stack)?.let { POSES[it] }

    private val HEAD_REST = Vector3f(0f, 0f, -1f)
    private val baseBuffer = FloatArray(CHANNELS)
    private val firstPersonBase = FloatArray(CHANNELS)
    private val outBuffer = FloatArray(CHANNELS)
    private val restQuat = Quaternionf()
    private val restDir = Vector3f()
    private val armQuat = Quaternionf()
    private val target = Vector3f()
    private val local = Vector3f()
    private val wristQuat = Quaternionf()
    private val gripPoint = Vector3f()
    private val gripShaft = Vector3f()
    private val gripTo = Vector3f()
    private val gripEuler = Vector3f()
    private val gripQuat = Quaternionf()
    private val gripDir = Vector3f(0f, GRIP_Y, GRIP_Z).normalize()
    private val itemQuat = Quaternionf()
    private val ITEM_FRAME = Quaternionf()
        .rotationX(Math.toRadians(-90.0).toFloat())
        .mul(Quaternionf().rotationY(Math.PI.toFloat()))
    private val ITEM_FRAME_INV = Quaternionf(ITEM_FRAME).invert()
    private val twistQuat = Quaternionf()
    private val trailNear = Vector3f()
    private val trailFar = Vector3f()
    private val nearWorld = DoubleArray(3)
    private val farWorld = DoubleArray(3)
    private val clipQuat = Quaternionf()
    private val clipPoint = Vector3f()

    private var headA = 0f
    private var headY = 0f
    private var headTwist = 0f
    private var headYaw = 0f
    private var headSide = 1f
    private var headActive = false
    private var headFrame = -1L
    private var debugLine = ""
    private var debugKey = ""
    private var debugT = 0f
    private var debugRestA = 0f
    private var debugRestY = 0f
    private val debugDir = Vector3f()

    @JvmStatic
    fun applyThirdPerson(model: HumanoidModel<*>, state: HumanoidRenderState): Boolean {
        if (state !is AvatarRenderState) return false
        val player = Minecraft.getInstance().player ?: return false
        if (state.id != player.id) return false
        SwingClock.idleStep()
        val t = SwingClock.progress()
        val pose = SwingClock.pose
        val swinging = t >= 0f && pose != null
        if (!swinging && !SwingClock.idleActive()) {
            headActive = false
            return false
        }

        val k = 1f
        val right = if (swinging) SwingClock.right else SwingClock.idleRight
        val s = if (right) 1f else -1f
        val main = if (right) model.rightArm else model.leftArm
        val off = if (right) model.leftArm else model.rightArm

        val base = baseBuffer
        base[PITCH] = main.xRot
        base[ACROSS] = -s * main.yRot
        base[ABDUCT] = s * main.zRot
        base[OFF_PITCH] = off.xRot
        base[OFF_ACROSS] = s * off.yRot
        base[OFF_ABDUCT] = -s * off.zRot
        base[TURN] = 0f
        base[REACH] = 0f
        base[TWIST] = 0f
        base[GRIP] = 0f
        base[LEAN] = 0f
        base[STEP] = 0f

        restQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        restDir.set(HEAD_REST).rotate(restQuat)
        base[HEAD_A] = asin(restDir.y.coerceIn(-1f, 1f))
        base[HEAD_Y] = atan2(s * restDir.x, -restDir.z)

        SwingClock.blendIdle(base)
        val breath = SwingClock.breath()
        base[PITCH] += breath
        base[OFF_PITCH] += breath * 0.8f
        base[HEAD_A] += breath * 0.5f

        val out = outBuffer
        if (pose != null && swinging) SwingClock.sample(pose, t, base, k, out) else base.copyInto(out)

        val yaw = if (swinging) -s * out[TURN] else 0f
        if (swinging) {
            model.body.yRot = yaw
            model.rightArm.z = sin(yaw) * 5f
            model.rightArm.x = -cos(yaw) * 5f
            model.leftArm.z = -sin(yaw) * 5f
            model.leftArm.x = cos(yaw) * 5f
        }

        applyArm(main, -s, s, yaw, out[PITCH], out[ACROSS], out[ABDUCT])
        applyArm(off, s, -s, yaw, out[OFF_PITCH], out[OFF_ACROSS], out[OFF_ABDUCT])
        main.z += out[REACH]
        applyBody(model, right, yaw, out[LEAN], out[STEP])
        val mainShoulder = if (right) -SHOULDER_X else SHOULDER_X
        keepOutOfBody(main, mainShoulder, yaw)
        val offShoulder = -mainShoulder
        val gripWeight = out[GRIP].coerceIn(0f, 1f)
        val gripOffset = SwingClock.idleGripOffset() ?: pose?.gripOffset ?: 7f
        val offX = off.xRot
        val offY = off.yRot
        val offZ = off.zRot
        for (relax in GRIP_RELAX) {
            off.xRot = offX
            off.yRot = offY
            off.zRot = offZ
            applyGrip(main, off, s, yaw, out[HEAD_A], out[HEAD_Y], gripWeight * relax, gripOffset)
            if (!armInsideBody(off, offShoulder, yaw)) break
        }
        keepOutOfBody(off, offShoulder, yaw)

        if (swinging && pose != null) sampleTrailThirdPerson(state, main, s, yaw, out[HEAD_A], out[HEAD_Y], pose.key)

        headA = out[HEAD_A]
        headY = out[HEAD_Y]
        headTwist = out[TWIST]
        headYaw = yaw
        headSide = s
        headActive = true
        headFrame = System.nanoTime()
        if (OverwatchConfig.current.weaponAnimationPreview) {
            debugKey = pose?.key ?: "IDLE"
            debugT = t
            debugRestA = base[HEAD_A]
            debugRestY = base[HEAD_Y]
        }
        return true
    }

    private fun sampleTrailThirdPerson(state: HumanoidRenderState, main: ModelPart, side: Float, yaw: Float, headA: Float, headY: Float, key: String) {
        if (!OverwatchConfig.current.weaponAnimationTrail) return
        if (Minecraft.getInstance().options.cameraType.isFirstPerson) return
        val span = trailSpan(key) ?: return
        armQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        gripPoint.set(0f, GRIP_Y, GRIP_Z).rotate(armQuat).add(main.x, main.y, main.z)
        val cosA = cos(headA)
        gripShaft.set(side * sin(headY) * cosA, sin(headA), -cos(headY) * cosA).rotateY(yaw)
        val angle = Math.toRadians((180f - state.bodyRot).toDouble()).toFloat()
        val scale = PLAYER_SCALE * state.scale
        trailNear.set(gripPoint).fma(span.first, gripShaft)
        trailFar.set(gripPoint).fma(span.second, gripShaft)
        worldPoint(state, trailNear, angle, scale, nearWorld)
        worldPoint(state, trailFar, angle, scale, farWorld)
        WeaponTrail.addWorld(nearWorld[0], nearWorld[1], nearWorld[2], farWorld[0], farWorld[1], farWorld[2], SwingClock.trailStrength)
    }

    private fun worldPoint(state: HumanoidRenderState, p: Vector3f, angle: Float, scale: Float, out: DoubleArray) {
        val lx = -p.x / MODEL_UNIT * scale
        val ly = (MODEL_LIFT - p.y / MODEL_UNIT) * scale
        val lz = p.z / MODEL_UNIT * scale
        val c = cos(angle)
        val sn = sin(angle)
        out[0] = state.x + lx * c + lz * sn
        out[1] = state.y + ly
        out[2] = state.z - lx * sn + lz * c
    }

    private fun leanPart(part: ModelPart, yaw: Float, lean: Float) {
        clipPoint.set(part.x, part.y - HIP_Y, part.z).rotateY(-yaw).rotateX(lean).rotateY(yaw)
        part.x = clipPoint.x
        part.y = clipPoint.y + HIP_Y
        part.z = clipPoint.z
    }

    private fun applyBody(model: HumanoidModel<*>, right: Boolean, yaw: Float, lean: Float, step: Float) {
        if (abs(lean) > 0.001f) {
            leanPart(model.body, yaw, lean)
            model.body.xRot += lean
            leanPart(model.head, yaw, lean)
            model.head.xRot += lean * LEAN_HEAD_SHARE
            leanPart(model.rightArm, yaw, lean)
            model.rightArm.xRot += lean
            leanPart(model.leftArm, yaw, lean)
            model.leftArm.xRot += lean
        }
        if (abs(step) > 0.001f) {
            val front = if (right) model.rightLeg else model.leftLeg
            val back = if (right) model.leftLeg else model.rightLeg
            front.xRot -= STEP_LEG_PITCH * step
            front.z -= STEP_REACH * step
            back.xRot += STEP_BACK_PITCH * step
            back.z += STEP_BACK_REACH * step
        }
    }

    private fun armInsideBody(part: ModelPart, shoulderX: Float, yaw: Float): Boolean {
        clipQuat.rotationZYX(part.zRot, part.yRot - yaw, part.xRot)
        for (f in GUARD_POINTS) {
            clipPoint.set(0f, GRIP_Y * f, GRIP_Z * f).rotate(clipQuat)
            val x = shoulderX + clipPoint.x
            val y = part.y + clipPoint.y
            if (abs(x) < BODY_HALF_WIDTH && clipPoint.z > BODY_FRONT && y > 0f && y < BODY_BOTTOM) return true
        }
        return false
    }

    private fun keepOutOfBody(part: ModelPart, shoulderX: Float, yaw: Float) {
        val outward = if (shoulderX > 0f) -CLIP_OUTWARD else CLIP_OUTWARD
        repeat(CLIP_STEPS) {
            if (!armInsideBody(part, shoulderX, yaw)) return
            part.xRot -= CLIP_STEP
            part.yRot += outward
        }
    }

    private fun applyGrip(main: ModelPart, off: ModelPart, side: Float, yaw: Float, headA: Float, headY: Float, weight: Float, offset: Float) {
        if (weight <= 0.001f) return
        armQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        gripPoint.set(0f, GRIP_Y, GRIP_Z).rotate(armQuat).add(main.x, main.y, main.z)
        val cosA = cos(headA)
        gripShaft.set(side * sin(headY) * cosA, sin(headA), -cos(headY) * cosA).rotateY(yaw)
        gripTo.set(gripPoint).sub(off.x, off.y, off.z)
        val b = gripTo.dot(gripShaft)
        val disc = b * b - (gripTo.lengthSquared() - GRIP_LEN_SQ)
        val along = if (disc >= 0f) {
            val r = sqrt(disc)
            val near = -b - r
            val far = -b + r
            if (abs(near - offset) < abs(far - offset)) near else far
        } else {
            -b
        }
        gripTo.fma(along, gripShaft).normalize()
        gripQuat.rotationTo(gripDir, gripTo)
        gripQuat.getEulerAnglesZYX(gripEuler)
        off.xRot += (gripEuler.x - off.xRot) * weight
        off.yRot += (gripEuler.y - off.yRot) * weight
        off.zRot += (gripEuler.z - off.zRot) * weight
    }

    private fun applyArm(part: ModelPart, yawSign: Float, rollSign: Float, yaw: Float, pitch: Float, across: Float, abduct: Float) {
        part.xRot = pitch
        part.yRot = yawSign * across + yaw
        part.zRot = rollSign * abduct
    }

    @JvmStatic
    fun applyWrist(state: ArmedEntityRenderState, arm: HumanoidArm, part: ModelPart, stack: PoseStack) {
        if (state !is AvatarRenderState) return
        val player = Minecraft.getInstance().player ?: return
        if (state.id != player.id) return
        if (!headActive || (arm == HumanoidArm.RIGHT) != (headSide > 0f)) return
        if (System.nanoTime() - headFrame > 100_000_000L) return

        val cosA = cos(headA)
        target.set(headSide * sin(headY) * cosA, sin(headA), -cos(headY) * cosA).rotateY(headYaw)
        armQuat.rotationZYX(part.zRot, part.yRot, part.xRot)
        local.set(target).rotate(armQuat.invert())
        wristQuat.rotationTo(HEAD_REST, local)
        twistQuat.rotationAxis(headTwist * headSide, HEAD_REST)
        itemQuat.set(ITEM_FRAME_INV).mul(wristQuat).mul(twistQuat).mul(ITEM_FRAME)
        stack.mulPose(itemQuat)
        if (OverwatchConfig.current.weaponAnimationPreview) {
            armQuat.rotationZYX(part.zRot, part.yRot, part.xRot)
            debugDir.set(HEAD_REST).rotate(wristQuat).rotate(armQuat).rotateY(-headYaw)
            val gotA = asin(debugDir.y.coerceIn(-1f, 1f))
            val gotY = atan2(headSide * debugDir.x, -debugDir.z)
            debugLine = String.format(
                "%s t=%.2f | want a=%.2f y=%.2f tw=%.2f | got a=%.2f y=%.2f | rest a=%.2f y=%.2f | arm p=%.2f",
                debugKey, debugT, headA, headY, headTwist, gotA, gotY, debugRestA, debugRestY, part.xRot,
            )
        }
    }

    @JvmStatic
    fun applyFirstPerson(stack: PoseStack, arm: HumanoidArm, sign: Int): Boolean {
        val t = SwingClock.progress()
        val pose = SwingClock.pose
        if (t < 0f || pose == null || (arm == HumanoidArm.RIGHT) != SwingClock.right) return false

        val k = 1f
        val out = outBuffer
        val fpBase = firstPersonBase
        REST_BASE.copyInto(fpBase)
        SwingClock.blendIdle(fpBase)
        SwingClock.sample(pose, t, fpBase, k, out)
        poseFirstPerson(stack, sign, out, 0f)
        sampleTrailFirstPerson(stack, pose.key)
        return true
    }

    private val fpNear = Vector3f()
    private val fpFar = Vector3f()
    private val fpAxis = Vector3f(0f, 0.8f, -0.5f).normalize()

    private fun sampleTrailFirstPerson(stack: PoseStack, key: String) {
        if (!OverwatchConfig.current.weaponAnimationTrail) return
        if (trailSpan(key) == null) return
        val pose = stack.last().pose()
        fpNear.set(fpAxis).mul(-0.05f).mulPosition(pose)
        fpFar.set(fpAxis).mul(0.95f).mulPosition(pose)
        WeaponTrail.addView(fpNear.x, fpNear.y, fpNear.z, fpFar.x, fpFar.y, fpFar.z, SwingClock.trailStrength)
    }

    @JvmStatic
    fun applyFirstPersonIdle(stack: PoseStack, arm: HumanoidArm, sign: Int) {
        SwingClock.idleStep()
        if (!SwingClock.idleActive() || (arm == HumanoidArm.RIGHT) != SwingClock.idleRight) return
        if (SwingClock.pose != null && SwingClock.progress() >= 0f && (arm == HumanoidArm.RIGHT) == SwingClock.right) return
        val base = firstPersonBase
        REST_BASE.copyInto(base)
        SwingClock.blendIdle(base)
        val breath = SwingClock.breath()
        poseFirstPerson(stack, sign, base, breath)
    }

    private const val FP_MOVE = 1.1f / 16f
    private const val FP_ROT = 0.9f
    private const val FP_LIMIT_X = 0.4f
    private const val FP_LIMIT_Y = 0.5f
    private const val FP_LIMIT_Z = 0.7f
    private val fpGrip = Vector3f()
    private val fpRestGrip = Vector3f()
    private val fpArm = Quaternionf()
    private val fpQuat = Quaternionf()
    private val fpRest = Quaternionf()
    private val fpMix = Quaternionf()
    private val fpIdentity = Quaternionf()
    private val fpRestFor = FloatArray(CHANNELS)

    private fun fpGripPos(ch: FloatArray, s: Float, out: Vector3f) {
        val yaw = -s * ch[TURN]
        fpArm.rotationZYX(s * ch[ABDUCT], -s * ch[ACROSS] + yaw, ch[PITCH])
        out.set(0f, GRIP_Y, GRIP_Z).rotate(fpArm)
        out.add(-s * cos(yaw) * SHOULDER_X, 2f, s * sin(yaw) * SHOULDER_X)
        out.z += ch[REACH]
    }

    private fun fpOrient(a: Float, y: Float, turn: Float, twist: Float, s: Float, out: Quaternionf) {
        out.rotationY(s * (y + turn)).rotateX(-a).rotateZ(-s * twist)
    }

    private fun poseFirstPerson(stack: PoseStack, sign: Int, ch: FloatArray, breath: Float) {
        val s = sign.toFloat()
        fpGripPos(ch, s, fpGrip)
        REST_BASE.copyInto(fpRestFor)
        fpGripPos(fpRestFor, s, fpRestGrip)
        val dx = (-(fpGrip.x - fpRestGrip.x) * FP_MOVE).coerceIn(-FP_LIMIT_X, FP_LIMIT_X)
        val dy = (-(fpGrip.y - fpRestGrip.y) * FP_MOVE + breath).coerceIn(-FP_LIMIT_Y, FP_LIMIT_Y)
        val dz = ((fpGrip.z - fpRestGrip.z) * FP_MOVE * 1.5f).coerceIn(-FP_LIMIT_Z, FP_LIMIT_Z)
        fpOrient(ch[HEAD_A] + breath * 0.5f, ch[HEAD_Y], ch[TURN], ch[TWIST], s, fpQuat)
        fpOrient(REST_BASE[HEAD_A], 0f, 0f, 0f, s, fpRest)
        fpQuat.mul(fpRest.invert())
        fpMix.set(fpIdentity).slerp(fpQuat, FP_ROT)
        stack.translate(dx, dy, dz)
        stack.mulPose(fpMix)
    }
}

package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.vertex.PoseStack
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
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.ThreadLocalRandom

object WeaponAnimations {
    private const val MIN_SECONDS = 0.18f
    private const val GAP_FRACTION = 0.95f
    private const val STRETCH_MAX = 2.4f
    private const val PACE_MAX = 1.2f
    private const val PACE_SMOOTH = 0.5f
    private const val IDLE_ENTRY_BLEND = 0.12f
    private const val HIT_SECONDS = 0.14f
    private const val HIT_SHARE = 0.5f
    private const val AMP_MIN = 0.45f
    private const val CHAIN_SKIP = 0.7f
    private const val HOLD_SECONDS = 0.12f
    private const val HOLD_SHARE = 0.2f
    private const val SHORT_SWING_SECONDS = 0.22f
    private const val MIN_HOLD_NANOS = 15_000_000L
    private const val RELAX_SECONDS = 0.22f
    private const val MIN_GAP_FRACTION = 0.88f
    private const val MIN_GAP_LOW = 0.12f
    private const val MIN_GAP_HIGH = 0.9f
    private const val FALLBACK_GAP = 0.25f
    private const val BLEND_SECONDS = 0.09f
    private const val AMP_SMOOTH_TAU = 0.09f
    private const val SPELL_BLEND_SECONDS = 0.12f
    private const val IDLE_TAU = 0.16f
    private const val COMBO_RESET_SECONDS = 1.6f
    private const val COMBO_PITCH_STEP = 0.035f
    private const val MODEL_UNIT = 16f
    private const val READY_NANOS = 3_500_000_000L
    private const val BREATH_NANOS = 3_400_000_000L
    private const val BREATH_AMP = 0.025f
    private const val IDLE_BREATH_BOOST = 0.7f
    private const val SWAY_NANOS = 7_300_000_000L
    private const val SWAY_AMP = 0.045f
    private const val SPRINT_BOB_NANOS = 450_000_000L
    private const val SPRINT_BOB_AMP = 0.02f
    private const val WALK_SHARE = 0.35f
    private const val GAIT_PHASE = 0.6662f
    private const val WALK_MAIN_SWING = 0.2f
    private const val WALK_OFF_SWING = 0.5f
    private const val SPRINT_MAIN_SWING = 0.55f
    private const val SPRINT_OFF_SWING = 1.05f
    private const val SPRINT_LEAN = 0.14f
    private const val SPRINT_ACROSS = 0.18f
    private const val SPRINT_BOUNCE = 0.05f
    private const val SPRINT_TIP_BOUNCE = 0.14f
    private const val WALK_TIP_BOUNCE = 0.05f
    private const val WALK_SPEED_SQ = 0.0006
    private const val STILL_SPEED_SQ = 0.0004
    private const val SPRINT_SPEED_SQ = 0.005
    private const val TICK_NANOS = 50_000_000L
    private const val GATHER_CHAIN_SECONDS = 1.2f
    private const val GATHER_MAX_SECONDS = 0.6f
    private const val GATHER_MIN_STROKE = 0.34f
    private const val GATHER_RESTART_SHARE = 0.6f
    private const val GATHER_INTERVAL_SMOOTH = 0.5f

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
    private const val ROOT_F = 13
    private const val FOOT_LF = 14
    private const val FOOT_RF = 15
    private const val BODY_Y = 16
    private const val BODY_R = 17
    private const val HIPS_Y = 18
    private const val FOOT_LS = 19
    private const val FOOT_RS = 20
    private const val ROOT_S = 21
    private const val ROOT_H = 22
    private const val FOOT_LU = 23
    private const val FOOT_RU = 24
    internal const val CHANNELS = 25
    private const val TURN_BOOST = 1.4f
    private const val LEAN_HEAD_SHARE = 0.3f
    private const val HIP_Y = 12f
    private const val GRIP_Y = 10f
    private const val GRIP_Z = -2f
    private const val SHOULDER_X = 5f
    private const val BODY_HALF_WIDTH = 4.5f
    private const val BODY_FRONT = -4.2f
    private const val BODY_BOTTOM = 14f
    private const val CLIP_STEP = 0.025f
    private const val CLIP_OUTWARD = 0.0125f
    private const val CLIP_STEPS = 36
    private const val GRIP_BISECT = 6
    private const val GRIP_RELAX_TAU = 0.06f
    private const val GRIP_SLIDE_STEP = 1f
    private const val GRIP_SLIDE_STEPS = 8
    private const val GRIP_MIN_OFFSET = 3f
    private const val SPRING_OMEGA = 26f
    private const val SPRING_DAMPING = 0.5f
    private const val SPRING_MIX = 0.85f
    private const val SPRING_MAX_LAG = 0.6f
    private const val SPRING_RESET_GAP = 0.25f
    private const val SPRING_MAX_DT = 0.05f
    private const val SPRING_STEPS = 2
    private const val TWO_PI = 6.2831855f
    private const val PI_F = 3.1415927f

    private fun wrapPi(v: Float): Float {
        val r = v % TWO_PI
        return if (r > PI_F) r - TWO_PI else if (r < -PI_F) r + TWO_PI else r
    }

    private fun springTip(out: FloatArray) {
        val now = System.nanoTime()
        val gap = (now - clock.springNanos) / 1_000_000_000f
        clock.springNanos = now
        if (!clock.springReady || gap > SPRING_RESET_GAP) {
            clock.springA = out[HEAD_A]
            clock.springY = out[HEAD_Y]
            clock.springAV = 0f
            clock.springYV = 0f
            clock.springReady = true
            return
        }
        val h = gap.coerceIn(0f, SPRING_MAX_DT) / SPRING_STEPS
        val k = SPRING_OMEGA * SPRING_OMEGA
        val c = 2f * SPRING_DAMPING * SPRING_OMEGA
        repeat(SPRING_STEPS) {
            clock.springAV += (k * (out[HEAD_A] - clock.springA) - c * clock.springAV) * h
            clock.springA += clock.springAV * h
            clock.springYV += (k * wrapPi(out[HEAD_Y] - clock.springY) - c * clock.springYV) * h
            clock.springY += clock.springYV * h
        }
        val lagA = (out[HEAD_A] - clock.springA).coerceIn(-SPRING_MAX_LAG, SPRING_MAX_LAG)
        val lagY = wrapPi(out[HEAD_Y] - clock.springY).coerceIn(-SPRING_MAX_LAG, SPRING_MAX_LAG)
        out[HEAD_A] -= lagA * SPRING_MIX
        out[HEAD_Y] -= lagY * SPRING_MIX
    }
    private val GUARD_POINTS = floatArrayOf(0.6f, 0.85f, 1f)
    private const val GRIP_LEN_SQ = GRIP_Y * GRIP_Y + GRIP_Z * GRIP_Z

    internal val REST_BASE = floatArrayOf(-0.3f, 0f, 0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    internal val B = Float.NaN

    internal class Gait(
        val mainWalk: Float,
        val offWalk: Float,
        val mainSprint: Float,
        val offSprint: Float,
        val across: Float,
        val lean: Float,
        val leanBounce: Float,
        val tipWalk: Float,
        val tipSprint: Float,
        val yaw: Float,
        val armBob: Float,
        val twist: Float = 0f,
    )

    internal val DEFAULT_GAIT = Gait(
        WALK_MAIN_SWING, WALK_OFF_SWING, SPRINT_MAIN_SWING, SPRINT_OFF_SWING,
        SPRINT_ACROSS, SPRINT_LEAN, SPRINT_BOUNCE, WALK_TIP_BOUNCE, SPRINT_TIP_BOUNCE, 0.06f, 0f,
    )

    internal class Stance(
        val carry: FloatArray,
        val ready: FloatArray,
        val idle: FloatArray,
        val sprint: FloatArray,
        val gripOffset: Float = 7f,
        val walk: FloatArray? = null,
        val gait: Gait = DEFAULT_GAIT,
    )

    private val POSES: Map<String, Pose> get() = AnimationLibrary.tables().poses
    private val STANCES: Map<String, Stance> get() = AnimationLibrary.tables().stances
    private val COMBOS: Map<String, List<ComboStep>> get() = AnimationLibrary.tables().combos
    private val OPENERS: Map<String, ComboStep> get() = AnimationLibrary.tables().openers
    private val CHARGES: Map<String, ComboStep> get() = AnimationLibrary.tables().charges
    private val SFX: Map<String, List<Cue>> get() = AnimationLibrary.tables().sfx
    private val IMPACT: Map<String, List<Cue>> get() = AnimationLibrary.tables().impact
    private val FINISHER_CUE: Cue get() = AnimationLibrary.tables().finisher
    private val IMPACT_THUMP: Cue get() = AnimationLibrary.tables().thump
    private val IMPACT_TAIL: Cue get() = AnimationLibrary.tables().tail
    private val IMPACT_SLAM: Cue get() = AnimationLibrary.tables().slam
    private val LUNGE_FAMILIES: Set<String> get() = AnimationLibrary.tables().lungeFamilies
    private val HAND_SHIFT: Map<String, Float> get() = AnimationLibrary.tables().handShift
    private val HAND_SPRINT_EXTRA: Map<String, Float> get() = AnimationLibrary.tables().handSprintExtra
    private val TRAIL_SPANS: Map<String, Pair<Float, Float>> get() = AnimationLibrary.tables().trailSpans
    private val LABELS: Map<String, String> get() = AnimationLibrary.tables().labels

    private fun stanceFor(key: String?): Stance? {
        if (key == null) return null
        val alias = AnimationLibrary.tables().movesetStances[key]
        return STANCES[key] ?: alias?.let { STANCES[it] } ?: STANCES[key.substringBefore(':')]
    }

    private fun basePose(key: String): Pose? = POSES[key] ?: COMBOS[key]?.firstOrNull()?.let { POSES[it.key] }

    private fun swingStyle(): SwingStyle = AnimationLibrary.tables().locomotion.swingFor(WynnOverhaulConfig.current.locomotionStyle)

    private val CHANNEL_KIND = intArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)

    internal class Curve(val times: FloatArray, val values: FloatArray) {
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

    internal val KEEP = curve(0f to B, 1f to B)
    internal val ZERO = curve(0f to 0f, 1f to 0f)

    internal class Pose {
        val curves: Array<Curve> = Array(CHANNELS) { if (it == TURN || it == REACH || it == TWIST) ZERO else KEEP }
        var seconds = 0.36f
        var hit = 0.3f
        var recover = -1f
        var blend = BLEND_SECONDS
        var holdEnd = false
        var spin = false
        var gripOffset = 7f
        var key = ""

        fun mirrored(): Pose {
            val out = Pose()
            for (i in 0 until CHANNELS) {
                val source = when (i) {
                    FOOT_LF -> FOOT_RF
                    FOOT_RF -> FOOT_LF
                    FOOT_LS -> FOOT_RS
                    FOOT_RS -> FOOT_LS
                    FOOT_LU -> FOOT_RU
                    FOOT_RU -> FOOT_LU
                    else -> i
                }
                val flip = when (i) {
                    ACROSS, OFF_ACROSS, TURN, HEAD_Y, TWIST, BODY_Y, BODY_R, HIPS_Y, FOOT_LS, FOOT_RS, ROOT_S -> true
                    else -> false
                }
                out.curves[i] = if (flip) curves[source].negated() else curves[source]
            }
            out.seconds = seconds
            out.hit = hit
            out.recover = recover
            out.blend = blend
            out.holdEnd = holdEnd
            out.spin = spin
            out.gripOffset = gripOffset
            return out
        }
    }

    internal class ComboStep(val key: String, val mirror: Boolean = false, val finisher: Boolean = false)

    private val mirrorCache = HashMap<String, Pose>()

    private fun stepPose(step: ComboStep): Pose? {
        val base = POSES[step.key] ?: return null
        if (!step.mirror) return base
        return mirrorCache.getOrPut(step.key) { base.mirrored().also { it.key = base.key } }
    }

    internal class Cue(val at: Float, val sound: SoundEvent, val pitch: Float, val volume: Float)

    private fun playCue(level: Level, x: Double, y: Double, z: Double, cue: Cue, pitchScale: Float, volumeScale: Float) {
        val jitter = 0.97f + ThreadLocalRandom.current().nextFloat() * 0.06f
        val volume = cue.volume * WynnOverhaulConfig.current.weaponAnimationSfxVolume.toFloat() * volumeScale
        level.playLocalSound(x, y, z, cue.sound, SoundSource.PLAYERS, volume, cue.pitch * pitchScale * jitter, false)
    }

    private fun impactFamily(client: Minecraft): String =
        (clock.pose?.key ?: client.player?.mainHandItem?.let { autoStyleKey(it) })?.substringBefore(':') ?: "SPEAR"

    fun playImpact(at: Vec3, weight: Float, spell: Boolean) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val layers = ArrayList(IMPACT[if (spell) "SPELL" else impactFamily(client)] ?: IMPACT.getValue("SPEAR"))
        if (weight > THUMP_WEIGHT) layers.add(IMPACT_THUMP)
        if (weight > SLAM_WEIGHT) layers.add(IMPACT_SLAM)
        val distance = client.player?.position()?.distanceTo(at) ?: 0.0
        val reach = (1.0 - distance / IMPACT_FALLOFF_BLOCKS).coerceIn(IMPACT_FALLOFF_MIN, 1.0).toFloat()
        val volumeScale = (IMPACT_VOLUME_BASE + IMPACT_VOLUME_SPAN * weight.coerceIn(0.6f, 2f)) * reach
        val pitchScale = (IMPACT_PITCH_BASE - IMPACT_PITCH_DROP * weight).coerceIn(IMPACT_PITCH_MIN, IMPACT_PITCH_MAX)
        for (layer in layers) playCue(level, at.x, at.y, at.z, layer, pitchScale, volumeScale)
        tails.add(Tail(System.nanoTime() + IMPACT_TAIL_DELAY_NANOS, at, pitchScale, volumeScale))
    }

    private class Tail(val dueNanos: Long, val at: Vec3, val pitchScale: Float, val volumeScale: Float)

    private val tails = ArrayList<Tail>()

    private fun playTails(client: Minecraft) {
        if (tails.isEmpty()) return
        val level = client.level
        val now = System.nanoTime()
        val iterator = tails.iterator()
        while (iterator.hasNext()) {
            val tail = iterator.next()
            if (now < tail.dueNanos) continue
            iterator.remove()
            if (level != null) playCue(level, tail.at.x, tail.at.y, tail.at.z, IMPACT_TAIL, tail.pitchScale, tail.volumeScale)
        }
    }

    fun clearImpactTails() {
        tails.clear()
    }

    private const val MOVE_LEAN_CUT = 0.6f
    private const val CHARGE_SUFFIX = ":SPRINT"
    private const val CHARGE_KEEP = 0.6f
    private const val FORWARD_DOT = 0.6f
    private const val MOVE_TURN_CUT_WALK = 0.25f
    private const val MOVE_TURN_CUT_RUN = 0.45f
    private const val DRIVE_LEAN_WALK = 0.07f
    private const val DRIVE_LEAN_RUN = 0.16f
    private const val MOVE_BODY_CUT = 0.5f
    private const val RECOVER_SHARE = 0.35f
    private const val RECOVER_MOVE_CUT = 0.85f
    private const val PLANT_CUT = 0.25f
    private const val STRIKE_FRESH_NANOS = 120_000_000L
    private const val MOVE_TAU = 0.06f
    private const val MOVE_DUST_MAX = 0.3f
    private const val REBOUND_REACH = 1.4f
    private const val REBOUND_DECAY = 14f
    private const val REBOUND_FREQ = 22f
    private const val REBOUND_SECONDS = 0.25f
    private const val IMPACT_FALLOFF_BLOCKS = 40.0
    private const val IMPACT_FALLOFF_MIN = 0.45
    private const val IMPACT_TAIL_DELAY_NANOS = 70_000_000L
    private const val ANTICIPATION = 0.55f
    private const val FLINCH_LEAN = 0.2f
    private const val FLINCH_REACH = 1.2f
    private const val FLINCH_RISE = 0.04f
    private const val FLINCH_DECAY = 9f
    private const val FLINCH_SECONDS = 0.45f
    private const val THUMP_WEIGHT = 1.35f
    private const val SLAM_WEIGHT = 1.9f
    private const val IMPACT_VOLUME_BASE = 0.6f
    private const val IMPACT_VOLUME_SPAN = 0.4f
    private const val IMPACT_PITCH_BASE = 1.15f
    private const val IMPACT_PITCH_DROP = 0.15f
    private const val IMPACT_PITCH_MIN = 0.8f
    private const val IMPACT_PITCH_MAX = 1.1f

    private fun trailSpan(key: String): Pair<Float, Float>? = TRAIL_SPANS[key] ?: TRAIL_SPANS[key.substringBefore(':')]

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

    private class Clock {
        var owner: Player? = null
        var gripRelax = 1f
        var gripSlide = 0f
        var gripRelaxNanos = 0L
        var blockPush = 0f
        var blockPushNanos = 0L
        var springReady = false
        var springNanos = 0L
        var springA = 0f
        var springAV = 0f
        var springY = 0f
        var springYV = 0f

        fun keyFor(stack: ItemStack): String? = if (owner == null) resolveKey(stack) else remoteKey(stack)

        fun tickRemote(player: Player) {
            owner = player
            applyReload()
            if (!player.swinging && !(pose?.key?.startsWith("SPELL:") == true && computeProgress() >= 0f)) suppress = false
            refreshIdle(player)
        }

        var pose: Pose? = null
        var right = true
        var suppress = false
        private var previewing = false
        private var previewStack: ItemStack? = null
        private var previewCached: List<Pose> = emptyList()
        private var previewLocal = 0f
        private var startNanos = 0L
        private var pauseStart = 0L
        private var pauseEnd = 0L
        private var reboundStart = 0L
        private var reboundAmp = 0f
        private var totalSeconds = 1f
        private var startP = 0f
        private var blendSeconds = BLEND_SECONDS
        private var hitSeconds = 0.09f
        private var amp = 1f
        private var swingBody = 1f
        private var swingArc = 1f
        private var carry: FloatArray? = null
        private var pace = 0f
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
        private var stillAmt = 0f
        private var sprintAmt = 0f
        private var stillTicks = 0
        private var sprintingNow = false
        private var walkAmt = 0f
        private var walkingNow = false
        private var movingNow = false
        private var forwardNow = false
        private var moveAmt = 0f
        private var runAmt = 0f
        private var strikeWeight = 0f
        private var strikeStamp = 0L
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

        fun applyGait(base: FloatArray, pos: Float, speed: Float, side: Float) {
            val move = walkAmt + sprintAmt
            if (move <= 0.001f) return
            val sp = speed.coerceIn(0f, 1f)
            val phase = pos * GAIT_PHASE
            val swing = cos(phase)
            val bounce = sin(phase * 2f)
            val g = idleTable?.gait ?: DEFAULT_GAIT
            val mainAmp = (g.mainWalk * walkAmt + g.mainSprint * sprintAmt) * sp
            val offAmp = (g.offWalk * walkAmt + g.offSprint * sprintAmt) * sp
            val armBob = bounce * g.armBob * move * sp
            base[PITCH] += armBob - side * swing * mainAmp
            base[OFF_PITCH] += armBob + side * swing * offAmp
            base[ACROSS] += swing * g.across * sprintAmt * sp
            base[OFF_ACROSS] -= swing * g.across * sprintAmt * sp
            base[LEAN] += g.lean * sprintAmt + bounce * g.leanBounce * sprintAmt * sp
            base[HEAD_A] += bounce * (g.tipSprint * sprintAmt + g.tipWalk * walkAmt) * sp
            base[HEAD_Y] += swing * g.yaw * (sprintAmt + walkAmt) * sp
            base[TWIST] += swing * g.twist * move * sp
        }

        fun sway(): Float {
            if (stillAmt <= 0.001f) return 0f
            val phase = (System.nanoTime() % SWAY_NANOS).toDouble() / SWAY_NANOS
            return (sin(phase * 2.0 * Math.PI) * SWAY_AMP * stillAmt).toFloat()
        }

        fun sprintBob(): Float {
            if (sprintAmt <= 0.001f) return 0f
            val phase = (System.nanoTime() % SPRINT_BOB_NANOS).toDouble() / SPRINT_BOB_NANOS
            return (sin(phase * 2.0 * Math.PI) * SPRINT_BOB_AMP * sprintAmt).toFloat()
        }

        fun idleKey(): String? = idleStackKey

        fun sprintAmount(): Float = sprintAmt

        fun idleGripOffset(): Float? = idleTable?.takeIf { idleAmt > 0.5f && it.ready[GRIP] > 0f }?.gripOffset

        fun idleActive(): Boolean = idleAmt > 0.01f

        fun heldAmount(): Float = idleAmt

        fun moveAmount(): Float = moveAmt

        fun charging(): Float = if (pose?.key?.endsWith(CHARGE_SUFFIX) == true) 1f else 0f

        fun rawAt(p: Pose, t: Float, base: FloatArray, out: FloatArray) = sampleRaw(p, t, base, out)

        fun breath(): Float {
            val phase = (System.nanoTime() % BREATH_NANOS).toDouble() / BREATH_NANOS
            return (sin(phase * 2.0 * Math.PI) * BREATH_AMP * idleAmt * (1f + IDLE_BREATH_BOOST * stillAmt) * (1f - sprintAmt)).toFloat()
        }

        fun idleStep() {
            val now = System.nanoTime()
            val dt = if (idleLast == 0L) 0f else ((now - idleLast) / 1_000_000_000f).coerceIn(0f, 0.1f)
            idleLast = now
            val a = MathX.expApproach(dt, IDLE_TAU)
            val table = idleTable
            val config = WynnOverhaulConfig.current
            val ready = now < readyUntil
            val r = if (ready) 1f else 0f
            val stillGoal = table != null && !ready && config.weaponTrueIdleEnabled &&
                stillTicks * TICK_NANOS >= (config.weaponTrueIdleDelaySeconds * 1_000_000_000.0).toLong()
            val sprintGoal = table != null && !ready && config.weaponSprintEnabled && sprintingNow
            val walkGoal = table != null && !ready && config.weaponWalkEnabled && walkingNow && !sprintGoal
            stillAmt += ((if (stillGoal) 1f else 0f) - stillAmt) * a
            sprintAmt += ((if (sprintGoal) 1f else 0f) - sprintAmt) * a
            walkAmt += ((if (walkGoal) 1f else 0f) - walkAmt) * a
            val moveA = MathX.expApproach(dt, MOVE_TAU)
            moveAmt += ((if (movingNow) 1f else 0f) - moveAmt) * moveA
            runAmt += ((if (sprintingNow) 1f else 0f) - runAmt) * moveA
            if (table != null) {
                val snap = idleAmt < 0.01f
                for (i in 0 until CHANNELS) {
                    var goal = table.carry[i]
                    goal += (table.idle[i] - goal) * stillAmt
                    val walkPose = table.walk?.get(i) ?: (table.carry[i] + (table.sprint[i] - table.carry[i]) * WALK_SHARE)
                    goal += (walkPose - goal) * walkAmt
                    goal += (table.sprint[i] - goal) * sprintAmt
                    goal += (table.ready[i] - goal) * r
                    idleCur[i] = if (snap) goal else idleCur[i] + (goal - idleCur[i]) * a
                }
            }
            idleAmt += ((if (table == null) 0f else 1f) - idleAmt) * a
        }

        private fun refreshIdle(player: Player) {
            idleRight = player.mainArm == HumanoidArm.RIGHT
            val dx = if (owner == null) player.deltaMovement.x else player.x - player.xOld
            val dz = if (owner == null) player.deltaMovement.z else player.z - player.zOld
            val speedSq = (dx * dx + dz * dz).toFloat()
            val bodyYaw = player.yBodyRot * (Math.PI.toFloat() / 180f)
            forwardNow = speedSq > WALK_SPEED_SQ && (-dx * sin(bodyYaw) + dz * cos(bodyYaw)).toFloat() > FORWARD_DOT * sqrt(speedSq)
            val still = speedSq < STILL_SPEED_SQ && player.onGround() && !player.isSwimming && !player.isUsingItem && !player.swinging
            stillTicks = if (still) stillTicks + 1 else 0
            sprintingNow = player.isSprinting && speedSq > SPRINT_SPEED_SQ
            walkingNow = speedSq > WALK_SPEED_SQ && player.onGround() && !player.isSwimming && !player.isCrouching
            movingNow = speedSq > WALK_SPEED_SQ && !player.isSwimming
            if (!enabled() || !WynnOverhaulConfig.current.weaponIdleEnabled || player.isUsingItem) {
                idleTable = null
                return
            }
            val stack = player.mainHandItem
            if (stack !== idleStack) {
                idleStack = stack
                idleStackKey = keyFor(stack)
            }
            idleTable = stanceFor(idleStackKey)
        }

        private var seenRevision = 0

        private fun applyReload() {
            if (seenRevision == AnimationLibrary.revision) return
            seenRevision = AnimationLibrary.revision
            mirrorCache.clear()
            pose = null
            carry = null
            previewStack = null
            previewCached = emptyList()
            idleStack = null
            idleStackKey = null
            comboKey = null
        }

        fun tick(client: Minecraft) {
            applyReload()
            val player = client.player
            if (player == null) {
                pose = null
                carry = null
                suppress = false
                idleTable = null
                return
            }
            if (!player.swinging && !(pose?.key?.startsWith("SPELL:") == true && computeProgress() >= 0f)) suppress = false
            refreshIdle(player)
            watchHurt(player)
            watchGather(client, player)
            val config = WynnOverhaulConfig.current
            if (config.weaponAnimationPreview && enabled()) {
                val stack = player.mainHandItem
                val chain = if (stack.isEmpty) emptyList() else previewChain(stack)
                if (chain.isEmpty()) {
                    pose = null
                    previewLocal = 0f
                } else {
                    val scaled = config.weaponAnimationPreviewT.toFloat().coerceIn(0f, 1f) * chain.size
                    val index = minOf(scaled.toInt(), chain.size - 1)
                    pose = chain[index]
                    previewLocal = (scaled - index).coerceIn(0f, 0.999f)
                }
                right = player.mainArm == HumanoidArm.RIGHT
                carry = null
                amp = 1f
                readyUntil = System.nanoTime() + READY_NANOS
                previewing = true
                if (debugLine.isNotEmpty()) player.sendOverlayMessage(Component.literal(debugLine))
                return
            }
            if (previewing) {
                previewing = false
                pose = null
            }
        }

        fun begin(player: Player) {
            if (!enabled()) {
                pose = null
                carry = null
                suppress = false
                return
            }
            val hand = player.swingingArm ?: InteractionHand.MAIN_HAND
            val holdingTool = toolKey(player.getItemInHand(hand)) != null
            if ((owner == null && SpellComboGuard.isSuspended() && !holdingTool) || (pose?.key?.startsWith("SPELL:") == true && computeProgress() >= 0f)) {
                suppress = true
                return
            }
            val arm = if (hand == InteractionHand.MAIN_HAND) player.mainArm else player.mainArm.opposite
            val stack = player.getItemInHand(hand)
            val styleKey = keyFor(stack)?.let { if (it == "TOOL:ROD" && player.fishing != null) "TOOL:ROD_REEL" else it }
            val styleBase = styleKey?.let { basePose(it) }
            if (styleKey == null || styleBase == null) {
                pose = null
                carry = null
                suppress = false
                return
            }
            if (styleKey.startsWith("TOOL:ROD") && !previewing) {
                suppress = true
                return
            }
            val gathering = styleKey.startsWith("TOOL:")
            val now = System.nanoTime()
            val gap = if (lastBeginNanos == 0L) Float.MAX_VALUE else (now - lastBeginNanos) / 1_000_000_000f
            val aps = WynnWeapons.attacksPerSecond(stack)
            val minGap = if (aps != null) (MIN_GAP_FRACTION / aps.toFloat()).coerceIn(MIN_GAP_LOW, MIN_GAP_HIGH) else FALLBACK_GAP
            suppress = true
            val wasReady = now < readyUntil
            readyUntil = now + READY_NANOS
            if (gathering) {
                noteGatherSwing(now)
                if (pose?.key?.startsWith("TOOL:") == true && computeProgress() >= 0f && elapsedSeconds() < totalSeconds * GATHER_RESTART_SHARE) return
            } else if (gap < minGap) {
                return
            }
            val config = WynnOverhaulConfig.current
            val charge = if (!gathering && config.weaponAnimationCombo && sprintingNow && forwardNow) CHARGES[styleKey] else null
            val combo = if (charge != null) charge else advanceCombo(styleKey, if (config.weaponAnimationCombo) COMBOS[styleKey] else null, gap)
            val opener = if (!wasReady && comboIndex == 0 && config.weaponAnimationCombo) OPENERS[styleKey] else null
            val standard = (if (charge != null) stepPose(charge) else null) ?: (if (opener != null) stepPose(opener) else null) ?: stepPose(combo) ?: styleBase
            val chosen = standard
            cues = buildList {
                addAll(SFX[standard.key] ?: emptyList())
                if (combo.finisher) add(Cue(chosen.hit, FINISHER_CUE.sound, FINISHER_CUE.pitch, FINISHER_CUE.volume))
            }
            cueFired = BooleanArray(cues.size)
            cueFinisher = combo.finisher
            trailStrength = if (combo.finisher) 1.25f else 1f
            carry = captureCarry()
            val chained = carry != null
            if (!chained) carry = idleEntryCarry()
            pose = chosen
            right = arm == HumanoidArm.RIGHT
            startNanos = now
            pauseEnd = 0L
            reboundStart = 0L
            startP = if (chained) chosen.hit * CHAIN_SKIP else 0f
            lastBeginNanos = now
            if (config.weaponAnimationLunge && chosen.key.substringBefore(':') in LUNGE_FAMILIES && moveAmt < MOVE_DUST_MAX) (player as? LocalPlayer)?.let { WynnHitFeedback.stepDust(it) }
            val style = swingStyle()
            swingBody = style.body
            swingArc = style.arc
            val styled = chosen.seconds * style.speed
            pace = if (gap < PACE_MAX) (if (pace == 0f) gap else pace + (gap - pace) * PACE_SMOOTH) else 0f
            val expected = aps?.let { 1f / it.toFloat() } ?: pace
            val stretched = if (expected > 0f) maxOf(styled, minOf(expected * GAP_FRACTION, styled * STRETCH_MAX)) else styled
            val total = if (gathering) gatherStrokeSeconds(chosen) else minOf(stretched, maxOf(MIN_SECONDS, gap * GAP_FRACTION))
            totalSeconds = total
            hitSeconds = if (gathering) total * chosen.hit else minOf(HIT_SECONDS, total * HIT_SHARE)
            blendSeconds = if (chained) hitSeconds else maxOf(chosen.blend * style.blend, if (carry != null) IDLE_ENTRY_BLEND else 0f)
            val span = styled - MIN_SECONDS
            val target = if (span <= 0f) 1f else AMP_MIN + (1f - AMP_MIN) * ((total - MIN_SECONDS) / span).coerceIn(0f, 1f)
            val settle = MathX.expApproach(gap, AMP_SMOOTH_TAU)
            amp += (target - amp) * settle.coerceIn(0f, 1f)
        }

        private var gatherSwingNanos = 0L
        private var gatherInterval = 0f

        private fun noteGatherSwing(now: Long) {
            val since = if (gatherSwingNanos == 0L) Float.MAX_VALUE else (now - gatherSwingNanos) / 1_000_000_000f
            gatherSwingNanos = now
            if (since >= GATHER_CHAIN_SECONDS) {
                gatherInterval = 0f
                return
            }
            gatherInterval = if (gatherInterval == 0f) since else gatherInterval + (since - gatherInterval) * GATHER_INTERVAL_SMOOTH
        }

        private fun gatherStrokeSeconds(chosen: Pose): Float {
            val interval = if (gatherInterval > 0f) gatherInterval else chosen.seconds
            val beats = ceil(GATHER_MIN_STROKE / interval).coerceAtLeast(1f)
            return (beats * interval).coerceIn(GATHER_MIN_STROKE, GATHER_MAX_SECONDS)
        }

        private fun watchGather(client: Minecraft, player: LocalPlayer) {
            val key = if (enabled() && !previewing) toolKey(player.mainHandItem) else null
            GatherSession.tick(client, player, key)
            val started = GatherSession.takeStarted()
            if (started && key == "TOOL:ROD") POSES["TOOL:ROD"]?.let { beginSpell(player, it) }
            if (GatherSession.fishing()) readyUntil = System.nanoTime() + READY_NANOS
            if (GatherSession.takeCompleted() && key == "TOOL:ROD") POSES["TOOL:ROD_REEL"]?.let { beginSpell(player, it) }
        }

        fun beginSpell(player: LocalPlayer, chosen: Pose) {
            if (!enabled()) return
            suppress = true
            val now = System.nanoTime()
            readyUntil = now + READY_NANOS
            cues = SpellCasts.cuesOf(chosen) ?: SFX[chosen.key] ?: emptyList()
            cueFired = BooleanArray(cues.size)
            cueFinisher = false
            trailStrength = 1f
            carry = captureCarry()
            pose = chosen
            right = player.mainArm == HumanoidArm.RIGHT
            startNanos = now
            pauseEnd = 0L
            reboundStart = 0L
            startP = 0f
            lastBeginNanos = now
            val style = swingStyle()
            swingBody = style.body
            swingArc = style.arc
            totalSeconds = chosen.seconds * style.speed
            hitSeconds = totalSeconds * chosen.hit
            blendSeconds = if (carry != null) SPELL_BLEND_SECONDS else chosen.blend * style.blend
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
            val config = WynnOverhaulConfig.current
            val client = Minecraft.getInstance()
            val player = owner ?: client.player
            val level = client.level
            for (i in cues.indices) {
                if (cueFired[i] || t < cues[i].at) continue
                cueFired[i] = true
                if (!config.weaponAnimationSfx || player == null || level == null) continue
                playCue(level, player.x, player.y, player.z, cues[i], 1f + comboIndex * COMBO_PITCH_STEP, if (cueFinisher) 1.15f else 1f)
            }
        }

        private fun relaxWeight(): Float {
            if (previewing) return 1f
            val extra = elapsedSeconds() - totalSeconds - HOLD_SECONDS
            return if (extra <= 0f) 1f else 1f - smooth(extra / RELAX_SECONDS)
        }

        private fun elapsedSeconds(): Float {
            val now = System.nanoTime()
            settlePause(now)
            val reference = if (pauseEnd != 0L) pauseStart else now
            return (reference - startNanos) / 1_000_000_000f
        }

        private fun settlePause(now: Long) {
            if (pauseEnd == 0L || now < pauseEnd) return
            startNanos += pauseEnd - pauseStart
            pauseStart = 0L
            pauseEnd = 0L
        }

        private var flinchStart = 0L
        private var flinchAmp = 0f
        private var lastHurtTime = 0

        private fun watchHurt(player: LocalPlayer) {
            val hurt = player.hurtTime
            if (hurt > lastHurtTime && enabled() && WynnOverhaulConfig.current.weaponAnimationFlinch) {
                flinchStart = System.nanoTime()
                flinchAmp = WynnOverhaulConfig.current.hitFeedbackIntensity.coerceIn(0, 200) / 100f
            }
            lastHurtTime = hurt
        }

        fun flinch(): Float {
            if (flinchStart == 0L) return 0f
            val t = (System.nanoTime() - flinchStart) / 1_000_000_000f
            if (t > FLINCH_SECONDS) {
                flinchStart = 0L
                return 0f
            }
            val rise = (t / FLINCH_RISE).coerceAtMost(1f)
            return flinchAmp * rise * exp(-FLINCH_DECAY * maxOf(0f, t - FLINCH_RISE))
        }

        fun applyFlinch(out: FloatArray) {
            val f = flinch()
            if (f == 0f) return
            out[LEAN] -= FLINCH_LEAN * f
            out[REACH] -= FLINCH_REACH * f
        }

        fun hold(nanos: Long, strength: Float) {
            if (pose == null || previewing) return
            val now = System.nanoTime()
            settlePause(now)
            if (pauseEnd != 0L) return
            val limit = (totalSeconds * HOLD_SHARE * 1_000_000_000f).toLong()
            val held = if (totalSeconds < SHORT_SWING_SECONDS) 0L else minOf(nanos, limit)
            if (held >= MIN_HOLD_NANOS) {
                pauseStart = now
                pauseEnd = now + held
            }
            reboundStart = now + maxOf(held, 0L)
            reboundAmp = strength * amp
        }

        private fun rebound(): Float {
            if (reboundStart == 0L) return 0f
            val t = (System.nanoTime() - reboundStart) / 1_000_000_000f
            if (t < 0f) return 0f
            if (t > REBOUND_SECONDS) {
                reboundStart = 0L
                return 0f
            }
            return REBOUND_REACH * reboundAmp * exp(-REBOUND_DECAY * t) * cos(REBOUND_FREQ * t)
        }

        private fun strike(p: Pose, t: Float): Float {
            val hit = p.hit.coerceIn(0.05f, 0.95f)
            return if (t < hit) smooth(t / hit) else 1f - smooth((t - hit) / (1f - hit))
        }

        fun moveStrike(): Float =
            if (System.nanoTime() - strikeStamp > STRIKE_FRESH_NANOS) 0f else strikeWeight * moveAmt

        fun runShare(): Float = runAmt

        private fun previewChain(stack: ItemStack): List<Pose> {
            if (stack !== previewStack) {
                previewStack = stack
                val key = WeaponAnimationRegistry.styleFor(stack) ?: autoStyleKey(stack) ?: toolKey(stack)
                val steps = key?.let { COMBOS[it] }
                previewCached = if (steps != null) steps.mapNotNull { stepPose(it) } else listOfNotNull(key?.let { basePose(it) })
            }
            return previewCached
        }

        fun progress(): Float {
            val t = computeProgress()
            if (t >= 0f && !previewing) fireCues(t)
            return t
        }

        private fun computeProgress(): Float {
            if (pose == null) {
                carry = null
                return -1f
            }
            if (previewing) return previewLocal
            val current = pose ?: return -1f
            val tau = elapsedSeconds()
            if (tau >= totalSeconds) {
                if (!current.holdEnd || tau >= totalSeconds + HOLD_SECONDS + RELAX_SECONDS) {
                    pose = null
                    carry = null
                    return -1f
                }
                return 1f
            }
            val anticipation = if (WynnOverhaulConfig.current.weaponAnimationAnticipation) ANTICIPATION * ((amp - AMP_MIN) / (1f - AMP_MIN)).coerceIn(0f, 1f) else 0f
            return if (tau < hitSeconds) {
                val u = tau / hitSeconds
                val eased = if (startP == 0f) u + (u * u - u) * anticipation else u
                startP + eased * (current.hit - startP)
            } else {
                val u = (tau - hitSeconds) / (totalSeconds - hitSeconds)
                current.hit + (u + ((1f - (1f - u) * (1f - u)) - u) * anticipation) * (1f - current.hit)
            }
        }

        private fun idleEntryCarry(): FloatArray? {
            val table = idleTable ?: return null
            if (idleAmt < 0.5f) return null
            val out = FloatArray(CHANNELS) { Float.NaN }
            for (i in intArrayOf(HEAD_Y, TWIST)) {
                out[i] = idleCur[i]
                idleCur[i] = table.ready[i]
            }
            return out
        }

        private fun captureCarry(): FloatArray? {
            val previous = pose ?: return null
            val previousT = progress()
            if (previousT < 0f) return null
            val out = FloatArray(CHANNELS)
            sample(previous, previousT, REST_BASE, 1f, out)
            return out
        }

        private fun recoveryFade(p: Pose, t: Float): Float {
            if (moveAmt <= 0.001f) return 1f
            val start = if (p.recover > p.hit && p.recover < 1f) p.recover else p.hit + (1f - p.hit) * RECOVER_SHARE
            val r = ((t - start) / (1f - start)).coerceIn(0f, 1f)
            return 1f - RECOVER_MOVE_CUT * moveAmt * smooth(r)
        }

        private fun sampleRaw(p: Pose, t: Float, base: FloatArray, out: FloatArray) {
            for (i in 0 until CHANNELS) {
                out[i] = if ((i == HEAD_Y || i == TWIST) && p.spin) base[i] + p.curves[i].at(t, 0f) else p.curves[i].at(t, base[i])
            }
        }

        fun sample(p: Pose, t: Float, base: FloatArray, k: Float, out: FloatArray) {
            sampleRaw(p, t, base, raw)
            val from = carry
            if (from != null) {
                val w = smooth(elapsedSeconds() / blendSeconds)
                for (i in 0 until CHANNELS) {
                    val f = from[i]
                    if (f.isNaN()) continue
                    val yawLike = (i == HEAD_Y || i == TWIST) && !p.spin
                    raw[i] = f + (if (yawLike) wrapPi(raw[i] - f) else raw[i] - f) * w
                }
            }
            val scale = k * amp * relaxWeight() * recoveryFade(p, t)
            for (i in 0 until CHANNELS) {
                val sc = if ((i == HEAD_Y || i == TWIST) && p.spin) 1f else scale * when (CHANNEL_KIND[i]) {
                    1 -> swingBody
                    2 -> 1f
                    else -> swingArc
                }
                out[i] = base[i] + (raw[i] - base[i]) * sc
            }
            out[REACH] += rebound()
            val weight = strike(p, t)
            strikeWeight = weight * scale
            strikeStamp = System.nanoTime()
        }
    }

    private val LOCAL_CLOCK = Clock()
    private var clock: Clock = LOCAL_CLOCK
    private val remotes = HashMap<Int, Clock>()
    private val LOCAL_CLOCK_TICK = { client: Minecraft ->
        LOCAL_CLOCK.tick(client)
        tickRemotes(client)
    }
    private const val REMOTE_RANGE_SQ = 48.0 * 48.0

    private fun tickRemotes(client: Minecraft) {
        val level = client.level
        val me = client.player
        if (level == null || me == null || !enabled() || !WynnOverhaulConfig.current.weaponAnimationOthers) {
            remotes.clear()
            return
        }
        val seen = HashSet<Int>()
        for (other in level.players()) {
            if (other === me || other.distanceToSqr(me) > REMOTE_RANGE_SQ) continue
            seen.add(other.id)
            remotes.getOrPut(other.id) { Clock() }.tickRemote(other)
        }
        remotes.keys.retainAll(seen)
    }

    private fun clockFor(state: HumanoidRenderState): Clock? {
        if (state !is AvatarRenderState) return null
        val me = Minecraft.getInstance().player ?: return null
        return if (state.id == me.id) LOCAL_CLOCK else remotes[state.id]
    }

    private fun remoteKey(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val entry = WeaponAnimationRegistry.entryFor(stack)
        val key = entry?.style?.takeIf { it != WeaponAnimationRegistry.AUTO }
            ?: WynnSkinStyles.styleFor(stack)
            ?: entry?.autoStyle?.takeIf { it.isNotEmpty() }
        return key?.takeIf { it in POSES || it in COMBOS }
    }

    @JvmStatic
    fun onRemoteSwing(player: Player) {
        if (!enabled() || !WynnOverhaulConfig.current.weaponAnimationOthers) return
        remotes[player.id]?.begin(player)
    }

    @JvmStatic
    fun suppressVanillaFor(entity: LivingEntity): Boolean {
        if (entity is LocalPlayer) return LOCAL_CLOCK.suppress
        return remotes[entity.id]?.suppress == true
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { LOCAL_CLOCK_TICK(it) }
        ClientTickEvents.END_CLIENT_TICK.register(::playTails)
    }

    @JvmStatic
    fun suppressVanilla(): Boolean = clock.suppress

    @JvmStatic
    fun trailRight(): Boolean = clock.right

    @JvmStatic
    fun trailStrength(): Float = clock.trailStrength

    fun holdAtImpact(nanos: Long, strength: Float) = clock.hold(nanos, strength)

    @JvmStatic
    fun trailActive(): Boolean {
        val pose = clock.pose ?: return false
        val family = pose.key.substringBefore(':')
        if (family == "WAND" || family == "RELIK") return false
        return trailSpan(pose.key) != null
    }

    @JvmStatic
    fun onSwing(player: LocalPlayer) {
        if (WynnOverhaulConfig.current.weaponAnimationPreview) return
        clock.begin(player)
    }

    @JvmStatic
    fun onSpellCast(name: String) {
        val config = WynnOverhaulConfig.current
        if (!config.weaponAnimationSpells || config.weaponAnimationPreview) return
        val player = Minecraft.getInstance().player ?: return
        val styleKey = resolveKey(player.mainHandItem)
        val chosen = SpellCasts.choose(styleKey, name.lowercase().filter { it.isLetter() }) ?: return
        clock.beginSpell(player, chosen)
    }

    fun styleKeys(): List<String> {
        val combos = COMBOS
        val steps = combos.values.flatMapTo(HashSet()) { chain -> chain.map { it.key } }
        CHARGES.values.forEach { steps.add(it.key) }
        val movesets = combos.keys.filter { !it.startsWith("SPELL:") }
        val loose = POSES.keys.filter { !it.startsWith("SPELL:") && it !in combos && it !in steps }
        return listOf(WeaponAnimationRegistry.AUTO) + movesets + loose
    }

    fun styleLabel(key: String): String =
        if (key == WeaponAnimationRegistry.AUTO) "Auto" else LABELS[key] ?: key

    internal fun classKind(stack: ItemStack): GearSlotKind? {
        if (WynnWeapons.attacksPerSecond(stack) == null) return null
        val line = WynnItemRarity.loreLines(stack).firstOrNull { it.contains("Class Type", ignoreCase = true) } ?: return null
        val name = CLASS_NAME.find(line)?.value?.lowercase()?.replace(" ", "") ?: return null
        return CLASS_KINDS[name]
    }

    internal fun autoKind(stack: ItemStack): GearSlotKind? =
        WynnGearKind.of(stack)?.takeIf { it.name in POSES } ?: classKind(stack)

    fun autoStyleKey(stack: ItemStack): String? {
        val kind = autoKind(stack) ?: return null
        val skin = WynnSkinStyles.styleFor(stack)
        return if (skin != null && skin.substringBefore(':') == kind.name && (skin in POSES || skin in COMBOS)) skin else kind.name
    }

    fun isRanged(stack: ItemStack): Boolean = autoStyleKey(stack)?.startsWith("BOW") == true

    private fun enabled(): Boolean =
        WynnOverhaulConfig.current.weaponAnimationsEnabled && WynnOverhaulGate.isInGame()

    private fun toolKey(stack: ItemStack): String? {
        if (!WynnOverhaulConfig.current.weaponAnimationTools) return null
        return GatheringTool.kind(stack)?.let { "TOOL:$it" }
    }

    private fun resolveKey(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val assigned = WeaponAnimationRegistry.styleFor(stack) ?: autoStyleKey(stack) ?: toolKey(stack)
        return assigned?.takeIf { it in POSES || it in COMBOS }
    }

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
    private val BOW_FRAME = Quaternionf().rotationX(-Math.PI.toFloat() / 2f)
    private val twistQuat = Quaternionf()
    private val clipQuat = Quaternionf()
    private val clipPoint = Vector3f()

    private var headA = 0f
    private var headY = 0f
    private var headTwist = 0f
    private var headYaw = 0f
    private var headSide = 1f
    private var handShift = 0f
    private val shiftVec = Vector3f()
    private var lastReach = 0f
    private var headActive = false
    private var headEntity = -1
    private var headBow = false
    private var headFrame = -1L
    private var debugLine = ""
    private var debugKey = ""
    private var debugT = 0f
    private var debugRestA = 0f
    private var debugRestY = 0f
    private val debugDir = Vector3f()

    @JvmStatic
    fun combatElbow(right: Boolean): Float {
        val main = (right == (headSide > 0f))
        val reach = if (main) lastReach else lastReach * OFFHAND_REACH_SHARE
        return (ELBOW_REST_FLEX + ELBOW_REACH_FLEX * reach).coerceIn(ELBOW_MIN_FLEX, ELBOW_MAX_FLEX)
    }

    private const val OFFHAND_REACH_SHARE = 0.7f
    private const val ELBOW_REST_FLEX = 0.32f
    private const val ELBOW_REACH_FLEX = 0.13f
    private const val ELBOW_MIN_FLEX = 0.08f
    private const val ELBOW_MAX_FLEX = 0.85f

    @JvmStatic
    fun claimsArms(state: HumanoidRenderState): Boolean {
        val target = clockFor(state) ?: return false
        if (LocomotionAnimations.unseen(state as AvatarRenderState)) return false
        return (target.progress() >= 0f && target.pose != null) || target.idleActive()
    }

    @JvmStatic
    fun applyThirdPerson(model: HumanoidModel<*>, state: HumanoidRenderState): Boolean {
        val target = clockFor(state) ?: return false
        val previous = clock
        clock = target
        try {
            return applyThirdPersonWith(model, state as AvatarRenderState)
        } finally {
            clock = previous
        }
    }

    private fun applyThirdPersonWith(model: HumanoidModel<*>, state: AvatarRenderState): Boolean {
        if (LocomotionAnimations.unseen(state)) return false
        clock.idleStep()
        val t = clock.progress()
        val pose = clock.pose
        val swinging = t >= 0f && pose != null
        if (!swinging && !clock.idleActive()) {
            headActive = false
            clock.springReady = false
            return false
        }

        val k = 1f
        val right = if (swinging) clock.right else clock.idleRight
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
        for (i in ROOT_F..FOOT_RU) base[i] = 0f

        restQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        restDir.set(HEAD_REST).rotate(restQuat)
        base[HEAD_A] = asin(restDir.y.coerceIn(-1f, 1f))
        base[HEAD_Y] = atan2(s * restDir.x, -restDir.z)

        clock.blendIdle(base)
        clock.applyGait(base, state.walkAnimationPos, state.walkAnimationSpeed, s)
        val breath = clock.breath()
        val sway = clock.sway()
        base[ACROSS] += sway
        base[HEAD_Y] += sway * 0.6f
        base[PITCH] += breath
        base[OFF_PITCH] += breath * 0.8f
        base[HEAD_A] += breath * 0.5f

        val out = outBuffer
        if (pose != null && swinging) clock.sample(pose, t, base, k, out) else base.copyInto(out)
        if (!swinging && FirstPersonBody.relaxArms(state)) {
            out[PITCH] += FirstPersonBody.ARM_DROP
            out[OFF_PITCH] += FirstPersonBody.ARM_DROP
        }
        clock.applyFlinch(out)
        val moving = clock.moveAmount()
        val drive = clock.moveStrike()
        val run = clock.runShare()
        val charge = clock.charging() * CHARGE_KEEP
        val cut = moving * (1f - charge)
        out[BODY_Y] *= 1f - cut * MOVE_BODY_CUT
        out[BODY_R] *= 1f - cut * MOVE_BODY_CUT
        out[LEAN] = out[LEAN] * (1f - cut * MOVE_LEAN_CUT) + (DRIVE_LEAN_WALK + (DRIVE_LEAN_RUN - DRIVE_LEAN_WALK) * run) * drive * (1f - charge)
        out[TURN] *= 1f - cut * (MOVE_TURN_CUT_WALK + (MOVE_TURN_CUT_RUN - MOVE_TURN_CUT_WALK) * run)
        springTip(out)

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
        applyBody(model, yaw, out[LEAN])
        plantLegs(model, drive)
        applyTorsoTwist(model, out)
        val mainShoulder = if (right) -SHOULDER_X else SHOULDER_X
        keepOutOfBody(main, mainShoulder, yaw)
        val guardKey = if (swinging) pose.key else clock.idleKey()
        if (guardKey != null && guardKey in HAND_SHIFT) {
            val guardSpan = trailSpan(guardKey)
            if (guardSpan != null) keepShaftClear(main, s, yaw, out, guardSpan, HAND_SHIFT.getValue(guardKey))
        }
        guardBlocks(state, main, s, yaw, out, if (guardKey != null) trailSpan(guardKey)?.second else null)
        val offShoulder = -mainShoulder
        val gripWeight = out[GRIP].coerceIn(0f, 1f)
        val gripOffset = clock.idleGripOffset() ?: pose?.gripOffset ?: 7f
        val offX = off.xRot
        val offY = off.yRot
        val offZ = off.zRot
        val gripFits = { slide: Float, relax: Float ->
            off.xRot = offX
            off.yRot = offY
            off.zRot = offZ
            applyGrip(main, off, s, yaw, out[HEAD_A], out[HEAD_Y], gripWeight * relax, (gripOffset + slide).coerceAtLeast(minOf(GRIP_MIN_OFFSET, gripOffset)))
            !armInsideBody(off, offShoulder, yaw)
        }
        var slideGoal = 0f
        var relaxGoal = 1f
        if (!gripFits(0f, 1f)) {
            var found = false
            for (i in 1..GRIP_SLIDE_STEPS) {
                val d = i * GRIP_SLIDE_STEP
                if (gripFits(d, 1f)) {
                    slideGoal = d
                    found = true
                    break
                }
                if (gripFits(-d, 1f)) {
                    slideGoal = -d
                    found = true
                    break
                }
            }
            if (!found) {
                relaxGoal = if (!gripFits(0f, 0f)) 0f else {
                    var lo = 0f
                    var hi = 1f
                    repeat(GRIP_BISECT) {
                        val mid = (lo + hi) * 0.5f
                        if (gripFits(0f, mid)) lo = mid else hi = mid
                    }
                    lo
                }
            }
        }
        val now = System.nanoTime()
        val dt = if (clock.gripRelaxNanos == 0L) 1f else ((now - clock.gripRelaxNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        clock.gripRelaxNanos = now
        val blend = MathX.expApproach(dt, GRIP_RELAX_TAU)
        clock.gripRelax += (relaxGoal - clock.gripRelax) * blend
        clock.gripSlide += (slideGoal - clock.gripSlide) * blend
        gripFits(clock.gripSlide, clock.gripRelax)
        keepOutOfBody(off, offShoulder, yaw)
        RigLegs.submitClip(
            state.id, if (swinging) 1f - moving else 0f, -s * out[HIPS_Y], out[ROOT_F], out[ROOT_S], out[ROOT_H],
            out[FOOT_LF], out[FOOT_LS], out[FOOT_LU], out[FOOT_RF], out[FOOT_RS], out[FOOT_RU],
        )

        headA = out[HEAD_A]
        headY = out[HEAD_Y]
        headTwist = out[TWIST]
        headYaw = yaw
        headSide = s
        lastReach = out[REACH]
        headActive = true
        val shiftKey = if (swinging) pose.key else clock.idleKey()
        headEntity = state.id
        headBow = shiftKey != "BOW:FIREARM" && (clock.owner ?: Minecraft.getInstance().player)?.let { ActiveWeapon.isRanged(it) } == true
        handShift = (HAND_SHIFT[shiftKey] ?: 0f) + (HAND_SPRINT_EXTRA[shiftKey] ?: 0f) * clock.sprintAmount()
        headFrame = System.nanoTime()
        if (WynnOverhaulConfig.current.weaponAnimationPreview) {
            debugKey = pose?.key ?: "IDLE"
            debugT = t
            debugRestA = base[HEAD_A]
            debugRestY = base[HEAD_Y]
        }
        return true
    }

    private fun keepShaftClear(main: ModelPart, side: Float, yaw: Float, out: FloatArray, span: Pair<Float, Float>, shift: Float) {
        armQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        gripPoint.set(0f, GRIP_Y, GRIP_Z).rotate(armQuat).add(main.x, main.y, main.z)
        val outward = if (cos(out[HEAD_Y]) >= 0f) -1f else 1f
        val from = minOf(span.first + shift, -SHAFT_TAIL)
        val to = span.second + shift
        repeat(SHAFT_GUARD_STEPS) {
            val cosA = cos(out[HEAD_A])
            gripShaft.set(side * sin(out[HEAD_Y]) * cosA, sin(out[HEAD_A]), -cos(out[HEAD_Y]) * cosA).rotateY(yaw)
            if (!shaftHitsBody(gripPoint, gripShaft, yaw, from, to)) return
            out[HEAD_Y] += outward * SHAFT_GUARD_STEP
        }
    }

    private fun shaftHitsBody(origin: Vector3f, dir: Vector3f, yaw: Float, from: Float, to: Float): Boolean {
        var t = from
        while (t <= to) {
            clipPoint.set(origin).fma(t, dir).rotateY(-yaw)
            val margin = if (t > to - SHAFT_BLADE_LENGTH) SHAFT_BLADE_MARGIN else SHAFT_MARGIN
            val x = abs(clipPoint.x)
            val y = clipPoint.y
            val z = abs(clipPoint.z)
            if (x < BODY_HALF_WIDTH + margin && y > 0f && y < BODY_BOTTOM - 2f && z < 2f + margin) return true
            if (x < 4f + margin && y > -8f - margin && y < margin && z < 4f + margin) return true
            t += SHAFT_SAMPLE
        }
        return false
    }

    private val blockFrom = DoubleArray(3)
    private val blockTo = DoubleArray(3)

    private fun worldPoint(state: AvatarRenderState, point: Vector3f, out: DoubleArray) {
        val rad = state.bodyRot * (Math.PI.toFloat() / 180f)
        val sinR = sin(rad).toDouble()
        val cosR = cos(rad).toDouble()
        val left = point.x / 16.0
        val back = point.z / 16.0
        out[0] = state.x + cosR * left + sinR * back
        out[1] = state.y + (MODEL_FEET - point.y) / 16.0
        out[2] = state.z + sinR * left - cosR * back
    }

    private fun guardBlocks(state: AvatarRenderState, main: ModelPart, side: Float, yaw: Float, out: FloatArray, tip: Float?) {
        val entity = clock.owner ?: Minecraft.getInstance().player
        var target = 0f
        if (entity != null && WynnOverhaulConfig.current.weaponAnimationBlockPushback && state.distanceToCameraSq < BLOCK_GUARD_FAR_SQ) {
            armQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
            gripPoint.set(0f, GRIP_Y, GRIP_Z).rotate(armQuat).add(main.x, main.y, main.z)
            val cosA = cos(out[HEAD_A])
            gripShaft.set(side * sin(out[HEAD_Y]) * cosA, sin(out[HEAD_A]), -cos(out[HEAD_Y]) * cosA).rotateY(yaw)
            val length = tip ?: BLOCK_TIP_DEFAULT
            worldPoint(state, gripPoint, blockFrom)
            clipPoint.set(gripPoint).fma(length, gripShaft)
            worldPoint(state, clipPoint, blockTo)
            val hit = entity.level().clip(
                ClipContext(
                    Vec3(blockFrom[0], blockFrom[1], blockFrom[2]), Vec3(blockTo[0], blockTo[1], blockTo[2]),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity,
                ),
            )
            if (hit.type == HitResult.Type.BLOCK && !hit.isInside) {
                val reached = Math.sqrt(
                    (hit.location.x - blockFrom[0]) * (hit.location.x - blockFrom[0]) +
                        (hit.location.y - blockFrom[1]) * (hit.location.y - blockFrom[1]) +
                        (hit.location.z - blockFrom[2]) * (hit.location.z - blockFrom[2]),
                ).toFloat() * 16f
                target = (length - reached - BLOCK_CUT_IN).coerceIn(0f, BLOCK_PUSH_MAX)
            }
        }
        val now = System.nanoTime()
        val dt = if (clock.blockPushNanos == 0L) 0f else ((now - clock.blockPushNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        clock.blockPushNanos = now
        clock.blockPush += (target - clock.blockPush) * MathX.expApproach(dt, if (target > clock.blockPush) BLOCK_PUSH_IN else BLOCK_PUSH_OUT)
        if (clock.blockPush < 0.02f) return
        armQuat.rotationZYX(main.zRot, main.yRot, main.xRot)
        val cosA = cos(out[HEAD_A])
        gripShaft.set(side * sin(out[HEAD_Y]) * cosA, sin(out[HEAD_A]), -cos(out[HEAD_Y]) * cosA).rotateY(yaw)
        main.x -= gripShaft.x * clock.blockPush
        main.y -= gripShaft.y * clock.blockPush
        main.z -= gripShaft.z * clock.blockPush
    }

    private const val MODEL_FEET = 24f
    private const val BLOCK_GUARD_FAR_SQ = 576.0
    private const val BLOCK_TIP_DEFAULT = 14f
    private const val BLOCK_CUT_IN = 2.5f
    private const val BLOCK_PUSH_MAX = 9f
    private const val BLOCK_PUSH_IN = 0.035f
    private const val BLOCK_PUSH_OUT = 0.12f

    private const val SHAFT_GUARD_STEPS = 30
    private const val SHAFT_GUARD_STEP = 0.04f
    private const val SHAFT_SAMPLE = 3f
    private const val SHAFT_TAIL = 14f
    private const val SHAFT_MARGIN = 1.5f
    private const val SHAFT_BLADE_MARGIN = 4f
    private const val SHAFT_BLADE_LENGTH = 20f

    private fun leanPart(part: ModelPart, yaw: Float, lean: Float) {
        clipPoint.set(part.x, part.y - HIP_Y, part.z).rotateY(-yaw).rotateX(lean).rotateY(yaw)
        part.x = clipPoint.x
        part.y = clipPoint.y + HIP_Y
        part.z = clipPoint.z
    }

    private fun plantLegs(model: HumanoidModel<*>, drive: Float) {
        if (drive <= 0.001f) return
        val keep = 1f - PLANT_CUT * drive
        model.rightLeg.xRot *= keep
        model.leftLeg.xRot *= keep
    }

    private fun applyTorsoTwist(model: HumanoidModel<*>, out: FloatArray) {
        model.body.yRot += out[BODY_Y]
        model.body.zRot += out[BODY_R]
    }

    private fun applyBody(model: HumanoidModel<*>, yaw: Float, lean: Float) {
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
        if (state !is AvatarRenderState || state.id != headEntity) return
        if (!headActive || (arm == HumanoidArm.RIGHT) != (headSide > 0f)) return
        if (System.nanoTime() - headFrame > 100_000_000L) return

        val cosA = cos(headA)
        target.set(headSide * sin(headY) * cosA, sin(headA), -cos(headY) * cosA).rotateY(headYaw)
        armQuat.rotationZYX(part.zRot, part.yRot, part.xRot)
        val bend = LimbBend.freshBend(part)
        if (bend != 0f) armQuat.rotateX(bend)
        local.set(target).rotate(armQuat.invert())
        wristQuat.rotationTo(HEAD_REST, local)
        twistQuat.rotationAxis(headTwist * headSide, HEAD_REST)
        itemQuat.set(ITEM_FRAME_INV).mul(wristQuat).mul(twistQuat)
        if (headBow) itemQuat.mul(BOW_FRAME)
        itemQuat.mul(ITEM_FRAME)
        if (handShift != 0f) {
            shiftVec.set(local).rotate(ITEM_FRAME_INV).mul(handShift / MODEL_UNIT)
            stack.translate(shiftVec.x, shiftVec.y, shiftVec.z)
        }
        stack.mulPose(itemQuat)
        if (WynnOverhaulConfig.current.weaponAnimationPreview) {
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
        val t = clock.progress()
        val pose = clock.pose
        if (t < 0f || pose == null || (arm == HumanoidArm.RIGHT) != clock.right) return false

        val out = outBuffer
        val fpBase = firstPersonBase
        REST_BASE.copyInto(fpBase)
        clock.blendIdle(fpBase)
        clock.sample(pose, t, fpBase, 1f, out)
        out[REACH] -= clock.flinch() * FLINCH_REACH
        clock.rawAt(pose, 0f, fpBase, fpStart)
        poseFirstPersonSwing(stack, sign, out, fpStart, t)
        return true
    }

    @JvmStatic
    fun applyFirstPersonIdle(stack: PoseStack, arm: HumanoidArm, sign: Int) {
        clock.idleStep()
        if (!clock.idleActive() || (arm == HumanoidArm.RIGHT) != clock.idleRight) return
        if (clock.pose != null && clock.progress() >= 0f && (arm == HumanoidArm.RIGHT) == clock.right) return
        val held = clock.heldAmount()
        val breath = clock.breath() + clock.sprintBob()
        val sway = clock.sway()
        val scale = 1f - (1f - FP_SCALE) * held
        stack.translate(sway * FP_SWAY_MOVE, breath, clock.flinch() * FLINCH_REACH * FP_UNIT)
        stack.scale(scale, scale, scale)
        fpMix.rotationX(-breath * FP_BREATH_TILT).rotateZ(sway * FP_SWAY_TILT * sign)
        stack.mulPose(fpMix)
    }

    private const val FP_UNIT = 1f / 16f
    private const val FP_EYE_Y = -1.9f
    private const val FP_HAND_GAIN = 1f
    private const val FP_LIMIT_OUT = 0.18f
    private const val FP_LIMIT_IN = 0.7f
    private const val FP_LIMIT_UP = 0.55f
    private const val FP_LIMIT_DOWN = 0.06f
    private const val FP_LIMIT_BACK = 0.1f
    private const val FP_LIMIT_FORWARD = 0.5f
    private const val FP_MIN_FORWARD = 0.4f
    private const val FP_CONE = 1.2f
    private const val FP_ROLL = 1.2f
    private const val FP_SCALE = 0.82f
    private const val FP_SHRINK = 0.1f
    private const val FP_RAMP_IN = 0.08f
    private const val FP_RAMP_OUT = 0.16f
    private const val FP_SWAY_MOVE = 0.6f
    private const val FP_SWAY_TILT = 0.5f
    private const val FP_BREATH_TILT = 0.5f
    private val fpArm = Quaternionf()
    private val fpQuat = Quaternionf()
    private val fpMix = Quaternionf()
    private val fpStart = FloatArray(CHANNELS)
    private val fpPosNow = Vector3f()
    private val fpPosStart = Vector3f()
    private val fpDirNow = Vector3f()
    private val fpDirStart = Vector3f()

    private fun fpGripPos(ch: FloatArray, s: Float, out: Vector3f) {
        val yaw = -s * ch[TURN]
        fpArm.rotationZYX(s * ch[ABDUCT], -s * ch[ACROSS] + yaw, ch[PITCH])
        out.set(0f, GRIP_Y, GRIP_Z).rotate(fpArm)
        out.add(-s * cos(yaw) * SHOULDER_X, 2f, s * sin(yaw) * SHOULDER_X)
        out.z += ch[REACH]
    }

    private fun fpRig(ch: FloatArray, s: Float, pos: Vector3f, dir: Vector3f) {
        fpGripPos(ch, s, pos)
        val cosA = cos(ch[HEAD_A])
        dir.set(s * sin(ch[HEAD_Y]) * cosA, sin(ch[HEAD_A]), -cos(ch[HEAD_Y]) * cosA).rotateY(-s * ch[TURN])
        val lean = ch[LEAN]
        if (lean != 0f) {
            pos.y -= HIP_Y
            pos.rotateX(lean)
            pos.y += HIP_Y
            dir.rotateX(lean)
        }
        pos.set(-pos.x * FP_UNIT, -(pos.y - FP_EYE_Y) * FP_UNIT, pos.z * FP_UNIT)
        dir.set(-dir.x, -dir.y, dir.z)
        if (dir.z > -FP_MIN_FORWARD) dir.z = -FP_MIN_FORWARD
        dir.normalize()
    }

    private fun softLimit(v: Float, positive: Float, negative: Float): Float =
        if (v >= 0f) positive * kotlin.math.tanh(v / positive) else -negative * kotlin.math.tanh(-v / negative)

    private fun poseFirstPersonSwing(stack: PoseStack, sign: Int, ch: FloatArray, start: FloatArray, t: Float) {
        val s = sign.toFloat()
        fpRig(ch, s, fpPosNow, fpDirNow)
        fpRig(start, s, fpPosStart, fpDirStart)
        val dx = softLimit((fpPosNow.x - fpPosStart.x) * s * FP_HAND_GAIN, FP_LIMIT_OUT, FP_LIMIT_IN) * s
        val dy = softLimit((fpPosNow.y - fpPosStart.y) * FP_HAND_GAIN, FP_LIMIT_UP, FP_LIMIT_DOWN)
        val dz = softLimit((fpPosNow.z - fpPosStart.z) * FP_HAND_GAIN, FP_LIMIT_BACK, FP_LIMIT_FORWARD)

        fpQuat.rotationTo(fpDirStart, fpDirNow)
        val half = kotlin.math.acos(fpQuat.w.coerceIn(-1f, 1f))
        val sinHalf = sin(half)
        if (sinHalf > 1.0e-4f) {
            val compressed = FP_CONE * kotlin.math.tanh(2f * half / FP_CONE)
            fpQuat.rotationAxis(compressed, fpQuat.x / sinHalf, fpQuat.y / sinHalf, fpQuat.z / sinHalf)
        } else {
            fpQuat.identity()
        }
        val roll = FP_ROLL * kotlin.math.tanh(-s * (ch[TWIST] - start[TWIST]) / FP_ROLL)
        fpMix.rotationAxis(roll, fpDirNow.x, fpDirNow.y, fpDirNow.z)
        fpMix.mul(fpQuat)

        val ramp = smooth(minOf(t / FP_RAMP_IN, (1f - t) / FP_RAMP_OUT, 1f).coerceAtLeast(0f))
        val held = clock.heldAmount()
        val scale = (1f - (1f - FP_SCALE) * held) * (1f - FP_SHRINK * ramp)
        stack.translate(dx, dy, dz)
        stack.scale(scale, scale, scale)
        stack.mulPose(fpMix)
    }
}

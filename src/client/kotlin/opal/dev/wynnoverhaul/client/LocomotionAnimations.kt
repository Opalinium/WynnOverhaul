package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.entity.Avatar
import net.minecraft.world.item.ItemUseAnimation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object LocomotionAnimations {
    val styleKeys: List<String> get() = AnimationLibrary.tables().locomotion.styles.keys.toList()

    fun labelOf(key: String): String = AnimationLibrary.tables().locomotion.styles[key]?.label ?: key


    private const val AIR_DELAY = 0.1f
    private const val STEP_DIR_MIN = 0.02f
    private const val BLOCK_PX = 16f
    private const val STRIDE_RATE_MIN = 0.02f
    private const val STRIDE_K_MIN = 2f
    private const val STRIDE_K_MAX = 14f
    private const val STRIDE_SMOOTH = 0.15f
    private const val GAIT_DUTY_MIN = 0.3f
    private const val GAIT_TRAVEL_MAX = 20f
    private const val SWING_ARC_SKEW = 0.8f
    private const val LAND_MIN_FALL = 0.12f
    private const val LAND_FALL_SPAN = 0.55f
    private const val LAND_TAU = 0.2f
    private const val TAU = 0.08f
    private const val STALE_NANOS = 5_000_000_000L
    private const val GAIT_PHASE = 0.6662f
    private const val CROUCH_LIFT_CUT = 0.5f
    private const val CROUCH_STRIDE_CUT = 0.35f
    private const val RIG_DROP = 0
    private const val RIG_LIFT = 1
    private const val RIG_ROOT_F = 2
    private const val RIG_FOOT_LF = 3
    private const val RIG_FOOT_LS = 4
    private const val RIG_FOOT_LU = 5
    private const val RIG_FOOT_RF = 6
    private const val RIG_FOOT_RS = 7
    private const val RIG_FOOT_RU = 8
    private const val MASTER = 0.7f
    private const val GATE_MIN = 0.001f
    private const val PACE_REFERENCE = 0.22f
    private const val LAUNCH_MIN_VY = 0.15f
    private const val LAUNCH_SECONDS = 0.34f
    private const val OVERDRIVE_START = 1.25f
    private const val KNEE_BACK = 0.95f
    private const val KNEE_FORWARD = 0.2f
    private const val KNEE_TUCK = 0.6f
    private const val KNEE_LAND = 0.5f
    private const val KNEE_CROUCH = 0.4f
    private const val KNEE_SIT = 1.25f
    private const val KNEE_LIMIT = 1.4f
    private const val UPPER_ARM = 4f
    private const val FOREARM = 6f
    private const val LAUNCH_KNEE_COMPRESS = 0.8f
    private const val LAUNCH_KNEE_DRIVE = 0.9f
    private const val ELBOW_REST = 0.1f
    private const val ELBOW_FORWARD = 0.35f
    private const val ELBOW_LIMIT = 0.5f
    private const val ELBOW_RUN = 0.7f
    private const val ELBOW_RUN_EXTRA = 0.8f
    private const val PIVOT_LIMIT = 3f
    private const val JOINT_SLACK = 0.6f
    private const val ARM_SLACK = 0.9f
    private const val PITCH_LIMIT = 0.9f
    private const val ROLL_LIMIT = 0.3f
    private const val YAW_LIMIT = 0.45f
    private const val HIP_YAW_LIMIT = 0.4f
    private const val HIP_TILT_LIMIT = 0.3f
    private const val LIFT_LIMIT = 1.4f
    private const val DIP_LIMIT = 2.6f
    private const val HEAD_PITCH_LIMIT = 0.4f
    private const val HEAD_YAW_LIMIT = 0.3f
    private const val HEAD_ROLL_LIMIT = 0.2f
    private const val ARM_PITCH_LIMIT = 1.1f
    private const val CLIMB_ARM_LIMIT = 2.4f
    private const val ARM_YAW_LIMIT = 0.3f
    private const val ARM_ROLL_LIMIT = 0.6f
    private const val LEG_PITCH_LIMIT = 1.0f
    private const val LEG_ROLL_LIMIT = 0.3f
    private const val BODY_PITCH_LIMIT = 0.55f
    private const val BODY_YAW_LIMIT = 0.25f
    private const val BODY_ROLL_LIMIT = 0.18f

    private const val ANIMATE_FAR_SQ = 4096.0

    private const val USE_NONE = 0
    private const val USE_CONSUME = 1
    private const val USE_BLOCK = 2

    private class Body {
        var lastNanos = 0L
        var seenNanos = 0L
        var onGround = true
        var vy = 0f
        var hSpeed = 0f
        var stepX = 0f
        var stepZ = 0f
        var moveFwd = 0f
        var moveSide = 0f
        var strideK = 6f
        var climbing = false
        var sprinting = false
        var hurt = 0f
        var useKind = USE_NONE
        var styleSeed = 0
        var lastAirVy = 0f
        var airTime = 0f
        var wAir = 0f
        var wRise = 0f
        var wFall = 0f
        var wLand = 0f
        var pace = 0f
        var wasGround = true
        var launchAge = 9f
        var leadRight = true
        var wMove = 0f
        var wSprint = 0f
        var wClimb = 0f
        var climbMove = 0f
        var wSwim = 0f
        var wTread = 0f
        var wCrawl = 0f
        var wRide = 0f
        var wFly = 0f
        var wUse = 0f
        var wBlock = 0f
        var wHurt = 0f
        var wDead = 0f
        var wCrouch = 0f
        var wClaim = 0f
        var wStroke = 0f
        var lastBodyRot = 0f
        var rotSeen = false
        var turnRate = 0f
    }

    private val bodies = it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Body>()

    @JvmStatic
    fun capture(entity: Avatar, state: AvatarRenderState) {
        if (!enabled()) return
        val body = bodies.get(state.id) ?: Body().also { bodies.put(state.id, it) }
        val delta = entity.deltaMovement
        body.vy = delta.y.toFloat()
        body.hSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z).toFloat()
        body.stepX = (entity.x - entity.xOld).toFloat()
        body.stepZ = (entity.z - entity.zOld).toFloat()
        body.onGround = entity.onGround()
        body.climbing = entity.onClimbable() && !entity.onGround()
        body.sprinting = entity.isSprinting
        body.hurt = entity.hurtTime / 10f
        body.useKind = if (!entity.isUsingItem) USE_NONE else when (entity.useItem.useAnimation) {
            ItemUseAnimation.EAT, ItemUseAnimation.DRINK -> USE_CONSUME
            ItemUseAnimation.BLOCK -> USE_BLOCK
            else -> USE_NONE
        }
        body.styleSeed = entity.uuid.hashCode() * -1640531535
        body.seenNanos = System.nanoTime()
    }

    private fun enabled(): Boolean =
        WynnOverhaulConfig.current.locomotionEnabled && WynnOverhaulGate.isInGame()

    private fun approach(current: Float, goal: Float, a: Float): Float = current + (goal - current) * a

    private fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

    private fun step(body: Body, state: AvatarRenderState, config: WynnOverhaulConfig, claimed: Boolean, stroking: Boolean) {
        val now = System.nanoTime()
        val dt = if (body.lastNanos == 0L) 0f else ((now - body.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        body.lastNanos = now
        val a = MathX.expApproach(dt, TAU)
        val slow = MathX.expApproach(dt, TAU * 2f)

        val swimming = state.isVisuallySwimming
        val tread = state.isInWater && !swimming && !body.onGround
        val grounded = body.onGround || state.isPassenger

        if (!body.onGround && !state.isPassenger && !state.isInWater && !body.climbing && !state.isFallFlying) {
            body.airTime += dt
            if (body.vy < body.lastAirVy) body.lastAirVy = body.vy
        } else {
            if (body.airTime > AIR_DELAY && body.onGround && body.lastAirVy < -LAND_MIN_FALL) {
                body.wLand = max(body.wLand, clamp01((-body.lastAirVy - LAND_MIN_FALL) / LAND_FALL_SPAN))
            }
            body.airTime = 0f
            body.lastAirVy = 0f
        }
        body.wLand *= exp(-dt / LAND_TAU)
        if (body.wasGround && !body.onGround && body.vy > LAUNCH_MIN_VY && !state.isPassenger && !state.isInWater) {
            body.launchAge = 0f
            body.leadRight = cos(state.walkAnimationPos * GAIT_PHASE) >= 0f
        } else {
            body.launchAge += dt
        }
        body.wasGround = body.onGround

        val airborne = body.airTime > AIR_DELAY
        body.wAir = approach(body.wAir, if (airborne) 1f else 0f, a)
        body.wRise = approach(body.wRise, if (airborne) clamp01(body.vy / 0.42f) else 0f, a)
        body.wFall = approach(body.wFall, if (airborne) clamp01(-body.vy / 0.8f) else 0f, a)

        body.pace = approach(body.pace, body.hSpeed / PACE_REFERENCE, slow)
        var drot = state.bodyRot - body.lastBodyRot
        drot = ((drot % 360f) + 540f) % 360f - 180f
        if (!body.rotSeen) {
            body.rotSeen = true
            drot = 0f
        }
        body.lastBodyRot = state.bodyRot
        body.turnRate += ((drot / max(dt, 1e-4f) / 360f).coerceIn(-1f, 1f) - body.turnRate) * a
        val moving = clamp01(state.walkAnimationSpeed / 0.4f)
        val stepLen = hypot(body.stepX, body.stepZ)
        if (stepLen > STEP_DIR_MIN) {
            val yaw = state.bodyRot * (PI.toFloat() / 180f)
            val sinY = sin(yaw)
            val cosY = cos(yaw)
            body.moveFwd = approach(body.moveFwd, (-body.stepX * sinY + body.stepZ * cosY) / stepLen, a)
            body.moveSide = approach(body.moveSide, (body.stepX * cosY + body.stepZ * sinY) / stepLen, a)
        }
        val sprintGoal = if (body.sprinting && grounded && !swimming) 1f else 0f
        body.wSprint = approach(body.wSprint, sprintGoal, a)
        body.wMove = approach(body.wMove, if (grounded) moving else 0f, a)

        body.wClimb = approach(body.wClimb, if (body.climbing) 1f else 0f, a)
        body.climbMove = approach(body.climbMove, clamp01(abs(body.vy) / 0.1f), a)
        body.wSwim = approach(body.wSwim, if (swimming) 1f else 0f, a)
        body.wTread = approach(body.wTread, if (tread) 1f else 0f, a)
        body.wCrawl = approach(body.wCrawl, if (swimming && body.onGround && !state.isInWater) 1f else 0f, a)
        body.wRide = approach(body.wRide, if (state.isPassenger) 1f else 0f, a)
        body.wFly = approach(body.wFly, if (state.isFallFlying) 1f else 0f, a)
        body.wCrouch = approach(body.wCrouch, if (state.isCrouching) 1f else 0f, a)
        body.wClaim = approach(body.wClaim, if (claimed) 1f else 0f, a)
        body.wStroke = approach(body.wStroke, if (stroking) 1f else 0f, a)
        body.wUse = approach(body.wUse, if (body.useKind == USE_CONSUME) 1f else 0f, slow)
        body.wBlock = approach(body.wBlock, if (body.useKind == USE_BLOCK) 1f else 0f, a)
        body.wHurt = approach(body.wHurt, if (config.locomotionHurt) body.hurt else 0f, a * 2f)
        body.wDead = clamp01(state.deathTime / 20f)
    }

    private class Torso {
        var pitch = 0f
        var roll = 0f
        var yaw = 0f
        var shift = 0f
        var hipYaw = 0f
        var hipPitch = 0f
        var hipRoll = 0f
        var curl = 0f

        fun reset() {
            pitch = 0f
            roll = 0f
            yaw = 0f
            shift = 0f
            hipYaw = 0f
            hipPitch = 0f
            hipRoll = 0f
            curl = 0f
        }
    }

    private val torso = Torso()
    private val rigPose = FloatArray(LocoTargets.RIG_AXES.size)
    private val rigDrop get() = rigPose[RIG_DROP]
    private val rigLift get() = rigPose[RIG_LIFT]

    private class Snapshot {
        val values = FloatArray(21)
        val pivots = FloatArray(12)

        private fun rotations(part: ModelPart, at: Int) {
            values[at] = part.xRot
            values[at + 1] = part.yRot
            values[at + 2] = part.zRot
        }

        private fun pivot(part: ModelPart, at: Int) {
            pivots[at] = part.x
            pivots[at + 1] = part.y
            pivots[at + 2] = part.z
        }

        fun capture(model: HumanoidModel<*>) {
            rotations(model.head, 0)
            rotations(model.rightArm, 3)
            rotations(model.leftArm, 6)
            rotations(model.rightLeg, 9)
            rotations(model.leftLeg, 12)
            rotations(model.body, 15)
            pivot(model.body, 0)
            pivot(model.head, 3)
            pivot(model.rightArm, 6)
            pivot(model.leftArm, 9)
        }
    }

    private val snapshot = Snapshot()

    private fun limit(current: Float, base: Float, max: Float): Float = base + (current - base).coerceIn(-max, max)

    private fun clampParts(model: HumanoidModel<*>, armsFree: Boolean, climbing: Boolean) {
        val v = snapshot.values
        model.head.xRot = limit(model.head.xRot, v[0], HEAD_PITCH_LIMIT)
        model.head.yRot = limit(model.head.yRot, v[1], HEAD_YAW_LIMIT)
        model.head.zRot = limit(model.head.zRot, v[2], HEAD_ROLL_LIMIT)
        if (armsFree) {
            val armPitch = if (climbing) CLIMB_ARM_LIMIT else ARM_PITCH_LIMIT
            model.rightArm.xRot = limit(model.rightArm.xRot, v[3], armPitch)
            model.rightArm.yRot = limit(model.rightArm.yRot, v[4], ARM_YAW_LIMIT)
            model.rightArm.zRot = limit(model.rightArm.zRot, v[5], ARM_ROLL_LIMIT)
            model.leftArm.xRot = limit(model.leftArm.xRot, v[6], armPitch)
            model.leftArm.yRot = limit(model.leftArm.yRot, v[7], ARM_YAW_LIMIT)
            model.leftArm.zRot = limit(model.leftArm.zRot, v[8], ARM_ROLL_LIMIT)
        }
        model.rightLeg.xRot = limit(model.rightLeg.xRot, v[9], LEG_PITCH_LIMIT)
        model.rightLeg.zRot = limit(model.rightLeg.zRot, v[11], LEG_ROLL_LIMIT)
        model.leftLeg.xRot = limit(model.leftLeg.xRot, v[12], LEG_PITCH_LIMIT)
        model.leftLeg.zRot = limit(model.leftLeg.zRot, v[14], LEG_ROLL_LIMIT)
        model.body.xRot = limit(model.body.xRot, v[15], BODY_PITCH_LIMIT)
        model.body.yRot = limit(model.body.yRot, v[16], BODY_YAW_LIMIT)
        model.body.zRot = limit(model.body.zRot, v[17], BODY_ROLL_LIMIT)
    }

    private fun guardJoint(part: ModelPart, base: Int, trunkDx: Float, trunkDy: Float, trunkDz: Float, tolerance: Float) {
        val v = snapshot.pivots
        part.x = v[base] + trunkDx + (part.x - v[base] - trunkDx).coerceIn(-tolerance, tolerance)
        part.y = v[base + 1] + trunkDy + (part.y - v[base + 1] - trunkDy).coerceIn(-tolerance, tolerance)
        part.z = v[base + 2] + trunkDz + (part.z - v[base + 2] - trunkDz).coerceIn(-tolerance, tolerance)
    }

    private fun guardJoints(model: HumanoidModel<*>, armsFree: Boolean) {
        val v = snapshot.pivots
        val bx = (model.body.x - v[0]).coerceIn(-PIVOT_LIMIT, PIVOT_LIMIT)
        val by = (model.body.y - v[1]).coerceIn(-LIFT_LIMIT - 0.5f, DIP_LIMIT + PIVOT_LIMIT)
        val bz = (model.body.z - v[2]).coerceIn(-PIVOT_LIMIT, PIVOT_LIMIT)
        model.body.x = v[0] + bx
        model.body.y = v[1] + by
        model.body.z = v[2] + bz
        guardJoint(model.head, 3, bx, by, bz, JOINT_SLACK)
        guardJoint(model.rightArm, 6, bx, by, bz, JOINT_SLACK + ARM_SLACK)
        guardJoint(model.leftArm, 9, bx, by, bz, JOINT_SLACK + ARM_SLACK)
    }

    private fun applyTorso(model: HumanoidModel<*>, armsFree: Boolean) {
        val t = torso
        val pitch = t.pitch.coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        val roll = t.roll.coerceIn(-ROLL_LIMIT, ROLL_LIMIT)
        val yaw = t.yaw.coerceIn(-YAW_LIMIT, YAW_LIMIT)
        val hipYaw = t.hipYaw.coerceIn(-HIP_YAW_LIMIT, HIP_YAW_LIMIT)
        val hipPitch = t.hipPitch.coerceIn(-HIP_TILT_LIMIT, HIP_TILT_LIMIT)
        val hipRoll = t.hipRoll.coerceIn(-HIP_TILT_LIMIT, HIP_TILT_LIMIT)
        val shift = t.shift.coerceIn(-LIFT_LIMIT, DIP_LIMIT)
        val share = Trunk.PELVIS_SHARE
        Trunk.apply(
            model, hipYaw, hipPitch + pitch * share, hipRoll + roll * share,
            yaw - hipYaw, pitch * (1f - share) + t.curl, roll * (1f - share), !armsFree,
        )
        if (shift != 0f) {
            model.body.y += shift
            model.head.y += shift
            model.rightArm.y += shift
            model.leftArm.y += shift
        }
        model.body.yScale = if (shift < 0f) 1f - shift / 12f else 1f
    }

    @JvmStatic
    fun legAuthority(state: AvatarRenderState): Float {
        val body = bodies.get(state.id)
        if (body == null || System.nanoTime() - body.seenNanos > STALE_NANOS) {
            return if (state.isPassenger || state.isFallFlying || state.isVisuallySwimming || state.isInWater) 0f else 1f
        }
        val away = max(max(body.wAir, body.wRide), max(max(body.wSwim, body.wClimb), max(body.wFly, max(body.wTread, body.wCrouch))))
        return clamp01(1f - away)
    }

    @JvmStatic
    fun unseen(state: AvatarRenderState): Boolean =
        state.isInvisible && !state.isInvisibleToPlayer && !state.appearsGlowing()

    @JvmStatic
    fun apply(model: HumanoidModel<*>, humanoid: HumanoidRenderState) {
        val state = humanoid as? AvatarRenderState ?: return
        applyLayers(model, state)
        Trunk.flush(model)
        RigLegs.flush(model, state.id, 1f, 0f, 0f)
    }

    private fun applyLayers(model: HumanoidModel<*>, state: AvatarRenderState) {
        FirstPersonBody.applyHeadVisibility(model, state)
        if (unseen(state) || (state.distanceToCameraSq > ANIMATE_FAR_SQ && Minecraft.getInstance().player?.id != state.id)) {
            clearBends(model)
            return
        }
        val armsClaimed = WeaponAnimations.claimsArms(state)
        val config = WynnOverhaulConfig.current
        if (!enabled() || state.isSpectator) {
            clearBends(model)
            if (armsClaimed && config.weaponAnimationsEnabled && !state.isSpectator) {
                val stamp = System.nanoTime()
                setBend(model.rightArm, combatElbow(model.rightArm, true), stamp)
                setBend(model.leftArm, combatElbow(model.leftArm, false), stamp)
                if (model is PlayerModel) {
                    setBend(model.rightSleeve, freshBendOf(model.rightArm), stamp)
                    setBend(model.leftSleeve, freshBendOf(model.leftArm), stamp)
                }
            }
            return
        }
        val local = Minecraft.getInstance().player?.id == state.id
        val body = bodies.get(state.id)
        if ((!local && !config.locomotionOtherPlayers) || body == null) {
            clearBends(model)
            return
        }
        val styles = AnimationLibrary.tables().locomotion.styles
        val style = (if (!local && config.locomotionRandomizeOthers) styles.values.elementAt(Math.floorMod(body.styleSeed, styles.size)) else styles[config.locomotionStyle])
            ?: styles.values.firstOrNull() ?: run {
                clearBends(model)
                return
            }
        if (System.nanoTime() - body.seenNanos > STALE_NANOS) {
            clearBends(model)
            return
        }
        step(body, state, config, armsClaimed, WeaponAnimations.swinging(state))
        prune()

        val k = MASTER
        val armsFree = !armsClaimed
        val owned = armsClaimed
        torso.reset()
        rigPose.fill(0f)
        snapshot.capture(model)
        val rArm = model.rightArm
        val lArm = model.leftArm
        val rLeg = model.rightLeg
        val lLeg = model.leftLeg
        val grounded = clamp01(1f - body.wAir) * clamp01(1f - body.wSwim) * clamp01(1f - body.wClimb) *
            clamp01(1f - body.wFly) * clamp01(1f - body.wRide) * clamp01(1f - body.wTread)
        val vanillaSp = clamp01(state.walkAnimationSpeed)
        val pace = body.pace.coerceIn(0f, 1.6f)
        val sp = ((vanillaSp + clamp01(pace * 0.9f + max(0f, pace - OVERDRIVE_START) * 0.5f)) * 0.5f).coerceIn(0f, 1.3f)
        val boost = max(0f, sp - vanillaSp)
        val phase = state.walkAnimationPos * GAIT_PHASE
        val gait = cos(phase)
        val gaitOpp = cos(phase + PI.toFloat())

        applyStates(model, state, body, style, config, k, armsFree, grounded, pace, sp, boost, vanillaSp, phase, gait, gaitOpp)
        submitPose(state, body, submitGait(state, body, style, grounded, phase))

        clampParts(model, armsFree, body.wClimb > 0.3f)
        guardJoints(model, armsFree)
        applyTorso(model, armsFree)
        setBends(model, state.id, body, armsFree, owned)
    }

    private fun styleValue(style: StyleDef, name: String): Float {
        val i = AnimationLibrary.tables().locomotion.styleParams.indexOf(name)
        return if (i < 0) 1f else style.values[i]
    }

    private fun swingOffset(phase: Float, duty: Float, travel: Float, lift: Float, out: FloatArray) {
        val tau = (2.0 * PI).toFloat()
        var q = ((phase - 1.5f * PI.toFloat()) / tau + 0.5f) % 1f
        if (q < 0f) q += 1f
        val half = duty / 2f
        if (abs(q - 0.5f) <= half) {
            val p = (q - (0.5f - half)) / duty
            out[0] = travel * 0.5f * (1f - 2f * p)
            out[1] = 0f
        } else {
            val r = (if (q > 0.5f) q - 0.5f - half else q + 0.5f - half) / (1f - duty)
            val slope = -travel * (1f - duty) / duty
            val r2 = r * r
            val r3 = r2 * r
            out[0] = travel * 0.5f * (-(2f * r3 - 3f * r2 + 1f) + (-2f * r3 + 3f * r2)) + slope * ((r3 - 2f * r2 + r) + (r3 - r2))
            out[1] = lift * sin(PI.toFloat() * r.pow(SWING_ARC_SKEW))
        }
    }

    private val footStep = FloatArray(2)

    private fun submitGait(state: AvatarRenderState, body: Body, style: StyleDef, grounded: Float, phase: Float): Float {
        val weight = clamp01(body.wMove) * grounded * (1f - body.wDead)
        if (weight <= 0.01f) return 0f
        val spec = AnimationLibrary.tables().locomotion.gait
        val rate = GAIT_PHASE * state.walkAnimationSpeed
        val ground = hypot(body.stepX, body.stepZ) * BLOCK_PX
        val raw = if (rate > STRIDE_RATE_MIN) ground / rate else body.strideK
        body.strideK = approach(body.strideK, raw.coerceIn(STRIDE_K_MIN, STRIDE_K_MAX), STRIDE_SMOOTH)
        val sprint = body.wSprint
        val duty = max(GAIT_DUTY_MIN, spec.walkDuty + (spec.sprintDuty - spec.walkDuty) * sprint)
        val lift = (spec.walkLift + (spec.sprintLift - spec.walkLift) * sprint) * (1f - CROUCH_LIFT_CUT * body.wCrouch)
        val stride = (styleValue(style, "walkLeg") + (styleValue(style, "sprintLeg") - styleValue(style, "walkLeg")) * sprint).coerceIn(0.2f, 1.2f)
        val travel = (body.strideK * (2.0 * PI).toFloat() * duty * stride * (1f - CROUCH_STRIDE_CUT * body.wCrouch)).coerceAtMost(GAIT_TRAVEL_MAX)
        var df = body.moveFwd
        var ds = body.moveSide
        val dn = hypot(df, ds)
        if (dn > 0.05f) {
            df /= dn
            ds /= dn
        } else {
            df = 1f
            ds = 0f
        }
        swingOffset(phase, duty, travel, lift, footStep)
        val rightOffset = footStep[0]
        val rightLift = footStep[1]
        swingOffset(phase + PI.toFloat(), duty, travel, lift, footStep)
        RigLegs.submitGait(
            state.id, weight, torso.hipYaw.coerceIn(-HIP_YAW_LIMIT, HIP_YAW_LIMIT), rigDrop,
            footStep[0] * df, footStep[0] * ds, footStep[1],
            rightOffset * df, rightOffset * ds, rightLift,
        )
        return weight
    }

    private fun submitPose(state: AvatarRenderState, body: Body, gaitWeight: Float) {
        val launching = clamp01(1f - body.launchAge / LAUNCH_SECONDS)
        val weight = clamp01(max(max(max(body.wAir, body.wCrouch), max(body.wClimb, body.wLand)), launching)) * (1f - body.wDead)
        if (weight > 0.01f) {
            RigLegs.submitPose(
                state.id, weight, rigPose[RIG_ROOT_F], if (gaitWeight < 0.01f) rigDrop + rigLift else 0f,
                rigPose[RIG_FOOT_LF], rigPose[RIG_FOOT_LS], rigPose[RIG_FOOT_LU],
                rigPose[RIG_FOOT_RF], rigPose[RIG_FOOT_RS], rigPose[RIG_FOOT_RU],
            )
        }
        torso.shift += (rigDrop + rigLift) * (1f - clamp01(gaitWeight + weight))
    }

    private var env = FloatArray(LocoSignals.COUNT)

    private fun toggleOn(config: WynnOverhaulConfig, name: String?): Boolean = name == null || LocoToggles.enabled(config, name)

    private fun partOf(model: HumanoidModel<*>, index: Int): ModelPart = when (index) {
        LocoTargets.HEAD -> model.head
        LocoTargets.RIGHT_ARM -> model.rightArm
        LocoTargets.LEFT_ARM -> model.leftArm
        LocoTargets.RIGHT_LEG -> model.rightLeg
        else -> model.leftLeg
    }

    private fun readTarget(model: HumanoidModel<*>, code: Int): Float {
        val part = code / LocoTargets.AXIS_STRIDE
        val axis = code % LocoTargets.AXIS_STRIDE
        if (part == LocoTargets.RIG) return rigPose[axis]
        if (part == LocoTargets.TORSO) {
            return when (axis) {
                0 -> torso.pitch
                1 -> torso.roll
                2 -> torso.yaw
                3 -> torso.shift
                4 -> torso.hipYaw
                5 -> torso.hipPitch
                6 -> torso.hipRoll
                else -> torso.curl
            }
        }
        val p = partOf(model, part)
        return when (axis) {
            0 -> p.xRot
            1 -> p.yRot
            2 -> p.zRot
            else -> p.y
        }
    }

    private fun writeTarget(model: HumanoidModel<*>, code: Int, value: Float) {
        val part = code / LocoTargets.AXIS_STRIDE
        val axis = code % LocoTargets.AXIS_STRIDE
        if (part == LocoTargets.RIG) {
            rigPose[axis] = value
            return
        }
        if (part == LocoTargets.TORSO) {
            when (axis) {
                0 -> torso.pitch = value
                1 -> torso.roll = value
                2 -> torso.yaw = value
                3 -> torso.shift = value
                4 -> torso.hipYaw = value
                5 -> torso.hipPitch = value
                6 -> torso.hipRoll = value
                else -> torso.curl = value
            }
            return
        }
        val p = partOf(model, part)
        when (axis) {
            0 -> p.xRot = value
            1 -> p.yRot = value
            2 -> p.zRot = value
            else -> p.y = value
        }
    }

    private fun applyStates(
        model: HumanoidModel<*>, state: AvatarRenderState, body: Body, style: StyleDef, config: WynnOverhaulConfig,
        k: Float, armsFree: Boolean, grounded: Float, pace: Float, sp: Float, boost: Float, vanillaSp: Float,
        phase: Float, gait: Float, gaitOpp: Float,
    ) {
        val loco = AnimationLibrary.tables().locomotion
        if (env.size < loco.envSize) env = FloatArray(loco.envSize)
        val e = env
        e[LocoSignals.K] = k
        e[LocoSignals.W_AIR] = body.wAir
        e[LocoSignals.W_RISE] = body.wRise
        e[LocoSignals.W_FALL] = body.wFall
        e[LocoSignals.W_LAND] = body.wLand
        e[LocoSignals.W_MOVE] = body.wMove
        e[LocoSignals.W_SPRINT] = body.wSprint
        e[LocoSignals.W_CLIMB] = body.wClimb
        e[LocoSignals.CLIMB_MOVE] = body.climbMove
        e[LocoSignals.W_SWIM] = body.wSwim
        e[LocoSignals.W_TREAD] = body.wTread
        e[LocoSignals.W_CRAWL] = body.wCrawl
        e[LocoSignals.W_RIDE] = body.wRide
        e[LocoSignals.W_FLY] = body.wFly
        e[LocoSignals.W_USE] = body.wUse
        e[LocoSignals.W_BLOCK] = body.wBlock
        e[LocoSignals.W_HURT] = body.wHurt
        e[LocoSignals.W_DEAD] = body.wDead
        e[LocoSignals.W_CROUCH] = body.wCrouch
        e[LocoSignals.PACE] = pace
        e[LocoSignals.SP] = sp
        e[LocoSignals.BOOST] = boost
        e[LocoSignals.VANILLA_SP] = vanillaSp
        e[LocoSignals.PHASE] = phase
        e[LocoSignals.GAIT] = gait
        e[LocoSignals.GAIT_OPP] = gaitOpp
        e[LocoSignals.AGE] = state.ageInTicks
        e[LocoSignals.GROUNDED] = grounded
        e[LocoSignals.TURN_RATE] = body.turnRate
        e[LocoSignals.LAUNCH_AGE] = body.launchAge
        e[LocoSignals.LEAD_RIGHT] = if (body.leadRight) 1f else 0f
        e[LocoSignals.LAUNCH] = clamp01(1f - body.launchAge / LAUNCH_SECONDS)
        e[LocoSignals.MOVE_FWD] = body.moveFwd
        e[LocoSignals.MOVE_SIDE] = body.moveSide
        e[LocoSignals.CLAIMED] = body.wClaim
        e[LocoSignals.STROKE] = body.wStroke
        System.arraycopy(style.values, 0, e, LocoSignals.COUNT, style.values.size)
        for (s in loco.states) {
            if (!toggleOn(config, s.toggle)) continue
            if (s.gate >= 0 && e[s.gate] <= GATE_MIN) continue
            for (i in s.locals.indices) e[s.localSlots[i]] = s.locals[i].eval(e)
            for (fx in s.effects) {
                if (fx.freeArmsOnly && !armsFree) continue
                val v = fx.value.eval(e)
                if (fx.approach) {
                    val current = readTarget(model, fx.target)
                    writeTarget(model, fx.target, current + (v - current) * fx.rate!!.eval(e))
                } else {
                    writeTarget(model, fx.target, readTarget(model, fx.target) + v)
                }
            }
        }
    }


    private fun setBend(part: ModelPart, angle: Float, stamp: Long) {
        ((part as Any) as BendHolder).setLimbBend(angle, stamp)
    }

    private fun freshBendOf(part: ModelPart): Float = LimbBend.freshBend(part)

    private fun clearBends(model: HumanoidModel<*>) {
        setBend(model.rightArm, 0f, 0L)
        setBend(model.leftArm, 0f, 0L)
        setBend(model.rightLeg, 0f, 0L)
        setBend(model.leftLeg, 0f, 0L)
        if (model is PlayerModel) {
            setBend(model.rightSleeve, 0f, 0L)
            setBend(model.leftSleeve, 0f, 0L)
            setBend(model.rightPants, 0f, 0L)
            setBend(model.leftPants, 0f, 0L)
        }
    }

    private fun knee(leg: ModelPart, body: Body): Float {
        val swing = leg.xRot
        var bend = KNEE_BACK * max(0f, swing) + KNEE_FORWARD * max(0f, -swing)
        bend += KNEE_TUCK * body.wRise * body.wAir + KNEE_LAND * body.wLand + KNEE_CROUCH * body.wCrouch
        bend += launchKnee(leg, body)
        bend = max(bend, KNEE_SIT * body.wRide)
        return bend.coerceIn(0f, KNEE_LIMIT)
    }

    private fun launchKnee(leg: ModelPart, body: Body): Float {
        val u = body.launchAge / LAUNCH_SECONDS
        if (u >= 1f) return 0f
        val compress = clamp01(1f - u / 0.4f)
        val isLead = (leg.x < 0f) == body.leadRight
        val drive = sin(clamp01(u / 0.5f) * PI.toFloat() * 0.5f) * (1f - u)
        return compress * LAUNCH_KNEE_COMPRESS + if (isLead) drive * LAUNCH_KNEE_DRIVE else 0f
    }

    private fun combatElbow(arm: ModelPart, right: Boolean): Float {
        val bend = -WeaponAnimations.combatElbow(right)
        arm.xRot -= atan2(FOREARM * sin(bend), UPPER_ARM + FOREARM * cos(bend))
        return bend
    }

    private fun elbow(arm: ModelPart, armsFree: Boolean, body: Body): Float {
        if (!armsFree) return 0f
        val forward = max(0f, -arm.xRot)
        val run = body.wSprint * body.wMove
        val flex = ELBOW_REST + ELBOW_FORWARD * forward + ELBOW_RUN * run
        return -flex.coerceIn(0f, ELBOW_LIMIT + ELBOW_RUN_EXTRA * run)
    }

    private fun setBends(model: HumanoidModel<*>, id: Int, body: Body, armsFree: Boolean, owned: Boolean) {
        val stamp = System.nanoTime()
        val rig = RigLegs.kneeWeight(id)
        var rKnee = knee(model.rightLeg, body)
        var lKnee = knee(model.leftLeg, body)
        if (rig > 0.001f) {
            rKnee = approach(rKnee, RigLegs.kneeRight(id), rig)
            lKnee = approach(lKnee, RigLegs.kneeLeft(id), rig)
        }
        val rElbow = if (owned) combatElbow(model.rightArm, true) else elbow(model.rightArm, armsFree, body)
        val lElbow = if (owned) combatElbow(model.leftArm, false) else elbow(model.leftArm, armsFree, body)
        setBend(model.rightLeg, rKnee, stamp)
        setBend(model.leftLeg, lKnee, stamp)
        setBend(model.rightArm, rElbow, stamp)
        setBend(model.leftArm, lElbow, stamp)
        if (model is PlayerModel) {
            setBend(model.rightPants, rKnee, stamp)
            setBend(model.leftPants, lKnee, stamp)
            setBend(model.rightSleeve, rElbow, stamp)
            setBend(model.leftSleeve, lElbow, stamp)
        }
    }

    private var lastPrune = 0L

    private fun prune() {
        val now = System.nanoTime()
        if (now - lastPrune < STALE_NANOS) return
        lastPrune = now
        bodies.values.removeAll { now - it.seenNanos > STALE_NANOS * 2 }
    }
}

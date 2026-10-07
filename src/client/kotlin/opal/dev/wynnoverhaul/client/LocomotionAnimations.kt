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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object LocomotionAnimations {
    val styleKeys: List<String> get() = AnimationLibrary.tables().locomotion.styles.keys.toList()

    fun labelOf(key: String): String = AnimationLibrary.tables().locomotion.styles[key]?.label ?: key


    private const val AIR_DELAY = 0.1f
    private const val LAND_MIN_FALL = 0.12f
    private const val LAND_FALL_SPAN = 0.55f
    private const val LAND_TAU = 0.2f
    private const val TAU = 0.08f
    private const val STALE_NANOS = 5_000_000_000L
    private const val GAIT_PHASE = 0.4f
    private const val SLOPE_REACH = 1.5
    private const val SLOPE_SPAN_PX = 48f
    private const val SLOPE_MAX = 0.35f
    private const val SLOPE_SAMPLE_NANOS = 50_000_000L
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
    private const val PIVOT_LIMIT = 3f
    private const val JOINT_SLACK = 0.6f
    private const val ARM_SLACK = 0.9f
    private const val PITCH_LIMIT = 0.5f
    private const val ROLL_LIMIT = 0.16f
    private const val YAW_LIMIT = 0.2f
    private const val LIFT_LIMIT = 1.4f
    private const val DIP_LIMIT = 2.6f
    private const val HEAD_COUNTER = 0.7f
    private const val HEAD_ROLL_COUNTER = 0.6f
    private const val ARM_PITCH_SHARE = 0.5f
    private const val SHOULDER_HALF = 5f
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

    private const val LEG_HALF = 6f
    private const val LEG_FULL = 12f
    private const val LEG_LATERAL = 1.9f
    private const val TERRAIN_STEP_UP = 0.56
    private const val TERRAIN_REACH_DOWN = 0.6
    private const val TERRAIN_R_MIN = 5.4f
    private const val TERRAIN_REACH_CAP = 11.6f
    private const val TERRAIN_PELVIS_LIMIT = 6f
    private const val TERRAIN_TAU = 0.07f
    private const val TERRAIN_FAR_SQ = 576.0
    private const val ANIMATE_FAR_SQ = 4096.0
    private const val TERRAIN_DEADBAND = 0.05f

    private const val USE_NONE = 0
    private const val USE_CONSUME = 1
    private const val USE_BLOCK = 2

    private class Body {
        var lastNanos = 0L
        var seenNanos = 0L
        var onGround = true
        var vy = 0f
        var hSpeed = 0f
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
        var lastBodyRot = 0f
        var rotSeen = false
        var turnRate = 0f
        var slopePitch = 0f
        var slopeGoal = 0f
        var slopeNanos = 0L
        var entity: Avatar? = null
        var terrainNanos = 0L
        var groundRight = 0f
        var groundLeft = 0f
        var pelvis = 0f
    }

    private val bodies = HashMap<Int, Body>()

    @JvmStatic
    fun capture(entity: Avatar, state: AvatarRenderState) {
        if (!enabled()) return
        val body = bodies.getOrPut(state.id) { Body() }
        val delta = entity.deltaMovement
        body.vy = delta.y.toFloat()
        body.hSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z).toFloat()
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
        body.entity = entity
        body.seenNanos = System.nanoTime()
    }

    private fun enabled(): Boolean =
        WynnOverhaulConfig.current.locomotionEnabled && WynnOverhaulGate.isInGame()

    private fun approach(current: Float, goal: Float, a: Float): Float = current + (goal - current) * a

    private fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

    private fun step(body: Body, state: AvatarRenderState, config: WynnOverhaulConfig) {
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
        body.slopePitch = approach(body.slopePitch, sampledSlope(body, state, config), slow)
        val moving = clamp01(state.walkAnimationSpeed / 0.4f)
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

        fun reset() {
            pitch = 0f
            roll = 0f
            yaw = 0f
            shift = 0f
        }
    }

    private val torso = Torso()

    private class Snapshot {
        val values = FloatArray(21)
        val pivots = FloatArray(12)

        fun capture(model: HumanoidModel<*>) {
            var i = 0
            for (part in arrayOf(model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg)) {
                values[i++] = part.xRot
                values[i++] = part.yRot
                values[i++] = part.zRot
            }
            values[i++] = model.body.xRot
            values[i++] = model.body.yRot
            values[i] = model.body.zRot
            var j = 0
            for (part in arrayOf(model.body, model.head, model.rightArm, model.leftArm)) {
                pivots[j++] = part.x
                pivots[j++] = part.y
                pivots[j++] = part.z
            }
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
        val shift = t.shift.coerceIn(-LIFT_LIMIT, DIP_LIMIT)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)
        val trunk = 12f
        val shoulder = 10f
        val trunkDx = trunk * sr
        val trunkDy = trunk * (1f - cp * cr) + shift
        val trunkDz = -trunk * sp
        val armDx = shoulder * sr
        val armDy = shoulder * (1f - cp * cr) + shift
        val armDz = -shoulder * sp

        model.body.xRot += pitch
        model.body.yRot += yaw
        model.body.zRot += roll
        model.body.x += trunkDx
        model.body.y += trunkDy
        model.body.z += trunkDz
        model.head.xRot -= pitch * HEAD_COUNTER
        model.head.yRot -= yaw * HEAD_COUNTER
        model.head.zRot -= roll * HEAD_ROLL_COUNTER
        model.head.x += trunkDx
        model.head.y += trunkDy
        model.head.z += trunkDz

        val swing = 1f - cos(yaw)
        val depth = sin(yaw) * SHOULDER_HALF
        model.rightArm.x += armDx + swing * SHOULDER_HALF
        model.rightArm.y += armDy
        model.rightArm.z += armDz + depth
        model.leftArm.x += armDx - swing * SHOULDER_HALF
        model.leftArm.y += armDy
        model.leftArm.z += armDz - depth
        if (armsFree) {
            model.rightArm.xRot += pitch * ARM_PITCH_SHARE
            model.leftArm.xRot += pitch * ARM_PITCH_SHARE
            model.rightArm.yRot += yaw
            model.leftArm.yRot += yaw
        }

        val lift = -shift
        model.body.yScale = if (lift > 0f) 1f + lift / trunk else 1f
    }

    @JvmStatic
    fun unseen(state: AvatarRenderState): Boolean =
        state.isInvisible && !state.isInvisibleToPlayer && !state.appearsGlowing()

    @JvmStatic
    fun apply(model: HumanoidModel<*>, humanoid: HumanoidRenderState) {
        val state = humanoid as? AvatarRenderState ?: return
        FirstPersonBody.applyHeadVisibility(model, state)
        if (unseen(state) || (state.distanceToCameraSq > ANIMATE_FAR_SQ && Minecraft.getInstance().player?.id != state.id)) {
            clearBends(model)
            return
        }
        val armsClaimed = WeaponAnimations.claimsArms(state)
        val config = WynnOverhaulConfig.current
        if (!enabled() || state.isSpectator) {
            clearBends(model)
            if (armsClaimed && config.locomotionBend && config.weaponAnimationsEnabled && !state.isSpectator) {
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
        val body = bodies[state.id]
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
        step(body, state, config)
        prune()

        val k = MASTER
        val armsFree = !armsClaimed
        val owned = armsClaimed
        torso.reset()
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

        clampParts(model, armsFree, body.wClimb > 0.3f)
        applyTorso(model, armsFree)
        guardJoints(model, armsFree)
        val wTerrain = grounded * (1f - body.wDead)
        val terrainActive = config.locomotionTerrain && config.locomotionBend && applyTerrain(model, state, body, wTerrain, k)
        if (config.locomotionBend) setBends(model, body, armsFree, owned, if (terrainActive) terrainKnees else null) else clearBends(model)
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
        val part = code shr 2
        val axis = code and 3
        if (part == LocoTargets.TORSO) {
            return when (axis) {
                0 -> torso.pitch
                1 -> torso.roll
                2 -> torso.yaw
                else -> torso.shift
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
        val part = code shr 2
        val axis = code and 3
        if (part == LocoTargets.TORSO) {
            when (axis) {
                0 -> torso.pitch = value
                1 -> torso.roll = value
                2 -> torso.yaw = value
                else -> torso.shift = value
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
        e[LocoSignals.SLOPE_PITCH] = body.slopePitch
        e[LocoSignals.LAUNCH_AGE] = body.launchAge
        e[LocoSignals.LEAD_RIGHT] = if (body.leadRight) 1f else 0f
        e[LocoSignals.LAUNCH] = clamp01(1f - body.launchAge / LAUNCH_SECONDS)
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


    private val terrainKnees = FloatArray(2)
    private val footRight = FloatArray(2)
    private val footLeft = FloatArray(2)
    private val solved = FloatArray(2)

    private fun legFoot(theta: Float, bend: Float, out: FloatArray) {
        val ly = LEG_HALF + LEG_HALF * cos(bend)
        val lz = LEG_HALF * sin(bend)
        out[0] = ly * sin(theta) + lz * cos(theta)
        out[1] = ly * cos(theta) - lz * sin(theta)
    }

    private fun solveLeg(dz: Float, dy: Float, out: FloatArray) {
        val r = hypot(dz, dy).coerceIn(TERRAIN_R_MIN, LEG_FULL)
        val bend = acos((r * r / (2f * LEG_HALF * LEG_HALF) - 1f).coerceIn(-1f, 1f))
        out[0] = atan2(dz, dy.coerceAtLeast(0.5f)) - bend * 0.5f
        out[1] = bend
    }

    private fun sampledSlope(body: Body, state: AvatarRenderState, config: WynnOverhaulConfig): Float {
        val now = System.nanoTime()
        if (now - body.slopeNanos >= SLOPE_SAMPLE_NANOS) {
            body.slopeNanos = now
            body.slopeGoal = slopeTarget(body, state, config)
        }
        return body.slopeGoal
    }

    private fun slopeTarget(body: Body, state: AvatarRenderState, config: WynnOverhaulConfig): Float {
        if (!config.locomotionTerrain || !config.locomotionWalk || body.wMove < 0.05f) return 0f
        val entity = body.entity
        if (entity !is LocalPlayer) return 0f
        val rad = state.bodyRot * (PI.toFloat() / 180f)
        val fx = -sin(rad).toDouble()
        val fz = cos(rad).toDouble()
        val front = groundDelta(entity, state.x + fx * SLOPE_REACH, state.z + fz * SLOPE_REACH, state.y)
        val back = groundDelta(entity, state.x - fx * SLOPE_REACH, state.z - fz * SLOPE_REACH, state.y)
        return atan2((front - back).toDouble(), SLOPE_SPAN_PX.toDouble()).toFloat().coerceIn(-SLOPE_MAX, SLOPE_MAX)
    }

    private fun groundDelta(entity: Avatar, x: Double, z: Double, baseY: Double): Float {
        val hit = entity.level().clip(
            ClipContext(Vec3(x, baseY + TERRAIN_STEP_UP, z), Vec3(x, baseY - TERRAIN_REACH_DOWN, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity),
        )
        if (hit.type == HitResult.Type.MISS || hit.isInside) return 0f
        val delta = ((hit.location.y - baseY) * 16.0).toFloat()
        return if (abs(delta) < 0.4f) 0f else delta
    }

    private fun reachHeight(base: Float, dz: Float, cap: Float): Float =
        base - Math.sqrt(max(0f, cap * cap - dz * dz).toDouble()).toFloat()

    private fun applyTerrain(model: HumanoidModel<*>, state: AvatarRenderState, body: Body, weight: Float, k: Float): Boolean {
        val entity = body.entity
        val now = System.nanoTime()
        val dt = if (body.terrainNanos == 0L) 0f else ((now - body.terrainNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        body.terrainNanos = now
        val a = MathX.expApproach(dt, TERRAIN_TAU)
        val rLeg = model.rightLeg
        val lLeg = model.leftLeg
        val kneeRight = knee(rLeg, body)
        val kneeLeft = knee(lLeg, body)
        legFoot(rLeg.xRot, kneeRight, footRight)
        legFoot(lLeg.xRot, kneeLeft, footLeft)

        var goalRight = 0f
        var goalLeft = 0f
        if (entity != null && weight > 0.01f && state.distanceToCameraSq < TERRAIN_FAR_SQ) {
            val rad = state.bodyRot * (PI.toFloat() / 180f)
            val fx = -sin(rad).toDouble()
            val fz = cos(rad).toDouble()
            val lx = cos(rad).toDouble()
            val lz = sin(rad).toDouble()
            val side = LEG_LATERAL / 16.0
            val forwardRight = -footRight[0] / 16.0
            val forwardLeft = -footLeft[0] / 16.0
            goalRight = groundDelta(entity, state.x + fx * forwardRight - lx * side, state.z + fz * forwardRight - lz * side, state.y)
            goalLeft = groundDelta(entity, state.x + fx * forwardLeft + lx * side, state.z + fz * forwardLeft + lz * side, state.y)
        }
        body.groundRight = approach(body.groundRight, goalRight, a)
        body.groundLeft = approach(body.groundLeft, goalLeft, a)
        val dRight = body.groundRight * weight
        val dLeft = body.groundLeft * weight

        val baseRight = footRight[1] - dRight
        val baseLeft = footLeft[1] - dLeft
        val capRight = max(hypot(footRight[0], footRight[1]), TERRAIN_REACH_CAP)
        val capLeft = max(hypot(footLeft[0], footLeft[1]), TERRAIN_REACH_CAP)
        var pelvisGoal = max(0f, max(reachHeight(baseRight, footRight[0], capRight), reachHeight(baseLeft, footLeft[0], capLeft)))
        val ceiling = min(reachHeight(baseRight, footRight[0], TERRAIN_R_MIN), reachHeight(baseLeft, footLeft[0], TERRAIN_R_MIN))
        pelvisGoal = min(pelvisGoal, max(0f, ceiling)).coerceAtMost(TERRAIN_PELVIS_LIMIT)
        body.pelvis = approach(body.pelvis, pelvisGoal, a)

        if (abs(dRight) < TERRAIN_DEADBAND && abs(dLeft) < TERRAIN_DEADBAND && body.pelvis < TERRAIN_DEADBAND) return false

        solveLeg(footRight[0], baseRight - body.pelvis, solved)
        rLeg.xRot = solved[0]
        terrainKnees[0] = solved[1]
        solveLeg(footLeft[0], baseLeft - body.pelvis, solved)
        lLeg.xRot = solved[0]
        terrainKnees[1] = solved[1]

        val drop = body.pelvis
        rLeg.y += drop
        lLeg.y += drop
        model.body.y += drop
        model.head.y += drop
        model.rightArm.y += drop
        model.leftArm.y += drop
        return true
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

    private fun elbow(arm: ModelPart, armsFree: Boolean): Float {
        if (!armsFree) return 0f
        val forward = max(0f, -arm.xRot)
        return -(ELBOW_REST + ELBOW_FORWARD * forward).coerceIn(0f, ELBOW_LIMIT)
    }

    private fun setBends(model: HumanoidModel<*>, body: Body, armsFree: Boolean, owned: Boolean, kneeOverride: FloatArray?) {
        val stamp = System.nanoTime()
        val rKnee = kneeOverride?.get(0) ?: knee(model.rightLeg, body)
        val lKnee = kneeOverride?.get(1) ?: knee(model.leftLeg, body)
        val rElbow = if (owned) combatElbow(model.rightArm, true) else elbow(model.rightArm, armsFree)
        val lElbow = if (owned) combatElbow(model.leftArm, false) else elbow(model.leftArm, armsFree)
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

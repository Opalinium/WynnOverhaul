package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.world.entity.Avatar
import net.minecraft.world.item.ItemUseAnimation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

object LocomotionAnimations {
    class Style(
        val label: String,
        val walkArm: Float = 1f, val walkLeg: Float = 1f, val walkBob: Float = 0f, val walkLean: Float = 0f, val walkSway: Float = 0f,
        val walkDrop: Float = 0f, val roll: Float = 0f, val swagger: Float = 0f, val headBob: Float = 0f, val headRoll: Float = 0f,
        val armFlare: Float = 0f, val stomp: Float = 0f, val bobSharp: Float = 1f,
        val launchDip: Float = 1.5f, val launchStretch: Float = 0.6f, val launchArms: Float = 0.8f, val launchKnee: Float = 0.7f,
        val sprintArm: Float = 1f, val sprintLeg: Float = 1f, val sprintBob: Float = 0f, val sprintLean: Float = 0f, val sprintSway: Float = 0f,
        val posture: Float = 0f, val idleBreath: Float = 0f, val idleRate: Float = 0.06f, val idleSway: Float = 0f, val idleArmOpen: Float = 0f,
        val idleHeadDrop: Float = 0f,
        val jumpArms: Float = 0f, val jumpLegs: Float = 0f, val fallSpread: Float = 0f, val fallLegs: Float = 0f,
        val landDip: Float = 0f, val landLean: Float = 0f,
        val crouchLean: Float = 0f, val crouchArm: Float = 0f, val crouchLeg: Float = 0f,
        val swimKick: Float = 0f, val swimArm: Float = 0f,
        val climbArm: Float = 0f, val climbLeg: Float = 0f,
        val rideSpread: Float = 0f, val rideArm: Float = 0f, val rideLean: Float = 0f,
        val elytraArm: Float = 0f, val elytraLeg: Float = 0f,
        val useBrace: Float = 0f, val useChew: Float = 0f,
        val hurtRecoil: Float = 0f, val hurtFlail: Float = 0f,
        val deathFlop: Float = 0f,
    )

    private val STYLES = linkedMapOf(
        "HEROIC" to Style(
            "Heroic",
            walkArm = 1.3f, walkLeg = 1.2f, walkBob = 0.5f, walkLean = 0f, walkSway = 0.05f,
            walkDrop = 0f, roll = 0.02f, swagger = 0.07f, headBob = 0.01f, headRoll = 0.02f, armFlare = 0.12f,
            sprintArm = 1.6f, sprintLeg = 1.4f, sprintBob = 0.9f, sprintLean = 0.24f, sprintSway = 0.08f,
            posture = -0.06f, idleBreath = 0.35f, idleRate = 0.05f, idleSway = 0.02f, idleArmOpen = 0.16f, idleHeadDrop = -0.04f,
            jumpArms = 1.0f, jumpLegs = 0.8f, fallSpread = 0.9f, fallLegs = 0.25f,
            landDip = 1.8f, landLean = 0.25f,
            crouchLean = 0.9f, crouchArm = 0.5f, crouchLeg = 0.3f,
            swimKick = 0.6f, swimArm = 0.5f,
            climbArm = 0.6f, climbLeg = 0.55f,
            rideSpread = 0.2f, rideArm = 0.8f, rideLean = -0.05f,
            elytraArm = 0.5f, elytraLeg = 0.15f,
            useBrace = 0.25f, useChew = 0.1f,
            hurtRecoil = 0.3f, hurtFlail = 0.6f,
            deathFlop = 0.9f,
        ),
        "STEALTH" to Style(
            "Stealth",
            walkArm = 0.45f, walkLeg = 0.8f, walkBob = 0.1f, walkLean = 0.2f, walkSway = 0.015f,
            walkDrop = 1.4f, roll = 0.01f, swagger = -0.05f, headBob = 0f, headRoll = 0f, armFlare = -0.05f,
            sprintArm = 0.8f, sprintLeg = 1.0f, sprintBob = 0.3f, sprintLean = 0.42f, sprintSway = 0.03f,
            posture = 0.12f, idleBreath = 0.12f, idleRate = 0.04f, idleSway = 0.0f, idleArmOpen = -0.05f,
            idleHeadDrop = 0.08f,
            jumpArms = 0.4f, jumpLegs = 0.5f, fallSpread = 0.25f, fallLegs = 0.05f,
            landDip = 2.6f, landLean = 0.35f,
            crouchLean = 1.5f, crouchArm = 1.0f, crouchLeg = 0.6f,
            swimKick = 0.35f, swimArm = 0.25f,
            climbArm = 0.35f, climbLeg = 0.35f,
            rideSpread = 0.08f, rideArm = 0.5f, rideLean = 0.18f,
            elytraArm = 0.25f, elytraLeg = 0.05f,
            useBrace = 0.3f, useChew = 0.05f,
            hurtRecoil = 0.2f, hurtFlail = 0.3f,
            deathFlop = 0.5f,
        ),
        "LIGHT" to Style(
            "Lightfoot",
            walkArm = 1.35f, walkLeg = 1.25f, walkBob = 0.9f, walkLean = 0.05f, walkSway = 0.02f,
            walkDrop = 0f, roll = 0f, swagger = 0f, headBob = 0.03f, headRoll = 0.02f, armFlare = 0.03f, bobSharp = 2f,
            launchDip = 1.0f, launchStretch = 1.3f, launchArms = 1.3f, launchKnee = 0.6f,
            sprintArm = 1.6f, sprintLeg = 1.4f, sprintBob = 1.2f, sprintLean = 0.2f, sprintSway = 0.04f,
            posture = -0.02f, idleBreath = 0.4f, idleRate = 0.1f, idleSway = 0.05f, idleArmOpen = 0.04f,
            idleHeadDrop = 0f,
            jumpArms = 1.2f, jumpLegs = 1.0f, fallSpread = 0.9f, fallLegs = 0.3f,
            landDip = 0.9f, landLean = 0.08f,
            crouchLean = 0.6f, crouchArm = 0.35f, crouchLeg = 0.2f,
            swimKick = 0.8f, swimArm = 0.6f,
            climbArm = 0.8f, climbLeg = 0.7f,
            rideSpread = 0.22f, rideArm = 0.9f, rideLean = 0.05f,
            elytraArm = 0.7f, elytraLeg = 0.25f,
            useBrace = 0.12f, useChew = 0.2f,
            hurtRecoil = 0.4f, hurtFlail = 0.8f,
            deathFlop = 1.1f,
        ),
        "HEAVY" to Style(
            "Heavy",
            walkArm = 0.45f, walkLeg = 0.8f, walkBob = 0.12f, walkLean = 0.1f, walkSway = 0.03f,
            walkDrop = 1.1f, roll = 0.05f, swagger = 0.02f, headBob = 0.04f, headRoll = 0.03f, armFlare = 0.02f, stomp = 1.2f,
            launchDip = 2.4f, launchStretch = 0.25f, launchArms = 0.25f, launchKnee = 0.9f,
            sprintArm = 0.7f, sprintLeg = 0.95f, sprintBob = 0.2f, sprintLean = 0.3f, sprintSway = 0.05f,
            posture = 0.11f, idleBreath = 0.4f, idleRate = 0.035f, idleSway = 0.02f, idleArmOpen = 0.03f,
            idleHeadDrop = 0.09f,
            jumpArms = 0.2f, jumpLegs = 0.2f, fallSpread = 0.15f, fallLegs = 0.02f,
            landDip = 3.0f, landLean = 0.4f,
            crouchLean = 0.9f, crouchArm = 0.5f, crouchLeg = 0.3f,
            swimKick = 0.35f, swimArm = 0.25f,
            climbArm = 0.45f, climbLeg = 0.4f,
            rideSpread = 0.06f, rideArm = 0.5f, rideLean = 0.2f,
            elytraArm = 0.25f, elytraLeg = 0.05f,
            useBrace = 0.55f, useChew = 0.08f,
            hurtRecoil = 0.5f, hurtFlail = 0.2f,
            deathFlop = 0.5f,
        ),
        "WEARY" to Style(
            "Weary",
            walkArm = 0.35f, walkLeg = 0.65f, walkBob = 0.3f, walkLean = 0.16f, walkSway = 0.08f,
            walkDrop = 0.9f, roll = 0.05f, swagger = 0.03f, headBob = 0.06f, headRoll = 0.05f, armFlare = 0.08f,
            sprintArm = 0.7f, sprintLeg = 0.9f, sprintBob = 0.8f, sprintLean = 0.34f, sprintSway = 0.12f,
            posture = 0.14f, idleBreath = 0.7f, idleRate = 0.03f, idleSway = 0.05f, idleArmOpen = 0.1f,
            idleHeadDrop = 0.16f,
            jumpArms = 0.3f, jumpLegs = 0.3f, fallSpread = 0.4f, fallLegs = 0.1f,
            landDip = 2.2f, landLean = 0.32f,
            crouchLean = 1.1f, crouchArm = 0.6f, crouchLeg = 0.4f,
            swimKick = 0.3f, swimArm = 0.2f,
            climbArm = 0.3f, climbLeg = 0.3f,
            rideSpread = 0.1f, rideArm = 0.4f, rideLean = 0.2f,
            elytraArm = 0.2f, elytraLeg = 0.05f,
            useBrace = 0.4f, useChew = 0.16f,
            hurtRecoil = 0.4f, hurtFlail = 0.4f,
            deathFlop = 0.6f,
        ),
    )

    val styleKeys: List<String> get() = STYLES.keys.toList()

    fun styleNamed(key: String?): Style? = key?.let { STYLES[it] }

    fun labelOf(key: String): String = STYLES[key]?.label ?: key

    fun nextStyle(key: String): String {
        val keys = styleKeys
        return keys[(keys.indexOf(key) + 1).mod(keys.size)]
    }

    private const val AIR_DELAY = 0.1f
    private const val LAND_MIN_FALL = 0.12f
    private const val LAND_FALL_SPAN = 0.55f
    private const val LAND_TAU = 0.2f
    private const val TAU = 0.08f
    private const val STALE_NANOS = 5_000_000_000L
    private const val LEG_SWING = 1.4f
    private const val GAIT_PHASE = 0.6662f
    private const val MASTER = 0.7f
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
        var styleIndex = 0
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
        var wRide = 0f
        var wFly = 0f
        var wUse = 0f
        var wBlock = 0f
        var wHurt = 0f
        var wDead = 0f
        var wCrouch = 0f
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
        body.styleIndex = Math.floorMod(entity.uuid.hashCode() * -1640531535, STYLES.size)
        body.seenNanos = System.nanoTime()
    }

    private fun enabled(): Boolean =
        OverwatchConfig.current.locomotionEnabled && OverwatchGate.isInGame()

    private fun approach(current: Float, goal: Float, a: Float): Float = current + (goal - current) * a

    private fun clamp01(x: Float): Float = x.coerceIn(0f, 1f)

    private fun step(body: Body, state: AvatarRenderState, config: OverwatchConfig) {
        val now = System.nanoTime()
        val dt = if (body.lastNanos == 0L) 0f else ((now - body.lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        body.lastNanos = now
        val a = 1f - exp(-dt / TAU)
        val slow = 1f - exp(-dt / (TAU * 2f))

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
        val moving = clamp01(state.walkAnimationSpeed / 0.4f)
        val sprintGoal = if (body.sprinting && grounded && !swimming) 1f else 0f
        body.wSprint = approach(body.wSprint, sprintGoal, a)
        body.wMove = approach(body.wMove, if (grounded) moving else 0f, a)

        body.wClimb = approach(body.wClimb, if (body.climbing) 1f else 0f, a)
        body.climbMove = approach(body.climbMove, clamp01(abs(body.vy) / 0.1f), a)
        body.wSwim = approach(body.wSwim, if (swimming) 1f else 0f, a)
        body.wTread = approach(body.wTread, if (tread) 1f else 0f, a)
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

    private fun pull(current: Float, goal: Float, w: Float): Float = current + (goal - current) * w

    @JvmStatic
    fun apply(model: HumanoidModel<*>, humanoid: HumanoidRenderState) {
        val state = humanoid as? AvatarRenderState ?: return
        val armsClaimed = WeaponAnimations.claimsArms(state)
        val config = OverwatchConfig.current
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
        val style = (if (!local && config.locomotionRandomizeOthers) STYLES.values.elementAt(body.styleIndex) else STYLES[config.locomotionStyle]) ?: run {
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

        if (config.locomotionWalk) {
            val wSprint = body.wSprint * grounded
            val wWalk = body.wMove * grounded * (1f - body.wSprint)
            val armMul = (style.walkArm - 1f) * wWalk + (style.sprintArm - 1f) * wSprint
            val legMul = (style.walkLeg - 1f) * wWalk + (style.sprintLeg - 1f) * wSprint
            val gr = grounded * (1f - body.wCrouch)
            rLeg.xRot += gait * LEG_SWING * (sp * legMul + boost) * k
            lLeg.xRot += gaitOpp * LEG_SWING * (sp * legMul + boost) * k
            rLeg.zRot += style.swagger * sp * gr * k
            lLeg.zRot -= style.swagger * sp * gr * k
            if (!owned) {
                val bob = style.walkBob * wWalk + style.sprintBob * wSprint
                val sway = style.walkSway * wWalk + style.sprintSway * wSprint
                val leanAmt = style.walkLean * wWalk + style.sprintLean * wSprint
                rArm.xRot += gaitOpp * (sp * armMul + boost) * k
                lArm.xRot += gait * (sp * armMul + boost) * k
                rArm.zRot += style.armFlare * sp * gr * k
                lArm.zRot -= style.armFlare * sp * gr * k
                val stride = Math.pow((1f - abs(gait)).toDouble(), style.bobSharp.toDouble()).toFloat()
                val footfall = abs(gait) * abs(gait) * abs(gait)
                torso.shift += -stride * sp * bob * k + style.walkDrop * wWalk * k + footfall * sp * style.stomp * (wWalk + 1.5f * wSprint) * k
                torso.pitch += footfall * sp * style.stomp * 0.03f * (wWalk + wSprint) * k
                torso.yaw += sin(phase) * sp * sway * k
                torso.roll += gait * sp * style.roll * gr * k
                torso.pitch += leanAmt * k * clamp01(sp * 2f)
                model.head.zRot -= gait * sp * style.headRoll * gr * k
                model.head.xRot += sin(phase * 2f) * sp * style.headBob * gr * k
                val idle = gr * (1f - body.wMove)
                torso.pitch += style.posture * gr * k
                model.head.xRot += style.idleHeadDrop * idle * k
                torso.shift += sin(state.ageInTicks * style.idleRate * 6.2832f) * style.idleBreath * idle * k
                torso.yaw += sin(state.ageInTicks * style.idleRate * 2.3f) * style.idleSway * idle * k
                torso.roll += sin(state.ageInTicks * style.idleRate * 1.7f) * style.idleSway * 0.6f * idle * k
                rArm.zRot += style.idleArmOpen * idle * k
                lArm.zRot -= style.idleArmOpen * idle * k
            }
        }

        if (config.locomotionJump) {
            val air = body.wAir
            val rise = body.wRise
            val fall = body.wFall
            if (armsFree) {
                rArm.xRot += -style.jumpArms * rise * air * k
                lArm.xRot += -style.jumpArms * rise * air * k
                rArm.zRot += style.fallSpread * fall * air * k
                lArm.zRot -= style.fallSpread * fall * air * k
            }
            rLeg.xRot += (-style.jumpLegs * rise + 0.25f * fall) * air * k
            lLeg.xRot += (0.4f * style.jumpLegs * rise + 0.1f * fall) * air * k
            rLeg.zRot += style.fallLegs * fall * air * k
            lLeg.zRot -= style.fallLegs * fall * air * k
            val launch = clamp01(1f - body.launchAge / LAUNCH_SECONDS)
            if (launch > 0.001f) {
                val u = body.launchAge / LAUNCH_SECONDS
                val compress = clamp01(1f - u / 0.4f)
                val stretch = sin(clamp01(u / 0.75f) * PI.toFloat())
                val drive = sin(clamp01(u / 0.5f) * PI.toFloat() * 0.5f) * launch
                val speedLean = clamp01(pace) * 0.1f
                torso.shift += compress * compress * style.launchDip * k - stretch * style.launchStretch * k
                torso.pitch += (compress * 0.16f + speedLean * stretch) * k
                val leadLeg = if (body.leadRight) rLeg else lLeg
                val trailLeg = if (body.leadRight) lLeg else rLeg
                leadLeg.xRot -= drive * 0.9f * k
                trailLeg.xRot += (stretch * 0.6f + compress * 0.1f) * k
                if (armsFree) {
                    val swing = sin(clamp01(u / 0.6f) * PI.toFloat()) * style.launchArms
                    val drivenArm = if (body.leadRight) lArm else rArm
                    val otherArm = if (body.leadRight) rArm else lArm
                    drivenArm.xRot -= swing * 0.9f * k
                    otherArm.xRot -= swing * 0.6f * k
                }
            }
            val land = body.wLand
            if (land > 0.001f) {
                val dip = style.landDip * land * k
                torso.shift += dip
                torso.pitch += style.landLean * land * k
                rLeg.xRot -= 0.5f * land * k
                lLeg.xRot -= 0.5f * land * k
                if (armsFree) {
                    rArm.zRot += 0.35f * land * k
                    lArm.zRot -= 0.35f * land * k
                }
            }
        }

        if (config.locomotionCrouch && body.wCrouch > 0.001f) {
            val w = body.wCrouch
            torso.pitch += style.crouchLean * 0.25f * w * k
            if (armsFree) {
                rArm.xRot -= style.crouchArm * 0.4f * w * k
                lArm.xRot -= style.crouchArm * 0.4f * w * k
                rArm.zRot -= 0.1f * style.crouchArm * w * k
                lArm.zRot += 0.1f * style.crouchArm * w * k
            }
            rLeg.xRot -= style.crouchLeg * w * k * (0.6f + 0.4f * gait * sp)
            lLeg.xRot -= style.crouchLeg * w * k * (0.6f - 0.4f * gait * sp)
            model.head.xRot += 0.1f * w * k
        }

        if (config.locomotionSwim) {
            val beat = state.ageInTicks * 0.9f
            val wSwim = body.wSwim
            val wTread = body.wTread
            rLeg.xRot += sin(beat) * style.swimKick * wSwim * k + sin(beat * 0.6f) * 0.45f * wTread * k
            lLeg.xRot += sin(beat + PI.toFloat()) * style.swimKick * wSwim * k - sin(beat * 0.6f) * 0.45f * wTread * k
            if (armsFree) {
                val stroke = sin(state.ageInTicks * 0.45f)
                rArm.zRot += (stroke * style.swimArm * 0.5f * wSwim + (0.55f + 0.25f * stroke) * wTread) * k
                lArm.zRot -= (stroke * style.swimArm * 0.5f * wSwim + (0.55f - 0.25f * stroke) * wTread) * k
            }
        }

        if (config.locomotionClimb && body.wClimb > 0.001f) {
            val w = body.wClimb
            val ph = state.ageInTicks * 0.4f
            val amp = body.climbMove
            if (armsFree) {
                rArm.xRot = pull(rArm.xRot, -1.9f + sin(ph) * style.climbArm * amp, w * k.coerceAtMost(1f))
                lArm.xRot = pull(lArm.xRot, -1.9f - sin(ph) * style.climbArm * amp, w * k.coerceAtMost(1f))
                rArm.zRot += 0.15f * w * k
                lArm.zRot -= 0.15f * w * k
            }
            rLeg.xRot += (-0.35f - sin(ph + PI.toFloat()) * style.climbLeg * amp) * w * k
            lLeg.xRot += (-0.35f - sin(ph) * style.climbLeg * amp) * w * k
            torso.pitch += 0.08f * w * k
        }

        if (config.locomotionRide && body.wRide > 0.001f) {
            val w = body.wRide
            rLeg.zRot += style.rideSpread * w * k
            lLeg.zRot -= style.rideSpread * w * k
            if (armsFree) {
                rArm.xRot -= style.rideArm * w * k
                lArm.xRot -= style.rideArm * w * k
                rArm.zRot += 0.1f * w * k
                lArm.zRot -= 0.1f * w * k
            }
            torso.pitch += style.rideLean * w * k
        }

        if (config.locomotionElytra && body.wFly > 0.001f) {
            val w = body.wFly
            if (armsFree) {
                rArm.xRot += style.elytraArm * w * k
                lArm.xRot += style.elytraArm * w * k
                rArm.zRot += 0.12f * w * k
                lArm.zRot -= 0.12f * w * k
            }
            rLeg.xRot += style.elytraLeg * w * k
            lLeg.xRot += style.elytraLeg * w * k
            rLeg.zRot -= 0.08f * w * k
            lLeg.zRot += 0.08f * w * k
        }

        if (config.locomotionUseItem) {
            if (body.wUse > 0.001f) {
                val chew = sin(state.ageInTicks * 1.6f) * style.useChew * body.wUse * k
                model.head.xRot += chew
                model.head.y += chew * 0.5f
            }
            if (body.wBlock > 0.001f) {
                val w = body.wBlock
                torso.pitch += -style.useBrace * w * k
                rLeg.xRot -= 0.15f * w * k
                lLeg.xRot += 0.25f * w * k
            }
        }

        if (config.locomotionHurt && body.wHurt > 0.001f) {
            val w = body.wHurt
            torso.pitch += -style.hurtRecoil * w * k
            model.head.xRot -= 0.25f * w * k
            if (armsFree) {
                rArm.zRot += style.hurtFlail * w * k
                lArm.zRot -= style.hurtFlail * w * k
            }
        }

        if (config.locomotionDeath && body.wDead > 0.001f) {
            val w = body.wDead
            if (armsFree) {
                rArm.zRot += style.deathFlop * w * k
                lArm.zRot -= style.deathFlop * w * k
                rArm.xRot = pull(rArm.xRot, -0.3f, w * 0.5f)
                lArm.xRot = pull(lArm.xRot, -0.3f, w * 0.5f)
            }
            rLeg.zRot += 0.3f * w * k
            lLeg.zRot -= 0.3f * w * k
        }

        clampParts(model, armsFree, body.wClimb > 0.3f)
        applyTorso(model, armsFree)
        guardJoints(model, armsFree)
        if (config.locomotionBend) setBends(model, body, style, armsFree, owned, k) else clearBends(model)
    }

    private fun setBend(part: ModelPart, angle: Float, stamp: Long) {
        (part as BendHolder).setLimbBend(angle, stamp)
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

    private fun knee(leg: ModelPart, body: Body, k: Float): Float {
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

    private fun elbow(arm: ModelPart, armsFree: Boolean, owned: Boolean): Float {
        if (owned || !armsFree) return 0f
        val forward = max(0f, -arm.xRot)
        return -(ELBOW_REST + ELBOW_FORWARD * forward).coerceIn(0f, ELBOW_LIMIT)
    }

    private fun setBends(model: HumanoidModel<*>, body: Body, style: Style, armsFree: Boolean, owned: Boolean, k: Float) {
        val stamp = System.nanoTime()
        val rKnee = knee(model.rightLeg, body, k)
        val lKnee = knee(model.leftLeg, body, k)
        val rElbow = if (owned) combatElbow(model.rightArm, true) else elbow(model.rightArm, armsFree, false)
        val lElbow = if (owned) combatElbow(model.leftArm, false) else elbow(model.leftArm, armsFree, false)
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

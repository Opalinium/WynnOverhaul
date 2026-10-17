package opal.dev.wynnoverhaul.client

import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal object Trunk {
    const val SEGMENTS = 8
    const val HIP = 12f
    const val LENGTH = 12f
    const val PELVIS_SHARE = 0.35f
    private const val SHOULDER_U = 10f / 12f
    private const val HEAD_PITCH_FOLLOW = 0.15f
    private const val HEAD_YAW_FOLLOW = 0.3f
    private const val HEAD_ROLL_FOLLOW = 0.5f
    private const val EPS = 0.0005f

    private val chainP = FloatArray((SEGMENTS + 1) * 3)
    private val chainR = FloatArray((SEGMENTS + 1) * 9)
    private val pelvis = FloatArray(9)
    private val scratch = FloatArray(9)
    private val mid = FloatArray(9)
    private val qChest = Quaternionf()
    private val qSpine = Quaternionf()
    private val qPart = Quaternionf()
    private val euler = Vector3f()

    private var accTwist = 0f
    private var accCurl = 0f
    private var accBend = 0f

    fun twistProfile(u: Float): Float = (u / SHOULDER_U).coerceIn(0f, 1f)

    fun curlProfile(u: Float): Float {
        val c = (u / SHOULDER_U).coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    fun rotation(roll: Float, yaw: Float, pitch: Float, out: FloatArray, o: Int) {
        val cz = cos(roll)
        val sz = sin(roll)
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cx = cos(pitch)
        val sx = sin(pitch)
        out[o] = cz * cy
        out[o + 1] = cz * sy * sx - sz * cx
        out[o + 2] = cz * sy * cx + sz * sx
        out[o + 3] = sz * cy
        out[o + 4] = sz * sy * sx + cz * cx
        out[o + 5] = sz * sy * cx - cz * sx
        out[o + 6] = -sy
        out[o + 7] = cy * sx
        out[o + 8] = cy * cx
    }

    fun fillChain(twist: Float, curl: Float, bend: Float, p: FloatArray, r: FloatArray) {
        val seg = LENGTH / SEGMENTS
        p[0] = 0f
        p[1] = HIP
        p[2] = 0f
        for (j in 0..SEGMENTS) {
            val u = j.toFloat() / SEGMENTS
            rotation(bend * curlProfile(u), twist * twistProfile(u), curl * curlProfile(u), r, j * 9)
        }
        for (i in 0 until SEGMENTS) {
            val u = (i + 0.5f) / SEGMENTS
            rotation(bend * curlProfile(u), twist * twistProfile(u), curl * curlProfile(u), scratch, 0)
            p[(i + 1) * 3] = p[i * 3] - scratch[1] * seg
            p[(i + 1) * 3 + 1] = p[i * 3 + 1] - scratch[4] * seg
            p[(i + 1) * 3 + 2] = p[i * 3 + 2] - scratch[7] * seg
        }
    }

    private fun chainAt(u: Float, twist: Float, curl: Float, bend: Float, out: FloatArray) {
        val x = u * SEGMENTS
        val i = x.toInt().coerceIn(0, SEGMENTS - 1)
        val f = x - i
        val seg = LENGTH / SEGMENTS
        val midU = (i + 0.5f) / SEGMENTS
        rotation(bend * curlProfile(midU), twist * twistProfile(midU), curl * curlProfile(midU), mid, 0)
        out[0] = chainP[i * 3] - mid[1] * seg * f
        out[1] = chainP[i * 3 + 1] - mid[4] * seg * f
        out[2] = chainP[i * 3 + 2] - mid[7] * seg * f
    }

    private val point = FloatArray(3)
    private val hold = FloatArray(9)

    private fun place(part: ModelPart, twist: Float, curl: Float, bend: Float, rp: FloatArray, hipPose: Boolean) {
        val u = ((HIP - part.y) / LENGTH).coerceIn(0f, 1f)
        val ycl = HIP - u * LENGTH
        chainAt(u, twist, curl, bend, point)
        rotation(bend * curlProfile(u), twist * twistProfile(u), curl * curlProfile(u), hold, 0)
        val lx = part.x
        val ly = part.y - ycl
        val lz = part.z
        var x = point[0] + hold[0] * lx + hold[1] * ly + hold[2] * lz
        var y = point[1] + hold[3] * lx + hold[4] * ly + hold[5] * lz
        var z = point[2] + hold[6] * lx + hold[7] * ly + hold[8] * lz
        if (hipPose) {
            val dy = y - HIP
            val nx = rp[0] * x + rp[1] * dy + rp[2] * z
            val ny = rp[3] * x + rp[4] * dy + rp[5] * z
            val nz = rp[6] * x + rp[7] * dy + rp[8] * z
            x = nx
            y = HIP + ny
            z = nz
        }
        part.x = x
        part.y = y
        part.z = z
    }

    private fun composeEuler(part: ModelPart, chest: Quaternionf) {
        qPart.rotationZYX(part.zRot, part.yRot, part.xRot)
        chest.mul(qPart, qPart)
        qPart.getEulerAnglesZYX(euler)
        part.xRot = euler.x
        part.yRot = euler.y
        part.zRot = euler.z
    }

    fun apply(
        model: HumanoidModel<*>,
        hipYaw: Float, hipPitch: Float, hipRoll: Float,
        twist: Float, curl: Float, bend: Float,
        ownedArms: Boolean,
    ) {
        if (abs(hipYaw) < EPS && abs(hipPitch) < EPS && abs(hipRoll) < EPS && abs(twist) < EPS && abs(curl) < EPS && abs(bend) < EPS) return
        val hipTurn = abs(hipYaw) > EPS || abs(hipPitch) > EPS || abs(hipRoll) > EPS
        fillChain(twist, curl, bend, chainP, chainR)
        rotation(hipRoll, hipYaw, hipPitch, pelvis, 0)

        val body = model.body
        if (hipTurn) {
            val dy = body.y - HIP
            val nx = pelvis[0] * body.x + pelvis[1] * dy + pelvis[2] * body.z
            val ny = pelvis[3] * body.x + pelvis[4] * dy + pelvis[5] * body.z
            val nz = pelvis[6] * body.x + pelvis[7] * dy + pelvis[8] * body.z
            body.x = nx
            body.y = HIP + ny
            body.z = nz
            qPart.rotationZYX(body.zRot, body.yRot, body.xRot)
            qChest.rotationZYX(hipRoll, hipYaw, hipPitch).mul(qPart, qPart)
            qPart.getEulerAnglesZYX(euler)
            body.xRot = euler.x
            body.yRot = euler.y
            body.zRot = euler.z
        }

        place(model.head, twist, curl, bend, pelvis, hipTurn)
        place(model.rightArm, twist, curl, bend, pelvis, hipTurn)
        place(model.leftArm, twist, curl, bend, pelvis, hipTurn)

        qSpine.rotationZYX(bend * curlProfile(SHOULDER_U), twist * twistProfile(SHOULDER_U), curl * curlProfile(SHOULDER_U))
        qChest.rotationZYX(hipRoll, hipYaw, hipPitch).mul(qSpine)
        qChest.getEulerAnglesZYX(euler)
        model.head.xRot += euler.x * HEAD_PITCH_FOLLOW
        model.head.yRot += euler.y * HEAD_YAW_FOLLOW
        model.head.zRot += euler.z * HEAD_ROLL_FOLLOW
        if (ownedArms) {
            val pitch = hipPitch + curl * curlProfile(SHOULDER_U)
            model.rightArm.xRot += pitch
            model.leftArm.xRot += pitch
        } else {
            composeEuler(model.rightArm, qChest)
            composeEuler(model.leftArm, qChest)
        }
        accTwist += twist
        accCurl += curl
        accBend += bend
    }

    fun flush(model: HumanoidModel<*>) {
        val stamp = System.nanoTime()
        spine(model.body, stamp)
        if (model is PlayerModel) spine(model.jacket, stamp)
        accTwist = 0f
        accCurl = 0f
        accBend = 0f
    }

    private fun spine(part: ModelPart, stamp: Long) {
        ((part as Any) as BendHolder).setSpine(accTwist, accCurl, accBend, stamp)
    }
}

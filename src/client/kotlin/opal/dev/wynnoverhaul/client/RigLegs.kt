package opal.dev.wynnoverhaul.client

import net.minecraft.client.model.HumanoidModel
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal object RigLegs {
    private const val LEG = 12f
    private const val HALF = 6f
    private const val HIP_X = 1.9f
    private const val HIP_Y = 12f
    private const val HIP_Z = 0.1f
    private const val GROUND = HIP_Y + LEG
    private const val REACH_EPS = 0.002f
    private const val MAX_RISE = 8f
    private const val MAX_DROP = 14f
    private const val STALE_NANOS = 120_000_000L

    private val hip = FloatArray(6)
    private val foot = FloatArray(6)
    private val angles = FloatArray(6)
    private val bends = FloatArray(2)

    private class Knees {
        var left = 0f
        var right = 0f
        var weight = 0f
        var stamp = 0L
    }

    private val knees = it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<Knees>()

    @JvmStatic
    fun kneeWeight(id: Int): Float {
        val k = knees.get(id) ?: return 0f
        return if (System.nanoTime() - k.stamp < STALE_NANOS) k.weight else 0f
    }

    @JvmStatic
    fun kneeLeft(id: Int): Float = knees.get(id)?.left ?: 0f

    @JvmStatic
    fun kneeRight(id: Int): Float = knees.get(id)?.right ?: 0f

    @JvmStatic
    fun active(id: Int): Boolean = kneeWeight(id) > 0.01f

    private fun remember(id: Int, w: Float) {
        val now = System.nanoTime()
        if (knees.size > 64) knees.values.removeIf { now - it.stamp > STALE_NANOS }
        val k = knees.get(id) ?: Knees().also { knees.put(id, it) }
        k.left = bends[0]
        k.right = bends[1]
        k.weight = w
        k.stamp = now
    }

    private var lowered = 0f

    fun angle(index: Int): Float = angles[index]

    fun bend(leg: Int): Float = bends[leg]

    fun lowered(): Float = lowered

    fun compute(
        yawSign: Float, hipY: Float, rootF: Float, rootS: Float, rootH: Float,
        leftF: Float, leftS: Float, leftU: Float, rightF: Float, rightS: Float, rightU: Float,
        groundLeft: Float, groundRight: Float,
    ) {
        val twist = yawSign * hipY
        val st = sin(twist)
        val ct = cos(twist)
        hip[0] = HIP_X * ct + rootS
        hip[1] = HIP_Z - HIP_X * st - rootF
        hip[3] = -HIP_X * ct + rootS
        hip[4] = HIP_Z + HIP_X * st - rootF
        foot[0] = HIP_X + leftS
        foot[1] = HIP_Z - leftF
        foot[2] = GROUND - leftU - groundLeft
        foot[3] = -HIP_X + rightS
        foot[4] = HIP_Z - rightF
        foot[5] = GROUND - rightU - groundRight
        var drop = rootH
        for (i in 0..1) {
            val dx = foot[i * 3] - hip[i * 3]
            val dz = foot[i * 3 + 1] - hip[i * 3 + 1]
            val vmax = sqrt(max(LEG * LEG - dx * dx - dz * dz, 0f)) - REACH_EPS
            drop = max(drop, foot[i * 3 + 2] - vmax - HIP_Y)
        }
        lowered = drop.coerceIn(-MAX_RISE, MAX_DROP)
        solve(0, twist, lowered)
        solve(1, twist, lowered)
    }

    private class Source {
        var id = -1
        var weight = 0f
        val v = FloatArray(10)
    }

    private val clip = Source()
    private val gait = Source()
    private val mixed = FloatArray(10)

    fun submitClip(
        id: Int, w: Float, hipY: Float, rootF: Float, rootS: Float, rootH: Float,
        leftF: Float, leftS: Float, leftU: Float, rightF: Float, rightS: Float, rightU: Float,
    ) {
        store(clip, id, w, hipY, rootF, rootS, rootH, leftF, leftS, leftU, rightF, rightS, rightU)
    }

    fun submitGait(
        id: Int, w: Float, rootH: Float,
        leftF: Float, leftS: Float, leftU: Float, rightF: Float, rightS: Float, rightU: Float,
    ) {
        store(gait, id, w, 0f, 0f, 0f, rootH, leftF, leftS, leftU, rightF, rightS, rightU)
    }

    private fun store(
        s: Source, id: Int, w: Float, hipY: Float, rootF: Float, rootS: Float, rootH: Float,
        leftF: Float, leftS: Float, leftU: Float, rightF: Float, rightS: Float, rightU: Float,
    ) {
        s.id = id
        s.weight = w
        s.v[0] = hipY
        s.v[1] = rootF
        s.v[2] = rootS
        s.v[3] = rootH
        s.v[4] = leftF
        s.v[5] = leftS
        s.v[6] = leftU
        s.v[7] = rightF
        s.v[8] = rightS
        s.v[9] = rightU
    }

    fun flush(model: HumanoidModel<*>, id: Int, yawSign: Float, groundLeft: Float, groundRight: Float) {
        val wc = if (clip.id == id) clip.weight else 0f
        val wg = if (gait.id == id) gait.weight else 0f
        clip.id = -1
        gait.id = -1
        val total = wc + wg
        if (total <= 0.001f) {
            knees.get(id)?.weight = 0f
            return
        }
        for (i in 0 until 10) mixed[i] = (wc * clip.v[i] + wg * gait.v[i]) / total
        val w = if (total > 1f) 1f else total
        compute(
            yawSign, mixed[0], mixed[1], mixed[2], mixed[3], mixed[4], mixed[5], mixed[6], mixed[7], mixed[8], mixed[9],
            groundLeft, groundRight,
        )
        remember(id, w)
        val left = model.leftLeg
        val right = model.rightLeg
        left.x = lerp(left.x, hip[0], w)
        left.y = lerp(left.y, HIP_Y + lowered, w)
        left.z = lerp(left.z, hip[1], w)
        right.x = lerp(right.x, hip[3], w)
        right.y = lerp(right.y, HIP_Y + lowered, w)
        right.z = lerp(right.z, hip[4], w)
        left.xRot = lerp(left.xRot, angles[0], w)
        left.yRot = lerp(left.yRot, angles[1], w)
        left.zRot = lerp(left.zRot, angles[2], w)
        right.xRot = lerp(right.xRot, angles[3], w)
        right.yRot = lerp(right.yRot, angles[4], w)
        right.zRot = lerp(right.zRot, angles[5], w)
        val shift = lowered * w
        val shiftX = mixed[2] * w
        val shiftZ = -mixed[1] * w
        for (part in arrayOf(model.body, model.head, model.rightArm, model.leftArm)) {
            part.x += shiftX
            part.y += shift
            part.z += shiftZ
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun solve(leg: Int, twist: Float, lowered: Float) {
        val i = leg * 3
        var vx = foot[i] - hip[i]
        var vy = foot[i + 2] - (HIP_Y + lowered)
        var vz = foot[i + 1] - hip[i + 1]
        var d = sqrt(vx * vx + vy * vy + vz * vz)
        val limit = LEG - REACH_EPS
        if (d > limit) {
            val k = limit / d
            vx *= k
            vy *= k
            vz *= k
            d = limit
        }
        d = max(d, 1f)
        val inv = 1f / max(sqrt(vx * vx + vy * vy + vz * vz), 1e-6f)
        val e1x = vx * inv
        val e1y = vy * inv
        val e1z = vz * inv
        val px = -sin(twist)
        val pz = -cos(twist)
        val dot = px * e1x + pz * e1z
        var e2x = px - dot * e1x
        var e2y = -dot * e1y
        var e2z = pz - dot * e1z
        val e2n = sqrt(e2x * e2x + e2y * e2y + e2z * e2z)
        if (e2n > 1e-6f) {
            e2x /= e2n
            e2y /= e2n
            e2z /= e2n
        } else {
            e2x = 0f
            e2y = 0f
            e2z = -1f
        }
        val a = d / 2f
        val h = sqrt(max(HALF * HALF - a * a, 0f))
        val kx = a * e1x + h * e2x
        val ky = a * e1y + h * e2y
        val kz = a * e1z + h * e2z
        val tn = max(sqrt(kx * kx + ky * ky + kz * kz), 1e-6f)
        val tx = kx / tn
        val ty = ky / tn
        val tz = kz / tn
        var sx = vx - kx
        var sy = vy - ky
        var sz = vz - kz
        val sn = max(sqrt(sx * sx + sy * sy + sz * sz), 1e-6f)
        sx /= sn
        sy /= sn
        sz /= sn
        val cosB = (tx * sx + ty * sy + tz * sz).coerceIn(-1f, 1f)
        bends[leg] = acos(cosB)
        var zx = sx - cosB * tx
        var zy = sy - cosB * ty
        var zz = sz - cosB * tz
        val zn = sqrt(zx * zx + zy * zy + zz * zz)
        if (zn > 1e-6f) {
            zx /= zn
            zy /= zn
            zz /= zn
        } else {
            zx = -e2x
            zy = -e2y
            zz = -e2z
        }
        val xx = ty * zz - tz * zy
        val xy = tz * zx - tx * zz
        val xz = tx * zy - ty * zx
        val m20 = xz
        val m21 = tz
        val m22 = zz
        val o = leg * 3
        if (abs(m20) < 0.999999f) {
            angles[o] = atan2(m21, m22)
            angles[o + 2] = atan2(xy, xx)
        } else {
            angles[o] = atan2(-zy, ty)
            angles[o + 2] = 0f
        }
        angles[o + 1] = -asin(m20.coerceIn(-1f, 1f))
    }

}

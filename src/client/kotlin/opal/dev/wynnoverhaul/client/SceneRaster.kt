package opal.dev.wynnoverhaul.client

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Raster(val w: Int, val h: Int) {
    val px = IntArray(w * h)

    fun blend(x: Int, y: Int, argb: Int) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        val a = argb ushr 24
        if (a == 0) return
        val i = y * w + x
        if (a == 255) {
            px[i] = argb
            return
        }
        val o = px[i]
        val oa = o ushr 24
        val k = a / 255f
        val outA = (a + oa * (1f - k)).toInt().coerceIn(0, 255)
        val r = (((argb shr 16) and 255) * k + ((o shr 16) and 255) * (1f - k)).toInt()
        val g = (((argb shr 8) and 255) * k + ((o shr 8) and 255) * (1f - k)).toInt()
        val b = ((argb and 255) * k + (o and 255) * (1f - k)).toInt()
        px[i] = (outA shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun add(x: Int, y: Int, argb: Int) {
        if (x < 0 || y < 0 || x >= w || y >= h) return
        val a = (argb ushr 24) / 255f
        val i = y * w + x
        val o = px[i]
        val r = min(255, ((o shr 16) and 255) + (((argb shr 16) and 255) * a).toInt())
        val g = min(255, ((o shr 8) and 255) + (((argb shr 8) and 255) * a).toInt())
        val b = min(255, (o and 255) + ((argb and 255) * a).toInt())
        px[i] = (max(o ushr 24, (a * 255).toInt()) shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun rect(x1: Int, y1: Int, x2: Int, y2: Int, argb: Int) {
        for (y in max(0, y1) until min(h, y2)) for (x in max(0, x1) until min(w, x2)) blend(x, y, argb)
    }

    fun vgrad(x1: Int, y1: Int, x2: Int, y2: Int, top: Int, bottom: Int) {
        val span = max(1, y2 - y1)
        for (y in max(0, y1) until min(h, y2)) {
            val c = mix(top, bottom, (y - y1).toFloat() / span)
            for (x in max(0, x1) until min(w, x2)) blend(x, y, c)
        }
    }

    fun disc(cx: Float, cy: Float, r: Float, argb: Int) {
        for (y in floor(cy - r).toInt()..floor(cy + r).toInt()) for (x in floor(cx - r).toInt()..floor(cx + r).toInt()) {
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            if (dx * dx + dy * dy <= r * r) blend(x, y, argb)
        }
    }

    fun glow(cx: Float, cy: Float, rx: Float, ry: Float, argb: Int, strength: Float, additive: Boolean = true) {
        val a0 = ((argb ushr 24) * strength).coerceIn(0f, 255f)
        for (y in floor(cy - ry).toInt()..floor(cy + ry).toInt()) for (x in floor(cx - rx).toInt()..floor(cx + rx).toInt()) {
            val dx = (x + 0.5f - cx) / rx
            val dy = (y + 0.5f - cy) / ry
            val d = dx * dx + dy * dy
            if (d >= 1f) continue
            val f = (1f - d)
            val a = (a0 * f * f).toInt()
            val c = (a shl 24) or (argb and 0xFFFFFF)
            if (additive) add(x, y, c) else blend(x, y, c)
        }
    }

    fun poly(xs: FloatArray, ys: FloatArray, argb: Int) {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (v in ys) {
            minY = min(minY, v)
            maxY = max(maxY, v)
        }
        val n = xs.size
        for (y in max(0, floor(minY).toInt())..min(h - 1, floor(maxY).toInt())) {
            val yf = y + 0.5f
            var xl = Float.MAX_VALUE
            var xr = -Float.MAX_VALUE
            for (i in 0 until n) {
                val j = if (i + 1 == n) 0 else i + 1
                val ya = ys[i]
                val yb = ys[j]
                if ((ya <= yf && yb > yf) || (yb <= yf && ya > yf)) {
                    val x = xs[i] + (yf - ya) / (yb - ya) * (xs[j] - xs[i])
                    if (x < xl) xl = x
                    if (x > xr) xr = x
                }
            }
            if (xl <= xr) for (x in floor(xl).toInt()..floor(xr).toInt()) blend(x, y, argb)
        }
    }

    fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, argb: Int) =
        poly(floatArrayOf(ax, bx, cx), floatArrayOf(ay, by, cy), argb)

    fun ridge(profile: (Int) -> Float, top: Int, bottom: Int, rim: Int, rimWidth: Int = 1, shade: ((Int, Int) -> Int)? = null) {
        for (x in 0 until w) {
            val y0 = profile(x).toInt().coerceIn(0, h)
            for (y in y0 until h) {
                val c = if (shade != null) shade(x, y) else mix(top, bottom, ((y - y0).toFloat() / max(1, h - y0)).coerceIn(0f, 1f))
                blend(x, y, c)
            }
            for (k in 0 until rimWidth) blend(x, y0 + k, rim)
        }
    }
}

fun mix(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val aa = (a ushr 24) + (((b ushr 24) - (a ushr 24)) * k).toInt()
    val r = ((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * k).toInt()
    val g = ((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * k).toInt()
    val bl = (a and 255) + (((b and 255) - (a and 255)) * k).toInt()
    return (aa shl 24) or (r shl 16) or (g shl 8) or bl
}

fun shade(c: Int, f: Float): Int {
    val r = (((c shr 16) and 255) * f).toInt().coerceIn(0, 255)
    val g = (((c shr 8) and 255) * f).toInt().coerceIn(0, 255)
    val b = ((c and 255) * f).toInt().coerceIn(0, 255)
    return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
}

fun alpha(c: Int, a: Float): Int = (((c ushr 24) * a).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

class Noise(private val seed: Int) {
    private fun hash(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 65535f
    }

    fun value(x: Float, y: Float): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = hash(x0, y0)
        val b = hash(x0 + 1, y0)
        val c = hash(x0, y0 + 1)
        val d = hash(x0 + 1, y0 + 1)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }

    fun fbm(x: Float, y: Float, octaves: Int = 4): Float {
        var sum = 0f
        var amp = 0.5f
        var f = 1f
        var norm = 0f
        for (i in 0 until octaves) {
            sum += value(x * f, y * f) * amp
            norm += amp
            amp *= 0.5f
            f *= 2f
        }
        return sum / norm
    }

    fun rand(i: Int): Float = hash(i, i * 31 + 7)
}

fun wobble(t: Double, i: Int, speed: Double = 1.0): Float = sin(t * speed + i * 1.713).toFloat()

fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))

fun absf(v: Float): Float = abs(v)

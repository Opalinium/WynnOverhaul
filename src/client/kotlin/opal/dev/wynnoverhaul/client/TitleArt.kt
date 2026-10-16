package opal.dev.wynnoverhaul.client

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

const val ART_W = 640
const val ART_H = 270

enum class ArtPose { SIT, STAND, LOOK_UP }

class ArtLayer(val raster: Raster, val parallax: Float, val drift: Float = 0f)

class ArtFigure(val x: Float, val y: Float, val height: Float, val pose: ArtPose, val bodyRot: Float, val behind: Int)

class Baked(val layers: List<ArtLayer>, val figure: ArtFigure)

interface Fx {
    fun layer(parallax: Float)
    fun rect(x1: Float, y1: Float, x2: Float, y2: Float, argb: Int)
    fun glow(cx: Float, cy: Float, rx: Float, ry: Float, argb: Int, strength: Float)
    fun vgrad(x1: Float, y1: Float, x2: Float, y2: Float, top: Int, bottom: Int)
    fun flash(argb: Int)
}

abstract class TitleArt(val name: String) {
    abstract fun bake(): Baked
    abstract fun effects(fx: Fx, t: Double)
}

private fun Raster.pine(x: Float, baseY: Float, h: Float, w: Float, base: Int, lit: Float, snow: Int = 0) {
    val tiers = 5
    val trunk = shade(base, 0.6f)
    rect((x - w * 0.06f).toInt(), (baseY - h * 0.1f).toInt(), (x + w * 0.06f).toInt() + 1, baseY.toInt() + 1, trunk)
    for (k in tiers - 1 downTo 0) {
        val apex = baseY - h + k * h * 0.17f
        val bottom = apex + h * 0.36f
        val half = w * (0.28f + 0.14f * k)
        tri(x, apex, x - half, bottom, x, bottom, shade(base, 1f + lit))
        tri(x, apex, x, bottom, x + half, bottom, shade(base, 0.72f))
        if (snow != 0) {
            tri(x, apex, x - half * 0.55f, apex + (bottom - apex) * 0.55f, x, apex + (bottom - apex) * 0.55f, snow)
            tri(x, apex, x, apex + (bottom - apex) * 0.55f, x + half * 0.55f, apex + (bottom - apex) * 0.55f, shade(snow, 0.8f))
        }
    }
}

private fun periodic(f: (Float) -> Float): (Int) -> Float = { x ->
    val p = ART_W.toFloat()
    val xf = ((x % ART_W) + ART_W) % ART_W + 0f
    (f(xf) * (p - xf) + f(xf - p) * xf) / p
}

private fun Raster.pineWrapped(x: Float, baseY: Float, h: Float, w: Float, base: Int, lit: Float, snow: Int = 0) {
    for (k in -1..1) pine(x + k * ART_W, baseY, h, w, base, lit, snow)
}

private fun nearHills(n: Noise, seed: Float, base: Float, amp: Float, top: Int, bottom: Int): Raster {
    val r = Raster(ART_W, ART_H)
    r.ridge(periodic { x -> base + n.fbm(x * 0.016f, seed) * amp }, top, bottom, 0x00000000)
    return r
}

private fun slopeLight(profile: (Int) -> Float, x: Int): Float {
    val d = profile(x + 3) - profile(x - 3)
    return (d / 14f).coerceIn(-1f, 1f)
}

private fun litRidge(r: Raster, profile: (Int) -> Float, dark: Int, lit: Int, bottom: Int, rim: Int, depth: Float = 70f) {
    r.ridge(profile, dark, bottom, rim, 1) { x, y ->
        val y0 = profile(x)
        val s = slopeLight(profile, x)
        val facing = ((s + 1f) * 0.5f).let { it * it }
        val fade = (1f - ((y - y0) / depth).coerceIn(0f, 1f))
        mix(mix(dark, bottom, ((y - y0) / depth).coerceIn(0f, 1f)), lit, facing * fade * 0.8f)
    }
}

private fun Raster.stars(noise: Noise, count: Int, bright: Int) {
    for (i in 0 until count) {
        val x = (noise.rand(i * 3 + 1) * w).toInt()
        val y = (noise.rand(i * 3 + 2) * h * 0.45f).toInt()
        blend(x, y, alpha(bright, 0.25f + noise.rand(i * 3 + 3) * 0.45f))
    }
}

object CampfireArt : TitleArt("Campfire") {
    private const val FIRE_X = 438f
    private const val FIRE_Y = 214f

    override fun bake(): Baked {
        val n = Noise(11)
        val sky = Raster(ART_W, ART_H)
        sky.vgrad(0, 0, ART_W, 200, 0xFF03040B.toInt(), 0xFF2E3B6E.toInt())
        sky.glow(520f, 54f, 80f, 80f, 0x408FA8E0, 1f, false)
        sky.glow(520f, 54f, 36f, 36f, 0x609FB4F0.toInt(), 1f, false)
        sky.disc(520f, 54f, 16f, 0xFFE9E6D2.toInt())
        sky.disc(515f, 50f, 4f, 0xFFD0CCB4.toInt())
        sky.disc(526f, 59f, 3f, 0xFFD6D2BA.toInt())
        sky.stars(n, 90, 0xFFDCE4FF.toInt())
        val far = Raster(ART_W, ART_H)
        litRidge(far, periodic { x -> 150f + n.fbm(x * 0.012f, 3f) * 62f }, 0xFF141B38.toInt(), 0xFF3A4A86.toInt(), 0xFF0C1126.toInt(), 0x663A4A86)
        val mid = Raster(ART_W, ART_H)
        val midLine = periodic { x -> 178f + n.fbm(x * 0.02f, 9f) * 16f }
        mid.ridge(midLine, 0xFF0B1226.toInt(), 0xFF080C1A.toInt(), 0x00000000)
        var px = -10f
        var i = 0
        while (px < ART_W + 10) {
            val h = 44f + n.rand(i + 40) * 44f
            mid.pineWrapped(px, midLine(px.toInt()) + 6f, h, h * 0.5f, 0xFF0F1C2E.toInt(), 0.35f)
            px += 13f + n.rand(i + 90) * 14f
            i++
        }
        val near = nearHills(n, 17f, 196f, 11f, 0xFF070C16.toInt(), 0xFF05080F.toInt())
        val ground = Raster(ART_W, ART_H)
        val gLine = { _: Int -> 204f }
        ground.vgrad(0, 204, ART_W, ART_H, 0xFF22362A.toInt(), 0xFF060A08.toInt())
        for (k in 0 until 260) {
            val gx = (n.rand(k + 200) * ART_W).toInt()
            val gy = 206 + (n.rand(k + 700) * 62).toInt()
            ground.blend(gx, gy, alpha(0xFF3E5A3A.toInt(), 0.5f))
            ground.blend(gx, gy - 1, alpha(0xFF3E5A3A.toInt(), 0.3f))
        }
        ground.glow(FIRE_X, FIRE_Y + 2f, 150f, 34f, 0xFFFF9A3A.toInt(), 0.55f)
        ground.glow(FIRE_X, FIRE_Y, 70f, 16f, 0xFFFFC060.toInt(), 0.7f)
        val stoneCount = 12
        for (pass in 0..1) {
            if (pass == 1) {
                ground.poly(floatArrayOf(FIRE_X - 26f, FIRE_X + 22f, FIRE_X + 26f, FIRE_X - 22f), floatArrayOf(FIRE_Y - 2f, FIRE_Y - 8f, FIRE_Y - 3f, FIRE_Y + 3f), 0xFF3A2414.toInt())
                ground.poly(floatArrayOf(FIRE_X - 22f, FIRE_X + 26f, FIRE_X + 22f, FIRE_X - 26f), floatArrayOf(FIRE_Y - 8f, FIRE_Y - 2f, FIRE_Y + 3f, FIRE_Y - 3f), 0xFF2E1B0F.toInt())
            }
            for (st in 0 until stoneCount) {
                val ang = st / stoneCount.toFloat() * 2f * PI.toFloat() + 0.1f + (n.rand(st + 900) - 0.5f) * 0.06f
                val front = sin(ang) >= 0f
                if (front != (pass == 1)) continue
                val r = if (front) 5.2f else 4.4f
                val sx = FIRE_X + cos(ang) * 38f
                val sy = FIRE_Y + 1f + sin(ang) * 8.5f
                val base = if (st % 2 == 0) 0xFF595C66.toInt() else 0xFF4A4D57.toInt()
                ground.disc(sx, sy + 1f, r, shade(base, 0.55f))
                ground.disc(sx, sy, r, base)
                ground.disc(sx - r * 0.25f, sy - r * 0.3f, r * 0.62f, shade(base, 1.22f))
            }
        }
        ground.rect(300, 206, 392, 214, 0xFF2B1A0E.toInt())
        ground.rect(300, 206, 392, 207, 0xFF4A3220.toInt())
        val fg = Raster(ART_W, ART_H)
        fg.pine(34f, ART_H + 30f, 330f, 150f, 0xFF05080C.toInt(), 0.1f)
        fg.pine(614f, ART_H + 30f, 270f, 120f, 0xFF05080C.toInt(), 0.1f)
        for (k in 0 until 40) {
            val gx = (n.rand(k + 300) * ART_W)
            fg.tri(gx - 3f, ART_H.toFloat(), gx, ART_H - 9f - n.rand(k + 400) * 12f, gx + 3f, ART_H.toFloat(), 0xFF040608.toInt())
        }
        return Baked(
            listOf(ArtLayer(sky, 0.1f), ArtLayer(far, 0.3f, 1.5f), ArtLayer(mid, 0.55f, 3.5f), ArtLayer(near, 0.8f, 7f), ArtLayer(ground, 1f), ArtLayer(fg, 1.9f)),
            ArtFigure(345f, 211f, 80f, ArtPose.SIT, 130f, 5),
        )
    }

    override fun effects(fx: Fx, t: Double) {
        val n = Noise(5)
        fx.layer(0.1f)
        for (i in 0 until 60) {
            val tw = 0.5f + 0.5f * wobble(t, i, 0.9 + (i % 5) * 0.4)
            fx.rect(n.rand(i * 2 + 1) * ART_W, n.rand(i * 2 + 2) * 110f, n.rand(i * 2 + 1) * ART_W + 1.2f, n.rand(i * 2 + 2) * 110f + 1.2f, alpha(0xFFFFFFFF.toInt(), 0.15f + tw * 0.7f))
        }
        fx.layer(1f)
        val flick = 1f + 0.1f * wobble(t, 1, 11.0) + 0.06f * wobble(t, 2, 23.0)
        fx.glow(FIRE_X, FIRE_Y - 22f, 52f * flick, 40f * flick, 0xFFFF8A2A.toInt(), 0.38f)
        for (i in -10..10) {
            val edge = 1f - abs(i) / 11f
            val wob = 0.72f + 0.28f * wobble(t, i, 9.0) * wobble(t, i + 5, 5.3) + 0.12f * wobble(t, i + 9, 17.0)
            val h = (50f * edge * edge * wob + 3f).let { (it / 3f).toInt() * 3f + 3f }
            val x = FIRE_X + i * 2.6f
            val base = FIRE_Y - 6f
            fx.rect(x, base - h * 0.3f, x + 2.7f, base, 0xFFFFE9A0.toInt())
            fx.rect(x, base - h * 0.65f, x + 2.7f, base - h * 0.3f, 0xFFFF9A2E.toInt())
            fx.rect(x, base - h, x + 2.7f, base - h * 0.65f, alpha(0xFFC8321C.toInt(), 0.8f))
        }
        for (i in 0 until 16) {
            val phase = ((t * (0.18 + (i % 4) * 0.05) + i * 0.173) % 1.0).toFloat()
            val x = FIRE_X + sin(i * 12.9 + t * 0.9).toFloat() * 16f * phase + (i % 5 - 2) * 3f
            val y = FIRE_Y - 14f - phase * 110f
            fx.rect(x, y, x + 1.3f, y + 1.3f, alpha(0xFFFFA040.toInt(), (1f - phase) * 0.9f))
        }
        fx.layer(0.55f)
        for (i in 0 until 10) {
            val x = 90f + n.rand(i + 60) * 420f + sin(t * 0.3 + i).toFloat() * 14f
            val y = 160f + n.rand(i + 80) * 40f + cos(t * 0.4 + i * 2).toFloat() * 6f
            val p = 0.4f + 0.6f * wobble(t, i, 1.7)
            fx.glow(x, y, 4f, 4f, 0xFFB8FF5A.toInt(), max(0f, p) * 0.9f)
        }
    }
}

object SnowCabinArt : TitleArt("Snowed In") {
    private const val CABIN_X = 470f
    private const val CABIN_Y = 214f

    override fun bake(): Baked {
        val n = Noise(23)
        val sky = Raster(ART_W, ART_H)
        sky.vgrad(0, 0, ART_W, 200, 0xFF0A1230.toInt(), 0xFF5B6FA3.toInt())
        sky.stars(n, 50, 0xFFE8EEFF.toInt())
        val far = Raster(ART_W, ART_H)
        val peaks = periodic { x -> 120f + n.fbm(x * 0.009f, 4f) * 70f - abs(sin(x * 0.011f + 1f)) * 24f }
        litRidge(far, peaks, 0xFF4E6498.toInt(), 0xFFD2E0F8.toInt(), 0xFF2E4070.toInt(), 0xCCF0F6FF.toInt(), 90f)
        val mid = Raster(ART_W, ART_H)
        val line = periodic { x -> 186f + n.fbm(x * 0.02f, 8f) * 14f }
        mid.ridge(line, 0xFF8CA0C8.toInt(), 0xFF5A6E9A.toInt(), 0x00000000)
        var px = -6f
        var i = 0
        while (px < ART_W + 10) {
            val h = 38f + n.rand(i + 11) * 40f
            mid.pineWrapped(px, line(px.toInt()) + 8f, h, h * 0.5f, 0xFF16283E.toInt(), 0.3f, 0xFFDCE8FF.toInt())
            px += 12f + n.rand(i + 51) * 13f
            i++
        }
        val near = nearHills(n, 17f, 198f, 10f, 0xFFA8B9DA.toInt(), 0xFF7F93BD.toInt())
        val ground = Raster(ART_W, ART_H)
        ground.vgrad(0, 206, ART_W, ART_H, 0xFFC5D3EE.toInt(), 0xFF6A7FAE.toInt())
        for (k in 0 until 16) {
            val cx = n.rand(k + 100) * ART_W
            val cy = 212f + n.rand(k + 140) * 50f
            ground.glow(cx, cy, 60f + n.rand(k) * 50f, 4f + n.rand(k + 3) * 4f, 0xFFFFFFFF.toInt(), 0.45f, false)
        }
        ground.glow(CABIN_X - 10f, CABIN_Y + 6f, 110f, 26f, 0xFFFFB84A.toInt(), 0.5f)
        ground.rect(CABIN_X.toInt() - 40, CABIN_Y.toInt() - 40, CABIN_X.toInt() + 40, CABIN_Y.toInt(), 0xFF5A3822.toInt())
        for (row in 0 until 8) ground.rect(CABIN_X.toInt() - 40, CABIN_Y.toInt() - 40 + row * 5, CABIN_X.toInt() + 40, CABIN_Y.toInt() - 40 + row * 5 + 1, 0xFF3C2414.toInt())
        ground.rect(CABIN_X.toInt() + 24, CABIN_Y.toInt() - 40, CABIN_X.toInt() + 40, CABIN_Y.toInt(), 0xFF472C19.toInt())
        ground.poly(floatArrayOf(CABIN_X - 50f, CABIN_X, CABIN_X + 50f), floatArrayOf(CABIN_Y - 38f, CABIN_Y - 74f, CABIN_Y - 38f), 0xFF2A1A12.toInt())
        ground.poly(floatArrayOf(CABIN_X - 50f, CABIN_X, CABIN_X + 50f), floatArrayOf(CABIN_Y - 40f, CABIN_Y - 76f, CABIN_Y - 40f), 0xFFE9F0FF.toInt())
        ground.poly(floatArrayOf(CABIN_X - 48f, CABIN_X, CABIN_X + 48f), floatArrayOf(CABIN_Y - 38f, CABIN_Y - 72f, CABIN_Y - 38f), 0xFF3A2418.toInt())
        ground.rect(CABIN_X.toInt() + 20, CABIN_Y.toInt() - 80, CABIN_X.toInt() + 30, CABIN_Y.toInt() - 52, 0xFF4B4F5A.toInt())
        ground.rect(CABIN_X.toInt() + 19, CABIN_Y.toInt() - 82, CABIN_X.toInt() + 31, CABIN_Y.toInt() - 78, 0xFFEEF4FF.toInt())
        ground.rect(CABIN_X.toInt() - 32, CABIN_Y.toInt() - 28, CABIN_X.toInt() - 16, CABIN_Y.toInt() - 14, 0xFFFFC25A.toInt())
        ground.rect(CABIN_X.toInt() - 30, CABIN_Y.toInt() - 26, CABIN_X.toInt() - 18, CABIN_Y.toInt() - 16, 0xFFFFE6A0.toInt())
        ground.rect(CABIN_X.toInt() - 24, CABIN_Y.toInt() - 28, CABIN_X.toInt() - 23, CABIN_Y.toInt() - 14, 0xFF4A2E1A.toInt())
        ground.rect(CABIN_X.toInt() - 4, CABIN_Y.toInt() - 30, CABIN_X.toInt() + 12, CABIN_Y.toInt(), 0xFF2E1B10.toInt())
        ground.rect(CABIN_X.toInt() - 42, CABIN_Y.toInt() - 2, CABIN_X.toInt() + 42, CABIN_Y.toInt() + 3, 0xFFE9F0FF.toInt())
        val fg = Raster(ART_W, ART_H)
        fg.pine(30f, ART_H + 30f, 320f, 150f, 0xFF0E1D30.toInt(), 0.25f, 0xFFE4EEFF.toInt())
        fg.pine(620f, ART_H + 30f, 250f, 120f, 0xFF0E1D30.toInt(), 0.25f, 0xFFE4EEFF.toInt())
        fg.glow(320f, ART_H.toFloat() + 6f, 420f, 22f, 0xFFF4F8FF.toInt(), 0.9f, false)
        return Baked(
            listOf(ArtLayer(sky, 0.1f), ArtLayer(far, 0.3f, 1.5f), ArtLayer(mid, 0.55f, 3.5f), ArtLayer(near, 0.8f, 7f), ArtLayer(ground, 1f), ArtLayer(fg, 1.9f)),
            ArtFigure(340f, 228f, 80f, ArtPose.STAND, 150f, 5),
        )
    }

    override fun effects(fx: Fx, t: Double) {
        val n = Noise(9)
        fx.layer(0.1f)
        for (i in 0 until 40) {
            val tw = 0.5f + 0.5f * wobble(t, i, 0.8)
            val x = n.rand(i * 2 + 1) * ART_W
            val y = n.rand(i * 2 + 2) * 100f
            fx.rect(x, y, x + 1.2f, y + 1.2f, alpha(0xFFFFFFFF.toInt(), 0.2f + tw * 0.6f))
        }
        fx.layer(1f)
        val flick = 0.85f + 0.15f * wobble(t, 4, 6.0) * wobble(t, 6, 3.3)
        fx.glow(CABIN_X - 24f, CABIN_Y - 21f, 22f, 18f, 0xFFFFC25A.toInt(), 0.5f * flick)
        for (i in 0 until 7) {
            val phase = ((t * 0.12 + i * 0.143) % 1.0).toFloat()
            val x = CABIN_X + 25f + phase * 30f + sin(t * 0.7 + i).toFloat() * 4f
            val y = CABIN_Y - 84f - phase * 70f
            fx.glow(x, y, 6f + phase * 14f, 5f + phase * 12f, 0xFFB8C0D4.toInt(), (1f - phase) * 0.3f)
        }
        for (band in 0 until 3) {
            fx.layer(0.4f + band * 0.7f)
            val count = 34
            val speed = 16.0 + band * 14.0
            val size = 1.1f + band * 0.8f
            for (i in 0 until count) {
                val seed = i * 7 + band * 131
                val x = ((n.rand(seed) * ART_W + t * (6.0 + band * 4.0) + sin(t * 0.6 + i).toFloat() * 6f) % ART_W).toFloat()
                val y = ((n.rand(seed + 1) * ART_H + t * speed) % ART_H).toFloat()
                fx.rect(x, y, x + size, y + size, alpha(0xFFFFFFFF.toInt(), 0.45f + band * 0.2f))
            }
        }
    }
}

object AuroraArt : TitleArt("Aurora") {
    private const val HORIZON = 168

    override fun bake(): Baked {
        val n = Noise(37)
        val backdrop = Raster(ART_W, ART_H)
        backdrop.vgrad(0, 0, ART_W, HORIZON, 0xFF010208.toInt(), 0xFF0E2433.toInt())
        backdrop.stars(n, 120, 0xFFDDEEFF.toInt())
        val peaks = { x: Int -> 128f + n.fbm(x * 0.008f, 6f) * 46f - abs(sin(x * 0.014f)) * 18f }
        litRidge(backdrop, peaks, 0xFF0A1424.toInt(), 0xFF5C86AA.toInt(), 0xFF070E1A.toInt(), 0xAA9CC8E8.toInt(), 60f)
        backdrop.rect(0, HORIZON - 2, ART_W, HORIZON, 0xFF0A1420.toInt())
        val lake = Raster(ART_W, ART_H)
        lake.vgrad(0, HORIZON, ART_W, ART_H, 0xFF0A1A2A.toInt(), 0xFF02050A.toInt())
        for (k in 0 until ART_H - HORIZON) {
            val sy = HORIZON - 1 - k
            if (sy < 0) break
            val wave = (sin(k * 0.55f) * (1f + k * 0.04f)).toInt()
            val fade = (0.55f - k / (ART_H - HORIZON).toFloat() * 0.5f).coerceAtLeast(0.08f)
            for (x in 0 until ART_W) {
                val c = backdrop.px[sy * ART_W + (x + wave).coerceIn(0, ART_W - 1)]
                lake.blend(x, HORIZON + k, alpha(shade(c, 0.85f), fade))
            }
        }
        val shore = Raster(ART_W, ART_H)
        val shoreLine = { x: Int -> 238f + n.fbm(x * 0.03f, 1f) * 10f - (if (x in 250..420) 6f else 0f) }
        shore.ridge(shoreLine, 0xFF101A16.toInt(), 0xFF040806.toInt(), 0xFF2A4A44.toInt())
        for (k in 0 until 14) {
            val x = n.rand(k + 5) * ART_W
            val side = if (x < 250 || x > 440) 1 else 0
            if (side == 1) shore.pine(x, shoreLine(x.toInt()) + 4f, 50f + n.rand(k + 9) * 40f, 36f, 0xFF081410.toInt(), 0.25f)
        }
        shore.rect(250, 232, 420, 238, 0xFF1B2A28.toInt())
        val fg = Raster(ART_W, ART_H)
        fg.pine(28f, ART_H + 30f, 310f, 140f, 0xFF030507.toInt(), 0.1f)
        fg.pine(618f, ART_H + 30f, 260f, 120f, 0xFF030507.toInt(), 0.1f)
        return Baked(
            listOf(ArtLayer(backdrop, 0.2f), ArtLayer(lake, 0.45f), ArtLayer(shore, 1f), ArtLayer(fg, 1.9f)),
            ArtFigure(335f, 237f, 80f, ArtPose.LOOK_UP, 160f, 3),
        )
    }

    override fun effects(fx: Fx, t: Double) {
        val n = Noise(41)
        fx.layer(0.15f)
        for (i in 0 until 70) {
            val tw = 0.5f + 0.5f * wobble(t, i, 0.7 + (i % 4) * 0.3)
            val x = n.rand(i * 2 + 1) * ART_W
            val y = n.rand(i * 2 + 2) * 120f
            fx.rect(x, y, x + 1.2f, y + 1.2f, alpha(0xFFFFFFFF.toInt(), 0.2f + tw * 0.6f))
        }
        fx.layer(0.2f)
        for (curtain in 0 until 3) {
            val base = 40f + curtain * 16f
            var x = 0f
            while (x < ART_W.toFloat()) {
                val wave = sin(x * 0.018 + t * (0.35 + curtain * 0.12) + curtain * 2.1).toFloat()
                val wave2 = sin(x * 0.045 - t * 0.5 + curtain).toFloat()
                val top = base + wave * 22f + wave2 * 7f
                val height = 52f + 28f * sin(x * 0.012 + t * 0.2 + curtain).toFloat()
                val intensity = (0.55f + 0.45f * wave2) * (0.8f - curtain * 0.18f)
                val green = if (curtain == 1) 0xFF7A58FF.toInt() else 0xFF3CFFA0.toInt()
                val col = mix(green, 0xFFB46CFF.toInt(), 0.3f)
                fx.vgrad(x, top, x + 8f, top + height, alpha(col, intensity * 0.5f), alpha(col, 0f))
                x += 8f
            }
        }
        fx.layer(0.45f)
        for (i in 0 until 26) {
            val x = 60f + n.rand(i + 70) * 520f
            val y = HORIZON + 6f + n.rand(i + 71) * 80f
            val a = (0.5f + 0.5f * wobble(t, i, 1.3)).coerceAtLeast(0f) * 0.35f
            fx.rect(x, y, x + 8f + n.rand(i) * 14f, y + 0.8f, alpha(0xFFBFE8FF.toInt(), a))
        }
        val phase = ((t * 0.07) % 1.0).toFloat()
        if (phase < 0.08f) {
            fx.layer(0.15f)
            val p = phase / 0.08f
            fx.rect(380f - p * 90f, 20f + p * 50f, 380f - p * 90f + 14f, 20f + p * 50f + 1.2f, alpha(0xFFFFFFFF.toInt(), 1f - p))
        }
    }
}

object RainArt : TitleArt("Downpour") {
    private var nextFlash = 3.0
    private var flashAt = -10.0

    override fun bake(): Baked {
        val n = Noise(53)
        val sky = Raster(ART_W, ART_H)
        sky.vgrad(0, 0, ART_W, 210, 0xFF141A28.toInt(), 0xFF4D566F.toInt())
        for (y in 0 until 190) for (x in 0 until ART_W) {
            val c = n.fbm(x * 0.012f, y * 0.03f + 20f, 5)
            val a = ((c - 0.35f) * 1.4f).coerceIn(0f, 0.75f)
            sky.blend(x, y, alpha(0xFF6A748E.toInt(), a * (0.5f + y / 380f)))
        }
        val far = Raster(ART_W, ART_H)
        litRidge(far, periodic { x -> 160f + n.fbm(x * 0.01f, 2f) * 40f }, 0xFF29324A.toInt(), 0xFF4C587A.toInt(), 0xFF1B2236.toInt(), 0x445C6A8C, 50f)
        val mid = Raster(ART_W, ART_H)
        val line = periodic { x -> 190f + n.fbm(x * 0.025f, 7f) * 12f }
        mid.ridge(line, 0xFF1C2A2A.toInt(), 0xFF111A1C.toInt(), 0x00000000)
        var px = 0f
        var i = 0
        while (px < ART_W) {
            val h = 30f + n.rand(i + 6) * 36f
            mid.pineWrapped(px, line(px.toInt()) + 6f, h, h * 0.5f, 0xFF18282A.toInt(), 0.2f)
            px += 14f + n.rand(i + 66) * 18f
            i++
        }
        val near = nearHills(n, 17f, 198f, 9f, 0xFF141E20.toInt(), 0xFF0C1214.toInt())
        val ground = Raster(ART_W, ART_H)
        ground.vgrad(0, 206, ART_W, ART_H, 0xFF2C3438.toInt(), 0xFF0A0E10.toInt())
        for (k in 0 until 240) {
            val gx = (n.rand(k + 400) * ART_W).toInt()
            val gy = 208 + (n.rand(k + 900) * 60).toInt()
            ground.blend(gx, gy, alpha(0xFF566068.toInt(), 0.5f))
        }
        for (k in 0 until 7) {
            val cx = 90f + n.rand(k + 30) * 470f
            val cy = 222f + n.rand(k + 34) * 36f
            ground.glow(cx, cy, 26f + n.rand(k + 2) * 20f, 4f + n.rand(k) * 3f, 0xFF7E8CAA.toInt(), 0.7f, false)
        }
        val ax = 470f
        val brick = 0xFF4E5262.toInt()
        for (col in floatArrayOf(ax - 46f, ax + 30f)) {
            val cx = col.toInt()
            ground.rect(cx, 146, cx + 16, 214, brick)
            for (r in 0 until 14) {
                val yy = 146 + r * 5
                ground.rect(cx, yy, cx + 16, yy + 1, 0xFF383C48.toInt())
                val joint = cx + 4 + (r % 2) * 8
                ground.rect(joint, yy, joint + 1, yy + 5, 0xFF383C48.toInt())
            }
            ground.rect(cx, 146, cx + 4, 214, shade(brick, 1.18f))
        }
        val blocks = 13
        for (k in 0 until blocks) {
            if (k == 8 || (k == 7 && n.rand(77) > 0.4f)) continue
            val a0 = PI.toFloat() * (1f - k / blocks.toFloat()) - 0.012f
            val a1 = PI.toFloat() * (1f - (k + 1) / blocks.toFloat()) + 0.012f
            val r1 = 30f
            val r2 = 46f
            val xs = floatArrayOf(ax + cos(a0) * r1, ax + cos(a0) * r2, ax + cos(a1) * r2, ax + cos(a1) * r1)
            val ys = floatArrayOf(146f - sin(a0) * r1, 146f - sin(a0) * r2, 146f - sin(a1) * r2, 146f - sin(a1) * r1)
            val tone = if (k % 2 == 0) brick else shade(brick, 0.86f)
            ground.poly(xs, ys, tone)
            ground.poly(floatArrayOf(xs[1], xs[2], (xs[1] + xs[2]) / 2f), floatArrayOf(ys[1], ys[2], (ys[1] + ys[2]) / 2f + 1f), shade(tone, 1.2f))
        }
        for (k in 0 until 7) {
            val rx = ax + 36f + n.rand(k + 300) * 40f
            ground.rect(rx.toInt(), 209 + (n.rand(k + 310) * 5).toInt(), rx.toInt() + 3 + (n.rand(k + 320) * 4).toInt(), 214, if (k % 2 == 0) brick else shade(brick, 0.8f))
        }
        for (k in 0 until 26) ground.disc(ax - 44f + n.rand(k + 500) * 16f, 150f + n.rand(k + 510) * 60f, 1.6f, 0xFF3C6A3A.toInt())
        val fg = Raster(ART_W, ART_H)
        for (k in 0 until 70) {
            val gx = n.rand(k + 700) * ART_W
            val gh = 10f + n.rand(k + 800) * 24f
            fg.tri(gx - 2.5f, ART_H.toFloat(), gx + n.rand(k + 5) * 6f - 3f, ART_H - gh, gx + 2.5f, ART_H.toFloat(), 0xFF070C0C.toInt())
        }
        fg.pine(24f, ART_H + 30f, 300f, 130f, 0xFF050809.toInt(), 0.1f)
        return Baked(
            listOf(ArtLayer(sky, 0.1f), ArtLayer(far, 0.3f, 1.5f), ArtLayer(mid, 0.55f, 3.5f), ArtLayer(near, 0.8f, 7f), ArtLayer(ground, 1f), ArtLayer(fg, 1.9f)),
            ArtFigure(340f, 226f, 80f, ArtPose.STAND, 160f, 5),
        )
    }

    override fun effects(fx: Fx, t: Double) {
        val n = Noise(61)
        if (t > nextFlash) {
            flashAt = t
            nextFlash = t + 6.0 + (n.rand((t * 3).toInt()) * 7.0)
        }
        val since = t - flashAt
        if (since in 0.0..0.5) {
            val f = (1f - since.toFloat() / 0.5f)
            val pulse = if (since < 0.12 || since in 0.2..0.3) 1f else 0.4f
            fx.flash(alpha(0xFFDCE6FF.toInt(), f * pulse * 0.35f))
            fx.layer(0.1f)
            var bx = 300f + n.rand((flashAt * 7).toInt()) * 200f
            var by = 0f
            while (by < 160f) {
                val nx = bx + (n.rand((by * 3 + flashAt * 11).toInt()) - 0.5f) * 22f
                val ny = by + 14f
                fx.rect(min(bx, nx), by, max(bx, nx) + 1.6f, ny, alpha(0xFFF4F8FF.toInt(), f))
                bx = nx
                by = ny
            }
        }
        for (band in 0 until 3) {
            fx.layer(0.3f + band * 0.7f)
            val count = 46
            val speed = 150.0 + band * 90.0
            val len = 6f + band * 5f
            for (i in 0 until count) {
                val seed = i * 5 + band * 97
                val x = ((n.rand(seed) * (ART_W + 80) - 40 + t * (30.0 + band * 20.0)) % (ART_W + 80)).toFloat() - 40f
                val y = ((n.rand(seed + 1) * ART_H + t * speed) % ART_H).toFloat()
                fx.rect(x, y, x + 1f, y + len, alpha(0xFFB4C4E0.toInt(), 0.18f + band * 0.14f))
            }
        }
        fx.layer(1f)
        for (i in 0 until 7) {
            val cx = 90f + n.rand(i + 30) * 470f
            val cy = 222f + n.rand(i + 34) * 36f
            val phase = ((t * 0.9 + i * 0.37) % 1.0).toFloat()
            val rx = 3f + phase * 12f
            val ry = rx * 0.22f
            fx.rect(cx - rx, cy - ry, cx + rx, cy - ry + 0.8f, alpha(0xFFC4D2EC.toInt(), (1f - phase) * 0.6f))
            fx.rect(cx - rx, cy + ry, cx + rx, cy + ry + 0.8f, alpha(0xFFC4D2EC.toInt(), (1f - phase) * 0.6f))
        }
    }
}

object DesertArt : TitleArt("Dusk Dunes") {
    private const val SUN_X = 448f
    private const val SUN_Y = 150f

    override fun bake(): Baked {
        val n = Noise(71)
        val sky = Raster(ART_W, ART_H)
        sky.vgrad(0, 0, ART_W, 100, 0xFF1E1538.toInt(), 0xFF7A3358.toInt())
        sky.vgrad(0, 100, ART_W, 176, 0xFF7A3358.toInt(), 0xFFFFA050.toInt())
        sky.stars(n, 40, 0xFFFFE8F0.toInt())
        sky.glow(SUN_X, SUN_Y, 190f, 120f, 0xFFFFB060.toInt(), 0.6f, false)
        sky.glow(SUN_X, SUN_Y, 80f, 60f, 0xFFFFD890.toInt(), 0.8f, false)
        sky.disc(SUN_X, SUN_Y, 30f, 0xFFFFE2A0.toInt())
        sky.disc(SUN_X, SUN_Y, 24f, 0xFFFFF0C8.toInt())
        for (k in 0 until 5) {
            val y = 40 + k * 22
            for (x in 0 until ART_W) {
                val c = n.fbm(x * 0.012f + k * 4f, k * 3f, 3)
                if (c > 0.52f) sky.blend(x, y + (c * 6).toInt(), alpha(0xFFFFB478.toInt(), (c - 0.52f) * 1.6f))
            }
        }
        val far = Raster(ART_W, ART_H)
        litRidge(far, periodic { x -> 164f + n.fbm(x * 0.006f, 1f) * 24f }, 0xFF6E3050.toInt(), 0xFFFF9A5A.toInt(), 0xFF4A2040.toInt(), 0xAAFFC080.toInt(), 40f)
        val mid = Raster(ART_W, ART_H)
        litRidge(mid, periodic { x -> 184f + n.fbm(x * 0.008f, 5f) * 30f - sin(x * 0.02f) * 6f }, 0xFF542442.toInt(), 0xFFE5784E.toInt(), 0xFF361A38.toInt(), 0xAAFFB070.toInt(), 50f)
        val near = nearHills(n, 17f, 200f, 12f, 0xFF3E1E3C.toInt(), 0xFF2A1430.toInt())
        val ground = Raster(ART_W, ART_H)
        val gl = { x: Int -> 214f + n.fbm(x * 0.01f, 8f) * 16f }
        litRidge(ground, gl, 0xFF3C1E34.toInt(), 0xFFC8603E.toInt(), 0xFF1E0F20.toInt(), 0xCCFFC080.toInt(), 56f)
        for (k in 0 until 40) {
            val y = 222 + k * 1
            for (x in 0 until ART_W step 2) {
                val w = sin(x * 0.06f + k * 0.9f + n.fbm(x * 0.02f, k * 0.1f) * 6f)
                if (w > 0.9f) ground.blend(x, y, alpha(0xFFFFB070.toInt(), 0.18f))
            }
        }
        val cx = 560f
        ground.rect(cx.toInt() - 3, 160, cx.toInt() + 3, 226, 0xFF1A2418.toInt())
        ground.rect(cx.toInt() - 14, 176, cx.toInt() - 9, 194, 0xFF1A2418.toInt())
        ground.rect(cx.toInt() - 14, 192, cx.toInt() - 3, 197, 0xFF1A2418.toInt())
        ground.rect(cx.toInt() + 9, 168, cx.toInt() + 14, 184, 0xFF1A2418.toInt())
        ground.rect(cx.toInt() + 3, 182, cx.toInt() + 14, 187, 0xFF1A2418.toInt())
        ground.rect(cx.toInt() + 1, 160, cx.toInt() + 3, 226, 0xFF3A5236.toInt())
        val fg = Raster(ART_W, ART_H)
        for (k in 0 until 9) {
            val rx = n.rand(k + 8) * ART_W
            val rr = 8f + n.rand(k + 3) * 18f
            fg.glow(rx, ART_H + 4f, rr * 2.2f, rr, 0xFF140A16.toInt(), 1f, false)
        }
        fg.rect(0, ART_H - 6, ART_W, ART_H, 0xFF120812.toInt())
        return Baked(
            listOf(ArtLayer(sky, 0.1f), ArtLayer(far, 0.28f, 1.5f), ArtLayer(mid, 0.5f, 3.5f), ArtLayer(near, 0.8f, 7f), ArtLayer(ground, 1f), ArtLayer(fg, 1.9f)),
            ArtFigure(340f, 234f, 80f, ArtPose.STAND, 128f, 5),
        )
    }

    override fun effects(fx: Fx, t: Double) {
        val n = Noise(77)
        fx.layer(0.1f)
        val pulse = 0.92f + 0.08f * wobble(t, 1, 0.8)
        fx.glow(SUN_X, SUN_Y, 120f * pulse, 80f * pulse, 0xFFFFC070.toInt(), 0.18f)
        for (i in 0 until 4) {
            val x = ((t * (14.0 + i * 5) + i * 190) % (ART_W + 80)).toFloat() - 40f
            val y = 60f + i * 18f + sin(t * 0.9 + i).toFloat() * 4f
            val flap = sin(t * 7 + i * 2).toFloat() * 3f
            fx.rect(x - 4f, y - flap * 0.4f, x, y + 0.9f, 0xFF20102A.toInt())
            fx.rect(x, y - flap * 0.4f, x + 4f, y + 0.9f, 0xFF20102A.toInt())
            fx.rect(x - 0.5f, y, x + 0.8f, y + 1.5f, 0xFF20102A.toInt())
        }
        fx.layer(0.5f)
        for (i in 0 until 8) {
            val y = 176f + i * 4f
            val off = sin(t * 1.3 + i * 0.8).toFloat() * 3f
            fx.rect(40f + off, y, 600f + off, y + 1f, alpha(0xFFFFC890.toInt(), 0.05f))
        }
        for (band in 0 until 2) {
            fx.layer(0.8f + band * 0.8f)
            for (i in 0 until 26) {
                val seed = i * 3 + band * 90
                val x = ((n.rand(seed) * ART_W + t * (24.0 + band * 20)) % ART_W).toFloat()
                val y = 210f + n.rand(seed + 1) * 56f + sin(t + i).toFloat() * 1.5f
                fx.rect(x, y, x + 6f + band * 6f, y + 0.8f, alpha(0xFFFFC890.toInt(), 0.16f + band * 0.1f))
            }
        }
    }
}

val TITLE_ARTS: List<TitleArt> = listOf(CampfireArt, SnowCabinArt, AuroraArt, RainArt, DesertArt)


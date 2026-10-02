package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object ImpactSparks {
    private class Spark(val origin: Vec3, val velocity: Vec3, val rgb: Int, val size: Int, val startNanos: Long, val life: Float) {
        val screenX = FloatArray(TRAIL_POINTS)
        val screenY = FloatArray(TRAIL_POINTS)
        val visible = BooleanArray(TRAIL_POINTS)
        var age = 0f
    }

    private class Arc(val points: Array<Vec3>, val rgb: Int, val startNanos: Long) {
        val screenX = FloatArray(points.size)
        val screenY = FloatArray(points.size)
        val visible = BooleanArray(points.size)
        var age = 0f
    }

    private val active = ArrayList<Spark>()
    private val arcs = ArrayList<Arc>()
    private var arcFlip = false
    private val clip = Vector4f()

    fun spawn(at: Vec3, toward: Vec3, strength: Float, rgb: Int) {
        val random = ThreadLocalRandom.current()
        val count = (COUNT_BASE + COUNT_PER_STRENGTH * strength).toInt().coerceIn(COUNT_BASE, COUNT_MAX)
        val speedScale = (SPEED_BASE + SPEED_PER_STRENGTH * strength).coerceAtMost(SPEED_MAX)
        val now = System.nanoTime()
        for (i in 0 until count) {
            val yaw = random.nextDouble() * TWO_PI
            val pitch = random.nextDouble(-0.25, 1.0)
            val flat = cos(pitch)
            val speed = speedScale * random.nextDouble(0.5, 1.0)
            val vx = (cos(yaw) * flat + toward.x * BIAS) * speed
            val vy = (sin(pitch) + BIAS_LIFT) * speed
            val vz = (sin(yaw) * flat + toward.z * BIAS) * speed
            val lift = if (i % 3 == 0) WHITE else rgb
            if (active.size >= MAX_ACTIVE) active.removeFirst()
            active.add(Spark(at, Vec3(vx, vy, vz), lift, if (i % 4 == 0) 2 else 1, now, random.nextFloat() * (LIFE_MAX - LIFE_MIN) + LIFE_MIN))
        }
    }

    fun spawnArc(at: Vec3, from: Vec3, strength: Float, rgb: Int) {
        val random = ThreadLocalRandom.current()
        val d = at.subtract(from).normalize()
        var right = d.cross(Vec3(0.0, 1.0, 0.0))
        right = if (right.lengthSqr() < 1.0e-6) Vec3(1.0, 0.0, 0.0) else right.normalize()
        val up = right.cross(d)
        arcFlip = !arcFlip
        val tilt = (if (arcFlip) ARC_TILT else -ARC_TILT) + random.nextDouble(-0.25, 0.25)
        val u = right.scale(cos(tilt)).add(up.scale(sin(tilt)))
        val w = right.scale(-sin(tilt)).add(up.scale(cos(tilt)))
        val radius = (ARC_RADIUS * (1.0 + ARC_RADIUS_PER_STRENGTH * strength)).coerceAtMost(ARC_RADIUS_MAX)
        val points = Array(ARC_POINTS) { i ->
            val s = i.toDouble() / (ARC_POINTS - 1) * 2.0 - 1.0
            at.add(u.scale(s * radius)).add(w.scale((1.0 - s * s) * radius * ARC_BULGE))
        }
        if (arcs.size >= MAX_ARCS) arcs.removeFirst()
        arcs.add(Arc(points, rgb, System.nanoTime()))
    }

    fun clear() {
        active.clear()
        arcs.clear()
    }

    fun capture(ctx: LevelRenderContext) {
        if (active.isEmpty() && arcs.isEmpty()) return
        val now = System.nanoTime()
        arcs.removeAll { (now - it.startNanos) / 1.0e9f > ARC_LIFE }
        active.removeAll { (now - it.startNanos) / 1.0e9f > it.life }
        val cam = ctx.levelState().cameraRenderState
        val view = cam.viewRotationMatrix
        val viewProjection = Matrix4f(cam.projectionMatrix).mul(view)
        for (arc in arcs) {
            arc.age = (now - arc.startNanos) / 1.0e9f
            for (i in arc.points.indices) {
                val p = arc.points[i]
                val ndc = WorldProjection.project(cam.pos, viewProjection, view, clip, p.x, p.y, p.z)
                arc.screenX[i] = ndc.x
                arc.screenY[i] = ndc.y
                arc.visible[i] = ndc.onScreen
            }
        }
        for (spark in active) {
            spark.age = (now - spark.startNanos) / 1.0e9f
            for (p in 0 until TRAIL_POINTS) {
                val t = (spark.age - p * TRAIL_STEP).coerceAtLeast(0f).toDouble()
                val x = spark.origin.x + spark.velocity.x * t
                val y = spark.origin.y + spark.velocity.y * t - GRAVITY * t * t * 0.5
                val z = spark.origin.z + spark.velocity.z * t
                val ndc = WorldProjection.project(cam.pos, viewProjection, view, clip, x, y, z)
                spark.screenX[p] = ndc.x
                spark.screenY[p] = ndc.y
                spark.visible[p] = ndc.onScreen
            }
        }
    }

    fun draw(graphics: GuiGraphicsExtractor) {
        if ((active.isEmpty() && arcs.isEmpty()) || !WynnOverhaulGate.inGame) return
        val halfW = graphics.guiWidth() * 0.5f
        val halfH = graphics.guiHeight() * 0.5f
        for (arc in arcs) {
            val sweep = (arc.age / ARC_SWEEP).coerceIn(0f, 1f) * (ARC_POINTS - 1)
            val fade = 1f - max(0f, arc.age - ARC_SWEEP) / (ARC_LIFE - ARC_SWEEP)
            for (i in arc.points.indices) {
                if (!arc.visible[i] || i > sweep) continue
                val trail = (1f - (sweep - i) / ARC_TAIL).coerceIn(0f, 1f)
                val edge = 1f - abs(i - (ARC_POINTS - 1) / 2f) / ((ARC_POINTS - 1) / 2f) * ARC_EDGE_FADE
                val x = (halfW + arc.screenX[i] * halfW).toInt()
                val y = (halfH - arc.screenY[i] * halfH).toInt()
                graphics.fill(x, y, x + 2, y + 2, HudStyle.alpha(0xFF000000.toInt() or arc.rgb, ARC_ALPHA * fade * trail * edge))
            }
        }
        for (spark in active) {
            val fade = 1f - (spark.age / spark.life).coerceIn(0f, 1f)
            for (p in TRAIL_POINTS - 1 downTo 0) {
                if (!spark.visible[p]) continue
                val alpha = ALPHA * fade * (1f - p.toFloat() / TRAIL_POINTS)
                val x = (halfW + spark.screenX[p] * halfW).toInt()
                val y = (halfH - spark.screenY[p] * halfH).toInt()
                val size = if (p == 0) spark.size else 1
                graphics.fill(x, y, x + size, y + size, HudStyle.alpha(0xFF000000.toInt() or spark.rgb, alpha))
            }
        }
    }

    private const val ARC_POINTS = 18
    private const val ARC_TAIL = 9f
    private const val ARC_SWEEP = 0.1f
    private const val ARC_LIFE = 0.24f
    private const val ARC_ALPHA = 0.8f
    private const val ARC_EDGE_FADE = 0.6f
    private const val ARC_TILT = 0.7
    private const val ARC_RADIUS = 0.8
    private const val ARC_RADIUS_PER_STRENGTH = 0.25
    private const val ARC_RADIUS_MAX = 1.6
    private const val ARC_BULGE = 0.35
    private const val MAX_ARCS = 8
    private const val TRAIL_POINTS = 4
    private const val TRAIL_STEP = 0.025f
    private const val MAX_ACTIVE = 160
    private const val COUNT_BASE = 10
    private const val COUNT_PER_STRENGTH = 6f
    private const val COUNT_MAX = 32
    private const val SPEED_BASE = 3.5f
    private const val SPEED_PER_STRENGTH = 1.5f
    private const val SPEED_MAX = 8f
    private const val BIAS = 0.6
    private const val BIAS_LIFT = 0.15
    private const val GRAVITY = 9.0
    private const val LIFE_MIN = 0.3f
    private const val LIFE_MAX = 0.6f
    private const val ALPHA = 0.75f
    private const val WHITE = 0xFFFFFF
    private const val TWO_PI = 6.2831855
}

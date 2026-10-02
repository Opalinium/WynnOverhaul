package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f
import java.util.concurrent.ThreadLocalRandom

object DamageNumbers {
    enum class Style(val label: String) {
        POP("Pop"),
        FLOAT("Float"),
        SLAM("Slam"),
        FOUNTAIN("Fountain");

        companion object {
            fun of(id: String): Style = entries.firstOrNull { it.name == id } ?: POP
        }
    }

    private class Segment(val text: String, val rgb: Int)

    private class Popup(val at: Vec3, val segments: List<Segment>, val weight: Float, val startNanos: Long, val drift: Float, val style: Style) {
        var ndcX = 0f
        var ndcY = 0f
        var onScreen = false
    }

    private val active = ArrayList<Popup>()
    private val clip = Vector4f()

    fun spawn(at: Vec3, delta: Map<WynnDamageTracker.Element, Long>, weight: Float) {
        if (!WynnOverhaulConfig.current.hitFeedbackNumbers || !WynnOverhaulGate.inGame) return
        val compact = WynnOverhaulConfig.current.damageNumberCompact
        val segments = delta.entries.sortedByDescending { it.value }.map { Segment(format(it.value, compact), it.key.rgb) }
        if (active.size >= MAX_ACTIVE) active.removeFirst()
        active.add(Popup(at, segments, weight, System.nanoTime(), ThreadLocalRandom.current().nextFloat() * 2f - 1f, Style.of(WynnOverhaulConfig.current.damageNumberStyle)))
    }

    fun clear() {
        active.clear()
    }

    fun capture(ctx: LevelRenderContext) {
        if (active.isEmpty()) return
        val now = System.nanoTime()
        active.removeAll { now - it.startNanos > LIFE_NANOS }
        val cam = ctx.levelState().cameraRenderState
        val view = cam.viewRotationMatrix
        val viewProjection = Matrix4f(cam.projectionMatrix).mul(view)
        for (number in active) {
            val ndc = WorldProjection.project(cam.pos, viewProjection, view, clip, number.at.x, number.at.y, number.at.z)
            number.ndcX = ndc.x
            number.ndcY = ndc.y
            number.onScreen = ndc.onScreen
        }
    }

    fun draw(graphics: GuiGraphicsExtractor) {
        if (active.isEmpty() || !WynnOverhaulGate.inGame) return
        val font = Minecraft.getInstance().font
        val halfW = graphics.guiWidth() * 0.5f
        val halfH = graphics.guiHeight() * 0.5f
        val now = System.nanoTime()
        val pose = graphics.pose()
        for (number in active) {
            if (!number.onScreen) continue
            val t = ((now - number.startNanos) / LIFE_NANOS.toFloat()).coerceIn(0f, 1f)
            val grow = WynnOverhaulConfig.current.damageNumberScale / 100f * (BASE_SCALE + SIZE_PER_WEIGHT * (number.weight - 1f).coerceIn(0f, MAX_EXTRA_WEIGHT))
            val fade = if (t < FADE_START) 1f else 1f - (t - FADE_START) / (1f - FADE_START)
            val rise: Float
            val sideways: Float
            val size: Float
            var shake = 0f
            when (number.style) {
                Style.POP -> {
                    size = grow * (1f + POP_OVERSHOOT * (1f - HudStyle.smoothstep((t / POP_SHARE).coerceIn(0f, 1f))))
                    rise = RISE_PIXELS * (1f - (1f - t) * (1f - t))
                    sideways = number.drift * DRIFT_PIXELS * t
                }
                Style.FLOAT -> {
                    size = grow
                    rise = RISE_PIXELS * 1.3f * t
                    sideways = 0f
                }
                Style.SLAM -> {
                    val settle = HudStyle.smoothstep((t / SLAM_SHARE).coerceIn(0f, 1f))
                    size = grow * (1f + SLAM_OVERSHOOT * (1f - settle))
                    rise = RISE_PIXELS * 0.4f * t
                    sideways = 0f
                    shake = (1f - settle) * SLAM_SHAKE * number.drift * kotlin.math.sin(t * SLAM_SHAKE_RATE)
                }
                Style.FOUNTAIN -> {
                    size = grow
                    val side = if (number.drift < 0f) -1f else 1f
                    sideways = side * (FOUNTAIN_SIDE + FOUNTAIN_SPREAD * kotlin.math.abs(number.drift)) * t
                    rise = FOUNTAIN_LAUNCH * t - FOUNTAIN_GRAVITY * t * t
                }
            }
            val width = number.segments.sumOf { font.width(it.text) } + SEGMENT_GAP * (number.segments.size - 1)
            pose.pushMatrix()
            pose.translate(halfW + number.ndcX * halfW + sideways + shake, halfH - number.ndcY * halfH - rise)
            pose.scale(size)
            var x = -width / 2
            for (segment in number.segments) {
                graphics.text(font, segment.text, x, -font.lineHeight / 2, HudStyle.alpha(0xFF000000.toInt() or segment.rgb, fade), true)
                x += font.width(segment.text) + SEGMENT_GAP
            }
            pose.popMatrix()
        }
    }

    private fun format(value: Long, compact: Boolean): String = when {
        !compact || value < 1_000L -> value.toString()
        value < 1_000_000L -> trim(value / 1_000.0, "k")
        value < 1_000_000_000L -> trim(value / 1_000_000.0, "m")
        else -> trim(value / 1_000_000_000.0, "b")
    }

    private fun trim(value: Double, suffix: String): String {
        val text = if (value < 100.0) "%.1f".format(value) else "%.0f".format(value)
        return text.removeSuffix(".0") + suffix
    }

    private const val LIFE_NANOS = 900_000_000L
    private const val MAX_ACTIVE = 40
    private const val POP_SHARE = 0.18f
    private const val POP_OVERSHOOT = 0.55f
    private const val BASE_SCALE = 1.0f
    private const val SIZE_PER_WEIGHT = 0.45f
    private const val MAX_EXTRA_WEIGHT = 1.5f
    private const val RISE_PIXELS = 30f
    private const val DRIFT_PIXELS = 14f
    private const val FADE_START = 0.65f
    private const val SEGMENT_GAP = 4
    private const val SLAM_SHARE = 0.15f
    private const val SLAM_OVERSHOOT = 0.9f
    private const val SLAM_SHAKE = 5f
    private const val SLAM_SHAKE_RATE = 90f
    private const val FOUNTAIN_SIDE = 18f
    private const val FOUNTAIN_SPREAD = 16f
    private const val FOUNTAIN_LAUNCH = 60f
    private const val FOUNTAIN_GRAVITY = 55f
}

package opal.dev.wynnoverhaul.client

import opal.dev.wynnoverhaul.client.WeaponAnimations.Cue
import opal.dev.wynnoverhaul.client.WeaponAnimations.Curve
import opal.dev.wynnoverhaul.client.WeaponAnimations.Pose
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.sin

internal class CastStyle(
    val seconds: Float,
    val arc: Float,
    val body: Float,
    val reach: Float,
    val raise: Float,
    val turn: Float,
    val mirror: Boolean,
    val grip: Float,
    val hitShift: Float,
    val blend: Float,
    val twirlChannel: Int,
    val twirlAmplitude: Float,
    val twirlCycles: Float,
    val sounds: List<Cue>,
    val extraSounds: List<Cue>,
)

internal object SpellCasts {
    private const val PITCH = 0
    private const val OFF_PITCH = 3
    private const val TURN = 6
    private const val REACH = 7
    private const val GRIP = 11
    private const val TWIRL_STEPS = 18
    private const val ARC = 0
    private const val BODY = 1
    private val CHANNEL_KIND = intArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 2, 1, 1, 1, 1, 1, 1)

    private var builtFor: AnimationTables? = null
    private val poses = HashMap<String, Pose>()
    private val cues = IdentityHashMap<Pose, List<Cue>>()

    fun choose(styleKey: String?, spell: String): Pose? {
        val tables = AnimationLibrary.tables()
        if (tables !== builtFor) {
            poses.clear()
            cues.clear()
            builtFor = tables
        }
        val own = styleKey?.let { tables.movesetSpells[it]?.get(spell) }?.let { tables.poses[it] }
        if (own != null) return own
        val key = tables.spellKeys[spell] ?: "SPELL:CAST"
        val base = tables.poses[key] ?: return null
        val style = styleKey?.let { tables.castStyles[it] } ?: return base
        return poses.getOrPut("$styleKey|$key") { build(tables, base, style) }
    }

    fun cuesOf(pose: Pose): List<Cue>? = cues[pose]

    private fun build(tables: AnimationTables, base: Pose, style: CastStyle): Pose {
        val source = if (style.mirror) base.mirrored().also { it.key = base.key } else base
        val out = Pose()
        for (i in 0 until WeaponAnimations.CHANNELS) out.curves[i] = shape(i, source.curves[i], style)
        out.key = base.key
        out.seconds = base.seconds * style.seconds
        out.hit = (base.hit + style.hitShift).coerceIn(MIN_HIT, MAX_HIT)
        out.recover = if (base.recover > out.hit && base.recover < 1f) base.recover else -1f
        out.blend = base.blend * style.blend
        out.holdEnd = base.holdEnd
        out.spin = base.spin
        out.gripOffset = base.gripOffset
        val sounds = style.sounds.ifEmpty { tables.sfx[base.key].orEmpty() } + style.extraSounds
        cues[out] = sounds
        return out
    }

    private fun shape(index: Int, source: Curve, style: CastStyle): Curve {
        var curve = source
        val scale = when (CHANNEL_KIND[index]) {
            ARC -> style.arc
            BODY -> style.body
            else -> 1f
        } * if (index == REACH) style.reach else 1f
        if (scale != 1f) curve = curve.scaled(scale)
        if ((index == PITCH || index == OFF_PITCH) && style.raise != 0f) curve = offset(curve) { _, v -> v - style.raise }
        if (index == TURN && style.turn != 0f) curve = offset(curve) { t, v -> v + style.turn * sin(PI * t).toFloat() }
        if (index == GRIP && style.grip >= 0f) curve = grip(curve, style.grip)
        if (index == style.twirlChannel && style.twirlAmplitude != 0f) curve = twirl(curve, style.twirlAmplitude, style.twirlCycles)
        return curve
    }

    private fun offset(curve: Curve, change: (Float, Float) -> Float): Curve =
        Curve(curve.times, FloatArray(curve.values.size) { i -> curve.values[i].let { if (it.isNaN()) it else change(curve.times[i], it) } })

    private fun grip(curve: Curve, value: Float): Curve {
        if (curve.values.all { it.isNaN() }) {
            return Curve(floatArrayOf(0f, 0.12f, 0.65f, 1f), floatArrayOf(Float.NaN, value, value, Float.NaN))
        }
        return offset(curve) { _, _ -> value }
    }

    private fun twirl(curve: Curve, amplitude: Float, cycles: Float): Curve {
        val times = FloatArray(TWIRL_STEPS + 1) { it.toFloat() / TWIRL_STEPS }
        val values = FloatArray(times.size) { i ->
            if (i == 0 || i == times.lastIndex) {
                Float.NaN
            } else {
                val t = times[i]
                curve.at(t, 0f) + amplitude * sin(2.0 * PI * cycles * t).toFloat() * sin(PI * t).toFloat()
            }
        }
        return Curve(times, values)
    }

    private const val MIN_HIT = 0.1f
    private const val MAX_HIT = 0.9f
}

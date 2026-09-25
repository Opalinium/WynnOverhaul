package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.pow

object WeaponTrail {
    const val GHOST_MAX_ALPHA = 250

    private const val LIFE_NANOS = 260_000_000L
    private const val GHOST_COUNT = 6
    private const val MAX_SAMPLES = 12
    private const val MIN_INTERVAL_NANOS = 45_000_000L
    private const val BASE_ALPHA = 0.42f
    const val TINT = 0xE4EAF2
    private const val MIN_MOVE = 0.08
    private const val FULL_BRIGHT = 0xF000F0

    private class Sample(
        val first: Boolean,
        val nanos: Long,
        val camX: Double,
        val camY: Double,
        val camZ: Double,
        val matrix: Matrix4f,
        val ax: Double,
        val ay: Double,
        val az: Double,
        val bx: Double,
        val by: Double,
        val bz: Double,
        val strength: Float,
        val ox: Float,
        val oy: Float,
        val oz: Float,
    )

    private val samples = ArrayDeque<Sample>()
    private var lastNanos = 0L
    private var replaying = false

    @JvmField
    var handOwner = -1

    private val handRoot = Matrix4f()
    private val handRootInverse = Matrix4f()
    private val local = Matrix4f()
    private val rel = Matrix4f()
    private val ghostPose = Matrix4f()
    private val probe = Vector3f()
    private val origin = Vector3f()

    @JvmStatic
    fun captureOrigin(stack: PoseStack) {
        origin.set(0f, 0f, 0f).mulPosition(stack.last().pose())
    }

    fun clear() {
        samples.clear()
    }

    @JvmStatic
    fun captureRoot(stack: PoseStack) {
        handRoot.set(stack.last().pose())
        handRootInverse.set(handRoot).invert()
    }

    @JvmStatic
    fun onSubmit(state: ItemStackRenderState, context: ItemDisplayContext, stack: PoseStack, collector: SubmitNodeCollector, overlay: Int) {
        if (replaying) return
        val first = context.firstPerson()
        val hand = context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        if (!first && !hand) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (first) {
            if (!client.options.cameraType.isFirstPerson) return
        } else if (handOwner != player.id) {
            return
        }
        if (context.leftHand() == WeaponAnimations.trailRight()) return
        val config = WynnOverhaulConfig.current
        if (!config.weaponAnimationTrail) {
            samples.clear()
            return
        }
        val camera = client.gameRenderer.mainCamera()
        val camPos = camera.position()

        val now = System.nanoTime()
        while (samples.isNotEmpty() && (now - samples.first().nanos > LIFE_NANOS || samples.first().first != first)) samples.removeFirst()

        val pose = stack.last().pose()
        val current = if (first) {
            local.set(handRootInverse).mul(pose)
            build(true, now, 0.0, 0.0, 0.0, local, WeaponAnimations.trailStrength())
        } else {
            rel.set(pose)
            build(false, now, camPos.x, camPos.y, camPos.z, rel, WeaponAnimations.trailStrength())
        }

        if (samples.isNotEmpty()) {
            replay(state, stack, collector, overlay, first, current, now, camPos.x, camPos.y, camPos.z, config.weaponAnimationTrailIntensity.toFloat())
        }

        if (WeaponAnimations.trailActive() && now - lastNanos >= MIN_INTERVAL_NANOS) {
            lastNanos = now
            samples.addLast(current)
            while (samples.size > MAX_SAMPLES) samples.removeFirst()
        }
    }

    private fun build(first: Boolean, now: Long, cx: Double, cy: Double, cz: Double, matrix: Matrix4f, strength: Float): Sample {
        probe.set(1f, 0f, 0f).mulPosition(matrix)
        val ax = cx + probe.x
        val ay = cy + probe.y
        val az = cz + probe.z
        probe.set(0f, 1f, 0f).mulPosition(matrix)
        return Sample(first, now, cx, cy, cz, Matrix4f(matrix), ax, ay, az, cx + probe.x, cy + probe.y, cz + probe.z, strength, origin.x, origin.y, origin.z)
    }

    private fun distance(a: Sample, b: Sample): Double =
        abs(a.ax - b.ax) + abs(a.ay - b.ay) + abs(a.az - b.az) + abs(a.bx - b.bx) + abs(a.by - b.by) + abs(a.bz - b.bz)

    private fun replay(
        state: ItemStackRenderState,
        stack: PoseStack,
        collector: SubmitNodeCollector,
        overlay: Int,
        first: Boolean,
        current: Sample,
        now: Long,
        camX: Double,
        camY: Double,
        camZ: Double,
        intensity: Float,
    ) {
        var reference = current
        var drawn = 0
        var cursor = samples.size - 1
        while (drawn < GHOST_COUNT && cursor >= 0) {
            val s = samples[cursor--]
            if (distance(s, reference) >= MIN_MOVE) {
                val age = ((now - s.nanos).toFloat() / LIFE_NANOS).coerceIn(0f, 1f)
                val alpha = ((1f - age).pow(1.5f) * BASE_ALPHA * s.strength * intensity).coerceIn(0f, 1f)
                val a = (alpha * 255f).toInt().coerceAtMost(GHOST_MAX_ALPHA)
                if (a >= 5) {
                    if (first) {
                        ghostPose.set(handRoot).mul(s.matrix)
                    } else {
                        ghostPose.identity().translation(origin.x - s.ox, origin.y - s.oy, origin.z - s.oz).mul(s.matrix)
                    }
                    stack.pushPose()
                    stack.setIdentity()
                    stack.mulPose(ghostPose)
                    replaying = true
                    try {
                        state.submit(stack, collector, FULL_BRIGHT or (a shl 24), overlay, 0)
                    } finally {
                        replaying = false
                        stack.popPose()
                    }
                    reference = s
                    drawn++
                }
            }
        }
    }
}

package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

object WynnHitFeedback {
    private var lastHitNanos = 0L
    private var lastSpellNanos = 0L
    private var averageHit = 0.0

    fun observe(total: Long): Float {
        val reference = if (averageHit <= 0.0) total.toDouble() else averageHit
        averageHit = if (averageHit <= 0.0) total.toDouble() else averageHit * AVERAGE_KEEP + total * (1.0 - AVERAGE_KEEP)
        return (total / reference).coerceIn(MIN_WEIGHT, MAX_WEIGHT).toFloat()
    }

    fun noteSpellCast() {
        lastSpellNanos = System.nanoTime()
    }

    fun onHit(label: Vec3, delta: Map<WynnDamageTracker.Element, Long>, weight: Float) {
        val config = WynnOverhaulConfig.current
        if (!config.hitFeedbackEnabled || !WynnOverhaulGate.inGame) return
        if (delta.keys.all { it == WynnDamageTracker.Element.POISON }) return
        val now = System.nanoTime()
        if (now - lastHitNanos < MIN_GAP_NANOS) return
        lastHitNanos = now
        val strength = config.hitFeedbackIntensity.coerceIn(0, 200) / 100f * weight
        val rgb = (delta.maxByOrNull { it.value }?.key ?: WynnDamageTracker.Element.NEUTRAL).rgb
        val target = impactTarget(label)
        val point = target?.let { Vec3(it.x, it.y + it.bbHeight * IMPACT_HEIGHT, it.z) } ?: label
        val spell = now - lastSpellNanos < SPELL_WINDOW_NANOS
        if (config.hitFeedbackStop) WeaponAnimations.holdAtImpact((STOP_NANOS * strength).toLong().coerceAtMost(MAX_STOP_NANOS), strength)
        if (config.hitFeedbackSound) WeaponAnimations.playImpact(point, weight, spell)
        if (config.hitFeedbackSparks) {
            ImpactSparks.spawn(point, sparkDirection(point), strength, rgb)
            if (config.hitFeedbackArcs) Minecraft.getInstance().player?.let { ImpactSparks.spawnArc(point, it.eyePosition, strength, rgb) }
            if (spell) {
                lastSpellNanos = 0L
                spawnShockwave(Vec3(point.x, target?.y ?: point.y, point.z), strength, rgb)
            }
        }
    }

    fun clear() {
        lastHitNanos = 0L
        lastSpellNanos = 0L
        averageHit = 0.0
    }

    private fun impactTarget(label: Vec3): Entity? {
        val client = Minecraft.getInstance()
        val level = client.level ?: return null
        val player = client.player ?: return null
        val box = AABB(label, label).inflate(TARGET_SEARCH)
        return level.getEntities(player, box) { it is LivingEntity && it !is Player }
            .minByOrNull { it.position().distanceToSqr(label) }
    }

    fun stepDust(player: LocalPlayer) {
        val config = WynnOverhaulConfig.current
        if (!config.hitFeedbackEnabled || !config.hitFeedbackSparks || !WynnOverhaulGate.inGame) return
        val level = player.level()
        val state = level.getBlockState(player.blockPosition().below())
        if (state.isAir) return
        val random = ThreadLocalRandom.current()
        val back = player.lookAngle.multiply(1.0, 0.0, 1.0).normalize().scale(-STEP_DUST_SPEED)
        val type = BlockParticleOption(ParticleTypes.BLOCK, state)
        for (i in 0 until STEP_DUST_COUNT) {
            level.addParticle(type, player.x + random.nextDouble(-0.2, 0.2), player.y + 0.05, player.z + random.nextDouble(-0.2, 0.2), back.x + random.nextDouble(-0.05, 0.05), 0.06, back.z + random.nextDouble(-0.05, 0.05))
        }
    }

    private fun spawnCracks(feet: Vec3, strength: Float) {
        val level = Minecraft.getInstance().level ?: return
        val state = level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.2, feet.z))
        if (state.isAir) return
        val type = BlockParticleOption(ParticleTypes.BLOCK, state)
        val random = ThreadLocalRandom.current()
        val lines = (CRACK_LINES_BASE + strength).toInt().coerceIn(CRACK_LINES_BASE, CRACK_LINES_MAX)
        val offset = random.nextDouble() * TWO_PI
        for (line in 0 until lines) {
            val angle = offset + line * TWO_PI / lines + random.nextDouble(-0.2, 0.2)
            val dx = cos(angle)
            val dz = sin(angle)
            for (step in 1..CRACK_STEPS) {
                val d = step * CRACK_SPACING
                level.addParticle(type, feet.x + dx * d, feet.y + 0.05, feet.z + dz * d, dx * 0.03, 0.04, dz * 0.03)
            }
        }
    }

    private fun sparkDirection(at: Vec3): Vec3 {
        val player = Minecraft.getInstance().player ?: return Vec3.ZERO
        return player.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize()
    }

    private fun spawnShockwave(feet: Vec3, strength: Float, rgb: Int) {
        val level = Minecraft.getInstance().level ?: return
        val dust = DustParticleOptions(rgb, SHOCK_DUST_SIZE)
        val count = (SHOCK_BASE + SHOCK_PER_STRENGTH * strength).toInt().coerceIn(SHOCK_BASE, SHOCK_MAX)
        for (i in 0 until count) {
            val angle = i * TWO_PI / count
            val dx = cos(angle)
            val dz = sin(angle)
            val type = if (i % 2 == 0) ParticleTypes.CLOUD else dust
            level.addParticle(type, feet.x + dx * SHOCK_START, feet.y + SHOCK_LIFT, feet.z + dz * SHOCK_START, dx * SHOCK_SPEED, 0.01, dz * SHOCK_SPEED)
        }
        level.addParticle(ParticleTypes.POOF, feet.x, feet.y + SHOCK_LIFT, feet.z, 0.0, 0.02, 0.0)
        if (strength > CRACK_STRENGTH) spawnCracks(feet, strength)
    }

    private const val AVERAGE_KEEP = 0.85
    private const val MIN_GAP_NANOS = 60_000_000L
    private const val MIN_WEIGHT = 0.6
    private const val MAX_WEIGHT = 2.5
    private const val STOP_NANOS = 70_000_000L
    private const val MAX_STOP_NANOS = 140_000_000L
    private const val SPELL_WINDOW_NANOS = 1_200_000_000L
    private const val TARGET_SEARCH = 3.0
    private const val IMPACT_HEIGHT = 0.6
    private const val SHOCK_BASE = 14
    private const val SHOCK_PER_STRENGTH = 6f
    private const val SHOCK_MAX = 30
    private const val SHOCK_DUST_SIZE = 1.6f
    private const val SHOCK_START = 0.4
    private const val SHOCK_SPEED = 0.22
    private const val SHOCK_LIFT = 0.1
    private const val TWO_PI = 6.2831855
    private const val STEP_DUST_COUNT = 5
    private const val STEP_DUST_SPEED = 0.12
    private const val CRACK_STRENGTH = 1.1f
    private const val CRACK_LINES_BASE = 5
    private const val CRACK_LINES_MAX = 9
    private const val CRACK_STEPS = 5
    private const val CRACK_SPACING = 0.5
}

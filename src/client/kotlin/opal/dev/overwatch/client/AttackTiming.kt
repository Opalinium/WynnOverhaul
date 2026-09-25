package opal.dev.overwatch.client

import kotlin.math.exp
import kotlin.math.sin

object AttackTiming {
    const val MIN_EFFECTIVE_CPS = 2.0

    private const val LEGACY_ATTACK_SPEED_EPSILON = 0.5
    private const val LEGACY_BASE_CPS = 8.0
    private const val SKIP_MIN_MULTIPLIER = 1.8
    private const val SKIP_MAX_MULTIPLIER = 3.0
    private const val DOUBLE_TAP_MIN_FRACTION = 0.32
    private const val DOUBLE_TAP_MAX_FRACTION = 0.5
    private const val HARD_FLOOR_FRACTION = 0.7
    private const val MAX_INTERVAL_MS = 2_000.0
    private const val EXACT_RATE_CEIL_FRACTION = 1.35
    private const val EXACT_RATE_FLOOR_ADD_CPS = 0.5
    private const val EXACT_RATE_HARD_CAP_CPS = 20.0
    private const val TEMPO_CLAMP = 0.6
    private const val ENVELOPE_WALK_STEP = 0.01
    private const val ENVELOPE_WALK_CLAMP = 0.12

    private val rng = java.util.Random()
    private val envelopePhase = rng.nextDouble() * 2.0 * Math.PI
    private var tempo = 0.0
    private var envelopeWalk = 0.0
    private var lastWasDoubleTap = false

    fun effectiveCps(baseAttacksPerSecond: Double, config: OverwatchConfig, exactRate: Boolean = false): Double {
        val ceilCps: Double
        val floorCps: Double
        val rawBase: Double
        if (exactRate) {
            ceilCps = maxOf(baseAttacksPerSecond * EXACT_RATE_CEIL_FRACTION, config.maxCps).coerceIn(0.4, EXACT_RATE_HARD_CAP_CPS)
            floorCps = (baseAttacksPerSecond + EXACT_RATE_FLOOR_ADD_CPS).coerceIn(0.3, ceilCps)
            rawBase = ceilCps
        } else {
            ceilCps = config.maxCps
            floorCps = MIN_EFFECTIVE_CPS.coerceAtMost(config.maxCps)
            rawBase = if (baseAttacksPerSecond < LEGACY_ATTACK_SPEED_EPSILON) LEGACY_BASE_CPS else baseAttacksPerSecond
        }
        val baseCps = rawBase.coerceIn(floorCps, ceilCps)

        val nowMs = System.nanoTime() / 1_000_000.0
        envelopeWalk = (envelopeWalk + rng.nextGaussian() * ENVELOPE_WALK_STEP).coerceIn(-ENVELOPE_WALK_CLAMP, ENVELOPE_WALK_CLAMP)
        val envelope = 1.0 + HumanizationProfile.clickEnvelopeAmplitude *
            sin(nowMs / HumanizationProfile.clickEnvelopePeriodMs * 2.0 * Math.PI + envelopePhase) + envelopeWalk
        val envelopedCps = (baseCps * envelope).coerceIn(floorCps, ceilCps)

        tempo = (tempo * HumanizationProfile.clickTempoAr +
            rng.nextGaussian() * HumanizationProfile.clickTempoDriftStdev).coerceIn(-TEMPO_CLAMP, TEMPO_CLAMP)

        val baseIntervalMs = 1000.0 / envelopedCps
        val hardFloorMs = if (exactRate) 1000.0 / ceilCps else 1000.0 / config.maxCps * HARD_FLOOR_FRACTION
        val maxIntervalMs = if (exactRate) 1000.0 / floorCps else MAX_INTERVAL_MS

        if (!exactRate && !lastWasDoubleTap && rng.nextDouble() < HumanizationProfile.doubleTapChance) {
            lastWasDoubleTap = true
            val frac = DOUBLE_TAP_MIN_FRACTION + rng.nextDouble() * (DOUBLE_TAP_MAX_FRACTION - DOUBLE_TAP_MIN_FRACTION)
            return 1000.0 / (baseIntervalMs * frac).coerceAtLeast(1000.0 / config.maxCps * HARD_FLOOR_FRACTION)
        }
        if (!exactRate) lastWasDoubleTap = false

        var intervalMs = baseIntervalMs * exp(tempo) * exp(rng.nextGaussian() * HumanizationProfile.clickLognormalSigma)
        if (rng.nextDouble() < HumanizationProfile.skipChance) {
            intervalMs *= SKIP_MIN_MULTIPLIER + rng.nextDouble() * (SKIP_MAX_MULTIPLIER - SKIP_MIN_MULTIPLIER)
        }
        intervalMs = intervalMs.coerceIn(hardFloorMs, maxIntervalMs)
        return 1000.0 / intervalMs
    }
}

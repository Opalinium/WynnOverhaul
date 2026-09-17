package opal.dev.overwatch.client

import kotlin.math.ln
import kotlin.math.max

object HumanizationProfile {

    private val rng = java.util.Random()

    private fun range(min: Double, max: Double): Double = min + rng.nextDouble() * (max - min)

    val clickLognormalSigma: Double = range(0.28, 0.42)
    val clickEnvelopeAmplitude: Double = range(0.10, 0.20)
    val clickEnvelopePeriodMs: Double = range(14_000.0, 30_000.0)
    val clickTempoAr: Double = range(0.55, 0.78)
    val clickTempoDriftStdev: Double = range(0.05, 0.12)
    val doubleTapChance: Double = range(0.02, 0.06)
    val skipChance: Double = range(0.05, 0.11)

    val overshootChance: Double = range(0.28, 0.50)
    val aimNoiseDriveScale: Double = range(0.8, 1.3)
    val trackingSwayDegrees: Double = range(0.5, 1.4)

    private val reactionBaseNanos: Double = range(150.0, 235.0) * 1_000_000.0
    private val reactionJitterNanos: Double = range(18.0, 34.0) * 1_000_000.0
    private val reactionTailNanos: Double = range(55.0, 150.0) * 1_000_000.0

    fun nextReactionLatencyNanos(): Long {
        val gaussian = rng.nextGaussian() * reactionJitterNanos
        val tail = -ln(max(1.0e-6, rng.nextDouble())) * reactionTailNanos
        return (reactionBaseNanos + gaussian + tail).toLong().coerceIn(95_000_000L, 520_000_000L)
    }
}

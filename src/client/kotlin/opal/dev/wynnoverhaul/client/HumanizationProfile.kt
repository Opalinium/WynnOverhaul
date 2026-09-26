package opal.dev.wynnoverhaul.client


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

}

package opal.dev.wynnoverhaul.client

import kotlin.math.exp

object MathX {
    fun expApproach(dt: Float, tau: Float): Float = 1f - exp(-dt / tau)

    fun expApproach(dt: Double, rate: Double): Double = 1.0 - exp(-dt * rate)
}

package opal.dev.wynnoverhaul.client

import java.util.function.Supplier

object WynnMapBounds {
    const val MIN_X = -2560
    const val MAX_X = 2047
    const val MIN_Z = -6144
    const val MAX_Z = -1

    fun contains(x: Double, z: Double): Boolean = x >= MIN_X && x <= MAX_X && z >= MIN_Z && z <= MAX_Z
}

class CinderLodBounds : Supplier<IntArray?> {
    override fun get(): IntArray? {
        if (!WynnOverhaulGate.onWynncraft || !WynnOverhaulConfig.current.voxyVistaEnabled) return null
        return intArrayOf(WynnMapBounds.MIN_X, WynnMapBounds.MIN_Z, WynnMapBounds.MAX_X, WynnMapBounds.MAX_Z)
    }
}

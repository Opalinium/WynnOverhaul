package opal.dev.overwatch.client

import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

object HotspotRingParticles {

    private class Entry(val x: Double, val y: Double, val z: Double, val atNanos: Long)

    private val entries = ArrayDeque<Entry>()

    @JvmStatic
    fun record(color: Vector3f, x: Double, y: Double, z: Double) {
        if (!isPinkish(color)) return
        entries.addLast(Entry(x, y, z, System.nanoTime()))
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    fun estimateRadius(center: Vec3, reference: Vec3, horizontalSearchRadius: Double, verticalSearchRadius: Double): Double? {
        pruneOld()
        val referenceAngle = kotlin.math.atan2(reference.z - center.z, reference.x - center.x)
        val candidates = entries.asSequence()
            .filter { kotlin.math.abs(it.y - center.y) <= verticalSearchRadius }
            .mapNotNull { entry ->
                val dx = entry.x - center.x
                val dz = entry.z - center.z
                val radius = kotlin.math.sqrt(dx * dx + dz * dz)
                if (radius > horizontalSearchRadius) return@mapNotNull null
                val angleDegrees = angularDifferenceDegrees(kotlin.math.atan2(dz, dx), referenceAngle)
                radius to angleDegrees
            }
            .toList()
        if (candidates.size < MIN_SAMPLE_COUNT) return null

        val local = candidates.filter { it.second <= LOCAL_ANGLE_WINDOW_DEGREES }
        val pool = if (local.size >= MIN_SAMPLE_COUNT) local else candidates
        return pool.map { it.first }.average()
    }

    private fun angularDifferenceDegrees(a: Double, b: Double): Double {
        var diff = Math.toDegrees(a - b) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return kotlin.math.abs(diff)
    }

    private fun pruneOld() {
        val now = System.nanoTime()
        while (entries.isNotEmpty() && now - entries.first().atNanos > MAX_AGE_NANOS) {
            entries.removeFirst()
        }
    }

    private fun isPinkish(color: Vector3f): Boolean {
        val r = color.x
        val g = color.y
        val b = color.z
        if (r < PINK_MIN_CHANNEL || b < PINK_MIN_CHANNEL) return false
        return g <= minOf(r, b) * PINK_MAX_GREEN_RATIO
    }

    private const val MAX_ENTRIES = 400
    private const val MIN_SAMPLE_COUNT = 3
    private const val MAX_AGE_NANOS = 3_000_000_000L
    private const val LOCAL_ANGLE_WINDOW_DEGREES = 40.0
    private const val PINK_MIN_CHANNEL = 0.45f
    private const val PINK_MAX_GREEN_RATIO = 0.65f
}

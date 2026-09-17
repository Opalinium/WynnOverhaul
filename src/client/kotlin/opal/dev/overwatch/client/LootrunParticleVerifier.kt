package opal.dev.overwatch.client

import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object LootrunParticleVerifier {

    private val positions = ArrayList<Vec3>()

    fun onPosition(x: Double, y: Double, z: Double): Vec3? {
        val added = Vec3(x, y, z)
        if (!verifyNewPosition(added)) {
            positions.clear()
            return null
        }
        positions.add(added)
        return when (verifyCompleteness()) {
            Result.VERIFIED -> {
                val right = positions[0]
                val center = Vec3(right.x - RADIUS, right.y, right.z)
                positions.clear()
                center
            }
            Result.INVALID -> {
                positions.clear()
                null
            }
            Result.UNVERIFIED -> null
        }
    }

    private enum class Result { VERIFIED, UNVERIFIED, INVALID }

    private fun verifyNewPosition(added: Vec3): Boolean {
        if (positions.isEmpty()) return isParticlePrecise(added)

        val right = positions[0]
        val center = Vec3(right.x - RADIUS, right.y, right.z)
        if (center.distanceToSqr(added) >= CIRCLE_RADIUS_WITH_ERROR * CIRCLE_RADIUS_WITH_ERROR) return false

        if (positions.size % 5 == 0) return isParticlePrecise(added)
        return true
    }

    private fun verifyCompleteness(): Result {
        if (positions.size == 2) {
            val right = positions[0]
            val left = positions[1]
            if (isParticlePrecise(right) && isParticlePrecise(left)) {
                return if (right.distanceToSqr(left) == 100.0) Result.VERIFIED else Result.INVALID
            }
            return Result.UNVERIFIED
        }
        if (positions.size == 20) return Result.VERIFIED
        if (positions.size > 20) return Result.INVALID
        return Result.UNVERIFIED
    }

    private fun isParticlePrecise(pos: Vec3): Boolean = abs(pos.x % 0.5) == 0.0 && abs(pos.z % 0.5) == 0.0

    private const val RADIUS = 5.0
    private const val CIRCLE_RADIUS_WITH_ERROR = RADIUS + 1.0
}

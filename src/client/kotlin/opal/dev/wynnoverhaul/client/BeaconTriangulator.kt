package opal.dev.wynnoverhaul.client

import net.minecraft.world.phys.Vec3

object BeaconTriangulator {
    private class Track {
        val window = ArrayDeque<Vec3>()
        val rays = ArrayDeque<Pair<Vec3, Vec3>>()
        var last: Vec3? = null
        var solved: Vec3? = null
        var seenNanos = 0L
    }

    private val tracks = HashMap<ActivityType, Track>()

    fun estimate(kind: ActivityType, player: Vec3, beacon: Vec3, distance: Double): Vec3 {
        val track = tracks.getOrPut(kind) { Track() }
        val dir = beacon.subtract(player)
        val len = dir.length()
        val raw = when {
            len < NEAR_BEACON -> beacon
            distance <= len + NEAR_SLACK -> beacon
            else -> player.add(dir.scale(distance / len))
        }
        val window = track.window
        if (window.isNotEmpty() && mean(window).distanceTo(raw) > JUMP_RESET) window.clear()
        window.addLast(raw)
        while (window.size > WINDOW) window.removeFirst()
        addRay(track, player, dir, len)
        val solved = solve(track)
        track.solved = solved
        val result = solved ?: mean(window)
        track.last = result
        track.seenNanos = System.nanoTime()
        return result
    }

    fun reliable(kind: ActivityType): Boolean = tracks[kind]?.solved != null

    private fun addRay(track: Track, player: Vec3, dir: Vec3, len: Double) {
        if (len < MIN_RAY) return
        val unit = dir.scale(1.0 / len)
        val previous = track.solved
        if (previous != null && distanceToRay(previous, player, unit) > RAY_RESET) {
            track.rays.clear()
            track.solved = null
        }
        val last = track.rays.lastOrNull()
        if (last != null && last.first.distanceTo(player) < RAY_SPACING) return
        track.rays.addLast(player to unit)
        while (track.rays.size > MAX_RAYS) track.rays.removeFirst()
    }

    private fun distanceToRay(point: Vec3, origin: Vec3, unit: Vec3): Double {
        val rel = point.subtract(origin)
        val along = rel.dot(unit)
        return rel.subtract(unit.scale(along)).length()
    }

    private fun solve(track: Track): Vec3? {
        val rays = track.rays
        if (rays.size < MIN_RAYS) return null
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        for ((origin, d) in rays) {
            val dv = doubleArrayOf(d.x, d.y, d.z)
            val ov = doubleArrayOf(origin.x, origin.y, origin.z)
            for (i in 0..2) {
                for (j in 0..2) {
                    val m = (if (i == j) 1.0 else 0.0) - dv[i] * dv[j]
                    a[i][j] += m
                    b[i] += m * ov[j]
                }
            }
        }
        val n = rays.size.toDouble()
        val det = a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) -
            a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0]) +
            a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0])
        if (det / (n * n * n) < DET_MIN) return null
        fun cramer(col: Int): Double {
            val m = Array(3) { r -> DoubleArray(3) { c -> if (c == col) b[r] else a[r][c] } }
            return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
                m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
                m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
        }
        val point = Vec3(cramer(0) / det, cramer(1) / det, cramer(2) / det)
        var residual = 0.0
        var ahead = 0
        for ((origin, d) in rays) {
            residual += distanceToRay(point, origin, d)
            if (point.subtract(origin).dot(d) > 0.0) ahead++
        }
        if (residual / n > MAX_RESIDUAL || ahead < rays.size * 0.8) return null
        return point
    }

    fun recent(): Map<ActivityType, Vec3> {
        val now = System.nanoTime()
        val out = HashMap<ActivityType, Vec3>(tracks.size)
        val iterator = tracks.entries.iterator()
        while (iterator.hasNext()) {
            val (kind, track) = iterator.next()
            if (now - track.seenNanos > HOLD_NANOS) {
                iterator.remove()
                continue
            }
            track.last?.let { out[kind] = it }
        }
        return out
    }

    fun reset() {
        tracks.clear()
    }

    private fun mean(window: ArrayDeque<Vec3>): Vec3 {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (v in window) {
            x += v.x
            y += v.y
            z += v.z
        }
        val n = window.size.toDouble()
        return Vec3(x / n, y / n, z / n)
    }

    private const val WINDOW = 60
    private const val MIN_RAY = 3.0
    private const val MIN_RAYS = 4
    private const val MAX_RAYS = 32
    private const val RAY_SPACING = 2.0
    private const val RAY_RESET = 30.0
    private const val DET_MIN = 0.01
    private const val MAX_RESIDUAL = 6.0
    private const val JUMP_RESET = 25.0
    private const val NEAR_BEACON = 2.0
    private const val NEAR_SLACK = 2.0
    private const val HOLD_NANOS = 3_000_000_000L
}

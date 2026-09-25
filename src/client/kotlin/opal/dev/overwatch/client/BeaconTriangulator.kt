package opal.dev.overwatch.client

import net.minecraft.world.phys.Vec3

object BeaconTriangulator {
    private class Track {
        val window = ArrayDeque<Vec3>()
        var last: Vec3? = null
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
        val result = mean(window)
        track.last = result
        track.seenNanos = System.nanoTime()
        return result
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
    private const val JUMP_RESET = 25.0
    private const val NEAR_BEACON = 2.0
    private const val NEAR_SLACK = 2.0
    private const val HOLD_NANOS = 3_000_000_000L
}

package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max

object DungeonEntrances {
    private class Entrance(val name: String, val x: Int, val z: Int)

    private val ENTRANCES = listOf(
        Entrance("Decrepit Sewers", -946, -1886),
        Entrance("Infested Pit", -193, -1870),
        Entrance("Underworld Crypt", 290, -1950),
        Entrance("Timelost Sanctum", -263, -1069),
        Entrance("Sand-Swept Tomb", 1432, -1830),
        Entrance("Ice Barrows", 132, -636),
        Entrance("Undergrowth Ruins", -641, -841),
        Entrance("Galleon's Graveyard", -582, -3511),
        Entrance("Fallen Factory", -1646, -2608),
        Entrance("Eldritch Outlook", 1291, -749),
    )

    private val resolvedY = HashMap<String, Int>()

    fun at(name: String): QuestWikiFetcher.WikiCoord? {
        val entrance = ENTRANCES.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return QuestWikiFetcher.WikiCoord(entrance.x, heightOf(entrance), entrance.z)
    }

    fun snap(player: Vec3, beacon: Vec3, distance: Double): Vec3? {
        val dx = beacon.x - player.x
        val dz = beacon.z - player.z
        val dirLen = hypot(dx, dz)
        if (dirLen < 1.0) return null
        var best: Entrance? = null
        var bestAngle = MAX_ANGLE
        for (entrance in ENTRANCES) {
            val ex = entrance.x + 0.5 - player.x
            val ez = entrance.z + 0.5 - player.z
            val range = hypot(ex, ez)
            if (range < 1.0 || Math.abs(range - distance) > max(MIN_RANGE_SLACK, distance * RANGE_SLACK)) continue
            val angle = acos(((dx * ex + dz * ez) / (dirLen * range)).coerceIn(-1.0, 1.0))
            if (angle < bestAngle) {
                bestAngle = angle
                best = entrance
            }
        }
        val entrance = best ?: return null
        return Vec3(entrance.x + 0.5, heightOf(entrance).toDouble(), entrance.z + 0.5)
    }

    fun reset() {
        resolvedY.clear()
    }

    private fun heightOf(entrance: Entrance): Int {
        val client = Minecraft.getInstance()
        val level = client.level
        val loaded = level != null && level.hasChunk(entrance.x shr 4, entrance.z shr 4)
        if (loaded) {
            val surface = level!!.getHeight(Heightmap.Types.MOTION_BLOCKING, entrance.x, entrance.z)
            resolvedY[entrance.name] = surface
            return surface
        }
        return resolvedY.getOrPut(entrance.name) { client.player?.y?.toInt() ?: DEFAULT_Y }
    }

    private const val DEFAULT_Y = 70
    private const val MAX_ANGLE = 0.1
    private const val MIN_RANGE_SLACK = 30.0
    private const val RANGE_SLACK = 0.12
}

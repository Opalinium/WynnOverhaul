package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.AABB

object WynnTotemTracker {
    data class Totem(val id: Int, var seconds: Int, var maxSeconds: Int)

    @Volatile
    var totems: List<Totem> = emptyList()
        private set

    private val byId = HashMap<Int, Totem>()

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val self = player.name.string
        val box = AABB(
            player.x - SCAN_RADIUS, player.y - SCAN_RADIUS, player.z - SCAN_RADIUS,
            player.x + SCAN_RADIUS, player.y + SCAN_RADIUS, player.z + SCAN_RADIUS,
        )
        val seen = HashSet<Int>()
        for (entity in level.getEntities(player, box) { it is Display.TextDisplay }) {
            val seconds = parse((entity as Display.TextDisplay).text.string, self) ?: continue
            seen.add(entity.id)
            val known = byId[entity.id]
            if (known == null) {
                byId[entity.id] = Totem(entity.id, seconds, maxOf(seconds, 1))
            } else {
                known.seconds = seconds
                known.maxSeconds = maxOf(known.maxSeconds, seconds)
            }
        }
        byId.keys.retainAll(seen)
        totems = byId.values.filter { it.seconds > 0 }.sortedBy { it.seconds }.take(MAX_TOTEMS)
    }

    fun clear() {
        byId.clear()
        totems = emptyList()
    }

    private fun parse(raw: String, self: String): Int? {
        var ownerEnd = raw.indexOf("'s Totem")
        var suffix = SUFFIX_LONG
        if (ownerEnd < 0) {
            ownerEnd = raw.indexOf("' Totem")
            suffix = SUFFIX_SHORT
        }
        if (ownerEnd < 0) return null
        val lineStart = raw.lastIndexOf('\n', ownerEnd).let { if (it < 0) 0 else it + 1 }
        if (!raw.substring(lineStart, ownerEnd).trim().equals(self, ignoreCase = true)) return null
        val anchor = raw.indexOf(TIMER_GLYPH, ownerEnd + suffix)
        if (anchor < 0) return null
        var i = anchor + 1
        while (i < raw.length && (raw[i] == ' ' || raw[i] == '\n')) i++
        var j = i
        while (j < raw.length && raw[j].isDigit()) j++
        if (j == i || j >= raw.length || raw[j] != 's') return null
        return raw.substring(i, j).toIntOrNull()
    }

    private const val SCAN_RADIUS = 48.0
    private const val MAX_TOTEMS = 4
    private const val SUFFIX_LONG = 8
    private const val SUFFIX_SHORT = 7
    private const val TIMER_GLYPH = "\uE01F"
}

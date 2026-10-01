package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.AABB

object WynnTotemTracker {
    enum class Kind(val tag: String) { CLASS(""), MOB(" (Mob)"), GATHERING(" (Gathering)") }

    data class Totem(val id: Int, val owner: String, var seconds: Int, var maxSeconds: Int, val kind: Kind = Kind.CLASS)

    private class Parsed(val owner: String, val seconds: Int, val kind: Kind)

    @Volatile
    var totems: List<Totem> = emptyList()
        private set

    private val byId = HashMap<Int, Totem>()

    fun tick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val self = player.gameProfile.name
        val box = AABB(
            player.x - SCAN_RADIUS, player.y - SCAN_RADIUS, player.z - SCAN_RADIUS,
            player.x + SCAN_RADIUS, player.y + SCAN_RADIUS, player.z + SCAN_RADIUS,
        )
        val seen = HashSet<Int>()
        for (entity in level.getEntities(player, box) { it is Display.TextDisplay }) {
            val parsed = parse((entity as Display.TextDisplay).text.string) ?: continue
            if (parsed.kind == Kind.CLASS && parsed.owner != self) continue
            seen.add(entity.id)
            val known = byId[entity.id]
            if (known == null) {
                byId[entity.id] = Totem(entity.id, parsed.owner, parsed.seconds, maxOf(parsed.seconds, 1), parsed.kind)
            } else {
                known.seconds = parsed.seconds
                known.maxSeconds = maxOf(known.maxSeconds, parsed.seconds)
            }
        }
        byId.keys.retainAll(seen)
        totems = byId.values.filter { it.seconds > 0 }.sortedWith(compareBy({ it.kind }, { it.seconds })).take(MAX_TOTEMS)
    }

    fun clear() {
        byId.clear()
        totems = emptyList()
    }

    private fun parse(raw: String): Parsed? {
        MOB_TOTEM.matchEntire(raw)?.let { match ->
            val owner = match.groupValues[1].trim()
            if (owner.isEmpty()) return null
            val minutes = match.groupValues[3].toIntOrNull() ?: 0
            val seconds = match.groupValues[4].toIntOrNull() ?: return null
            val kind = if (match.groupValues[2] == "Gathering") Kind.GATHERING else Kind.MOB
            return Parsed(owner, minutes * 60 + seconds, kind)
        }
        return parseClassTotem(raw)
    }

    private fun parseClassTotem(raw: String): Parsed? {
        var ownerEnd = raw.indexOf("'s Totem")
        var suffix = SUFFIX_LONG
        if (ownerEnd < 0) {
            ownerEnd = raw.indexOf("' Totem")
            suffix = SUFFIX_SHORT
        }
        if (ownerEnd < 0) return null
        val lineStart = raw.lastIndexOf('\n', ownerEnd).let { if (it < 0) 0 else it + 1 }
        val owner = raw.substring(lineStart, ownerEnd).trim()
        if (owner.isEmpty()) return null
        val anchor = raw.indexOf(TIMER_GLYPH, ownerEnd + suffix)
        if (anchor < 0) return null
        var i = anchor + 1
        while (i < raw.length && (raw[i] == ' ' || raw[i] == '\n')) i++
        var j = i
        while (j < raw.length && raw[j].isDigit()) j++
        if (j == i || j >= raw.length || raw[j] != 's') return null
        val seconds = raw.substring(i, j).toIntOrNull() ?: return null
        return Parsed(owner, seconds, Kind.CLASS)
    }

    private val MOB_TOTEM = Regex("""^(.*?)'s? (Mob|Gathering) Totem\n (?:(\d+)m )?(\d+)s$""")

    private const val SCAN_RADIUS = 64.0
    private const val MAX_TOTEMS = 6
    private const val SUFFIX_LONG = 8
    private const val SUFFIX_SHORT = 7
    private const val TIMER_GLYPH = ""
}

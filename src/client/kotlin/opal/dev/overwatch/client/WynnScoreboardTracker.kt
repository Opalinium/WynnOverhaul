package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object WynnScoreboardTracker {
    data class Tracked(val type: String, val name: String, val nextTask: String)

    var current: Tracked? = null
        private set

    var sidebarText: String = ""
        private set

    var sidebarTitle: String = ""
        private set

    var sidebarLines: List<String> = emptyList()
        private set

    fun tick(mc: Minecraft) {
        val scoreboard = mc.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (scoreboard == null || objective == null) {
            current = null
            sidebarText = ""
            sidebarTitle = ""
            sidebarLines = emptyList()
            return
        }

        val lines = scoreboard.listPlayerScores(objective)
            .filterNot { it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })
            .take(15)
            .map { entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()) as Component }

        val texts = lines.map { clean(it.string) }
        sidebarText = joinWrapped(texts)
        val rawTitle = clean(objective.displayName.string)
        sidebarTitle = if (SERVER_BRAND_PATTERN.matches(rawTitle)) "" else rawTitle
        sidebarLines = texts.filter { it.isNotEmpty() }

        val headerIndex = texts.indexOfFirst { HEADER_PATTERN.matches(it) }
        if (headerIndex < 0) {
            current = null
            return
        }
        val type = HEADER_PATTERN.find(texts[headerIndex])!!.groupValues[1]

        val nameParts = mutableListOf<String>()
        var i = headerIndex + 1
        while (i < lines.size && startsWhite(lines[i])) {
            nameParts.add(texts[i])
            i++
        }

        val taskParts = mutableListOf<String>()
        while (i < lines.size) {
            val text = texts[i]
            if (text.isEmpty() || HEADER_PATTERN.matches(text)) break
            taskParts.add(text)
            i++
        }

        val name = joinWrapped(nameParts)
        current = Tracked(type, name, joinWrapped(taskParts))
    }

    private fun clean(text: String): String {
        val result = TextClean.clean(text)
        return if (result.all { it == SIDEBAR_SPACER || it.isWhitespace() }) "" else result
    }

    private fun joinWrapped(parts: List<String>): String {
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isEmpty()) continue
            if (sb.isNotEmpty() && !SPACER_PATTERN.matches(part)) sb.append(' ')
            sb.append(part)
        }
        return sb.toString()
    }

    private fun startsWhite(component: Component): Boolean = component.string.startsWith(WHITE_LEGACY_CODE)

    private const val WHITE_LEGACY_CODE = "§f"
    private const val SIDEBAR_SPACER = 'À'
    private val HEADER_PATTERN = Regex("^Tracked (.+):$")
    private val SPACER_PATTERN = Regex("""[^a-zA-Z\[\d-].*""")
    private val SERVER_BRAND_PATTERN = Regex("""(?i)^([a-z0-9-]+\.)*wynncraft\.(com|net)$""")
}

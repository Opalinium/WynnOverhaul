package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import java.util.Optional

object WynnScoreboardTracker {

    data class Tracked(val type: String, val name: String, val nextTask: String)

    var current: Tracked? = null
        private set

    var sidebarText: String = ""
        private set

    fun tick(mc: Minecraft) {
        val scoreboard = mc.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (scoreboard == null || objective == null) {
            current = null
            sidebarText = ""
            return
        }

        val lines = scoreboard.listPlayerScores(objective)
            .filterNot { it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })
            .take(15)
            .map { entry -> PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()) as Component }

        val texts = lines.map { clean(it.string) }
        sidebarText = joinWrapped(texts)

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

        current = Tracked(type, joinWrapped(nameParts), joinWrapped(taskParts))
    }

    private fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == LEGACY_CODE_PREFIX) {
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            if (!isPrivateUse(cp) && cp != 0xFFFD) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val result = sb.toString().trim()
        return if (result.all { it == SIDEBAR_SPACER || it.isWhitespace() }) "" else result
    }

    private fun isPrivateUse(cp: Int): Boolean = cp >= 0xE000

    private fun joinWrapped(parts: List<String>): String {
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isEmpty()) continue
            if (sb.isNotEmpty() && !SPACER_PATTERN.matches(part)) sb.append(' ')
            sb.append(part)
        }
        return sb.toString()
    }

    private fun startsWhite(component: Component): Boolean {
        var result = false
        var seen = false
        component.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                if (!seen && string.isNotEmpty()) {
                    seen = true
                    result = style.color?.value == WHITE_RGB
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private const val WHITE_RGB = 0xFFFFFF
    private const val LEGACY_CODE_PREFIX = '§'
    private const val SIDEBAR_SPACER = 'À'
    private val HEADER_PATTERN = Regex("^Tracked (.+):$")
    private val SPACER_PATTERN = Regex("""[^a-zA-Z\[\d-].*""")
}

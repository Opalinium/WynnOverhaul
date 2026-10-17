package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object WynnScoreboardTracker {
    data class Tracked(val type: String, val name: String, val nextTask: String)

    data class PartyMember(
        val name: String,
        val level: Int?,
        val tag: String?,
        val health: Int?,
        val healthColor: Int?,
        val dead: Boolean,
        val online: Boolean = true,
        val fraction: Float? = null,
    )

    data class Party(val level: Int?, val members: List<PartyMember>, val rawLines: List<String>, val title: String = "Party")

    var current: Tracked? = null
        private set

    var party: Party? = null
        private set

    var war: Party? = null
        private set

    var rawLines: List<Component> = emptyList()
        private set

    var sidebarText: String = ""
        private set

    var sidebarTitle: String = ""
        private set

    var sidebarLines: List<String> = emptyList()
        private set

    private var lastLines: List<Component>? = null
    private var lastTitle: Component? = null

    fun tick(mc: Minecraft) {
        val scoreboard = mc.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (scoreboard == null || objective == null) {
            lastLines = null
            lastTitle = null
            current = null
            party = null
            war = null
            rawLines = emptyList()
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

        val title = objective.displayName
        if (lines == lastLines && title == lastTitle) return
        lastLines = lines
        lastTitle = title
        rawLines = lines
        val allTexts = lines.map { clean(it.string) }
        val partyEnd = extractParty(lines, allTexts)
        val warEnd = extractWar(lines, allTexts)
        val texts = if (party == null && war == null) allTexts else allTexts.mapIndexed { index, text -> if (index in partyEnd || index in warEnd) "" else text }
        sidebarText = joinWrapped(texts)

        val rawTitle = clean(objective.displayName.string)
        sidebarTitle = if (SERVER_BRAND_PATTERN.matches(rawTitle)) "" else rawTitle
        sidebarLines = texts.filter { it.isNotEmpty() }
        ObjectiveClaims.onSidebar(sidebarText)

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

    private fun extractParty(lines: List<Component>, texts: List<String>): IntRange {
        val section = extractSection(lines, texts, PARTY_HEADER, "Party")
        party = section?.first
        return section?.second ?: IntRange.EMPTY
    }

    private fun extractWar(lines: List<Component>, texts: List<String>): IntRange {
        val section = extractSection(lines, texts, WAR_HEADER, "War")
        war = section?.first
        return section?.second ?: IntRange.EMPTY
    }

    private fun extractSection(lines: List<Component>, texts: List<String>, header: Regex, title: String): Pair<Party, IntRange>? {
        val headerIndex = texts.indexOfFirst { header.matches(it) }
        if (headerIndex < 0) return null
        val level = header.find(texts[headerIndex])!!.groupValues.getOrNull(1)?.toIntOrNull()
        var end = headerIndex + 1
        val members = ArrayList<PartyMember>()
        while (end < texts.size && texts[end].startsWith("-")) {
            members.add(parseMember(lines[end], texts[end]))
            end++
        }
        return Party(level, members, texts.subList(headerIndex, end).toList(), title) to (headerIndex until end)
    }

    private fun parseMember(line: Component, text: String): PartyMember {
        val match = MEMBER.matchEntire(text)
        val name = match?.groupValues?.get(2)?.trim() ?: text.removePrefix("-").trim()
        val badge = match?.groupValues?.get(3)
        val level = badge?.toIntOrNull()
        val tag = badge?.takeIf { level == null }
        val health = match?.let { DIGITS.find(it.groupValues[1])?.value?.toIntOrNull() }
        var color: Int? = null
        var struck = false
        var inBar = false
        var barDone = false
        var filled = 0
        var empty = 0
        line.visit(FormattedText.StyledContentConsumer<Unit> { style, piece ->
            if (color == null && piece.any { it.isDigit() }) color = style.color?.value?.let { 0xFF000000.toInt() or it }
            if (style.isStrikethrough && piece.isNotBlank()) struck = true
            for (ch in piece) {
                if (barDone) break
                if (!inBar) {
                    if (ch == '[') inBar = true
                } else if (ch == ']') {
                    barDone = true
                } else if (ch == '|') {
                    if (style.color?.value == BAR_EMPTY_RGB) empty++ else filled++
                }
            }
            java.util.Optional.empty()
        }, Style.EMPTY)
        val fraction = if (match != null && filled + empty > 0) filled.toFloat() / (filled + empty) else null
        return PartyMember(name, level, tag, health, color, struck || health == 0, match != null, if (struck || health == 0) 0f else fraction)
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

    private const val BAR_EMPTY_RGB = 0x555555
    private const val WHITE_LEGACY_CODE = "§f"
    private const val SIDEBAR_SPACER = 'À'
    private val HEADER_PATTERN = Regex("^Tracked (.+):$")
    private val PARTY_HEADER = Regex("""^Party:?(?:\s*\[Lv\.?\s*(\d+)])?$""")
    private val WAR_HEADER = Regex("""^War:?$""")
    private val MEMBER = Regex("""^-\s*\[([^\]]*)]\s*(.+?)\s*\[([^\]]+)]$""")
    private val DIGITS = Regex("""\d+""")
    private val SPACER_PATTERN = Regex("""[^a-zA-Z\[\d-].*""")
    private val SERVER_BRAND_PATTERN = Regex("""(?i)^([a-z0-9-]+\.)*wynncraft\.(com|net)$""")
}

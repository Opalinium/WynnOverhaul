package opal.dev.wynnoverhaul.client

import java.util.Optional
import kotlin.math.abs
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
object WynnDialogueTracker {
    data class Choice(val text: String, val selected: Boolean)

    @Volatile
    var text: String? = null
        private set

    @Volatile
    var speaker: String? = null
        private set

    @Volatile
    var body: String = ""
        private set

    @Volatile
    private var built: List<Choice> = emptyList()

    val choices: List<Choice>
        get() {
            settle()
            return built
        }

    @Volatile
    var requiresShift: Boolean = false
        private set

    @Volatile
    var hasChoices: Boolean = false
        private set

    @Volatile
    var dialogueAtMillis: Long = 0L
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        text = null
        speaker = null
        body = ""
        built = emptyList()
        lastTruth = null
        requiresShift = false
        hasChoices = false
        dialogueAtMillis = 0L
        options.clear()
        windowStart = 0
        selIndex = null
        pendingUntil = 0L
        stableSpeaker = null
        stableBody = ""
    }

    fun dialogueVisible(now: Long): Boolean {
        if (text == null) return false
        return now - dialogueAtMillis < DIALOGUE_TTL_MS
    }

    fun isDialogue(message: Component): Boolean {
        var found = false
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, _ ->
                val path = (style.font as? FontDescription.Resource)?.id?.path
                if (path != null && path.startsWith(DIALOGUE_FONT_PREFIX) && !path.startsWith(FADE_FONT_PREFIX)) {
                    found = true
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return found
    }

    private fun onActionBar(message: Component) {
        if (!isDialogue(message)) return
        val raw = message.string
        val now = System.currentTimeMillis()
        val cleaned = cleanText(raw)
        val choiceModeHint = raw.contains("to choose option", ignoreCase = true)
        requiresShift = raw.contains("to continue", ignoreCase = true) ||
            raw.contains("to confirm", ignoreCase = true)

        val visited = visitParts(message)
        val live = visited.choices
        hasChoices = live.isNotEmpty() || choiceModeHint

        val stripped = stripControls(cleaned)
        val (fallbackName, rest) = extractSpeaker(stripped)
        val visitedSpeaker = cleanText(visited.speakerRaw).trim()
        speaker = visitedSpeaker.ifBlank { fallbackName }

        val visitedBody = stripControls(cleanText(visited.bodyRaw))
        body = if (visitedBody.isNotBlank()) {
            visitedBody
        } else if (choiceModeHint) {
            val question = rest.indexOf('?')
            if (question >= 0) rest.substring(0, question + 1).trim() else rest
        } else {
            rest
        }

        val samePrompt = speaker == stableSpeaker &&
            (stableBody.isEmpty() || body.startsWith(stableBody) || stableBody.startsWith(body))
        if (!samePrompt) {
            options.clear()
            windowStart = 0
            selIndex = null
            pendingUntil = 0L
        }
        learnPolarity(visited.selectedRow)
        lastSelectedRow = visited.selectedRow
        val truthRank = visited.selectedRow?.let { rankOf(it, visited.rowCount) }
        if (live.isNotEmpty()) mergeWindow(live, truthRank)
        stableSpeaker = speaker
        stableBody = body
        refreshChoices()

        text = cleaned
        dialogueAtMillis = now
        WynnOverhaulGate.noteActionBar()
    }

    fun debugState(): String =
        "selectedRow=$lastSelectedRow polarity=$polarity options=${options.mapIndexed { i, t -> "$i:${t.take(40)}" }} windowStart=$windowStart selIndex=$selIndex hasChoices=$hasChoices"

    fun isChoiceActive(): Boolean {
        if (!hasChoices || choices.isEmpty()) return false
        return dialogueVisible(System.currentTimeMillis())
    }

    fun nudgeSelection(direction: Int) {
        if (direction == 0 || !isChoiceActive()) return
        if (options.isEmpty()) return
        val current = selIndex ?: 0
        selIndex = Math.floorMod(current + direction, options.size)
        pendingUntil = System.currentTimeMillis() + PENDING_NUDGE_MS
        if (polarity == 0) {
            rowBeforeNudge = lastSelectedRow
            nudgeDirection = direction
        }
        refreshChoices()
    }

    private fun settle() {
        val due = pendingUntil
        if (due == 0L || System.currentTimeMillis() < due) return
        pendingUntil = 0L
        val truth = lastTruth ?: return
        if (truth != selIndex && truth in options.indices) {
            selIndex = truth
            refreshChoices()
        }
    }

    private fun refreshChoices() {
        built = options.mapIndexed { index, text -> Choice(text.take(MAX_CHOICE_LENGTH), index == selIndex) }
            .filter { it.text.isNotEmpty() }
    }

    private fun mergeWindow(live: List<NumberedChoice>, selectedRank: Int?) {
        val window = live.sortedBy { it.number }
        val offset = alignWindow(window)
        for ((i, c) in window.withIndex()) {
            val at = offset + i
            while (options.size <= at) options.add("")
            options[at] = stitch(options[at], c.text)
        }
        windowStart = offset
        val strong = selectedRank?.coerceIn(0, window.size - 1)
            ?: strongPick(live)?.let { number -> window.indexOfFirst { it.number == number } }?.takeIf { it >= 0 }
        val current = selIndex
        val truth = strong?.let { offset + it }
        lastTruth = truth
        val now = System.currentTimeMillis()
        if (now < pendingUntil && current != null) {
            if (truth == current) pendingUntil = 0L
            return
        }
        selIndex = when {
            truth != null -> truth
            current == null -> offset
            current < offset -> offset
            current >= offset + window.size -> offset + window.size - 1
            else -> current
        }
    }

    private fun alignWindow(window: List<NumberedChoice>): Int {
        val absolute = maxOf(0, window.first().number)
        if (options.isEmpty()) return absolute
        var best = windowStart
        var bestScore = -1
        for (offset in maxOf(0, windowStart - window.size)..windowStart + window.size) {
            var score = 0
            for ((i, c) in window.withIndex()) {
                val known = options.getOrNull(offset + i) ?: continue
                if (sameOption(known, c.text)) score++
            }
            if (score > bestScore || score == bestScore && abs(offset - windowStart) < abs(best - windowStart)) {
                best = offset
                bestScore = score
            }
        }
        return if (bestScore > 0 || absolute == 0) best else absolute
    }

    private fun sameOption(a: String, b: String): Boolean {
        if (a.length < SAME_MIN_LENGTH || b.length < SAME_MIN_LENGTH) return false
        if (a.startsWith(b) || b.startsWith(a)) return true
        return longestCommonSubstring(a, b).len >= LCS_MIN_LENGTH
    }

    private data class NumberedChoice(val number: Int, val text: String, val color: String)

    private data class Visited(val bodyRaw: String, val choices: List<NumberedChoice>, val speakerRaw: String, val selectedRow: Int?, val rowCount: Int)

    private fun visitParts(message: Component): Visited {
        val body = StringBuilder()
        val speaker = StringBuilder()
        val texts = LinkedHashMap<Int, StringBuilder>()
        val colors = HashMap<Int, String>()
        var selectedRow: Int? = null
        val rows = HashSet<Int>()
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                val path = (style.font as? FontDescription.Resource)?.id?.path
                if (path != null) {
                    when {
                        CHOICE_NUMBER_PATTERN.containsMatchIn(path) -> {
                            val num = CHOICE_NUMBER_PATTERN.find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
                            if (num != null) {
                                texts.getOrPut(num) { StringBuilder() }.append(string)
                                colors.putIfAbsent(num, style.color?.toString() ?: "default")
                            }
                        }
                        path.endsWith(CHOICE_STYLE_SUFFIX) -> {
                            if (selectedRow == null) selectedRow = selectedRowOf(string)
                            rowsOf(string, rows)
                        }
                        BODY_MARKER in path -> body.append(string)
                        path.endsWith(NAMEPLATE_PATH) -> speaker.append(string)
                    }
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        val choices = texts.map { (num, sb) ->
            NumberedChoice(num, cleanText(sb.toString()).trim(), colors[num] ?: "default")
        }
        return Visited(body.toString(), choices, speaker.toString(), selectedRow, rows.size)
    }

    private fun rowsOf(string: String, into: MutableSet<Int>) {
        for (ch in string) {
            val code = ch.code
            if (code !in CHOICE_GLYPH_MIN..CHOICE_GLYPH_MAX || code == CHOICE_MARKER_GLYPH) continue
            val row = (code shr 4) and 0xF
            if (row in 1..8) into.add(row)
        }
    }

    private fun rankOf(row: Int, rowCount: Int): Int? = when {
        polarity > 0 -> row - 1
        polarity < 0 -> rowCount - row
        else -> null
    }

    private fun learnPolarity(row: Int?) {
        val before = rowBeforeNudge ?: return
        if (polarity != 0 || row == null || row == before) return
        rowBeforeNudge = null
        val delta = row - before
        if (abs(delta) != 1) return
        polarity = if (delta * nudgeDirection > 0) 1 else -1
    }

    private fun selectedRowOf(string: String): Int? {
        for (ch in string) {
            val code = ch.code
            if (code !in CHOICE_GLYPH_MIN..CHOICE_GLYPH_MAX || code == CHOICE_MARKER_GLYPH) continue
            val row = (code shr 4) and 0xF
            if (code and 0xF != 0 && row in 1..8) return row
        }
        return null
    }

    private fun strongPick(live: List<NumberedChoice>): Int? {
        if (live.size < 2) return null
        val byColor = live.groupBy { it.color }
        if (byColor.size == 2) {
            byColor.values.firstOrNull { it.size == 1 }?.firstOrNull()?.let { return it.number }
        }
        return null
    }

    private fun stitch(old: String, new: String): String {
        if (old.isEmpty()) return new.take(MAX_CHOICE_LENGTH)
        if (new.isEmpty()) return old
        if (new.contains(old)) return new.take(MAX_CHOICE_LENGTH)
        if (old.contains(new)) return old
        val run = longestCommonSubstring(old, new)
        if (run.len >= LCS_MIN_LENGTH) {
            return (old.substring(0, run.i) + new.substring(run.j)).take(MAX_CHOICE_LENGTH)
        }
        return if (new.length >= old.length) new.take(MAX_CHOICE_LENGTH) else old
    }

    private data class CommonRun(val len: Int, val i: Int, val j: Int)

    private fun longestCommonSubstring(a: String, b: String): CommonRun {
        val n = minOf(a.length, MAX_CHOICE_LENGTH)
        val m = minOf(b.length, MAX_CHOICE_LENGTH)
        var bestLen = 0
        var bestI = 0
        var bestJ = 0
        var prev = IntArray(m + 1)
        var curr = IntArray(m + 1)
        for (i in 1..n) {
            for (j in 1..m) {
                if (a[i - 1] == b[j - 1]) {
                    curr[j] = prev[j - 1] + 1
                    if (curr[j] > bestLen) {
                        bestLen = curr[j]
                        bestI = i - curr[j]
                        bestJ = j - curr[j]
                    }
                } else {
                    curr[j] = 0
                }
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return CommonRun(bestLen, bestI, bestJ)
    }

    private const val LCS_MIN_LENGTH = 12
    private const val PENDING_NUDGE_MS = 300L
    private const val SAME_MIN_LENGTH = 3

    private fun cleanText(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            if (cp in 0x20..0x7E) {
                sb.appendCodePoint(cp)
            } else {
                sb.append(' ')
            }
            i += Character.charCount(cp)
        }
        return sb.toString().replace(WHITESPACE_RUN, " ").trim()
    }

    private fun stripControls(cleaned: String): String {
        return CONTROL_PHRASES.replace(cleaned, " ")
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .trimStart { !it.isLetter() }
            .trim()
    }

    private fun extractSpeaker(stripped: String): Pair<String?, String> {
        val lastSpace = stripped.lastIndexOf(' ')
        if (lastSpace < 0) return null to stripped
        val last = stripped.substring(lastSpace + 1)
        if (last.length !in 2..16 || !last[0].isUpperCase() || !last.all { it.isLetter() || it == '\'' }) {
            return null to stripped
        }
        val head = stripped.substring(0, lastSpace).trim()
        if (head.isEmpty()) return null to stripped
        return last to head
    }

    @Volatile
    private var lastSelectedRow: Int? = null

    private val options = ArrayList<String>()
    private var windowStart = 0
    private var pendingUntil = 0L
    private var lastTruth: Int? = null
    private var polarity = 0
    private var rowBeforeNudge: Int? = null
    private var nudgeDirection = 0
    private var selIndex: Int? = null

    @Volatile
    private var stableSpeaker: String? = null

    @Volatile
    private var stableBody: String = ""

    private val WHITESPACE_RUN = Regex("\\s+")
    private val CONTROL_PHRASES = Regex("(?i)\\bto (choose option|confirm|continue)\\b")
    private val CHOICE_NUMBER_PATTERN = Regex("choice_(\\d+)")
    private const val CHOICE_STYLE_SUFFIX = "/style/default/choice"
    private const val CHOICE_GLYPH_MIN = 0xE000
    private const val CHOICE_GLYPH_MAX = 0xE0FF
    private const val CHOICE_MARKER_GLYPH = 0xE090
    private const val BODY_MARKER = "/body_"
    private const val NAMEPLATE_PATH = "/text/nameplate"
    private const val DIALOGUE_FONT_PREFIX = "hud/dialogue/"
    private const val FADE_FONT_PREFIX = "hud/dialogue/effect/fade"

    private const val MAX_CHOICE_LENGTH = 160

    private const val DIALOGUE_TTL_MS = 1500L
}

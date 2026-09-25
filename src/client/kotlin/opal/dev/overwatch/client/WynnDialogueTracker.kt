package opal.dev.overwatch.client

import java.util.Optional
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
    var choices: List<Choice> = emptyList()
        private set

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
        choices = emptyList()
        requiresShift = false
        hasChoices = false
        dialogueAtMillis = 0L
        choiceTexts.clear()
        selNumber = null
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
            choiceTexts.clear()
            selNumber = null
        }
        if (live.isNotEmpty()) {
            for (c in live) {
                val prev = choiceTexts[c.number]
                choiceTexts[c.number] = if (prev == null) c.text else stitch(prev, c.text)
            }

            if (selNumber == null) selNumber = oddOneOut(live) ?: choiceTexts.keys.minOrNull()
        }
        stableSpeaker = speaker
        stableBody = body
        refreshChoices()

        text = cleaned
        dialogueAtMillis = now
        OverwatchGate.noteActionBar()
    }

    fun isChoiceActive(): Boolean {
        if (!hasChoices || choices.isEmpty()) return false
        return dialogueVisible(System.currentTimeMillis())
    }

    fun nudgeSelection(direction: Int) {
        if (direction == 0 || !isChoiceActive()) return
        val numbers = choiceTexts.keys.sorted()
        if (numbers.isEmpty()) return
        val current = selNumber?.let { numbers.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        val next = ((current + direction) % numbers.size + numbers.size) % numbers.size
        selNumber = numbers[next]
        refreshChoices()
    }

    private fun refreshChoices() {
        choices = choiceTexts.entries.sortedBy { it.key }
            .map { Choice(it.value.take(MAX_CHOICE_LENGTH), it.key == selNumber) }
            .filter { it.text.isNotEmpty() }
    }

    private data class NumberedChoice(val number: Int, val text: String, val color: String)

    private data class Visited(val bodyRaw: String, val choices: List<NumberedChoice>, val speakerRaw: String)

    private fun visitParts(message: Component): Visited {
        val body = StringBuilder()
        val speaker = StringBuilder()
        val texts = LinkedHashMap<Int, StringBuilder>()
        val colors = HashMap<Int, String>()
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
        return Visited(body.toString(), choices, speaker.toString())
    }

    private fun oddOneOut(live: List<NumberedChoice>): Int? {
        if (live.isEmpty()) return null
        if (live.size == 1) return live[0].number
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

    private val choiceTexts = LinkedHashMap<Int, String>()
    private var selNumber: Int? = null

    @Volatile
    private var stableSpeaker: String? = null

    @Volatile
    private var stableBody: String = ""

    private val WHITESPACE_RUN = Regex("\\s+")
    private val CONTROL_PHRASES = Regex("(?i)\\bto (choose option|confirm|continue)\\b")
    private val CHOICE_NUMBER_PATTERN = Regex("choice_(\\d+)")
    private const val BODY_MARKER = "/body_"
    private const val NAMEPLATE_PATH = "/text/nameplate"
    private const val DIALOGUE_FONT_PREFIX = "hud/dialogue/"
    private const val FADE_FONT_PREFIX = "hud/dialogue/effect/fade"

    private const val MAX_CHOICE_LENGTH = 160

    private const val DIALOGUE_TTL_MS = 1500L
}

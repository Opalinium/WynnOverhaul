package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnVitalsTracker {

    @Volatile
    var health: Int? = null
        private set

    @Volatile
    var maxHealth: Int? = null
        private set

    @Volatile
    var mana: Int? = null
        private set

    @Volatile
    var maxMana: Int? = null
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onActionBar(message, overlay) }
    }

    fun clear() {
        health = null
        maxHealth = null
        mana = null
        maxMana = null
    }

    fun hasSegment(raw: String): Boolean {
        for (match in SEGMENT_PATTERN.findAll(raw)) {
            val segmentText = match.value
            val startChar = segmentText[0]
            val endChar = segmentText[segmentText.length - 2]
            if (startChar == NEGATIVE_SPACE_HIGH && endChar == POSITIVE_SPACE_HIGH) return true
            if (startChar == POSITIVE_SPACE_HIGH && endChar == NEGATIVE_SPACE_HIGH) return true
        }
        return false
    }

    private fun onActionBar(message: Component, overlay: Boolean) {
        val raw = message.string
        var parsed = false
        for (match in SEGMENT_PATTERN.findAll(raw)) {
            val segmentText = match.value
            val startChar = segmentText[0]
            val endChar = segmentText[segmentText.length - 2]
            val value = match.groups["value"]?.value ?: continue
            val (current, max) = decodeValue(value) ?: continue
            when {
                startChar == NEGATIVE_SPACE_HIGH && endChar == POSITIVE_SPACE_HIGH -> {
                    health = current
                    maxHealth = max
                    parsed = true
                }
                startChar == POSITIVE_SPACE_HIGH && endChar == NEGATIVE_SPACE_HIGH -> {
                    mana = current
                    maxMana = max
                    parsed = true
                }
            }
        }
        if (parsed) OverwatchGate.noteActionBar()
    }

    private fun decodeValue(encoded: String): Pair<Int, Int>? {
        val chunk = encoded.replace(CHAR_SEPARATOR, "").replace(SLASH_SEPARATOR, "")
        val sb = StringBuilder(chunk.length)
        for (ch in chunk) {
            val index = ch - DISPLAY_CHARACTER_START
            if (index !in 0 until DISPLAY_CHARACTER_TRANSLATION.size) return null
            sb.append(DISPLAY_CHARACTER_TRANSLATION[index])
        }
        val match = VALUE_PATTERN.matchEntire(sb) ?: return null
        val current = parseShortNumber(match.groups["current"]?.value ?: return null) ?: return null
        val max = parseShortNumber(match.groups["max"]?.value ?: return null) ?: return null
        if (current > Int.MAX_VALUE || max > Int.MAX_VALUE) return null
        return current.toInt() to max.toInt()
    }

    private fun parseShortNumber(value: String): Long? {
        if (value.isEmpty()) return 0L
        val lastChar = value.last()
        val multiplier = NUMBER_SHORT_MULTIPLIERS[lastChar]
        val digits = if (multiplier == null) value else value.dropLast(1)
        if (digits.isEmpty()) return null
        val parsed = digits.toLongOrNull() ?: return null
        return parsed * (multiplier ?: 1L)
    }

    private const val POSITIVE_SPACE_HIGH = '\uDB00'
    private const val NEGATIVE_SPACE_HIGH = '\uDAFF'
    private const val CHAR_SEPARATOR = "\uDAFF\uDFFF"
    private const val SLASH_SEPARATOR = "\uDB00\uDC02"
    private const val DISPLAY_CHARACTER_START = '\uE010'
    private const val DISPLAY_CHARACTER_END = '\uE01F'

    private val DISPLAY_CHARACTER_TRANSLATION =
        listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "k", "m", "b", "t", ".", "/")

    private val NUMBER_SHORT_MULTIPLIERS =
        mapOf('k' to 1_000L, 'm' to 1_000_000L, 'b' to 1_000_000_000L, 't' to 1_000_000_000_000L)

    private val VALUE_PATTERN = Regex("(?<current>\\d+[kmbt]?)/(?<max>\\d+[kmbt]?)")

    private val SEGMENT_PATTERN = Regex(
        ".(?<value>[" +
            DISPLAY_CHARACTER_START +
            "-" + DISPLAY_CHARACTER_END +
            CHAR_SEPARATOR +
            SLASH_SEPARATOR +
            "]+).",
    )
}
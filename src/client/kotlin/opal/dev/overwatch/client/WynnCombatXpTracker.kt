package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnCombatXpTracker {

    private const val EXPERIENCE_CHAR_START_C = '\uE110'
    private const val EXPERIENCE_CHAR_END_C = '\uE144'

    const val MAX_STEPS = EXPERIENCE_CHAR_END_C - EXPERIENCE_CHAR_START_C + 1

    @Volatile
    var progressSteps: Int? = null
        private set

    val progressFraction: Float?
        get() = progressSteps?.div(MAX_STEPS.toFloat())

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onActionBar(message, overlay) }
    }

    fun clear() {
        progressSteps = null
    }

    fun hasSegment(raw: String): Boolean = EXPERIENCE_PATTERN.containsMatchIn(raw)

    private fun onActionBar(message: Component, overlay: Boolean) {
        val raw = message.string
        val match = EXPERIENCE_PATTERN.find(raw) ?: return
        val value = match.groups["value"]?.value ?: return
        if (value.length != 1) return
        val progress = value[0] - EXPERIENCE_CHAR_START_C
        if (progress !in 0 until MAX_STEPS) return
        progressSteps = progress
        OverwatchGate.noteActionBar()
    }

    private const val SEGMENT_START = "\uDAFF\uDFA7"
    private const val SEGMENT_END = "\uDAFF\uDFA5"

    private val EXPERIENCE_PATTERN = Regex(
        SEGMENT_START + "(?<value>[" + EXPERIENCE_CHAR_START_C + "-" + EXPERIENCE_CHAR_END_C + "])" + SEGMENT_END,
    )
}
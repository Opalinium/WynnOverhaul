package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnSprintTracker {
    const val ACTION_STEPS = 25

    data class Meter(
        val action: String,
        val step: Int,
    ) {
        val fraction: Float
            get() = step.toFloat() / ACTION_STEPS
    }

    @Volatile
    var meter: Meter? = null
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onActionBar(message, overlay) }
    }

    fun clear() {
        meter = null
    }

    fun hasSegment(raw: String): Boolean = METER_PATTERN.containsMatchIn(raw)

    private fun onActionBar(message: Component, overlay: Boolean) {
        val raw = message.string
        val match = METER_PATTERN.find(raw) ?: return
        val value = match.groups["value"]?.value ?: return
        if (value.length != 1) return
        val meterChar = value[0]

        val parsed = when (meterChar) {
            EMPTY_METER_CHARACTER -> Meter("EMPTY", 0)
            FULL_METER_CHARACTER -> Meter("BOTH", ACTION_STEPS)
            else -> {
                val action = when {
                    meterChar in SPRINT_METER_START..SPRINT_METER_END -> SPRINT
                    meterChar in BREATH_METER_START..BREATH_METER_END -> BREATH
                    else -> return
                }
                val firstActionCharacter = if (action == SPRINT) SPRINT_METER_START else BREATH_METER_START
                val actionStep = ACTION_STEPS - (meterChar - firstActionCharacter)
                if (actionStep !in 0..ACTION_STEPS) return
                Meter(action, actionStep)
            }
        }
        meter = parsed
        WynnOverhaulGate.noteActionBar()
    }

    private const val SEGMENT_START = "\uDAFF\uDFF4"
    private const val SEGMENT_END = "\uDAFF\uDFF3"
    private const val SPRINT = "SPRINT"
    private const val BREATH = "BREATH"
    private const val FULL_METER_CHARACTER = '\uE090'
    private const val EMPTY_METER_CHARACTER = '\uE091'
    private const val SPRINT_METER_START = '\uE0A0'
    private const val SPRINT_METER_END = '\uE0B8'
    private const val BREATH_METER_START = '\uE0C0'
    private const val BREATH_METER_END = '\uE0D8'

    private val METER_PATTERN = Regex(
        SEGMENT_START +
            "(?<value>[" + FULL_METER_CHARACTER + "-" + EMPTY_METER_CHARACTER +
            SPRINT_METER_START + "-" + SPRINT_METER_END +
            BREATH_METER_START + "-" + BREATH_METER_END + "])" +
            SEGMENT_END,
    )
}
package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnMountEnergyTracker {
    const val MAX_ENERGY = 48

    @Volatile
    var energy: Int? = null
        private set

    @Volatile
    var energyAtMillis: Long = 0L
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        energy = null
        energyAtMillis = 0L
    }

    fun energyVisible(now: Long): Boolean {
        if (energy == null) return false
        return now - energyAtMillis < ENERGY_TTL_MS
    }

    fun hasSegment(raw: String): Boolean = ENERGY_PATTERN.containsMatchIn(raw)

    private fun onActionBar(message: Component) {
        val raw = message.string
        val match = ENERGY_PATTERN.find(raw) ?: return
        val value = match.groups["energy"]?.value ?: return
        if (value.length != 1) return
        val parsed = EMPTY_CHAR - value[0]
        if (parsed !in 0 until MAX_ENERGY) return
        energy = parsed
        energyAtMillis = System.currentTimeMillis()
        OverwatchGate.noteActionBar()
    }

    private fun c(hex: String): Char = hex.toInt(16).toChar()
    private fun g(hex: String): String = c(hex).toString()

    private val SEGMENT_START: String = g("DB00") + g("DC08")
    private val SEGMENT_END: String = g("DAFF") + g("DFE7")
    private val FULL_CHAR: Char = c("E000")
    private val EMPTY_CHAR: Char = c("E02F")

    private val ENERGY_PATTERN = Regex(
        SEGMENT_START + "(?<energy>[" + FULL_CHAR + "-" + EMPTY_CHAR + "])" + SEGMENT_END,
    )

    private const val ENERGY_TTL_MS = 3000L
}

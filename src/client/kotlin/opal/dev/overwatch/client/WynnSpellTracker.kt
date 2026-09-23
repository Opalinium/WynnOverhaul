package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

object WynnSpellTracker {

    data class Cast(val name: String, val costs: List<WynnSpellSegments.SpellCost>, val atMillis: Long)

    @Volatile
    var lastCast: Cast? = null
        private set

    @Volatile
    var combo: List<WynnSpellSegments.ComboInput>? = null
        private set

    @Volatile
    var comboAtMillis: Long = 0L
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message.string) }
    }

    fun clear() {
        lastCast = null
        combo = null
        comboAtMillis = 0L
    }

    fun castVisible(now: Long): Boolean {
        val cast = lastCast ?: return false
        return now - cast.atMillis < CAST_TTL_MS
    }

    fun comboVisible(now: Long): Boolean {
        if (combo == null) return false
        return now - comboAtMillis < COMBO_TTL_MS
    }

    private fun onActionBar(raw: String) {
        val now = System.currentTimeMillis()
        val cast = WynnSpellSegments.parseCast(raw)
        if (cast != null) {
            lastCast = Cast(cast.name, cast.costs, now)
            combo = null
            OverwatchGate.noteActionBar()
            return
        }
        val glyphs = WynnSpellSegments.parseInputs(raw)
        if (glyphs != null) {
            combo = glyphs.map { WynnSpellSegments.classifyInput(it) }
            comboAtMillis = now
            OverwatchGate.noteActionBar()
        }
    }

    private const val CAST_TTL_MS = 2500L
    private const val COMBO_TTL_MS = 2000L
}

package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnLevelTracker {
    @Volatile
    var level: Int? = null
        private set

    @Volatile
    var iconText: String? = null
        private set

    val HEXAGON_CHAR = 0xE00A.toChar()

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        level = null
        iconText = null
    }

    private fun onActionBar(message: Component) {
        val raw = message.string
        val parsed = findLevel(raw) ?: return
        level = parsed
        WynnOverhaulGate.noteActionBar()
    }

    fun findLevel(raw: String): Int? {
        val match = LEVEL_PATTERN.findAll(raw).lastOrNull { m ->
            val v = m.value
            v.length >= 2 && v[0] == SPACER && v[v.length - 2] == SPACER
        } ?: return null
        val levelGroup = match.groups["level"]?.value ?: return null
        val digits = levelGroup.replace(SEPARATOR, "")
        val sb = StringBuilder()
        for (ch in digits) {
            if (ch in LEVEL_CHAR_START..LEVEL_CHAR_END) sb.append(ch - LEVEL_CHAR_START)
        }
        val parsed = sb.toString().toIntOrNull() ?: return null
        if (parsed !in 1..200) return null
        iconText = levelGroup
        return parsed
    }

    private const val SPACER = '\uDAFF'
    private const val SEPARATOR = "󏿾"
    private const val LEVEL_CHAR_START = ''
    private const val LEVEL_CHAR_END = ''
    private val LEVEL_PATTERN = Regex(""".(?<level>([-]󏿾?){1,6}).""")
}

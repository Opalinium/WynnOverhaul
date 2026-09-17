package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnLevelTracker {

    @Volatile
    var level: Int? = null
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (overlay) onActionBar(message) }
    }

    private fun onActionBar(message: Component) {
        val raw = message.string
        val match = LEVEL_PATTERN.find(raw) ?: return
        val full = match.value
        if (full.length < 2) return
        if (full[0] != SPACER || full[full.length - 2] != SPACER) return
        val levelGroup = match.groups["level"]?.value ?: return
        val digits = levelGroup.replace(SEPARATOR, "")
        val sb = StringBuilder()
        for (ch in digits) {
            if (ch in LEVEL_CHAR_START..LEVEL_CHAR_END) sb.append(ch - LEVEL_CHAR_START)
        }
        level = sb.toString().toIntOrNull() ?: level
    }

    private const val SPACER = '\uDAFF'
    private const val SEPARATOR = "󏿾"
    private const val LEVEL_CHAR_START = ''
    private const val LEVEL_CHAR_END = ''
    private val LEVEL_PATTERN = Regex(""".(?<level>([-]󏿾?){1,6}).""")
}

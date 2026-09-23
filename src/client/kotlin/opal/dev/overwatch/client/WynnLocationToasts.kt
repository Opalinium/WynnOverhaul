package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnLocationToasts {

    private var lastToastedRegion: String? = null
    private var lastToastedAt = 0L
    private var lastDiscoveryName: String? = null
    private var lastDiscoveryAt = 0L
    private var blockUntil = 0L
    private var blockCount = 0

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
    }

    fun reset() {
        lastToastedRegion = null
        lastToastedAt = 0L
        lastDiscoveryName = null
        lastDiscoveryAt = 0L
        blockUntil = 0L
        blockCount = 0
    }

    fun onRegionChanged(previous: String?, name: String) {
        if (previous == null) return
        if (!OverwatchGate.inGame || !OverwatchConfig.current.locationToastEnabled) return
        val now = System.currentTimeMillis()
        if (name.equals(lastDiscoveryName, ignoreCase = true) && now - lastDiscoveryAt < DISCOVERY_DEDUP_MILLIS) return
        if (name.equals(lastToastedRegion, ignoreCase = true) && now - lastToastedAt < REPEAT_COOLDOWN_MILLIS) return
        lastToastedRegion = name
        lastToastedAt = now
        OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LOCATION, ENTERING_TITLE, name, LOCATION_COLOR))
    }

    private fun onMessage(message: Component): Boolean {
        if (!OverwatchGate.inGame) return true
        if (!OverwatchConfig.current.discoveryToastEnabled) return true
        val now = System.currentTimeMillis()
        val lines = clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }

        val header = lines.firstNotNullOfOrNull { DISCOVERY.matchEntire(it) }
        if (header != null) {
            val kind = header.groupValues[1]
            val name = header.groupValues[2].trim()
            val xp = header.groupValues[3]
            val detail = if (xp.isEmpty()) "" else "+$xp XP"
            lastDiscoveryName = name
            lastDiscoveryAt = now
            OverwatchToastQueue.dropPending { it.title == ENTERING_TITLE && it.subtitle.equals(name, ignoreCase = true) }
            OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.DISCOVERY, "$kind Discovered", name, DISCOVERY_COLOR, detail))
            blockUntil = now + CONTINUATION_MILLIS
            blockCount = 0
            return false
        }

        if (now < blockUntil && blockCount < MAX_CONTINUATION_MESSAGES && !isOtherKnownBlock(lines)) {
            blockCount++
            blockUntil = now + CONTINUATION_MILLIS
            return false
        }
        return true
    }

    private fun isOtherKnownBlock(lines: List<String>): Boolean =
        lines.any { it == QUEST_HEADER || it == LEVEL_UP_HEADER }

    private fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '§') {
                i += 2
                continue
            }
            val cp = text.codePointAt(i)
            if (cp < 0xE000 && cp != 0xFFFD) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private const val ENTERING_TITLE = "Entering"
    private const val QUEST_HEADER = "[Quest Completed]"
    private const val LEVEL_UP_HEADER = "Level Up!"
    private const val DISCOVERY_COLOR = 0xFFFFAA00.toInt()
    private const val LOCATION_COLOR = 0xFFE6D3A0.toInt()
    private const val CONTINUATION_MILLIS = 150L
    private const val MAX_CONTINUATION_MESSAGES = 8
    private const val DISCOVERY_DEDUP_MILLIS = 6_000L
    private const val REPEAT_COOLDOWN_MILLIS = 20_000L
    private val DISCOVERY = Regex("""^([A-Za-z]+) Discovered:\s*(.+?)\s*(?:\(\+?([\d,]+)\s*XP\))?$""")
}

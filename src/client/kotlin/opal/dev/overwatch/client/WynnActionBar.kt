package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import opal.dev.overwatch.Overwatch
import java.util.Optional

object WynnActionBar {
    private var leftoverSource: Component? = null
    private var leftoverResult: Component? = null

    private var lastLogged = ""

    fun registerDebug() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay || !OverwatchConfig.current.debugActionBarLog) return@register
            val raw = message.string
            if (raw == lastLogged) return@register
            lastLogged = raw
            val encoded = StringBuilder()
            for (cp in raw.codePoints()) {
                if (cp in 0x20..0x7E) encoded.appendCodePoint(cp) else encoded.append("<U+").append(Integer.toHexString(cp).uppercase()).append('>')
            }
            Overwatch.LOGGER.info("[ActionBar] {}", encoded)
        }
    }

    fun isComposite(message: Component): Boolean {
        val raw = message.string
        if (!hasPrivateUse(raw)) return false
        if (isPersistentComposite(raw)) return true

        return WynnDialogueTracker.isDialogue(message)
    }

    fun leftover(message: Component): Component? {
        if (message === leftoverSource) return leftoverResult
        leftoverSource = message
        leftoverResult = if (WynnDialogueTracker.isDialogue(message)) null else buildLeftover(message)
        return leftoverResult
    }

    private fun buildLeftover(message: Component): Component? {
        val raw = message.string
        val keep = BooleanArray(raw.length)
        var any = false
        for (match in ULTIMATE_SEGMENT.findAll(raw)) {
            for (i in match.range) keep[i] = true
            any = true
        }
        if (!any) return null

        val result = Component.empty()
        var offset = 0
        var pending = StringBuilder()
        var pendingStyle: Style? = null

        fun flush() {
            val style = pendingStyle
            if (style != null && pending.isNotEmpty()) result.append(Component.literal(pending.toString()).withStyle(style))
            pending = StringBuilder()
            pendingStyle = null
        }

        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                for (ch in text) {
                    val kept = offset < keep.size && keep[offset]
                    offset++
                    if (!kept) continue
                    if (pendingStyle != style) {
                        flush()
                        pendingStyle = style
                    }
                    pending.append(ch)
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        flush()
        return result
    }

    private fun isPersistentComposite(raw: String): Boolean {
        val parsed = WynnLevelTracker.findLevel(raw) ?: return false
        val known = WynnLevelTracker.level
        if (known != null && parsed != known) return false
        var families = 0
        if (WynnVitalsTracker.hasSegment(raw)) families++
        if (WynnSprintTracker.hasSegment(raw)) families++
        if (WynnCombatXpTracker.hasSegment(raw)) families++
        return families >= 2
    }

    private fun hasPrivateUse(text: String): Boolean {
        for (i in text.indices) {
            val c = text[i]
            if ((c >= '' && c <= '') || Character.isSurrogate(c)) return true
        }
        return false
    }

    private val ULTIMATE_SEGMENT = Regex("\uDAFF\uDFE8[\uE4F0-\uE52F\uE190-\uE19C]+\uDAFF\uDFE7")
}

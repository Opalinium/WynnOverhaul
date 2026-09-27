package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import opal.dev.wynnoverhaul.WynnOverhaul
import java.util.Optional

object WynnActionBar {
    private var leftoverSource: Component? = null
    private var leftoverResult: Component? = null

    private var lastLogged = ""

    fun registerDebug() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay || !WynnOverhaulConfig.current.debugActionBarLog) return@register
            val raw = message.string
            if (raw == lastLogged) return@register
            lastLogged = raw
            val encoded = StringBuilder()
            for (cp in raw.codePoints()) {
                if (cp in 0x20..0x7E) encoded.appendCodePoint(cp) else encoded.append("<U+").append(Integer.toHexString(cp).uppercase()).append('>')
            }
            WynnOverhaul.LOGGER.info("[ActionBar] {}", encoded)
        }
    }

    fun isComposite(message: Component): Boolean {
        val raw = message.string
        if (!hasPrivateUse(raw)) return false
        if (isPersistentComposite(raw)) return true

        return WynnDialogueTracker.isDialogue(message)
    }

    fun hasUltimateSegment(raw: String): Boolean = ULTIMATE_SEGMENT.containsMatchIn(raw)

    fun withoutUltimates(message: Component): Component = project(message, keepUltimate = false)

    fun isBlankOverlay(component: Component): Boolean = component.string.none { it.isLetterOrDigit() }

    fun leftover(message: Component): Component? {
        if (message === leftoverSource) return leftoverResult
        leftoverSource = message
        leftoverResult = if (WynnDialogueTracker.isDialogue(message)) null else buildLeftover(message)
        return leftoverResult
    }

    private var lastCaptureLog = ""
    private var lastCaptureAt = 0L

    private fun logCapture(raw: String, keep: BooleanArray) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureAt < 5000) return
        val parts = ArrayList<String>()
        var i = 0
        while (i < raw.length && parts.size < 16) {
            if (i < keep.size && keep[i]) parts.add("U+%04X".format(raw[i].code))
            i++
        }
        val summary = parts.joinToString(" ")
        if (summary == lastCaptureLog) return
        lastCaptureLog = summary
        lastCaptureAt = now
        WynnOverhaul.LOGGER.info("[Ultimate] captured: {}", summary)
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
        logCapture(raw, keep)

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

        return project(message, keepUltimate = true)
    }

    private fun project(message: Component, keepUltimate: Boolean): Component {
        val raw = message.string
        val mark = BooleanArray(raw.length)
        for (match in ULTIMATE_SEGMENT.findAll(raw)) for (i in match.range) mark[i] = true
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
                    val kept = offset < mark.size && (mark[offset] == keepUltimate)
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

    private val ULTIMATE_SEGMENT = Regex("\uDAFF\uDFFA\uE4E0\uDAFF\uDFF6")
}

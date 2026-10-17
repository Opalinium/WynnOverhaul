package opal.dev.wynnoverhaul.client

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

object ChatKinds {
    enum class Kind { PUBLIC, PARTY, GUILD, PRIVATE, SHOUT, PETS, DIALOGUE, SYSTEM }

    private const val TAB_MARK = "󏿼"
    private const val TAB_TAIL = "󏿿󏿾"
    private const val TAB_ALT = "󐀆"
    private val NUMBERED_DIALOGUE = Regex("""^\[\d+/\d+]""")

    fun classify(message: Component): Kind {
        val text = message.string
        if (text.isBlank()) return Kind.SYSTEM
        if (isPublic(text)) return Kind.PUBLIC
        if (text.startsWith(TAB_MARK)) {
            val after = text.substring(TAB_MARK.length)
            when {
                after.startsWith("$TAB_TAIL") -> return Kind.PARTY
                after.startsWith("$TAB_TAIL") -> return Kind.GUILD
                after.startsWith("$TAB_TAIL") -> return Kind.PRIVATE
                after.startsWith("$TAB_TAIL") -> return Kind.SHOUT
                after.startsWith("$TAB_TAIL") -> return Kind.PETS
                after.startsWith(TAB_ALT) -> return byColor(firstColor(message))
            }
        }
        if (WynnDirectMessages.parse(text) != null) return Kind.PRIVATE
        if (NUMBERED_DIALOGUE.containsMatchIn(text.trim())) return Kind.DIALOGUE
        return Kind.SYSTEM
    }

    fun channelOf(kind: Kind): WynnChatChannels.Channel? = when (kind) {
        Kind.PUBLIC, Kind.SHOUT -> WynnChatChannels.Channel.GLOBAL
        Kind.PARTY -> WynnChatChannels.Channel.PARTY
        Kind.GUILD -> WynnChatChannels.Channel.GUILD
        Kind.PRIVATE, Kind.PETS, Kind.DIALOGUE, Kind.SYSTEM -> null
    }

    fun isSystemLog(kind: Kind): Boolean = kind == Kind.SYSTEM || kind == Kind.PETS || kind == Kind.DIALOGUE

    fun isChat(kind: Kind): Boolean = kind != Kind.SYSTEM && kind != Kind.DIALOGUE

    fun fromSelf(message: Component, name: String): Boolean {
        if (name.isEmpty()) return false
        val text = TextClean.clean(message.string)
        val colon = text.indexOf(':')
        return colon in 1..60 && text.substring(0, colon).contains(name, ignoreCase = true)
    }

    private fun isPublic(text: String): Boolean {
        if (text.length < 3) return false
        if (text[0] !in ''..'' || text[1] !in ''..'') return false
        var i = 2
        while (i < text.length && i < 5 && text[i] in ''..'') i++
        return i > 2
    }

    private fun byColor(rgb: Int?): Kind = when (rgb) {
        0xFFFF55 -> Kind.PARTY
        0x55FFFF -> Kind.GUILD
        0xDDCC99 -> Kind.PRIVATE
        0xBD45FF -> Kind.SHOUT
        0xFFAA00 -> Kind.PETS
        else -> Kind.SYSTEM
    }

    private fun firstColor(message: Component): Int? {
        var color: Int? = null
        var done = false
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                if (!done && string.isNotBlank()) {
                    done = true
                    color = style.color?.value
                    return@StyledContentConsumer Optional.of(Unit)
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return color
    }
}

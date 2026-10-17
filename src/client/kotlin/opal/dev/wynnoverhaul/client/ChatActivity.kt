package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.sounds.SoundEvents
import java.util.Optional

object ChatActivity {
    private val unread = HashMap<WynnChatChannels.Channel, Int>()
    private var mentionPattern: Regex? = null
    private var mentionSource = ""
    private var lastMentionSoundMillis = 0L
    private lateinit var replyKey: KeyMapping

    fun register() {
        replyKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.wynnoverhaul.chat_reply", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, WynnOverhaulKeyCategory.CATEGORY),
        )
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay -> if (overlay) message else highlight(message) }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) countUnread(message) }
    }

    fun tick(client: Minecraft) {
        while (replyKey.consumeClick()) {
            if (client.gui.screen() == null && WynnOverhaulGate.inGame) reply(client)
        }
    }

    fun unread(channel: WynnChatChannels.Channel): Int = unread[channel] ?: 0

    fun clear(channel: WynnChatChannels.Channel) {
        unread.remove(channel)
    }

    fun clearAll() {
        unread.clear()
    }

    private fun reply(client: Minecraft) {
        val partner = WynnDirectMessages.lastIncoming
        if (partner != null) {
            WynnChatChannels.selectDirect(partner)
            client.setScreenAndShow(ChatScreen("", false))
        } else {
            client.setScreenAndShow(ChatScreen("/r ", false))
        }
    }

    private fun countUnread(message: Component) {
        if (!WynnOverhaulGate.onWynncraft) return
        val kind = ChatKinds.classify(message)
        val channel = ChatKinds.channelOf(kind) ?: return
        val client = Minecraft.getInstance()
        if (ChatKinds.fromSelf(message, client.player?.gameProfile?.name.orEmpty())) return
        bump(channel)
    }

    fun bump(channel: WynnChatChannels.Channel) {
        val client = Minecraft.getInstance()
        val viewing = client.gui.screen() is ChatScreen && WynnChatChannels.directTarget == null &&
            (WynnChatChannels.current == channel || WynnChatChannels.current == WynnChatChannels.Channel.ALL)
        if (!viewing) unread[channel] = (unread[channel] ?: 0) + 1
    }

    private fun highlight(message: Component): Component {
        val config = WynnOverhaulConfig.current
        if (!config.chatMentionsEnabled || !WynnOverhaulGate.onWynncraft) return message
        val client = Minecraft.getInstance()
        val me = client.player?.gameProfile?.name.orEmpty()
        val pattern = patternFor(me, config.chatMentionKeywords) ?: return message
        if (!ChatKinds.isChat(ChatKinds.classify(message))) return message
        if (ChatKinds.fromSelf(message, me)) return message
        val pieces = ArrayList<Pair<Style, String>>()
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                pieces.add(style to string)
                Optional.empty()
            },
            Style.EMPTY,
        )
        if (pieces.none { pattern.containsMatchIn(it.second) }) return message
        val accent = TextColor.fromRgb(config.chatMentionColor and 0xFFFFFF)
        val out = Component.empty()
        for ((style, string) in pieces) {
            var last = 0
            for (match in pattern.findAll(string)) {
                if (match.range.first > last) out.append(Component.literal(string.substring(last, match.range.first)).setStyle(style))
                out.append(Component.literal(match.value).setStyle(style.withBold(true).withColor(accent)))
                last = match.range.last + 1
            }
            if (last < string.length) out.append(Component.literal(string.substring(last)).setStyle(style))
        }
        if (config.chatMentionSound) playMention(client)
        return out
    }

    private fun playMention(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (now - lastMentionSoundMillis < SOUND_COOLDOWN_MILLIS) return
        lastMentionSoundMillis = now
        client.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.3f, 0.7f))
    }

    private fun patternFor(name: String, keywords: String): Regex? {
        val source = "$name|$keywords"
        if (source == mentionSource) return mentionPattern
        mentionSource = source
        val words = (listOf(name) + keywords.split(',')).map { it.trim() }.filter { it.length >= 2 }.distinct()
        mentionPattern = if (words.isEmpty()) null else Regex(
            "(?<![A-Za-z0-9_])(" + words.joinToString("|") { Regex.escape(it) } + ")(?![A-Za-z0-9_])",
            RegexOption.IGNORE_CASE,
        )
        return mentionPattern
    }

    private const val SOUND_COOLDOWN_MILLIS = 400L
}

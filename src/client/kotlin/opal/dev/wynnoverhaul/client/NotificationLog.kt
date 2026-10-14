package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component

object NotificationLog {
    private const val KEEP = 400
    private val recorded = ArrayDeque<Component>()

    fun record(toast: WynnOverhaulToastQueue.Toast) {
        if (toast.kind == WynnOverhaulToastQueue.Kind.LOCATION || !WynnOverhaulGate.onWynncraft) return
        val line = Component.empty().append(Component.literal(toast.title).withColor(toast.colorArgb and 0xFFFFFF))
        if (toast.subtitle.isNotBlank()) line.append(Component.literal(": ${toast.subtitle}").withColor(NEUTRAL))
        if (toast.detail.isNotBlank()) line.append(Component.literal(" (${toast.detail})").withColor(MUTED))
        Minecraft.getInstance().execute {
            synchronized(recorded) {
                recorded.addLast(line)
                while (recorded.size > KEEP) recorded.removeFirst()
            }
            val client = Minecraft.getInstance()
            WynnDirectMessages.applyView(client.gui.screen() is ChatScreen)
            client.gui.hud.chat.addClientSystemMessage(line)
            ChatActivity.bump(WynnChatChannels.Channel.SYSTEM)
        }
    }

    fun isNotification(message: Component): Boolean = synchronized(recorded) { recorded.any { it === message } }

    private const val NEUTRAL = 0xE6E6E6
    private const val MUTED = 0xA0A0A0
}

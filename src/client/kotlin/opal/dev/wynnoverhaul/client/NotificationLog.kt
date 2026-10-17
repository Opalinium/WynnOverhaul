package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object NotificationLog {
    fun record(toast: WynnOverhaulToastQueue.Toast) {
        if (toast.kind == WynnOverhaulToastQueue.Kind.LOCATION || !WynnOverhaulGate.onWynncraft) return
        val line = Component.empty().append(Component.literal(toast.title).withColor(toast.colorArgb and 0xFFFFFF))
        if (toast.subtitle.isNotBlank()) line.append(Component.literal(": ${toast.subtitle}").withColor(NEUTRAL))
        if (toast.detail.isNotBlank()) line.append(Component.literal(" (${toast.detail})").withColor(MUTED))
        Minecraft.getInstance().execute { ChatSysPanel.add(line, true) }
    }

    private const val NEUTRAL = 0xE6E6E6
    private const val MUTED = 0xA0A0A0
}

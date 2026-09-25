package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnQuestCompletionTracker {
    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
    }

    private var pendingHeader = false
    private var suppressPendingContinuation = false

    private fun onMessage(message: Component): Boolean {
        if (!OverwatchGate.inGame) return true
        val lines = TextClean.clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return true

        val headerIndex = lines.indexOf(HEADER)
        if (headerIndex >= 0) {
            val suppress = OverwatchConfig.current.questCompletionToastEnabled
            if (headerIndex + 1 < lines.size) {
                pendingHeader = false
                onQuestCompleted(lines[headerIndex + 1], suppress)
            } else {
                pendingHeader = true
                suppressPendingContinuation = suppress
            }
            return !suppress
        }

        if (pendingHeader) {
            pendingHeader = false
            val suppress = suppressPendingContinuation
            onQuestCompleted(lines[0], suppress)
            return !suppress
        }
        return true
    }

    private fun onQuestCompleted(name: String, toast: Boolean) {
        val updated = ContentBookCache.markCompleted(name)
        if (updated != null) {
            val screen = Minecraft.getInstance().gui.screen()
            if (screen is OverwatchInventoryScreen) screen.updateBookActivities(updated)
        }
        if (toast) {
            OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.QUEST, "Quest Completed", name, TOAST_COLOR))
        }
    }

    private const val HEADER = "[Quest Completed]"
    private const val TOAST_COLOR = 0xFF55FF55.toInt()
}

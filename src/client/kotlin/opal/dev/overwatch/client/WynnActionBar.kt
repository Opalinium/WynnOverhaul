package opal.dev.overwatch.client

import net.minecraft.network.chat.Component

object WynnActionBar {
    fun isComposite(message: Component): Boolean {
        val raw = message.string
        if (!hasPrivateUse(raw)) return false
        if (isPersistentComposite(raw)) return true
        return WynnDialogueTracker.isDialogue(message)
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
            if ((c >= '\uE000' && c <= '\uF8FF') || Character.isSurrogate(c)) return true
        }
        return false
    }
}

package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import opal.dev.overwatch.Overwatch

object WynnLevelUpToastOverride {
    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
        ClientTickEvents.END_CLIENT_TICK.register { flushPending(false) }
    }

    private class Pending(val startedAt: Long, val lines: MutableList<String>, val headerOnly: Boolean) {
        var followUps = 0
    }

    private var pending: Pending? = null

    private fun flushPending(force: Boolean) {
        val p = pending ?: return
        if (!force && System.currentTimeMillis() - p.startedAt < FOLLOW_UP_WINDOW_MILLIS) return
        pending = null
        val pet = p.lines.any { PET_HINT.containsMatchIn(it) } || (p.headerOnly && p.followUps > 0)
        Overwatch.LOGGER.info("Level-up message lines (pet={}): {}", pet, p.lines)
        if (pet) petToast(p.lines) else richLevelUpToast(p.lines)
    }

    private fun petToast(lines: List<String>) {
        val leveled = lines.firstNotNullOfOrNull { PET_LEVELED.find(it) }
        val unlocks = lines.mapNotNull { UNLOCK_LINE.matchEntire(it)?.groupValues?.get(1)?.trim() }.map { "+ $it" }
        val progress = lines.firstNotNullOfOrNull { PROGRESS_LINE.matchEntire(it)?.value }.orEmpty()
        val subtitle = when {
            leveled != null -> "${leveled.groupValues[1].trim()} reached level ${leveled.groupValues[2]}"
            unlocks.isNotEmpty() -> unlocks.joinToString(" · ")
            else -> "Your pet leveled up!"
        }
        OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, "Pet Level Up!", subtitle, PET_TOAST_COLOR, progress))
    }

    private fun onMessage(message: Component): Boolean {
        if (!OverwatchGate.inGame) return true
        if (!OverwatchConfig.current.levelUpToastEnabled) return true
        val lines = TextClean.clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val open = pending
        if (open != null) {
            if (System.currentTimeMillis() - open.startedAt >= FOLLOW_UP_WINDOW_MILLIS) {
                flushPending(true)
            } else if (lines.isEmpty() || lines.all { PROGRESS_LINE.matches(it) || UNLOCK_LINE.matches(it) }) {
                if (lines.isNotEmpty()) {
                    open.lines.addAll(lines)
                    open.followUps++
                }
                return false
            }
        }
        if (RICH_HEADER in lines) {
            flushPending(true)
            val complete = lines.any { PROGRESS_LINE.matches(it) || UNLOCK_LINE.matches(it) || REWARD_LINE.matches(it) }
            if (complete && lines.none { PET_HINT.containsMatchIn(it) }) {
                richLevelUpToast(lines)
                return false
            }
            pending = Pending(System.currentTimeMillis(), lines.toMutableList(), headerOnly = !complete)
            return false
        }
        val text = TextClean.clean(message.string)

        PERSONAL_COMBAT.matchEntire(text)?.let { m ->
            personalToast(m.groupValues[1].trim().trimEnd('!'))
            return false
        }

        PERSONAL_PROFESSION.matchEntire(text)?.let { m ->
            val profession = m.groupValues[2].replace(Regex("^[^A-Za-z]+"), "").trim()
            OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, "Level Up!", "You reached $profession level ${m.groupValues[1]}!", TOAST_COLOR))
            return false
        }

        PERSONAL_GENERIC.matchEntire(text)?.let { m ->
            val name = m.groupValues[1].trim()
            val level = m.groupValues[2].trim().trimEnd('!')
            if (name == Minecraft.getInstance().player?.name?.string) {
                personalToast(level)
            } else {
                OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, name, "reached $level", OTHER_TOAST_COLOR))
            }
            return false
        }
        BROADCAST_LEVEL_UP.matchEntire(text)?.let { m ->
            val name = m.groupValues[1].trim()
            val level = m.groupValues[2].trim().trimEnd('!')
            if (name == Minecraft.getInstance().player?.name?.string) {
                val now = System.currentTimeMillis()
                if (level != lastPersonalLevel || now - lastPersonalAt > SELF_BROADCAST_DEDUP_MILLIS) {
                    personalToast(level)
                }
            } else {
                OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, name, "reached $level", OTHER_TOAST_COLOR))
            }
            return false
        }
        return true
    }

    private fun personalToast(level: String) {
        lastPersonalLevel = level
        lastPersonalAt = System.currentTimeMillis()
        OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, "Level Up!", "You reached $level!", TOAST_COLOR))
    }

    private fun richLevelUpToast(lines: List<String>) {
        lastPersonalAt = System.currentTimeMillis()
        val rewards = lines.mapNotNull { REWARD_LINE.matchEntire(it) }
            .map { "+${it.groupValues[1]} ${it.groupValues[2]}" }
        val unlocks = lines.mapNotNull { UNLOCK_LINE.matchEntire(it)?.groupValues?.get(1)?.trim() }
            .map { "+ $it" }
        val progress = lines.firstNotNullOfOrNull { PROGRESS_LINE.matchEntire(it)?.value }.orEmpty()
        val subtitle = (rewards + unlocks).joinToString(" · ").ifEmpty {
            lines.getOrNull(lines.indexOf(RICH_HEADER) + 1)?.takeIf { it != RICH_HEADER } ?: "Level up!"
        }
        OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, RICH_HEADER, subtitle, TOAST_COLOR, progress))
    }

    private const val TOAST_COLOR = 0xFFFFD700.toInt()
    private const val OTHER_TOAST_COLOR = 0xFF8FA3B8.toInt()
    private var lastPersonalLevel: String? = null
    private var lastPersonalAt: Long = 0L
    private const val SELF_BROADCAST_DEDUP_MILLIS = 15_000L
    private val PERSONAL_COMBAT = Regex("""^You are now (combat level \d+)!?$""")
    private val PERSONAL_PROFESSION = Regex("""^You are now level (\d+) in (.+)$""")
    private val PERSONAL_GENERIC = Regex("""^(.+) is now ((?:combat )?level .+?)(?: in .+)?!?$""")
    private val BROADCAST_LEVEL_UP = Regex("""^(?:\[!]|!!) Congratulations to (.+) for reaching ((?:combat )?level .+?)!$""")
    private const val FOLLOW_UP_WINDOW_MILLIS = 700L
    private const val PET_TOAST_COLOR = 0xFF5FD6A6.toInt()
    private val PET_HINT = Regex("""(?i)\bpet\b""")
    private val PET_LEVELED = Regex("""^(?:Your )?(.+?) (?:has )?level(?:l)?ed up to level (\d+)!?$""")
    private const val RICH_HEADER = "Level Up!"
    private val REWARD_LINE = Regex("""^-\s*\+([\d,]+)\s+(Experience Points|Emeralds)$""")
    private val UNLOCK_LINE = Regex("""^\+\s*(\D.*)$""")
    private val PROGRESS_LINE = Regex("""^\d+ more levels? until .+$""")
}

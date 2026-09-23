package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnLevelUpToastOverride {

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
    }

    private fun onMessage(message: Component): Boolean {
        if (!OverwatchGate.inGame) return true
        if (!OverwatchConfig.current.levelUpToastEnabled) return true
        val lines = clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (RICH_HEADER in lines) {
            richLevelUpToast(lines)
            return false
        }
        val text = clean(message.string)
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
            .joinToString(" · ") { "+${it.groupValues[1]} ${it.groupValues[2]}" }
        val subtitle = rewards.ifEmpty {
            lines.getOrNull(lines.indexOf(RICH_HEADER) + 1)?.takeIf { it != RICH_HEADER } ?: "Level up!"
        }
        OverwatchToastQueue.show(OverwatchToastQueue.make(OverwatchToastQueue.Kind.LEVEL_UP, RICH_HEADER, subtitle, TOAST_COLOR))
    }

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
        return sb.toString().trim()
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
    private const val RICH_HEADER = "Level Up!"
    private val REWARD_LINE = Regex("""^-\s*\+([\d,]+)\s+(Experience Points|Emeralds)$""")
}

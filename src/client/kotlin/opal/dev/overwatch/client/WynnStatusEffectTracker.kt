package opal.dev.overwatch.client

import net.minecraft.network.chat.Component

object WynnStatusEffectTracker {
    data class Cooldown(val name: String, val remainingSeconds: Int, val maxSeconds: Int, val receivedAtMillis: Long) {
        fun displaySeconds(): Int {
            val elapsed = ((System.currentTimeMillis() - receivedAtMillis) / 1000L).toInt()
            return (remainingSeconds - elapsed).coerceIn(0, maxSeconds.coerceAtLeast(remainingSeconds))
        }

        fun displayFraction(): Float {
            if (maxSeconds <= 0) return 0f
            return (displaySeconds().toFloat() / maxSeconds).coerceIn(0f, 1f)
        }
    }

    @Volatile
    var activeCooldowns: List<Cooldown> = emptyList()
        private set

    private val maxSecondsByName = HashMap<String, Int>()

    fun onFooterUpdate(footer: Component) {
        val text = clean(footer.string)
        if (!text.contains(STATUS_EFFECTS_TITLE)) {
            if (activeCooldowns.isNotEmpty()) activeCooldowns = emptyList()
            maxSecondsByName.clear()
            return
        }

        val now = System.currentTimeMillis()
        val seenNames = HashSet<String>()
        val cooldowns = ArrayList<Cooldown>()
        for (match in COOLDOWN_PATTERN.findAll(text)) {
            val name = match.groups["name"]?.value?.trim() ?: continue
            if (name.isEmpty()) continue
            val minutesText = match.groups["minutes"]?.value ?: continue
            val secondsText = match.groups["seconds"]?.value ?: continue
            if (minutesText.contains('*') || secondsText.contains('*')) continue
            val hours = match.groups["hours"]?.value?.toIntOrNull() ?: 0
            val remaining = hours * 3600 + minutesText.toInt() * 60 + secondsText.toInt() + 1

            seenNames.add(name)
            val max = maxOf(remaining, maxSecondsByName[name] ?: 0)
            maxSecondsByName[name] = max
            cooldowns.add(Cooldown(name, remaining, max, now))
        }
        maxSecondsByName.keys.retainAll(seenNames)
        activeCooldowns = cooldowns
    }

    private fun clean(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '§') {
                i += 2
                continue
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    private const val STATUS_EFFECTS_TITLE = "Status Effects"
    private val COOLDOWN_PATTERN = Regex(
        """⬤\s+(?<name>[A-Za-z0-9'][A-Za-z0-9' ]*?)\s*\((?:(?<hours>\d{2}):)?(?<minutes>\d{2}|\*{2}):(?<seconds>\d{2}|\*{2})\)""",
    )
}

package opal.dev.wynnoverhaul.client

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

    data class Effect(val name: String, val modifier: String, val remainingSeconds: Int, val indefinite: Boolean, val receivedAtMillis: Long) {
        fun displaySeconds(): Int {
            val elapsed = ((System.currentTimeMillis() - receivedAtMillis) / 1000L).toInt()
            return (remainingSeconds - elapsed).coerceAtLeast(0)
        }
    }

    @Volatile
    var effects: List<Effect> = emptyList()
        private set

    @Volatile
    var activeCooldowns: List<Cooldown> = emptyList()
        private set

    private val maxSecondsByName = HashMap<String, Int>()

    @Volatile
    var lastFooter: Component? = null
        private set

    fun onFooterUpdate(footer: Component) {
        lastFooter = footer
        val text = TextClean.clean(footer.string)
        if (!text.contains(STATUS_EFFECTS_TITLE)) {
            if (activeCooldowns.isNotEmpty()) activeCooldowns = emptyList()
            if (effects.isNotEmpty()) effects = emptyList()
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

        val body = text.substring(text.indexOf(STATUS_EFFECTS_TITLE) + STATUS_EFFECTS_TITLE.length)
        val found = ArrayList<Effect>()
        for (match in EFFECT_PATTERN.findAll(body)) {
            if (match.groups["icon"]?.value?.contains(COOLDOWN_ICON) == true) continue
            val name = match.groups["name"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val minutesText = match.groups["minutes"]?.value ?: continue
            val secondsText = match.groups["seconds"]?.value ?: continue
            val modifier = match.groups["modifier"]?.value?.trim().orEmpty()
            if (minutesText.contains('*') || secondsText.contains('*')) {
                found.add(Effect(name, modifier, 0, true, now))
                continue
            }
            val hours = match.groups["hours"]?.value?.toIntOrNull() ?: 0
            found.add(Effect(name, modifier, hours * 3600 + minutesText.toInt() * 60 + secondsText.toInt() + 1, false, now))
        }
        effects = found
    }

    private const val STATUS_EFFECTS_TITLE = "Status Effects"
    private val COOLDOWN_PATTERN = Regex(
        """⬤\s+(?<name>[A-Za-z0-9'][A-Za-z0-9' ]*?)\s*\((?:(?<hours>\d{2}):)?(?<minutes>\d{2}|\*{2}):(?<seconds>\d{2}|\*{2})\)""",
    )

    private const val COOLDOWN_ICON = '⬤'
    private val EFFECT_PATTERN = Regex(
        """(?<modifier>[+-]?\d+(?:\.\d+)?(?:%|/\d+s)\s+|[+-]\d+(?:\.\d+)?\s+)?(?<icon>[^A-Za-z0-9\s(+'-]*)\s*(?<name>[A-Za-z0-9][A-Za-z0-9' ]*?)\s*\((?:(?<hours>\d{2}):)?(?<minutes>\d{2}|\*{2}):(?<seconds>\d{2}|\*{2})\)""",
    )
}

package opal.dev.wynnoverhaul.client

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

object WynnWeapons {
    private val HITS_PER_SEC = Regex("""(\d+(?:\.\d+)?)\s*(?:hits?|atks?|attacks?)\s*/\s*s""", RegexOption.IGNORE_CASE)

    private val TIERS: List<Pair<String, Double>> = listOf(
        "super fast" to 4.3,
        "very fast" to 3.1,
        "fast" to 2.5,
        "normal" to 2.05,
        "slow" to 1.5,
        "very slow" to 0.83,
        "super slow" to 0.51,
    )

    fun attacksPerSecond(stack: ItemStack): Double? {
        if (stack.isEmpty) return null
        val lore = stack.get(DataComponents.LORE) ?: return null
        val lines = lore.lines().map { it.string }

        for (line in lines) {
            HITS_PER_SEC.find(line)?.let { m ->
                val v = m.groupValues[1].toDoubleOrNull()
                if (v != null && v in 0.1..20.0) return v
            }
        }
        for (line in lines) {
            val lc = line.trim().lowercase()
            val tier = if (lc.startsWith("attack speed")) lc.removePrefix("attack speed").trim(':', ' ') else lc
            TIERS.firstOrNull { tier == it.first }?.let { return it.second }
        }
        return null
    }
}

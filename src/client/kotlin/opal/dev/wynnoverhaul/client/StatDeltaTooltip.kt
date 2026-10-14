package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.roundToLong

object StatDeltaTooltip {
    private class Stat(val name: String, val value: Double, val unit: String)

    private val LINE = Regex("""^([+-]\d[\d,]*(?:\.\d+)?)(%|/\d+[sm])?\s+([A-Za-z][A-Za-z' -]*?)\s*$""")
    private val RANGE = Regex("""^([A-Za-z][A-Za-z' -]*?)\s*([+-]\d[\d,]*)(%?)\s+to\s+([+-]\d[\d,]*)%?\s*$""")

    private val BADGE = Regex("""\s*\[\d+%]\s*$""")

    private const val GAIN = 0x55FF55
    private const val LOSS = 0xFF5555

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val config = WynnOverhaulConfig.current
            if (!config.equipComparisonEnabled || !config.equipCompareDeltas || !WynnOverhaulGate.inGame) return@register
            if (stack.isEmpty || lines.size < 2 || EquipCompareTooltip.building) return@register
            val target = EquipCompareTooltip.primaryTarget(stack) ?: return@register
            apply(lines, target.first, target.second)
        }
    }

    private fun apply(lines: MutableList<Component>, label: String, base: ItemStack) {
        val baseStats = LinkedHashMap<String, Stat>()
        for (line in WynnItemRarity.loreLines(base)) parse(line)?.let { baseStats.putIfAbsent(it.name.lowercase(), it) }
        if (baseStats.isEmpty()) return
        val matched = HashSet<String>()
        var changed = false
        for (i in 1 until lines.size) {
            val stat = parse(BADGE.replace(TextClean.clean(lines[i].string), "")) ?: continue
            val key = stat.name.lowercase()
            val before = baseStats[key]
            matched.add(key)
            val delta = stat.value - (before?.value ?: 0.0)
            if (abs(delta) < EPSILON) continue
            lines[i] = lines[i].copy().append(deltaText(stat, delta))
            changed = true
        }
        val lost = baseStats.filter { it.key !in matched }.values
        if (lost.isNotEmpty()) {
            if (!changed) lines.add(Component.literal(""))
            lines.add(Component.literal("Compared with $label").withColor(HEADER))
            for (stat in lost) lines.add(deltaLine(stat))
            changed = true
        }
        if (changed && lost.isEmpty()) lines.add(Component.literal("Compared with $label").withColor(HEADER))
    }

    private fun parse(text: String): Stat? {
        LINE.matchEntire(text)?.let { m ->
            val value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            return Stat(m.groupValues[3].trim(), value, m.groupValues[2])
        }
        RANGE.matchEntire(text)?.let { m ->
            val low = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
            val high = m.groupValues[4].replace(",", "").toDoubleOrNull() ?: return null
            return Stat(m.groupValues[1].trim(), (low + high) / 2.0, m.groupValues[3])
        }
        return null
    }

    private fun inverted(name: String): Boolean = name.contains("cost", ignoreCase = true)

    private fun colorFor(name: String, delta: Double): Int {
        val good = if (inverted(name)) delta < 0 else delta > 0
        return if (good) GAIN else LOSS
    }

    private fun format(value: Double): String {
        val rounded = if (abs(value - value.roundToLong()) < EPSILON) value.roundToLong().toString() else "%.1f".format(value)
        return if (value > 0) "+$rounded" else rounded
    }

    private fun deltaText(stat: Stat, delta: Double): Component =
        Component.literal(" (${format(delta)}${stat.unit})").withColor(colorFor(stat.name, delta))

    private fun deltaLine(stat: Stat): Component =
        Component.literal("${format(-stat.value)}${stat.unit} ${stat.name}").withColor(colorFor(stat.name, -stat.value))

    private const val EPSILON = 0.001
    private const val HEADER = 0xAAAAAA
}

package opal.dev.overwatch.client

import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

data class MountStatReading(val current: Int, val limit: Int, val max: Int?)

data class MountReading(
    val name: String,
    val typeName: String,
    val potential: Int?,
    val primaryColor: String?,
    val secondaryColor: String?,
    val currentEnergy: Int?,
    val energyCap: Int?,
    val stats: Map<String, MountStatReading>,
)

object MountTooltipParser {
    fun parse(stack: ItemStack): MountReading? {
        if (stack.isEmpty || stack.item != Items.POTION) return null
        val title = TextClean.clean(stack.hoverName.string)
        val nameMatch = MOUNT_TITLE.find(title) ?: return null
        val name = nameMatch.groupValues[1].trim()
        val typeWord = nameMatch.groupValues[2]
        val typeName = MountGuideData.TYPES.firstOrNull { it.itemName == typeWord || it.summonItemName == typeWord }?.displayName ?: return null

        val loreLines = stack.get(DataComponents.LORE)?.lines()?.map { TextClean.clean(it.string) } ?: return null
        if (loreLines.isEmpty()) return null

        var potential: Int? = null
        var primaryColor: String? = null
        var secondaryColor: String? = null
        var currentEnergy: Int? = null
        var energyCap: Int? = null
        val stats = LinkedHashMap<String, MountStatReading>()

        for (line in loreLines) {
            if (line.isEmpty()) continue

            val potentialMatch = if (potential == null) POTENTIAL_LINE.find(line) else null
            if (potentialMatch != null) {
                potential = parseSuffixedInt(potentialMatch.groupValues[1])
                continue
            }

            val colorMatch = if (primaryColor == null) COLOR_LINE.find(line) else null
            if (colorMatch != null) {
                primaryColor = colorMatch.groupValues[1].trim()
                secondaryColor = colorMatch.groupValues[2].trim()
                continue
            }

            val statMatch = STAT_LINE.find(line)
            if (statMatch != null) {
                val rawName = MountFeedingData.STAT_KEYS.firstOrNull { line.startsWith(it) } ?: statMatch.groupValues[1]
                val statName = if (rawName == "Jump Height") "Altitude" else rawName
                val current = statMatch.groupValues[2].toIntOrNull()
                val cap = statMatch.groupValues[3].toIntOrNull()
                val max = statMatch.groupValues[4].toIntOrNull()
                if (current != null && cap != null) {
                    stats[statName] = MountStatReading(current, cap, max)
                    continue
                }
            }

            if (currentEnergy == null && stats.size < 2) {
                val energyMatch = ENERGY_LINE.find(line)
                if (energyMatch != null) {
                    currentEnergy = energyMatch.groupValues[1].toIntOrNull()
                    energyCap = energyMatch.groupValues[2].toIntOrNull()
                }
            }
        }

        return MountReading(name, typeName, potential, primaryColor, secondaryColor, currentEnergy, energyCap, stats)
    }

    private fun parseSuffixedInt(text: String): Int? {
        if (text.endsWith("k", ignoreCase = true)) {
            return text.dropLast(1).toDoubleOrNull()?.let { (it * 1000).toInt() }
        }
        return text.toIntOrNull()
    }

    private val MOUNT_TITLE = Regex("""^(.+?)(?:'s?)? (Saddle|Reins|Harness|Whistle|Flute|Ocarina)$""")
    private val POTENTIAL_LINE = Regex("""^(\d+(?:\.\d+)?k?) Potential$""")
    private val COLOR_LINE = Regex("""^(.+)-(.+)$""")
    private val ENERGY_LINE = Regex("""^Energy (\d+)/(\d+)$""")
    private val STAT_LINE = Regex(
        "^(" + (MountFeedingData.STAT_KEYS + "Jump Height").joinToString("|") { Regex.escape(it) } + """).*?(\d+)/(\d+)(?:\s*\((\d+)\))?""",
    )
}

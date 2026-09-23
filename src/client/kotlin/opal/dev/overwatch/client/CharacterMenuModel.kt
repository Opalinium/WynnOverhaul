package opal.dev.overwatch.client

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack

object CharacterMenuModel {

    @Volatile
    var lastSnapshot: Snapshot? = null

    fun clearSnapshot() {
        lastSnapshot = null
    }

    data class SkillEntry(
        val name: String,
        val slot: Int,
        val points: Int,
        val percent: String,
        val isConfirm: Boolean = false,
    )

    data class Snapshot(
        val skillCrystalSlot: Int,
        val skills: List<SkillEntry>,
        val openers: List<Pair<String, Int>>,
    )

    fun snapshot(menu: AbstractContainerMenu): Snapshot? {
        var skillCrystal = -1
        val skills = ArrayList<SkillEntry>()
        val openers = ArrayList<Pair<String, Int>>()
        val total = menu.slots.size
        val own = if (total > 36) total - 36 else total
        for (slot in 0 until own) {
            val stack = menu.slots[slot].item
            if (stack.isEmpty) continue
            val letters = lettersOf(stack)
            when {
                letters.contains("Skill Crystal", ignoreCase = true) -> skillCrystal = slot
                letters.contains("Ability Tree", ignoreCase = true) ->
                    openers.add("Ability Tree" to slot)
                letters.contains("Mastery Tomes", ignoreCase = true) ->
                    openers.add("Mastery Tomes" to slot)
                letters.contains("View Your Guild", ignoreCase = true) ->
                    openers.add("View Your Guild" to slot)
                letters.contains("Recruit A Friend", ignoreCase = true) ->
                    openers.add("Recruit a Friend" to slot)
                letters.contains("Daily Reward", ignoreCase = true) ->
                    openers.add("Daily Reward" to slot)
                letters.contains("Store", ignoreCase = true) && letters.contains("Wardrobe", ignoreCase = true) ->
                    openers.add("Store" to slot)
                else -> skillFor(letters)?.let { name ->
                    val confirm = isSkillConfirm(stack)
                    val pp = skillPoints(stack)
                    if (pp != null) {
                        skills.add(SkillEntry(name, slot, pp.first, pp.second, confirm))
                    } else if (confirm) {
                        skills.add(SkillEntry(name, slot, -1, "--", true))
                    }
                }
            }
        }
        if (skillCrystal < 0 && skills.isEmpty() && openers.isEmpty()) return null
        return Snapshot(skillCrystal, skills, openers).also { lastSnapshot = it }
    }

    private val SKILL_NAMES = listOf("Strength", "Dexterity", "Intelligence", "Defence", "Agility")

    private fun skillFor(letters: String): String? {
        if (!letters.contains("Upgrade your", ignoreCase = true)) return null
        return SKILL_NAMES.firstOrNull { letters.contains(it, ignoreCase = true) }
    }

    private fun isSkillConfirm(stack: ItemStack): Boolean {
        val lore = WynnItemRarity.loreLines(stack)
        return lore.any { val l = it.lowercase(); "click" in l && "confirm" in l }
    }

    private fun skillPoints(stack: ItemStack): Pair<Int, String>? {
        val lore = WynnItemRarity.loreLines(stack)
        var points: Int? = null
        var percent: String? = null
        for (line in lore) {
            val clean = line.trim()
            Regex("""(\d+)\s+points""").find(clean)?.let {
                if (points == null) points = it.groupValues[1].toIntOrNull()
            }
            if (percent == null) Regex("""(\d+(?:\.\d+)?%)""").find(clean)?.let {
                percent = it.groupValues[1]
            }
            if (points != null && percent != null) break
        }
        return if (points != null) points to (percent ?: "--") else null
    }

    private fun lettersOf(stack: ItemStack): String =
        stack.hoverName.string.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
}

package opal.dev.overwatch.client

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object WynnPouches {
    fun isEmeraldPouch(stack: ItemStack): Boolean = lettersOf(stack).contains("Emerald Pouch", ignoreCase = true)

    fun isIngredientPouch(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val name = lettersOf(stack)
        if (name.contains("Ingredient Pouch", ignoreCase = true)) return true
        if (name.contains("Click to confirm", ignoreCase = true)) return true
        return isSellConfirm(stack)
    }

    fun isSellConfirm(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val lore = WynnItemRarity.loreLines(stack).map { it.lowercase() }
        return lore.any { "click to confirm" in it } && lore.any { "selling for" in it }
    }

    fun isConfirmMorph(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return lettersOf(stack).contains("Click to confirm", ignoreCase = true)
    }

    fun emeraldPouchTotal(stack: ItemStack): Long? {
        val lore = WynnItemRarity.loreLines(stack)
        for (line in lore) {
            val raw = POUCH_TOTAL.find(line)?.groupValues?.get(1) ?: continue
            raw.replace(Regex("[,.\\s']"), "").toLongOrNull()?.let { return it }
        }
        for (line in lore) {
            val runs = DIGIT_RUN.findAll(line).mapNotNull { it.value.toLongOrNull() }.toList()
            if (runs.size >= 3) {
                val last = runs.takeLast(3)
                return last[0] * 4096L + last[1] * 64L + last[2]
            }
        }
        return null
    }

    fun ingredientEntries(stack: ItemStack): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        for (line in WynnItemRarity.loreLines(stack)) {
            val match = INGREDIENT_LINE.find(line) ?: continue
            val count = match.groupValues[1].toIntOrNull() ?: continue
            val name = match.groupValues[2].trim()
            if (count > 0 && name.isNotEmpty()) out.add(count to name)
        }
        return out
    }

    fun totalEmeralds(stacks: List<ItemStack>): Long {
        var total = 0L
        for (stack in stacks) {
            if (stack.isEmpty) continue
            when {
                isEmeraldPouch(stack) -> total += emeraldPouchTotal(stack) ?: 0L
                stack.item == Items.EMERALD -> total += stack.count
                stack.item == Items.EMERALD_BLOCK -> total += stack.count * 9L
                stack.hoverName.string.contains("Liquid Emerald", ignoreCase = true) -> total += stack.count * 4096L
            }
        }
        return total
    }

    private fun lettersOf(stack: ItemStack): String =
        stack.hoverName.string.filter { it.isLetter() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()

    private val POUCH_TOTAL = Regex("""([\d][\d,.\s']*)\s*E\b""")
    private val DIGIT_RUN = Regex("[0-9]+")
    private val INGREDIENT_LINE = Regex("""(\d+)\s*[×x]\s*(.+)""")
}

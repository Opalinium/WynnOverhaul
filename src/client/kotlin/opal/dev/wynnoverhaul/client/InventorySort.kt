package opal.dev.wynnoverhaul.client

import net.minecraft.world.item.ItemStack

enum class InventorySort(val label: String) {
    DEFAULT("Default"),
    NAME("Name"),
    TYPE("Type"),
    RARITY("Rarity"),
    VALUE("Value");

    fun next(): InventorySort = entries[(ordinal + 1) % entries.size]

    private class Keyed(val slot: Int, val name: String, val primary: Double)

    fun apply(slots: List<Int>, stackOf: (Int) -> ItemStack): List<Int> {
        if (this == DEFAULT) return slots
        val occupied = ArrayList<Keyed>(slots.size)
        val empty = ArrayList<Int>()
        for (slot in slots) {
            val stack = stackOf(slot)
            if (stack.isEmpty) {
                empty.add(slot)
                continue
            }
            val primary = when (this) {
                NAME -> 0.0
                TYPE -> WynnItemCategory.of(stack).ordinal.toDouble()
                RARITY -> -(WynnItemRarity.of(stack)?.ordinal ?: -1).toDouble()
                VALUE -> -PriceCheck.sortValue(stack)
                DEFAULT -> 0.0
            }
            occupied.add(Keyed(slot, plainName(stack).lowercase(), primary))
        }
        occupied.sortWith(compareBy<Keyed>({ it.primary }, { it.name }, { it.slot }))
        val out = ArrayList<Int>(slots.size)
        for (k in occupied) out.add(k.slot)
        out.addAll(empty)
        return out
    }

    private fun plainName(stack: ItemStack): String = TextClean.clean(stack.hoverName.string)

    companion object {
        fun parse(raw: String?): InventorySort = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

package opal.dev.wynnoverhaul.client

import net.minecraft.world.item.ItemStack

enum class InventorySort(val label: String) {
    DEFAULT("Default"),
    NAME("Name"),
    TYPE("Type"),
    RARITY("Rarity"),
    VALUE("Value");

    fun next(): InventorySort = entries[(ordinal + 1) % entries.size]

    fun apply(slots: List<Int>, stackOf: (Int) -> ItemStack): List<Int> {
        if (this == DEFAULT) return slots
        val keyed = slots.map { slot -> Triple(slot, stackOf(slot), 0) }
        val occupied = keyed.filter { !it.second.isEmpty }
        val empty = keyed.filter { it.second.isEmpty }.map { it.first }
        val ordered = when (this) {
            NAME -> occupied.sortedWith(compareBy({ plainName(it.second).lowercase() }, { it.first }))
            TYPE -> occupied.sortedWith(compareBy({ WynnItemCategory.of(it.second).ordinal }, { plainName(it.second).lowercase() }, { it.first }))
            RARITY -> occupied.sortedWith(compareBy({ -(WynnItemRarity.of(it.second)?.ordinal ?: -1) }, { plainName(it.second).lowercase() }, { it.first }))
            VALUE -> occupied.sortedWith(compareBy({ -PriceCheck.sortValue(it.second) }, { plainName(it.second).lowercase() }, { it.first }))
            DEFAULT -> occupied
        }
        return ordered.map { it.first } + empty
    }

    private fun plainName(stack: ItemStack): String = TextClean.clean(stack.hoverName.string)

    companion object {
        fun parse(raw: String?): InventorySort = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}

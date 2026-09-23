package opal.dev.overwatch.client

import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

enum class WynnItemCategory(val displayName: String, val colorArgb: Int) {
    WEAPONS("Weapons", 0xFFD64545.toInt()),
    ARMOR("Armor", 0xFF7FA8D6.toInt()),
    ACCESSORIES("Accessories", 0xFFC77FE0.toInt()),
    CONSUMABLES("Consumables", 0xFF8FBF5C.toInt()),
    MATERIALS("Materials", 0xFFD9B45C.toInt()),
    QUEST("Quest Items", 0xFF6FE0D0.toInt()),
    MISC("Misc", 0xFFA6947A.toInt());

    companion object {
        fun of(stack: ItemStack): WynnItemCategory {
            if (stack.isEmpty) return MISC
            loreGearCategory(stack)?.let { return it }
            if (WynnWeapons.attacksPerSecond(stack) != null) return WEAPONS
            if (stack.get(DataComponents.EQUIPPABLE)?.slot()?.type == EquipmentSlot.Type.HUMANOID_ARMOR) return ARMOR

            val name = stack.hoverName.string.trim()
            val lcName = name.lowercase()
            val lore = WynnItemRarity.loreLines(stack)
            val lcLore = lore.map { it.lowercase() }

            if (lcLore.any { "quest item" in it }) return QUEST
            if (ACCESSORY_NAME.containsMatchIn(lcName)) return ACCESSORIES
            if (isConsumable(stack, lcName)) return CONSUMABLES
            if (isMaterial(stack, lcName, lcLore)) return MATERIALS
            return MISC
        }

        private fun loreGearCategory(stack: ItemStack): WynnItemCategory? {
            val first = WynnItemRarity.loreLines(stack).firstOrNull { it.isNotEmpty() }?.lowercase() ?: return null
            val type = TYPE_LINE.find(first)?.groupValues?.get(1) ?: return null
            return when (type) {
                "wand", "spear", "dagger", "bow", "relik" -> WEAPONS
                "helmet", "chestplate", "leggings", "boots" -> ARMOR
                "ring", "bracelet", "necklace" -> ACCESSORIES
                else -> null
            }
        }

        private val TYPE_LINE =
            Regex("""^(?:normal|unique|rare|legendary|fabled|mythic|set|crafted)\s+(wand|spear|dagger|bow|relik|helmet|chestplate|leggings|boots|ring|bracelet|necklace)\b""")

        private fun isConsumable(stack: ItemStack, lcName: String): Boolean {
            val gear = hasGearMarkers(stack)
            if (stack.get(DataComponents.FOOD) != null) return !gear
            if (stack.item == Items.POTION || stack.item == Items.SPLASH_POTION || stack.item == Items.LINGERING_POTION) {
                if (stack.get(DataComponents.POTION_CONTENTS) != null || POTION_NAME_HINTS.any { it in lcName }) return !gear
                return false
            }
            return CONSUMABLE_NAME.containsMatchIn(lcName) && !gear
        }

        private fun hasGearMarkers(stack: ItemStack): Boolean {
            val lore = WynnItemRarity.loreLines(stack).map { it.lowercase() }
            if (lore.isEmpty()) return false
            return lore.any { line -> GEAR_MARKERS.any { it.containsMatchIn(line) } }
        }

        private fun isMaterial(stack: ItemStack, lcName: String, lcLore: List<String>): Boolean {
            if (lcLore.any { "crafting ingredient" in it }) return true
            if (stack.item == Items.EMERALD) return true
            return MATERIAL_NAME.containsMatchIn(lcName)
        }

        private val ACCESSORY_NAME = Regex("""\b(ring|bracelet|necklace)s?\b""")
        private val CONSUMABLE_NAME = Regex("""\b(scroll|key|tome|charm|rune|potion|drink|meal|food|bait|soup|stew|cookie|cake|bread|pie)\b""")
        private val MATERIAL_NAME = Regex("""(powder|ingot|ingot block|ore|shard|fragment|essence|material)\b""")
        private val POTION_NAME_HINTS = listOf("potion", "flask", "elixir", "tonic", "brew")
        private val GEAR_MARKERS = listOf(
            Regex("""\battack speed\b"""),
            Regex("""\bcombat level\b"""),
            Regex("""\bset bonus\b"""),
            Regex("""\bpowder slots?\b"""),
            Regex("""\bidentifications?\b"""),
            Regex("""\brequirements?\b"""),
            Regex("""\bclass req"""),
            Regex("""\b(warrior|assassin|shaman|archer|mage)\b"""),
            Regex("""\bdamage\b"""),
            Regex("""\bdefen[cs]e\b"""),
            Regex("""\btier\b"""),
        )
    }
}

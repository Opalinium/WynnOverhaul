package opal.dev.overwatch.client

import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object FishingRodItems {

    private val SKYBLOCK_RARITIES = listOf(
        "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC",
        "DIVINE", "SPECIAL", "VERY SPECIAL", "ULTIMATE", "ADMIN",
    )

    @JvmStatic
    fun isRealFishingRod(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item !== Items.FISHING_ROD) return false

        val lore = stack.get(DataComponents.LORE) ?: return true
        val category = lore.lines()
            .map { it.getString().trim().uppercase() }
            .lastOrNull { line -> SKYBLOCK_RARITIES.any { line.startsWith("$it ") } }
            ?: return true

        return category.contains("FISHING ROD") || category.contains("FISHING WEAPON")
    }

    @JvmStatic
    fun isHoldingRealFishingRod(player: Player): Boolean =
        isRealFishingRod(player.mainHandItem) || isRealFishingRod(player.offhandItem)
}

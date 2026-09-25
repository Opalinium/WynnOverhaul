package opal.dev.wynnoverhaul.client

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object ActiveWeapon {
    private var slot: Int = -1

    fun tick(client: Minecraft) {
        val player = client.player
        if (player == null) {
            slot = -1
            return
        }
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        if (WynnGearKind.isWeapon(inventory.getItem(selected))) {
            slot = selected
            return
        }
        if (slot in HOTBAR && WynnGearKind.isWeapon(inventory.getItem(slot))) return
        slot = HOTBAR.firstOrNull { WynnGearKind.isWeapon(inventory.getItem(it)) } ?: -1
    }

    fun stack(player: Player): ItemStack? {
        if (slot !in HOTBAR) return null
        val stack = player.inventory.getItem(slot)
        return if (WynnGearKind.isWeapon(stack)) stack else null
    }

    fun current(player: Player): ItemStack = stack(player) ?: player.mainHandItem

    fun isRanged(player: Player): Boolean {
        val stack = current(player)
        if (stack !== rangedStack) {
            rangedStack = stack
            rangedResult = WeaponAnimations.isRanged(stack)
        }
        return rangedResult
    }

    fun attackKey(client: Minecraft): KeyMapping {
        val player = client.player
        return if (player != null && isRanged(player)) client.options.keyUse else client.options.keyAttack
    }

    private var rangedStack: ItemStack? = null
    private var rangedResult = false

    private val HOTBAR = 0..8
}

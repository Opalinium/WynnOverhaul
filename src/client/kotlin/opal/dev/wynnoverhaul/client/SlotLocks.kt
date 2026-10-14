package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

object SlotLocks {
    private const val MESSAGE_GAP_MILLIS = 800L
    private var lastMessageMillis = 0L

    private fun enabled(): Boolean = WynnOverhaulConfig.current.slotLocksEnabled

    private fun inventoryIndex(menu: AbstractContainerMenu, slot: Int): Int? {
        val target = menu.slots.getOrNull(slot) ?: return null
        if (target.container !is Inventory) return null
        return target.containerSlot.takeIf { it in 0..35 }
    }

    fun isLocked(menu: AbstractContainerMenu, slot: Int): Boolean {
        if (!enabled()) return false
        val index = inventoryIndex(menu, slot) ?: return false
        return index in WynnOverhaulConfig.current.lockedSlots
    }

    fun toggle(menu: AbstractContainerMenu, slot: Int): Boolean {
        if (!enabled()) return false
        val index = inventoryIndex(menu, slot) ?: return false
        val config = WynnOverhaulConfig.current
        config.lockedSlots = if (index in config.lockedSlots) config.lockedSlots - index else (config.lockedSlots + index).sorted()
        config.save()
        Minecraft.getInstance().gui.hud.setOverlayMessage(
            Component.literal(if (index in config.lockedSlots) "Slot locked" else "Slot unlocked"),
            false,
        )
        return true
    }

    @JvmStatic
    fun blocks(containerId: Int, slot: Int, input: ContainerInput, player: Player): Boolean {
        if (!enabled() || !WynnOverhaulGate.inGame) return false
        if (input != ContainerInput.QUICK_MOVE && input != ContainerInput.THROW) return false
        val menu = player.containerMenu
        if (menu.containerId != containerId || !isLocked(menu, slot)) return false
        val now = System.currentTimeMillis()
        if (now - lastMessageMillis > MESSAGE_GAP_MILLIS) {
            lastMessageMillis = now
            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("That slot is locked. Alt+click it to unlock"), false)
        }
        return true
    }
}

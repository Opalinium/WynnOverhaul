package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.InventoryMenu
import opal.dev.wynnoverhaul.WynnOverhaul

object WynnOverhaulInventory {
    fun register() {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (!WynnOverhaulGate.inGame) return@register
            if (!WynnOverhaulConfig.current.customInventoryEnabled) return@register
            if (screen !is InventoryScreen) return@register

            val tab = pendingTransitionTab
            pendingCapture = Minecraft.getInstance().player?.inventoryMenu?.let {
                it to tab
            }
        }
    }

    fun tick(client: Minecraft) {
        val capture = pendingCapture ?: return
        pendingCapture = null
        val (menu, targetTab) = capture

        pendingTransitionTab = null
        if (!WynnOverhaulGate.inGame) return
        if (!WynnOverhaulConfig.current.customInventoryEnabled) return
        if (client.gui.screen() !is InventoryScreen) return
        if (client.player?.inventoryMenu !== menu) return
        client.setScreenAndShow(WynnOverhaulInventoryScreen(menu, targetTab ?: WynnOverhaulInventoryScreen.InvTab.INVENTORY))
    }

    private var pendingCapture: Pair<InventoryMenu, WynnOverhaulInventoryScreen.InvTab?>? = null

    @Volatile
    var pendingTransitionTab: WynnOverhaulInventoryScreen.InvTab? = null
}
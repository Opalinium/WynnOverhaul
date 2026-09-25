package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.InventoryMenu
import opal.dev.overwatch.Overwatch

object OverwatchInventory {
    fun register() {
        ScreenEvents.BEFORE_INIT.register { _, screen, _, _ ->
            if (!OverwatchGate.inGame) return@register
            if (!OverwatchConfig.current.customInventoryEnabled) return@register
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
        if (!OverwatchGate.inGame) return
        if (!OverwatchConfig.current.customInventoryEnabled) return
        if (client.gui.screen() !is InventoryScreen) return
        if (client.player?.inventoryMenu !== menu) return
        client.setScreenAndShow(OverwatchInventoryScreen(menu, targetTab ?: OverwatchInventoryScreen.InvTab.INVENTORY))
    }

    private var pendingCapture: Pair<InventoryMenu, OverwatchInventoryScreen.InvTab?>? = null

    @Volatile
    var pendingTransitionTab: OverwatchInventoryScreen.InvTab? = null
}
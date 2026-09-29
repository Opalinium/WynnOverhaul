package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.AbstractContainerMenu

object LootChestBlocker {
    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.mythicBlockerEnabled) return@register
            val container = screen as? AbstractContainerScreen<*> ?: return@register
            if (!isLootChest(container)) return@register
            ScreenKeyboardEvents.allowKeyPress(screen).register { _, event -> allowClose(container, event) }
        }
    }

    private fun allowClose(screen: AbstractContainerScreen<*>, event: KeyEvent): Boolean {
        val client = Minecraft.getInstance()
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.mythicBlockerEnabled) return true
        if (event.key() != KEY_ESCAPE && !client.options.keyInventory.matches(event)) return true
        val best = richestFind(screen.menu) ?: return true
        warnThrottled(client, best)
        return false
    }

    private fun isLootChest(screen: AbstractContainerScreen<*>): Boolean =
        LOOT_TITLE.containsMatchIn(screen.title.string)

    private fun richestFind(menu: AbstractContainerMenu): WynnRarity? {
        val threshold = WynnRarity.entries.firstOrNull { it.name == WynnOverhaulConfig.current.mythicAlertMinRarity }
            ?: WynnRarity.MYTHIC
        var best: WynnRarity? = null
        val chestSlots = (menu.slots.size - PLAYER_MIRROR_SIZE).coerceAtLeast(0)
        for (i in 0 until chestSlots) {
            val rarity = WynnItemRarity.of(menu.slots[i].item) ?: continue
            if (rarity.ordinal < threshold.ordinal) continue
            if (best == null || rarity.ordinal > best.ordinal) best = rarity
        }
        return best
    }

    private fun warnThrottled(client: Minecraft, rarity: WynnRarity) {
        val now = System.nanoTime()
        if (now - lastWarnNanos < WARN_GAP_NANOS) return
        lastWarnNanos = now
        client.player?.sendOverlayMessage(
            Component.literal("[WynnOverhaul] Loot chest still holds a ${rarity.displayName} item").withStyle(ChatFormatting.RED),
        )
    }

    private var lastWarnNanos = 0L

    private const val KEY_ESCAPE = 256
    private const val PLAYER_MIRROR_SIZE = 36
    private const val WARN_GAP_NANOS = 2_000_000_000L
    private val LOOT_TITLE = Regex("Loot Chest \\[\u272B\u272B\u272B\u272B\\]")
}

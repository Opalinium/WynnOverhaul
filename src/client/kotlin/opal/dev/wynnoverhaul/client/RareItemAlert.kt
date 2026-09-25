package opal.dev.wynnoverhaul.client

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

object RareItemAlert {
    private var lastCounts: Map<String, Int> = emptyMap()
    private var initialized = false
    private var nextScanNanos = 0L

    fun tick(client: Minecraft) {
        val config = WynnOverhaulConfig.current
        if (!config.mythicAlertEnabled) {
            if (initialized) {
                lastCounts = emptyMap()
                initialized = false
            }
            return
        }
        val player = client.player ?: return

        val now = System.nanoTime()
        if (now < nextScanNanos) return
        nextScanNanos = now + SCAN_INTERVAL_NANOS

        val threshold = WynnRarity.entries.firstOrNull { it.name == config.mythicAlertMinRarity } ?: WynnRarity.MYTHIC
        val inventory = player.inventory
        val current = HashMap<String, Int>()
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.isEmpty) continue
            val rarity = WynnItemRarity.of(stack) ?: continue
            if (rarity.ordinal < threshold.ordinal) continue
            val key = stack.hoverName.string
            current[key] = (current[key] ?: 0) + stack.count
        }

        if (initialized) {
            for ((name, count) in current) {
                if (count > (lastCounts[name] ?: 0)) {
                    val stack = findByName(inventory, name) ?: continue
                    val rarity = WynnItemRarity.of(stack) ?: continue
                    announce(client, player, stack, rarity)
                }
            }
        }
        lastCounts = current
        initialized = true
    }

    private fun findByName(inventory: Inventory, name: String): ItemStack? {
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (!stack.isEmpty && stack.hoverName.string == name) return stack
        }
        return null
    }

    private fun announce(client: Minecraft, player: LocalPlayer, stack: ItemStack, rarity: WynnRarity) {
        val config = WynnOverhaulConfig.current
        if (config.mythicAlertSound) {
            NotificationSounds.play(config.mythicAlertSoundId, config.mythicAlertVolume)
        }
        if (config.mythicAlertChat) {
            player.sendSystemMessage(
                Component.literal("[WynnOverhaul] Obtained ").withStyle(ChatFormatting.GRAY)
                    .append(stack.hoverName.copy())
                    .append(Component.literal(" (${rarity.displayName})").withStyle(ChatFormatting.GRAY)),
            )
        }
    }

    private const val SCAN_INTERVAL_NANOS = 500_000_000L
}

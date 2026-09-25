package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class EntityTrackerClient : ClientModInitializer {
    private lateinit var toggleKey: KeyMapping

    override fun onInitializeClient() {
        WynnOverhaulConfig.ensureLoaded()

        toggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.wynnoverhaul.tracker.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                WynnOverhaulKeyCategory.CATEGORY,
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.END_MAIN.register { ctx -> TrackerEsp.capture(ctx) }
        HudElementRegistry.addFirst(
            Identifier.fromNamespaceAndPath("wynnoverhaul", "entity_tracker_esp"),
            TrackerEspHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("wynnoverhaul", "entity_tracker"),
            EntityTrackerHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("wynnoverhaul", "potion_effects"),
            PotionEffectHudElement(),
        )
    }

    private fun onTick(client: Minecraft) {
        WynnOverhaulGate.refresh(client)
        if (!WynnOverhaulGate.inGame) return
        val config = WynnOverhaulConfig.current

        while (toggleKey.consumeClick()) {
            config.trackerEnabled = !config.trackerEnabled
            config.save()
            client.player?.sendOverlayMessage(
                Component.literal("[WynnOverhaul] Entity Tracker ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal(if (config.trackerEnabled) "enabled" else "disabled")
                        .withStyle(if (config.trackerEnabled) ChatFormatting.GREEN else ChatFormatting.RED),
                ),
            )
        }

        EntityTracker.tick(client)
    }
}

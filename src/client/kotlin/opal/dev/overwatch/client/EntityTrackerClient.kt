package opal.dev.overwatch.client

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
        OverwatchConfig.ensureLoaded()

        toggleKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.overwatch.tracker.toggle",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.value,
                OverwatchKeyCategory.CATEGORY,
            ),
        )

        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        LevelRenderEvents.END_MAIN.register { ctx -> TrackerEsp.capture(ctx) }
        HudElementRegistry.addFirst(
            Identifier.fromNamespaceAndPath("overwatch", "entity_tracker_esp"),
            TrackerEspHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "entity_tracker"),
            EntityTrackerHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "potion_effects"),
            PotionEffectHudElement(),
        )
    }

    private fun onTick(client: Minecraft) {
        OverwatchGate.refresh(client)
        if (!OverwatchGate.inGame) return
        val config = OverwatchConfig.current

        while (toggleKey.consumeClick()) {
            config.trackerEnabled = !config.trackerEnabled
            config.save()
            client.player?.sendOverlayMessage(
                Component.literal("[Overwatch] Entity Tracker ").withStyle(ChatFormatting.GRAY).append(
                    Component.literal(if (config.trackerEnabled) "enabled" else "disabled")
                        .withStyle(if (config.trackerEnabled) ChatFormatting.GREEN else ChatFormatting.RED),
                ),
            )
        }

        EntityTracker.tick(client)
    }
}

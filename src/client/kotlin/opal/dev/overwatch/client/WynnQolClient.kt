package opal.dev.overwatch.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

class WynnQolClient : ClientModInitializer {

    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        PartyFriendModel.register()
        WynnBuffTracker.register()
        WynnLevelTracker.register()
        MountTooltipFeature.register()
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "mount_feeder"),
            MountFeederHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "lootrun"),
            LootrunHudElement(),
        )
    }

    private fun onTick(client: Minecraft) {
        RareItemAlert.tick(client)
        WynnScoreboardTracker.tick(client)
        QuestBeaconTracker.tick(client)
        ContentBookQuery.tick(client)
        ContentBookCache.attach(client)
        VoxyVista.tick(client)
        PartyFriendModel.tick(client)
        WynnBuffTracker.tick(client)
        LootrunModel.tick(client)
        LootrunBeaconTracker.tick(client)
        LootrunRecorder.tick(client)
    }
}

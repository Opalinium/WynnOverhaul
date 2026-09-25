package opal.dev.overwatch.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import opal.dev.overwatch.Overwatch

class WynnQolClient : ClientModInitializer {
    override fun onInitializeClient() {
        OverwatchConfig.ensureLoaded()

        WynnRegions.ensureLoaded()

        Overwatch.LOGGER.info("Overwatch client initialized (nw-style HUD)")
        ClientTickEvents.END_CLIENT_TICK.register(::onTick)
        ChatHud.init()
        WynnDirectMessages.register()
        PartyFriendModel.register()
        WynnBuffTracker.register()
        WynnLevelTracker.register()
        WynnVitalsTracker.register()
        WynnSprintTracker.register()
        WynnMountEnergyTracker.register()
        WynnMountPickupTracker.register()
        WynnDialogueTracker.register()
        WynnCombatXpTracker.register()
        WynnSpellTracker.register()
        WynnChatChannels.register()
        OverwatchInventory.register()
        CharacterInfo.register()
        MountSettingsInterceptor.register()
        WynnQuestCompletionTracker.register()
        WynnLevelUpToastOverride.register()
        WynnLocationToasts.register()
        MountTooltipFeature.register()
        PowderSpecialsTooltip.register()
        ObjectiveClaims.register()
        WynnActionBar.registerDebug()
        PriceCheck.register()
        registerHudLayouts()
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "mount_feeder"),
            MountFeederHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "lootrun"),
            LootrunHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "ability_cooldowns"),
            AbilityCooldownHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "toast"),
            OverwatchToastHudElement(),
        )

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "hp"),
            HpHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "mana"),
            ManaHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "sprint"),
            SprintHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "xp_bar"),
            XpHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "resource_bar"),
            ResourceBarHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "guild"),
            WynnGuildHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "quest_log"),
            WynnQuestLogHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "compass"),
            WynnCompassHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "hotbar"),
            HotbarHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "spell_cast"),
            WynnSpellCastHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "spell_combo"),
            WynnSpellComboHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "mount_pickup"),
            WynnMountPickupHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "mount_energy"),
            WynnMountEnergyHudElement(),
        )
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("overwatch", "dialogue"),
            WynnDialogueHudElement(),
        )
    }

    private fun registerHudLayouts() {
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("mount_feeder", "Mount Feeder", "TOP_RIGHT", fallbackW = 230, fallbackH = 60, hidden = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("lootrun", "Lootrun", "TOP_LEFT", fallbackW = 180, fallbackH = 50))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("ability_cooldowns", "Ability Cooldowns", "BOTTOM_RIGHT", fallbackW = 140, fallbackH = 42))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("toast", "Toasts", "TOP_LEFT", defaultOffsetX = 220, defaultOffsetY = 24, fallbackW = 200, fallbackH = 34))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("potion_effects", "Potion Effects", "TOP_RIGHT", fallbackW = 160, fallbackH = 54))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("tracker", "Entity Tracker", "TOP_LEFT", fallbackW = 160, fallbackH = 60))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("hp", "Health", "TOP_LEFT", fallbackW = 180, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("mana", "Mana", "TOP_LEFT", defaultOffsetY = 24, fallbackW = 180, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("spell_cast", "Spell Cast", "BOTTOM_LEFT", defaultOffsetX = 220, defaultOffsetY = 46, fallbackW = 140, fallbackH = 15))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("spell_combo", "Spell Combo", "BOTTOM_LEFT", defaultOffsetX = 220, defaultOffsetY = 66, fallbackW = 100, fallbackH = 16))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("sprint", "Sprint / Stamina", "BOTTOM_LEFT", fallbackW = 140, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("mount_energy", "Mount Energy", "BOTTOM_LEFT", defaultOffsetY = 20, fallbackW = 140, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("mount_pickup", "Mount Pickup", "BOTTOM_LEFT", defaultOffsetX = 220, defaultOffsetY = 86, fallbackW = 140, fallbackH = 24))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("dialogue", "Dialogue", "BOTTOM_LEFT", defaultOffsetX = 40, defaultOffsetY = 60, fallbackW = 300, fallbackH = 48))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("xp_bar", "Experience", "BOTTOM_LEFT", defaultOffsetY = 24, fallbackW = 200, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("resource_bar", "Class Resource", "TOP_LEFT", defaultOffsetY = 24, fallbackW = 170, fallbackH = 15, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("guild", "Guild", "TOP_LEFT", defaultOffsetY = 44, fallbackW = 160, fallbackH = 26))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("quest_log", "Quest Log", "TOP_RIGHT", fallbackW = 260, fallbackH = 80))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("compass", "Compass", "TOP_LEFT", defaultOffsetX = 4, defaultOffsetY = 100, fallbackW = 300, fallbackH = 48))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("chat", "Chat", "BOTTOM_LEFT", defaultOffsetX = 0, defaultOffsetY = 40, fallbackW = 320, fallbackH = 180, barStretch = true))
        HudLayoutManager.register(HudLayoutManager.HudElementSpec("hotbar", "Hotbar", "BOTTOM_LEFT", defaultOffsetX = 220, defaultOffsetY = 4, fallbackW = 211, fallbackH = 24, barStretch = true))
    }

    private fun onTick(client: Minecraft) {
        OverwatchGate.refresh(client)
        if (!OverwatchGate.inGame) return
        RareItemAlert.tick(client)
        WynnScoreboardTracker.tick(client)
        WynnDirectMessages.tick(client)
        QuestBeaconTracker.tick(client)
        ContentBookQuery.tick(client)
        ContentBookInterceptor.tick(client)
        OverwatchInventory.tick(client)
        ActiveWeapon.tick(client)
        CharacterInfo.tick(client)
        MountSettingsInterceptor.tick(client)
        (client.gui.screen() as? OverwatchInventoryScreen)?.pollPendingFire()
        ContentBookCache.attach(client)
        WynnScoreboardTracker.current?.let { tracked ->
            val updated = ContentBookCache.reconcileLiveTracked(tracked.name)
            if (updated != null) {
                val screen = client.gui.screen()
                if (screen is OverwatchInventoryScreen) screen.updateBookActivities(updated)
            }
        }
        VoxyVista.tick(client)
        PartyFriendModel.tick(client)
        WynnBuffTracker.tick(client)
        LootrunModel.tick(client)
        LootrunBeaconTracker.tick(client)
        LootrunRecorder.tick(client)
    }
}

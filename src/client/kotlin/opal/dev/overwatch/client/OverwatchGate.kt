package opal.dev.overwatch.client

import net.minecraft.client.Minecraft

object OverwatchGate {
    @Volatile
    var onWynncraft: Boolean = false
        private set

    @Volatile
    var inGame: Boolean = false
        private set

    fun isInGame(): Boolean = inGame

    fun isOnWynncraft(): Boolean = onWynncraft

    fun noteActionBar() {
        lastActionBarMillis = System.currentTimeMillis()
    }

    fun refresh(client: Minecraft) {
        val onWynn = client.currentServer?.ip?.contains("wynncraft", ignoreCase = true) == true
        val fresh = System.currentTimeMillis() - lastActionBarMillis < HEARTBEAT_TIMEOUT_MS
        val inGameNow = onWynn && fresh
        if (inGame && !inGameNow) {
            CharacterMenuModel.clearSnapshot()
            OverwatchInventoryScreen.clearDockedCache()
            WynnLevelTracker.clear()
            WynnVitalsTracker.clear()
            WynnSprintTracker.clear()
            WynnMountEnergyTracker.clear()
            WynnMountPickupTracker.clear()
            WynnDialogueTracker.clear()
            WynnCombatXpTracker.clear()
            WynnResourceBarTracker.clear()
            WynnGuildBarTracker.clear()
            WynnRegionBarTracker.clear()
            WynnSpellTracker.clear()
            HudLayoutManager.clearSizes()
            ContentBookCache.clearTracking()
        }
        onWynncraft = onWynn
        inGame = inGameNow
    }

    @Volatile
    private var lastActionBarMillis: Long = 0L

    private const val HEARTBEAT_TIMEOUT_MS = 8000L
}

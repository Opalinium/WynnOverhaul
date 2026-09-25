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
        val inGameNow = onWynn && fresh && !inCharacterSelect(client)
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
            ObjectiveClaims.clear()
        }
        onWynncraft = onWynn
        inGame = inGameNow
    }

    private fun inCharacterSelect(client: Minecraft): Boolean {
        val title = client.gui.screen()?.title?.string ?: return false
        return CHARACTER_SELECT.containsMatchIn(TextClean.clean(title))
    }

    @Volatile
    private var lastActionBarMillis: Long = 0L

    private const val HEARTBEAT_TIMEOUT_MS = 8000L
    private val CHARACTER_SELECT = Regex("""select a character|character selection|choose a character""", RegexOption.IGNORE_CASE)
}

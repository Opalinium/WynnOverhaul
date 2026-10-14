package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft

object WynnOverhaulGate {
    @Volatile
    var onWynncraft: Boolean = false
        private set

    @Volatile
    var inGame: Boolean = false
        private set

    fun isInGame(): Boolean = inGame

    fun noteActionBar() {
        lastActionBarMillis = System.currentTimeMillis()
    }

    fun refresh(client: Minecraft) {
        val ip = client.currentServer?.ip
        if (ip != cachedIp) {
            cachedIp = ip
            cachedOnWynn = ip?.contains("wynncraft", ignoreCase = true) == true
        }
        val onWynn = cachedOnWynn
        val fresh = System.currentTimeMillis() - lastActionBarMillis < HEARTBEAT_TIMEOUT_MS
        val inGameNow = onWynn && fresh && !inCharacterSelect(client)
        if (inGame && !inGameNow) {
            CharacterMenuModel.clearSnapshot()
            WynnItemRarity.clearCaches()
            WynnOverhaulInventoryScreen.clearDockedCache()
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
            SimulatedCast.clear()
            WynnCutsceneTracker.clear()
            WynnSpellTracker.clear()
            WynnClassTracker.clear()
            WynnTotemTracker.clear()
            BankSession.clear()
            WynnDamageTracker.clear()
            WynnHitFeedback.clear()
            DamageNumbers.clear()
            ImpactSparks.clear()
            LootBeams.clear()
            WeaponAnimations.clearImpactTails()
            WynnClassBuffTracker.clear()
            WynnUltimateTracker.clear()
            WynnPowderSpecialTracker.clear()
            PartyBuffTracker.clear()
            HudLayoutManager.clearSizes()
            ContentBookCache.clearTracking()
            DungeonEntrances.reset()
            ObjectiveClaims.clear()
            DiscoveryTracker.clear()
            XaeroHook.clear()
            ChatActivity.clearAll()
            PinBoard.clearKind(PinBoard.Kind.PING)
            PinBoard.clearKind(PinBoard.Kind.RECEIVED)
        }
        onWynncraft = onWynn
        inGame = inGameNow
    }

    private fun inCharacterSelect(client: Minecraft): Boolean {
        val screen = client.gui.screen()
        if (screen == null) {
            checkedScreen = null
            return false
        }
        if (screen === checkedScreen) return checkedSelect
        checkedScreen = screen
        checkedSelect = CHARACTER_SELECT.containsMatchIn(TextClean.clean(screen.title.string))
        return checkedSelect
    }

    private var cachedIp: String? = null
    private var cachedOnWynn = false
    private var checkedScreen: Any? = null
    private var checkedSelect = false

    @Volatile
    private var lastActionBarMillis: Long = 0L

    private const val HEARTBEAT_TIMEOUT_MS = 8000L
    private val CHARACTER_SELECT = Regex("""select a character|character selection|choose a character""", RegexOption.IGNORE_CASE)
}

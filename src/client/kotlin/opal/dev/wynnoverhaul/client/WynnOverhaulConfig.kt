package opal.dev.wynnoverhaul.client

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import opal.dev.wynnoverhaul.WynnOverhaul
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class WynnOverhaulConfig(
    var enabled: Boolean = true,
    var maxCps: Double = 8.0,
    var combatSpellGuardEnabled: Boolean = true,
    var combatSpellGuardMs: Double = 1500.0,
    var wynnAttackSpeed: Boolean = true,
    var qolPreventHotbarOverscroll: Boolean = false,
    var rememberCameraMode: Boolean = false,
    var weeklyObjectivePending: Boolean = false,
    var lastCameraMode: String = "FIRST_PERSON",
    var weaponAnimationsEnabled: Boolean = false,
    var weaponIdleEnabled: Boolean = true,
    var weaponTrueIdleEnabled: Boolean = true,
    var weaponTrueIdleDelaySeconds: Double = 3.0,
    var weaponSprintEnabled: Boolean = true,
    var weaponWalkEnabled: Boolean = true,
    var weaponAnimationCombo: Boolean = true,
    var weaponAnimationSpells: Boolean = true,
    var weaponAnimationSfx: Boolean = true,
    var weaponAnimationSfxVolume: Double = 0.6,
    var weaponAnimationTrail: Boolean = true,
    var weaponAnimationTrailIntensity: Double = 1.0,
    var weaponAnimationPreview: Boolean = false,
    var weaponAnimationPreviewT: Double = 0.5,
    var weaponAnimationEntries: MutableList<WeaponAnimationEntry>? = null,
    var locomotionEnabled: Boolean = false,
    var locomotionStyle: String = "HEROIC",
    var locomotionOtherPlayers: Boolean = true,
    var locomotionRandomizeOthers: Boolean = true,
    var locomotionBend: Boolean = true,
    var locomotionTerrain: Boolean = true,
    var locomotionWalk: Boolean = true,
    var locomotionJump: Boolean = true,
    var locomotionCrouch: Boolean = true,
    var locomotionSwim: Boolean = true,
    var locomotionClimb: Boolean = true,
    var locomotionRide: Boolean = true,
    var locomotionElytra: Boolean = true,
    var locomotionUseItem: Boolean = true,
    var locomotionHurt: Boolean = true,
    var locomotionDeath: Boolean = true,
    var soulsCameraEnabled: Boolean = false,
    var soulsCameraDistance: Double = 4.0,
    var soulsCameraHeight: Double = 0.0,
    var soulsCameraShoulder: Double = 0.0,
    var soulsCameraSensitivity: Double = 1.0,
    var soulsCameraSmoothing: Double = 0.25,
    var soulsCameraTurnSpeed: Double = 0.55,
    var soulsCameraFaceHoldMs: Double = 700.0,
    var soulsCameraReticle: Boolean = true,
    var debugItemCopyEnabled: Boolean = false,
    var debugActionBarLog: Boolean = false,
    var mythicAlertEnabled: Boolean = false,
    var mythicAlertMinRarity: String = "MYTHIC",
    var mythicAlertSound: Boolean = true,
    var mythicAlertChat: Boolean = true,
    var mythicAlertSoundId: String = "minecraft:entity.player.levelup",
    var mythicAlertVolume: Double = 1.0,
    var discordRpcEnabled: Boolean = true,
    var discordShowActivity: Boolean = true,
    var discordShowLevel: Boolean = true,
    var discordShowRegion: Boolean = true,
    var discordShowButton: Boolean = true,
    var contentBookOverrideEnabled: Boolean = true,
    var contentBookSort: String = "RECOMMENDED",
    var inventorySort: String = "DEFAULT",
    var xaeroHookEnabled: Boolean = true,
    var contentBookFilter: String = "",
    var questWaypointOverrideEnabled: Boolean = true,
    var questWaypointRange: Double = 400.0,
    var voxyVistaEnabled: Boolean = true,
    var voxyVistaMessages: Boolean = true,
    var hideVanillaPotionHud: Boolean = false,
    var customPotionHudEnabled: Boolean = false,
    var abilityCooldownHudEnabled: Boolean = true,
    var questLogHudEnabled: Boolean = true,
    var ultimateHudEnabled: Boolean = true,

    var hudPanelsEnabled: Boolean = false,

    var hudBarStyle: String = "CLASSIC",
    var hotbarStyle: String = "CLASSIC",
    var shiftDragQuickMove: Boolean = true,
    var chatHudEnabled: Boolean = true,
    var chatStyle: String = "CLASSIC",
    var chatSmartReply: Boolean = true,
    var chatConversationView: Boolean = true,
    var questCompletionToastEnabled: Boolean = false,
    var levelUpToastEnabled: Boolean = false,
    var discoveryToastEnabled: Boolean = true,
    var locationToastEnabled: Boolean = true,
    var questToastStyle: String = "CLASSIC",
    var levelUpToastStyle: String = "CLASSIC",
    var discoveryToastStyle: String = "SOULS",
    var locationToastStyle: String = "SOULS",
    var toastTextScale: Double = 1.75,
    var soulsToastScale: Double = 1.5,
    var toastDurationSeconds: Double = 4.0,
    var toastKinds: MutableMap<String, ToastSettings>? = null,
    var customPartyNametagsEnabled: Boolean = true,
    var mountTooltipEnabled: Boolean = true,
    var equipComparisonEnabled: Boolean = true,
    var powderSpecialsTooltipEnabled: Boolean = true,
    var priceCheckEnabled: Boolean = true,
    var priceCheckNpcEnabled: Boolean = true,
    var wynnventoryApiKey: String = "",
    var mountFeederHudEnabled: Boolean = true,
    var lootrunEnabled: Boolean = true,
    var lootrunBeaconsEnabled: Boolean = true,
    var lootrunTaskMarkerEnabled: Boolean = true,
    var lootrunHudEnabled: Boolean = true,
    var lootrunRecorderEnabled: Boolean = false,
    var trackerEnabled: Boolean = false,
    var trackerWaypointsEnabled: Boolean = true,
    var trackerWaypointScale: Double = 1.0,
    var townNpcMarkersEnabled: Boolean = true,
    var trackerRange: Double = 64.0,
    var trackerDiscoveredChestsEnabled: Boolean = true,
    var trackerDiscoveredChestRange: Double = 400.0,
    var trackerDiscoveredChestGuidanceRange: Double = 64.0,
    var trackerDiscoveredNodesEnabled: Boolean = true,
    var trackerDiscoveredNodeRange: Double = 400.0,
    var trackerDiscoveredNodeGuidanceRange: Double = 64.0,
    var trackerPingSound: Boolean = true,
    var trackerPingChat: Boolean = true,
    var trackerPingSoundId: String = "minecraft:block.note_block.pling",
    var trackerPingPitch: Double = 1.5,
    var trackerPingVolume: Double = 0.6,
    var trackerHudShowDistance: Boolean = true,
    var trackerRules: MutableList<TrackerRule> = mutableListOf(),
    var hudLayouts: MutableMap<String, HudElementLayout> = mutableMapOf(),
    var hudLayoutPreset: String = "DEFAULT",
    var hudLayoutSlots: MutableMap<String, MutableMap<String, HudElementLayout>> = mutableMapOf(),
    var customInventoryEnabled: Boolean = true,

    var customHudEnabled: Boolean = false,

    var chatChannel: String = "all",

    var mountRegistry: MutableMap<String, StoredMount>? = null,
) {
    data class ToastSettings(
        var scale: Double = 1.75,
        var durationSeconds: Double = 4.0,
        var opacity: Double = 1.0,
        var sound: String = "",
        var soundVolume: Double = 0.6,
    )

    fun toast(kind: WynnOverhaulToastQueue.Kind): ToastSettings {
        val map = toastKinds ?: mutableMapOf<String, ToastSettings>().also { toastKinds = it }
        return map.getOrPut(kind.name) { legacyToastSettings(kind) }
    }

    private fun legacyToastSettings(kind: WynnOverhaulToastQueue.Kind): ToastSettings {
        val souls = ToastStyle.parse(WynnOverhaulToastQueue.styleName(kind)) == ToastStyle.SOULS
        return ToastSettings(
            scale = if (souls) soulsToastScale else toastTextScale,
            durationSeconds = if (souls) toastDurationSeconds * 1.5 else toastDurationSeconds,
        )
    }

    data class TrackerRule(
        var enabled: Boolean = true,
        var label: String = "",
        var nameMode: String = "CONTAINS",
        var namePattern: String = "",
        var entityTypeId: String = "",
        var colorArgb: Long = 0xFFFF5555L,
        var throughWalls: Boolean = true,
        var trackBlockBelow: Boolean = false,
        var trackAllChests: Boolean = false,
        var minChestTier: Int = 0,
        var trackGatheringNode: Boolean = false,
        var nodeProfession: String = "",
        var alwaysDisplay: Boolean = false,
    )

    data class HudElementLayout(
        var corner: String = "TOP_LEFT",
        var offsetX: Int = 4,
        var offsetY: Int = 4,
        var scale: Double = 1.0,
        var locked: Boolean = false,

        var barWidth: Int = 0,

        var boxH: Int = 0,

        var style: String = "",
    )

    fun sanitize(): WynnOverhaulConfig {
        maxCps = maxCps.coerceIn(1.0, 12.0)
        combatSpellGuardMs = combatSpellGuardMs.coerceIn(300.0, 4000.0)
        trackerRange = trackerRange.coerceIn(8.0, 128.0)
        trackerDiscoveredChestRange = trackerDiscoveredChestRange.coerceIn(64.0, 4000.0)
        trackerDiscoveredChestGuidanceRange = trackerDiscoveredChestGuidanceRange.coerceIn(8.0, trackerDiscoveredChestRange)
        trackerDiscoveredNodeRange = trackerDiscoveredNodeRange.coerceIn(64.0, 4000.0)
        trackerDiscoveredNodeGuidanceRange = trackerDiscoveredNodeGuidanceRange.coerceIn(8.0, trackerDiscoveredNodeRange)
        questWaypointRange = questWaypointRange.coerceIn(64.0, 4000.0)
        trackerPingPitch = trackerPingPitch.coerceIn(0.5, 2.0)
        trackerWaypointScale = trackerWaypointScale.coerceIn(0.5, 2.5)
        trackerRules.forEach { it.minChestTier = it.minChestTier.coerceIn(0, 4) }
        weaponAnimationPreviewT = weaponAnimationPreviewT.coerceIn(0.0, 1.0)
        weaponAnimationSfxVolume = weaponAnimationSfxVolume.coerceIn(0.0, 1.0)
        weaponTrueIdleDelaySeconds = weaponTrueIdleDelaySeconds.coerceIn(0.5, 15.0)
        weaponAnimationTrailIntensity = weaponAnimationTrailIntensity.coerceIn(0.2, 1.5)
        if (LocomotionAnimations.styleNamed(locomotionStyle) == null) locomotionStyle = "HEROIC"
        mythicAlertVolume = mythicAlertVolume.coerceIn(0.0, 1.0)
        trackerPingVolume = trackerPingVolume.coerceIn(0.0, 1.0)
        WynnOverhaulToastQueue.Kind.entries.forEach {
            val t = toast(it)
            t.scale = t.scale.coerceIn(0.5, 4.0)
            t.durationSeconds = t.durationSeconds.coerceIn(1.5, 20.0)
            t.opacity = t.opacity.coerceIn(0.2, 1.0)
            t.soundVolume = t.soundVolume.coerceIn(0.0, 1.0)
        }
        soulsCameraDistance = soulsCameraDistance.coerceIn(1.5, 12.0)
        soulsCameraHeight = soulsCameraHeight.coerceIn(-1.0, 2.0)
        soulsCameraShoulder = soulsCameraShoulder.coerceIn(-1.5, 1.5)
        soulsCameraSensitivity = soulsCameraSensitivity.coerceIn(0.2, 3.0)
        soulsCameraSmoothing = soulsCameraSmoothing.coerceIn(0.0, 1.0)
        soulsCameraTurnSpeed = soulsCameraTurnSpeed.coerceIn(0.15, 1.0)
        soulsCameraFaceHoldMs = soulsCameraFaceHoldMs.coerceIn(0.0, 2000.0)
        if (mountRegistry == null) mountRegistry = mutableMapOf()
        if (weaponAnimationEntries == null) weaponAnimationEntries = mutableListOf()
        hudLayouts.values.forEach {
            it.scale = it.scale.coerceIn(0.5, 2.5)
            it.barWidth = it.barWidth.coerceIn(0, 2000)
            it.boxH = it.boxH.coerceIn(0, 2000)
        }
        return this
    }

    fun save() {
        try {
            sanitize()
            CONFIG_PATH.parent?.let { Files.createDirectories(it) }
            CONFIG_PATH.writeText(GSON.toJson(this))
        } catch (e: Exception) {
            WynnOverhaul.LOGGER.error("Failed to save wynnoverhaul config", e)
        }
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("wynnoverhaul.json")

        @Volatile
        private var loaded = false

        var current: WynnOverhaulConfig = WynnOverhaulConfig()
            private set

        fun ensureLoaded(): WynnOverhaulConfig {
            if (!loaded) {
                load()
                loaded = true
            }
            return current
        }

        fun load(): WynnOverhaulConfig {
            current = try {
                if (CONFIG_PATH.exists()) {
                    (GSON.fromJson(CONFIG_PATH.readText(), WynnOverhaulConfig::class.java) ?: WynnOverhaulConfig())
                        .sanitize()
                } else {
                    WynnOverhaulConfig().also { it.save() }
                }
            } catch (e: Exception) {
                WynnOverhaul.LOGGER.error("Failed to load wynnoverhaul config, using defaults", e)
                WynnOverhaulConfig()
            }
            return current
        }
    }
}

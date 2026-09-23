package opal.dev.overwatch.client

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import opal.dev.overwatch.Overwatch
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class OverwatchConfig(
    var enabled: Boolean = true,
    var maxCps: Double = 8.0,
    var requireEntityTarget: Boolean = true,
    var ignorePlayers: Boolean = true,
    var ignoreFakePlayers: Boolean = false,
    var combatSpellGuardEnabled: Boolean = true,
    var combatSpellGuardMs: Double = 1500.0,
    var wynnCombatEnabled: Boolean = false,
    var wynnAttackSpeed: Boolean = true,
    var qolPreventHotbarOverscroll: Boolean = false,
    var debugItemCopyEnabled: Boolean = false,
    var mythicAlertEnabled: Boolean = false,
    var mythicAlertMinRarity: String = "MYTHIC",
    var mythicAlertSound: Boolean = true,
    var mythicAlertChat: Boolean = true,
    var discordRpcEnabled: Boolean = true,
    var discordShowActivity: Boolean = true,
    var discordShowLevel: Boolean = true,
    var discordShowRegion: Boolean = true,
    var contentBookOverrideEnabled: Boolean = true,
    var contentBookSort: String = "RECOMMENDED",
    var contentBookFilter: String = "",
    var questWaypointOverrideEnabled: Boolean = true,
    var questWaypointRange: Double = 400.0,
    var voxyVistaEnabled: Boolean = true,
    var voxyVistaMessages: Boolean = true,
    var hideVanillaPotionHud: Boolean = false,
    var customPotionHudEnabled: Boolean = false,
    var abilityCooldownHudEnabled: Boolean = true,
    var questLogHudEnabled: Boolean = true,
    var hudPanelsEnabled: Boolean = false,
    var hudBarStyle: String = "CLASSIC",
    var questCompletionToastEnabled: Boolean = false,
    var levelUpToastEnabled: Boolean = false,
    var customPartyNametagsEnabled: Boolean = true,
    var mountTooltipEnabled: Boolean = true,
    var mountFeederHudEnabled: Boolean = true,
    var mountPickupDebug: Boolean = false,
    var lootrunEnabled: Boolean = true,
    var lootrunBeaconsEnabled: Boolean = true,
    var lootrunTaskMarkerEnabled: Boolean = true,
    var lootrunHudEnabled: Boolean = true,
    var lootrunRecorderEnabled: Boolean = false,
    var trackerEnabled: Boolean = false,
    var trackerWaypointsEnabled: Boolean = true,
    var trackerWaypointScale: Double = 1.0,
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
    var trackerHudShowDistance: Boolean = true,
    var trackerRules: MutableList<TrackerRule> = mutableListOf(),
    var hudLayouts: MutableMap<String, HudElementLayout> = mutableMapOf(),
    var customInventoryEnabled: Boolean = true,
    var customHudEnabled: Boolean = false,
    var chatChannel: String = "all",
    var mountRegistry: MutableMap<String, StoredMount>? = null,
) {
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
    )

    fun sanitize(): OverwatchConfig {
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
        if (mountRegistry == null) mountRegistry = mutableMapOf()
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
            Overwatch.LOGGER.error("Failed to save overwatch config", e)
        }
    }

    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().create()
        private val CONFIG_PATH = FabricLoader.getInstance().configDir.resolve("overwatch.json")

        @Volatile
        private var loaded = false

        var current: OverwatchConfig = OverwatchConfig()
            private set

        fun ensureLoaded(): OverwatchConfig {
            if (!loaded) {
                load()
                loaded = true
            }
            return current
        }

        fun load(): OverwatchConfig {
            current = try {
                if (CONFIG_PATH.exists()) {
                    (GSON.fromJson(CONFIG_PATH.readText(), OverwatchConfig::class.java) ?: OverwatchConfig())
                        .sanitize()
                } else {
                    OverwatchConfig().also { it.save() }
                }
            } catch (e: Exception) {
                Overwatch.LOGGER.error("Failed to load overwatch config, using defaults", e)
                OverwatchConfig()
            }
            return current
        }
    }
}

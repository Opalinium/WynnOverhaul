package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.Overwatch
import opal.dev.overwatch.mixin.client.ItemDisplayAccessor
import opal.dev.overwatch.mixin.client.TextDisplayAccessor
import java.util.Optional

object QuestBeaconTracker {

    @Volatile
    var current: List<EntityTracker.Match> = emptyList()
        private set

    @Volatile
    var hiddenEntityIds: Set<Int> = emptySet()
        private set

    @Volatile
    private var beaconPositions: List<Vec3> = emptyList()

    fun shouldHide(entity: Entity): Boolean {
        if (current.isEmpty()) return false
        if (entity.id in hiddenEntityIds) return true
        return when (entity) {
            is Display.ItemDisplay -> questKindOf(entity) != null
            is Display.TextDisplay -> nearBeacon(entity.position()) && isMarkerFont(entity)
            else -> false
        }
    }

    private fun nearBeacon(pos: Vec3): Boolean = beaconPositions.any { b ->
        val dx = b.x - pos.x
        val dz = b.z - pos.z
        dx * dx + dz * dz <= MARKER_PROXIMITY_SQR
    }

    fun tick(client: Minecraft) {
        val config = OverwatchConfig.current
        if (!config.questWaypointOverrideEnabled) {
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }

        val match = liveTextCoordinate() ?: wikiFallback() ?: wikiCoordFallback()
        if (match == null) {
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            if (beaconPositions.isNotEmpty()) beaconPositions = emptyList()
            return
        }

        val box = player.boundingBox.inflate(config.questWaypointRange)
        val hiddenIds = HashSet<Int>()
        val positions = ArrayList<Vec3>(2)
        val markerCandidates = ArrayList<Display.TextDisplay>()

        for (entity in level.getEntities(player, box) { it.isAlive }) {
            when (entity) {
                is Display.ItemDisplay -> {
                    if (questKindOf(entity) == null) continue
                    positions.add(entity.position())
                    hiddenIds.add(entity.id)
                }
                is Display.TextDisplay -> markerCandidates.add(entity)
                else -> {}
            }
        }
        beaconPositions = positions
        if (positions.isNotEmpty()) {
            for (marker in markerCandidates) {
                if (!nearBeacon(marker.position())) continue
                if (isMarkerFont(marker)) hiddenIds.add(marker.id)
            }
        }

        current = listOf(match)
        hiddenEntityIds = hiddenIds
    }

    private var lastDiagnostic: String? = null

    private fun diagnose(message: String) {
        if (message == lastDiagnostic) return
        lastDiagnostic = message
        Overwatch.LOGGER.info("Overwatch waypoint diagnostic: {}", message)
    }

    private fun liveTextCoordinate(): EntityTracker.Match? {
        val sidebar = WynnScoreboardTracker.sidebarText
        if (sidebar.isEmpty()) {
            diagnose("sidebar is empty")
            return null
        }
        val match = COORD_IN_TEXT.find(sidebar)
        if (match == null) {
            diagnose("no coordinate in sidebar='$sidebar'")
            return null
        }
        val x = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        val z = match.groupValues[3].toIntOrNull() ?: return null
        diagnose("using live coordinate [$x, $y, $z] from sidebar='$sidebar'")
        return waypointMatch(x, y, z, "Quest", QUEST_WAYPOINT_COLOR)
    }

    private fun wikiFallback(): EntityTracker.Match? {
        val tracked = WynnScoreboardTracker.current ?: return null
        val quest = WynncraftQuests.findTracked(tracked.name) ?: return null
        val stage = QuestWaypoints.findStageWaypoint(quest.name, tracked.nextTask) ?: return null
        val x = stage.x ?: return null
        val y = stage.y ?: return null
        val z = stage.z ?: return null
        return waypointMatch(x, y, z, "Quest (wiki)", WIKI_WAYPOINT_COLOR)
    }

    private fun wikiCoordFallback(): EntityTracker.Match? {
        val tracked = WynnScoreboardTracker.current ?: return null
        val cachedType = ContentBookCache.snapshot?.firstOrNull {
            it.trackingState == ActivityTrackingState.TRACKED && it.name.equals(tracked.name, ignoreCase = true)
        }?.type
        val candidateTypes = cachedType?.let { listOf(it) }
            ?: ActivityType.entries.filter { it.displayName.equals(tracked.type, ignoreCase = true) }
                .ifEmpty { discoveryTypesFor(tracked.type) }
        for (type in candidateTypes) {
            val coord = QuestWikiFetcher.find(type, tracked.name)?.coord ?: continue
            return waypointMatch(coord.x, coord.y, coord.z, "${tracked.type} (wiki)", WIKI_WAYPOINT_COLOR)
        }
        return null
    }

    private fun discoveryTypesFor(label: String): List<ActivityType> =
        if (label.contains("discovery", ignoreCase = true)) {
            listOf(ActivityType.WORLD_DISCOVERY, ActivityType.SECRET_DISCOVERY, ActivityType.TERRITORIAL_DISCOVERY)
        } else {
            emptyList()
        }

    private fun waypointMatch(x: Int, y: Int, z: Int, label: String, colorArgb: Int): EntityTracker.Match {
        val markerBox = AABB(x - 0.15, y.toDouble(), z - 0.15, x + 0.15, y + 1.6, z + 0.15)
        return EntityTracker.Match(anchor = null, blockBox = markerBox, label = label, colorArgb = colorArgb, throughWalls = true)
    }

    private val COORD_IN_TEXT = Regex("""\[\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*]""")

    private fun isMarkerFont(entity: Display.TextDisplay): Boolean {
        val text = (entity as TextDisplayAccessor).`overwatch$getText`()
        var result = false
        var seen = false
        text.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                if (!seen && string.isNotEmpty()) {
                    seen = true
                    result = style.font == MARKER_FONT
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return result
    }

    private fun questKindOf(entity: Display.ItemDisplay): String? {
        val stack = (entity as ItemDisplayAccessor).`overwatch$getItemStack`()
        if (stack.isEmpty || stack.item != Items.POTION) return null

        val customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: return null
        if (customModelData.floats().none { it in BEACON_COLOR_RANGE }) return null

        val potionContents = stack.get(DataComponents.POTION_CONTENTS) ?: return null
        val color = potionContents.customColor().orElse(null) ?: return null
        return QUEST_KIND_COLORS[color and 0xFFFFFF]
    }

    private val BEACON_COLOR_RANGE = 197.0f..222.0f

    private val QUEST_KIND_COLORS = mapOf(
        0x29CC96 to "Quest",
        0x33B33B to "Storyline Quest",
        0xB38FAD to "Mini-Quest",
    )

    private const val QUEST_WAYPOINT_COLOR = 0xFF29CC96.toInt()
    private const val WIKI_WAYPOINT_COLOR = 0xFFC9A227.toInt()
    private const val MARKER_PROXIMITY_SQR = 16.0
    private val MARKER_FONT: FontDescription = FontDescription.Resource(Identifier.withDefaultNamespace("marker"))
}

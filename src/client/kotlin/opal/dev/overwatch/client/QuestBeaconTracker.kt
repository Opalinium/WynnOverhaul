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
            is Display.TextDisplay -> isMarkerFont(entity)
            else -> false
        }
    }

    private fun nearBeacon(pos: Vec3): Boolean = beaconPositions.any { b -> withinMarkerProximity(b, pos) }

    private fun withinMarkerProximity(a: Vec3, b: Vec3): Boolean {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz <= MARKER_PROXIMITY_SQR
    }

    fun tick(client: Minecraft) {
        val config = OverwatchConfig.current
        if (!config.questWaypointOverrideEnabled) {
            QuestGoalTracker.reset()
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            QuestGoalTracker.reset()
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }

        val goals = QuestGoalTracker.update(player.x, player.y, player.z, WynnScoreboardTracker.current)
        val matches = goals.map { goalMatch(it) }.ifEmpty { listOfNotNull(wikiCoordFallback()) }
        if (matches.isEmpty()) {
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
        val matchPositions = matches.map { it.center() }
        for (marker in markerCandidates) {
            if (!isMarkerFont(marker)) continue
            val pos = marker.position()
            if (nearBeacon(pos) || matchPositions.any { withinMarkerProximity(pos, it) }) hiddenIds.add(marker.id)
        }

        current = matches
        hiddenEntityIds = hiddenIds
    }

    private fun goalMatch(goal: QuestGoalTracker.Goal): EntityTracker.Match = when (goal.source) {
        QuestGoalTracker.Source.LIVE -> waypointMatch(goal.x, goal.y, goal.z, goal.label, QUEST_WAYPOINT_COLOR)
        QuestGoalTracker.Source.WIKI -> waypointMatch(goal.x, goal.y, goal.z, wikiLabel("Quest (wiki)", goal.label), WIKI_WAYPOINT_COLOR)
        QuestGoalTracker.Source.WIKI_APPROX ->
            waypointMatch(goal.x, goal.y, goal.z, wikiLabel("Quest (wiki, approx.)", goal.label), WIKI_WAYPOINT_COLOR)
    }

    private fun wikiLabel(base: String, tag: String): String = if (tag.isEmpty()) base else "$base: $tag"

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

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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import opal.dev.overwatch.mixin.client.ItemDisplayAccessor
import opal.dev.overwatch.mixin.client.TextDisplayAccessor
import java.util.Optional
import kotlin.math.floor

object QuestBeaconTracker {
    @Volatile
    var current: List<EntityTracker.Match> = emptyList()
        private set

    @Volatile
    var hiddenEntityIds: Set<Int> = emptySet()
        private set

    @Volatile
    private var beaconPositions: List<Vec3> = emptyList()

    @Volatile
    private var hideUntilNanos = 0L

    fun shouldHide(entity: Entity): Boolean {
        if (current.isEmpty() && System.nanoTime() > hideUntilNanos) return false
        if (entity.id in hiddenEntityIds) return true
        return when (entity) {
            is Display.ItemDisplay -> activityKindOf(entity) != null
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
            BeaconTriangulator.reset()
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            QuestGoalTracker.reset()
            BeaconTriangulator.reset()
            if (current.isNotEmpty()) current = emptyList()
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            return
        }

        val tracked = WynnScoreboardTracker.current
        val goals = QuestGoalTracker.update(player.x, player.y, player.z, tracked)
        val goalMatches = goals.map { goalMatch(it) }
        if (tracked == null && goalMatches.isEmpty()) {
            BeaconTriangulator.reset()
            clearMatches()
            return
        }

        val box = player.boundingBox.inflate(config.questWaypointRange)
        val hiddenIds = HashSet<Int>()
        val positions = ArrayList<Vec3>(2)
        val beacons = ArrayList<Pair<Vec3, ActivityType>>(2)
        val markerCandidates = ArrayList<Display.TextDisplay>()

        for (entity in level.getEntities(player, box) { it.isAlive }) {
            when (entity) {
                is Display.ItemDisplay -> {
                    val kind = activityKindOf(entity) ?: continue
                    positions.add(entity.position())
                    beacons.add(entity.position() to kind)
                    hiddenIds.add(entity.id)
                }
                is Display.TextDisplay -> markerCandidates.add(entity)
                else -> {}
            }
        }

        val wikiGoalMatches = goals.filter { it.source != QuestGoalTracker.Source.LIVE }.map { goalMatch(it) }
        val liveGoalMatches = goals.filter { it.source == QuestGoalTracker.Source.LIVE }.map { goalMatch(it) }
        val questTracked = tracked != null && ActivityType.entries.any { it.isQuest && it.displayName.equals(tracked.type, ignoreCase = true) }
        val pageCoord = if (questTracked) null else wikiCoordFallback()
        val matches = when {
            wikiGoalMatches.isNotEmpty() -> wikiGoalMatches
            pageCoord != null -> listOf(pageCoord)
            liveGoalMatches.isNotEmpty() -> liveGoalMatches
            else -> triangulate(player.position(), beacons, markerCandidates, tracked)
                .ifEmpty { listOfNotNull(if (questTracked) wikiCoordFallback() else null) }
        }
        if (matches.isEmpty()) {
            clearMatches()
            return
        }

        hideUntilNanos = System.nanoTime() + HIDE_GRACE_NANOS
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

    private fun clearMatches() {
        if (current.isNotEmpty()) current = emptyList()
        if (System.nanoTime() > hideUntilNanos) {
            if (hiddenEntityIds.isNotEmpty()) hiddenEntityIds = emptySet()
            if (beaconPositions.isNotEmpty()) beaconPositions = emptyList()
        }
    }

    private fun goalMatch(goal: QuestGoalTracker.Goal): EntityTracker.Match = when (goal.source) {
        QuestGoalTracker.Source.LIVE -> waypointMatch(goal.x, goal.y, goal.z, goal.label, QUEST_WAYPOINT_COLOR, WaypointIcons.activity(ActivityType.QUEST))
        QuestGoalTracker.Source.WIKI -> waypointMatch(goal.x, goal.y, goal.z, wikiLabel("Quest", goal.label), WIKI_WAYPOINT_COLOR, WaypointIcons.activity(ActivityType.QUEST))
        QuestGoalTracker.Source.WIKI_APPROX ->
            waypointMatch(goal.x, goal.y, goal.z, wikiLabel("Quest", goal.label), WIKI_WAYPOINT_COLOR, WaypointIcons.activity(ActivityType.QUEST))
    }

    private fun triangulate(
        player: Vec3,
        beacons: List<Pair<Vec3, ActivityType>>,
        markers: List<Display.TextDisplay>,
        tracked: WynnScoreboardTracker.Tracked?,
    ): List<EntityTracker.Match> {
        val fresh = HashMap<ActivityType, Vec3>(2)
        for (marker in markers) {
            if (!isMarkerFont(marker)) continue
            val text = (marker as TextDisplayAccessor).`overwatch$getText`().string
            val distance = markerDistance(text) ?: continue
            val position = marker.position()
            val kind = markerKind(text)
                ?: beacons.firstOrNull { withinMarkerProximity(it.first, position) }?.second
                ?: continue
            if (kind.isQuest || kind in fresh) continue
            fresh[kind] = BeaconTriangulator.estimate(kind, player, position, distance)
        }
        val targets = HashMap<ActivityType, Vec3>(BeaconTriangulator.recent())
        targets.putAll(fresh)
        return targets.map { (kind, target) -> triangulatedMatch(target, kind, tracked) }
    }

    private fun triangulatedMatch(target: Vec3, kind: ActivityType, tracked: WynnScoreboardTracker.Tracked?): EntityTracker.Match {
        val sameKind = tracked != null && (
            tracked.type.equals(kind.displayName, ignoreCase = true) ||
                (kind == ActivityType.WORLD_DISCOVERY && tracked.type.contains("discovery", ignoreCase = true))
            )
        val label = if (sameKind && tracked!!.name.isNotBlank()) "${kind.displayName}: ${tracked.name}" else kind.displayName
        return waypointMatch(floor(target.x).toInt(), floor(target.y).toInt(), floor(target.z).toInt(), label, kind.colorArgb, WaypointIcons.activity(kind))
    }

    private fun markerKind(raw: String): ActivityType? {
        val icon = MARKER_ICON.find(raw)?.groupValues?.get(1)?.firstOrNull() ?: return null
        return MARKER_ICON_KINDS[icon]
    }

    private fun markerDistance(raw: String): Double? {
        val text = SECTION_CODE.replace(raw, "")
        val match = MARKER_DISTANCE.find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return if (match.groupValues[2].equals("km", ignoreCase = true)) value * 1000.0 else value
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
            return waypointMatch(coord.x, coord.y, coord.z, tracked.type, WIKI_WAYPOINT_COLOR, WaypointIcons.activity(type))
        }
        return null
    }

    private fun discoveryTypesFor(label: String): List<ActivityType> =
        if (label.contains("discovery", ignoreCase = true)) {
            listOf(ActivityType.WORLD_DISCOVERY, ActivityType.SECRET_DISCOVERY, ActivityType.TERRITORIAL_DISCOVERY)
        } else {
            emptyList()
        }

    private fun waypointMatch(x: Int, y: Int, z: Int, label: String, colorArgb: Int, icon: ItemStack): EntityTracker.Match {
        val markerBox = AABB(x - 0.15, y.toDouble(), z - 0.15, x + 0.15, y + 1.6, z + 0.15)
        return EntityTracker.Match(anchor = null, blockBox = markerBox, label = label, colorArgb = colorArgb, throughWalls = true, icon = icon)
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

    private fun activityKindOf(entity: Display.ItemDisplay): ActivityType? {
        val stack = (entity as ItemDisplayAccessor).`overwatch$getItemStack`()
        if (stack.isEmpty || stack.item != Items.POTION) return null

        val customModelData = stack.get(DataComponents.CUSTOM_MODEL_DATA) ?: return null
        if (customModelData.floats().none { it in BEACON_COLOR_RANGE }) return null

        val potionContents = stack.get(DataComponents.POTION_CONTENTS) ?: return null
        val color = potionContents.customColor().orElse(null) ?: return null
        return ACTIVITY_BEACON_COLORS[color and 0xFFFFFF]
    }

    private val BEACON_COLOR_RANGE = 197.0f..222.0f

    private val ACTIVITY_BEACON_COLORS = mapOf(
        0x29CC96 to ActivityType.QUEST,
        0x33B33B to ActivityType.STORYLINE_QUEST,
        0xB38FAD to ActivityType.MINI_QUEST,
        0x00BDBF to ActivityType.WORLD_EVENT,
        0xA1C3E6 to ActivityType.WORLD_DISCOVERY,
        0xFF8C19 to ActivityType.CAVE,
        0xCC6677 to ActivityType.DUNGEON,
        0xD6401E to ActivityType.RAID,
        0xF2D349 to ActivityType.BOSS_ALTAR,
        0x3399CC to ActivityType.LOOTRUN_CAMP,
    )

    private const val QUEST_WAYPOINT_COLOR = 0xFF29CC96.toInt()
    private const val WIKI_WAYPOINT_COLOR = 0xFFC9A227.toInt()
    private const val MARKER_PROXIMITY_SQR = 16.0
    private const val HIDE_GRACE_NANOS = 3_000_000_000L
    private val MARKER_ICON = Regex("[\uE010-\uE014]\uDAFF\uDFDE([\uE001-\uE00B])")
    private val MARKER_ICON_KINDS = mapOf(
        '\uE007' to ActivityType.QUEST,
        '\uE009' to ActivityType.STORYLINE_QUEST,
        '\uE006' to ActivityType.MINI_QUEST,
        '\uE00A' to ActivityType.WORLD_EVENT,
        '\uE002' to ActivityType.WORLD_DISCOVERY,
        '\uE003' to ActivityType.CAVE,
        '\uE004' to ActivityType.DUNGEON,
        '\uE008' to ActivityType.RAID,
        '\uE001' to ActivityType.BOSS_ALTAR,
        '\uE005' to ActivityType.LOOTRUN_CAMP,
    )
    private val SECTION_CODE = Regex("§.")
    private val MARKER_DISTANCE = Regex("""(\d+(?:\.\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val MARKER_FONT: FontDescription = FontDescription.Resource(Identifier.withDefaultNamespace("marker"))
}

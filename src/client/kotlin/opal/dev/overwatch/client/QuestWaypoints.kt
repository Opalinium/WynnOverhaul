package opal.dev.overwatch.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import opal.dev.overwatch.Overwatch

object QuestWaypoints {

    data class Stage(val stage: Int, val task: String?, val x: Int? = null, val y: Int? = null, val z: Int? = null) {
        val hasCoord: Boolean get() = x != null && y != null && z != null
    }

    data class WaypointResult(val stage: Stage, val approximate: Boolean)

    private val byQuest: Map<String, List<Stage>> by lazy { load() }

    fun hasQuest(questName: String): Boolean = byQuest.containsKey(questName)

    fun findCurrentStage(questName: String, liveTaskText: String): Stage? {
        val stages = byQuest[questName] ?: return null
        if (liveTaskText.isBlank()) return null
        val liveWords = significantWords(liveTaskText)
        if (liveWords.isEmpty()) return null

        var best: Stage? = null
        var bestScore = 0
        for (stage in stages) {
            if (stage.task.isNullOrBlank()) continue
            val score = significantWords(stage.task).intersect(liveWords).size
            if (score > bestScore) {
                bestScore = score
                best = stage
            }
        }
        return if (bestScore >= MIN_MATCH_WORDS) best else null
    }

    fun findStageWaypoint(questName: String, liveTaskText: String): WaypointResult? {
        val stages = byQuest[questName] ?: return null
        val current = findCurrentStage(questName, liveTaskText) ?: return null
        if (current.hasCoord) return WaypointResult(current, approximate = false)
        val nearest = stages.filter { it.hasCoord }.minByOrNull { kotlin.math.abs(it.stage - current.stage) } ?: return null
        return WaypointResult(nearest, approximate = true)
    }

    private fun significantWords(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9']+"))
            .filterTo(HashSet()) { it.length > 3 && it !in STOP_WORDS }

    private fun load(): Map<String, List<Stage>> {
        return try {
            val stream = QuestWaypoints::class.java.getResourceAsStream("/assets/overwatch/quest_waypoints.json")
                ?: return emptyMap()
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val type = object : TypeToken<Map<String, List<Stage>>>() {}.type
                Gson().fromJson<Map<String, List<Stage>>>(reader, type) ?: emptyMap()
            }
        } catch (t: Throwable) {
            Overwatch.LOGGER.error("Failed to load bundled quest waypoint data", t)
            emptyMap()
        }
    }

    private const val MIN_MATCH_WORDS = 2
    private val STOP_WORDS = setOf(
        "talk", "find", "return", "with", "from", "near", "into", "that", "your",
        "then", "have", "this", "them", "back", "head", "while", "once", "will",
    )
}

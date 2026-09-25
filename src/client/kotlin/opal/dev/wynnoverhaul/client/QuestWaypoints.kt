package opal.dev.wynnoverhaul.client

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import opal.dev.wynnoverhaul.WynnOverhaul
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

object QuestWaypoints {
    data class Goal(val x: Int, val y: Int, val z: Int, val role: String? = null, val label: String? = null)

    data class Stage(
        val stage: Int,
        val task: String?,
        val x: Int? = null,
        val y: Int? = null,
        val z: Int? = null,
        val goals: List<Goal>? = null,
    ) {
        val hasCoord: Boolean get() = x != null && y != null && z != null
    }

    data class StageMatch(val stage: Stage, val score: Double)

    data class GoalSet(val goals: List<Goal>, val approximate: Boolean)

    data class WaypointResult(val stage: Stage, val goals: List<Goal>, val approximate: Boolean)

    private class QuestIndex(val stages: List<Stage>) {
        val tokens: List<Set<String>> = stages.map { tokenize(it.task.orEmpty()) }
        val idf: Map<String, Double>
        val unknownIdf: Double

        init {
            val docFreq = HashMap<String, Int>()
            for (set in tokens) for (word in set) docFreq.merge(word, 1, Int::plus)
            val n = stages.size.coerceAtLeast(1).toDouble()
            idf = docFreq.mapValues { (_, df) -> ln(1.0 + n / df) }
            unknownIdf = ln(1.0 + n) * UNKNOWN_IDF_FACTOR
        }

        fun weight(word: String): Double = idf[word] ?: unknownIdf
    }

    private val byQuest: Map<String, List<Stage>> by lazy { load() }
    private val indexes = HashMap<String, QuestIndex>()
    private val keysByNormalized: Map<String, String> by lazy { byQuest.keys.associateBy { normalizeName(it) } }

    fun hasQuest(questName: String): Boolean = resolveQuest(questName) != null

    fun resolveQuest(name: String): String? {
        if (name.isBlank()) return null
        if (byQuest.containsKey(name)) return name
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return null
        keysByNormalized[normalized]?.let { return it }
        if (normalized.length < MIN_CONTAINS_LENGTH) return null
        return keysByNormalized.entries
            .filter { (key, _) -> key.length >= MIN_CONTAINS_LENGTH && (key.contains(normalized) || normalized.contains(key)) }
            .minByOrNull { abs(it.key.length - normalized.length) }
            ?.value
    }

    fun stages(questName: String): List<Stage> {
        val key = resolveQuest(questName) ?: return emptyList()
        return byQuest[key].orEmpty()
    }

    fun matchStage(questName: String, liveTaskText: String, hintStage: Int? = null): StageMatch? {
        val key = resolveQuest(questName) ?: return null
        if (liveTaskText.isBlank()) return null
        val index = indexFor(key) ?: return null
        val live = tokenize(liveTaskText)
        if (live.isEmpty()) return null

        val liveNorm = norm(live, index)
        if (liveNorm == 0.0) return null

        val scores = DoubleArray(index.stages.size)
        for (i in index.stages.indices) {
            val stageTokens = index.tokens[i]
            if (stageTokens.isEmpty()) continue
            var dot = 0.0
            for (word in live) {
                if (word in stageTokens) {
                    val w = index.weight(word)
                    dot += w * w
                }
            }
            if (dot == 0.0) continue
            var score = dot / (liveNorm * norm(stageTokens, index))
            if (hintStage != null && index.stages[i].stage < hintStage) score *= BACKWARD_PENALTY
            scores[i] = score
        }

        val best = scores.maxOrNull() ?: return null
        if (best < MIN_SCORE) return null

        var chosen = scores.indexOfFirst { it == best }
        if (hintStage != null) {
            val tied = scores.indices.filter { scores[it] >= best - TIE_MARGIN && index.stages[it].stage >= hintStage }
            tied.minByOrNull { index.stages[it].stage }?.let { chosen = it }
        }
        return StageMatch(index.stages[chosen], scores[chosen])
    }

    fun findCurrentStage(questName: String, liveTaskText: String, hintStage: Int? = null): Stage? =
        matchStage(questName, liveTaskText, hintStage)?.stage

    fun goalsFor(stage: Stage): GoalSet {
        val all = stage.goals.orEmpty()
        val primary = all.filter { it.role == ROLE_TASK || it.role == ROLE_DEST }
        if (primary.isNotEmpty()) return GoalSet(primary.take(MAX_GOALS), approximate = false)
        val prose = all.filter { it.role == ROLE_PROSE }
        if (prose.size in 2..MAX_PROSE_GROUP) return GoalSet(prose, approximate = true)
        prose.firstOrNull()?.let { return GoalSet(listOf(it), approximate = true) }
        if (all.isEmpty() && stage.hasCoord) return GoalSet(listOf(Goal(stage.x!!, stage.y!!, stage.z!!)), approximate = false)
        return GoalSet(emptyList(), approximate = false)
    }

    fun findStageWaypoint(questName: String, liveTaskText: String, hintStage: Int? = null): WaypointResult? {
        val match = matchStage(questName, liveTaskText, hintStage) ?: return null
        val current = match.stage
        val direct = goalsFor(current)
        if (direct.goals.isNotEmpty()) return WaypointResult(current, direct.goals, direct.approximate)
        val key = resolveQuest(questName) ?: return null
        val nearest = byQuest[key].orEmpty()
            .filter { goalsFor(it).goals.isNotEmpty() }
            .minByOrNull { abs(it.stage - current.stage) } ?: return null
        return WaypointResult(nearest, goalsFor(nearest).goals.take(1), approximate = true)
    }

    fun isWorkStage(stage: Stage): Boolean = WORK_TASK.containsMatchIn(stage.task.orEmpty())

    private fun indexFor(key: String): QuestIndex? {
        indexes[key]?.let { return it }
        val stages = byQuest[key] ?: return null
        return QuestIndex(stages).also { indexes[key] = it }
    }

    private fun norm(words: Set<String>, index: QuestIndex): Double {
        var sum = 0.0
        for (word in words) {
            val w = index.weight(word)
            sum += w * w
        }
        return sqrt(sum)
    }

    internal fun tokenize(text: String): Set<String> {
        val cleaned = PROGRESS.replace(COORD.replace(text.lowercase(), " "), " ")
        val out = LinkedHashSet<String>()
        for (m in TOKEN.findAll(cleaned)) {
            val word = stem(m.value)
            if (word.length >= MIN_WORD_LENGTH && word !in STOP_WORDS && !word.all { it.isDigit() }) out.add(word)
        }
        return out
    }

    private fun stem(word: String): String {
        var w = word
        if (w.length > 4 && w.endsWith("ies")) return w.dropLast(3) + "y"
        if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us")) w = w.dropLast(1)
        if (w.length > 5 && w.endsWith("ing")) w = w.dropLast(3)
        else if (w.length > 4 && w.endsWith("ed")) w = w.dropLast(2)
        return w
    }

    private fun normalizeName(name: String): String =
        name.lowercase().replace(QUEST_SUFFIX, "").filter { it.isLetterOrDigit() }

    private fun load(): Map<String, List<Stage>> {
        return try {
            val stream = QuestWaypoints::class.java.getResourceAsStream("/assets/wynnoverhaul/quest_waypoints.json")
                ?: return emptyMap()
            stream.bufferedReader(Charsets.UTF_8).use { reader ->
                val type = object : TypeToken<Map<String, List<Stage>>>() {}.type
                Gson().fromJson<Map<String, List<Stage>>>(reader, type) ?: emptyMap()
            }
        } catch (t: Throwable) {
            WynnOverhaul.LOGGER.error("Failed to load bundled quest waypoint data", t)
            emptyMap()
        }
    }

    const val ROLE_TASK = "task"
    const val ROLE_DEST = "dest"
    const val ROLE_PROSE = "prose"

    private const val MAX_GOALS = 6
    private const val MAX_PROSE_GROUP = 6
    private const val MIN_SCORE = 0.40
    private const val TIE_MARGIN = 0.04
    private const val BACKWARD_PENALTY = 0.9
    private const val UNKNOWN_IDF_FACTOR = 0.5
    private const val MIN_WORD_LENGTH = 3
    private const val MIN_CONTAINS_LENGTH = 6

    private val TOKEN = Regex("[a-z0-9]+")
    private val COORD = Regex("""\[?\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*]?""")
    private val PROGRESS = Regex("""#?\d*\s*/\s*\d+""")
    private val QUEST_SUFFIX = Regex("""\((mini-?)?quest\)""")
    private val WORK_TASK = Regex("""(?i)\b(kill|slay|defeat|destroy|collect|gather|bring|obtain|retrieve|loot|fetch|clear|craft|deliver|smash|burn)\b""")
    private val STOP_WORDS = setOf(
        "the", "and", "for", "you", "are", "his", "her", "its", "out", "all", "get", "can", "use", "one",
        "two", "him", "she", "they", "their", "there", "where", "when", "what", "who", "how", "but", "not",
        "has", "had", "was", "were", "been", "would", "should", "could", "about", "over", "under", "onto",
        "upon", "with", "from", "near", "into", "that", "your", "then", "have", "this", "them", "back",
        "while", "once", "will", "some", "any", "more", "than", "also", "just", "only", "each", "other",
    )
}

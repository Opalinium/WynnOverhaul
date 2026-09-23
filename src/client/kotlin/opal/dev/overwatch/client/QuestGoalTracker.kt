package opal.dev.overwatch.client

import kotlin.math.abs
import kotlin.math.sqrt

object QuestGoalTracker {

    enum class Source { LIVE, WIKI, WIKI_APPROX }

    class Goal(val x: Int, val y: Int, val z: Int, val label: String, val source: Source) {
        val key: Long get() = pack(x, y, z)
    }

    class LiveGoal(val x: Int, val y: Int, val z: Int, val label: String, val done: Boolean)

    private class Held(var goal: Goal, var lastSeenTick: Int)

    private var ticks = 0
    private var lastInputTick = 0
    private var lastMatchTick = 0
    private var questKey: String? = null
    private var stageNumber: Int? = null
    private val held = LinkedHashMap<Long, Held>()
    private val reached = HashSet<Long>()

    fun reset() {
        questKey = null
        stageNumber = null
        held.clear()
        reached.clear()
    }

    fun update(px: Double, py: Double, pz: Double, tracked: WynnScoreboardTracker.Tracked?): List<Goal> {
        ticks++
        if (tracked == null || tracked.nextTask.isBlank()) {
            if (ticks - lastInputTick > GRACE_TICKS) reset()
            return snapshot()
        }
        lastInputTick = ticks

        val key = tracked.name.lowercase()
        if (key != questKey) {
            reset()
            questKey = key
        }

        val live = parseLive(tracked.nextTask)
        if (live.isNotEmpty()) return applyLive(live)
        return applyWiki(px, py, pz, tracked)
    }

    private fun snapshot(): List<Goal> = held.values.map { it.goal }

    private fun applyLive(parsed: List<LiveGoal>): List<Goal> {
        if (held.values.any { it.goal.source != Source.LIVE }) {
            held.clear()
            reached.clear()
            stageNumber = null
        }
        val fresh = parsed.filter { !it.done }
        val freshGoals = fresh.map { Goal(it.x, it.y, it.z, it.label, Source.LIVE) }

        if (freshGoals.isNotEmpty() && held.isNotEmpty() && freshGoals.none { it.key in held }) held.clear()

        for (goal in freshGoals) {
            val existing = held[goal.key]
            if (existing != null) {
                existing.goal = goal
                existing.lastSeenTick = ticks
            } else {
                held[goal.key] = Held(goal, ticks)
            }
        }
        val iterator = held.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (ticks - entry.value.lastSeenTick > MISSING_TICKS) iterator.remove()
        }
        return snapshot()
    }

    private fun applyWiki(px: Double, py: Double, pz: Double, tracked: WynnScoreboardTracker.Tracked): List<Goal> {
        if (held.values.any { it.goal.source == Source.LIVE }) {
            held.clear()
            stageNumber = null
        }
        val questName = WynncraftQuests.findTracked(tracked.name)?.name ?: tracked.name
        val result = QuestWaypoints.findStageWaypoint(questName, tracked.nextTask, stageNumber)
        if (result == null) {
            if (ticks - lastMatchTick > GRACE_TICKS) {
                held.clear()
                reached.clear()
                stageNumber = null
            }
            return snapshot()
        }
        lastMatchTick = ticks

        if (result.stage.stage != stageNumber) {
            stageNumber = result.stage.stage
            reached.clear()
            held.clear()
        }

        val source = if (result.approximate) Source.WIKI_APPROX else Source.WIKI
        val total = result.goals.size
        val work = QuestWaypoints.isWorkStage(result.stage)
        val labels = result.goals.map { it.label?.trim().orEmpty() }
        val useLabels = labels.all { it.isNotEmpty() && it.length <= MAX_LABEL } && labels.toSet().size == labels.size
        val candidates = result.goals.mapIndexed { index, g ->
            Goal(g.x, g.y, g.z, wikiLabel(if (useLabels) labels[index] else null, index, total), source)
        }

        if (total > 1 && !work) {
            var remaining = candidates.count { it.key !in reached }
            for (goal in candidates) {
                if (remaining <= 1) break
                if (goal.key !in reached && withinReach(px, py, pz, goal)) {
                    reached.add(goal.key)
                    remaining--
                }
            }
        }

        val visible = candidates.filter { it.key !in reached }
        held.clear()
        for (goal in visible) held[goal.key] = Held(goal, ticks)
        return snapshot()
    }

    private fun wikiLabel(goalLabel: String?, index: Int, total: Int): String {
        if (total <= 1) return ""
        val tag = goalLabel?.takeIf { it.isNotBlank() && it.length <= MAX_LABEL } ?: "${index + 1}/$total"
        return tag
    }

    private fun withinReach(px: Double, py: Double, pz: Double, goal: Goal): Boolean {
        val dx = px - (goal.x + 0.5)
        val dz = pz - (goal.z + 0.5)
        val dy = py - goal.y
        return sqrt(dx * dx + dz * dz) <= REACH_HORIZONTAL && abs(dy) <= REACH_VERTICAL
    }

    fun parseLive(text: String): List<LiveGoal> {
        val coords = COORD.findAll(text).toList()
        if (coords.isEmpty()) return emptyList()
        val out = ArrayList<LiveGoal>(coords.size)
        var segmentStart = 0
        for ((index, m) in coords.withIndex()) {
            val x = m.groupValues[1].toIntOrNull()
            val y = m.groupValues[2].toIntOrNull()
            val z = m.groupValues[3].toIntOrNull()
            val segment = text.substring(segmentStart, m.range.first)
            val trailingEnd = if (index + 1 < coords.size) coords[index + 1].range.first else text.length
            segmentStart = m.range.last + 1
            if (x == null || y == null || z == null) continue

            val items = itemsIn(segment)
            val label = when {
                items.isNotEmpty() -> "Quest: " + items.joinToString(", ") { it.name }.take(MAX_LABEL)
                else -> clauseLabel(segment)?.let { "Quest: $it" } ?: if (coords.size > 1) "Quest ${index + 1}/${coords.size}" else "Quest"
            }
            val allDone = items.isNotEmpty() && items.all { it.done }
            val trailing = text.substring(m.range.last + 1, trailingEnd)
            val trailingDone = items.isEmpty() && isCounterComplete(trailing.take(TRAILING_WINDOW))
            out.add(LiveGoal(x, y, z, label, done = allDone || trailingDone))
        }
        return out
    }

    private class Item(val name: String, val done: Boolean)

    private fun itemsIn(segment: String): List<Item> {
        val items = ArrayList<Item>(2)
        for (m in BRACKET.findAll(segment)) {
            val raw = m.groupValues[1].trim()
            if (raw.isEmpty() || raw.none { it.isLetter() }) continue
            val progress = PROGRESS_ITEM.matchEntire(raw)
            if (progress != null) {
                val current = progress.groupValues[1].toIntOrNull() ?: 0
                val max = progress.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
                items.add(Item(progress.groupValues[3].trim(), current >= max))
                continue
            }
            val counted = COUNTED_ITEM.matchEntire(raw)
            items.add(Item(counted?.groupValues?.get(2)?.trim() ?: raw, done = false))
        }
        return items
    }

    private fun isCounterComplete(text: String): Boolean {
        val m = COUNTER.find(text) ?: return false
        val current = m.groupValues[1].toIntOrNull() ?: return false
        val max = m.groupValues[2].toIntOrNull() ?: return false
        return max > 0 && current >= max
    }

    private fun clauseLabel(segment: String): String? {
        var clause = segment.substringAfterLast('.').substringAfterLast(';').substringAfterLast(':').trim()
        if (clause.isEmpty()) return null
        clause = TRAILING_PREPOSITION.replace(clause, "").trim()
        clause = clause.replace(BRACKETS, "").replace(Regex("\\s+"), " ").trim()
        if (clause.isEmpty()) return null
        if (clause.length > MAX_LABEL) {
            val cut = clause.substring(0, MAX_LABEL)
            clause = cut.substringBeforeLast(' ', cut).ifEmpty { cut }
        }
        return clause.replaceFirstChar { it.uppercaseChar() }
    }

    private fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

    private const val GRACE_TICKS = 60
    private const val MISSING_TICKS = 30
    private const val REACH_HORIZONTAL = 6.0
    private const val REACH_VERTICAL = 8.0
    private const val MAX_LABEL = 32
    private const val TRAILING_WINDOW = 24

    private val COORD = Regex("""\[\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*]""")
    private val BRACKET = Regex("""\[([^\[\]]+)]""")
    private val BRACKETS = Regex("""[\[\]]""")
    private val PROGRESS_ITEM = Regex("""(\d+)\s*/\s*(\d+)\s+(.+)""")
    private val COUNTED_ITEM = Regex("""(\d+)\s*x?\s+(.+)""")
    private val COUNTER = Regex("""(\d+)\s*/\s*(\d+)""")
    private val TRAILING_PREPOSITION = Regex("""(?i)\s+(at|in|near|from|to|by|on|inside|outside|around|within|and|or|then)\s*$""")
}

package opal.dev.overwatch.client

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

object LootrunModel {
    enum class State { NOT_RUNNING, CHOOSING_BEACON, IN_TASK }
    enum class TaskType { LOOT, SLAY, DESTROY, DEFEND, UNKNOWN }

    var state: State = State.NOT_RUNNING
        private set
    var taskType: TaskType? = null
        private set
    var lootCurrent: Int = 0
        private set
    var lootTotal: Int = 0
        private set
    var timeLeftSeconds: Int? = null
        private set
    var challengesDone: Int = 0
        private set
    var challengesTotal: Int = 0
        private set

    fun tick(mc: Minecraft) {
        val scoreboard = mc.level?.scoreboard
        val objective = scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)
        if (scoreboard == null || objective == null) {
            reset()
            return
        }

        val lines = scoreboard.listPlayerScores(objective)
            .filterNot { it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() })
            .take(15)
            .map { entry -> TextClean.clean(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).string) }

        val headerIndex = lines.indexOfFirst { it == "Lootrun:" }
        if (headerIndex < 0) {
            reset()
            return
        }

        var matchedState = false
        for (i in headerIndex + 1 until lines.size) {
            val text = lines[i]
            if (text.isEmpty()) break

            if (!matchedState) {
                CHOOSE_BEACON.matchEntire(text)?.let { state = State.CHOOSING_BEACON; taskType = null; matchedState = true }
                WARPING_BACK.matchEntire(text)?.let { state = State.NOT_RUNNING; taskType = null; matchedState = true }
                LOOT_TASK.matchEntire(text)?.let {
                    state = State.IN_TASK; taskType = TaskType.LOOT
                    lootCurrent = it.groupValues[1].toIntOrNull() ?: 0
                    lootTotal = it.groupValues[2].toIntOrNull() ?: 0
                    matchedState = true
                }
                SLAY_TASK.matchEntire(text)?.let { state = State.IN_TASK; taskType = TaskType.SLAY; matchedState = true }
                DESTROY_TASK.matchEntire(text)?.let { state = State.IN_TASK; taskType = TaskType.DESTROY; matchedState = true }
                DEFEND_TASK.matchEntire(text)?.let { state = State.IN_TASK; taskType = TaskType.DEFEND; matchedState = true }
                if (matchedState) continue
            }

            TIME_LEFT.matchEntire(text)?.let {
                val minutes = it.groupValues[1].toIntOrNull() ?: 0
                val seconds = it.groupValues[2].toIntOrNull() ?: 0
                timeLeftSeconds = minutes * 60 + seconds
            }
            CHALLENGES.matchEntire(text)?.let {
                challengesDone = it.groupValues[1].toIntOrNull() ?: 0
                challengesTotal = it.groupValues[2].toIntOrNull() ?: 0
            }
        }

        if (!matchedState && state == State.NOT_RUNNING) taskType = null
    }

    private fun reset() {
        if (state != State.NOT_RUNNING) state = State.NOT_RUNNING
        taskType = null
        timeLeftSeconds = null
        challengesDone = 0
        challengesTotal = 0
        lootCurrent = 0
        lootTotal = 0
    }

    private val CHOOSE_BEACON = Regex("""Choose a beacon!""")
    private val WARPING_BACK = Regex("""Warping back to camp!""")
    private val LOOT_TASK = Regex("""Loot (\d+)/(\d+) chests!""")
    private val SLAY_TASK = Regex("""Slay! Wave (\d+) [-—] (\d+) (Target|Mob)s? Left!""")
    private val DESTROY_TASK = Regex("""Destroy the objective!""")
    private val DEFEND_TASK = Regex("""Defend for (\d+)s!""")
    private val TIME_LEFT = Regex("""[-—] Time Left: (\d+):(\d+)(?: \[[+-]\d+[msMS]])?""")
    private val CHALLENGES = Regex("""[-—] Challenges: (\d+)/(\d+)(?: \[[+-]\d+])?""")
}

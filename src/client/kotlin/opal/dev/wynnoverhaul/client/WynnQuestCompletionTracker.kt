package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnQuestCompletionTracker {
    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
        ClientTickEvents.END_CLIENT_TICK.register { flushDungeon(false) }
    }

    private class DungeonRun(val name: String, val suppress: Boolean, val startedAt: Long) {
        val rewards = ArrayList<String>()
        var lastAt = startedAt

        fun expired(now: Long): Boolean = now - lastAt >= DUNGEON_WINDOW_MILLIS || now - startedAt >= DUNGEON_MAX_MILLIS
    }

    private var dungeon: DungeonRun? = null

    private fun flushDungeon(force: Boolean) {
        val run = dungeon ?: return
        if (!force && !run.expired(System.currentTimeMillis())) return
        dungeon = null
        if (!run.suppress) return
        val xp = run.rewards.firstOrNull { DUNGEON_XP.containsMatchIn(it) }?.let { DUNGEON_XP.find(it)!!.groupValues[1] }
        val emeralds = run.rewards.firstOrNull { it.contains("emerald", ignoreCase = true) }
        val items = run.rewards.count { !DUNGEON_XP.containsMatchIn(it) && !it.contains("emerald", ignoreCase = true) }
        val detail = listOfNotNull(
            xp?.let { "+$it XP" },
            emeralds?.let { "+$it" },
            items.takeIf { it > 0 }?.let { if (it == 1) "1 item" else "$it items" },
        ).joinToString(", ")
        WynnOverhaulToastQueue.show(WynnOverhaulToastQueue.make(WynnOverhaulToastQueue.Kind.QUEST, "Dungeon Completed", run.name, TOAST_COLOR, detail))
    }

    private var pendingHeader = false
    private var pendingKind = "Quest"
    private var suppressPendingContinuation = false
    private var lastObjectiveAt = 0L

    private fun onMessage(message: Component): Boolean {
        if (!WynnOverhaulGate.inGame) return true
        val lines = TextClean.clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return true

        val open = dungeon
        if (open != null) {
            if (open.expired(System.currentTimeMillis())) {
                flushDungeon(true)
            } else if (lines.all { DUNGEON_REWARD.matches(it) }) {
                for (line in lines) open.rewards.add(DUNGEON_REWARD.matchEntire(line)!!.groupValues[1].trim())
                open.lastAt = System.currentTimeMillis()
                return !open.suppress
            }
        }
        for ((index, line) in lines.withIndex()) {
            val done = DUNGEON_DONE.matchEntire(line) ?: continue
            flushDungeon(true)
            val run = DungeonRun(done.groupValues[1].trim(), WynnOverhaulConfig.current.questCompletionToastEnabled, System.currentTimeMillis())
            for (reward in lines.drop(index + 1)) DUNGEON_REWARD.matchEntire(reward)?.let { run.rewards.add(it.groupValues[1].trim()) }
            dungeon = run
            return !run.suppress
        }

        if (lines.any { it.equals(OBJECTIVE_FINISHED, ignoreCase = true) }) {
            ObjectiveClaims.setWeekly(true)
            val suppress = WynnOverhaulConfig.current.questCompletionToastEnabled
            if (suppress) {
                WynnOverhaulToastQueue.show(
                    WynnOverhaulToastQueue.make(WynnOverhaulToastQueue.Kind.QUEST, OBJECTIVE_FINISHED, "Redeem your prize", TOAST_COLOR, "/guild rewards"),
                )
            }
            return !suppress
        }
        for (line in lines) {
            val finished = MEMBER_FINISHED.matchEntire(line) ?: continue
            val name = finished.groupValues[1]
            val me = Minecraft.getInstance().player?.name?.string
            val own = name.equals(me, ignoreCase = true)
            if (own) ObjectiveClaims.setWeekly(true)
            val suppress = WynnOverhaulConfig.current.questCompletionToastEnabled
            if (suppress && !own) {
                WynnOverhaulToastQueue.show(
                    WynnOverhaulToastQueue.make(WynnOverhaulToastQueue.Kind.QUEST, "Guild Objective", "$name finished their weekly objective", TOAST_COLOR),
                )
            }
            return !suppress
        }

        val headerIndex = lines.indexOfFirst { HEADER.matches(it) }
        if (headerIndex < 0 && lines.all { CLAIM_LINE.matches(it) } &&
            System.currentTimeMillis() - lastObjectiveAt < CLAIM_WINDOW_MILLIS
        ) {
            return !WynnOverhaulConfig.current.questCompletionToastEnabled
        }
        if (headerIndex >= 0) {
            val suppress = WynnOverhaulConfig.current.questCompletionToastEnabled
            val kind = HEADER.matchEntire(lines[headerIndex])!!.groupValues[1]
            if (kind == "Objective") lastObjectiveAt = System.currentTimeMillis()
            if (headerIndex + 1 < lines.size) {
                pendingHeader = false
                onQuestCompleted(kind, lines[headerIndex + 1], rewardDetail(lines.drop(headerIndex + 2)), suppress)
            } else {
                pendingHeader = true
                pendingKind = kind
                suppressPendingContinuation = suppress
            }
            return !suppress
        }

        if (pendingHeader) {
            pendingHeader = false
            val suppress = suppressPendingContinuation
            if (CLAIM_LINE.matches(lines[0])) return !suppress
            onQuestCompleted(pendingKind, lines[0], rewardDetail(lines.drop(1)), suppress)
            return !suppress
        }
        return true
    }

    private fun rewardDetail(lines: List<String>): String {
        val parts = ArrayList<String>()
        for (line in lines) {
            val text = line.trimStart('-', ' ').trim()
            if (text.isEmpty() || text.startsWith("Rewards", ignoreCase = true) || CLAIM_LINE.matches(text)) continue
            val xp = XP_LINE.find(text)
            parts.add(if (xp != null) "+${xp.groupValues[1]} XP" else text.replace(PLUS_ONE, "").replace(Regex("""\s+"""), " ").trim())
        }
        return parts.joinToString(", ").take(MAX_DETAIL)
    }

    private fun onQuestCompleted(kind: String, name: String, rewards: String, toast: Boolean) {
        val objective = kind == "Objective"
        if (objective) ObjectiveClaims.setObjectivePending()
        val detail = if (objective) "Claim it with /daily" else rewards
        val updated = ContentBookCache.markCompleted(name)
        if (updated != null) {
            val screen = Minecraft.getInstance().gui.screen()
            if (screen is WynnOverhaulInventoryScreen) screen.updateBookActivities(updated)
        }
        if (toast) {
            WynnOverhaulToastQueue.show(WynnOverhaulToastQueue.make(WynnOverhaulToastQueue.Kind.QUEST, "$kind Completed", name, TOAST_COLOR, detail))
        }
    }

    private val HEADER = Regex("""^\[(Quest|Mini-Quest|Cave|Dungeon|Raid|World Event|Boss Altar|Objective) Completed]$""")
    private val XP_LINE = Regex("""^\+?(\d[\d,]*) Experience Points""", RegexOption.IGNORE_CASE)
    private val PLUS_ONE = Regex("""^\+1 """)
    private val CLAIM_LINE = Regex("""^Click here to claim your rewards!?$""", RegexOption.IGNORE_CASE)
    private const val CLAIM_WINDOW_MILLIS = 5000L
    private val DUNGEON_DONE = Regex("""^Great job! You['’]ve completed the (.+?) Dungeon!$""")
    private val DUNGEON_REWARD = Regex("""^\[\+(.+)]$""")
    private val DUNGEON_XP = Regex("""^(\d[\d,]*) XP$""", RegexOption.IGNORE_CASE)
    private const val DUNGEON_WINDOW_MILLIS = 900L
    private const val DUNGEON_MAX_MILLIS = 4000L
    private const val MAX_DETAIL = 60
    private const val OBJECTIVE_FINISHED = "Objective Finished"
    private val MEMBER_FINISHED = Regex("""^(\w{3,16}) has finished their weekly objective\.?$""")
    private const val TOAST_COLOR = 0xFF55FF55.toInt()
}

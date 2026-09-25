package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object WynnQuestCompletionTracker {
    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
    }

    private var pendingHeader = false
    private var pendingKind = "Quest"
    private var suppressPendingContinuation = false

    private fun onMessage(message: Component): Boolean {
        if (!WynnOverhaulGate.inGame) return true
        val lines = TextClean.clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return true

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
        if (headerIndex >= 0) {
            val suppress = WynnOverhaulConfig.current.questCompletionToastEnabled
            val kind = HEADER.matchEntire(lines[headerIndex])!!.groupValues[1]
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
            onQuestCompleted(pendingKind, lines[0], rewardDetail(lines.drop(1)), suppress)
            return !suppress
        }
        return true
    }

    private fun rewardDetail(lines: List<String>): String {
        val parts = ArrayList<String>()
        for (line in lines) {
            val text = line.trimStart('-', ' ').trim()
            if (text.isEmpty() || text.startsWith("Rewards", ignoreCase = true)) continue
            val xp = XP_LINE.find(text)
            parts.add(if (xp != null) "+${xp.groupValues[1]} XP" else text.replace(PLUS_ONE, "").replace(Regex("""\s+"""), " ").trim())
        }
        return parts.joinToString(", ").take(MAX_DETAIL)
    }

    private fun onQuestCompleted(kind: String, name: String, detail: String, toast: Boolean) {
        val updated = ContentBookCache.markCompleted(name)
        if (updated != null) {
            val screen = Minecraft.getInstance().gui.screen()
            if (screen is WynnOverhaulInventoryScreen) screen.updateBookActivities(updated)
        }
        if (toast) {
            WynnOverhaulToastQueue.show(WynnOverhaulToastQueue.make(WynnOverhaulToastQueue.Kind.QUEST, "$kind Completed", name, TOAST_COLOR, detail))
        }
    }

    private val HEADER = Regex("""^\[(Quest|Mini-Quest|Cave|Dungeon|Raid|World Event|Boss Altar) Completed]$""")
    private val XP_LINE = Regex("""^\+?(\d[\d,]*) Experience Points""", RegexOption.IGNORE_CASE)
    private val PLUS_ONE = Regex("""^\+1 """)
    private const val MAX_DETAIL = 60
    private const val OBJECTIVE_FINISHED = "Objective Finished"
    private val MEMBER_FINISHED = Regex("""^(\w{3,16}) has finished their weekly objective\.?$""")
    private const val TOAST_COLOR = 0xFF55FF55.toInt()
}

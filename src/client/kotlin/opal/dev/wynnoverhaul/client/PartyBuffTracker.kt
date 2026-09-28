package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object PartyBuffTracker {
    data class HealFeed(val giver: String, var total: Long, var hits: Int, var lastAmount: Int, var lastAtMillis: Long) {
        fun recencyFraction(now: Long): Float =
            (1f - (now - lastAtMillis).toFloat() / FEED_TTL_MILLIS).coerceIn(0f, 1f)
    }

    data class Notice(val text: String, val atMillis: Long)

    @Volatile
    var heals: List<HealFeed> = emptyList()
        private set

    @Volatile
    var notices: List<Notice> = emptyList()
        private set

    private val feeds = LinkedHashMap<String, HealFeed>()
    private val noticeList = ArrayDeque<Notice>()

    fun register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> if (overlay) true else onMessage(message) }
    }

    fun tick() {
        val now = System.currentTimeMillis()
        var changed = false
        if (feeds.values.removeIf { now - it.lastAtMillis > FEED_TTL_MILLIS }) changed = true
        while (noticeList.isNotEmpty() && now - noticeList.first().atMillis > NOTICE_TTL_MILLIS) {
            noticeList.removeFirst()
            changed = true
        }
        while (noticeList.size > MAX_NOTICES) {
            noticeList.removeFirst()
            changed = true
        }
        if (changed) publish()
    }

    fun clear() {
        feeds.clear()
        noticeList.clear()
        publish()
    }

    private fun publish() {
        heals = feeds.values.sortedByDescending { it.lastAtMillis }
        notices = noticeList.toList()
    }

    private fun onMessage(message: Component): Boolean {
        if (!WynnOverhaulGate.inGame) return true
        if (!WynnOverhaulConfig.current.partyBuffTrackerEnabled) return true
        val lines = TextClean.clean(message.string).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return true
        var matched = 0
        val now = System.currentTimeMillis()
        for (line in lines) {
            val heal = HEAL_GIVEN.matchEntire(line)
            if (heal != null) {
                val giver = heal.groupValues[1].trim()
                val amount = heal.groupValues[2].toIntOrNull() ?: continue
                if (giver.isEmpty() || amount <= 0) continue
                val feed = feeds[giver]
                if (feed == null) {
                    feeds[giver] = HealFeed(giver, amount.toLong(), 1, amount, now)
                } else {
                    feed.total += amount
                    feed.hits++
                    feed.lastAmount = amount
                    feed.lastAtMillis = now
                }
                matched++
                continue
            }
            val refreshed = REFRESHED.matchEntire(line)
            if (refreshed != null) {
                val name = refreshed.groupValues[1].trim()
                if (name.isEmpty()) continue
                noticeList.addLast(Notice("$name refreshed", now))
                matched++
            }
        }
        if (matched == 0) return true
        publish()
        return matched != lines.size
    }

    private val HEAL_GIVEN = Regex("""^(.+) gave you \[\+(\d+)[^\]]*\]$""")
    private val REFRESHED = Regex("""^(.+) has been refreshed!$""")

    private const val FEED_TTL_MILLIS = 30_000L
    private const val NOTICE_TTL_MILLIS = 15_000L
    private const val MAX_NOTICES = 3
}

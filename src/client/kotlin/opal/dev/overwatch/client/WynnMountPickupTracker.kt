package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnMountPickupTracker {

    data class Pickup(val title: String, val words: List<String>, val atMillis: Long)

    @Volatile
    var last: Pickup? = null
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        last = null
    }

    fun pickupVisible(now: Long): Boolean {
        val pickup = last ?: return false
        return now - pickup.atMillis < PICKUP_TTL_MS
    }

    fun hasSegment(raw: String): Boolean = parse(raw) != null

    private fun onActionBar(message: Component) {
        val raw = message.string
        val pickup = parse(raw)
        if (pickup != null) {
            last = pickup
            OverwatchGate.noteActionBar()
            return
        }
    }

    fun parse(raw: String): Pickup? {
        if (!WynnMountEnergyTracker.hasSegment(raw)) return null
        val body = tailBody(raw) ?: return null
        val words = decodeWords(body)
        val upper = words.any { it.any { c -> c in 'A'..'Z' } }
        val lower = words.any { it.any { c -> c in 'a'..'z' } }
        if (!upper || !lower) return null
        val title = when {
            words.any { it.equals("LEVEL", ignoreCase = true) } -> "Mount Training"
            words.any { it.equals("ENERGY", ignoreCase = true) } -> "Mount Boost"
            else -> "Mount Pickup"
        }
        val distinct = words.asReversed().distinctBy { it.uppercase() }.reversed()
        return Pickup(title, distinct, System.currentTimeMillis())
    }

    private fun isWrapMark(raw: String, pos: Int): Boolean {
        if (pos + 1 >= raw.length) return false
        return raw[pos] == WRAP_HIGH && raw[pos + 1] in WRAP_LOW_FIRST..WRAP_LOW_LAST
    }

    private fun upperRun(raw: String): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var i = 0
        val n = raw.length
        while (i < n) {
            val start = i
            var upper = 0
            var j = i
            while (j < n) {
                val ch = raw[j]
                when {
                    ch in UPPER_FIRST..UPPER_LAST -> {
                        upper++
                        j++
                    }
                    ch == WRAP_HIGH -> {
                        j++
                        if (j < n && raw[j] == SEP_SECOND) j++
                    }
                    else -> break
                }
            }
            if (j > start && upper >= MIN_UPPER_IN_RUN) best = start to j
            i = if (j > start) j else i + 1
        }
        return best
    }

    private fun tailBody(raw: String): String? {
        val (runStart, runEnd) = upperRun(raw) ?: return null
        var start = -1
        var end = -1
        var k = 0
        while (k + 1 < raw.length) {
            if (isWrapMark(raw, k)) {
                if (k < runStart) start = k
                end = k
                k += 2
            } else {
                k++
            }
        }
        if (start < 0) return null
        val close = maxOf(end, runEnd).coerceAtMost(raw.length)
        if (close <= start + WRAP_LEN) return null
        return raw.substring(start + WRAP_LEN, close)
    }

    private fun decodeWords(body: String): List<String> {
        val words = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        fun flush() {
            if (current.length >= MIN_WORD_LENGTH) words.add(current.toString())
            current.clear()
        }
        while (i < body.length) {
            if (body[i] == DAFF_CHAR) {
                var j = i + 1
                if (j < body.length && body[j] == DFFF_CHAR) j++
                val next = if (j < body.length) body.codePointAt(j) else -1
                if (current.isNotEmpty() && current.last() in 'A'..'Z' && next in UPPER_START..UPPER_END) {
                    i = j
                    continue
                }
                flush()
                i++
                continue
            }
            val cp = body.codePointAt(i)
            when {
                cp in UPPER_START..UPPER_END -> current.append('A' + (cp - UPPER_START))
                cp in LOWER_START..LOWER_END -> current.append('a' + (cp - LOWER_START))
                else -> flush()
            }
            i += Character.charCount(cp)
        }
        flush()
        return words
    }

    private const val WRAP_HIGH = '\uDAFF'
    private const val WRAP_LOW_FIRST = '\uDF00'
    private const val WRAP_LOW_LAST = '\uDFFF'
    private const val SEP_SECOND = '\uDFFF'
    private const val DAFF_CHAR = '\uDAFF'
    private const val DFFF_CHAR = '\uDFFF'
    private const val UPPER_FIRST = '\uE030'
    private const val UPPER_LAST = '\uE049'
    private const val WRAP_LEN = 2
    private const val UPPER_START = 0xE030
    private const val UPPER_END = 0xE049
    private const val LOWER_START = 0xE000
    private const val LOWER_END = 0xE019
    private const val MIN_UPPER_IN_RUN = 3

    private const val MIN_WORD_LENGTH = 2

    private const val PICKUP_TTL_MS = 3500L
}

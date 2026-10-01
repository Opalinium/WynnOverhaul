package opal.dev.wynnoverhaul.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.entity.Display
import net.minecraft.world.phys.AABB
import java.util.Optional

object WynnDamageTracker {
    enum class Element(val rgb: Int, val label: String) {
        NEUTRAL(0xAA0000, "Neutral"),
        EARTH(0x00AA00, "Earth"),
        THUNDER(0xFFFF55, "Thunder"),
        WATER(0x55FFFF, "Water"),
        FIRE(0xFF5555, "Fire"),
        AIR(0xFFFFFF, "Air"),
        POISON(0xAA00AA, "Poison"),
    }

    data class Stats(
        val dps: Double,
        val peak: Double,
        val total: Long,
        val seconds: Int,
        val elements: Map<Element, Long>,
    )

    private class Hit(val atMillis: Long, val total: Long)

    @Volatile
    var stats: Stats? = null
        private set

    private val hits = ArrayDeque<Hit>()
    private val lastLabel = HashMap<Int, Component>()
    private val lastValues = HashMap<Int, Map<Element, Long>>()
    private val fightElements = newElementMap()
    private var fightStart = 0L
    private var lastHitAt = 0L
    private var fightTotal = 0L
    private var peak = 0.0

    fun tick(client: Minecraft) {
        if (!WynnOverhaulConfig.current.dpsHudEnabled) {
            if (stats != null) clear()
            return
        }
        val player = client.player ?: return
        val level = client.level ?: return
        val now = System.currentTimeMillis()
        val box = AABB(
            player.x - SCAN_RADIUS, player.y - SCAN_RADIUS, player.z - SCAN_RADIUS,
            player.x + SCAN_RADIUS, player.y + SCAN_RADIUS, player.z + SCAN_RADIUS,
        )
        val seen = HashSet<Int>()
        for (entity in level.getEntities(player, box) { it is Display.TextDisplay }) {
            val text = (entity as Display.TextDisplay).text
            seen.add(entity.id)
            if (lastLabel[entity.id] == text) continue
            lastLabel[entity.id] = text
            val values = parse(text)
            if (values == null) {
                lastValues.remove(entity.id)
                continue
            }
            val previous = lastValues.put(entity.id, values)
            val delta = HashMap<Element, Long>()
            for ((element, value) in values) {
                val before = previous?.get(element)
                val gained = if (before != null && value >= before) value - before else value
                if (gained > 0) delta[element] = gained
            }
            if (delta.isNotEmpty()) record(now, delta)
        }
        lastLabel.keys.retainAll(seen)
        lastValues.keys.retainAll(seen)
        publish(now)
    }

    fun clear() {
        hits.clear()
        lastLabel.clear()
        lastValues.clear()
        fightElements.clear()
        fightStart = 0L
        lastHitAt = 0L
        fightTotal = 0L
        peak = 0.0
        stats = null
    }

    private fun record(now: Long, delta: Map<Element, Long>) {
        if (lastHitAt == 0L || now - lastHitAt > FIGHT_GAP_MILLIS) {
            fightStart = now
            fightTotal = 0L
            fightElements.clear()
            peak = 0.0
        }
        lastHitAt = now
        val sum = delta.values.sum()
        fightTotal += sum
        for ((element, value) in delta) fightElements.merge(element, value, Long::plus)
        hits.addLast(Hit(now, sum))
    }

    private fun publish(now: Long) {
        while (hits.isNotEmpty() && now - hits.first().atMillis > RETENTION_MILLIS) hits.removeFirst()
        if (lastHitAt == 0L || now - lastHitAt > LINGER_MILLIS) {
            if (stats != null) stats = null
            return
        }
        val windowMillis = WynnOverhaulConfig.current.dpsWindowSeconds.coerceIn(1, 30) * 1000L
        val elapsed = (now - fightStart).coerceAtLeast(1000L)
        val span = minOf(windowMillis, elapsed)
        val inWindow = hits.filter { now - it.atMillis <= windowMillis }.sumOf { it.total }
        val live = if (now - lastHitAt > FIGHT_GAP_MILLIS) 0.0 else inWindow * 1000.0 / span
        peak = maxOf(peak, live)
        val seconds = ((lastHitAt - fightStart) / 1000L).toInt().coerceAtLeast(1)
        stats = Stats(live, peak, fightTotal, seconds, fightElements.toMap())
    }

    private fun parse(label: Component): Map<Element, Long>? {
        val out = HashMap<Element, Long>()
        var valid = true
        label.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                for (piece in text.split(SEPARATOR)) {
                    val clean = TextClean.clean(piece).trim()
                    if (clean.isEmpty()) continue
                    val match = NUMBER.matchEntire(clean)
                    val element = elementOf(style)
                    if (match == null || element == null) {
                        valid = false
                        return@StyledContentConsumer Optional.of(Unit)
                    }
                    val amount = amountOf(match.groupValues[1], match.groupValues[2])
                    out.merge(element, amount, Long::plus)
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        return if (valid && out.isNotEmpty()) out else null
    }

    private fun elementOf(style: Style): Element? {
        val rgb = style.color?.value ?: return null
        return Element.entries.firstOrNull { it.rgb == (rgb and 0xFFFFFF) }
    }

    private fun amountOf(number: String, suffix: String): Long {
        val base = number.toDoubleOrNull() ?: return 0L
        val scale = when (suffix.lowercase()) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (base * scale).toLong()
    }

    private fun newElementMap() = java.util.EnumMap<Element, Long>(Element::class.java)

    private val NUMBER = Regex("""(\d+(?:\.\d+)?)([kKmMbB]?)""")
    private const val SEPARATOR = "󐀊"
    private const val SCAN_RADIUS = 64.0
    private const val FIGHT_GAP_MILLIS = 6_000L
    private const val LINGER_MILLIS = 10_000L
    private const val RETENTION_MILLIS = 60_000L
}

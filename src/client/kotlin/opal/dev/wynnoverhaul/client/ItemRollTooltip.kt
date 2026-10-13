package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

object ItemRollTooltip {
    private const val METER_FIRST = 0xE000
    private const val METER_LAST = 0xE023
    private const val GOOD_VALUE = 0xACFAC6
    private const val BAD_VALUE = 0xFAACAC
    private val VALUE = Regex("""^[-+][\d,]+""")
    private val STOPS = listOf(
        0f to 0xFF5555,
        40f to 0xFFAA00,
        70f to 0xFFFF55,
        90f to 0x55FF55,
        100f to 0x55FFFF,
    )

    fun register() {
        ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
            val config = WynnOverhaulConfig.current
            if (!WynnOverhaulGate.inGame || stack.isEmpty || lines.size < 2) return@register
            if (!config.itemRollPercentEnabled && !config.itemRollOverallEnabled) return@register
            var total = 0f
            var count = 0
            for (i in 1 until lines.size) {
                val roll = rollOf(lines[i]) ?: continue
                total += roll
                count++
                if (config.itemRollPercentEnabled) lines[i] = lines[i].copy().append(badge(roll))
            }
            if (count > 0 && config.itemRollOverallEnabled) lines[0] = lines[0].copy().append(badge(total / count))
        }
    }

    private val rollCache = java.util.IdentityHashMap<Component, Float>()

    private fun rollOf(line: Component): Float? {
        val cached = rollCache[line]
        if (cached != null) return if (cached.isNaN()) null else cached
        val roll = computeRoll(line)
        if (rollCache.size >= ROLL_CACHE_LIMIT) rollCache.clear()
        rollCache[line] = roll ?: Float.NaN
        return roll
    }

    private const val ROLL_CACHE_LIMIT = 1024

    private fun computeRoll(line: Component): Float? {
        var seenValue = false
        var meter = -1
        line.visit(
            FormattedText.StyledContentConsumer<Unit> { style, text ->
                if (!seenValue) {
                    val rgb = style.color?.value
                    if ((rgb == GOOD_VALUE || rgb == BAD_VALUE) && VALUE.containsMatchIn(text.trim())) seenValue = true
                } else {
                    for (c in text) if (c.code in METER_FIRST..METER_LAST) meter = c.code
                }
                Optional.empty()
            },
            Style.EMPTY,
        )
        if (!seenValue || meter < 0) return null
        return (meter - METER_FIRST) * 100f / (METER_LAST - METER_FIRST)
    }

    private fun badge(percent: Float): Component =
        Component.literal(" [${Math.round(percent)}%]").withColor(colorFor(percent))

    private fun colorFor(percent: Float): Int {
        val p = percent.coerceIn(0f, 100f)
        for (i in 1 until STOPS.size) {
            val (hi, hiColor) = STOPS[i]
            if (p <= hi) {
                val (lo, loColor) = STOPS[i - 1]
                val t = (p - lo) / (hi - lo)
                return mix(loColor, hiColor, t)
            }
        }
        return STOPS.last().second
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val x = (a shr shift) and 0xFF
            val y = (b shr shift) and 0xFF
            return (x + (y - x) * t).toInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

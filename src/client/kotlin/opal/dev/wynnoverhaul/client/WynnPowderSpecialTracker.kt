package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component

object WynnPowderSpecialTracker {

    enum class Element(val displayName: String, val fillArgb: Int) {
        AIR("Air", 0xFFE8E8E8.toInt()),
        EARTH("Earth", 0xFF6FA84B.toInt()),
        FIRE("Fire", 0xFFD64545.toInt()),
        THUNDER("Thunder", 0xFFE0B83B.toInt()),
        WATER("Water", 0xFF45A3D6.toInt()),
    }

    data class PowderState(val element: Element?, val charge: Float, val glyph: Char)

    @Volatile
    var element: Element? = null
        private set

    @Volatile
    var charge: Float = 0f
        private set

    @Volatile
    var glyph: Char? = null
        private set

    @Volatile
    private var lastSeenMillis: Long = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        element = null
        charge = 0f
        glyph = null
        lastSeenMillis = 0L
    }

    fun visible(now: Long): Boolean =
        element != null && charge > 0f && now - lastSeenMillis < VISIBLE_TTL_MILLIS

    private fun onActionBar(message: Component) {
        parse(message.string)?.let { state ->
            element = state.element
            charge = state.charge
            glyph = state.glyph
            lastSeenMillis = System.currentTimeMillis()
        }
    }

    private fun parse(raw: String): PowderState? {
        var i = 0
        while (i < raw.length) {
            val startLen = matchStart(raw, i)
            if (startLen == 0) {
                i++
                continue
            }
            val g = i + startLen
            if (g >= raw.length) break
            val glyph = raw[g]
            if (!matchEnd(raw, g + 1)) {
                i++
                continue
            }
            return classify(glyph)
        }
        return null
    }

    private fun matchStart(raw: String, i: Int): Int {
        if (i + 4 < raw.length &&
            raw[i] == c("DAFF") && raw[i + 1] == c("DFEF") && raw[i + 2] == c("DAFF") &&
            raw[i + 3] == c("DFFF") && raw[i + 4] == 0x01.toChar()
        ) {
            return 5
        }
        if (i + 1 < raw.length && raw[i] == c("DAFF") && raw[i + 1] == c("DFF0")) return 2
        return 0
    }

    private fun matchEnd(raw: String, i: Int): Boolean {
        if (i + 1 >= raw.length) return false
        return raw[i] == c("DAFF") && (raw[i + 1] == c("DFEE") || raw[i + 1] == c("DFEF"))
    }

    private fun classify(glyph: Char): PowderState? {
        if (glyph == c("E010")) return PowderState(null, 0f, glyph)
        for ((element, range) in ELEMENT_RANGES) {
            if (glyph in range) return PowderState(element, ((glyph - range.start) + 1) * 0.1f, glyph)
        }
        return null
    }

    private fun c(hex: String): Char = hex.toInt(16).toChar()

    private val ELEMENT_RANGES = listOf(
        Element.AIR to (c("E020")..c("E029")),
        Element.EARTH to (c("E030")..c("E039")),
        Element.FIRE to (c("E040")..c("E049")),
        Element.THUNDER to (c("E050")..c("E059")),
        Element.WATER to (c("E060")..c("E069")),
    )

    private const val VISIBLE_TTL_MILLIS = 3000L
}

package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional

object WynnSpellTracker {
    data class Cast(val name: String, val costs: List<WynnSpellSegments.SpellCost>, val atMillis: Long)

    @Volatile
    var lastCast: Cast? = null
        private set

    @Volatile
    var combo: List<WynnSpellSegments.ComboInput>? = null
        private set

    @Volatile
    var comboComponents: List<Component>? = null
        private set

    @Volatile
    var comboArrow: Component? = null
        private set

    @Volatile
    var comboAtMillis: Long = 0L
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, _ -> onActionBar(message) }
    }

    fun clear() {
        lastCast = null
        combo = null
        comboComponents = null
        comboArrow = null
        comboAtMillis = 0L
    }

    fun castVisible(now: Long): Boolean {
        val cast = lastCast ?: return false
        return now - cast.atMillis < CAST_TTL_MS
    }

    fun comboVisible(now: Long): Boolean {
        if (combo == null) return false
        return now - comboAtMillis < COMBO_TTL_MS
    }

    private fun onActionBar(message: Component) {
        val raw = message.string
        val now = System.currentTimeMillis()
        val cast = WynnSpellSegments.parseCast(raw)
        if (cast != null) {
            val fresh = lastCast?.let { it.name != cast.name || now - it.atMillis > CAST_TTL_MS } ?: true
            lastCast = Cast(cast.name, cast.costs, now)
            if (fresh) WeaponAnimations.onSpellCast(cast.name)
            combo = null
            comboComponents = null
            WynnOverhaulGate.noteActionBar()
            return
        }
        val glyphs = WynnSpellSegments.parseInputs(raw)
        if (glyphs != null) {
            combo = glyphs.map { WynnSpellSegments.classifyInput(it) }
            val styles = styleByChar(message)
            comboComponents = glyphs.map { styled(it, styles) }
            comboArrow = styled(WynnSpellSegments.CLICK_ARROW, styles)
            comboAtMillis = now
            WynnOverhaulGate.noteActionBar()
        }
    }

    private fun styleByChar(message: Component): Map<Char, Style> {
        val map = HashMap<Char, Style>()
        message.visit(
            FormattedText.StyledContentConsumer<Unit> { style, string ->
                for (ch in string) map.putIfAbsent(ch, style)
                Optional.empty()
            },
            Style.EMPTY,
        )
        return map
    }

    private fun styled(text: String, styles: Map<Char, Style>): Component {
        val style = styles[text[0]] ?: Style.EMPTY
        return Component.literal(text).setStyle(style)
    }

    private const val CAST_TTL_MS = 2500L
    private const val COMBO_TTL_MS = 2000L
}

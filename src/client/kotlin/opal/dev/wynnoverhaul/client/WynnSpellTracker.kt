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

    @Volatile
    private var castDisplayed = false

    @Volatile
    private var displayedName: String? = null

    @Volatile
    private var displayedCosts: List<WynnSpellSegments.SpellCost>? = null

    private class CostRecord(var costs: List<WynnSpellSegments.SpellCost>, var lastMillis: Long)

    private val baseCostsByName = HashMap<String, CostRecord>()

    fun spellCosts(name: String): List<WynnSpellSegments.SpellCost> = baseCostsByName[name]?.costs ?: emptyList()

    private fun noteCastCost(name: String, observed: List<WynnSpellSegments.SpellCost>, now: Long) {
        val record = baseCostsByName[name]
        val observedTotal = observed.sumOf { it.amount }
        if (record == null || now - record.lastMillis > SPAM_RESET_MS || observedTotal <= record.costs.sumOf { it.amount }) {
            baseCostsByName[name] = CostRecord(observed, now)
        } else {
            record.lastMillis = now
        }
    }

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> onActionBar(message, overlay) }
    }

    fun clear() {
        lastCast = null
        combo = null
        comboComponents = null
        comboArrow = null
        comboAtMillis = 0L
        castDisplayed = false
        displayedName = null
        displayedCosts = null
        baseCostsByName.clear()
    }

    fun castVisible(now: Long): Boolean {
        val cast = lastCast ?: return false
        return now - cast.atMillis < CAST_TTL_MS
    }

    fun comboVisible(now: Long): Boolean {
        if (combo == null) return false
        return now - comboAtMillis < COMBO_TTL_MS
    }

    private fun onActionBar(message: Component, overlay: Boolean) {
        if (!overlay) return
        val raw = message.string
        val now = System.currentTimeMillis()
        val cast = WynnSpellSegments.parseCast(raw)
        if (cast != null) {
            WynnClassTracker.noteCastSpell(cast.name)
            noteCastCost(cast.name, cast.costs, now)
            val repeat = castDisplayed && displayedName == cast.name && displayedCosts == cast.costs
            if (!repeat) {
                lastCast = Cast(cast.name, cast.costs, now)
                WeaponAnimations.onSpellCast(cast.name)
            }
            castDisplayed = true
            displayedName = cast.name
            displayedCosts = cast.costs
            combo = null
            comboComponents = null
            WynnOverhaulGate.noteActionBar()
            return
        }
        castDisplayed = false
        displayedName = null
        displayedCosts = null
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
    private const val SPAM_RESET_MS = 8000L
}

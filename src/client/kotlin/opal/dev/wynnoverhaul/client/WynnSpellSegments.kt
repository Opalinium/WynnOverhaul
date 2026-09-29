package opal.dev.wynnoverhaul.client

object WynnSpellSegments {

    fun parseInputs(raw: String): List<String>? {
        val match = INPUTS_PATTERN.find(raw) ?: return null
        return listOf("first", "second", "third").map { match.groups[it]?.value ?: return null }
    }

    enum class ComboInput {
        LEFT,
        RIGHT,
        EMPTY,
    }

    fun classifyInput(glyph: String): ComboInput {
        if (LEFT_CLICK_PATTERN.matches(glyph)) return ComboInput.LEFT
        if (RIGHT_CLICK_PATTERN.matches(glyph)) return ComboInput.RIGHT
        return ComboInput.EMPTY
    }

    fun clickGlyph(left: Boolean): String = if (left) LEFT_B else RIGHT_B

    val SLOT_COMBOS: List<List<Boolean>> = listOf(
        listOf(true, false, true),
        listOf(true, true, true),
        listOf(true, false, false),
        listOf(true, true, false),
    )

    val SLOT_COMBOS_LEFT: List<List<Boolean>> = listOf(
        listOf(false, true, false),
        listOf(false, false, false),
        listOf(false, true, true),
        listOf(false, false, true),
    )

    data class SpellCost(val amount: Int, val mana: Boolean)
    data class SpellCast(val name: String, val costs: List<SpellCost>)

    fun parseCast(raw: String): SpellCast? {
        val match = CAST_PATTERN.find(raw) ?: return null
        val segmentText = match.value
        if (segmentText[0] != SPACER || segmentText[segmentText.length - 2] != SPACER) return null
        val name = match.groups["spellName"]?.value?.trim().orEmpty()
        if (name.isEmpty()) return null
        val costs = ArrayList<SpellCost>(2)
        for (slot in listOf("One", "Two")) {
            val amountText = match.groups["cost$slot"]?.value ?: continue
            val icon = match.groups["cost${slot}Icon"]?.value ?: continue
            val amount = amountText.toIntOrNull() ?: return null
            costs.add(SpellCost(amount, icon == MANA_ICON))
        }
        return SpellCast(name, costs)
    }

    private fun c(hex: String): Char = hex.toInt(16).toChar()
    private fun g(hex: String): String = c(hex).toString()

    private val SPACER: Char = c("DAFF")
    private val MANA_ICON: String = g("E531")
    private val HEALTH_ICON: String = g("E530")
    val CLICK_ARROW: String = g("E106")
    private val LEFT_A: String = g("E100")
    private val LEFT_B: String = g("E103")
    private val RIGHT_A: String = g("E101")
    private val RIGHT_B: String = g("E104")
    private val NONE_A: String = g("E102")
    private val NONE_B: String = g("E105")
    private val SEG_HEAD: String = g("DAFF") + g("DFE0")

    private val CAST_PATTERN = Regex(
        ".(?<spellName>[A-Za-z ]+?) Cast!(?: -(?<costOne>[0-9]+) ?(?<costOneIcon>" + MANA_ICON + "|" + HEALTH_ICON +
            "))?(?: -(?<costTwo>[0-9]+) ?(?<costTwoIcon>" + MANA_ICON + "|" + HEALTH_ICON + "))?.",
    )

    private val LEFT_CLICK = "[$LEFT_A$LEFT_B]"
    private val RIGHT_CLICK = "[$RIGHT_A$RIGHT_B]"
    private val NO_CLICK = "[$NONE_A$NONE_B]"
    private val CLICK_SEPARATOR = "\\s$CLICK_ARROW\\s"
    private val INPUTS_PATTERN = Regex(
        SEG_HEAD +
            "(?<first>" + LEFT_CLICK + "|" + RIGHT_CLICK + "|" + NO_CLICK + ")" + CLICK_SEPARATOR +
            "(?<second>" + LEFT_CLICK + "|" + RIGHT_CLICK + "|" + NO_CLICK + ")" + CLICK_SEPARATOR +
            "(?<third>" + LEFT_CLICK + "|" + RIGHT_CLICK + "|" + NO_CLICK + ")" + SEG_HEAD,
    )
    private val LEFT_CLICK_PATTERN = Regex(LEFT_CLICK)
    private val RIGHT_CLICK_PATTERN = Regex(RIGHT_CLICK)
}

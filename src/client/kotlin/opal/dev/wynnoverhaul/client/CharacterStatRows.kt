package opal.dev.wynnoverhaul.client

object CharacterStatRows {
    sealed interface Row {
        val slot: Int
    }

    data class Header(val text: String, val tag: String, override val slot: Int) : Row
    data class Stat(
        val label: String,
        val value: String,
        val labelColor: Int,
        val valueColor: Int,
        val half: Boolean,
        override val slot: Int,
    ) : Row
    data class Note(val text: String, override val slot: Int) : Row

    private val TAGGED = Regex("""^\[([^\]]+)]\s*(.+)$""")
    private val PAGER = Regex("""^[«»■□▪\s]+$""")
    private val LEVEL = Regex("""Lv\.\s*\d+""")
    private val BULLETS = charArrayOf('-', '–', '•', ' ')

    private val ELEMENT_COLORS = mapOf(
        "Earth" to 0xFF5FB45F.toInt(),
        "Thunder" to 0xFFE8D24A.toInt(),
        "Water" to 0xFF58C7E8.toInt(),
        "Fire" to 0xFFE2573F.toInt(),
        "Air" to 0xFFDDE3E8.toInt(),
        "Neutral" to 0xFFE0A63A.toInt(),
    )
    private const val HEALTH_COLOR = 0xFFE0605A.toInt()

    fun combat(lines: List<Pair<String, Int>>): List<Row> {
        val rows = ArrayList<Row>()
        for ((raw, slot) in lines) {
            val text = raw.trim()
            if (text.isEmpty() || PAGER.matches(text) || text == "Convergence") continue
            val tagged = TAGGED.matchEntire(text)
            when {
                tagged != null -> rows.add(Header(tagged.groupValues[2].trim(), tagged.groupValues[1].trim(), slot))
                text.endsWith(":") -> rows.add(Header(text.removeSuffix(":").trim(), "", slot))
                text.startsWith("(") -> rows.add(Note(text, slot))
                else -> rows.add(combatStat(text, slot) ?: Note(text, slot))
            }
        }
        return rows
    }

    fun identifications(lines: List<Pair<String, Int>>, colorOf: (String) -> Int?): List<Row> {
        val rows = ArrayList<Row>()
        for ((raw, slot) in lines) {
            val text = raw.trim()
            if (text.isEmpty() || text == "Identifications:" || PAGER.matches(text)) continue
            val body = text.trimStart(*BULLETS).trim()
            val idx = body.indexOf(':')
            if (idx <= 0 || idx == body.length - 1) {
                rows.add(Note(body, slot))
                continue
            }
            val value = body.substring(idx + 1).trim()
            val color = colorOf(text) ?: signColor(value)
            rows.add(Stat(shorten(body.substring(0, idx).trim()), value, OwTheme.TEXT_DIM, color, true, slot))
        }
        return rows
    }

    fun professions(lines: List<Pair<String, Int>>): List<Row> {
        val rows = ArrayList<Row>()
        for ((raw, slot) in lines) {
            val body = raw.trim().trimStart(*BULLETS).trim()
            if (body.isEmpty()) continue
            val colon = body.indexOf(':')
            val level = LEVEL.find(body)
            val stat = when {
                colon > 0 && colon < body.length - 1 ->
                    Stat(body.substring(0, colon).trim(), body.substring(colon + 1).trim(), OwTheme.TEXT, OwTheme.TEXT_DIM, false, slot)
                level != null && level.range.first > 0 ->
                    Stat(body.substring(0, level.range.first).trim(), body.substring(level.range.first).trim(), OwTheme.TEXT, OwTheme.TEXT_DIM, false, slot)
                else -> null
            }
            rows.add(stat ?: Note(body, slot))
        }
        return rows
    }

    private fun combatStat(text: String, slot: Int): Stat? {
        val body = text.trimStart(*BULLETS).trim()
        val idx = body.indexOf(':')
        if (idx <= 0 || idx == body.length - 1) return null
        val label = body.substring(0, idx).trim()
        val value = body.substring(idx + 1).trim()
        val element = ELEMENT_COLORS.entries.firstOrNull { label.startsWith(it.key) }
        return when {
            element != null -> Stat(
                label.removeSuffix(" Defence").removeSuffix(" Damage"),
                value, element.value, OwTheme.TEXT, true, slot,
            )
            label.startsWith("❤") -> Stat(label, value, HEALTH_COLOR, OwTheme.TEXT, false, slot)
            label.contains("DPS") -> Stat(label, value, OwTheme.TEXT_DIM, OwTheme.ACCENT, false, slot)
            else -> Stat(label, value, OwTheme.TEXT_DIM, OwTheme.TEXT, false, slot)
        }
    }

    private fun shorten(label: String): String = label
        .replace("Experience", "XP")
        .replace(" Damage", " Dmg")
        .replace(" Defence", " Def")
        .replace("Attack", "Atk")

    private fun signColor(value: String): Int = when {
        value.startsWith("+") -> OwTheme.GOOD
        value.startsWith("-") -> OwTheme.BAD
        else -> OwTheme.TEXT
    }
}

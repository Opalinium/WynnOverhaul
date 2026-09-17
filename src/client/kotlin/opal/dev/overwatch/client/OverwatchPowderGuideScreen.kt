package opal.dev.overwatch.client

import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class OverwatchPowderGuideScreen(parent: Screen) : OwScreen(Component.literal("Overwatch - Powder Guide"), parent) {

    private val collapsedElements = HashSet<PowderElement>()

    override fun init() {
        super.init()
        rebuildLines()
    }

    private fun rebuildLines() {
        val left = contentLeft
        val w = contentWidth
        val rows = mutableListOf<Pair<AbstractWidget, Int>>()

        fun textLine(text: String, color: Int) {
            rows += OwLabel(left, 0, w, LINE_HEIGHT, text, color) to LINE_HEIGHT
        }

        fun clickableLine(text: String, color: Int? = null, onClick: () -> Unit) {
            rows += OwButton(left, 0, w, LINE_HEIGHT, Component.literal(text), textColor = color) { onClick() } to LINE_HEIGHT
        }

        for (element in PowderElement.entries) {
            val collapsed = element in collapsedElements
            val arrow = if (collapsed) "▶" else "▼"
            clickableLine("$arrow ${element.displayName} Powder", element.colorArgb) { toggleElement(element) }
            if (collapsed) continue

            textLine("Tier   Weapon Dmg   Neutral→Elem   Armor: Health / +Def / -Opp.Def   Min Lv", GRAY)
            for (t in PowderGuideData.TIERS.getValue(element)) {
                val roman = ROMAN[t.tier - 1]
                textLine(
                    " $roman     +${t.minDamage}-${t.maxDamage}          +${t.neutralToElementPercent}%             +${t.health} / +${t.addedDefense} / -${t.removedDefense}          Lv.${t.minItemLevel}",
                    WHITE,
                )
            }
            textLine("", GRAY)
            textLine("Weapon special: ${element.weaponSpecial}", HEADER)
            for (wrapped in wrap(PowderGuideData.WEAPON_SPECIAL_DESC.getValue(element), w)) {
                textLine(wrapped, LIGHT_GRAY)
            }
            textLine("Armor special: ${element.armorSpecial}", HEADER)
            for (wrapped in wrap(PowderGuideData.ARMOR_SPECIAL_DESC.getValue(element), w)) {
                textLine(wrapped, LIGHT_GRAY)
            }
            textLine("", GRAY)
        }
        textLine("A powder special unlocks with 2+ Tier IV+ same-element powders on one item.", DARK_GRAY)
        textLine("The first powder applied determines which special a weapon gets.", DARK_GRAY)

        installScrollList(rows, left, contentTop, w, contentBottom - contentTop)
    }

    private fun toggleElement(element: PowderElement) {
        if (!collapsedElements.add(element)) collapsedElements.remove(element)
        rebuildWidgets()
    }

    private fun wrap(text: String, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (font.width(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                current.clear()
                current.append(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    private companion object {
        const val LINE_HEIGHT = 12
        val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII")
        val WHITE = 0xFFFFFFFF.toInt()
        val GRAY = 0xFFAAAAAA.toInt()
        val LIGHT_GRAY = 0xFFCCCCCC.toInt()
        val DARK_GRAY = 0xFF777777.toInt()
        val HEADER = 0xFFE0C060.toInt()
    }
}

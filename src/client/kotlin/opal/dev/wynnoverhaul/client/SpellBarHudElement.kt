package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.ChatFormatting
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import opal.dev.wynnoverhaul.WynnOverhaul

class SpellBarHudElement : HudElement {
    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                WynnOverhaul.LOGGER.error("Spell bar HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!WynnOverhaulGate.inGame || !WynnOverhaulConfig.current.customHudEnabled) return
        val now = System.currentTimeMillis()
        val liveCombo = WynnSpellTracker.combo?.takeIf { WynnSpellTracker.comboVisible(now) }
        val preview = isHudDesignerOpen()

        val prefix = liveCombo?.takeWhile { it != WynnSpellSegments.ComboInput.EMPTY }
            ?.map { it == WynnSpellSegments.ComboInput.RIGHT }
            ?: if (preview) DEMO_PREFIX else emptyList()

        val font = Minecraft.getInstance().font
        val base = WynnClassTracker.baseNames()
        val showPlaceholder = preview && base == null
        val names = (0 until SLOT_COUNT).map { slot ->
            base?.getOrNull(slot) ?: DEMO_NAMES.getOrNull(slot).takeIf { showPlaceholder }
        }
        val keys = (0 until SLOT_COUNT).map { QuickCast.keyLabel(it) }
        val nameParts = ArrayList<Component>(SLOT_COUNT)
        val comboParts = ArrayList<Component>(SLOT_COUNT)
        val costParts = ArrayList<Component?>(SLOT_COUNT)
        val costs = (0 until SLOT_COUNT).map { slot ->
            names[slot]?.let { WynnSpellTracker.spellCosts(it) }?.ifEmpty { null }
                ?: if (showPlaceholder) DEMO_COSTS.getOrNull(slot) ?: emptyList() else emptyList()
        }
        val lastName = WynnSpellTracker.lastCast?.takeIf { WynnSpellTracker.castVisible(now) }?.name
        var contentW = 0
        for (slot in 0 until SLOT_COUNT) {
            val name = names[slot] ?: UNKNOWN_NAME
            nameParts.add(Component.literal("${slot + 1} $name"))
            val key = keys[slot]
            val combo = if (key != null) {
                Component.literal(key)
            } else {
                val row = StringBuilder()
                QuickCast.slotCombo(slot).forEachIndexed { i, right ->
                    if (i > 0) row.append(' ')
                    row.append(if (right) RIGHT_GLYPH else LEFT_GLYPH)
                }
                Component.literal(row.toString())
            }
            comboParts.add(combo)
            val cost = costs[slot].firstOrNull { it.mana } ?: costs[slot].firstOrNull()
            val costPart = cost?.let { Component.literal("-${it.amount}") }
            costParts.add(costPart)
            var w = maxOf(font.width(nameParts[slot]), font.width(combo))
            if (costPart != null) w += font.width(costPart) + COST_GAP
            contentW = maxOf(contentW, w)
        }
        val cellW = contentW + PAD * 2
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, cellW * COLS, ROW_H * ROWS)

        val scale = HudLayoutManager.scale(ID)
        val (baseX, baseY) = HudLayoutManager.resolve(ID, graphics.guiWidth(), graphics.guiHeight())

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        OwTheme.hudPanel(graphics, ox, oy, boxW, boxH)
        for (slot in 0 until SLOT_COUNT) {
            val match = QuickCast.slotCombo(slot).take(prefix.size) == prefix
            val fired = lastName != null && names[slot] == lastName
            val lit = match || fired
            val color = if (lit) ChatFormatting.WHITE else ChatFormatting.GRAY
            val cx = ox + (slot % COLS) * cellW
            val cy = oy + (slot / COLS) * ROW_H
            if (match) graphics.fill(cx, cy, cx + cellW, cy + ROW_H, HudStyle.alpha(OwTheme.ACCENT, FILL_FADE))
            var tx = cx + PAD
            graphics.text(font, nameParts[slot].copy().withStyle(color), tx, cy + NAME_Y, 0xFFFFFFFF.toInt(), true)
            tx += font.width(nameParts[slot])
            costParts[slot]?.let {
                tx += COST_GAP
                val cost = costs[slot].firstOrNull { c -> c.mana } ?: costs[slot].first()
                val costColor = if (!lit) {
                    GRAY_TEXT
                } else if (cost.mana) {
                    MANA_TEXT
                } else {
                    HP_TEXT
                }
                graphics.text(font, it.copy().setStyle(Style.EMPTY.withColor(costColor)), tx, cy + NAME_Y, 0xFFFFFFFF.toInt(), true)
            }
            graphics.text(font, glyphRun(comboParts[slot].string, color), cx + PAD, cy + COMBO_Y, 0xFFFFFFFF.toInt(), true)
            if (match) HudStyle.fadeRule(graphics, cx, cy + ROW_H - 2, cellW, OwTheme.ACCENT_DIM, leftSolid = true)
            if (fired) graphics.outline(cx, cy, cellW, ROW_H, OwTheme.ACCENT)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun glyphRun(text: String, color: ChatFormatting): Component {
        val out = Component.empty()
        for (ch in text) {
            val part = Component.literal(ch.toString())
            val glyph = ch.toString()
            if (glyph == RIGHT_GLYPH || glyph == LEFT_GLYPH || glyph == ARROW_GLYPH) {
                part.setStyle(Style.EMPTY.withColor(color).withFont(GLYPH_FONT))
            } else {
                part.withStyle(color)
            }
            out.append(part)
        }
        return out
    }

    private companion object {
        const val ID = "spell_bar"
        const val SLOT_COUNT = 4
        const val COLS = 2
        const val ROWS = 2
        const val ROW_H = 30
        const val PAD = 3
        const val NAME_Y = 3
        const val COMBO_Y = 13
        const val UNKNOWN_NAME = "..."
        const val COST_GAP = 4
        val DEMO_PREFIX = listOf(true, false)
        val DEMO_NAMES = listOf("Bash", "Charge", "Uppercut", "War Scream")
        val DEMO_COSTS = listOf(
            listOf(WynnSpellSegments.SpellCost(40, true)),
            listOf(WynnSpellSegments.SpellCost(25, true)),
            emptyList(),
            emptyList(),
        )
        const val FILL_FADE = 0.12f
        const val MANA_TEXT = 0xFF7FE3FF.toInt()
        const val HP_TEXT = 0xFFD64545.toInt()
        const val GRAY_TEXT = 0xFFAAAAAA.toInt()
        const val RIGHT_GLYPH = "\uE104"
        const val LEFT_GLYPH = "\uE103"
        const val ARROW_GLYPH = "\uE106"
        val GLYPH_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft", "hud/gameplay/default/bottom_middle"))
    }
}

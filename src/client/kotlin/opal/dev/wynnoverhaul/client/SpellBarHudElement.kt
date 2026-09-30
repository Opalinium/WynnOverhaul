package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
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
        val costs = (0 until SLOT_COUNT).map { slot ->
            names[slot]?.let { WynnSpellTracker.spellCosts(it) }?.ifEmpty { null }
                ?: if (showPlaceholder) DEMO_COSTS.getOrNull(slot) ?: emptyList() else emptyList()
        }
        val lastName = WynnSpellTracker.lastCast?.takeIf { WynnSpellTracker.castVisible(now) }?.name

        val nameParts = (0 until SLOT_COUNT).map { slot -> Component.literal("${slot + 1}  ${names[slot] ?: UNKNOWN_NAME}") }
        val costParts = (0 until SLOT_COUNT).map { slot ->
            val cost = costs[slot].firstOrNull { it.mana } ?: costs[slot].firstOrNull()
            cost?.let { it to Component.literal("-${it.amount}") }
        }
        val badgeW = (0 until SLOT_COUNT).maxOf { slot -> badgeWidth(font, keys[slot], slot) }
        val costW = costParts.maxOf { it?.second?.let { c -> font.width(c) } ?: 0 }

        val contentW = (0 until SLOT_COUNT).maxOf { slot ->
            PAD + font.width(nameParts[slot]) + GAP + costW + GAP + badgeW + PAD
        }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, ROW_H * SLOT_COUNT)

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
            val nameColor = if (lit) OwTheme.TEXT else OwTheme.TEXT_DIM
            val cy = oy + slot * ROW_H

            if (match) graphics.fill(ox, cy, ox + boxW, cy + ROW_H, HudStyle.alpha(OwTheme.ACCENT, FILL_FADE))
            if (slot > 0) graphics.fill(ox + 1, cy, ox + boxW - 1, cy + 1, HudStyle.alpha(OwTheme.HAIRLINE, 0.6f))

            graphics.text(font, nameParts[slot].copy().withStyle(Style.EMPTY.withColor(nameColor)), ox + PAD, cy + TEXT_Y, 0xFFFFFFFF.toInt(), true)

            var tx = ox + boxW - PAD - badgeW
            val key = keys[slot]
            if (key != null) {
                HudStyle.keycap(graphics, font, tx, cy + (ROW_H - font.lineHeight - 3) / 2, key, if (lit) 1f else 0.75f)
            } else {
                drawComboGlyphs(graphics, font, tx, cy + TEXT_Y, slot, if (lit) OwTheme.TEXT else OwTheme.TEXT_FAINT)
            }

            costParts[slot]?.let { (cost, part) ->
                val cw = font.width(part)
                val costColor = if (!lit) OwTheme.TEXT_FAINT else if (cost.mana) MANA_TEXT else HP_TEXT
                graphics.text(font, part.copy().setStyle(Style.EMPTY.withColor(costColor)), tx - GAP - cw, cy + TEXT_Y, 0xFFFFFFFF.toInt(), true)
            }

            if (fired) HudStyle.brackets(graphics, ox + 1, cy + 1, boxW - 2, ROW_H - 2, OwTheme.ACCENT)
        }

        if (boxH > 0) graphics.fill(ox + 1, oy + boxH - 1, ox + boxW - 1, oy + boxH, HudStyle.alpha(OwTheme.HAIRLINE, 0.6f))

        if (scaled) graphics.pose().popMatrix()
    }

    private fun badgeWidth(font: net.minecraft.client.gui.Font, key: String?, slot: Int): Int {
        if (key != null) return font.width(key) + 8
        return QuickCast.slotCombo(slot).size * (GLYPH_W + GLYPH_GAP) - GLYPH_GAP
    }

    private fun drawComboGlyphs(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, x: Int, y: Int, slot: Int, color: Int) {
        var gx = x
        for (right in QuickCast.slotCombo(slot)) {
            val glyph = Component.literal(if (right) RIGHT_GLYPH else LEFT_GLYPH).setStyle(Style.EMPTY.withColor(color).withFont(GLYPH_FONT))
            graphics.text(font, glyph, gx, y, 0xFFFFFFFF.toInt(), true)
            gx += GLYPH_W + GLYPH_GAP
        }
    }

    private companion object {
        const val ID = "spell_bar"
        const val SLOT_COUNT = 4
        const val ROW_H = 16
        const val PAD = 4
        const val GAP = 6
        const val TEXT_Y = 4
        const val UNKNOWN_NAME = "..."
        const val GLYPH_W = 6
        const val GLYPH_GAP = 1
        val DEMO_PREFIX = listOf(true, false)
        val DEMO_NAMES = listOf("Bash", "Charge", "Uppercut", "War Scream")
        val DEMO_COSTS = listOf(
            listOf(WynnSpellSegments.SpellCost(40, true)),
            listOf(WynnSpellSegments.SpellCost(25, true)),
            emptyList(),
            emptyList(),
        )
        const val FILL_FADE = 0.14f
        const val MANA_TEXT = 0xFF7FE3FF.toInt()
        const val HP_TEXT = 0xFFD64545.toInt()
        const val RIGHT_GLYPH = ""
        const val LEFT_GLYPH = ""
        val GLYPH_FONT = FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft", "hud/gameplay/default/bottom_middle"))
    }
}

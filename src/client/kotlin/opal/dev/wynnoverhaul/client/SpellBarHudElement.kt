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
import kotlin.math.roundToInt

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
        val lit = BooleanArray(SLOT_COUNT) { slot ->
            val match = QuickCast.slotCombo(slot).take(prefix.size) == prefix
            match || (lastName != null && names[slot] == lastName)
        }
        val fired = BooleanArray(SLOT_COUNT) { slot -> lastName != null && names[slot] == lastName }
        val badgeW = (0 until SLOT_COUNT).maxOf { slot -> badgeWidth(font, keys[slot], slot) }
        val costW = costParts.maxOf { it?.second?.let { c -> font.width(c) } ?: 0 }

        val style = WynnOverhaulConfig.current.spellBarStyle.takeIf { it in STYLES } ?: ROWS
        val (contentW, contentH) = sizeFor(style, font, names, nameParts, badgeW, costW)
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, contentW, contentH)

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

        when (style) {
            TILES -> drawTiles(graphics, font, ox, oy, names, keys, costParts, lit, fired)
            ARC -> drawArc(graphics, font, ox, oy, boxW, nameParts, keys, costParts, lit, fired)
            CROSS -> drawCross(graphics, font, ox, oy, names, keys, costParts, lit, fired)
            else -> drawRows(graphics, font, ox, oy, boxW, boxH, nameParts, keys, costParts, badgeW, lit, fired)
        }

        if (scaled) graphics.pose().popMatrix()
    }

    private fun drawRows(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        ox: Int,
        oy: Int,
        boxW: Int,
        boxH: Int,
        nameParts: List<Component>,
        keys: List<String?>,
        costParts: List<Pair<WynnSpellSegments.SpellCost, Component>?>,
        badgeW: Int,
        lit: BooleanArray,
        fired: BooleanArray,
    ) {
        OwTheme.hudPanel(graphics, ox, oy, boxW, boxH)
        for (slot in 0 until SLOT_COUNT) {
            val nameColor = if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_DIM
            val cy = oy + slot * ROW_H

            if (lit[slot]) graphics.fill(ox, cy, ox + boxW, cy + ROW_H, HudStyle.alpha(OwTheme.ACCENT, FILL_FADE))
            if (slot > 0) graphics.fill(ox + 1, cy, ox + boxW - 1, cy + 1, HudStyle.alpha(OwTheme.HAIRLINE, 0.6f))

            graphics.text(font, nameParts[slot].copy().withStyle(Style.EMPTY.withColor(nameColor)), ox + PAD, cy + TEXT_Y, 0xFFFFFFFF.toInt(), true)

            val tx = ox + boxW - PAD - badgeW
            val key = keys[slot]
            if (key != null) {
                HudStyle.keycap(graphics, font, tx, cy + (ROW_H - font.lineHeight - 3) / 2, key, if (lit[slot]) 1f else 0.75f)
            } else {
                drawComboGlyphs(graphics, font, tx, cy + TEXT_Y, slot, if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_FAINT)
            }

            costParts[slot]?.let { (cost, part) ->
                val cw = font.width(part)
                val costColor = if (!lit[slot]) OwTheme.TEXT_FAINT else if (cost.mana) MANA_TEXT else HP_TEXT
                graphics.text(font, part.copy().setStyle(Style.EMPTY.withColor(costColor)), tx - GAP - cw, cy + TEXT_Y, 0xFFFFFFFF.toInt(), true)
            }

            if (fired[slot]) HudStyle.brackets(graphics, ox + 1, cy + 1, boxW - 2, ROW_H - 2, OwTheme.ACCENT)
        }

        if (boxH > 0) graphics.fill(ox + 1, oy + boxH - 1, ox + boxW - 1, oy + boxH, HudStyle.alpha(OwTheme.HAIRLINE, 0.6f))
    }

    private fun drawTiles(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        ox: Int,
        oy: Int,
        names: List<String?>,
        keys: List<String?>,
        costParts: List<Pair<WynnSpellSegments.SpellCost, Component>?>,
        lit: BooleanArray,
        fired: BooleanArray,
    ) {
        for (slot in 0 until SLOT_COUNT) {
            val x = ox + slot * (TILE + TILE_GAP)
            val y = oy
            val edge = if (lit[slot]) OwTheme.ACCENT else OwTheme.HAIRLINE
            graphics.fill(x, y, x + TILE, y + TILE, if (lit[slot]) HudStyle.GLASS_DEEP else HudStyle.GLASS)
            if (lit[slot]) graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, HudStyle.alpha(OwTheme.ACCENT, FILL_FADE))
            graphics.fill(x, y, x + TILE, y + 1, edge)
            graphics.fill(x, y + TILE - 1, x + TILE, y + TILE, edge)
            graphics.fill(x, y, x + 1, y + TILE, edge)
            graphics.fill(x + TILE - 1, y, x + TILE, y + TILE, edge)

            val abbr = abbreviate(names[slot] ?: UNKNOWN_NAME)
            val abbrColor = if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_DIM
            scaledText(graphics, font, x + TILE / 2, y + TILE / 2 - 2, abbr, abbrColor, 1.15f, centered = true)

            val key = keys[slot]
            if (key != null) {
                HudStyle.keycap(graphics, font, x + 1, y + 1, shortKey(key), if (lit[slot]) 1f else 0.7f)
            } else {
                drawComboGlyphs(graphics, font, x + 2, y + 2, slot, if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_FAINT)
            }

            costParts[slot]?.let { (cost, part) ->
                val cw = (font.width(part) * 0.7f).roundToInt()
                val costColor = if (cost.mana) MANA_TEXT else HP_TEXT
                scaledText(graphics, font, x + TILE - 2 - cw, y + TILE - 9, part.string, costColor, 0.7f, centered = false)
            }

            if (fired[slot]) HudStyle.brackets(graphics, x, y, TILE, TILE, OwTheme.ACCENT, 5)
        }
    }

    private fun drawArc(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        ox: Int,
        oy: Int,
        boxW: Int,
        nameParts: List<Component>,
        keys: List<String?>,
        costParts: List<Pair<WynnSpellSegments.SpellCost, Component>?>,
        lit: BooleanArray,
        fired: BooleanArray,
    ) {
        val pitch = ARC_TILE + 6
        val mid = (SLOT_COUNT - 1) / 2f
        val sag = 6f
        val cyBase = oy + ARC_TILE / 2 + 2
        var activeSlot = -1
        for (slot in 0 until SLOT_COUNT) if (lit[slot]) activeSlot = slot
        for (slot in 0 until SLOT_COUNT) {
            val u = (slot - mid) / mid.coerceAtLeast(0.5f)
            val cx = ox + ARC_TILE / 2 + 3 + slot * pitch
            val cy = cyBase + (sag * u * u).roundToInt()
            val big = lit[slot]
            val r = if (big) ARC_TILE / 2 + 2 else ARC_TILE / 2 - 2
            HotbarStyles.disc(graphics, cx, cy, r, HudStyle.GLASS_DEEP)
            HotbarStyles.ring(graphics, cx, cy, r, if (big) OwTheme.ACCENT else OwTheme.HAIRLINE)
            if (big) HotbarStyles.ring(graphics, cx, cy, r + 2, HudStyle.alpha(OwTheme.ACCENT, 0.5f))
            scaledText(graphics, font, cx, cy - 3, (slot + 1).toString(), if (big) OwTheme.TEXT else OwTheme.TEXT_DIM, if (big) 1f else 0.85f, centered = true)
            val key = keys[slot]
            val labelY = cy + r + 3
            if (key != null) {
                scaledText(graphics, font, cx, labelY, shortKey(key), if (big) OwTheme.TEXT else OwTheme.TEXT_FAINT, 0.75f, centered = true)
            } else {
                drawComboGlyphs(graphics, font, cx - QuickCast.slotCombo(slot).size * (GLYPH_W + GLYPH_GAP) / 2, labelY, slot, if (big) OwTheme.TEXT else OwTheme.TEXT_FAINT)
            }
            if (fired[slot]) HotbarStyles.ring(graphics, cx, cy, r + 4, HudStyle.alpha(OwTheme.ACCENT, 0.8f))
        }
        val statusY = oy + ARC_TILE + 14
        if (activeSlot >= 0) {
            val name = nameParts[activeSlot].string
            val cost = costParts[activeSlot]
            val label = if (cost != null) "$name  ${cost.second.string}" else name
            val lw = font.width(label)
            graphics.text(font, name, ox + boxW / 2 - lw / 2, statusY, OwTheme.TEXT, true)
            cost?.let { (c, part) ->
                val nw = font.width(name)
                val color = if (c.mana) MANA_TEXT else HP_TEXT
                graphics.text(font, part.copy().setStyle(Style.EMPTY.withColor(color)), ox + boxW / 2 - lw / 2 + nw + font.width("  "), statusY, 0xFFFFFFFF.toInt(), true)
            }
        }
    }

    private fun drawCross(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        ox: Int,
        oy: Int,
        names: List<String?>,
        keys: List<String?>,
        costParts: List<Pair<WynnSpellSegments.SpellCost, Component>?>,
        lit: BooleanArray,
        fired: BooleanArray,
    ) {
        var mainSlot = 0
        for (slot in 0 until SLOT_COUNT) if (lit[slot]) mainSlot = slot

        HudStyle.plate(graphics, ox, oy, CROSS_MAIN_W, CROSS_MAIN_H, OwTheme.ACCENT)
        val mainName = names[mainSlot] ?: UNKNOWN_NAME
        graphics.text(font, mainName, ox + PAD, oy + 5, OwTheme.TEXT, true)
        val key = keys[mainSlot]
        val badgeY = oy + CROSS_MAIN_H - font.lineHeight - 7
        if (key != null) {
            HudStyle.keycap(graphics, font, ox + PAD, badgeY, shortKey(key), 1f)
        } else {
            drawComboGlyphs(graphics, font, ox + PAD, badgeY + 2, mainSlot, OwTheme.TEXT)
        }
        costParts[mainSlot]?.let { (cost, part) ->
            val color = if (cost.mana) MANA_TEXT else HP_TEXT
            val cw = font.width(part)
            graphics.text(font, part.copy().setStyle(Style.EMPTY.withColor(color)), ox + CROSS_MAIN_W - PAD - cw, badgeY + 2, 0xFFFFFFFF.toInt(), true)
        }
        if (fired[mainSlot]) HudStyle.brackets(graphics, ox, oy, CROSS_MAIN_W, CROSS_MAIN_H, OwTheme.ACCENT)

        val sideX = ox + CROSS_MAIN_W + GAP
        var chip = 0
        for (slot in 0 until SLOT_COUNT) {
            if (slot == mainSlot) continue
            val cy = oy + chip * (CROSS_CHIP_H + 2)
            val edge = if (lit[slot]) OwTheme.ACCENT else OwTheme.TILE_BORDER
            graphics.fill(sideX, cy, sideX + CROSS_SIDE_W, cy + CROSS_CHIP_H, HudStyle.TRACK)
            graphics.fill(sideX, cy, sideX + CROSS_SIDE_W, cy + 1, edge)
            graphics.fill(sideX, cy + CROSS_CHIP_H - 1, sideX + CROSS_SIDE_W, cy + CROSS_CHIP_H, edge)
            val abbr = abbreviate(names[slot] ?: UNKNOWN_NAME)
            graphics.text(font, abbr, sideX + 3, cy + 2, if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_DIM, true)
            val sk = keys[slot]?.let { shortKey(it) }
            if (sk != null) {
                val kw = font.width(sk)
                graphics.text(font, sk, sideX + CROSS_SIDE_W - 3 - kw, cy + 2, OwTheme.TEXT_FAINT, true)
            }
            if (fired[slot]) HudStyle.brackets(graphics, sideX, cy, CROSS_SIDE_W, CROSS_CHIP_H, OwTheme.ACCENT, 3)
            chip++
        }
    }

    private fun sizeFor(
        style: String,
        font: net.minecraft.client.gui.Font,
        names: List<String?>,
        nameParts: List<Component>,
        badgeW: Int,
        costW: Int,
    ): Pair<Int, Int> = when (style) {
        TILES -> (SLOT_COUNT * TILE + (SLOT_COUNT - 1) * TILE_GAP) to TILE
        ARC -> {
            val w = SLOT_COUNT * (ARC_TILE + 6)
            val statusW = (0 until SLOT_COUNT).maxOf { font.width(names[it] ?: UNKNOWN_NAME) } + 40
            w.coerceAtLeast(statusW) to (ARC_TILE + 28)
        }
        CROSS -> (CROSS_MAIN_W + GAP + CROSS_SIDE_W) to CROSS_MAIN_H
        else -> {
            val contentW = (0 until SLOT_COUNT).maxOf { slot ->
                PAD + font.width(nameParts[slot]) + GAP + costW + GAP + badgeW + PAD
            }
            contentW to ROW_H * SLOT_COUNT
        }
    }

    private fun shortKey(label: String): String = if (label.length <= 4) label else label.take(3)

    private fun abbreviate(name: String): String {
        val words = name.trim().split(' ').filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> (words[0].take(1) + words[1].take(1)).uppercase()
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun scaledText(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        cx: Int,
        cy: Int,
        text: String,
        color: Int,
        scale: Float,
        centered: Boolean,
    ) {
        val pose = graphics.pose()
        pose.pushMatrix()
        val w = font.width(text)
        val px = if (centered) cx - (w * scale) / 2f else cx.toFloat()
        pose.translate(px, cy.toFloat())
        pose.scale(scale)
        graphics.text(font, text, 0, 0, color, true)
        pose.popMatrix()
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

    companion object {
        const val ID = "spell_bar"
        const val SLOT_COUNT = 4
        const val ROW_H = 16
        const val PAD = 4
        const val GAP = 6
        const val TEXT_Y = 4
        const val UNKNOWN_NAME = "..."
        const val GLYPH_W = 6
        const val GLYPH_GAP = 1

        const val ROWS = "ROWS"
        const val TILES = "TILES"
        const val ARC = "ARC"
        const val CROSS = "CROSS"
        val STYLES = listOf(ROWS, TILES, ARC, CROSS)
        fun styleLabel(style: String): String = when (style) {
            TILES -> "Tile grid"
            ARC -> "Arc"
            CROSS -> "Focus cross"
            else -> "Row list"
        }

        const val TILE = 34
        const val TILE_GAP = 4
        const val ARC_TILE = 26
        const val CROSS_MAIN_W = 112
        const val CROSS_MAIN_H = 40
        const val CROSS_SIDE_W = 54
        const val CROSS_CHIP_H = 12
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

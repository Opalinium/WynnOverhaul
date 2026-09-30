package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.client.renderer.RenderPipelines
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
        val badgeW = (0 until SLOT_COUNT).maxOf { slot -> keyBadgeWidth(font, keys[slot], slot) }
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
            ARC -> drawArc(graphics, font, ox, oy, names, keys, costParts, lit, fired)
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
            drawKeyBadge(graphics, font, tx, cy + (ROW_H - font.lineHeight - 3) / 2, keys[slot], slot, if (lit[slot]) 1f else 0.75f)

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
        var activeSlot = -1
        for (slot in 0 until SLOT_COUNT) {
            if (lit[slot]) activeSlot = slot
            val x = ox + slot * (TILE + TILE_GAP)
            val y = oy
            val edge = if (lit[slot]) OwTheme.ACCENT else OwTheme.HAIRLINE
            graphics.fill(x, y, x + TILE, y + TILE, if (lit[slot]) HudStyle.GLASS_DEEP else HudStyle.GLASS)
            if (lit[slot]) graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, HudStyle.alpha(OwTheme.ACCENT, FILL_FADE))
            graphics.fill(x, y, x + TILE, y + 1, edge)
            graphics.fill(x, y + TILE - 1, x + TILE, y + TILE, edge)
            graphics.fill(x, y, x + 1, y + TILE, edge)
            graphics.fill(x + TILE - 1, y, x + TILE, y + TILE, edge)

            val icon = SpellIcons.iconFor(names[slot])
            if (icon != null) {
                val inset = 3
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + inset, y + inset, TILE - inset * 2, TILE - inset * 2)
            } else {
                val numColor = if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_DIM
                scaledText(graphics, font, x + TILE / 2, y + 5, (slot + 1).toString(), numColor, 1.3f, centered = true)
            }

            val key = keys[slot]
            drawKeyBadge(graphics, font, x + 1, y + 1, key, slot, if (lit[slot]) 1f else 0.75f)

            if (fired[slot]) HudStyle.brackets(graphics, x, y, TILE, TILE, OwTheme.ACCENT, 5)
        }
        val boxW = SLOT_COUNT * TILE + (SLOT_COUNT - 1) * TILE_GAP
        drawCaption(graphics, font, ox, oy + TILE + GAP_ROW, boxW, activeSlot, names, costParts)
    }

    private fun drawArc(
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
        val pitch = ARC_TILE + ARC_GAP
        val mid = (SLOT_COUNT - 1) / 2f
        val sag = 6f
        val cyBase = oy + ARC_TILE / 2 + 2
        var activeSlot = -1
        for (slot in 0 until SLOT_COUNT) {
            if (lit[slot]) activeSlot = slot
            val u = (slot - mid) / mid.coerceAtLeast(0.5f)
            val cx = ox + ARC_TILE / 2 + 3 + slot * pitch
            val cy = cyBase + (sag * u * u).roundToInt()
            val big = lit[slot]
            val r = if (big) ARC_TILE / 2 + 2 else ARC_TILE / 2 - 2
            HotbarStyles.disc(graphics, cx, cy, r, HudStyle.GLASS_DEEP)
            HotbarStyles.ring(graphics, cx, cy, r, if (big) OwTheme.ACCENT else OwTheme.HAIRLINE)
            if (big) HotbarStyles.ring(graphics, cx, cy, r + 2, HudStyle.alpha(OwTheme.ACCENT, 0.5f))
            val icon = SpellIcons.iconFor(names[slot])
            if (icon != null) {
                val iconSize = (r * 1.5f).roundToInt()
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, cx - iconSize / 2, cy - iconSize / 2, iconSize, iconSize)
            } else {
                scaledText(graphics, font, cx, cy - 3, (slot + 1).toString(), if (big) OwTheme.TEXT else OwTheme.TEXT_DIM, if (big) 1f else 0.85f, centered = true)
            }

            val key = keys[slot]
            val bw = keyBadgeWidth(font, key, slot)
            drawKeyBadge(graphics, font, cx - bw / 2, cy + r + 3, key, slot, if (big) 1f else 0.7f)

            if (fired[slot]) HotbarStyles.ring(graphics, cx, cy, r + 4, HudStyle.alpha(OwTheme.ACCENT, 0.8f))
        }
        val boxW = SLOT_COUNT * pitch
        val rowH = ARC_TILE + 10 + font.lineHeight
        drawCaption(graphics, font, ox, oy + rowH + GAP_ROW, boxW, activeSlot, names, costParts)
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
        graphics.text(font, mainName, ox + PAD, oy + 4, OwTheme.TEXT, true)
        val badgeH = font.lineHeight + 3
        val badgeY = oy + CROSS_MAIN_H - badgeH - 4
        val mainKey = keys[mainSlot]
        drawKeyBadge(graphics, font, ox + PAD, badgeY, mainKey, mainSlot, 1f)
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
            val chipIcon = SpellIcons.iconFor(names[slot])
            if (chipIcon != null) {
                val s = CROSS_CHIP_H - 4
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, chipIcon, sideX + 2, cy + 2, s, s)
            } else {
                graphics.text(font, (slot + 1).toString(), sideX + 4, cy + (CROSS_CHIP_H - font.lineHeight) / 2, if (lit[slot]) OwTheme.TEXT else OwTheme.TEXT_DIM, true)
            }
            val chipKey = keys[slot]
            val cbw = keyBadgeWidth(font, chipKey, slot)
            val cbh = font.lineHeight + 3
            drawKeyBadge(graphics, font, sideX + CROSS_SIDE_W - cbw - 3, cy + (CROSS_CHIP_H - cbh) / 2, chipKey, slot, if (lit[slot]) 1f else 0.75f)
            if (fired[slot]) HudStyle.brackets(graphics, sideX, cy, CROSS_SIDE_W, CROSS_CHIP_H, OwTheme.ACCENT, 3)
            chip++
        }
    }

    private fun drawCaption(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        ox: Int,
        oy: Int,
        boxW: Int,
        activeSlot: Int,
        names: List<String?>,
        costParts: List<Pair<WynnSpellSegments.SpellCost, Component>?>,
    ) {
        if (activeSlot < 0) return
        val name = names[activeSlot] ?: UNKNOWN_NAME
        val nw = font.width(name)
        val cost = costParts[activeSlot]
        val cw = cost?.let { font.width(it.second) + font.width("  ") } ?: 0
        val tx = ox + (boxW - nw - cw) / 2
        graphics.text(font, name, tx, oy, OwTheme.TEXT, true)
        cost?.let { (c, part) ->
            val color = if (c.mana) MANA_TEXT else HP_TEXT
            graphics.text(font, part.copy().setStyle(Style.EMPTY.withColor(color)), tx + nw + font.width("  "), oy, 0xFFFFFFFF.toInt(), true)
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
        TILES -> (SLOT_COUNT * TILE + (SLOT_COUNT - 1) * TILE_GAP) to (TILE + GAP_ROW + CAPTION_H)
        ARC -> {
            val w = SLOT_COUNT * (ARC_TILE + ARC_GAP)
            val rowH = ARC_TILE + 10 + font.lineHeight
            w to (rowH + GAP_ROW + CAPTION_H)
        }
        CROSS -> {
            val chips = SLOT_COUNT - 1
            val chipStackH = chips * CROSS_CHIP_H + (chips - 1) * 2
            (CROSS_MAIN_W + GAP + CROSS_SIDE_W) to maxOf(CROSS_MAIN_H, chipStackH)
        }
        else -> {
            val contentW = (0 until SLOT_COUNT).maxOf { slot ->
                PAD + font.width(nameParts[slot]) + GAP + costW + GAP + badgeW + PAD
            }
            contentW to ROW_H * SLOT_COUNT
        }
    }

    private fun shortKey(label: String): String = if (label.length <= 4) label else label.take(3)

    private fun keyBadgeWidth(font: net.minecraft.client.gui.Font, key: String?, slot: Int): Int {
        if (key != null) return font.width(shortKey(key)) + 8
        return QuickCast.slotCombo(slot).size * (GLYPH_W + GLYPH_GAP) - GLYPH_GAP + 8
    }

    private fun drawKeyBadge(
        graphics: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        x: Int,
        y: Int,
        key: String?,
        slot: Int,
        fade: Float,
    ): Int {
        if (key != null) return HudStyle.keycap(graphics, font, x, y, shortKey(key), fade)
        val inner = QuickCast.slotCombo(slot).size * (GLYPH_W + GLYPH_GAP) - GLYPH_GAP
        val w = inner + 8
        val h = font.lineHeight + 3
        drawComboGlyphs(graphics, font, x + 4, y + (h - font.lineHeight) / 2, slot, HudStyle.alpha(OwTheme.TEXT, fade))
        return w
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
        const val ARC_GAP = 10
        const val CROSS_MAIN_W = 112
        const val CROSS_MAIN_H = 36
        const val CROSS_SIDE_W = 54
        const val CROSS_CHIP_H = 16
        const val CAPTION_H = 13
        const val GAP_ROW = 4
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

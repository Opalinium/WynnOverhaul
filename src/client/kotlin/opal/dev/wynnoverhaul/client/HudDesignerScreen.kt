package opal.dev.wynnoverhaul.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class HudDesignerScreen(private val parentScreen: Screen?) : Screen(Component.literal("Customize HUD")) {
    private var activeId: String? = null
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0
    private var dragRawX: Double = 0.0
    private var dragRawY: Double = 0.0
    private var snapGuidesX: List<Int> = emptyList()
    private var snapGuidesY: List<Int> = emptyList()

    private val toolbarWidgets = ArrayList<AbstractWidget>()
    private var toolbarLeft = 0
    private var toolbarRight = 0
    private var toolbarFade = 1f
    private var helpFade = 1f

    private var resizingId: String? = null
    private var resizeScaleMode: Boolean = false
    private var resizeBarStretch: Boolean = false
    private var resizeStartW: Double = 0.0
    private var resizeStartH: Double = 0.0
    private var resizeDx: Double = 0.0
    private var resizeDy: Double = 0.0
    private var resizeOriginX: Int = 0
    private var resizeOriginY: Int = 0

    private fun altHeld(): Boolean {
        val window = Minecraft.getInstance().window
        return InputConstants.isKeyDown(window, InputConstants.KEY_LALT) || InputConstants.isKeyDown(window, InputConstants.KEY_RALT)
    }

    override fun init() {
        OwDropdownOverlay.close()
        toolbarWidgets.clear()
        val config = WynnOverhaulConfig.current
        val cycleW = 170
        val saveW = 100
        val undoW = 80
        val helpW = 24
        val gap = 4
        val left = (width - cycleW - saveW - undoW - helpW - gap * 3) / 2
        toolbarLeft = left
        toolbarRight = left + cycleW + saveW + undoW + helpW + gap * 3
        addTool(
            OwDropdown(
                left,
                TOOLBAR_Y,
                cycleW,
                TOOLBAR_H,
                "Layout",
                HudLayoutPresets.IDS.map { OwDropdownOverlay.Option(it, HudLayoutPresets.label(it)) },
                { HudLayoutPresets.selected(config) },
            ) {
                HudLayoutPresets.apply(config, it)
                rebuildWidgets()
            },
        )
        val saveX = left + cycleW + gap
        addTool(
            OwButton(saveX, TOOLBAR_Y, saveW, TOOLBAR_H, Component.literal("Save layout...")) {
                val slots = (1..HudLayoutPresets.SLOT_COUNT).map { HudLayoutPresets.slotId(it) }
                OwDropdownOverlay.open(saveX, TOOLBAR_Y + TOOLBAR_H, saveW, 0, slots.map { OwDropdownOverlay.Option(it, HudLayoutPresets.slotLabel(it)) }, "") {
                    HudLayoutPresets.saveTo(config, it)
                    rebuildWidgets()
                }
            },
        )
        addTool(
            OwButton(
                left + cycleW + gap + saveW + gap,
                TOOLBAR_Y,
                undoW,
                TOOLBAR_H,
                Component.literal("Undo swap"),
                enabled = { HudLayoutPresets.canUndo(config) },
            ) {
                HudLayoutPresets.undo(config)
                rebuildWidgets()
            },
        )
        addTool(
            OwButton(left + cycleW + gap + saveW + gap + undoW + gap, TOOLBAR_Y, helpW, TOOLBAR_H, Component.literal("?"), accent = helpOpen) {
                helpOpen = !helpOpen
                rebuildWidgets()
            },
        )
    }

    private fun addTool(widget: AbstractWidget) {
        toolbarWidgets.add(widget)
        addRenderableWidget(widget)
    }

    private fun overlapsBox(b: IntArray, x0: Int, y0: Int, x1: Int, y1: Int): Boolean =
        b[0] < x1 + FADE_MARGIN && b[2] > x0 - FADE_MARGIN && b[1] < y1 + FADE_MARGIN && b[3] > y0 - FADE_MARGIN

    private fun updateFades() {
        val movingId = activeId ?: resizingId
        val bounds = movingId?.let { HudLayoutManager.bounds(it, width, height) }
        val overToolbar = bounds != null && overlapsBox(bounds, toolbarLeft, TOOLBAR_Y, toolbarRight, TOOLBAR_Y + TOOLBAR_H)
        var overHelp = false
        if (bounds != null && helpOpen) {
            val r = helpRect(helpLines())
            overHelp = overlapsBox(bounds, r[0], r[1], r[2], r[3])
        }
        toolbarFade = approach(toolbarFade, if (overToolbar) FADED_ALPHA else 1f)
        helpFade = approach(helpFade, if (overHelp) FADED_ALPHA else 1f)
        for (widget in toolbarWidgets) widget.alpha = toolbarFade
    }

    private fun approach(value: Float, target: Float): Float {
        val next = value + (target - value) * FADE_SPEED
        return if (kotlin.math.abs(next - target) < 0.01f) target else next
    }

    private fun helpLines(): List<String> {
        val maxW = (width - 40).coerceAtMost(HELP_MAX_W) - HELP_PAD * 2
        val out = ArrayList<String>()
        for (entry in HELP_ENTRIES) {
            val wrapped = HudStyle.wrap(font, entry, maxW - font.width("- "))
            wrapped.forEachIndexed { i, line -> out.add(if (i == 0) "- $line" else "  $line") }
        }
        return out
    }

    private fun helpRect(lines: List<String>): IntArray {
        val w = ((width - 40).coerceAtMost(HELP_MAX_W))
        val h = lines.size * (TEXT_H + 2) + HELP_PAD * 2
        val x = (width - w) / 2
        val y = height - h - 8
        return intArrayOf(x, y, x + w, y + h)
    }

    private fun drawHelp(graphics: GuiGraphicsExtractor) {
        if (!helpOpen || helpFade <= 0.02f) return
        val lines = helpLines()
        val r = helpRect(lines)
        graphics.fill(r[0], r[1], r[2], r[3], HudStyle.alpha(0xEE100C08.toInt(), helpFade))
        graphics.outline(r[0], r[1], r[2] - r[0], r[3] - r[1], HudStyle.alpha(OwTheme.BORDER_BRIGHT, helpFade))
        var ty = r[1] + HELP_PAD
        for (line in lines) {
            graphics.text(font, line, r[0] + HELP_PAD, ty, HudStyle.alpha(OwTheme.TEXT_DIM, helpFade))
            ty += TEXT_H + 2
        }
    }

    private fun inHelp(mx: Int, my: Int): Boolean {
        if (!helpOpen) return false
        val r = helpRect(helpLines())
        return mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val covered = OwDropdownOverlay.covers(mouseX, mouseY, height)
        val mx = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseX
        val my = if (covered) OwDropdownOverlay.HIDDEN_MOUSE else mouseY
        renderDesigner(graphics, mx, my, partialTick)
        OwDropdownOverlay.render(graphics, mouseX, mouseY, height)
    }

    private fun renderDesigner(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        updateFades()
        drawGridlines(graphics)
        for (spec in HudLayoutManager.specs()) {
            val (w, h) = HudLayoutManager.peekSize(spec.id)
            val b = HudLayoutManager.bounds(spec.id, width, height)
            val x0 = b[0]
            val y0 = b[1]
            val x1 = b[2]
            val y1 = b[3]
            val hovered = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1
            val dragging = activeId == spec.id
            val locked = HudLayoutManager.isLocked(spec.id)

            graphics.fill(x0, y0, x1, y1, if (dragging) 0x60FFD54F.toInt() else if (hovered) 0x40FFD54F.toInt() else 0x30120D08.toInt())
            graphics.outline(x0 - 1, y0 - 1, (x1 - x0) + 2, (y1 - y0) + 2, if (hovered) OwTheme.ACCENT else OwTheme.BORDER_BRIGHT)

            val compact = (y1 - y0) < COMPACT_H
            val lockX = if (compact) x0 + 2 else x1 - LOCK_SIZE - 2
            val lockY = y0 + 2
            val scalePct = (HudLayoutManager.scale(spec.id) * 100).toInt()
            val menu = HudLayoutManager.styleMenu(spec.id)
            val styleTag = if (menu != null && (menu.overridden || spec.id !in HudLayoutManager.BAR_IDS)) "  [${menu.currentLabel}]" else ""
            val label = if (!spec.barStretch && scalePct != 100) {
                "${spec.displayName}  ${w}x$h  $scalePct%$styleTag"
            } else {
                "${spec.displayName}  ${w}x$h$styleTag"
            }
            val chipSpace = if (menu != null) CHIP_W + 2 else 0
            val labelX0 = if (compact) lockX + LOCK_SIZE + 2 + chipSpace else x0 + 3

            val labelX1 = if (compact && (y1 - y0) < TEXT_H + 2 + HANDLE_SIZE) x1 - HANDLE_SIZE - 2 else if (compact) x1 - 1 else lockX - 1 - chipSpace
            val labelW = (labelX1 - labelX0).coerceAtLeast(0)
            if (labelW > MIN_LABEL_W && (y1 - y0) >= TEXT_H + 3) {
                val shortLabel = truncateToWidth(font, label, labelW)
                graphics.fill(labelX0 - 1, y0 + 1, labelX1, y0 + TEXT_H + 2, 0xE0100C08.toInt())
                graphics.text(font, shortLabel, labelX0, y0 + 2, OwTheme.TEXT)
            }

            HudLayoutManager.drawLockGlyph(
                graphics,
                lockX,
                lockY,
                LOCK_SIZE,
                LOCK_SIZE,
                locked = locked,
                hovered = mouseX >= lockX && mouseX < lockX + LOCK_SIZE && mouseY >= lockY && mouseY < lockY + LOCK_SIZE,
            )

            if (menu != null) {
                val chip = chipRect(spec)
                val chipHovered = mouseX >= chip[0] && mouseX < chip[0] + CHIP_W && mouseY >= chip[1] && mouseY < chip[1] + LOCK_SIZE
                val tint = if (menu.overridden || chipHovered) OwTheme.ACCENT else OwTheme.TEXT_DIM
                graphics.fill(chip[0], chip[1], chip[0] + CHIP_W, chip[1] + LOCK_SIZE, if (chipHovered) OwTheme.TILE_HOVER else 0xE0100C08.toInt())
                graphics.outline(chip[0], chip[1], CHIP_W, LOCK_SIZE, if (menu.overridden) OwTheme.ACCENT else OwTheme.BORDER_BRIGHT)
                val initial = menu.currentLabel.take(1)
                graphics.text(font, initial, chip[0] + 3, chip[1] + (LOCK_SIZE - TEXT_H) / 2 + 1, tint)
                val cx = chip[0] + CHIP_W - 6
                val cy = chip[1] + LOCK_SIZE / 2
                for (row in 0 until 3) graphics.fill(cx - 2 + row, cy - 1 + row, cx + 3 - row, cy + row, tint)
            }

            if (!locked) {
                val hx = x1 - HANDLE_SIZE
                val hy = y1 - HANDLE_SIZE
                val handleHovered = mouseX >= hx && mouseX < hx + HANDLE_SIZE && mouseY >= hy && mouseY < hy + HANDLE_SIZE
                graphics.fill(hx, hy, hx + HANDLE_SIZE, hy + HANDLE_SIZE, if (handleHovered || resizingId == spec.id) OwTheme.ACCENT else OwTheme.BORDER_BRIGHT)
            }
        }

        for (guideX in snapGuidesX) {
            graphics.fill(guideX, 0, guideX + 1, height, GUIDE_LINE)
        }
        for (guideY in snapGuidesY) {
            graphics.fill(0, guideY, width, guideY + 1, GUIDE_LINE)
        }

        drawHelp(graphics)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }

    private fun drawGridlines(graphics: GuiGraphicsExtractor) {
        var gx = 0
        while (gx <= width) {
            val center = gx == width / 2
            graphics.fill(gx, 0, gx + 1, height, if (center) GRID_CENTER else GRID_MINOR)
            gx += HudLayoutManager.SNAP_GRID
        }
        var gy = 0
        while (gy <= height) {
            val center = gy == height / 2
            graphics.fill(0, gy, width, gy + 1, if (center) GRID_CENTER else GRID_MINOR)
            gy += HudLayoutManager.SNAP_GRID
        }
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (OwDropdownOverlay.isOpen && event.key() == KEY_ESCAPE) {
            OwDropdownOverlay.close()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (OwDropdownOverlay.mouseScrolled(mouseX.toInt(), mouseY.toInt(), scrollY, height)) return true
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        if (OwDropdownOverlay.mouseClicked(event.x().toInt(), event.y().toInt(), height)) return true
        if (super.mouseClicked(event, doubled)) return true
        if (inHelp(event.x().toInt(), event.y().toInt())) return true
        val spec = hitTest(event.x().toInt(), event.y().toInt()) ?: return super.mouseClicked(event, doubled)
        val locked = HudLayoutManager.isLocked(spec.id)

        if (inLockGlyph(spec, event.x().toInt(), event.y().toInt())) {
            HudLayoutManager.setLocked(spec.id, !locked)
            return true
        }

        if (HudLayoutManager.styleMenu(spec.id) != null && inChip(spec, event.x().toInt(), event.y().toInt())) {
            openStyleMenu(spec)
            return true
        }

        if (event.button() == 1 && !inResizeHandle(spec, event.x().toInt(), event.y().toInt())) {
            HudLayoutManager.setLocked(spec.id, !locked)
            return true
        }

        if (event.button() == 0 && !locked) {
            if (inResizeHandle(spec, event.x().toInt(), event.y().toInt())) {
                val (w, h) = HudLayoutManager.peekSize(spec.id)
                resizingId = spec.id
                resizeBarStretch = spec.barStretch
                resizeScaleMode = event.hasAltDown() && !spec.barStretch
                if (resizeScaleMode) {
                    resizeStartW = (w * HudLayoutManager.scale(spec.id)).toDouble()
                } else {
                    resizeStartW = w.toDouble()
                    resizeStartH = h.toDouble()
                }
                resizeDx = 0.0
                resizeDy = 0.0
                val origin = boxOf(spec)
                resizeOriginX = origin[0]
                resizeOriginY = origin[1]
                snapGuidesX = emptyList()
                snapGuidesY = emptyList()
                return true
            }
            activeId = spec.id
            val b = boxOf(spec)
            dragStartX = b[0]
            dragStartY = b[1]
            dragRawX = 0.0
            dragRawY = 0.0
            snapGuidesX = emptyList()
            snapGuidesY = emptyList()
            return true
        }
        return super.mouseClicked(event, doubled)
    }

    override fun mouseDragged(event: MouseButtonEvent, amountX: Double, amountY: Double): Boolean {
        val dragId = activeId
        if (dragId != null) {
            dragRawX += amountX
            dragRawY += amountY
            val tx = dragStartX + dragRawX.toInt()
            val ty = dragStartY + dragRawY.toInt()
            if (altHeld()) {
                HudLayoutManager.moveTo(dragId, tx, ty, width, height)
                snapGuidesX = emptyList()
                snapGuidesY = emptyList()
            } else {
                val snapped = HudLayoutManager.snapMove(dragId, tx, ty, width, height)
                snapGuidesX = snapped.guidesX
                snapGuidesY = snapped.guidesY
            }
            return true
        }
        val resizeId = resizingId
        if (resizeId != null) {
            resizeDx += amountX
            resizeDy += amountY
            if (resizeScaleMode) {
                val (w, h) = HudLayoutManager.peekSize(resizeId)
                if (w > 0 && h > 0) {
                    val fitScale = maxOf(0.5f, minOf(width / w.toFloat(), height / h.toFloat()))
                    val scaled = minOf(((resizeStartW + resizeDx) / w).toFloat(), 2.5f, fitScale)
                    HudLayoutManager.setScale(resizeId, scaled)
                }
            } else {
                val scale = HudLayoutManager.scale(resizeId)
                val unit = if (resizeBarStretch) 1f else scale
                var rawW = resizeStartW + resizeDx / unit
                var rawH = resizeStartH + resizeDy / unit
                var guideX: Int? = null
                var guideY: Int? = null
                if (!altHeld()) {
                    val right = HudLayoutManager.snapEdge(resizeId, true, (resizeOriginX + rawW * unit).toInt(), width, height)
                    val bottom = HudLayoutManager.snapEdge(resizeId, false, (resizeOriginY + rawH * unit).toInt(), width, height)
                    rawW = (right.value - resizeOriginX) / unit.toDouble()
                    rawH = (bottom.value - resizeOriginY) / unit.toDouble()
                    guideX = right.guide
                    guideY = bottom.guide
                }
                snapGuidesX = listOfNotNull(guideX)
                snapGuidesY = listOfNotNull(guideY)
                val minW = if (resizeBarStretch) MIN_BAR_W else MIN_BOX_W
                val maxW = ((width - resizeOriginX) / unit).toInt().coerceAtLeast(minW)
                val maxH = ((height - resizeOriginY) / unit).toInt().coerceAtLeast(MIN_BOX_H)
                HudLayoutManager.setBoxSize(resizeId, rawW.toInt().coerceIn(minW, maxW), rawH.toInt().coerceIn(MIN_BOX_H, maxH))
                HudLayoutManager.moveTo(resizeId, resizeOriginX, resizeOriginY, width, height)
            }
            HudLayoutManager.applyDrag(resizeId, 0, 0, width, height)
            return true
        }
        return super.mouseDragged(event, amountX, amountY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val dragId = activeId
        if (dragId != null) {
            activeId = null
            resizingId = null
            snapGuidesX = emptyList()
            snapGuidesY = emptyList()
            HudLayoutManager.persist()
            return true
        }
        if (resizingId != null) {
            resizingId = null
            snapGuidesX = emptyList()
            snapGuidesY = emptyList()
            HudLayoutManager.persist()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun onClose() {
        val p = parentScreen
        val minecraft = Minecraft.getInstance()
        if (p != null) minecraft.setScreenAndShow(p) else minecraft.gui.setScreen(null)
    }

    private fun boxOf(spec: HudLayoutManager.HudElementSpec): IntArray {
        return HudLayoutManager.bounds(spec.id, width, height)
    }

    private fun hitTest(mx: Int, my: Int): HudLayoutManager.HudElementSpec? =
        HudLayoutManager.specs().firstOrNull { spec ->
            val b = boxOf(spec)
            mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]
        }

    private fun inResizeHandle(spec: HudLayoutManager.HudElementSpec, mx: Int, my: Int): Boolean {
        val b = boxOf(spec)
        val hx = b[2] - HANDLE_SIZE
        val hy = b[3] - HANDLE_SIZE
        return mx >= hx && mx < hx + HANDLE_SIZE && my >= hy && my < hy + HANDLE_SIZE
    }

    private fun openStyleMenu(spec: HudLayoutManager.HudElementSpec) {
        val menu = HudLayoutManager.styleMenu(spec.id) ?: return
        val options = menu.options.map { OwDropdownOverlay.Option(it.first, it.second) }
        val chip = chipRect(spec)
        OwDropdownOverlay.open(chip[0], chip[1], CHIP_W, LOCK_SIZE, options, menu.selected) {
            menu.select(it)
            HudLayoutManager.persist()
        }
    }

    private fun chipRect(spec: HudLayoutManager.HudElementSpec): IntArray {
        val b = boxOf(spec)
        val compact = (b[3] - b[1]) < COMPACT_H
        val x = if (compact) b[0] + 2 + LOCK_SIZE + 2 else b[2] - LOCK_SIZE - 2 - CHIP_W - 2
        return intArrayOf(x, b[1] + 2)
    }

    private fun inChip(spec: HudLayoutManager.HudElementSpec, mx: Int, my: Int): Boolean {
        val chip = chipRect(spec)
        return mx >= chip[0] && mx < chip[0] + CHIP_W && my >= chip[1] && my < chip[1] + LOCK_SIZE
    }

    private fun inLockGlyph(spec: HudLayoutManager.HudElementSpec, mx: Int, my: Int): Boolean {
        val b = boxOf(spec)

        val lockX = if ((b[3] - b[1]) < COMPACT_H) b[0] + 2 else b[2] - LOCK_SIZE - 2
        val lockY = b[1] + 2
        return mx >= lockX && mx < lockX + LOCK_SIZE && my >= lockY && my < lockY + LOCK_SIZE
    }

    private companion object {
        var helpOpen = true
        const val FADED_ALPHA = 0.22f
        const val FADE_SPEED = 0.35f
        const val FADE_MARGIN = 6
        const val HELP_MAX_W = 460
        const val HELP_PAD = 6
        val HELP_ENTRIES = listOf(
            "Drag an element to move it. It snaps to edges and centres; hold Alt to drag freely.",
            "The corner handle resizes the box with the same snapping. Alt frees it, Alt+click the handle scales instead.",
            "Click the lock, or right-click an element, to pin it in place.",
            "The chip on a bar, the hotbar or the chat opens that element's style menu. On a bar, a gold outline means it overrides the group style.",
            "Layout switches the whole arrangement (it overrides locks) and Undo swap goes back. Save layout... stores what you have now into a Custom slot.",
            "Press Esc to close, or the ? button to hide this help.",
        )
        const val KEY_ESCAPE = 256
        const val LOCK_SIZE = 12
        const val CHIP_W = 22
        const val TOOLBAR_Y = 6
        const val TOOLBAR_H = 18
        const val HANDLE_SIZE = 8
        const val TEXT_H = 9
        const val MIN_BAR_W = 40
        const val MIN_BOX_W = 40
        const val MIN_BOX_H = 12
        const val COMPACT_H = LOCK_SIZE + HANDLE_SIZE + 6
        const val MIN_LABEL_W = 8
        const val GRID_MINOR = 0x2E6B5D48.toInt()
        const val GRID_CENTER = 0x80C9A227.toInt()
        const val GUIDE_LINE = 0xA0FFD54F.toInt()
    }
}

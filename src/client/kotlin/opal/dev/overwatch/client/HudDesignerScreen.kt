package opal.dev.overwatch.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
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

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
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
            val label = if (!spec.barStretch && scalePct != 100) {
                "${spec.displayName}  ${w}x$h  $scalePct%"
            } else {
                "${spec.displayName}  ${w}x$h"
            }
            val labelX0 = if (compact) lockX + LOCK_SIZE + 2 else x0 + 3
            val labelX1 = if (compact && (y1 - y0) < TEXT_H + 2 + HANDLE_SIZE) x1 - HANDLE_SIZE - 2 else if (compact) x1 - 1 else lockX - 1
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

        val hint = "Drag to move (magnetic snap, hold Alt to drag free) -- corner handle resizes the box with the same snapping (Alt frees it; Alt+click the handle scales) -- click a lock to pin it (right-click toggles too) -- Esc to close"
        graphics.centeredText(font, hint, width / 2, height - 22, OwTheme.TEXT_DIM)
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

    override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
        val spec = hitTest(event.x().toInt(), event.y().toInt()) ?: return super.mouseClicked(event, doubled)
        val locked = HudLayoutManager.isLocked(spec.id)

        if (inLockGlyph(spec, event.x().toInt(), event.y().toInt())) {
            HudLayoutManager.setLocked(spec.id, !locked)
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

    private fun inLockGlyph(spec: HudLayoutManager.HudElementSpec, mx: Int, my: Int): Boolean {
        val b = boxOf(spec)
        val lockX = if ((b[3] - b[1]) < COMPACT_H) b[0] + 2 else b[2] - LOCK_SIZE - 2
        val lockY = b[1] + 2
        return mx >= lockX && mx < lockX + LOCK_SIZE && my >= lockY && my < lockY + LOCK_SIZE
    }

    private companion object {
        const val LOCK_SIZE = 12
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

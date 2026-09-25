package opal.dev.overwatch.client

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.abs

object HudLayoutManager {
    data class HudElementSpec(
        val id: String,
        val displayName: String,
        val defaultCorner: String = "TOP_LEFT",
        val defaultOffsetX: Int = 4,
        val defaultOffsetY: Int = 4,
        val fallbackW: Int = 80,
        val fallbackH: Int = 16,
        val barStretch: Boolean = false,
        val hidden: Boolean = false,
    )

    private val specs = LinkedHashMap<String, HudElementSpec>()
    private val stableSizes = HashMap<String, Pair<Int, Int>>()

    fun register(spec: HudElementSpec) {
        specs[spec.id] = spec
    }

    fun specs(): List<HudElementSpec> = specs.values.filter { !it.hidden }

    private fun layoutFor(id: String): OverwatchConfig.HudElementLayout {
        val config = OverwatchConfig.current
        return config.hudLayouts.getOrPut(id) {
            val spec = specs[id]
            OverwatchConfig.HudElementLayout(
                corner = spec?.defaultCorner ?: "TOP_LEFT",
                offsetX = spec?.defaultOffsetX ?: 4,
                offsetY = spec?.defaultOffsetY ?: 4,
            )
        }
    }

    fun corner(id: String): String = layoutFor(id).corner

    fun isLocked(id: String): Boolean = layoutFor(id).locked

    fun setLocked(id: String, locked: Boolean) {
        layoutFor(id).locked = locked
        OverwatchConfig.current.save()
    }

    fun scale(id: String): Float = layoutFor(id).scale.toFloat().coerceIn(0.5f, 2.5f)

    fun boxSize(id: String): Pair<Int, Int> {
        val layout = layoutFor(id)
        val spec = specs[id]
        val w = (if (layout.barWidth > 0) layout.barWidth else spec?.fallbackW ?: 80).coerceAtLeast(MIN_BOX_W)
        val h = (if (layout.boxH > 0) layout.boxH else spec?.fallbackH ?: 16).coerceAtLeast(MIN_BOX_H)
        return w to h
    }

    fun stableSize(id: String, contentW: Int, contentH: Int): Pair<Int, Int> {
        val (floorW, floorH) = boxSize(id)
        val w = maxOf(floorW, contentW)
        val h = maxOf(floorH, contentH)
        val prev = stableSizes[id]
        if (prev == null) {
            stableSizes[id] = w to h
            return w to h
        }
        if (w > prev.first || h > prev.second) {
            val grown = maxOf(prev.first, w) to maxOf(prev.second, h)
            stableSizes[id] = grown
            return grown
        }
        return prev
    }

    fun peekSize(id: String): Pair<Int, Int> {
        val (floorW, floorH) = boxSize(id)
        val mem = stableSizes[id] ?: return floorW to floorH
        return maxOf(floorW, mem.first) to maxOf(floorH, mem.second)
    }

    fun setBoxSize(id: String, width: Int, height: Int) {
        layoutFor(id).barWidth = width.coerceAtLeast(0)
        layoutFor(id).boxH = height.coerceAtLeast(0)
        stableSizes.remove(id)
    }

    fun forgetSize(id: String) {
        stableSizes.remove(id)
    }

    fun clearSizes() {
        stableSizes.clear()
    }

    fun setScale(id: String, scale: Float) {
        layoutFor(id).scale = scale.coerceIn(0.5f, 2.5f).toDouble()
    }

    fun resolve(id: String, guiW: Int, guiH: Int): Pair<Int, Int> {
        val scale = scale(id)
        val (boxW, boxH) = peekSize(id)
        return resolveForSize(id, guiW, guiH, (boxW * scale).toInt(), (boxH * scale).toInt())
    }

    fun resolveFlat(id: String, guiW: Int, guiH: Int): Pair<Int, Int> {
        val (boxW, boxH) = peekSize(id)
        return resolveForSize(id, guiW, guiH, boxW, boxH)
    }

    fun resolveForSize(id: String, guiW: Int, guiH: Int, w: Int, h: Int): Pair<Int, Int> {
        val layout = layoutFor(id)
        val right = layout.corner.endsWith("RIGHT")
        val bottom = layout.corner.startsWith("BOTTOM")
        val x = if (right) guiW - layout.offsetX - w else layout.offsetX
        val y = if (bottom) guiH - layout.offsetY - h else layout.offsetY
        return x to y
    }

    fun bounds(id: String, guiW: Int, guiH: Int): IntArray {
        val (x, y) = resolve(id, guiW, guiH)
        val scale = scale(id)
        val (boxW, boxH) = peekSize(id)
        return intArrayOf(x, y, x + (boxW * scale).toInt(), y + (boxH * scale).toInt())
    }

    fun applyDrag(id: String, dx: Int, dy: Int, guiW: Int, guiH: Int) {
        val layout = layoutFor(id)
        if (layout.locked) return
        val (x, y) = resolve(id, guiW, guiH)
        val scale = scale(id)
        val (boxW, boxH) = peekSize(id)
        val w = (boxW * scale).toInt()
        val h = (boxH * scale).toInt()
        val nx = (x + dx).coerceIn(0, (guiW - w).coerceAtLeast(0))
        val ny = (y + dy).coerceIn(0, (guiH - h).coerceAtLeast(0))
        place(id, nx, ny, w, h, guiW, guiH)
    }

    data class SnapMove(val x: Int, val y: Int, val guidesX: List<Int>, val guidesY: List<Int>)

    fun snapMove(id: String, nx: Int, ny: Int, guiW: Int, guiH: Int): SnapMove {
        val scale = scale(id)
        val (boxW, boxH) = peekSize(id)
        val w = (boxW * scale).toInt()
        val h = (boxH * scale).toInt()
        val tx = nx.coerceIn(0, (guiW - w).coerceAtLeast(0))
        val ty = ny.coerceIn(0, (guiH - h).coerceAtLeast(0))
        val others = specs()
            .filter { it.id != id }
            .map { spec -> bounds(spec.id, guiW, guiH) }
        val xEdges = ArrayList<Int>(4 + others.size * 2)
        val yEdges = ArrayList<Int>(4 + others.size * 2)
        val xCenters = ArrayList<Int>(2 + others.size)
        val yCenters = ArrayList<Int>(2 + others.size)
        xEdges.add(0)
        xEdges.add(guiW)
        yEdges.add(0)
        yEdges.add(guiH)
        xCenters.add(guiW / 2)
        yCenters.add(guiH / 2)
        for (b in others) {
            xEdges.add(b[0])
            xEdges.add(b[2])
            yEdges.add(b[1])
            yEdges.add(b[3])
            xCenters.add((b[0] + b[2]) / 2)
            yCenters.add((b[1] + b[3]) / 2)
        }
        val (sx, gx) = snapAxis(tx, w, xEdges, xCenters)
        val (sy, gy) = snapAxis(ty, h, yEdges, yCenters)
        moveTo(id, sx, sy, guiW, guiH)
        return SnapMove(sx, sy, listOfNotNull(gx), listOfNotNull(gy))
    }

    class SnapEdge(val value: Int, val guide: Int?)

    fun snapEdge(id: String, horizontal: Boolean, raw: Int, guiW: Int, guiH: Int): SnapEdge {
        val edges = ArrayList<Int>()
        val centers = ArrayList<Int>()
        if (horizontal) {
            edges.add(0)
            edges.add(guiW)
            centers.add(guiW / 2)
        } else {
            edges.add(0)
            edges.add(guiH)
            centers.add(guiH / 2)
        }
        for (spec in specs()) {
            if (spec.id == id) continue
            val b = bounds(spec.id, guiW, guiH)
            if (horizontal) {
                edges.add(b[0])
                edges.add(b[2])
                centers.add((b[0] + b[2]) / 2)
            } else {
                edges.add(b[1])
                edges.add(b[3])
                centers.add((b[1] + b[3]) / 2)
            }
        }
        var best = raw
        var line: Int? = null
        var bestDelta = Int.MAX_VALUE
        fun consider(target: Int, cap: Int) {
            val delta = abs(target - raw)
            if (delta <= cap && delta < bestDelta) {
                bestDelta = delta
                best = target
                line = target
            }
        }
        for (center in centers) consider(center, CENTER_SNAP_RADIUS)
        for (edge in edges) consider(edge, EDGE_SNAP_RADIUS)
        val nearest = Math.round(raw / SNAP_GRID.toFloat()) * SNAP_GRID
        consider(nearest, GRID_SNAP_RADIUS)
        return SnapEdge(best, line)
    }

    fun moveTo(id: String, nx: Int, ny: Int, guiW: Int, guiH: Int) {
        if (layoutFor(id).locked) return
        val scale = scale(id)
        val (boxW, boxH) = peekSize(id)
        val w = (boxW * scale).toInt()
        val h = (boxH * scale).toInt()
        place(
            id,
            nx.coerceIn(0, (guiW - w).coerceAtLeast(0)),
            ny.coerceIn(0, (guiH - h).coerceAtLeast(0)),
            w, h, guiW, guiH,
        )
    }

    private fun snapAxis(pos: Int, size: Int, edgeTargets: List<Int>, centerTargets: List<Int>): Pair<Int, Int?> {
        var best = pos
        var bestLine: Int? = null
        var bestDelta = Int.MAX_VALUE
        fun consider(newPos: Int, line: Int, cap: Int) {
            val delta = abs(newPos - pos)
            if (delta <= cap && delta < bestDelta) {
                bestDelta = delta
                best = newPos
                bestLine = line
            }
        }
        for (center in centerTargets) consider(center - size / 2, center, CENTER_SNAP_RADIUS)
        for (anchor in intArrayOf(0, size)) {
            for (target in edgeTargets) consider(pos + (target - (pos + anchor)), target, EDGE_SNAP_RADIUS)
        }
        val nearest = Math.round(pos / SNAP_GRID.toFloat()) * SNAP_GRID
        consider(nearest - SNAP_GRID, nearest - SNAP_GRID, GRID_SNAP_RADIUS)
        consider(nearest, nearest, GRID_SNAP_RADIUS)
        consider(nearest + SNAP_GRID, nearest + SNAP_GRID, GRID_SNAP_RADIUS)
        return best to bestLine
    }

    private fun place(id: String, nx: Int, ny: Int, w: Int, h: Int, guiW: Int, guiH: Int) {
        val layout = layoutFor(id)
        val cx = nx + w / 2f
        val cy = ny + h / 2f
        val right = cx > guiW / 2f
        val bottom = cy > guiH / 2f
        layout.corner = (if (bottom) "BOTTOM_" else "TOP_") + (if (right) "RIGHT" else "LEFT")
        layout.offsetX = if (right) guiW - nx - w else nx
        layout.offsetY = if (bottom) guiH - ny - h else ny
    }

    fun persist() = OverwatchConfig.current.save()

    const val MIN_BOX_W = 20
    const val MIN_BOX_H = 8

    const val SNAP_GRID = 20

    const val CENTER_SNAP_RADIUS = 5

    const val EDGE_SNAP_RADIUS = 6

    const val GRID_SNAP_RADIUS = 3

    fun drawLockGlyph(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, locked: Boolean, hovered: Boolean) {
        val sprite = if (locked) {
            if (hovered) OwTheme.CHECKBOX_SELECTED_HOVER_SPRITE else OwTheme.CHECKBOX_SELECTED_SPRITE
        } else {
            if (hovered) OwTheme.CHECKBOX_HOVER_SPRITE else OwTheme.CHECKBOX_SPRITE
        }
        val tint = if (locked) OwTheme.ACCENT else OwTheme.SPRITE_TINT_DISABLED
        OwTheme.drawTintedSprite(graphics, sprite, x, y, w, h, tint)
    }
}

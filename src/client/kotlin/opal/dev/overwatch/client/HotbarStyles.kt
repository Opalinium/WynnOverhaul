package opal.dev.overwatch.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object HotbarStyles {
    const val CLASSIC = "CLASSIC"
    const val GLASS = "GLASS"
    const val TILES = "TILES"
    const val ARC = "ARC"
    const val RADIAL = "RADIAL"
    const val CROSS = "CROSS"

    val STYLES = listOf(CLASSIC, GLASS, TILES, ARC, RADIAL, CROSS)

    fun label(style: String): String = when (style) {
        GLASS -> "Glass strip"
        TILES -> "Floating tiles"
        ARC -> "Arc"
        RADIAL -> "Radial wheel"
        CROSS -> "Elden cross"
        else -> "Classic"
    }

    class Frame(
        val player: Player,
        val font: Font,
        val slots: List<ItemStack>,
        val selected: Int,
        val offhand: ItemStack,
        val offhandLeft: Boolean,
    )

    private const val CELL = 20
    private const val TILE_GAP = 3
    private const val OFF_GAP = 6
    private const val RADIAL_SLOT = 18
    private const val RADIAL_PITCH = 25f
    private const val RADIAL_NAME_H = 12
    private const val CROSS_W = 104
    private const val CROSS_H = 88

    private val rarityCache = IdentityHashMap<ItemStack, Int>()
    private var radialPos = 0f
    private var radialNanos = 0L

    fun size(style: String, count: Int, hasOffhand: Boolean): Pair<Int, Int> {
        val n = count.coerceAtLeast(1)
        val off = if (hasOffhand) CELL + OFF_GAP else 0
        return when (style) {
            GLASS -> (n * CELL + 8 + off) to 30
            TILES -> (n * (CELL + TILE_GAP) - TILE_GAP + off) to 27
            ARC -> (n * (CELL + 2) + 6 + off) to 38
            RADIAL -> {
                val d = radialDiameter(n)
                (d + 12 + (if (hasOffhand) 24 else 0)) to (d + RADIAL_NAME_H + 8)
            }
            CROSS -> (CROSS_W + off) to CROSS_H
            else -> (n * 20 + 2 + off) to 22
        }
    }

    fun draw(g: GuiGraphicsExtractor, style: String, f: Frame, w: Int, h: Int) {
        when (style) {
            GLASS -> drawGlass(g, f, w, h)
            TILES -> drawTiles(g, f)
            ARC -> drawArc(g, f, w)
            RADIAL -> drawRadial(g, f, w, h)
            CROSS -> drawCross(g, f, w, h)
        }
    }

    private fun rarityColor(stack: ItemStack): Int {
        if (stack.isEmpty) return 0
        rarityCache[stack]?.let { return it }
        val rgb = WynnItemRarity.of(stack)?.colorRgb
        val color = if (rgb != null) 0xFF000000.toInt() or rgb else 0
        if (rarityCache.size > 256) rarityCache.clear()
        rarityCache[stack] = color
        return color
    }

    private fun item(g: GuiGraphicsExtractor, f: Frame, stack: ItemStack, x: Int, y: Int, seed: Int, scale: Float = 1f) {
        if (stack.isEmpty) return
        if (scale == 1f) {
            g.item(f.player, stack, x, y, seed)
            g.itemDecorations(f.font, stack, x, y)
            return
        }
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale)
        g.item(f.player, stack, 0, 0, seed)
        g.itemDecorations(f.font, stack, 0, 0)
        pose.popMatrix()
    }

    private fun tinyNumber(g: GuiGraphicsExtractor, f: Frame, n: Int, x: Int, y: Int, color: Int) {
        val pose = g.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(0.6f)
        g.text(f.font, n.toString(), 0, 0, color, true)
        pose.popMatrix()
    }

    private fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        g.fill(x, y, x + w, y + 1, color)
        g.fill(x, y + h - 1, x + w, y + h, color)
        g.fill(x, y, x + 1, y + h, color)
        g.fill(x + w - 1, y, x + w, y + h, color)
    }

    private fun halfWidth(r: Int, dy: Int): Int {
        val a = abs(dy)
        return if (a > r) 0 else sqrt((r * r - a * a).toFloat()).roundToInt()
    }

    private fun disc(g: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        if (r < 0 || (color ushr 24) == 0) return
        var start = -r
        var hw = halfWidth(r, -r)
        for (dy in -r + 1..r + 1) {
            val next = if (dy > r) -1 else halfWidth(r, dy)
            if (next != hw) {
                g.fill(cx - hw, cy + start, cx + hw + 1, cy + dy, color)
                start = dy
                hw = next
            }
        }
    }

    private fun ring(g: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, color: Int) {
        if (r < 0 || (color ushr 24) == 0) return
        var start = -r
        var hi = halfWidth(r, -r)
        var lo = halfWidth(r, -r + 1).coerceAtMost(hi)
        for (dy in -r + 1..r + 1) {
            var nhi = -1
            var nlo = -1
            if (dy <= r) {
                nhi = halfWidth(r, dy)
                nlo = if (abs(dy) >= r) 0 else halfWidth(r, abs(dy) + 1).coerceAtMost(nhi)
            }
            if (nhi != hi || nlo != lo) {
                if (lo == 0) {
                    g.fill(cx - hi, cy + start, cx + hi + 1, cy + dy, color)
                } else {
                    g.fill(cx - hi, cy + start, cx - lo + 1, cy + dy, color)
                    g.fill(cx + lo, cy + start, cx + hi + 1, cy + dy, color)
                }
                start = dy
                hi = nhi
                lo = nlo
            }
        }
    }

    private fun offhandTile(g: GuiGraphicsExtractor, f: Frame, x: Int, y: Int, size: Int) {
        g.fill(x, y, x + size, y + size, HudStyle.TRACK)
        frame(g, x, y, size, size, HudStyle.alpha(OwTheme.ACCENT_DIM, 0.8f))
        item(g, f, f.offhand, x + (size - 16) / 2, y + (size - 16) / 2, 99)
    }

    private fun drawGlass(g: GuiGraphicsExtractor, f: Frame, w: Int, h: Int) {
        val offW = if (f.offhand.isEmpty) 0 else CELL + OFF_GAP
        val mainX = if (f.offhandLeft) offW else 0
        val mainW = w - offW
        HudStyle.plate(g, mainX, 2, mainW, h - 4, OwTheme.ACCENT)
        for (i in f.slots.indices) {
            val x = mainX + 4 + i * CELL
            val selected = i == f.selected
            if (selected) {
                g.fill(x, 4, x + CELL, h - 6, HudStyle.alpha(OwTheme.ACCENT, 0.16f))
                g.fill(x + 2, h - 8, x + CELL - 2, h - 6, OwTheme.ACCENT)
                HudStyle.brackets(g, x, 4, CELL, h - 10, OwTheme.ACCENT, 3)
            } else if (i > 0) {
                g.fill(x, 8, x + 1, h - 10, 0x14FFFFFF)
            }
            val stack = f.slots[i]
            item(g, f, stack, x + 2, 6 + if (selected) 0 else 1, i + 1)
            tinyNumber(g, f, i + 1, x + 2, 5, if (selected) OwTheme.ACCENT else OwTheme.TEXT_FAINT)
        }
        if (!f.offhand.isEmpty) {
            val ox = if (f.offhandLeft) 0 else mainW + OFF_GAP
            offhandTile(g, f, ox, 5, CELL)
        }
    }

    private fun drawTiles(g: GuiGraphicsExtractor, f: Frame) {
        val offW = if (f.offhand.isEmpty) 0 else CELL + OFF_GAP
        val mainX = if (f.offhandLeft) offW else 0
        for (i in f.slots.indices) {
            val selected = i == f.selected
            val x = mainX + i * (CELL + TILE_GAP)
            val y = if (selected) 0 else 3
            val stack = f.slots[i]
            val rarity = rarityColor(stack)
            val edge = when {
                selected -> OwTheme.ACCENT
                rarity != 0 -> HudStyle.alpha(rarity, 0.75f)
                else -> HudStyle.TRACK_EDGE
            }
            g.fill(x, y, x + CELL, y + CELL, if (selected) HudStyle.GLASS_DEEP else HudStyle.GLASS)
            g.fillGradient(x + 1, y + 1, x + CELL - 1, y + CELL / 2, 0x18FFFFFF, 0x00FFFFFF)
            frame(g, x, y, CELL, CELL, edge)
            if (selected) {
                HudStyle.brackets(g, x - 2, y - 2, CELL + 4, CELL + 4, OwTheme.ACCENT, 3)
                g.fill(x + 3, y + CELL + 2, x + CELL - 3, y + CELL + 4, OwTheme.ACCENT)
            }
            item(g, f, stack, x + 2, y + 2, i + 1)
            tinyNumber(g, f, i + 1, x + 2, y + 1, if (selected) OwTheme.ACCENT else OwTheme.TEXT_FAINT)
        }
        if (!f.offhand.isEmpty) {
            val ox = if (f.offhandLeft) 0 else mainX + f.slots.size * (CELL + TILE_GAP) - TILE_GAP + OFF_GAP
            offhandTile(g, f, ox, 3, CELL)
        }
    }

    private fun drawArc(g: GuiGraphicsExtractor, f: Frame, w: Int) {
        val offW = if (f.offhand.isEmpty) 0 else CELL + OFF_GAP
        val mainX = if (f.offhandLeft) offW else 0
        val n = f.slots.size
        val pitch = CELL + 2
        val mid = (n - 1) / 2f
        val sag = 9f
        for (i in f.slots.indices) {
            val u = if (n > 1) (i - mid) / (mid.coerceAtLeast(0.5f)) else 0f
            val selected = i == f.selected
            val x = mainX + 3 + i * pitch
            val y = (sag * u * u).roundToInt() + if (selected) 0 else 3
            val stack = f.slots[i]
            val rarity = rarityColor(stack)
            val edge = when {
                selected -> OwTheme.ACCENT
                rarity != 0 -> HudStyle.alpha(rarity, 0.7f)
                else -> HudStyle.TRACK_EDGE
            }
            val cx = x + CELL / 2
            val cy = y + CELL / 2 + 2
            disc(g, cx, cy, CELL / 2, HudStyle.GLASS_DEEP)
            ring(g, cx, cy, CELL / 2, edge)
            if (selected) {
                ring(g, cx, cy, CELL / 2 + 2, HudStyle.alpha(OwTheme.ACCENT, 0.6f))
                HudStyle.diamond(g, cx, y + CELL + 6, 2, OwTheme.ACCENT)
            }
            item(g, f, stack, cx - 8, cy - 8, i + 1)
        }
        if (!f.offhand.isEmpty) {
            val ox = if (f.offhandLeft) 0 else w - CELL
            offhandTile(g, f, ox, 8, CELL)
        }
    }

    private fun radialDiameter(count: Int): Int {
        val radius = (RADIAL_PITCH * count / (2f * PI.toFloat())).coerceAtLeast(30f)
        return (radius * 2f + RADIAL_SLOT).roundToInt()
    }

    private fun stepRadial(selected: Int, count: Int): Float {
        val now = System.nanoTime()
        val dt = if (radialNanos == 0L) 1f else ((now - radialNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        radialNanos = now
        var diff = (selected - radialPos) % count
        if (diff > count / 2f) diff -= count
        if (diff < -count / 2f) diff += count
        radialPos += diff * (1f - exp(-dt / 0.07f))
        if (abs(diff) < 0.002f) radialPos = selected.toFloat()
        radialPos = ((radialPos % count) + count) % count
        return radialPos
    }

    private fun drawRadial(g: GuiGraphicsExtractor, f: Frame, w: Int, h: Int) {
        val n = f.slots.size.coerceAtLeast(1)
        val offW = if (f.offhand.isEmpty) 0 else 24
        val mainX = if (f.offhandLeft) offW else 0
        val d = radialDiameter(n)
        val cx = mainX + (w - offW) / 2
        val cy = 6 + d / 2
        val radius = (d - RADIAL_SLOT) / 2
        val pos = stepRadial(f.selected.coerceIn(0, n - 1), n)

        disc(g, cx, cy, radius + RADIAL_SLOT / 2 + 3, HudStyle.alpha(HudStyle.GLASS, 0.55f))
        ring(g, cx, cy, radius, HudStyle.alpha(OwTheme.HAIRLINE, 0.9f))
        ring(g, cx, cy, radius + RADIAL_SLOT / 2 + 3, HudStyle.alpha(OwTheme.ACCENT_DIM, 0.7f))

        for (i in f.slots.indices) {
            if (i == f.selected && abs(pos - f.selected) < 0.35f) continue
            val angle = -PI / 2 + (i - pos) * 2.0 * PI / n
            val sx = cx + (cos(angle) * radius).roundToInt()
            val sy = cy + (sin(angle) * radius).roundToInt()
            val stack = f.slots[i]
            val rarity = rarityColor(stack)
            val edge = if (rarity != 0) HudStyle.alpha(rarity, 0.8f) else HudStyle.TRACK_EDGE
            disc(g, sx, sy, RADIAL_SLOT / 2, HudStyle.GLASS_DEEP)
            ring(g, sx, sy, RADIAL_SLOT / 2, edge)
            item(g, f, stack, sx - 6, sy - 6, i + 1, 0.75f)
        }

        val selectedStack = f.slots.getOrElse(f.selected) { ItemStack.EMPTY }
        val centerR = 15
        disc(g, cx, cy, centerR, HudStyle.GLASS_DEEP)
        ring(g, cx, cy, centerR, OwTheme.ACCENT)
        ring(g, cx, cy, centerR + 1, HudStyle.alpha(OwTheme.ACCENT, 0.35f))
        item(g, f, selectedStack, cx - 12, cy - 12, 77, 1.5f)

        HudStyle.diamond(g, cx, cy - radius - RADIAL_SLOT / 2 - 6, 3, OwTheme.ACCENT)

        if (!selectedStack.isEmpty) {
            val name = selectedStack.hoverName
            val nameW = f.font.width(name)
            g.text(f.font, name, cx - nameW / 2, d + 9, -1, true)
        }
        if (!f.offhand.isEmpty) {
            val ox = if (f.offhandLeft) 0 else w - 22
            offhandTile(g, f, ox, h - 26, 22)
        }
    }

    private fun drawCross(g: GuiGraphicsExtractor, f: Frame, w: Int, h: Int) {
        val n = f.slots.size.coerceAtLeast(1)
        val offW = if (f.offhand.isEmpty) 0 else CELL + OFF_GAP
        val mainX = if (f.offhandLeft) offW else 0
        val cx = mainX + CROSS_W / 2
        val cy = 40
        val sel = f.selected.coerceIn(0, n - 1)
        val prev = f.slots.getOrElse((sel - 1 + n) % n) { ItemStack.EMPTY }
        val next = f.slots.getOrElse((sel + 1) % n) { ItemStack.EMPTY }
        val current = f.slots.getOrElse(sel) { ItemStack.EMPTY }
        val far = f.slots.getOrElse((sel + 2) % n) { ItemStack.EMPTY }

        HudStyle.fadeRule(g, cx - 34, cy, 68, HudStyle.alpha(OwTheme.HAIRLINE, 0.8f), true)
        g.fill(cx, cy - 34, cx + 1, cy + 34, HudStyle.alpha(OwTheme.HAIRLINE, 0.5f))

        crossTile(g, f, prev, cx - 14 - 8 - CELL, cy - CELL / 2, CELL, false)
        crossTile(g, f, next, cx + 14 + 8, cy - CELL / 2, CELL, false)
        crossTile(g, f, far, cx - CELL / 2, cy + 14 + 8, CELL, false, 0.55f)
        val topStack = if (f.offhand.isEmpty) f.slots.getOrElse((sel - 2 + n) % n) { ItemStack.EMPTY } else f.offhand
        crossTile(g, f, topStack, cx - CELL / 2, cy - 14 - 8 - CELL, CELL, false, if (f.offhand.isEmpty) 0.55f else 1f)

        val cs = 28
        g.fill(cx - cs / 2, cy - cs / 2, cx + cs / 2, cy + cs / 2, HudStyle.GLASS_DEEP)
        frame(g, cx - cs / 2, cy - cs / 2, cs, cs, OwTheme.ACCENT)
        HudStyle.brackets(g, cx - cs / 2 - 2, cy - cs / 2 - 2, cs + 4, cs + 4, OwTheme.ACCENT, 4)
        item(g, f, current, cx - 12, cy - 12, 77, 1.5f)

        val pipY = h - 7
        val pipW = 5
        val totalW = n * pipW + (n - 1) * 2
        var px = cx - totalW / 2
        for (i in 0 until n) {
            g.fill(px, pipY, px + pipW, pipY + 2, if (i == sel) OwTheme.ACCENT else HudStyle.alpha(OwTheme.TEXT_FAINT, 0.7f))
            px += pipW + 2
        }
        if (!f.offhand.isEmpty) {
            val ox = if (f.offhandLeft) 0 else w - CELL
            offhandTile(g, f, ox, cy - CELL / 2, CELL)
        }
    }

    private fun crossTile(g: GuiGraphicsExtractor, f: Frame, stack: ItemStack, x: Int, y: Int, size: Int, selected: Boolean, dim: Float = 1f) {
        g.fill(x, y, x + size, y + size, HudStyle.alpha(HudStyle.GLASS, dim))
        val rarity = rarityColor(stack)
        val edge = when {
            selected -> OwTheme.ACCENT
            rarity != 0 -> HudStyle.alpha(rarity, 0.7f * dim)
            else -> HudStyle.alpha(HudStyle.TRACK_EDGE, dim)
        }
        frame(g, x, y, size, size, edge)
        item(g, f, stack, x + (size - 16) / 2, y + (size - 16) / 2, x + y, 1f)
    }
}

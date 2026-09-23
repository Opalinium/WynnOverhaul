package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import opal.dev.overwatch.Overwatch

class WynnMountPickupHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Mount pickup HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val pickup = WynnMountPickupTracker.last ?: return
        if (!WynnMountPickupTracker.pickupVisible(System.currentTimeMillis())) return
        if (pickup.words.isEmpty()) return

        val font = Minecraft.getInstance().font
        val title = pickup.title
        val detail = if (title == "Mount Training") {
            val stat = pickup.words.firstOrNull { !it.equals("level", ignoreCase = true) }
            if (stat != null) "+1 ${titlecase(stat)}" else titlecase(pickup.words.joinToString(" "))
        } else {
            titlecase(pickup.words.joinToString(" "))
        }
        val (boxW, boxH) = HudLayoutManager.stableSize(ID, maxOf(font.width(title), font.width(detail)) + PAD * 2, CONTENT_H)
        val (floorW, floorH) = HudLayoutManager.boxSize(ID)

        val scale = HudLayoutManager.scale(ID)
        val scaledFloorW = (floorW * scale).toInt()
        val scaledFloorH = (floorH * scale).toInt()
        val scaledBoxW = (boxW * scale).toInt()
        val scaledBoxH = (boxH * scale).toInt()
        val (anchorX, anchorY) = HudLayoutManager.resolveForSize(ID, graphics.guiWidth(), graphics.guiHeight(), scaledFloorW, scaledFloorH)
        val baseX = (anchorX + scaledFloorW / 2 - scaledBoxW / 2).coerceIn(0, (graphics.guiWidth() - scaledBoxW).coerceAtLeast(0))
        val baseY = (anchorY + scaledFloorH / 2 - scaledBoxH / 2).coerceIn(0, (graphics.guiHeight() - scaledBoxH).coerceAtLeast(0))

        val scaled = scale != 1f
        if (scaled) {
            graphics.pose().pushMatrix()
            graphics.pose().translate(baseX.toFloat(), baseY.toFloat())
            graphics.pose().scale(scale)
        }
        val ox = if (scaled) 0 else baseX
        val oy = if (scaled) 0 else baseY

        OwTheme.hudPanel(graphics, ox, oy, boxW, boxH)
        graphics.text(font, title, ox + (boxW - font.width(title)) / 2, oy + 2, OwTheme.ACCENT, true)
        graphics.text(font, detail, ox + (boxW - font.width(detail)) / 2, oy + 2 + TITLE_H, OwTheme.TEXT, true)

        if (scaled) graphics.pose().popMatrix()
    }

    private fun titlecase(text: String): String =
        text.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private companion object {
        const val ID = "mount_pickup"
        const val CONTENT_H = 24
        const val TITLE_H = 10
        const val PAD = 3
    }
}

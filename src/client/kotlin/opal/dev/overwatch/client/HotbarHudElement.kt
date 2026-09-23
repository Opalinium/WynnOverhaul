package opal.dev.overwatch.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import opal.dev.overwatch.Overwatch

class HotbarHudElement : HudElement {

    private var loggedError = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        try {
            render(graphics)
        } catch (t: Throwable) {
            if (!loggedError) {
                loggedError = true
                Overwatch.LOGGER.error("Hotbar HUD failed", t)
            }
        }
    }

    private fun render(graphics: GuiGraphicsExtractor) {
        if (!OverwatchGate.inGame || !OverwatchConfig.current.customHudEnabled) return
        val player = Minecraft.getInstance().player ?: return
        if (player.isSpectator) return
        val inventory = player.inventory
        val offhand = player.offhandItem
        val showOffhand = !offhand.isEmpty
        val offhandLeft = showOffhand && player.mainArm.opposite == HumanoidArm.LEFT

        val selected = inventory.getSelectedSlot()
        var lastKept = selected
        for (slot in 0 until HOTBAR_SLOTS) {
            val stack = inventory.getItem(slot)
            if (!stack.isEmpty && !isHidden(stack)) lastKept = maxOf(lastKept, slot)
        }
        val mainW = (lastKept + 1) * SLOT_W + 2
        val visibleSlots = lastKept + 1
        val contentW = mainW + if (showOffhand) OFF_W else 0
        val contentH = if (showOffhand) OFF_H else MAIN_H
        HudLayoutManager.stableSize(ID, contentW, contentH)

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

        val mainX = ox + if (showOffhand && offhandLeft) OFF_W else 0
        val mainY = oy + if (showOffhand) 1 else 0

        if (visibleSlots >= HOTBAR_SLOTS) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, mainX, mainY, MAIN_W, MAIN_H)
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, MAIN_W, MAIN_H, 0, 0, mainX, mainY, mainW - 1, MAIN_H)
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, MAIN_W, MAIN_H, MAIN_W - 1, 0, mainX + mainW - 1, mainY, 1, MAIN_H)
        }
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            HOTBAR_SELECTION_SPRITE,
            mainX - 1 + inventory.getSelectedSlot() * SLOT_W,
            mainY - 1,
            SELECT_W,
            SELECT_H,
        )

        val font = Minecraft.getInstance().font
        var seed = 1
        for (slot in 0 until visibleSlots) {
            val stack = inventory.getItem(slot)
            val x = mainX + SLOT_INSET + slot * SLOT_W
            val y = mainY + SLOT_INSET
            if (isHidden(stack)) continue
            graphics.item(player, stack, x, y, seed++)
            graphics.itemDecorations(font, stack, x, y)
        }

        if (showOffhand) {
            val hidden = isHidden(offhand)
            if (offhandLeft) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_LEFT_SPRITE, ox, oy, OFF_W, OFF_H)
                if (!hidden) {
                    graphics.item(player, offhand, mainX - OFF_ITEM_DX, mainY + SLOT_INSET, seed++)
                    graphics.itemDecorations(font, offhand, mainX - OFF_ITEM_DX, mainY + SLOT_INSET)
                }
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_OFFHAND_RIGHT_SPRITE, mainX + MAIN_W, oy, OFF_W, OFF_H)
                if (!hidden) {
                    graphics.item(player, offhand, mainX + MAIN_W + OFF_ITEM_RIGHT_DX, mainY + SLOT_INSET, seed++)
                    graphics.itemDecorations(font, offhand, mainX + MAIN_W + OFF_ITEM_RIGHT_DX, mainY + SLOT_INSET)
                }
            }
        }

        if (scaled) graphics.pose().popMatrix()
    }

    companion object {
        const val ID = "hotbar"
        const val MAIN_W = 182
        const val MAIN_H = 22
        const val OFF_W = 29
        const val OFF_H = 24
        const val SLOT_W = 20
        const val SLOT_INSET = 3
        const val SELECT_W = 24
        const val SELECT_H = 23
        const val OFF_ITEM_DX = 26
        const val OFF_ITEM_RIGHT_DX = 10
        const val HOTBAR_SLOTS = 9
        val HOTBAR_SPRITE: Identifier = Identifier.withDefaultNamespace("hud/hotbar")
        val HOTBAR_SELECTION_SPRITE: Identifier = Identifier.withDefaultNamespace("hud/hotbar_selection")
        val HOTBAR_OFFHAND_LEFT_SPRITE: Identifier = Identifier.withDefaultNamespace("hud/hotbar_offhand_left")
        val HOTBAR_OFFHAND_RIGHT_SPRITE: Identifier = Identifier.withDefaultNamespace("hud/hotbar_offhand_right")

        fun isHidden(stack: ItemStack): Boolean {
            if (stack.isEmpty) return false
            return CharacterInfo.isInfo(stack) ||
                ContentBookInterceptor.isContentBook(stack) ||
                WynnPouches.isIngredientPouch(stack)
        }
    }
}

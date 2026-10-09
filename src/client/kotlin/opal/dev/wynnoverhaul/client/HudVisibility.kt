package opal.dev.wynnoverhaul.client

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.minecraft.client.Minecraft

object HudVisibility {
    fun hidden(client: Minecraft): Boolean =
        WynnCutsceneTracker.hidesHud || client.debugOverlay.showDebugScreen() || client.options.keyPlayerList.isDown

    fun hideable(element: HudElement): HudElement = HudElement { graphics, deltaTracker ->
        if (!hidden(Minecraft.getInstance())) element.extractRenderState(graphics, deltaTracker)
    }
}

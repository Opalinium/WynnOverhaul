package opal.dev.overwatch.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import opal.dev.overwatch.client.OverwatchConfig;
import opal.dev.overwatch.client.OverwatchGate;
import opal.dev.overwatch.client.WynnActionBar;
import opal.dev.overwatch.client.WynnGuildBarTracker;
import opal.dev.overwatch.client.WynnRegionBarTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudSuppressionMixin {
    @Shadow
    private Component overlayMessageString;

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideActionBarOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!OverwatchGate.INSTANCE.isInGame()) {
            return;
        }
        if (!OverwatchConfig.Companion.getCurrent().getCustomHudEnabled()) {
            return;
        }
        if (overlayMessageString == null) {
            return;
        }
        String raw = overlayMessageString.getString();
        if (WynnGuildBarTracker.INSTANCE.ownsOverlayLine(raw) || WynnRegionBarTracker.INSTANCE.ownsOverlayLine(raw)) {
            ci.cancel();
            return;
        }
        if (WynnActionBar.INSTANCE.isComposite(overlayMessageString)) {
            Component leftover = WynnActionBar.INSTANCE.leftover(overlayMessageString);
            if (leftover == null) {
                ci.cancel();
            } else {
                overlayMessageString = leftover;
            }
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideVanillaHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!OverwatchGate.INSTANCE.isInGame()) {
            return;
        }
        if (!OverwatchConfig.Companion.getCurrent().getCustomHudEnabled()) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null && !player.isSpectator()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideVanillaHunger(GuiGraphicsExtractor graphics, Player player, int left, int top, CallbackInfo ci) {
        if (OverwatchGate.INSTANCE.isInGame() && OverwatchConfig.Companion.getCurrent().getCustomHudEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideScoreboardSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (OverwatchGate.INSTANCE.isInGame() && OverwatchConfig.Companion.getCurrent().getQuestLogHudEnabled()) {
            ci.cancel();
        }
    }
}
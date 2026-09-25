package opal.dev.overwatch.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import opal.dev.overwatch.client.OverwatchConfig;
import opal.dev.overwatch.client.OverwatchGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudEffectsMixin {
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideVanillaEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (OverwatchGate.INSTANCE.isInGame() && OverwatchConfig.Companion.getCurrent().getHideVanillaPotionHud()) {
            ci.cancel();
        }
    }
}

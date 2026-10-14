package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import opal.dev.wynnoverhaul.client.ProjectedReticle;
import opal.dev.wynnoverhaul.client.SoulsCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class SoulsCameraReticleMixin {
    @Redirect(
        method = "extractCrosshair",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z", ordinal = 0)
    )
    private boolean wynnoverhaul$soulsReticle(CameraType type) {
        return type.isFirstPerson() || SoulsCamera.showsReticle();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"))
    private void wynnoverhaul$projectReticleBegin(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        ProjectedReticle.begin(Minecraft.getInstance(), graphics);
    }

    @Inject(method = "extractCrosshair", at = @At("RETURN"))
    private void wynnoverhaul$projectReticleEnd(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        ProjectedReticle.end(graphics);
    }
}

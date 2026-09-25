package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Hud;
import opal.dev.wynnoverhaul.client.SoulsCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Hud.class)
public abstract class SoulsCameraReticleMixin {
    @Redirect(
        method = "extractCrosshair",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z", ordinal = 0)
    )
    private boolean wynnoverhaul$soulsReticle(CameraType type) {
        return type.isFirstPerson() || SoulsCamera.showsReticle();
    }
}

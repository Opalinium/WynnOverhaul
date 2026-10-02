package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import opal.dev.wynnoverhaul.client.WynnDamageTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class DamageLabelHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$hideDamageLabels(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (WynnDamageTracker.hidesLabel(entity)) {
            cir.setReturnValue(false);
        }
    }
}

package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import opal.dev.wynnoverhaul.client.LocomotionAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class LocomotionModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void wynnoverhaul$locomotion(HumanoidRenderState state, CallbackInfo ci) {
        LocomotionAnimations.apply((HumanoidModel<?>) (Object) this, state);
    }
}

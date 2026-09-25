package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import opal.dev.wynnoverhaul.client.WeaponAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class WeaponAnimationThirdPersonMixin {
    @Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$weaponAttackAnimation(HumanoidRenderState state, CallbackInfo ci) {
        if (WeaponAnimations.applyThirdPerson((HumanoidModel<?>) (Object) this, state)) {
            ci.cancel();
        }
    }
}

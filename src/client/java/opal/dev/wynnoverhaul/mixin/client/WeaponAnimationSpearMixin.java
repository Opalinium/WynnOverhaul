package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.world.entity.HumanoidArm;
import opal.dev.wynnoverhaul.client.WeaponAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpearAnimations.class)
public abstract class WeaponAnimationSpearMixin {
    @Inject(method = "firstPersonAttack", at = @At("HEAD"), cancellable = true)
    private static void wynnoverhaul$weaponSpearAttack(float swingProcess, PoseStack poseStack, int sign, HumanoidArm arm, CallbackInfo ci) {
        if (WeaponAnimations.applyFirstPerson(poseStack, arm, sign)) {
            ci.cancel();
        }
    }
}

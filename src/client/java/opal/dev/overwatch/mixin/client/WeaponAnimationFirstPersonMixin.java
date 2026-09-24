package opal.dev.overwatch.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import opal.dev.overwatch.client.WeaponAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class WeaponAnimationFirstPersonMixin {

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void overwatch$weaponSwingArm(float swingProcess, PoseStack poseStack, int sign, HumanoidArm arm, CallbackInfo ci) {
        if (WeaponAnimations.applyFirstPerson(poseStack, arm, sign)) {
            ci.cancel();
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void overwatch$weaponIdle(PoseStack poseStack, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
        WeaponAnimations.applyFirstPersonIdle(poseStack, arm, arm == HumanoidArm.RIGHT ? 1 : -1);
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float overwatch$suppressVanillaSwing(float swingProcess) {
        return WeaponAnimations.suppressVanilla() ? 0.0F : swingProcess;
    }
}

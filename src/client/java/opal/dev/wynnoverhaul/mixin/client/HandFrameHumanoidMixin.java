package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import opal.dev.wynnoverhaul.client.LimbBend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HandFrameHumanoidMixin {
    @Inject(
        method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("TAIL")
    )
    private void wynnoverhaul$handFrame(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        LimbBend.applyHandFrame(((net.minecraft.client.model.HumanoidModel<?>) (Object) this).getArm(arm), poseStack);
    }
}

package opal.dev.overwatch.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import opal.dev.overwatch.client.WeaponTrail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerOwnerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void overwatch$ownerStart(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        WeaponTrail.handOwner = state instanceof AvatarRenderState avatar ? avatar.id : -1;
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void overwatch$ownerEnd(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        WeaponTrail.handOwner = -1;
    }
}

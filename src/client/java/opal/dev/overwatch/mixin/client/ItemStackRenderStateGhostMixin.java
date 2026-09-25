package opal.dev.overwatch.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import opal.dev.overwatch.client.WeaponTrail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateGhostMixin {
    @Shadow
    ItemDisplayContext displayContext;

    @Inject(method = "submit", at = @At("HEAD"))
    private void overwatch$weaponGhosts(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outline, CallbackInfo ci) {
        WeaponTrail.onSubmit((ItemStackRenderState) (Object) this, this.displayContext, poseStack, collector, overlay);
    }
}

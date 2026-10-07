package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import opal.dev.wynnoverhaul.client.FirstPersonBody;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderDispatcher.class)
public abstract class FirstPersonBodyOffsetMixin {
    @ModifyVariable(method = "submit", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double wynnoverhaul$shiftSubmitX(double x, EntityRenderState state, CameraRenderState camera, double y, double z, double w, PoseStack stack, SubmitNodeCollector collector) {
        return x + FirstPersonBody.INSTANCE.submitShiftX(state);
    }

    @ModifyVariable(method = "submit", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double wynnoverhaul$shiftSubmitZ(double z, EntityRenderState state, CameraRenderState camera, double x, double y, double w, PoseStack stack, SubmitNodeCollector collector) {
        return z + FirstPersonBody.INSTANCE.submitShiftZ(state);
    }
}

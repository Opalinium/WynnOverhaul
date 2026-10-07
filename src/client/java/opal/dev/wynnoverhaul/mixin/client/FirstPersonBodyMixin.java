package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import opal.dev.wynnoverhaul.client.FirstPersonBody;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class FirstPersonBodyMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void wynnoverhaul$beginBodyExtraction(CallbackInfo ci) {
        FirstPersonBody.INSTANCE.beginExtraction();
    }

    @Redirect(method = "extractVisibleEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean wynnoverhaul$firstPersonBody(Camera camera) {
        if (FirstPersonBody.INSTANCE.renderSelf(camera)) {
            return true;
        }
        return camera.isDetached();
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void wynnoverhaul$markBodyState(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        FirstPersonBody.INSTANCE.noteExtracted(entity, cir.getReturnValue());
    }
}

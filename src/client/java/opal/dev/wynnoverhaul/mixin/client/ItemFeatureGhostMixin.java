package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import opal.dev.wynnoverhaul.client.WeaponTrail;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureGhostMixin extends RenderTypeFeatureRenderer<ItemFeatureRenderer.Submit> {
    @Shadow
    @Final
    private QuadInstance quadInstance;

    @Inject(method = "prepareSubmit", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$ghostSubmit(ItemFeatureRenderer.Submit submit, boolean foil, CallbackInfo ci) {
        int alpha = submit.lightCoords() >>> 24;
        if (alpha < 1 || alpha > WeaponTrail.GHOST_MAX_ALPHA) {
            return;
        }
        ci.cancel();
        this.quadInstance.setLightCoords(submit.lightCoords() & 0xFFFFFF);
        this.quadInstance.setOverlayCoords(submit.overlayCoords());
        this.quadInstance.setColor((alpha << 24) | WeaponTrail.TINT);
        for (BakedQuad quad : submit.quads()) {
            VertexConsumer consumer = this.getVertexBuilder(RenderTypes.itemTranslucent(quad.materialInfo().sprite().atlasLocation()));
            consumer.putBakedQuad(submit.pose(), quad, this.quadInstance);
        }
    }
}

package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import opal.dev.wynnoverhaul.client.LoadingScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    @Redirect(
        method = "extractRenderState",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
        )
    )
    private void wynnoverhaul$skipLogo(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier id, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int textureW, int textureH, int color) {
        if (!LoadingScene.INSTANCE.enabled()) {
            graphics.blit(pipeline, id, x, y, u, v, w, h, regionW, regionH, textureW, textureH, color);
        }
    }

    @Redirect(
        method = "extractRenderState",
        require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void wynnoverhaul$skipBrandFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (!LoadingScene.INSTANCE.enabled()) {
            graphics.fill(x0, y0, x1, y1, color);
        }
    }

    @ModifyArg(
        method = "extractRenderState",
        require = 0,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;setVector4fFromARGB32(Lorg/joml/Vector4f;I)Lorg/joml/Vector4f;"),
        index = 1
    )
    private int wynnoverhaul$clearColor(int color) {
        return LoadingScene.INSTANCE.enabled() ? LoadingScene.BACKGROUND : color;
    }

    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$drawScene(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
        if (LoadingScene.INSTANCE.enabled()) {
            LoadingScene.INSTANCE.drawOverlay(graphics, alpha, currentProgress);
            ci.cancel();
        }
    }
}

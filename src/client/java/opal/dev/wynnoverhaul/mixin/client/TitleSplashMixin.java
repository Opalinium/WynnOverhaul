package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import opal.dev.wynnoverhaul.client.MenuSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashRenderer.class)
public abstract class TitleSplashMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$hideSplash(GuiGraphicsExtractor graphics, int width, Font font, float alpha, CallbackInfo ci) {
        if (MenuSkin.hidesTitleBranding()) {
            ci.cancel();
        }
    }
}

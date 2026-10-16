package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import opal.dev.wynnoverhaul.client.MenuSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public abstract class TitleLogoMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$hideLogo(GuiGraphicsExtractor graphics, int width, float alpha, int height, CallbackInfo ci) {
        if (MenuSkin.hidesTitleBranding()) {
            ci.cancel();
        }
    }
}

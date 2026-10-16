package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class SkinMenuBackgroundMixin {
    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (!ScreenSkin.active()) {
            return;
        }
        ScreenSkin.drawMenuBackground(graphics, x, y, width, height);
        ci.cancel();
    }
}

package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class SkinListMixin {
    @Inject(method = "extractListBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenSkin.active()) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        ScreenSkin.drawListBackground(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight());
        ci.cancel();
    }

    @Inject(method = "extractListSeparators(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinListSeparators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (ScreenSkin.active()) {
            ci.cancel();
        }
    }
}

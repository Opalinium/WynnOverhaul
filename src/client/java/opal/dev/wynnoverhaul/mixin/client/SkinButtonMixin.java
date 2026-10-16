package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class SkinButtonMixin {
    @Inject(method = "extractDefaultSprite(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinSprite(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenSkin.active()) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        ScreenSkin.drawButton(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.isActive(), self.isHoveredOrFocused());
        ci.cancel();
    }
}

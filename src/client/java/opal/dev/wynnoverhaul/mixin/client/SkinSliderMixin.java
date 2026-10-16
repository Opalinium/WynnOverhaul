package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSliderButton.class)
public abstract class SkinSliderMixin {
    @Shadow
    protected double value;

    @Inject(method = "extractWidgetRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinSlider(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ScreenSkin.active()) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        ScreenSkin.drawSlider(graphics, self.getX(), self.getY(), self.getWidth(), self.getHeight(), this.value, self.getMessage(), self.isActive(), self.isHoveredOrFocused());
        ci.cancel();
    }
}

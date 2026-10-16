package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Checkbox.class)
public abstract class SkinCheckboxMixin {
    @Inject(method = "extractContents(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$skinCheckbox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ScreenSkin.active()) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        ScreenSkin.drawCheckbox(graphics, self.getX(), self.getY(), self.getMessage(), ((Checkbox) (Object) this).selected(), self.isActive(), self.isHoveredOrFocused());
        ci.cancel();
    }
}

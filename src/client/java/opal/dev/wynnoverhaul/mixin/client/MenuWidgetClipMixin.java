package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import opal.dev.wynnoverhaul.client.MenuSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class MenuWidgetClipMixin {
    @Unique
    private boolean wynnoverhaul$clipped;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void wynnoverhaul$beginClip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        wynnoverhaul$clipped = MenuSkin.clip(graphics, (AbstractWidget) (Object) this);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void wynnoverhaul$endClip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (wynnoverhaul$clipped) {
            graphics.disableScissor();
            wynnoverhaul$clipped = false;
        }
    }
}

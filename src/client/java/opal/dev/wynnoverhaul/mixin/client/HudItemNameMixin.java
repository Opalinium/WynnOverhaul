package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import opal.dev.wynnoverhaul.client.HotbarNameAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudItemNameMixin {
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void wynnoverhaul$anchorItemNameStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        HotbarNameAnchor.begin(graphics);
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void wynnoverhaul$anchorItemNameEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        HotbarNameAnchor.end(graphics);
    }
}

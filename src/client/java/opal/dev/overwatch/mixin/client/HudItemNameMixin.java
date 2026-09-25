package opal.dev.overwatch.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import opal.dev.overwatch.client.HotbarNameAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudItemNameMixin {
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"))
    private void overwatch$anchorItemNameStart(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        HotbarNameAnchor.begin(graphics);
    }

    @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
    private void overwatch$anchorItemNameEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        HotbarNameAnchor.end(graphics);
    }
}

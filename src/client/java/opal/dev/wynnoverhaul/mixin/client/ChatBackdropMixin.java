package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import opal.dev.wynnoverhaul.client.ChatHud;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public abstract class ChatBackdropMixin {
    @Shadow
    @Final
    private GuiGraphicsExtractor graphics;

    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$chatBackdrop(int x0, int y0, int x1, int y1, int color, CallbackInfo ci) {
        if (ChatHud.drawBackdrop(this.graphics, x0, y0, x1, y1, color)) {
            ci.cancel();
        }
    }
}

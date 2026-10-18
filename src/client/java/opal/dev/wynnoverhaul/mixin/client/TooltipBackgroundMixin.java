package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import opal.dev.wynnoverhaul.client.TooltipCard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipBackgroundMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void wynnoverhaul$card(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style, CallbackInfo ci) {
        if (TooltipCard.INSTANCE.draw(graphics, x, y, width, height)) {
            ci.cancel();
        }
    }
}

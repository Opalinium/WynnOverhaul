package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import opal.dev.wynnoverhaul.client.TooltipCard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipCardMixin {
    @Inject(method = "tooltip", at = @At("HEAD"))
    private void wynnoverhaul$beginCard(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        TooltipCard.INSTANCE.begin(components);
    }
}

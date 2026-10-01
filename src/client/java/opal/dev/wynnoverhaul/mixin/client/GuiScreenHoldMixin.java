package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import opal.dev.wynnoverhaul.client.ScreenHold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiScreenHoldMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$keepInventoryOpen(Screen screen, CallbackInfo ci) {
        if (ScreenHold.holdsClose(Minecraft.getInstance().gui.screen(), screen)) {
            ci.cancel();
        }
    }
}

package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import opal.dev.wynnoverhaul.client.ScreenHold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenHoldMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$blockHeldClicks(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ScreenHold.isHeld((AbstractContainerScreen<?>) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}

package opal.dev.overwatch.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import opal.dev.overwatch.client.OverwatchItemDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class ItemDebugCopyMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void overwatch$copyHoveredItem(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 2) {
            return;
        }
        Slot slot = hoveredSlot;
        if (slot == null) {
            return;
        }
        if (OverwatchItemDebug.INSTANCE.tryCopyToClipboard(slot.getItem())) {
            cir.setReturnValue(true);
        }
    }
}

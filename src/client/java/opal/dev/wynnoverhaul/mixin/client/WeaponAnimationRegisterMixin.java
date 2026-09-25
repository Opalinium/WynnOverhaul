package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import opal.dev.wynnoverhaul.client.WeaponAnimationRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class WeaponAnimationRegisterMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$registerHoveredWeapon(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Slot slot = hoveredSlot;
        if (slot == null) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (WeaponAnimationRegistry.INSTANCE.tryRegisterHovered(event, stack)) {
            cir.setReturnValue(true);
        }
    }
}

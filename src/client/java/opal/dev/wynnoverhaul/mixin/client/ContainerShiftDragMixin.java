package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import opal.dev.wynnoverhaul.client.WynnOverhaulConfig;
import opal.dev.wynnoverhaul.client.WynnPouches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerShiftDragMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final long RETRY_NANOS = 400_000_000L;

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

    @Shadow
    abstract Slot getHoveredSlot(double x, double y);

    @org.spongepowered.asm.mixin.Unique
    private boolean wynnoverhaul$shiftDrag;
    @org.spongepowered.asm.mixin.Unique
    private final Map<Slot, Long> wynnoverhaul$issued = new HashMap<>();
    @org.spongepowered.asm.mixin.Unique
    private Container wynnoverhaul$origin;
    @org.spongepowered.asm.mixin.Unique
    private Slot wynnoverhaul$lastSlot;
    @org.spongepowered.asm.mixin.Unique
    private double wynnoverhaul$lastX;
    @org.spongepowered.asm.mixin.Unique
    private double wynnoverhaul$lastY;

    @org.spongepowered.asm.mixin.Unique
    private static boolean wynnoverhaul$shiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }

    @org.spongepowered.asm.mixin.Unique
    private void wynnoverhaul$reset() {
        wynnoverhaul$shiftDrag = false;
        wynnoverhaul$issued.clear();
        wynnoverhaul$origin = null;
        wynnoverhaul$lastSlot = null;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void wynnoverhaul$shiftDragStart(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        wynnoverhaul$reset();
        if (!WynnOverhaulConfig.Companion.getCurrent().getShiftDragQuickMove()) {
            return;
        }
        if (event.button() != 0 || !wynnoverhaul$shiftHeld() || (Object) this instanceof CreativeModeInventoryScreen) {
            return;
        }
        Slot slot = getHoveredSlot(event.x(), event.y());
        wynnoverhaul$shiftDrag = true;
        wynnoverhaul$lastSlot = slot;
        if (slot != null) {
            wynnoverhaul$origin = slot.container;
            wynnoverhaul$issued.put(slot, System.nanoTime());
        }
        wynnoverhaul$lastX = event.x();
        wynnoverhaul$lastY = event.y();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$shiftDragMove(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (!wynnoverhaul$shiftDrag) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || event.button() != 0 || !wynnoverhaul$shiftHeld()) {
            wynnoverhaul$reset();
            return;
        }
        double distX = event.x() - wynnoverhaul$lastX;
        double distY = event.y() - wynnoverhaul$lastY;
        int steps = Math.max(1, (int) (Math.max(Math.abs(distX), Math.abs(distY)) / 2.0));
        long now = System.nanoTime();
        for (int i = 1; i <= steps; i++) {
            double px = wynnoverhaul$lastX + distX * i / steps;
            double py = wynnoverhaul$lastY + distY * i / steps;
            Slot slot = getHoveredSlot(px, py);
            if (slot == wynnoverhaul$lastSlot) {
                continue;
            }
            wynnoverhaul$lastSlot = slot;
            if (slot == null) {
                continue;
            }
            if (wynnoverhaul$origin == null) {
                wynnoverhaul$origin = slot.container;
            }
            if (slot.container != wynnoverhaul$origin) {
                continue;
            }
            if (!slot.hasItem() || !slot.isActive() || !slot.mayPickup(player)) {
                continue;
            }
            Long issuedAt = wynnoverhaul$issued.get(slot);
            if (issuedAt != null && now - issuedAt < RETRY_NANOS) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (WynnPouches.INSTANCE.isIngredientPouch(stack) || WynnPouches.INSTANCE.isSellConfirm(stack) || WynnPouches.INSTANCE.isConfirmMorph(stack)) {
                continue;
            }
            wynnoverhaul$issued.put(slot, now);
            slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
        }
        wynnoverhaul$lastX = event.x();
        wynnoverhaul$lastY = event.y();
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void wynnoverhaul$shiftDragEnd(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        wynnoverhaul$reset();
    }
}

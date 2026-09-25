package opal.dev.overwatch.mixin.client;

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
import opal.dev.overwatch.client.OverwatchConfig;
import opal.dev.overwatch.client.WynnPouches;
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
    private boolean overwatch$shiftDrag;
    @org.spongepowered.asm.mixin.Unique
    private final Map<Slot, Long> overwatch$issued = new HashMap<>();
    @org.spongepowered.asm.mixin.Unique
    private Container overwatch$origin;
    @org.spongepowered.asm.mixin.Unique
    private Slot overwatch$lastSlot;
    @org.spongepowered.asm.mixin.Unique
    private double overwatch$lastX;
    @org.spongepowered.asm.mixin.Unique
    private double overwatch$lastY;

    @org.spongepowered.asm.mixin.Unique
    private static boolean overwatch$shiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }

    @org.spongepowered.asm.mixin.Unique
    private void overwatch$reset() {
        overwatch$shiftDrag = false;
        overwatch$issued.clear();
        overwatch$origin = null;
        overwatch$lastSlot = null;
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    private void overwatch$shiftDragStart(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        overwatch$reset();
        if (!OverwatchConfig.Companion.getCurrent().getShiftDragQuickMove()) {
            return;
        }
        if (event.button() != 0 || !overwatch$shiftHeld() || (Object) this instanceof CreativeModeInventoryScreen) {
            return;
        }
        Slot slot = getHoveredSlot(event.x(), event.y());
        overwatch$shiftDrag = true;
        overwatch$lastSlot = slot;
        if (slot != null) {
            overwatch$origin = slot.container;
            overwatch$issued.put(slot, System.nanoTime());
        }
        overwatch$lastX = event.x();
        overwatch$lastY = event.y();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void overwatch$shiftDragMove(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (!overwatch$shiftDrag) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || event.button() != 0 || !overwatch$shiftHeld()) {
            overwatch$reset();
            return;
        }
        double distX = event.x() - overwatch$lastX;
        double distY = event.y() - overwatch$lastY;
        int steps = Math.max(1, (int) (Math.max(Math.abs(distX), Math.abs(distY)) / 2.0));
        long now = System.nanoTime();
        for (int i = 1; i <= steps; i++) {
            double px = overwatch$lastX + distX * i / steps;
            double py = overwatch$lastY + distY * i / steps;
            Slot slot = getHoveredSlot(px, py);
            if (slot == overwatch$lastSlot) {
                continue;
            }
            overwatch$lastSlot = slot;
            if (slot == null) {
                continue;
            }
            if (overwatch$origin == null) {
                overwatch$origin = slot.container;
            }
            if (slot.container != overwatch$origin) {
                continue;
            }
            if (!slot.hasItem() || !slot.isActive() || !slot.mayPickup(player)) {
                continue;
            }
            Long issuedAt = overwatch$issued.get(slot);
            if (issuedAt != null && now - issuedAt < RETRY_NANOS) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (WynnPouches.INSTANCE.isIngredientPouch(stack) || WynnPouches.INSTANCE.isSellConfirm(stack) || WynnPouches.INSTANCE.isConfirmMorph(stack)) {
                continue;
            }
            overwatch$issued.put(slot, now);
            slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
        }
        overwatch$lastX = event.x();
        overwatch$lastY = event.y();
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void overwatch$shiftDragEnd(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        overwatch$reset();
    }
}

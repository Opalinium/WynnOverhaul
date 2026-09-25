package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.world.entity.player.Player;
import opal.dev.wynnoverhaul.client.HotbarHudElement;
import opal.dev.wynnoverhaul.client.WynnOverhaulConfig;
import opal.dev.wynnoverhaul.client.WynnOverhaulGate;
import opal.dev.wynnoverhaul.client.WynnDialogueTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScrollWheelHandler.class)
public class ScrollWheelHandlerMixin {
    @Inject(method = "getNextScrollWheelSelection", at = @At("HEAD"), cancellable = true)
    private static void wynnoverhaul$hotbarScrollSelection(
            double scrollAmount,
            int currentSlot,
            int selectionSize,
            CallbackInfoReturnable<Integer> cir
    ) {
        WynnOverhaulConfig config = WynnOverhaulConfig.Companion.getCurrent();
        if (!WynnOverhaulGate.INSTANCE.isInGame()) {
            return;
        }

        if (WynnDialogueTracker.INSTANCE.isChoiceActive()) {
            int scrollDelta = (int) Math.signum(scrollAmount);
            if (scrollDelta != 0) {
                WynnDialogueTracker.INSTANCE.nudgeSelection(-scrollDelta);
            }
        }
        boolean clamp = config.getQolPreventHotbarOverscroll();
        boolean skipHidden = config.getCustomHudEnabled();
        if (!clamp && !skipHidden) {
            return;
        }
        int delta = (int) Math.signum(scrollAmount);
        if (delta == 0) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        int step = -delta;
        int next = currentSlot + step;
        if (clamp) {
            for (int guard = 0; guard < selectionSize; guard++) {
                if (next < 0 || next >= selectionSize) {
                    cir.setReturnValue(currentSlot);
                    return;
                }
                if (!isHiddenSlot(player, next)) {
                    cir.setReturnValue(next);
                    return;
                }
                next += step;
            }
            cir.setReturnValue(currentSlot);
            return;
        }
        for (int guard = 0; guard < selectionSize; guard++) {
            next = ((next % selectionSize) + selectionSize) % selectionSize;
            if (!isHiddenSlot(player, next)) {
                cir.setReturnValue(next);
                return;
            }
            next += step;
        }
        cir.setReturnValue(currentSlot);
    }

    private static boolean isHiddenSlot(Player player, int slot) {
        if (player == null || slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return false;
        }
        return HotbarHudElement.Companion.isHidden(player.getInventory().getItem(slot));
    }
}

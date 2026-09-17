package opal.dev.overwatch.mixin.client;

import net.minecraft.client.ScrollWheelHandler;
import opal.dev.overwatch.client.OverwatchConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScrollWheelHandler.class)
public class ScrollWheelHandlerMixin {

    @Inject(method = "getNextScrollWheelSelection", at = @At("HEAD"), cancellable = true)
    private static void overwatch$preventHotbarOverscroll(
            double scrollAmount,
            int currentSlot,
            int selectionSize,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!OverwatchConfig.Companion.getCurrent().getQolPreventHotbarOverscroll()) {
            return;
        }
        int delta = (int) Math.signum(scrollAmount);
        int next = currentSlot - delta;
        cir.setReturnValue(Math.max(0, Math.min(selectionSize - 1, next)));
    }
}

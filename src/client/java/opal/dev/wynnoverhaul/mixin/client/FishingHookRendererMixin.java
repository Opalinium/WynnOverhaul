package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import opal.dev.wynnoverhaul.client.SimulatedCast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Inject(method = "getHoldingArm", at = @At("RETURN"), cancellable = true)
    private static void wynnoverhaul$gatheringRodArm(Player player, CallbackInfoReturnable<HumanoidArm> cir) {
        HumanoidArm arm = SimulatedCast.INSTANCE.holdingArm(player);
        if (arm != null) {
            cir.setReturnValue(arm);
        }
    }
}

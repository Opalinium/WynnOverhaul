package opal.dev.overwatch.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import opal.dev.overwatch.client.FishingRodItems;
import opal.dev.overwatch.client.OverwatchConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftUseItemMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void overwatch$suppressNativeRepeatWhileAutoFishing(CallbackInfo ci) {
        if (!OverwatchConfig.Companion.getCurrent().getFishingEnabled()) {
            return;
        }
        Minecraft client = (Minecraft) (Object) this;
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        if (FishingRodItems.isHoldingRealFishingRod(player)) {
            ci.cancel();
        }
    }
}

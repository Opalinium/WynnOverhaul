package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import opal.dev.wynnoverhaul.client.ContentBookQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$muteDuringContentBookScan(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (ContentBookQuery.INSTANCE.isActive()) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}

package opal.dev.overwatch.mixin.client;

import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.components.BossHealthOverlay$1")
public abstract class BossHealthUpdateGuardMixin {
    @Shadow
    @Final
    BossHealthOverlay this$0;

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true)
    private void overwatch$guardUpdateName(UUID id, Component name, CallbackInfo ci) {
        if (!overwatch$hasBar(id)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateProgress", at = @At("HEAD"), cancellable = true)
    private void overwatch$guardUpdateProgress(UUID id, float progress, CallbackInfo ci) {
        if (!overwatch$hasBar(id)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateStyle", at = @At("HEAD"), cancellable = true)
    private void overwatch$guardUpdateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay, CallbackInfo ci) {
        if (!overwatch$hasBar(id)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateProperties", at = @At("HEAD"), cancellable = true)
    private void overwatch$guardUpdateProperties(UUID id, boolean darkenScreen, boolean playBossMusic, boolean createFog, CallbackInfo ci) {
        if (!overwatch$hasBar(id)) {
            ci.cancel();
        }
    }

    private boolean overwatch$hasBar(UUID id) {
        try {
            return ((BossHealthOverlayAccessor) this$0).overwatch$getEvents().containsKey(id);
        } catch (Throwable t) {
            return true;
        }
    }
}

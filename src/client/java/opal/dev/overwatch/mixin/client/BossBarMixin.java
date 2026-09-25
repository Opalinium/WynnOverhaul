package opal.dev.overwatch.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import opal.dev.overwatch.client.WynnGuildBarTracker;
import opal.dev.overwatch.client.WynnRegionBarTracker;
import opal.dev.overwatch.client.WynnResourceBarTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class BossBarMixin {
    @Inject(method = "handleBossUpdate", at = @At("HEAD"))
    private void overwatch$onBossUpdate(ClientboundBossEventPacket packet, CallbackInfo ci) {
        packet.dispatch(WynnResourceBarTracker.INSTANCE);
        packet.dispatch(WynnGuildBarTracker.INSTANCE);
        packet.dispatch(WynnRegionBarTracker.INSTANCE);
    }
}

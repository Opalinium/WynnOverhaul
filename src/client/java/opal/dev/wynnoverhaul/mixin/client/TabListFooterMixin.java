package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import opal.dev.wynnoverhaul.client.WynnStatusEffectTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TabListFooterMixin {
    @Inject(method = "handleTabListCustomisation", at = @At("HEAD"))
    private void wynnoverhaul$onTabListFooter(ClientboundTabListPacket packet, CallbackInfo ci) {
        WynnStatusEffectTracker.INSTANCE.onFooterUpdate(packet.footer());
    }
}

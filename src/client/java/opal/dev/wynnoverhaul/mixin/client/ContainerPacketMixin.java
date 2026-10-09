package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import opal.dev.wynnoverhaul.client.ContentBookQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ContainerPacketMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void wynnoverhaul$onSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            ContentBookQuery.INSTANCE.onContainerPacket(packet.getContainerId());
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void wynnoverhaul$onContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            ContentBookQuery.INSTANCE.onContainerPacket(packet.containerId());
        }
    }
}

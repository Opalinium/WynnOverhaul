package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import opal.dev.wynnoverhaul.client.SlotLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class SlotLockMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$lockedSlot(int containerId, int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (SlotLocks.blocks(containerId, slot, input, player)) ci.cancel();
    }
}

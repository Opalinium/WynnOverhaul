package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import opal.dev.wynnoverhaul.client.SoulsCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SoulsCameraInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void wynnoverhaul$soulsMovement(CallbackInfo ci) {
        SoulsCamera.Move move = SoulsCamera.remap(Minecraft.getInstance(), this.keyPresses, this.moveVector);
        if (move != null) {
            this.keyPresses = move.getPresses();
            this.moveVector = move.getVector();
        }
    }
}

package opal.dev.overwatch.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import opal.dev.overwatch.client.SoulsCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public abstract class SoulsCameraMouseMixin {
    @Redirect(
        method = "turnPlayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void overwatch$soulsTurn(LocalPlayer player, double dx, double dy) {
        if (!SoulsCamera.onMouseTurn(Minecraft.getInstance(), dx, dy)) {
            player.turn(dx, dy);
        }
    }
}

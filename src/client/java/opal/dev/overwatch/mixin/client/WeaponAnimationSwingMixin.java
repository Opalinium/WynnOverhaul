package opal.dev.overwatch.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import opal.dev.overwatch.client.WeaponAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class WeaponAnimationSwingMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("TAIL"))
    private void overwatch$weaponSwingStarted(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            WeaponAnimations.onSwing(player);
        }
    }
}

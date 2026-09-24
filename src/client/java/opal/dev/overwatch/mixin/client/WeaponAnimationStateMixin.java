package opal.dev.overwatch.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwingAnimationType;
import opal.dev.overwatch.client.WeaponAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
public abstract class WeaponAnimationStateMixin {

    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void overwatch$silenceVanillaSwing(
            LivingEntity entity,
            ArmedEntityRenderState state,
            ItemModelResolver resolver,
            float partialTick,
            CallbackInfo ci
    ) {
        if (entity instanceof LocalPlayer && WeaponAnimations.suppressVanilla()) {
            state.attackTime = 0.0F;
            state.swingAnimationType = SwingAnimationType.NONE;
        }
    }
}

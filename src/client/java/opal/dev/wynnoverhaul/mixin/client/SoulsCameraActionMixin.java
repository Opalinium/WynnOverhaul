package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import opal.dev.wynnoverhaul.client.SoulsCamera;
import opal.dev.wynnoverhaul.client.SpellComboGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class SoulsCameraActionMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void wynnoverhaul$soulsAttack(CallbackInfoReturnable<Boolean> cir) {
        SpellComboGuard.beforeClick((Minecraft) (Object) this, false);
        SoulsCamera.onCombatAction((Minecraft) (Object) this);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void wynnoverhaul$soulsUse(CallbackInfo ci) {
        SpellComboGuard.beforeClick((Minecraft) (Object) this, true);
        SoulsCamera.onCombatAction((Minecraft) (Object) this);
    }

    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void wynnoverhaul$soulsPickBegin(float partial, CallbackInfo ci) {
        SoulsCamera.beginPick((Minecraft) (Object) this, partial);
    }

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void wynnoverhaul$soulsPickEnd(float partial, CallbackInfo ci) {
        SoulsCamera.endPick((Minecraft) (Object) this);
    }
}

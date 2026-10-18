package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import opal.dev.wynnoverhaul.client.ScreenSkin;
import opal.dev.wynnoverhaul.client.UiSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class ButtonSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$click(SoundManager soundManager, CallbackInfo ci) {
        if (ScreenSkin.active() && UiSounds.INSTANCE.click()) {
            ci.cancel();
        }
    }
}

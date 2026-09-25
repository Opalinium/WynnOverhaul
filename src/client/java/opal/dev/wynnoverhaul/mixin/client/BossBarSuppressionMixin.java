package opal.dev.wynnoverhaul.mixin.client;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import opal.dev.wynnoverhaul.client.WynnOverhaulConfig;
import opal.dev.wynnoverhaul.client.WynnOverhaulGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarSuppressionMixin {
    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$hideServerBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!WynnOverhaulGate.INSTANCE.isInGame()
            || !WynnOverhaulConfig.Companion.getCurrent().getCustomHudEnabled()) {
            return;
        }
        try {
            if (events.isEmpty()) {
                return;
            }
            ci.cancel();
        } catch (Throwable t) {
        }
    }
}

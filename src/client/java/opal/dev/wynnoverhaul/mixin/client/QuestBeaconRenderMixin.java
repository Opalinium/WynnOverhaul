package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import opal.dev.wynnoverhaul.client.WynnOverhaulConfig;
import opal.dev.wynnoverhaul.client.QuestBeaconTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class QuestBeaconRenderMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$hideOverriddenQuestBeacon(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!WynnOverhaulConfig.Companion.getCurrent().getQuestWaypointOverrideEnabled()) {
            return;
        }
        if (QuestBeaconTracker.INSTANCE.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}

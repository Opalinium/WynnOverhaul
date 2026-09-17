package opal.dev.overwatch.mixin.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import opal.dev.overwatch.client.OverwatchConfig;
import opal.dev.overwatch.client.QuestBeaconTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class QuestBeaconRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void overwatch$hideOverriddenQuestBeacon(
            Entity entity,
            Frustum frustum,
            double camX,
            double camY,
            double camZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OverwatchConfig.Companion.getCurrent().getQuestWaypointOverrideEnabled()) {
            return;
        }
        if (QuestBeaconTracker.INSTANCE.shouldHide(entity)) {
            cir.setReturnValue(false);
        }
    }
}

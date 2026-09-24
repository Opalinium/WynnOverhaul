package opal.dev.overwatch.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import opal.dev.overwatch.client.SoulsCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class SoulsCameraMixin {

    @Shadow
    private Entity entity;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void move(float forward, float up, float left);

    @Shadow
    protected abstract float getMaxZoom(float distance);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void overwatch$soulsCamera(float partial, CallbackInfo ci) {
        SoulsCamera.Pose pose = SoulsCamera.pose(this.entity, this.eyeHeightOld, this.eyeHeight, partial);
        if (pose == null) {
            return;
        }
        this.setRotation(pose.getYaw(), pose.getPitch());
        this.setPosition(pose.getX(), pose.getY(), pose.getZ());
        this.move(-this.getMaxZoom(pose.getDistance()), 0.0F, 0.0F);
    }
}

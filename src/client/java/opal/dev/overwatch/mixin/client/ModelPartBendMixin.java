package opal.dev.overwatch.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import opal.dev.overwatch.client.BendHolder;
import opal.dev.overwatch.client.LimbBend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartBendMixin implements BendHolder {
    @Unique
    private float overwatch$bendAngle;

    @Unique
    private long overwatch$bendStamp;

    @Override
    public void setLimbBend(float angle, long stamp) {
        this.overwatch$bendAngle = angle;
        this.overwatch$bendStamp = stamp;
    }

    @Override
    public float getLimbBend() {
        return this.overwatch$bendAngle;
    }

    @Override
    public long getLimbBendStamp() {
        return this.overwatch$bendStamp;
    }

    @Inject(method = "compile", at = @At("HEAD"))
    private void overwatch$selectBend(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        LimbBend.select(this.overwatch$bendAngle, this.overwatch$bendStamp);
    }
}

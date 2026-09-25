package opal.dev.wynnoverhaul.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import opal.dev.wynnoverhaul.client.BendHolder;
import opal.dev.wynnoverhaul.client.LimbBend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartBendMixin implements BendHolder {
    @Unique
    private float wynnoverhaul$bendAngle;

    @Unique
    private long wynnoverhaul$bendStamp;

    @Override
    public void setLimbBend(float angle, long stamp) {
        this.wynnoverhaul$bendAngle = angle;
        this.wynnoverhaul$bendStamp = stamp;
    }

    @Override
    public float getLimbBend() {
        return this.wynnoverhaul$bendAngle;
    }

    @Override
    public long getLimbBendStamp() {
        return this.wynnoverhaul$bendStamp;
    }

    @Inject(method = "compile", at = @At("HEAD"))
    private void wynnoverhaul$selectBend(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        LimbBend.select(this.wynnoverhaul$bendAngle, this.wynnoverhaul$bendStamp);
    }
}

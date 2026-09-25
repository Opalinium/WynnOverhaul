package opal.dev.overwatch.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import opal.dev.overwatch.client.LimbBend;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ModelPart.Cube.class)
public abstract class CubeBendMixin {
    @Shadow
    @Final
    @Mutable
    public ModelPart.Polygon[] polygons;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void overwatch$subdivide(
        int u, int v, float x, float y, float z, float w, float h, float d,
        float growX, float growY, float growZ, boolean mirror, float texWidth, float texHeight,
        Set<Direction> visibleFaces, CallbackInfo ci
    ) {
        if (h == 12.0F && d == 4.0F && (w == 4.0F || w == 3.0F)) {
            this.polygons = LimbBend.subdivide(this.polygons);
        }
    }

    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void overwatch$bentCompile(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color, CallbackInfo ci) {
        if (LimbBend.compile((ModelPart.Cube) (Object) this, pose, buffer, light, overlay, color)) {
            ci.cancel();
        }
    }
}

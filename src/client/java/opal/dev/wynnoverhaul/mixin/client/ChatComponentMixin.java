package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import opal.dev.wynnoverhaul.client.ChatHud;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    protected abstract double getScale();

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$chatWidth(CallbackInfoReturnable<Integer> cir) {
        if (ChatHud.active()) {
            cir.setReturnValue(ChatHud.width(getScale()));
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$chatHeight(CallbackInfoReturnable<Integer> cir) {
        if (ChatHud.active()) {
            cir.setReturnValue(ChatHud.height(getScale()));
        }
    }

    @ModifyArg(
        method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V"),
        index = 0
    )
    private Consumer<Matrix3x2f> wynnoverhaul$chatPose(Consumer<Matrix3x2f> original) {
        if (!ChatHud.active()) {
            return original;
        }
        float dx = ChatHud.offsetX();
        float dy = ChatHud.offsetY();
        return matrix -> {
            matrix.translate(dx, dy);
            original.accept(matrix);
        };
    }
}

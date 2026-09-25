package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import opal.dev.wynnoverhaul.client.OwCenteredTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class EditBoxTextCenterMixin {
    @Shadow
    @Mutable
    private int textX;

    @Shadow
    @Mutable
    private int textY;

    @Inject(method = "updateTextPosition", at = @At("TAIL"))
    private void wynnoverhaul$recenterText(CallbackInfo ci) {
        if (!(this instanceof OwCenteredTextField)) return;
        EditBox self = (EditBox) (Object) this;
        this.textX = self.getX() + 4;
        this.textY = self.getY() + (self.getHeight() - 8) / 2;
    }
}

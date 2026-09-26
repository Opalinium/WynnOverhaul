package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import opal.dev.wynnoverhaul.client.EquipCompareTooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class ContainerEquipCompareMixin {
    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractTooltip", at = @At("TAIL"))
    private void wynnoverhaul$drawEquipCompare(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        Slot slot = hoveredSlot;
        if (slot == null || !slot.hasItem()) {
            return;
        }
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (!self.getMenu().getCarried().isEmpty()) {
            return;
        }
        ItemStack stack = slot.getItem();
        EquipCompareTooltip.INSTANCE.draw(graphics, Minecraft.getInstance().font, stack, mouseX, mouseY);
    }
}

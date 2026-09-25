package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import opal.dev.wynnoverhaul.client.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatScreen.class)
public abstract class ChatInputBarMixin extends Screen {
    protected ChatInputBarMixin(Component title) {
        super(title);
    }

    @Redirect(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void wynnoverhaul$inputBar(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        if (ChatHud.active()) {
            ChatHud.drawInputBar(graphics, this.height, color);
        } else {
            graphics.fill(x0, y0, x1, y1, color);
        }
    }
}

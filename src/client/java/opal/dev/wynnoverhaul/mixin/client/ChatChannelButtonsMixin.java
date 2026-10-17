package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import opal.dev.wynnoverhaul.client.ChatDirectPanel;
import opal.dev.wynnoverhaul.client.ChatHud;
import opal.dev.wynnoverhaul.client.ChatSysPanel;
import opal.dev.wynnoverhaul.client.ChatTabBar;
import opal.dev.wynnoverhaul.client.WynnOverhaulGate;
import opal.dev.wynnoverhaul.client.WynnChatChannels;
import opal.dev.wynnoverhaul.client.WynnDirectMessages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatChannelButtonsMixin extends Screen {
    protected ChatChannelButtonsMixin(Component title) {
        super(title);
    }

    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void wynnoverhaul$setupChat(CallbackInfo ci) {
        if (!WynnOverhaulGate.INSTANCE.isInGame()) {
            return;
        }
        if (ChatHud.active()) {
            this.input.setX(ChatHud.inputX());
            this.input.setY(ChatHud.inputY(this.height));
            this.input.setWidth(ChatHud.inputWidth());
        }
        WynnChatChannels.INSTANCE.applySmartReply();
        WynnDirectMessages.INSTANCE.applyView(true);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void wynnoverhaul$resetConversationView(CallbackInfo ci) {
        if (WynnOverhaulGate.INSTANCE.isInGame()) {
            WynnDirectMessages.INSTANCE.applyView(false);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wynnoverhaul$renderDirectPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (WynnOverhaulGate.INSTANCE.isInGame()) {
            ChatTabBar.INSTANCE.render(graphics, this.font, this.input, mouseX, mouseY, this.width, this.height);
            ChatSysPanel.INSTANCE.render(graphics, this.font, ChatTabBar.INSTANCE.top(this.input), this.width, this.height, mouseX, mouseY);
            ChatDirectPanel.INSTANCE.render(graphics, this.font, this.input, mouseX, mouseY);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$directPanelKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (WynnOverhaulGate.INSTANCE.isInGame() && ChatDirectPanel.INSTANCE.keyPressed(event.key(), this.input)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$directPanelClick(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!WynnOverhaulGate.INSTANCE.isInGame()) {
            return;
        }
        if (ChatDirectPanel.INSTANCE.mouseClicked(event.x(), event.y(), this.input)
            || ChatTabBar.INSTANCE.mouseClicked(event.x(), event.y())
            || ChatSysPanel.INSTANCE.mouseClicked(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void wynnoverhaul$sysPanelScroll(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (WynnOverhaulGate.INSTANCE.isInGame() && ChatSysPanel.INSTANCE.mouseScrolled(mouseX, mouseY, scrollY)) {
            cir.setReturnValue(true);
        }
    }
}

package opal.dev.wynnoverhaul.mixin.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import opal.dev.wynnoverhaul.client.ChatDirectPanel;
import opal.dev.wynnoverhaul.client.ChatHud;
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

    private final List<Button> wynnoverhaul$buttons = new ArrayList<>();
    private final List<Supplier<String>> wynnoverhaul$labels = new ArrayList<>();

    @Inject(method = "init", at = @At("TAIL"))
    private void wynnoverhaul$addChannelButtons(CallbackInfo ci) {
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

        wynnoverhaul$buttons.clear();
        wynnoverhaul$labels.clear();
        int buttonHeight = 16;
        int y = this.input.getY() - buttonHeight - 2;
        int x = this.input.getX();
        int limit = this.input.getX() + this.input.getWidth();

        for (WynnChatChannels.Channel channel : WynnChatChannels.Channel.values()) {
            Supplier<String> label = () -> wynnoverhaul$channelLabel(channel);
            x = wynnoverhaul$addButton(x, y, 54, buttonHeight, label, () -> {
                WynnChatChannels.INSTANCE.select(channel);
                WynnDirectMessages.INSTANCE.applyView(true);
            });
        }
        x = wynnoverhaul$addButton(x, y, 38, buttonHeight, () -> "@MSG", () -> {
            this.input.setValue("@");
            this.setFocused(this.input);
        });

        Set<String> shown = new HashSet<>();
        String target = WynnChatChannels.INSTANCE.getDirectTarget();
        List<String> names = new ArrayList<>();
        if (target != null) {
            names.add(target);
            shown.add(target.toLowerCase());
        }
        for (WynnDirectMessages.Conversation convo : WynnDirectMessages.INSTANCE.recent()) {
            if (shown.add(convo.getName().toLowerCase())) {
                names.add(convo.getName());
            }
            if (names.size() >= 4) {
                break;
            }
        }
        for (String name : names) {
            int width = this.font.width(name) + 26;
            if (x + width > limit) {
                break;
            }
            Supplier<String> label = () -> wynnoverhaul$chipLabel(name);
            x = wynnoverhaul$addButton(x, y, width, buttonHeight, label, () -> {
                WynnChatChannels.INSTANCE.selectDirect(name, false);
                wynnoverhaul$refreshLabels();
            });
        }
    }

    private int wynnoverhaul$addButton(int x, int y, int width, int height, Supplier<String> label, Runnable action) {
        Button button = Button.builder(Component.literal(label.get()), pressed -> {
                action.run();
                wynnoverhaul$refreshLabels();
            })
            .bounds(x, y, width, height)
            .build();
        wynnoverhaul$buttons.add(button);
        wynnoverhaul$labels.add(label);
        this.addRenderableWidget(button);
        return x + width + 4;
    }

    private void wynnoverhaul$refreshLabels() {
        for (int i = 0; i < wynnoverhaul$buttons.size(); i++) {
            wynnoverhaul$buttons.get(i).setMessage(Component.literal(wynnoverhaul$labels.get(i).get()));
        }
    }

    private static String wynnoverhaul$channelLabel(WynnChatChannels.Channel channel) {
        boolean direct = WynnChatChannels.INSTANCE.getDirectTarget() != null;
        boolean active = !direct && WynnChatChannels.INSTANCE.getCurrent() == channel;
        return (active ? "▶ " : "") + channel.getLabel();
    }

    private static String wynnoverhaul$chipLabel(String name) {
        String target = WynnChatChannels.INSTANCE.getDirectTarget();
        boolean active = target != null && target.equalsIgnoreCase(name);
        int unread = 0;
        for (WynnDirectMessages.Conversation convo : WynnDirectMessages.INSTANCE.recent()) {
            if (convo.getName().equalsIgnoreCase(name)) {
                unread = convo.getUnread();
            }
        }
        return (active ? "▶ " : "") + name + (unread > 0 ? " (" + unread + ")" : "");
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
        if (WynnOverhaulGate.INSTANCE.isInGame() && ChatDirectPanel.INSTANCE.mouseClicked(event.x(), event.y(), this.input)) {
            cir.setReturnValue(true);
        }
    }
}

package opal.dev.overwatch.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import opal.dev.overwatch.client.OverwatchGate;
import opal.dev.overwatch.client.WynnChatChannels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatChannelButtonsMixin extends Screen {

    protected ChatChannelButtonsMixin(Component title) {
        super(title);
    }

    @Shadow
    protected EditBox input;

    private final List<Button> overwatch$channelButtons = new ArrayList<>();

    @Inject(method = "init", at = @At("TAIL"))
    private void overwatch$addChannelButtons(CallbackInfo ci) {
        if (!OverwatchGate.INSTANCE.isInGame()) {
            return;
        }
        overwatch$channelButtons.clear();
        int buttonWidth = 54;
        int buttonHeight = 16;
        int y = this.input.getY() - buttonHeight - 2;
        int x = this.input.getX();
        for (WynnChatChannels.Channel channel : WynnChatChannels.Channel.values()) {
            Button button = Button.builder(
                    Component.literal(overwatch$label(channel)),
                    pressed -> {
                        WynnChatChannels.INSTANCE.select(channel);
                        overwatch$refreshLabels();
                    })
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();
            overwatch$channelButtons.add(button);
            this.addRenderableWidget(button);
            x += buttonWidth + 4;
        }
    }

    private void overwatch$refreshLabels() {
        List<WynnChatChannels.Channel> channels = List.of(WynnChatChannels.Channel.values());
        for (int i = 0; i < overwatch$channelButtons.size() && i < channels.size(); i++) {
            overwatch$channelButtons.get(i).setMessage(Component.literal(overwatch$label(channels.get(i))));
        }
    }

    private static String overwatch$label(WynnChatChannels.Channel channel) {
        boolean active = WynnChatChannels.INSTANCE.getCurrent() == channel;
        return (active ? "▶ " : "") + channel.getLabel();
    }
}

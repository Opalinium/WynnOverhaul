package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.components.ChatComponent;
import opal.dev.wynnoverhaul.client.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ChatComponent.class)
public abstract class ChatHistoryMixin {
    @ModifyConstant(method = "addMessageToQueue", constant = @Constant(intValue = 100), require = 0)
    private int wynnoverhaul$messageLimit(int original) {
        return ChatHud.historyLimit();
    }

    @ModifyConstant(method = "addMessageToDisplayQueue", constant = @Constant(intValue = 100), require = 0)
    private int wynnoverhaul$lineLimit(int original) {
        return ChatHud.historyLimit() * 3;
    }
}

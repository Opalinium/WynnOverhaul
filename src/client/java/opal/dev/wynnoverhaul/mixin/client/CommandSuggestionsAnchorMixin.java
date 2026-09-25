package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import opal.dev.wynnoverhaul.client.ChatHud;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsAnchorMixin {
    @Redirect(
        method = "showSuggestions",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = Opcodes.GETFIELD)
    )
    private int wynnoverhaul$suggestionHeight(Screen screen) {
        return wynnoverhaul$virtualHeight(screen);
    }

    @Redirect(
        method = "extractUsage",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = Opcodes.GETFIELD)
    )
    private int wynnoverhaul$usageHeight(Screen screen) {
        return wynnoverhaul$virtualHeight(screen);
    }

    private static int wynnoverhaul$virtualHeight(Screen screen) {
        if (screen instanceof ChatScreen && ChatHud.active()) {
            return ChatHud.virtualHeight(screen.height);
        }
        return screen.height;
    }
}

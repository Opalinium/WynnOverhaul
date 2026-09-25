package opal.dev.overwatch.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import opal.dev.overwatch.client.ChatHud;
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
    private int overwatch$suggestionHeight(Screen screen) {
        return overwatch$virtualHeight(screen);
    }

    @Redirect(
        method = "extractUsage",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = Opcodes.GETFIELD)
    )
    private int overwatch$usageHeight(Screen screen) {
        return overwatch$virtualHeight(screen);
    }

    private static int overwatch$virtualHeight(Screen screen) {
        if (screen instanceof ChatScreen && ChatHud.active()) {
            return ChatHud.virtualHeight(screen.height);
        }
        return screen.height;
    }
}

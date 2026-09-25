package opal.dev.wynnoverhaul.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import opal.dev.wynnoverhaul.client.WynnOverhaulGate;
import opal.dev.wynnoverhaul.client.PartyFriendNametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class PartyFriendNametagMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wynnoverhaul$decoratePlayerNametag(Entity entity, EntityRenderState renderState, float partialTick, CallbackInfo ci) {
        if (!WynnOverhaulGate.INSTANCE.isInGame()) return;
        if (renderState.nameTag == null) return;
        if (!(entity instanceof Player player)) return;
        renderState.nameTag = PartyFriendNametags.INSTANCE.decorate(player, renderState.nameTag);
    }
}

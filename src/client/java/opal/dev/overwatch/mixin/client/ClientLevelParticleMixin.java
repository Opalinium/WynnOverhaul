package opal.dev.overwatch.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import opal.dev.overwatch.client.HotspotRingParticles;
import opal.dev.overwatch.client.LootrunParticleFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelParticleMixin {

    @Inject(method = "doAddParticle", at = @At("HEAD"))
    private void overwatch$recordHotspotParticle(
            ParticleOptions particleOptions,
            boolean alwaysVisible,
            boolean ignoreRange,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            CallbackInfo ci
    ) {
        if (particleOptions instanceof DustParticleOptions dust) {
            HotspotRingParticles.record(dust.getColor(), x, y, z);
        } else if (particleOptions == ParticleTypes.FIREWORK) {
            LootrunParticleFeature.INSTANCE.onParticle(x, y, z);
        }
    }
}

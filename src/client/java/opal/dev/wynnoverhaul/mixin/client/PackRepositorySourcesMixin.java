package opal.dev.wynnoverhaul.mixin.client;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import opal.dev.wynnoverhaul.client.ItemPackCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackRepository.class)
public abstract class PackRepositorySourcesMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wynnoverhaul$addItemPack(RepositorySource[] sources, CallbackInfo ci) {
        if (this.sources.stream().noneMatch(source -> source instanceof ClientPackSource)) {
            return;
        }
        Set<RepositorySource> merged = new LinkedHashSet<>(this.sources);
        merged.add(ItemPackCache.INSTANCE);
        this.sources = merged;
    }
}

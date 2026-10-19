package net.sprocketgames.worldawakened.generation;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Concrete output: never re-resolve this after the owner has persisted it. */
public record WorldAwakenedGeneratedResult(
        String instanceId,
        WorldAwakenedGenerationMode source,
        List<WorldAwakenedGeneratedEffect> effects,
        int totalPower,
        Optional<ResourceLocation> rarityId,
        List<ResourceLocation> provenance) {
    public WorldAwakenedGeneratedResult { effects = List.copyOf(effects); provenance = List.copyOf(provenance); }
}

package net.sprocketgames.worldawakened.generation;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** Reload-compiled authoring input used by the shared generator. */
public record WorldAwakenedEffectCandidate(
        ResourceLocation id,
        ResourceLocation effectType,
        JsonObject parameters,
        int power,
        int selectionWeight,
        boolean allowRepeat,
        Set<ResourceLocation> excludes) {
    public WorldAwakenedEffectCandidate {
        excludes = Set.copyOf(excludes);
    }

    WorldAwakenedGeneratedEffect resolve() {
        return new WorldAwakenedGeneratedEffect(id, effectType, parameters.deepCopy(), Math.max(0, power));
    }
}

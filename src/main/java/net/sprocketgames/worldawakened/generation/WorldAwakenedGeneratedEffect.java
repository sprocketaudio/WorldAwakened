package net.sprocketgames.worldawakened.generation;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

/** Fully resolved, persistable selected effect. Parameters are copied at generation time. */
public record WorldAwakenedGeneratedEffect(
        ResourceLocation configuredEffectId,
        ResourceLocation effectType,
        JsonObject parameters,
        int power) {
}

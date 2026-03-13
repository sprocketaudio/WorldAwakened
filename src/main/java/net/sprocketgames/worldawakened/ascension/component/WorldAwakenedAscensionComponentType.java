package net.sprocketgames.worldawakened.ascension.component;

import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedAscensionComponentType(
        ResourceLocation id,
        boolean allowDuplicates,
        boolean suppressibleIndividually,
        Set<ResourceLocation> incompatibleWith,
        ParameterValidator parameterValidator) {
    @FunctionalInterface
    public interface ParameterValidator {
        Optional<String> validate(JsonObject parameters);
    }
}

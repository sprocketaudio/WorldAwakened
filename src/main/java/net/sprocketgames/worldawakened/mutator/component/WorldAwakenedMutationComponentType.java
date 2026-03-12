package net.sprocketgames.worldawakened.mutator.component;

import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedMutationComponentType(
        ResourceLocation id,
        boolean allowDuplicates,
        int budgetCost,
        Set<ResourceLocation> incompatibleWith,
        ParameterValidator parameterValidator) {
    @FunctionalInterface
    public interface ParameterValidator {
        Optional<String> validate(JsonObject parameters);
    }
}

package net.sprocketgames.worldawakened.data.load;

import com.mojang.serialization.Codec;

import net.sprocketgames.worldawakened.data.definition.WorldAwakenedDataDefinition;

public record WorldAwakenedObjectType<T extends WorldAwakenedDataDefinition>(
        String key,
        String folder,
        Codec<T> codec,
        DefinitionValidator<T> validator) {
    @FunctionalInterface
    public interface DefinitionValidator<T extends WorldAwakenedDataDefinition> {
        void validate(String sourcePath, T definition, net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary.Builder collector);
    }
}


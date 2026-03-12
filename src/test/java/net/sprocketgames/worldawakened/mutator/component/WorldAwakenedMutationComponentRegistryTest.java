package net.sprocketgames.worldawakened.mutator.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedMutationComponentRegistryTest {
    @Test
    void supportsAddonRegistrationWithoutReplacingBuiltins() {
        ResourceLocation id = ResourceLocation.parse("testpack:extension_mutation_" + System.nanoTime());
        WorldAwakenedMutationComponentType extension = new WorldAwakenedMutationComponentType(
                id,
                false,
                2,
                Set.of(),
                parameters -> Optional.empty());

        WorldAwakenedMutationComponentRegistry.RegistrationResult first = WorldAwakenedMutationComponentRegistry.register(extension);
        WorldAwakenedMutationComponentRegistry.RegistrationResult second = WorldAwakenedMutationComponentRegistry.register(extension);

        assertEquals(WorldAwakenedMutationComponentRegistry.RegistrationResult.REGISTERED, first);
        assertEquals(WorldAwakenedMutationComponentRegistry.RegistrationResult.ALREADY_REGISTERED, second);
        assertTrue(WorldAwakenedMutationComponentRegistry.lookup(id).isPresent());
    }
}

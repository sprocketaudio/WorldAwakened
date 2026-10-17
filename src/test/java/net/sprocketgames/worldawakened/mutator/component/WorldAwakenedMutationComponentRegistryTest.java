package net.sprocketgames.worldawakened.mutator.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedMutationComponentRegistryTest {
    @Test
    void supportsAddonRegistrationWithoutReplacingBuiltins() {
        ResourceLocation id = ResourceLocation.parse("testpack:extension_mutation_" + System.nanoTime());
        WorldAwakenedMutationComponentType extension = new WorldAwakenedMutationComponentType(
                id,
                false,
                Set.of(),
                parameters -> Optional.empty());

        WorldAwakenedMutationComponentRegistry.RegistrationResult first = WorldAwakenedMutationComponentRegistry.register(extension);
        WorldAwakenedMutationComponentRegistry.RegistrationResult second = WorldAwakenedMutationComponentRegistry.register(extension);

        assertEquals(WorldAwakenedMutationComponentRegistry.RegistrationResult.REGISTERED, first);
        assertEquals(WorldAwakenedMutationComponentRegistry.RegistrationResult.ALREADY_REGISTERED, second);
        assertTrue(WorldAwakenedMutationComponentRegistry.lookup(id).isPresent());
    }

    @Test
    void validatesLiveCombatComponentBounds() {
        assertTrue(validate("on_hit_effect", effect("minecraft:poison", 100, 0, 1.0D)).isEmpty());
        assertTrue(validate("on_hit_effect", effect("minecraft:not_real", 100, 0, 1.0D)).isPresent());
        assertTrue(validate("life_steal", amount(0.1D)).isEmpty());
        assertTrue(validate("life_steal", amount(1.1D)).isPresent());
        assertTrue(validate("death_explosion", amount(6.0D)).isEmpty());
        assertTrue(validate("death_explosion", amount(9.0D)).isPresent());
    }

    private static Optional<String> validate(String path, JsonObject parameters) {
        return WorldAwakenedMutationComponentRegistry.lookup(
                        ResourceLocation.fromNamespaceAndPath("worldawakened", path))
                .orElseThrow()
                .parameterValidator()
                .validate(parameters);
    }

    private static JsonObject amount(double value) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("amount", value);
        return parameters;
    }

    private static JsonObject effect(String id, int durationTicks, int amplifier, double chance) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("effect", id);
        parameters.addProperty("duration_ticks", durationTicks);
        parameters.addProperty("amplifier", amplifier);
        parameters.addProperty("chance", chance);
        return parameters;
    }
}

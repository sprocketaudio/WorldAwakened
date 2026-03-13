package net.sprocketgames.worldawakened.ascension.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedAscensionComponentRegistryTest {
    @Test
    void supportsAddonRegistrationWithoutReplacingBuiltins() {
        ResourceLocation id = ResourceLocation.parse("testpack:extension_ascension_" + System.nanoTime());
        WorldAwakenedAscensionComponentType extension = new WorldAwakenedAscensionComponentType(
                id,
                false,
                false,
                Set.of(),
                parameters -> Optional.empty());

        WorldAwakenedAscensionComponentRegistry.RegistrationResult first = WorldAwakenedAscensionComponentRegistry.register(extension);
        WorldAwakenedAscensionComponentRegistry.RegistrationResult second = WorldAwakenedAscensionComponentRegistry.register(extension);

        assertEquals(WorldAwakenedAscensionComponentRegistry.RegistrationResult.REGISTERED, first);
        assertEquals(WorldAwakenedAscensionComponentRegistry.RegistrationResult.ALREADY_REGISTERED, second);
        assertTrue(WorldAwakenedAscensionComponentRegistry.lookup(id).isPresent());
    }

    @Test
    void builtinsExposeComponentSuppressionCapabilityMetadata() {
        WorldAwakenedAscensionComponentType movement = WorldAwakenedAscensionComponentRegistry.lookup(
                ResourceLocation.parse("worldawakened:movement_speed_bonus")).orElseThrow();
        WorldAwakenedAscensionComponentType health = WorldAwakenedAscensionComponentRegistry.lookup(
                ResourceLocation.parse("worldawakened:max_health_bonus")).orElseThrow();

        assertTrue(movement.suppressibleIndividually());
        assertFalse(health.suppressibleIndividually());
    }
}

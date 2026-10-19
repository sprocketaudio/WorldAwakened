package net.sprocketgames.worldawakened.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.rarity.RarityDefinition;

class WorldAwakenedEffectGenerationServiceTest {
    private static final ResourceLocation HEALTH = id("health");
    private static final ResourceLocation ARMOR = id("armor");

    @Test
    void resolvesConcreteDeterministicCombinationAndRarityFromPowerOnly() {
        WorldAwakenedEffectCandidate health = candidate("health", 2, 100, false);
        WorldAwakenedEffectCandidate armor = candidate("armor", 3, 1, false);
        Map<ResourceLocation, RarityDefinition> rarities = Map.of(
                id("common"), rarity("common", 0), id("rare"), rarity("rare", 5));
        WorldAwakenedGenerationSpec spec = WorldAwakenedGenerationSpec.generated(Map.of(2, 1));

        WorldAwakenedGeneratedResult result = WorldAwakenedEffectGenerationService
                .generate("instance", List.of(health, armor), spec, rarities, 42L).orElseThrow();

        assertEquals(5, result.totalPower());
        assertEquals(Optional.of(id("rare")), result.rarityId());
        assertEquals(2, result.effects().size());
        assertEquals(result, WorldAwakenedEffectGenerationService
                .generate("instance", List.of(health, armor), spec, rarities, 42L).orElseThrow());
    }

    @Test
    void repeatRulesAndCompatibilityAreEnforced() {
        WorldAwakenedEffectCandidate only = candidate("only", 1, 1, false);
        assertTrue(WorldAwakenedEffectGenerationService.generate("id", List.of(only),
                WorldAwakenedGenerationSpec.generated(Map.of(2, 1)), Map.of(), 1L).isEmpty());

        WorldAwakenedEffectCandidate repeat = candidate("repeat", 1, 1, true);
        assertTrue(WorldAwakenedEffectGenerationService.generate("id", List.of(repeat),
                WorldAwakenedGenerationSpec.generated(Map.of(2, 1)), Map.of(), 1L).isPresent());
    }

    private static WorldAwakenedEffectCandidate candidate(String name, int power, int weight, boolean repeat) {
        return new WorldAwakenedEffectCandidate(id(name), id("effect_" + name), new JsonObject(), power, weight, repeat, Set.of());
    }
    private static RarityDefinition rarity(String name, int minimum) {
        return new RarityDefinition(1, id(name), true, new JsonPrimitive(name), minimum);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}

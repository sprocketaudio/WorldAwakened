package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;

class WorldAwakenedMutatorServiceSelectorIndexTest {
    @Test
    void selectorIndexNarrowsPoolsByEntityAndTag() {
        LinkedHashMap<ResourceLocation, MobMutatorDefinition> mutators = new LinkedHashMap<>();
        mutators.put(
                ResourceLocation.parse("testpack:zombie_mutator"),
                mutator("testpack:zombie_mutator", List.of("minecraft:zombie"), List.of("#minecraft:undead")));
        mutators.put(
                ResourceLocation.parse("testpack:raider_mutator"),
                mutator("testpack:raider_mutator", List.of(), List.of("#minecraft:raiders")));
        mutators.put(
                ResourceLocation.parse("testpack:wildcard_mutator"),
                mutator("testpack:wildcard_mutator", List.of(), List.of()));

        LinkedHashMap<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>();
        pools.put(
                ResourceLocation.parse("testpack:zombie_pool"),
                pool("testpack:zombie_pool", List.of("testpack:zombie_mutator")));
        pools.put(
                ResourceLocation.parse("testpack:raider_pool"),
                pool("testpack:raider_pool", List.of("testpack:raider_mutator")));
        pools.put(
                ResourceLocation.parse("testpack:wildcard_pool"),
                pool("testpack:wildcard_pool", List.of("testpack:wildcard_mutator")));

        WorldAwakenedMutatorService.SelectorIndex index = WorldAwakenedMutatorService.buildSelectorIndex(pools, mutators);

        LinkedHashSet<ResourceLocation> zombieCandidates = index.candidatePoolIds(
                ResourceLocation.parse("minecraft:zombie"),
                Set.of(ResourceLocation.parse("minecraft:undead")));
        assertEquals(
                new LinkedHashSet<>(List.of(
                        ResourceLocation.parse("testpack:zombie_pool"),
                        ResourceLocation.parse("testpack:wildcard_pool"))),
                zombieCandidates);

        LinkedHashSet<ResourceLocation> raiderCandidates = index.candidatePoolIds(
                ResourceLocation.parse("minecraft:pillager"),
                Set.of(ResourceLocation.parse("minecraft:raiders")));
        assertEquals(
                new LinkedHashSet<>(List.of(
                        ResourceLocation.parse("testpack:raider_pool"),
                        ResourceLocation.parse("testpack:wildcard_pool"))),
                raiderCandidates);

        assertTrue(zombieCandidates.size() < pools.size());
        assertTrue(raiderCandidates.size() < pools.size());
    }

    private static MobMutatorDefinition mutator(
            String id,
            List<String> eligibleEntities,
            List<String> eligibleTags) {
        return new MobMutatorDefinition(
                1,
                ResourceLocation.parse(id),
                true,
                1,
                List.of(),
                new MobMutatorDefinition.MutatorPresentation(new JsonPrimitive("Mutator"), Optional.empty()),
                new MobMutatorDefinition.MutatorStacking(Optional.empty(), List.of(), 1),
                new MobMutatorDefinition.MutatorEligibility(
                        resourceIds(eligibleEntities),
                        eligibleTags,
                        List.of(),
                        List.of(),
                        false,
                        false),
                new MobMutatorDefinition.MutatorBehavior(
                        List.of(),
                        new JsonObject(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of("on_spawn")));
    }

    private static MutationPoolDefinition pool(String id, List<String> mutatorIds) {
        return new MutationPoolDefinition(
                1,
                ResourceLocation.parse(id),
                true,
                1,
                1.0D,
                false,
                false,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                mutatorIds.stream().map(idValue -> (JsonElement) new JsonPrimitive(idValue)).toList(),
                Optional.of(2));
    }

    private static List<ResourceLocation> resourceIds(List<String> raw) {
        return raw.stream().map(ResourceLocation::parse).toList();
    }
}

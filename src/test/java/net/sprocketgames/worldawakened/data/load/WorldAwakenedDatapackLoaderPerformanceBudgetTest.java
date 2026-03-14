package net.sprocketgames.worldawakened.data.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnostic;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticSeverity;

class WorldAwakenedDatapackLoaderPerformanceBudgetTest {
    @Test
    void warnsWhenEnabledMutatorComponentsExceedLimit() {
        MobMutatorDefinition definition = mutatorDefinition(11, 0);

        Optional<WorldAwakenedDiagnostic> diagnostic =
                WorldAwakenedDatapackLoader.evaluateMutatorComponentCountLimit(definition, "test:mutator", 10);

        assertTrue(diagnostic.isPresent());
        assertEquals(WorldAwakenedDiagnosticSeverity.WARNING, diagnostic.get().severity());
        assertEquals(WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED, diagnostic.get().code());
    }

    @Test
    void doesNotWarnWhenDisabledComponentsAreOutsideLimitCount() {
        MobMutatorDefinition definition = mutatorDefinition(10, 5);

        Optional<WorldAwakenedDiagnostic> diagnostic =
                WorldAwakenedDatapackLoader.evaluateMutatorComponentCountLimit(definition, "test:mutator", 10);

        assertFalse(diagnostic.isPresent());
    }

    @Test
    void warnsWhenPoolOverrideExceedsMutatorsPerSpawnLimit() {
        MutationPoolDefinition definition = mutationPoolDefinition(Optional.of(9));

        Optional<WorldAwakenedDiagnostic> diagnostic =
                WorldAwakenedDatapackLoader.evaluatePoolMutatorCountLimit(definition, "test:pool", 8, 2);

        assertTrue(diagnostic.isPresent());
        assertEquals(WorldAwakenedDiagnosticSeverity.WARNING, diagnostic.get().severity());
        assertEquals(WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COUNT_EXCEEDED, diagnostic.get().code());
        assertTrue(diagnostic.get().message().contains("pool max_mutators_per_entity"));
    }

    @Test
    void warnsWhenDefaultMutatorCapExceedsMutatorsPerSpawnLimit() {
        MutationPoolDefinition definition = mutationPoolDefinition(Optional.empty());

        Optional<WorldAwakenedDiagnostic> diagnostic =
                WorldAwakenedDatapackLoader.evaluatePoolMutatorCountLimit(definition, "test:pool", 8, 9);

        assertTrue(diagnostic.isPresent());
        assertEquals(WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COUNT_EXCEEDED, diagnostic.get().code());
        assertTrue(diagnostic.get().message().contains("default mutators.max_mutators_per_mob"));
    }

    @Test
    void doesNotWarnWhenRequestedPoolCapIsWithinLimit() {
        MutationPoolDefinition definition = mutationPoolDefinition(Optional.of(8));

        Optional<WorldAwakenedDiagnostic> diagnostic =
                WorldAwakenedDatapackLoader.evaluatePoolMutatorCountLimit(definition, "test:pool", 8, 2);

        assertFalse(diagnostic.isPresent());
    }

    @Test
    void rejectsMutationChanceWhenRawTypeIsNotNumeric() {
        JsonObject root = JsonParser.parseString("""
                {
                  "id": "testpack:type_invalid_pool",
                  "mutation_chance": "high"
                }
                """).getAsJsonObject();

        Optional<String> validation = WorldAwakenedDatapackLoader.validateRawMutationChance("mutation_pools", root);

        assertTrue(validation.isPresent());
        assertTrue(validation.get().contains("number"));
    }

    @Test
    void rejectsMutationChanceWhenRawValueIsOutsideRange() {
        JsonObject root = JsonParser.parseString("""
                {
                  "id": "testpack:range_invalid_pool",
                  "mutation_chance": 1.2
                }
                """).getAsJsonObject();

        Optional<String> validation = WorldAwakenedDatapackLoader.validateRawMutationChance("mutation_pools", root);

        assertTrue(validation.isPresent());
        assertTrue(validation.get().contains("[0.0, 1.0]"));
    }

    @Test
    void acceptsMutationChanceWhenRawValueIsWithinRange() {
        JsonObject root = JsonParser.parseString("""
                {
                  "id": "testpack:valid_pool",
                  "mutation_chance": 0.35
                }
                """).getAsJsonObject();

        Optional<String> validation = WorldAwakenedDatapackLoader.validateRawMutationChance("mutation_pools", root);

        assertFalse(validation.isPresent());
    }

    private static MobMutatorDefinition mutatorDefinition(int enabledComponents, int disabledComponents) {
        List<MutationComponentDefinition> components = new ArrayList<>(enabledComponents + disabledComponents);
        for (int index = 0; index < enabledComponents; index++) {
            components.add(component(true, index));
        }
        for (int index = 0; index < disabledComponents; index++) {
            components.add(component(false, enabledComponents + index));
        }
        return new MobMutatorDefinition(
                1,
                ResourceLocation.parse("testpack:limit_test_mutator"),
                true,
                1,
                List.copyOf(components),
                new MobMutatorDefinition.MutatorPresentation(new JsonPrimitive("Limit Test"), Optional.empty()),
                new MobMutatorDefinition.MutatorStacking(Optional.empty(), List.of(), 1),
                new MobMutatorDefinition.MutatorEligibility(List.of(), List.of(), List.of(), List.of(), false, false),
                new MobMutatorDefinition.MutatorBehavior(List.of(), new JsonObject(), Optional.empty(), Optional.empty(), List.of("on_spawn")));
    }

    private static MutationComponentDefinition component(boolean enabled, int index) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("amount", index + 1.0D);
        return new MutationComponentDefinition(
                ResourceLocation.parse("worldawakened:max_health_bonus"),
                enabled,
                0,
                parameters,
                List.of(),
                List.of(),
                List.of());
    }

    private static MutationPoolDefinition mutationPoolDefinition(Optional<Integer> maxMutatorsPerEntity) {
        return new MutationPoolDefinition(
                1,
                ResourceLocation.parse("testpack:limit_test_pool"),
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
                List.of(new JsonPrimitive("testpack:limit_test_mutator")),
                maxMutatorsPerEntity);
    }
}

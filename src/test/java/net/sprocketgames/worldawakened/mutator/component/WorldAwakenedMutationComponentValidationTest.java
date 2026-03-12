package net.sprocketgames.worldawakened.mutator.component;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;

class WorldAwakenedMutationComponentValidationTest {
    @Test
    void rejectsEmptyComponents() {
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.EMPTY_COMPONENT_LIST);
    }

    @Test
    void rejectsUnknownComponentTypes() {
        MutationComponentDefinition unknown = component("testpack:unknown_component", true, params(), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(unknown), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.UNKNOWN_COMPONENT_TYPE);
    }

    @Test
    void rejectsIncompatibleComposition() {
        MutationComponentDefinition fire = component("worldawakened:fire_package", true, params(), List.of(), List.of());
        MutationComponentDefinition frost = component("worldawakened:frost_package", true, params(), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(fire, frost), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.INCOMPATIBLE_COMPONENT_COMPOSITION);
    }

    @Test
    void rejectsDuplicateComponentTypesWhenUnsupported() {
        MutationComponentDefinition first = component("worldawakened:max_health_bonus", true, params("amount", 3.0D), List.of(), List.of());
        MutationComponentDefinition second = component("worldawakened:max_health_bonus", true, params("amount", 2.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(first, second), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.DUPLICATE_COMPONENT_TYPE);
    }

    @Test
    void rejectsImpossibleSummonComposition() {
        MutationComponentDefinition summonCooldown = component("worldawakened:summon_cooldown", true, params("seconds", 10.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(summonCooldown), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.IMPOSSIBLE_COMPONENT_COMPOSITION);
    }

    @Test
    void rejectsOverBudgetCompositionWhenBudgetIsPresent() {
        MutationComponentDefinition summon = component("worldawakened:reinforcement_summon", true, params("entity", "minecraft:zombie"), List.of(), List.of());
        MutationComponentDefinition cooldown = component("worldawakened:summon_cooldown", true, params("seconds", 12.0D), List.of(), List.of());
        MutationComponentDefinition cap = component("worldawakened:summon_cap", true, params("max", 4), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(summon, cooldown, cap), Optional.of(3));
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.COMPONENT_BUDGET_EXCEEDED);
    }

    @Test
    void rejectsNoRuntimeResultWhenAllComponentsDisabled() {
        MutationComponentDefinition disabled = component("worldawakened:max_health_bonus", false, params("amount", 4.0D), List.of(), List.of());
        WorldAwakenedMutationComponentValidation.Result result =
                WorldAwakenedMutationComponentValidation.validate(List.of(disabled), Optional.empty());
        assertHasIssue(result, WorldAwakenedMutationComponentValidation.IssueKind.NO_RUNTIME_RESULT);
    }

    private static MutationComponentDefinition component(
            String type,
            boolean enabled,
            JsonObject parameters,
            List<ResourceLocation> exclusions,
            List<ResourceLocation> conflictsWith) {
        return new MutationComponentDefinition(
                ResourceLocation.parse(type),
                enabled,
                0,
                parameters,
                List.of(),
                exclusions,
                conflictsWith);
    }

    private static JsonObject params() {
        return new JsonObject();
    }

    private static JsonObject params(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static JsonObject params(String key, Number value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static void assertHasIssue(
            WorldAwakenedMutationComponentValidation.Result result,
            WorldAwakenedMutationComponentValidation.IssueKind expected) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.kind() == expected),
                () -> "Expected issue " + expected + " but got " + result.issues());
    }
}

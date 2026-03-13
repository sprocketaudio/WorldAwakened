package net.sprocketgames.worldawakened.ascension.component;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentSuppressionPolicy;

class WorldAwakenedAscensionComponentValidationTest {
    @Test
    void rejectsEmptyComponents() {
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of());
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.EMPTY_COMPONENT_LIST);
    }

    @Test
    void rejectsUnknownComponentTypes() {
        AscensionComponentDefinition unknown = component("testpack:unknown_component", true, params(), List.of(), List.of());
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(unknown));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.UNKNOWN_COMPONENT_TYPE);
    }

    @Test
    void rejectsInvalidComponentParameters() {
        AscensionComponentDefinition invalid = component("worldawakened:armor_bonus", true, params(), List.of(), List.of());
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(invalid));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.INVALID_COMPONENT_PARAMETERS);
    }

    @Test
    void rejectsIncompatibleComponentConflicts() {
        ResourceLocation rightType = ResourceLocation.fromNamespaceAndPath("worldawakened", "attack_damage_bonus");
        AscensionComponentDefinition left = component("worldawakened:max_health_bonus", true, params("amount", 4.0D), List.of(), List.of(rightType));
        AscensionComponentDefinition right = component("worldawakened:attack_damage_bonus", true, params("amount", 2.0D), List.of(), List.of());
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(left, right));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.INCOMPATIBLE_COMPONENT_COMPOSITION);
    }

    @Test
    void rejectsDuplicateComponentTypesWhenUnsupported() {
        AscensionComponentDefinition first = component("worldawakened:max_health_bonus", true, params("amount", 2.0D), List.of(), List.of());
        AscensionComponentDefinition second = component("worldawakened:max_health_bonus", true, params("amount", 3.0D), List.of(), List.of());
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(first, second));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.DUPLICATE_COMPONENT_TYPE);
    }

    @Test
    void rejectsNoRuntimeResultWhenAllComponentsDisabled() {
        AscensionComponentDefinition disabled = component("worldawakened:max_health_bonus", false, params("amount", 2.0D), List.of(), List.of());
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(disabled));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.NO_RUNTIME_RESULT);
    }

    @Test
    void rejectsGroupedSuppressionWithoutGroup() {
        AscensionComponentDefinition grouped = component(
                "worldawakened:movement_speed_bonus",
                true,
                params("amount", 0.1D),
                List.of(),
                List.of(),
                true,
                Optional.empty(),
                AscensionComponentSuppressionPolicy.GROUPED);
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(grouped));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.SUPPRESSION_GROUP_REQUIRED);
    }

    @Test
    void rejectsIndependentSuppressionOnUnsupportedComponentType() {
        AscensionComponentDefinition unsupported = component(
                "worldawakened:max_health_bonus",
                true,
                params("amount", 2.0D),
                List.of(),
                List.of(),
                true,
                Optional.empty(),
                AscensionComponentSuppressionPolicy.INDEPENDENT);
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(unsupported));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.COMPONENT_NOT_SUPPRESSIBLE);
    }

    @Test
    void rejectsComponentLevelSuppressionWhenMetadataFlagIsNotEnabled() {
        AscensionComponentDefinition missingMetadataFlag = component(
                "worldawakened:movement_speed_bonus",
                true,
                params("amount", 0.2D),
                List.of(),
                List.of(),
                false,
                Optional.empty(),
                AscensionComponentSuppressionPolicy.INDEPENDENT);
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(missingMetadataFlag));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.COMPONENT_NOT_SUPPRESSIBLE);
    }

    @Test
    void rejectsConflictingSuppressionPoliciesInOneGroup() {
        AscensionComponentDefinition grouped = component(
                "worldawakened:movement_speed_bonus",
                true,
                params("amount", 0.15D),
                List.of(),
                List.of(),
                false,
                Optional.of("mobility_package"),
                AscensionComponentSuppressionPolicy.GROUPED);
        AscensionComponentDefinition independentWithGroup = component(
                "worldawakened:movement_speed_bonus",
                true,
                params("amount", 0.2D),
                List.of(),
                List.of(),
                true,
                Optional.of("mobility_package"),
                AscensionComponentSuppressionPolicy.INDEPENDENT);
        WorldAwakenedAscensionComponentValidation.Result result =
                WorldAwakenedAscensionComponentValidation.validate(List.of(grouped, independentWithGroup));
        assertHasIssue(result, WorldAwakenedAscensionComponentValidation.IssueKind.SUPPRESSION_INVALID_PARTIAL);
    }

    private static AscensionComponentDefinition component(
            String type,
            boolean enabled,
            JsonObject parameters,
            List<ResourceLocation> exclusions,
            List<ResourceLocation> conflictsWith) {
        return component(
                type,
                enabled,
                parameters,
                exclusions,
                conflictsWith,
                false,
                Optional.empty(),
                AscensionComponentSuppressionPolicy.REWARD_ONLY);
    }

    private static AscensionComponentDefinition component(
            String type,
            boolean enabled,
            JsonObject parameters,
            List<ResourceLocation> exclusions,
            List<ResourceLocation> conflictsWith,
            boolean suppressibleIndividually,
            Optional<String> suppressionGroup,
            AscensionComponentSuppressionPolicy suppressionPolicy) {
        return new AscensionComponentDefinition(
                ResourceLocation.parse(type),
                enabled,
                0,
                parameters,
                List.of(),
                exclusions,
                conflictsWith,
                suppressibleIndividually,
                suppressionGroup,
                suppressionPolicy);
    }

    private static JsonObject params() {
        return new JsonObject();
    }

    private static JsonObject params(String key, Number value) {
        JsonObject object = new JsonObject();
        object.addProperty(key, value);
        return object;
    }

    private static void assertHasIssue(
            WorldAwakenedAscensionComponentValidation.Result result,
            WorldAwakenedAscensionComponentValidation.IssueKind expected) {
        assertTrue(result.issues().stream().anyMatch(issue -> issue.kind() == expected),
                () -> "Expected issue " + expected + " but got " + result.issues());
    }
}

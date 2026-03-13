package net.sprocketgames.worldawakened.ascension.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentSuppressionPolicy;

public final class WorldAwakenedAscensionComponentValidation {
    private WorldAwakenedAscensionComponentValidation() {
    }

    public static Result validate(List<AscensionComponentDefinition> components) {
        List<Issue> issues = new ArrayList<>();
        if (components.isEmpty()) {
            issues.add(new Issue(IssueKind.EMPTY_COMPONENT_LIST, "components must include at least one entry"));
            return new Result(List.copyOf(issues), List.of());
        }

        LinkedHashSet<ResourceLocation> activeTypeIds = new LinkedHashSet<>();
        Map<ResourceLocation, Integer> activeCounts = new LinkedHashMap<>();
        Map<Integer, WorldAwakenedAscensionComponentType> activeTypesByIndex = new LinkedHashMap<>();
        Map<String, AscensionComponentSuppressionPolicy> suppressionPolicyByGroup = new LinkedHashMap<>();
        Map<String, Integer> firstGroupIndex = new LinkedHashMap<>();

        for (int index = 0; index < components.size(); index++) {
            AscensionComponentDefinition component = components.get(index);
            Optional<WorldAwakenedAscensionComponentType> type = WorldAwakenedAscensionComponentRegistry.lookup(component.type());
            if (type.isEmpty()) {
                issues.add(new Issue(
                        IssueKind.UNKNOWN_COMPONENT_TYPE,
                        "components[" + index + "] unknown type: " + component.type()));
                continue;
            }

            WorldAwakenedAscensionComponentType resolvedType = type.get();
            validateSuppressionMetadata(
                    component,
                    resolvedType,
                    index,
                    suppressionPolicyByGroup,
                    firstGroupIndex,
                    issues);

            if (!component.enabled()) {
                continue;
            }

            int componentIndex = index;
            Optional<String> parameterError = resolvedType.parameterValidator().validate(component.parameters());
            parameterError.ifPresent(error -> issues.add(new Issue(
                    IssueKind.INVALID_COMPONENT_PARAMETERS,
                    "components[" + componentIndex + "] " + error)));

            activeTypeIds.add(component.type());
            activeCounts.merge(component.type(), 1, Integer::sum);
            activeTypesByIndex.put(index, resolvedType);
        }

        if (activeTypeIds.isEmpty()) {
            issues.add(new Issue(IssueKind.NO_RUNTIME_RESULT, "all components are disabled or invalid"));
        }

        for (Map.Entry<ResourceLocation, Integer> entry : activeCounts.entrySet()) {
            if (entry.getValue() < 2) {
                continue;
            }
            WorldAwakenedAscensionComponentType type = WorldAwakenedAscensionComponentRegistry.lookup(entry.getKey()).orElse(null);
            if (type != null && !type.allowDuplicates()) {
                issues.add(new Issue(
                        IssueKind.DUPLICATE_COMPONENT_TYPE,
                        "duplicate component type is not allowed: " + entry.getKey()));
            }
        }

        for (Map.Entry<Integer, WorldAwakenedAscensionComponentType> left : activeTypesByIndex.entrySet()) {
            for (Map.Entry<Integer, WorldAwakenedAscensionComponentType> right : activeTypesByIndex.entrySet()) {
                if (left.getKey() >= right.getKey()) {
                    continue;
                }
                AscensionComponentDefinition leftComponent = components.get(left.getKey());
                AscensionComponentDefinition rightComponent = components.get(right.getKey());
                ResourceLocation leftType = left.getValue().id();
                ResourceLocation rightType = right.getValue().id();

                if (left.getValue().incompatibleWith().contains(rightType) || right.getValue().incompatibleWith().contains(leftType)) {
                    issues.add(new Issue(
                            IssueKind.INCOMPATIBLE_COMPONENT_COMPOSITION,
                            "components[" + left.getKey() + "] " + leftType + " incompatible with components[" + right.getKey() + "] " + rightType));
                }
                if (leftComponent.conflictsWith().contains(rightType) || rightComponent.conflictsWith().contains(leftType)
                        || leftComponent.exclusions().contains(rightType) || rightComponent.exclusions().contains(leftType)) {
                    issues.add(new Issue(
                            IssueKind.INCOMPATIBLE_COMPONENT_COMPOSITION,
                            "components[" + left.getKey() + "] conflicts with components[" + right.getKey() + "]"));
                }
            }
        }

        return new Result(deduplicate(issues), List.copyOf(activeTypeIds));
    }

    private static List<Issue> deduplicate(List<Issue> issues) {
        List<Issue> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Issue issue : issues) {
            String key = issue.kind() + "|" + issue.detail();
            if (seen.add(key)) {
                deduplicated.add(issue);
            }
        }
        return List.copyOf(deduplicated);
    }

    private static void validateSuppressionMetadata(
            AscensionComponentDefinition component,
            WorldAwakenedAscensionComponentType type,
            int index,
            Map<String, AscensionComponentSuppressionPolicy> suppressionPolicyByGroup,
            Map<String, Integer> firstGroupIndex,
            List<Issue> issues) {
        AscensionComponentSuppressionPolicy policy = component.effectiveSuppressionPolicy();
        Optional<String> suppressionGroup = component.normalizedSuppressionGroup();

        if (component.suppressionGroup().isPresent() && suppressionGroup.isEmpty()) {
            issues.add(new Issue(
                    IssueKind.SUPPRESSION_INVALID_PARTIAL,
                    "components[" + index + "].suppression_group must not be blank"));
        }

        boolean componentLevelSuppressionRequested = policy == AscensionComponentSuppressionPolicy.INDEPENDENT
                || policy == AscensionComponentSuppressionPolicy.GROUPED;
        if (componentLevelSuppressionRequested && !component.suppressibleIndividually()) {
            issues.add(new Issue(
                    IssueKind.COMPONENT_NOT_SUPPRESSIBLE,
                    "components[" + index + "] component-level suppression requires suppressible_individually=true"));
        }

        if (componentLevelSuppressionRequested && !type.suppressibleIndividually()) {
            issues.add(new Issue(
                    IssueKind.COMPONENT_NOT_SUPPRESSIBLE,
                    "components[" + index + "] type " + component.type() + " does not support component-level suppression"));
        }

        if (policy == AscensionComponentSuppressionPolicy.GROUPED && suppressionGroup.isEmpty()) {
            issues.add(new Issue(
                    IssueKind.SUPPRESSION_GROUP_REQUIRED,
                    "components[" + index + "] grouped suppression requires suppression_group"));
            return;
        }

        if (policy != AscensionComponentSuppressionPolicy.GROUPED && suppressionGroup.isPresent()) {
            issues.add(new Issue(
                    IssueKind.SUPPRESSION_INVALID_PARTIAL,
                    "components[" + index + "] suppression_group requires suppression_policy=grouped"));
        }

        if (suppressionGroup.isPresent()) {
            String group = suppressionGroup.get();
            AscensionComponentSuppressionPolicy existing = suppressionPolicyByGroup.get(group);
            if (existing == null) {
                suppressionPolicyByGroup.put(group, policy);
                firstGroupIndex.put(group, index);
            } else if (existing != policy) {
                issues.add(new Issue(
                        IssueKind.SUPPRESSION_INVALID_PARTIAL,
                        "components[" + index + "] suppression_group '" + group
                                + "' conflicts with components[" + firstGroupIndex.get(group)
                                + "] policy " + existing.serializedName()));
            }
        }
    }

    public enum IssueKind {
        EMPTY_COMPONENT_LIST,
        UNKNOWN_COMPONENT_TYPE,
        INVALID_COMPONENT_PARAMETERS,
        INCOMPATIBLE_COMPONENT_COMPOSITION,
        NO_RUNTIME_RESULT,
        DUPLICATE_COMPONENT_TYPE,
        COMPONENT_NOT_SUPPRESSIBLE,
        SUPPRESSION_GROUP_REQUIRED,
        SUPPRESSION_INVALID_PARTIAL
    }

    public record Issue(IssueKind kind, String detail) {
    }

    public record Result(List<Issue> issues, List<ResourceLocation> activeComponentTypes) {
    }
}

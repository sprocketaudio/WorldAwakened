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

        for (int index = 0; index < components.size(); index++) {
            AscensionComponentDefinition component = components.get(index);
            Optional<WorldAwakenedAscensionComponentType> type = WorldAwakenedAscensionComponentRegistry.lookup(component.type());
            if (type.isEmpty()) {
                issues.add(new Issue(
                        IssueKind.UNKNOWN_COMPONENT_TYPE,
                        "components[" + index + "] unknown type: " + component.type()));
                continue;
            }

            if (!component.enabled()) {
                continue;
            }

            WorldAwakenedAscensionComponentType resolvedType = type.get();
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

    public enum IssueKind {
        EMPTY_COMPONENT_LIST,
        UNKNOWN_COMPONENT_TYPE,
        INVALID_COMPONENT_PARAMETERS,
        INCOMPATIBLE_COMPONENT_COMPOSITION,
        NO_RUNTIME_RESULT,
        DUPLICATE_COMPONENT_TYPE
    }

    public record Issue(IssueKind kind, String detail) {
    }

    public record Result(List<Issue> issues, List<ResourceLocation> activeComponentTypes) {
    }
}

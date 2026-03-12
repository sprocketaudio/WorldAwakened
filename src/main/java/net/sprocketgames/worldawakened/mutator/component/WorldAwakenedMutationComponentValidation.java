package net.sprocketgames.worldawakened.mutator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;

public final class WorldAwakenedMutationComponentValidation {
    private static final ResourceLocation REINFORCEMENT_SUMMON = ResourceLocation.fromNamespaceAndPath("worldawakened", "reinforcement_summon");
    private static final ResourceLocation SUMMON_COOLDOWN = ResourceLocation.fromNamespaceAndPath("worldawakened", "summon_cooldown");
    private static final ResourceLocation SUMMON_CAP = ResourceLocation.fromNamespaceAndPath("worldawakened", "summon_cap");

    private WorldAwakenedMutationComponentValidation() {
    }

    public static Result validate(List<MutationComponentDefinition> components, Optional<Integer> componentBudget) {
        List<Issue> issues = new ArrayList<>();
        if (components.isEmpty()) {
            issues.add(new Issue(IssueKind.EMPTY_COMPONENT_LIST, "components must include at least one entry"));
            return new Result(List.copyOf(issues), List.of(), 0);
        }

        int totalCost = 0;
        LinkedHashSet<ResourceLocation> activeTypeIds = new LinkedHashSet<>();
        Map<ResourceLocation, Integer> activeCounts = new LinkedHashMap<>();
        Map<Integer, WorldAwakenedMutationComponentType> activeTypesByIndex = new LinkedHashMap<>();

        for (int index = 0; index < components.size(); index++) {
            MutationComponentDefinition component = components.get(index);
            Optional<WorldAwakenedMutationComponentType> type = WorldAwakenedMutationComponentRegistry.lookup(component.type());
            if (type.isEmpty()) {
                issues.add(new Issue(
                        IssueKind.UNKNOWN_COMPONENT_TYPE,
                        "components[" + index + "] unknown type: " + component.type()));
                continue;
            }

            if (!component.enabled()) {
                continue;
            }

            WorldAwakenedMutationComponentType resolvedType = type.get();
            int componentIndex = index;
            Optional<String> parameterError = resolvedType.parameterValidator().validate(component.parameters());
            parameterError.ifPresent(error -> issues.add(new Issue(
                    IssueKind.INVALID_COMPONENT_PARAMETERS,
                    "components[" + componentIndex + "] " + error)));

            activeTypeIds.add(component.type());
            activeCounts.merge(component.type(), 1, Integer::sum);
            activeTypesByIndex.put(index, resolvedType);
            totalCost += Math.max(0, resolvedType.budgetCost());
        }

        if (activeTypeIds.isEmpty()) {
            issues.add(new Issue(IssueKind.NO_RUNTIME_RESULT, "all components are disabled or invalid"));
        }

        for (Map.Entry<ResourceLocation, Integer> entry : activeCounts.entrySet()) {
            if (entry.getValue() < 2) {
                continue;
            }
            WorldAwakenedMutationComponentType type = WorldAwakenedMutationComponentRegistry.lookup(entry.getKey()).orElse(null);
            if (type != null && !type.allowDuplicates()) {
                issues.add(new Issue(
                        IssueKind.DUPLICATE_COMPONENT_TYPE,
                        "duplicate component type is not allowed: " + entry.getKey()));
            }
        }

        for (Map.Entry<Integer, WorldAwakenedMutationComponentType> left : activeTypesByIndex.entrySet()) {
            for (Map.Entry<Integer, WorldAwakenedMutationComponentType> right : activeTypesByIndex.entrySet()) {
                if (left.getKey() >= right.getKey()) {
                    continue;
                }
                MutationComponentDefinition leftComponent = components.get(left.getKey());
                MutationComponentDefinition rightComponent = components.get(right.getKey());
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

        boolean hasSummonHelper = activeTypeIds.contains(SUMMON_COOLDOWN) || activeTypeIds.contains(SUMMON_CAP);
        if (hasSummonHelper && !activeTypeIds.contains(REINFORCEMENT_SUMMON)) {
            issues.add(new Issue(
                    IssueKind.IMPOSSIBLE_COMPONENT_COMPOSITION,
                    "summon_cooldown and summon_cap require reinforcement_summon"));
        }

        if (componentBudget.isPresent() && componentBudget.get() > 0 && totalCost > componentBudget.get()) {
            issues.add(new Issue(
                    IssueKind.COMPONENT_BUDGET_EXCEEDED,
                    "active component budget exceeded: cost=" + totalCost + " budget=" + componentBudget.get()));
        }

        return new Result(deduplicate(issues), List.copyOf(activeTypeIds), totalCost);
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
        IMPOSSIBLE_COMPONENT_COMPOSITION,
        NO_RUNTIME_RESULT,
        COMPONENT_BUDGET_EXCEEDED,
        DUPLICATE_COMPONENT_TYPE
    }

    public record Issue(IssueKind kind, String detail) {
    }

    public record Result(
            List<Issue> issues,
            List<ResourceLocation> activeComponentTypes,
            int totalBudgetCost) {
    }
}

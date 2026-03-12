package net.sprocketgames.worldawakened.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;

public final class WorldAwakenedStageRegistry {
    private static final Comparator<StageDefinition> DISPLAY_ORDER = Comparator
            .comparingInt(StageDefinition::sortIndex)
            .thenComparing(stage -> stage.id().toString());

    private final Map<ResourceLocation, StageDefinition> stagesById;
    private final Map<ResourceLocation, ResourceLocation> aliasMappings;
    private final Map<ResourceLocation, List<ResourceLocation>> aliasConflicts;
    private final Map<String, List<StageDefinition>> stagesByGroup;
    private final List<StageDefinition> orderedStages;

    private WorldAwakenedStageRegistry(
            Map<ResourceLocation, StageDefinition> stagesById,
            Map<ResourceLocation, ResourceLocation> aliasMappings,
            Map<ResourceLocation, List<ResourceLocation>> aliasConflicts,
            Map<String, List<StageDefinition>> stagesByGroup,
            List<StageDefinition> orderedStages) {
        this.stagesById = stagesById;
        this.aliasMappings = aliasMappings;
        this.aliasConflicts = aliasConflicts;
        this.stagesByGroup = stagesByGroup;
        this.orderedStages = orderedStages;
    }

    public static WorldAwakenedStageRegistry empty() {
        return new WorldAwakenedStageRegistry(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                List.of());
    }

    public static WorldAwakenedStageRegistry from(Map<ResourceLocation, StageDefinition> stageDefinitions) {
        if (stageDefinitions.isEmpty()) {
            return empty();
        }

        List<StageDefinition> ordered = stageDefinitions.values().stream()
                .sorted(DISPLAY_ORDER)
                .toList();

        Map<ResourceLocation, StageDefinition> byId = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> aliasToCanonical = new LinkedHashMap<>();
        Map<ResourceLocation, LinkedHashSet<ResourceLocation>> conflictCandidates = new LinkedHashMap<>();
        Map<String, List<StageDefinition>> byGroup = new LinkedHashMap<>();

        for (StageDefinition stage : ordered) {
            byId.put(stage.id(), stage);
            stage.progressionGroup().ifPresent(group -> byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(stage));
        }

        for (StageDefinition stage : ordered) {
            for (ResourceLocation alias : stage.aliases()) {
                if (alias.equals(stage.id())) {
                    continue;
                }
                ResourceLocation existing = aliasToCanonical.putIfAbsent(alias, stage.id());
                if (existing != null && !existing.equals(stage.id())) {
                    LinkedHashSet<ResourceLocation> collisions = conflictCandidates.computeIfAbsent(alias, ignored -> new LinkedHashSet<>());
                    collisions.add(existing);
                    collisions.add(stage.id());
                }
            }
        }

        Map<ResourceLocation, List<ResourceLocation>> frozenConflicts = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, LinkedHashSet<ResourceLocation>> entry : conflictCandidates.entrySet()) {
            List<ResourceLocation> ids = entry.getValue().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList();
            frozenConflicts.put(entry.getKey(), ids);
        }

        Map<String, List<StageDefinition>> frozenGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<StageDefinition>> entry : byGroup.entrySet()) {
            frozenGroups.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return new WorldAwakenedStageRegistry(
                Map.copyOf(byId),
                Map.copyOf(aliasToCanonical),
                Map.copyOf(frozenConflicts),
                Map.copyOf(frozenGroups),
                List.copyOf(ordered));
    }

    public Optional<ResourceLocation> resolveCanonicalId(ResourceLocation requestedStageId) {
        if (requestedStageId == null) {
            return Optional.empty();
        }
        if (stagesById.containsKey(requestedStageId)) {
            return Optional.of(requestedStageId);
        }
        return Optional.ofNullable(aliasMappings.get(requestedStageId));
    }

    public Optional<StageDefinition> definition(ResourceLocation canonicalStageId) {
        if (canonicalStageId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(stagesById.get(canonicalStageId));
    }

    public Optional<StageDefinition> resolveDefinition(ResourceLocation requestedStageId) {
        return resolveCanonicalId(requestedStageId).flatMap(this::definition);
    }

    public List<StageDefinition> orderedStages() {
        return orderedStages;
    }

    public List<StageDefinition> stagesInGroup(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return List.of();
        }
        return stagesByGroup.getOrDefault(groupId, List.of());
    }

    public Set<ResourceLocation> canonicalStageIds() {
        return stagesById.keySet();
    }

    public Map<ResourceLocation, ResourceLocation> aliasMappings() {
        return aliasMappings;
    }

    public Map<ResourceLocation, List<ResourceLocation>> aliasConflicts() {
        return aliasConflicts;
    }

    public Set<ResourceLocation> inactiveStageIds(Collection<ResourceLocation> unlockedStages) {
        if (unlockedStages == null || unlockedStages.isEmpty()) {
            return Set.of();
        }
        return unlockedStages.stream()
                .filter(stageId -> !stagesById.containsKey(stageId))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}


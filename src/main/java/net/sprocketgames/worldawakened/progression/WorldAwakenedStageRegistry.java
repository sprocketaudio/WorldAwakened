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
    private final Map<ResourceLocation, ResourceLocation> canonicalIdsByAlias;
    private final Map<String, List<StageDefinition>> stagesByGroup;
    private final List<StageDefinition> orderedStages;

    private WorldAwakenedStageRegistry(
            Map<ResourceLocation, StageDefinition> stagesById,
            Map<ResourceLocation, ResourceLocation> canonicalIdsByAlias,
            Map<String, List<StageDefinition>> stagesByGroup,
            List<StageDefinition> orderedStages) {
        this.stagesById = stagesById;
        this.canonicalIdsByAlias = canonicalIdsByAlias;
        this.stagesByGroup = stagesByGroup;
        this.orderedStages = orderedStages;
    }

    public static WorldAwakenedStageRegistry empty() {
        return new WorldAwakenedStageRegistry(
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
        Map<ResourceLocation, Set<ResourceLocation>> aliasOwners = new LinkedHashMap<>();
        Map<String, List<StageDefinition>> byGroup = new LinkedHashMap<>();

        for (StageDefinition stage : ordered) {
            byId.put(stage.id(), stage);
            for (ResourceLocation alias : stage.aliases()) {
                if (alias != null) {
                    aliasOwners.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(stage.id());
                }
            }
            stage.progressionGroup().ifPresent(group -> byGroup.computeIfAbsent(group, ignored -> new ArrayList<>()).add(stage));
        }

        Map<String, List<StageDefinition>> frozenGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<StageDefinition>> entry : byGroup.entrySet()) {
            frozenGroups.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        aliasOwners.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .filter(entry -> !byId.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue().size() == 1)
                .forEach(entry -> aliases.put(entry.getKey(), entry.getValue().iterator().next()));

        return new WorldAwakenedStageRegistry(
                Map.copyOf(byId),
                Map.copyOf(aliases),
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
        return Optional.ofNullable(canonicalIdsByAlias.get(requestedStageId));
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


    public Set<ResourceLocation> inactiveStageIds(Collection<ResourceLocation> unlockedStages) {
        if (unlockedStages == null || unlockedStages.isEmpty()) {
            return Set.of();
        }
        return unlockedStages.stream()
                .filter(stageId -> resolveCanonicalId(stageId).isEmpty())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Resolves the gameplay-active stage in every progression group without
     * conflating the persisted unlock history with the current progression
     * context. Ungrouped stages are independent and therefore remain active
     * while unlocked. A missing saved group cursor is a legacy-state fallback
     * only; it is resolved within that group, never across all stages.
     */
    public Set<ResourceLocation> activeStageIds(
            Collection<ResourceLocation> unlockedStages,
            Map<String, ResourceLocation> activeGroupStages) {
        if (unlockedStages == null || unlockedStages.isEmpty()) {
            return Set.of();
        }
        Set<ResourceLocation> unlocked = unlockedStages.stream()
                .map(this::resolveCanonicalId)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ResourceLocation> cursors = activeGroupStages == null
                ? Map.of()
                : activeGroupStages.entrySet().stream()
                        .map(entry -> resolveCanonicalId(entry.getValue())
                                .map(canonical -> Map.entry(entry.getKey(), canonical)))
                        .flatMap(Optional::stream)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
        LinkedHashSet<ResourceLocation> active = new LinkedHashSet<>();

        for (ResourceLocation stageId : unlocked) {
            StageDefinition stage = stagesById.get(stageId);
            if (stage != null && stage.progressionGroup().isEmpty()) {
                active.add(stageId);
            }
        }
        for (Map.Entry<String, List<StageDefinition>> entry : stagesByGroup.entrySet()) {
            ResourceLocation cursor = cursors.get(entry.getKey());
            if (cursor != null && unlocked.contains(cursor) && stagesById.containsKey(cursor)) {
                active.add(cursor);
                continue;
            }
            entry.getValue().stream()
                    .filter(stage -> unlocked.contains(stage.id()))
                    .max(DISPLAY_ORDER)
                    .map(StageDefinition::id)
                    .ifPresent(active::add);
        }
        return Set.copyOf(active);
    }
}


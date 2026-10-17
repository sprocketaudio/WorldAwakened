package net.sprocketgames.worldawakened.data.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;

public final class WorldAwakenedMutationPoolValidation {
    private WorldAwakenedMutationPoolValidation() {}

    public static List<String> validate(
            MutationPoolDefinition pool,
            Map<ResourceLocation, MobMutatorDefinition> knownMutators) {
        List<String> errors = new ArrayList<>();
        if (pool.weight() < 1) errors.add("Pool selection weight must be at least 1.");
        validateChance("Mutation chance", pool.mutationChance(), errors);
        validateChance("Additional mutation chance", pool.additionalMutationChance(), errors);
        if (pool.maxMutatorsPerEntity() < 1) errors.add("Maximum mutations per mob must be at least 1.");
        if (pool.mutators().isEmpty()) errors.add("At least one mutator is required.");

        LinkedHashSet<ResourceLocation> poolMutators = new LinkedHashSet<>();
        for (JsonElement entry : pool.mutators()) {
            ResourceLocation id = mutatorId(entry);
            if (id == null || !knownMutators.containsKey(id)) errors.add("Unknown mutator reference: " + entry);
            else if (!poolMutators.add(id)) errors.add("Duplicate mutator reference: " + id + ".");
            if (mutatorWeight(entry) < 1) {
                errors.add("Mutator weights must be at least 1.");
            }
        }

        Set<ResourceLocation> combinationIds = new HashSet<>();
        for (MutationPoolDefinition.CuratedCombination combination : pool.rareCombinations()) {
            String prefix = "Rare combination " + combination.id() + ": ";
            if (!combinationIds.add(combination.id())) errors.add(prefix + "ID is duplicated.");
            if (combination.weight() < 1) errors.add(prefix + "weight must be at least 1.");
            if (combination.mutators().size() < 2) errors.add(prefix + "at least two distinct mutators are required.");
            if (combination.mutators().size() > pool.maxMutatorsPerEntity()) {
                errors.add(prefix + "contains more mutators than max_mutators_per_entity.");
            }
            LinkedHashSet<ResourceLocation> distinct = new LinkedHashSet<>(combination.mutators());
            if (distinct.size() != combination.mutators().size()) errors.add(prefix + "duplicate mutators are not allowed.");
            for (ResourceLocation mutatorId : distinct) {
                if (!poolMutators.contains(mutatorId)) errors.add(prefix + mutatorId + " is not in the parent pool.");
            }
            if (!pool.eligibleEntities().isEmpty()) {
                for (ResourceLocation entityId : combination.eligibleEntities()) {
                    if (!pool.eligibleEntities().contains(entityId)) {
                        errors.add(prefix + entityId + " is outside the parent pool's eligible entities.");
                    }
                }
            }
            validateCompatibility(prefix, distinct, knownMutators, errors);
        }
        if (pool.combinationMode() == MutationPoolDefinition.CombinationMode.CURATED
                && pool.maxMutatorsPerEntity() > 1 && pool.rareCombinations().isEmpty()) {
            errors.add("Curated combination mode requires at least one rare combination.");
        }
        return List.copyOf(errors);
    }

    private static void validateChance(String label, double chance, List<String> errors) {
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            errors.add(label + " must be between 0% and 100%.");
        }
    }

    private static ResourceLocation mutatorId(JsonElement entry) {
        if (entry == null) return null;
        if (entry.isJsonPrimitive()) return ResourceLocation.tryParse(entry.getAsString());
        if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
            return ResourceLocation.tryParse(entry.getAsJsonObject().get("id").getAsString());
        }
        return null;
    }

    private static int mutatorWeight(JsonElement entry) {
        return entry != null && entry.isJsonObject() && entry.getAsJsonObject().has("weight")
                ? entry.getAsJsonObject().get("weight").getAsInt() : 1;
    }

    private static void validateCompatibility(
            String prefix,
            Set<ResourceLocation> mutatorIds,
            Map<ResourceLocation, MobMutatorDefinition> knownMutators,
            List<String> errors) {
        List<MobMutatorDefinition> definitions = mutatorIds.stream().map(knownMutators::get).filter(java.util.Objects::nonNull).toList();
        Map<String, Integer> groupCounts = new HashMap<>();
        Map<String, Integer> groupLimits = new HashMap<>();
        for (MobMutatorDefinition definition : definitions) {
            for (ResourceLocation other : mutatorIds) {
                if (!other.equals(definition.id()) && definition.exclusiveWith().contains(other)) {
                    errors.add(prefix + definition.id() + " is exclusive with " + other + ".");
                }
            }
            definition.stackingGroup().ifPresent(group -> {
                String normalized = group.toLowerCase(java.util.Locale.ROOT);
                groupCounts.merge(normalized, 1, Integer::sum);
                groupLimits.merge(normalized, Math.max(1, definition.maxStackCount()), Math::min);
            });
        }
        groupCounts.forEach((group, count) -> {
            int limit = groupLimits.getOrDefault(group, 1);
            if (count > limit) errors.add(prefix + "stacking group " + group + " exceeds its limit of " + limit + ".");
        });
    }
}

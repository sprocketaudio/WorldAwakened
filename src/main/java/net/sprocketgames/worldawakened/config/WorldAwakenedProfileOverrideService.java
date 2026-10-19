package net.sprocketgames.worldawakened.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.sprocketgames.worldawakened.WorldAwakenedMod;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.data.validation.WorldAwakenedMutationPoolValidation;

public final class WorldAwakenedProfileOverrideService {
    public static final int MAX_MUTATORS_PER_ENTITY = 8;
    private static final AtomicReference<State> ACTIVE = new AtomicReference<>(State.empty());
    private static final AtomicReference<Set<ResourceLocation>> LIVING_ENTITY_IDS = new AtomicReference<>(Set.of());

    private WorldAwakenedProfileOverrideService() {}

    public static synchronized boolean bind(MinecraftServer server) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(server.overworld());
        ACTIVE.set(state(data));
        java.util.LinkedHashSet<ResourceLocation> living = new java.util.LinkedHashSet<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    if (isLivingType(server.overworld(), entry.getValue())) living.add(entry.getKey().location());
                });
        LIVING_ENTITY_IDS.set(Set.copyOf(living));
        return !ACTIVE.get().isEmpty();
    }

    public static synchronized void unbind() { ACTIVE.set(State.empty()); LIVING_ENTITY_IDS.set(Set.of()); }
    public static State state() { return ACTIVE.get(); }
    public static Set<ResourceLocation> livingEntityIds() { return LIVING_ENTITY_IDS.get(); }

    public static WorldAwakenedDatapackSnapshot overlay(WorldAwakenedDatapackSnapshot source) {
        State authored = ACTIVE.get();
        if (authored.isEmpty()) return source;
        LinkedHashMap<ResourceLocation, StageDefinition> stages = new LinkedHashMap<>(source.data().stages());
        stages.putAll(authored.stages());
        LinkedHashMap<ResourceLocation, TriggerRuleDefinition> triggers = new LinkedHashMap<>(source.data().triggerRules());
        triggers.putAll(authored.triggerRules());
        LinkedHashMap<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>(source.data().mutationPools());
        pools.putAll(authored.mutationPools());
        compilePhaseMutationConfigurations(pools, authored.phaseMutationConfigurations(), stages);
        WorldAwakenedCompiledData data = source.data();
        var rules = merged(data.rules(), authored.rules());
        var rewards = merged(data.ascensionRewards(), authored.ascensionRewards());
        var offers = merged(data.ascensionOffers(), authored.ascensionOffers());
        var mutators = merged(data.mobMutators(), authored.mobMutators());
        var loot = merged(data.lootProfiles(), authored.lootProfiles());
        var invasions = merged(data.invasionProfiles(), authored.invasionProfiles());
        var integrations = merged(data.integrationProfiles(), authored.integrationProfiles());
        var bossFlags = merged(data.entityBossFlags(), authored.entityBossFlags());
        return new WorldAwakenedDatapackSnapshot(source.generation(), Instant.now(), new WorldAwakenedCompiledData(
                Map.copyOf(stages), Map.copyOf(triggers), rules, rewards, offers,
                mutators, Map.copyOf(pools), loot, invasions, integrations, bossFlags,
                data.rarities(), data.configuredEffects(), data.effectGenerationPools(),
                net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier.fromMaps(List.copyOf(bossFlags.values()))),
                source.validationSummary());
    }

    private static <T> Map<ResourceLocation, T> merged(Map<ResourceLocation, T> source, Map<ResourceLocation, T> authored) {
        if (authored.isEmpty()) return source;
        LinkedHashMap<ResourceLocation, T> result = new LinkedHashMap<>(source); result.putAll(authored); return Map.copyOf(result);
    }

    public static List<String> validate(MinecraftServer server, MutationPoolDefinition candidate,
            WorldAwakenedDatapackSnapshot source) {
        List<String> errors = new ArrayList<>();
        List<String> poolErrors = WorldAwakenedMutationPoolValidation.validate(candidate,
                merged(source.data().mobMutators(), ACTIVE.get().mobMutators()));
        for (String error : poolErrors) {
            if (!candidate.enabled() && candidate.mutators().isEmpty()
                    && error.equals("At least one mutator is required.")) continue;
            errors.add(error);
        }
        int maximum = candidate.maxMutatorsPerEntity();
        if (maximum < 1 || maximum > MAX_MUTATORS_PER_ENTITY) {
            errors.add("Maximum mutations per mob must be between 1 and " + MAX_MUTATORS_PER_ENTITY + ".");
        }
        for (ResourceLocation entityId : candidate.eligibleEntities()) {
            if (!LIVING_ENTITY_IDS.get().contains(entityId)) {
                errors.add("Unknown or non-living entity: " + entityId);
            }
        }
        for (ResourceLocation dimensionId : candidate.eligibleDimensions()) {
            if (server.levelKeys().stream().noneMatch(key -> key.location().equals(dimensionId))) {
                errors.add("Dimension is not available on this server: " + dimensionId);
            }
        }
        return List.copyOf(errors);
    }

    public static synchronized ApplyResult apply(ServerLevel level, long expectedRevision,
            MutationPoolDefinition candidate, WorldAwakenedDatapackSnapshot source) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(state(data));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed pool and try again.");
        }
        List<String> errors = new ArrayList<>(validate(level.getServer(), candidate, source));
        Map<ResourceLocation, StageDefinition> stages = data.stages();
        Map<ResourceLocation, TriggerRuleDefinition> triggers = data.triggerRules();
        Map<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>(data.mutationPools());
        pools.put(candidate.id(), candidate);
        errors.addAll(validateAuthoring(level.getServer(), stages, triggers, pools,
                data.phaseMutationConfigurations(), data.mobMutators(), source));
        if (!errors.isEmpty()) return new ApplyResult(false, String.join(" ", errors.stream().distinct().toList()));
        data.replaceAll(stages, triggers, pools);
        ACTIVE.set(state(data));
        return new ApplyResult(true, "Mutation pool applied successfully.");
    }

    public static synchronized ApplyResult revert(ServerLevel level, long expectedRevision, ResourceLocation id,
            WorldAwakenedDatapackSnapshot source) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(state(data));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed values and try again.");
        }
        Map<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>(data.mutationPools());
        if (pools.remove(id) == null) return new ApplyResult(false, "This pool has no world-authored changes.");
        data.replaceAll(data.stages(), data.triggerRules(), pools);
        ACTIVE.set(state(data));
        return new ApplyResult(true, source.data().mutationPools().containsKey(id)
                ? "Mutation pool reverted to its source definition." : "World-authored mutation pool deleted.");
    }

    public static synchronized ApplyResult applyAuthoring(ServerLevel level, long expectedRevision,
            Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> phaseMutationConfigurations,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition> rewards,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition> offers,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> mutators,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.LootProfileDefinition> lootProfiles,
            WorldAwakenedDatapackSnapshot source) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(state(data));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed values and try again.");
        }
        List<String> candidateErrors = validateAuthoring(level.getServer(), stages, triggers, pools,
                phaseMutationConfigurations, mutators, source);
        Set<String> existingErrors = Set.copyOf(validateAuthoring(level.getServer(), data.stages(),
                data.triggerRules(), data.mutationPools(), data.phaseMutationConfigurations(), data.mobMutators(), source));
        List<String> errors = new ArrayList<>(candidateErrors.stream()
                .filter(error -> !existingErrors.contains(error)).toList());
        if (!errors.isEmpty()) return new ApplyResult(false, String.join(" ", errors));
        List<String> ascensionErrors = validateAscensionAuthoring(rewards, offers, triggers, stages, source);
        Set<String> existingAscensionErrors = Set.copyOf(validateAscensionAuthoring(data.ascensionRewards(),
                data.ascensionOffers(), data.triggerRules(), data.stages(), source));
        errors.addAll(ascensionErrors.stream().filter(error -> !existingAscensionErrors.contains(error)).toList());
        List<String> lootErrors = validateLootAuthoring(lootProfiles);
        Set<String> existingLootErrors = Set.copyOf(validateLootAuthoring(data.lootProfiles()));
        errors.addAll(lootErrors.stream().filter(error -> !existingLootErrors.contains(error)).toList());
        if (!errors.isEmpty()) return new ApplyResult(false, String.join(" ", errors));
        data.replaceAuthoring(stages, triggers, pools, phaseMutationConfigurations, rewards, offers, mutators, lootProfiles);
        ACTIVE.set(state(data));
        boolean retainedExistingError = candidateErrors.stream().anyMatch(existingErrors::contains)
                || ascensionErrors.stream().anyMatch(existingAscensionErrors::contains);
        if (retainedExistingError) {
            WorldAwakenedMod.LOGGER.warn("Applied native-editor changes while retaining pre-existing validation issues: {}",
                    candidateErrors.stream().filter(existingErrors::contains).distinct().toList());
        }
        return new ApplyResult(true, retainedExistingError
                ? "Profile applied. An older configuration still needs review."
                : "Gameplay profile changes applied successfully.");
    }

    /** Compatibility overload for existing native-editor callers that do not change mutators. */
    public static synchronized ApplyResult applyAuthoring(ServerLevel level, long expectedRevision,
            Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition> rewards,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition> offers,
            WorldAwakenedDatapackSnapshot source) {
        return applyAuthoring(level, expectedRevision, stages, triggers, pools,
                WorldAwakenedProfileOverrideSavedData.get(level).phaseMutationConfigurations(), rewards, offers,
                WorldAwakenedProfileOverrideSavedData.get(level).mobMutators(),
                WorldAwakenedProfileOverrideSavedData.get(level).lootProfiles(), source);
    }

    /** Compatibility overload for callers which do not edit loot profiles. */
    public static synchronized ApplyResult applyAuthoring(ServerLevel level, long expectedRevision,
            Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition> rewards,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition> offers,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> mutators,
            WorldAwakenedDatapackSnapshot source) {
        return applyAuthoring(level, expectedRevision, stages, triggers, pools,
                WorldAwakenedProfileOverrideSavedData.get(level).phaseMutationConfigurations(), rewards, offers, mutators,
                WorldAwakenedProfileOverrideSavedData.get(level).lootProfiles(), source);
    }

    private static List<String> validateLootAuthoring(
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.LootProfileDefinition> profiles) {
        List<String> errors = new ArrayList<>();
        for (var profile : profiles.values()) {
            if (profile.replaceMode() != net.sprocketgames.worldawakened.data.definition.LootReplaceMode.INJECT
                    && profile.replaceMode() != net.sprocketgames.worldawakened.data.definition.LootReplaceMode.ADD_BONUS_POOL) {
                errors.add("Native Loot profile " + profile.id() + " must preserve existing loot.");
            }
            if (profile.authoring().itemChance() < 0 || profile.authoring().itemChance() > 1) {
                errors.add("Loot profile " + profile.id() + " has an item chance outside 0-100%.");
            }
            if (profile.profileType() == net.sprocketgames.worldawakened.data.definition.LootProfileType.MOB
                    && profile.authoring().targetEntities().isEmpty()) {
                errors.add("Mob Loot profile " + profile.id() + " needs at least one mob.");
            }
            if (profile.profileType() == net.sprocketgames.worldawakened.data.definition.LootProfileType.CHEST
                    && profile.targetLootTables().isEmpty()) {
                errors.add("Chest Loot profile " + profile.id() + " needs at least one chest loot table.");
            }
            boolean hasXp = profile.authoring().bonusXp().map(value -> value.enabled()).orElse(false);
            if (profile.entries().isEmpty() && !hasXp) {
                errors.add("Loot profile " + profile.id() + " needs an item or bonus XP reward.");
            }
            profile.authoring().bonusXp().ifPresent(xp -> {
                if (profile.profileType() != net.sprocketgames.worldawakened.data.definition.LootProfileType.MOB)
                    errors.add("Only Mob Loot profiles can grant bonus XP.");
                if (xp.minimum() < 0 || xp.maximum() < xp.minimum() || xp.chance() < 0 || xp.chance() > 1
                        || !Double.isFinite(xp.multiplier()) || xp.multiplier() < 0)
                    errors.add("Loot profile " + profile.id() + " has invalid bonus XP settings.");
            });
            for (JsonElement raw : profile.entries()) {
                if (!raw.isJsonObject() || !raw.getAsJsonObject().has("item")) {
                    errors.add("Loot profile " + profile.id() + " has an invalid item entry.");
                }
            }
        }
        return List.copyOf(errors);
    }

    private static boolean hasOfferGrantTrigger(java.util.Collection<TriggerRuleDefinition> triggers,
            ResourceLocation offerId) {
        String expected = offerId.toString();
        return triggers.stream().flatMap(trigger -> trigger.actions().stream())
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .anyMatch(action -> readActionOffer(action).map(expected::equals).orElse(false));
    }

    private static List<String> validateAscensionAuthoring(
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition> rewards,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition> offers,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, StageDefinition> stages,
            WorldAwakenedDatapackSnapshot source) {
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> availableRewards = new java.util.LinkedHashSet<>(source.data().ascensionRewards().keySet());
        availableRewards.addAll(rewards.keySet());
        for (var reward : rewards.values()) {
            var componentValidation = net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentValidation
                    .validate(reward.components());
            componentValidation.issues().forEach(issue -> errors.add(
                    "Ascension choice " + reward.id() + " is invalid: " + issue.detail() + "."));
            validateNativeAscensionChoice(reward, errors);
        }
        Set<ResourceLocation> availableStages = new java.util.LinkedHashSet<>(source.data().stages().keySet());
        availableStages.addAll(stages.keySet());
        for (var offer : offers.values()) {
            if (!offer.enabled()) continue;
            if (offer.triggerConditions().isEmpty() && offer.stageFilters().isEmpty()
                    && !hasOfferGrantTrigger(triggers.values(), offer.id()))
                errors.add("Ascension offer " + offer.id() + " needs a World Phase or custom conditions.");
            for (ResourceLocation stageId : stageReferences(offer.stageFilters().stream().toList()))
                if (!availableStages.contains(stageId)) errors.add("Ascension offer " + offer.id() + " references missing stage " + stageId + ".");
            for (ResourceLocation rewardId : offer.candidateRewards())
                if (!availableRewards.contains(rewardId)) errors.add("Ascension offer " + offer.id() + " references missing reward " + rewardId + ".");
        }
        return List.copyOf(errors);
    }

    private static void validateNativeAscensionChoice(
            net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition reward,
            List<String> errors) {
        if (!reward.id().getNamespace().equals("worldawakened")
                || !reward.id().getPath().startsWith("world/ascension_reward_")) return;
        if (reward.components().isEmpty() || reward.components().size() > 3) {
            errors.add("Native Ascension choice " + reward.id() + " must contain between one and three supported effects.");
            return;
        }
        Set<net.minecraft.resources.ResourceLocation> seen = new java.util.LinkedHashSet<>();
        for (var component : reward.components()) {
            if (!seen.add(component.type())) {
                errors.add("Native Ascension choice " + reward.id() + " repeats effect " + component.type() + ".");
                continue;
            }
            double maximum = switch (component.type().getPath()) {
                case "max_health_bonus" -> 40D;
                case "armor_bonus", "armor_toughness_bonus", "attack_damage_bonus" -> 20D;
                case "movement_speed_bonus" -> .5D;
                case "knockback_resistance_bonus" -> 1D;
                case "luck_bonus" -> 10D;
                case "fire_resistance_passive", "night_vision_passive" -> 0D;
                default -> -1D;
            };
            if (maximum < 0) {
                errors.add("Native Ascension choice " + reward.id() + " uses an effect without a live editor handler.");
                continue;
            }
            if (maximum == 0) continue;
            if (!component.parameters().has("amount") || !component.parameters().get("amount").isJsonPrimitive()
                    || !component.parameters().getAsJsonPrimitive("amount").isNumber()) continue;
            double amount = component.parameters().get("amount").getAsDouble();
            if (!Double.isFinite(amount) || amount <= 0 || amount > maximum)
                errors.add("Native Ascension choice " + reward.id() + " has an effect amount outside the safe editor range.");
        }
    }

    private static Optional<String> readActionOffer(JsonObject action) {
        if (!readString(action, "type", "").endsWith("grant_ascension_offer")
                || !action.has("parameters") || !action.get("parameters").isJsonObject()) return Optional.empty();
        JsonObject parameters = action.getAsJsonObject("parameters");
        return parameters.has("offer") ? Optional.of(parameters.get("offer").getAsString()) : Optional.empty();
    }

    private static String readString(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    public static synchronized ApplyResult applyAuthoring(ServerLevel level, long expectedRevision,
            Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, MutationPoolDefinition> pools,
            WorldAwakenedDatapackSnapshot source) {
        return applyAuthoring(level, expectedRevision, stages, triggers, pools,
                WorldAwakenedProfileOverrideSavedData.get(level).phaseMutationConfigurations(),
                WorldAwakenedProfileOverrideSavedData.get(level).ascensionRewards(),
                WorldAwakenedProfileOverrideSavedData.get(level).ascensionOffers(),
                WorldAwakenedProfileOverrideSavedData.get(level).mobMutators(),
                WorldAwakenedProfileOverrideSavedData.get(level).lootProfiles(), source);
    }

    public static synchronized ApplyResult replaceProfile(ServerLevel level, long expectedRevision, String kind,
            Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggers,
            Map<ResourceLocation, MutationPoolDefinition> pools) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(state(data));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed values and try again.");
        }
        data.clearCopiedDefinitions();
        data.replaceProfile(kind, stages, triggers, pools);
        ACTIVE.set(state(data));
        return new ApplyResult(true, "Active gameplay profile changed successfully.");
    }

    public static synchronized ApplyResult copyProfile(ServerLevel level, long expectedRevision,
            WorldAwakenedCompiledData copied) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(state(data));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed values and try again.");
        }
        data.replaceCopiedProfile("standard_copy", copied); ACTIVE.set(state(data));
        return new ApplyResult(true, "An independent editable copy of Standard is now active.");
    }

    public static synchronized ApplyResult startEmptyProfile(ServerLevel level, long expectedRevision,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> availableMutators) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) { ACTIVE.set(state(data)); return new ApplyResult(false,
                "The profile changed on the server. Review the refreshed values and try again."); }
        data.replaceEmptyProfile(availableMutators); ACTIVE.set(state(data));
        return new ApplyResult(true, "A blank custom gameplay profile is now active.");
    }

    public static List<String> validateAuthoring(MinecraftServer server,
            Map<ResourceLocation, StageDefinition> authoredStages,
            Map<ResourceLocation, TriggerRuleDefinition> authoredTriggers,
            Map<ResourceLocation, MutationPoolDefinition> authoredPools,
            Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> phaseMutationConfigurations,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> authoredMutators,
            WorldAwakenedDatapackSnapshot source) {
        List<String> errors = new ArrayList<>();
        LinkedHashMap<ResourceLocation, StageDefinition> stages = new LinkedHashMap<>(source.data().stages());
        stages.putAll(authoredStages);
        LinkedHashMap<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>(source.data().mutationPools());
        pools.putAll(authoredPools);
        LinkedHashMap<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> mutators =
                new LinkedHashMap<>(source.data().mobMutators());
        mutators.putAll(authoredMutators);
        for (var mutator : authoredMutators.values()) {
            if (mutator.displayName() == null || mutator.displayName().isJsonNull())
                errors.add("Mutator " + mutator.id() + " needs a display name.");
            if (mutator.weight() < 1) errors.add("Mutator " + mutator.id() + " needs a positive weight.");
            if (mutator.components().isEmpty()) errors.add("Mutator " + mutator.id() + " needs at least one component.");
            net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentValidation
                    .validate(mutator.components()).issues().forEach(issue -> errors.add(
                            "Mutator " + mutator.id() + " is invalid: " + issue.detail() + "."));
        }
        for (StageDefinition stage : authoredStages.values()) {
            if (stage.displayName() == null || stage.displayName().isJsonNull()) errors.add("Stage " + stage.id() + " needs a display name.");
            if (stage.progressionGroup().isPresent() && stage.progressionGroup().get().isBlank()) errors.add("Stage " + stage.id() + " has an empty progression group.");
        }
        for (TriggerRuleDefinition trigger : authoredTriggers.values()) {
            boolean stageAction = !stageReferences(trigger.actions()).isEmpty();
            boolean unlocksKnownStage = trigger.actions().stream().anyMatch(action -> referencesKnownStage(action, stages.keySet()));
            if (stageAction && !unlocksKnownStage) errors.add("Trigger " + trigger.id() + " must target an available stage.");
            if (stageAction) validateAuthoredTrigger(trigger, stages.keySet(), errors);
        }
        for (MutationPoolDefinition pool : authoredPools.values()) {
            errors.addAll(validate(server, pool, mutators));
            for (ResourceLocation stageId : stageReferences(pool.conditions())) if (!stages.containsKey(stageId))
                errors.add("Mutation pool " + pool.id() + " references missing stage " + stageId + ".");
        }
        for (WorldAwakenedPhaseMutationConfigurationDefinition configuration : phaseMutationConfigurations.values()) {
            if (configuration.displayName().isBlank()) {
                errors.add("Phase mutation configuration " + configuration.id() + " needs a display name.");
            }
            for (WorldAwakenedPhaseMutationConfigurationDefinition.Tier assignment : configuration.tiers()) {
                if (!stages.containsKey(assignment.stageId())) {
                    errors.add("Phase mutation configuration " + configuration.id()
                            + " references missing phase " + assignment.stageId() + ".");
                }
                if (!pools.containsKey(assignment.poolId())) {
                    errors.add("Phase mutation configuration " + configuration.id()
                            + " references missing mutation pool " + assignment.poolId() + ".");
                }
            }
            validatePhaseConfigurations(configuration, pools, errors);
        }
        for (ResourceLocation previous : ACTIVE.get().stages().keySet()) {
            if (authoredStages.containsKey(previous) || source.data().stages().containsKey(previous)) continue;
            if (hasNonEditorDependency(previous, source, ACTIVE.get()))
                errors.add("Stage " + previous + " is still used by a rule, reward, Ascension offer, loot profile, world event, or integration.");
        }
        return List.copyOf(errors);
    }

    public static List<String> validateAuthoring(MinecraftServer server,
            Map<ResourceLocation, StageDefinition> authoredStages,
            Map<ResourceLocation, TriggerRuleDefinition> authoredTriggers,
            Map<ResourceLocation, MutationPoolDefinition> authoredPools,
            WorldAwakenedDatapackSnapshot source) {
        return validateAuthoring(server, authoredStages, authoredTriggers, authoredPools,
                ACTIVE.get().phaseMutationConfigurations(), ACTIVE.get().mobMutators(), source);
    }

    private static List<String> validate(MinecraftServer server, MutationPoolDefinition candidate,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> mutators) {
        List<String> errors = new ArrayList<>();
        List<String> poolErrors = WorldAwakenedMutationPoolValidation.validate(candidate, mutators);
        for (String error : poolErrors) {
            if (!candidate.enabled() && candidate.mutators().isEmpty() && error.equals("At least one mutator is required.")) continue;
            errors.add(error);
        }
        int maximum = candidate.maxMutatorsPerEntity();
        if (maximum < 1 || maximum > MAX_MUTATORS_PER_ENTITY) errors.add("Maximum mutations per mob must be between 1 and " + MAX_MUTATORS_PER_ENTITY + ".");
        for (ResourceLocation entityId : candidate.eligibleEntities()) if (!LIVING_ENTITY_IDS.get().contains(entityId)) errors.add("Unknown or non-living entity: " + entityId);
        for (ResourceLocation dimensionId : candidate.eligibleDimensions()) if (server.levelKeys().stream().noneMatch(key -> key.location().equals(dimensionId))) errors.add("Dimension is not available on this server: " + dimensionId);
        return List.copyOf(errors);
    }

    private static boolean hasNonEditorDependency(ResourceLocation stageId, WorldAwakenedDatapackSnapshot source, State authored) {
        return encodedContains(net.sprocketgames.worldawakened.data.definition.RuleDefinition.CODEC,
                        merged(source.data().rules(), authored.rules()).values(), stageId)
                || encodedContains(net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition.CODEC,
                        merged(source.data().ascensionRewards(), authored.ascensionRewards()).values(), stageId)
                || encodedContains(net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition.CODEC,
                        merged(source.data().ascensionOffers(), authored.ascensionOffers()).values(), stageId)
                || encodedContains(net.sprocketgames.worldawakened.data.definition.LootProfileDefinition.CODEC,
                        merged(source.data().lootProfiles(), authored.lootProfiles()).values(), stageId)
                || encodedContains(net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition.CODEC,
                        merged(source.data().invasionProfiles(), authored.invasionProfiles()).values(), stageId)
                || encodedContains(net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition.CODEC,
                        merged(source.data().integrationProfiles(), authored.integrationProfiles()).values(), stageId);
    }

    private static <T> boolean encodedContains(com.mojang.serialization.Codec<T> codec,
            java.util.Collection<T> definitions, ResourceLocation stageId) {
        String needle = "\"" + stageId + "\"";
        return definitions.stream().anyMatch(definition -> codec.encodeStart(JsonOps.INSTANCE, definition).result()
                .map(json -> json.toString().contains(needle)).orElse(false));
    }

    private static void validateAuthoredTrigger(TriggerRuleDefinition trigger, Set<ResourceLocation> stages,
            List<String> errors) {
        String type = trigger.triggerType().getNamespace().equals("worldawakened") ? trigger.triggerType().getPath() : "";
        String required = switch (type) {
            case "world_day_milestone" -> "world_day_gte";
            case "player_enter_dimension" -> "current_dimension";
            case "advancement_completed" -> "advancement_completed";
            case "entity_killed" -> "entity_type";
            case "boss_killed" -> "boss_killed";
            case "manual_debug" -> "manual_trigger";
            default -> null;
        };
        if (required == null) errors.add("Trigger " + trigger.id() + " uses an unsupported event type.");
        else if (!hasCondition(trigger.conditions(), required) && !type.equals("manual_debug"))
            errors.add("Trigger " + trigger.id() + " is missing its required " + required.replace('_', ' ') + " condition.");
        boolean playerEvent = type.equals("player_enter_dimension") || type.equals("advancement_completed")
                || type.equals("entity_killed") || type.equals("boss_killed");
        if (playerEvent && trigger.sourceScope() != net.sprocketgames.worldawakened.data.definition.SourceScope.PLAYER)
            errors.add("Trigger " + trigger.id() + " requires player progression context.");
        boolean editorConditionGroup = hasAction(trigger.actions(), "record_unlock_condition");
        if (type.equals("world_day_milestone")
                && trigger.sourceScope() != net.sprocketgames.worldawakened.data.definition.SourceScope.WORLD
                && !(editorConditionGroup
                && trigger.sourceScope() == net.sprocketgames.worldawakened.data.definition.SourceScope.PLAYER)
                && !(hasAction(trigger.actions(), "grant_ascension_offer")
                && trigger.sourceScope() == net.sprocketgames.worldawakened.data.definition.SourceScope.PLAYER))
            errors.add("Trigger " + trigger.id() + " requires world progression context.");
        for (ResourceLocation stageId : stageReferences(trigger.actions())) if (!stages.contains(stageId))
            errors.add("Trigger " + trigger.id() + " references missing stage " + stageId + ".");
    }

    private static boolean hasCondition(List<JsonElement> conditions, String path) {
        return conditions.stream().anyMatch(condition -> condition.isJsonObject() && condition.getAsJsonObject().has("type")
                && condition.getAsJsonObject().get("type").getAsString().equals("worldawakened:" + path));
    }

    private static boolean hasAction(List<JsonElement> actions, String path) {
        return actions.stream().anyMatch(action -> action.isJsonObject() && action.getAsJsonObject().has("type")
                && action.getAsJsonObject().get("type").getAsString().equals("worldawakened:" + path));
    }

    private static Set<ResourceLocation> stageReferences(List<JsonElement> nodes) {
        Set<ResourceLocation> result = new java.util.LinkedHashSet<>();
        nodes.forEach(node -> collectStageReferences(node, result)); return result;
    }

    private static void collectStageReferences(JsonElement node, Set<ResourceLocation> result) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonArray()) { node.getAsJsonArray().forEach(value -> collectStageReferences(value, result)); return; }
        if (!node.isJsonObject()) return;
        JsonObject object = node.getAsJsonObject();
        if (object.has("stage") && object.get("stage").isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(object.get("stage").getAsString()); if (id != null) result.add(id);
        }
        object.entrySet().forEach(entry -> collectStageReferences(entry.getValue(), result));
    }

    private static boolean referencesKnownStage(JsonElement action, Set<ResourceLocation> stages) {
        if (!action.isJsonObject()) return false;
        JsonElement parameters = action.getAsJsonObject().get("parameters");
        if (parameters == null || !parameters.isJsonObject() || !parameters.getAsJsonObject().has("stage")) return false;
        ResourceLocation id = ResourceLocation.tryParse(parameters.getAsJsonObject().get("stage").getAsString());
        return id != null && stages.contains(id);
    }

    /** Compiles editor-only phase configuration metadata into ordinary, hot-path-ready pool definitions. */
    static void compilePhaseMutationConfigurations(Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> tracks,
            Map<ResourceLocation, StageDefinition> stages) {
        for (WorldAwakenedPhaseMutationConfigurationDefinition track : tracks.values()) {
            if (isPhaseMutationConfiguration(track)) {
                compilePhaseConfigurations(pools, track, stages);
                continue;
            }
            List<WorldAwakenedPhaseMutationConfigurationDefinition.Tier> tiers = track.tiers().stream()
                    .sorted(java.util.Comparator.comparingInt((WorldAwakenedPhaseMutationConfigurationDefinition.Tier tier) ->
                            stages.containsKey(tier.stageId()) ? stages.get(tier.stageId()).sortIndex() : Integer.MAX_VALUE)
                            .thenComparing(tier -> tier.stageId().toString())).toList();
            for (int index = 0; index < tiers.size(); index++) {
                WorldAwakenedPhaseMutationConfigurationDefinition.Tier tier = tiers.get(index);
                MutationPoolDefinition pool = pools.get(tier.poolId());
                if (pool == null) continue;
                List<JsonElement> conditions = new ArrayList<>();
                for (JsonElement condition : pool.conditions()) {
                    if (!isStageGate(condition)) conditions.add(condition.deepCopy());
                }
                conditions.add(stageGate("stage_unlocked", tier.stageId()));
                // Lock against every later tier rather than only the next one. This makes
                // out-of-order milestones deterministic while cumulative stages remain unlocked.
                for (int higher = index + 1; higher < tiers.size(); higher++) {
                    conditions.add(stageGate("stage_locked", tiers.get(higher).stageId()));
                }
                List<ResourceLocation> dimensions = track.eligibleDimensions().isEmpty()
                        ? pool.eligibleDimensions() : track.eligibleDimensions();
                pools.put(pool.id(), new MutationPoolDefinition(pool.schemaVersion(), pool.id(),
                        pool.enabled() && track.enabled(), pool.selection(), pool.allowFromSpawner(),
                        pool.allowFromTrialSpawner(), List.copyOf(conditions), pool.stageFilters(),
                        pool.apotheosisTierFilters(), dimensions, pool.eligibleBiomes(),
                        pool.eligibleEntities(), pool.mutators()));
            }
        }
    }

    private static boolean isPhaseMutationConfiguration(WorldAwakenedPhaseMutationConfigurationDefinition track) {
        return track.id().equals(ResourceLocation.parse("worldawakened:world/phase_mutations"))
                || track.tiers().stream().anyMatch(tier -> tier.dimension().isPresent());
    }

    private static void validatePhaseConfigurations(WorldAwakenedPhaseMutationConfigurationDefinition track,
            Map<ResourceLocation, MutationPoolDefinition> pools, List<String> errors) {
        Map<String, List<WorldAwakenedPhaseMutationConfigurationDefinition.Tier>> groups = track.tiers().stream()
                .filter(WorldAwakenedPhaseMutationConfigurationDefinition.Tier::active)
                .filter(tier -> tier.dimension().isPresent())
                .collect(java.util.stream.Collectors.groupingBy(tier -> tier.stageId() + "|" + tier.dimension().orElseThrow()));
        for (List<WorldAwakenedPhaseMutationConfigurationDefinition.Tier> configurations : groups.values()) {
            long defaults = configurations.stream().filter(WorldAwakenedPhaseMutationConfigurationDefinition.Tier::defaultConfiguration).count();
            if (defaults > 1) errors.add("A phase and dimension may have only one Default Mobs configuration.");
            Set<ResourceLocation> specialised = new java.util.HashSet<>();
            for (WorldAwakenedPhaseMutationConfigurationDefinition.Tier tier : configurations) {
                if (tier.configurationName().isBlank()) errors.add("A mob configuration needs a name.");
                MutationPoolDefinition pool = pools.get(tier.poolId());
                if (pool == null || tier.defaultConfiguration() || !pool.enabled()) continue;
                if (pool.eligibleEntities().isEmpty()) {
                    errors.add("Specialised mob configuration " + tier.configurationName() + " must select at least one mob.");
                    continue;
                }
                for (ResourceLocation entity : pool.eligibleEntities()) if (!specialised.add(entity))
                    errors.add("Mob " + entity + " belongs to more than one specialised configuration in the same phase and dimension.");
            }
        }
    }

    private static void compilePhaseConfigurations(Map<ResourceLocation, MutationPoolDefinition> pools,
            WorldAwakenedPhaseMutationConfigurationDefinition track, Map<ResourceLocation, StageDefinition> stages) {
        Map<String, Set<ResourceLocation>> specialisedByGroup = new java.util.HashMap<>();
        for (WorldAwakenedPhaseMutationConfigurationDefinition.Tier tier : track.tiers()) {
            if (!tier.active() || tier.defaultConfiguration() || tier.dimension().isEmpty()) continue;
            MutationPoolDefinition pool = pools.get(tier.poolId());
            if (pool != null && pool.enabled()) specialisedByGroup.computeIfAbsent(
                    tier.stageId() + "|" + tier.dimension().orElseThrow(), ignored -> new java.util.HashSet<>())
                    .addAll(pool.eligibleEntities());
        }
        for (WorldAwakenedPhaseMutationConfigurationDefinition.Tier tier : track.tiers()) {
            MutationPoolDefinition pool = pools.get(tier.poolId());
            if (pool == null) continue;
            List<JsonElement> conditions = pool.conditions().stream().filter(condition -> !isStageGate(condition))
                    .map(JsonElement::deepCopy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            conditions.add(stageGate("stage_unlocked", tier.stageId()));
            StageDefinition tierStage = stages.get(tier.stageId());
            if (tierStage != null) stages.values().stream()
                    .filter(stage -> stage.sortIndex() > tierStage.sortIndex())
                    .filter(stage -> java.util.Objects.equals(stage.progressionGroup(), tierStage.progressionGroup()))
                    .sorted(java.util.Comparator.comparingInt(StageDefinition::sortIndex)
                            .thenComparing(stage -> stage.id().toString()))
                    .forEach(stage -> conditions.add(stageGate("stage_locked", stage.id())));
            List<ResourceLocation> dimensions = tier.dimension().map(List::of).orElse(pool.eligibleDimensions());
            List<ResourceLocation> entities = pool.eligibleEntities();
            Set<ResourceLocation> specialised = Set.of();
            if (tier.defaultConfiguration() && tier.dimension().isPresent()) {
                specialised = specialisedByGroup.getOrDefault(
                        tier.stageId() + "|" + tier.dimension().orElseThrow(), Set.of());
                entities = defaultEntitiesForRuntime(entities, specialised, LIVING_ENTITY_IDS.get());
            }
            boolean emptiedDefault = tier.defaultConfiguration() && entities.isEmpty() && !specialised.isEmpty();
            pools.put(pool.id(), new MutationPoolDefinition(pool.schemaVersion(), pool.id(),
                    pool.enabled() && track.enabled() && tier.active() && !emptiedDefault,
                    pool.selection(), pool.allowFromSpawner(),
                    pool.allowFromTrialSpawner(), List.copyOf(conditions), pool.stageFilters(),
                    pool.apotheosisTierFilters(), dimensions, pool.eligibleBiomes(), entities, pool.mutators()));
        }
    }

    static List<ResourceLocation> defaultEntitiesForRuntime(List<ResourceLocation> authored,
            Set<ResourceLocation> specialised, Set<ResourceLocation> eligibleUniverse) {
        if (specialised.isEmpty()) return authored;
        java.util.stream.Stream<ResourceLocation> candidates = authored.isEmpty()
                ? eligibleUniverse.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                : authored.stream();
        return candidates.filter(entity -> !specialised.contains(entity)).toList();
    }

    private static boolean isStageGate(JsonElement condition) {
        if (!condition.isJsonObject() || !condition.getAsJsonObject().has("type")) return false;
        String type = condition.getAsJsonObject().get("type").getAsString();
        return type.equals("worldawakened:stage_unlocked") || type.equals("worldawakened:stage_locked");
    }

    private static JsonElement stageGate(String type, ResourceLocation stageId) {
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "worldawakened:" + type);
        JsonObject parameters = new JsonObject();
        parameters.addProperty("stage", stageId.toString());
        condition.add("parameters", parameters);
        return condition;
    }

    // Phase-configuration compilation is restored below before retiring any previous representation.
    private static State state(WorldAwakenedProfileOverrideSavedData data) {
        return new State(data.revision(), data.profileKind(), data.stages(), data.triggerRules(), data.mutationPools(),
                data.phaseMutationConfigurations(),
                data.rules(), data.ascensionRewards(), data.ascensionOffers(), data.mobMutators(),
                data.lootProfiles(), data.invasionProfiles(), data.integrationProfiles(), data.entityBossFlags());
    }

    public static boolean isLivingType(ServerLevel level, EntityType<?> type) {
        Entity entity = null;
        try {
            entity = type.create(level);
            return entity instanceof LivingEntity;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (entity != null) entity.discard();
        }
    }

    public static ResourceLocation mutatorId(JsonElement entry) {
        if (entry == null) return null;
        if (entry.isJsonPrimitive()) return ResourceLocation.tryParse(entry.getAsString());
        if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
            return ResourceLocation.tryParse(entry.getAsJsonObject().get("id").getAsString());
        }
        return null;
    }

    public static int mutatorWeight(JsonElement entry) {
        return entry != null && entry.isJsonObject() && entry.getAsJsonObject().has("weight")
                ? entry.getAsJsonObject().get("weight").getAsInt() : 1;
    }

    public record State(long revision, String profileKind, Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, TriggerRuleDefinition> triggerRules,
            Map<ResourceLocation, MutationPoolDefinition> mutationPools,
            Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> phaseMutationConfigurations,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.RuleDefinition> rules,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition> ascensionRewards,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition> ascensionOffers,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition> mobMutators,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.LootProfileDefinition> lootProfiles,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition> invasionProfiles,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition> integrationProfiles,
            Map<ResourceLocation, net.sprocketgames.worldawakened.data.definition.EntityBossFlagMapDefinition> entityBossFlags) {
        public State {
            stages = Map.copyOf(stages); triggerRules = Map.copyOf(triggerRules);
            mutationPools = Map.copyOf(mutationPools); phaseMutationConfigurations = Map.copyOf(phaseMutationConfigurations);
            rules = Map.copyOf(rules); ascensionRewards = Map.copyOf(ascensionRewards); ascensionOffers = Map.copyOf(ascensionOffers);
            mobMutators = Map.copyOf(mobMutators); lootProfiles = Map.copyOf(lootProfiles);
            invasionProfiles = Map.copyOf(invasionProfiles); integrationProfiles = Map.copyOf(integrationProfiles);
            entityBossFlags = Map.copyOf(entityBossFlags);
        }
        public static State empty() { return new State(0L, "standard", Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
        public boolean isEmpty() { return stages.isEmpty() && triggerRules.isEmpty() && mutationPools.isEmpty()
                && phaseMutationConfigurations.isEmpty() && rules.isEmpty() && ascensionRewards.isEmpty() && ascensionOffers.isEmpty()
                && mobMutators.isEmpty() && lootProfiles.isEmpty() && invasionProfiles.isEmpty()
                && integrationProfiles.isEmpty() && entityBossFlags.isEmpty(); }
    }
    public record ApplyResult(boolean accepted, String message) {}
}

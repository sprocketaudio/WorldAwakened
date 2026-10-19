package net.sprocketgames.worldawakened.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.worldawakened.WorldAwakenedMod;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.EntityBossFlagMapDefinition;

public final class WorldAwakenedProfileOverrideSavedData extends SavedData {
    private static final String DATA_NAME = "worldawakened_profile_overrides";
    private static final SavedData.Factory<WorldAwakenedProfileOverrideSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedProfileOverrideSavedData::new,
            WorldAwakenedProfileOverrideSavedData::load);
    private static final Map<ServerLevel, WorldAwakenedProfileOverrideSavedData> TRANSIENT_FALLBACK =
            Collections.synchronizedMap(new WeakHashMap<>());
    private long revision;
    private String profileKind = "standard";
    private int discardedOverrideCount;
    private final Map<ResourceLocation, StageDefinition> stages = new LinkedHashMap<>();
    private final Map<ResourceLocation, TriggerRuleDefinition> triggerRules = new LinkedHashMap<>();
    private final Map<ResourceLocation, MutationPoolDefinition> mutationPools = new LinkedHashMap<>();
    private final Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> phaseMutationConfigurations = new LinkedHashMap<>();
    private final Map<ResourceLocation, RuleDefinition> rules = new LinkedHashMap<>();
    private final Map<ResourceLocation, AscensionRewardDefinition> ascensionRewards = new LinkedHashMap<>();
    private final Map<ResourceLocation, AscensionOfferDefinition> ascensionOffers = new LinkedHashMap<>();
    private final Map<ResourceLocation, MobMutatorDefinition> mobMutators = new LinkedHashMap<>();
    private final Map<ResourceLocation, LootProfileDefinition> lootProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, IntegrationProfileDefinition> integrationProfiles = new LinkedHashMap<>();
    private final Map<ResourceLocation, EntityBossFlagMapDefinition> entityBossFlags = new LinkedHashMap<>();

    public static WorldAwakenedProfileOverrideSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer() != null && level.getServer().overworld() != null
                ? level.getServer().overworld() : level;
        if (storageLevel.getDataStorage() == null) {
            return TRANSIENT_FALLBACK.computeIfAbsent(storageLevel, ignored -> new WorldAwakenedProfileOverrideSavedData());
        }
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static WorldAwakenedProfileOverrideSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return fromTag(tag);
    }

    static WorldAwakenedProfileOverrideSavedData fromTag(CompoundTag tag) {
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.revision = Math.max(0L, tag.getLong("revision"));
        data.profileKind = tag.contains("profile_kind") ? tag.getString("profile_kind") : "standard";
        data.readDefinitions(tag, "stages_json", StageDefinition.CODEC, data.stages, "stage");
        data.readDefinitions(tag, "trigger_rules_json", TriggerRuleDefinition.CODEC, data.triggerRules, "trigger rule");
        data.readDefinitions(tag, "mutation_pools_json", MutationPoolDefinition.CODEC, data.mutationPools, "mutation pool");
        data.readDefinitions(tag, "phase_mutation_configurations_json", WorldAwakenedPhaseMutationConfigurationDefinition.CODEC,
                data.phaseMutationConfigurations, "phase mutation configuration");
        data.readDefinitions(tag, "rules_json", RuleDefinition.CODEC, data.rules, "rule");
        data.readDefinitions(tag, "ascension_rewards_json", AscensionRewardDefinition.CODEC, data.ascensionRewards, "ascension reward");
        data.readDefinitions(tag, "ascension_offers_json", AscensionOfferDefinition.CODEC, data.ascensionOffers, "ascension offer");
        data.readDefinitions(tag, "mob_mutators_json", MobMutatorDefinition.CODEC, data.mobMutators, "mob mutator");
        data.readDefinitions(tag, "loot_profiles_json", LootProfileDefinition.CODEC, data.lootProfiles, "loot profile");
        data.readDefinitions(tag, "invasion_profiles_json", InvasionProfileDefinition.CODEC, data.invasionProfiles, "invasion profile");
        data.readDefinitions(tag, "integration_profiles_json", IntegrationProfileDefinition.CODEC, data.integrationProfiles, "integration profile");
        data.readDefinitions(tag, "entity_boss_flags_json", EntityBossFlagMapDefinition.CODEC, data.entityBossFlags, "entity boss flag map");
        return data;
    }

    private <T> void readDefinitions(CompoundTag tag, String key, com.mojang.serialization.Codec<T> codec,
            Map<ResourceLocation, T> target, String kind) {
        if (!tag.contains(key)) return;
        try {
            JsonObject root = JsonParser.parseString(tag.getString(key)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                T definition = codec.parse(JsonOps.INSTANCE, entry.getValue()).result().orElse(null);
                ResourceLocation definitionId = definitionId(definition);
                if (id != null && definition != null && id.equals(definitionId)) target.put(id, definition);
                else {
                    discardedOverrideCount++;
                    WorldAwakenedMod.LOGGER.warn("Discarded incompatible saved {} {}.", kind, entry.getKey());
                }
            }
        } catch (RuntimeException exception) {
            discardedOverrideCount++;
            WorldAwakenedMod.LOGGER.warn("Discarded malformed saved {} definitions; datapack definitions remain active.", kind, exception);
        }
    }

    private static ResourceLocation definitionId(Object definition) {
        if (definition instanceof StageDefinition value) return value.id();
        if (definition instanceof TriggerRuleDefinition value) return value.id();
        if (definition instanceof MutationPoolDefinition value) return value.id();
        if (definition instanceof WorldAwakenedPhaseMutationConfigurationDefinition value) return value.id();
        if (definition instanceof RuleDefinition value) return value.id();
        if (definition instanceof AscensionRewardDefinition value) return value.id();
        if (definition instanceof AscensionOfferDefinition value) return value.id();
        if (definition instanceof MobMutatorDefinition value) return value.id();
        if (definition instanceof LootProfileDefinition value) return value.id();
        if (definition instanceof InvasionProfileDefinition value) return value.id();
        if (definition instanceof IntegrationProfileDefinition value) return value.id();
        if (definition instanceof EntityBossFlagMapDefinition value) return value.id();
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("revision", revision);
        tag.putString("profile_kind", profileKind);
        writeDefinitions(tag, "stages_json", StageDefinition.CODEC, stages);
        writeDefinitions(tag, "trigger_rules_json", TriggerRuleDefinition.CODEC, triggerRules);
        writeDefinitions(tag, "mutation_pools_json", MutationPoolDefinition.CODEC, mutationPools);
        writeDefinitions(tag, "phase_mutation_configurations_json", WorldAwakenedPhaseMutationConfigurationDefinition.CODEC,
                phaseMutationConfigurations);
        writeDefinitions(tag, "rules_json", RuleDefinition.CODEC, rules);
        writeDefinitions(tag, "ascension_rewards_json", AscensionRewardDefinition.CODEC, ascensionRewards);
        writeDefinitions(tag, "ascension_offers_json", AscensionOfferDefinition.CODEC, ascensionOffers);
        writeDefinitions(tag, "mob_mutators_json", MobMutatorDefinition.CODEC, mobMutators);
        writeDefinitions(tag, "loot_profiles_json", LootProfileDefinition.CODEC, lootProfiles);
        writeDefinitions(tag, "invasion_profiles_json", InvasionProfileDefinition.CODEC, invasionProfiles);
        writeDefinitions(tag, "integration_profiles_json", IntegrationProfileDefinition.CODEC, integrationProfiles);
        writeDefinitions(tag, "entity_boss_flags_json", EntityBossFlagMapDefinition.CODEC, entityBossFlags);
        return tag;
    }

    private static <T> void writeDefinitions(CompoundTag tag, String key, com.mojang.serialization.Codec<T> codec,
            Map<ResourceLocation, T> values) {
        JsonObject root = new JsonObject();
        values.forEach((id, definition) -> codec.encodeStart(JsonOps.INSTANCE, definition).result()
                .ifPresent(json -> root.add(id.toString(), json)));
        tag.putString(key, root.toString());
    }

    public long revision() { return revision; }
    public String profileKind() { return profileKind; }
    public int discardedOverrideCount() { return discardedOverrideCount; }
    public Map<ResourceLocation, StageDefinition> stages() { return Map.copyOf(stages); }
    public Map<ResourceLocation, TriggerRuleDefinition> triggerRules() { return Map.copyOf(triggerRules); }
    public Map<ResourceLocation, MutationPoolDefinition> mutationPools() { return Map.copyOf(mutationPools); }
    public Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> phaseMutationConfigurations() { return Map.copyOf(phaseMutationConfigurations); }
    public Map<ResourceLocation, RuleDefinition> rules() { return Map.copyOf(rules); }
    public Map<ResourceLocation, AscensionRewardDefinition> ascensionRewards() { return Map.copyOf(ascensionRewards); }
    public Map<ResourceLocation, AscensionOfferDefinition> ascensionOffers() { return Map.copyOf(ascensionOffers); }
    public Map<ResourceLocation, MobMutatorDefinition> mobMutators() { return Map.copyOf(mobMutators); }
    public Map<ResourceLocation, LootProfileDefinition> lootProfiles() { return Map.copyOf(lootProfiles); }
    public Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles() { return Map.copyOf(invasionProfiles); }
    public Map<ResourceLocation, IntegrationProfileDefinition> integrationProfiles() { return Map.copyOf(integrationProfiles); }
    public Map<ResourceLocation, EntityBossFlagMapDefinition> entityBossFlags() { return Map.copyOf(entityBossFlags); }

    public void put(MutationPoolDefinition definition) {
        mutationPools.put(definition.id(), definition);
        revision++;
        setDirty();
    }

    public void remove(ResourceLocation id) {
        if (mutationPools.remove(id) != null) {
            revision++;
            setDirty();
        }
    }

    public void replaceAll(Map<ResourceLocation, StageDefinition> nextStages,
            Map<ResourceLocation, TriggerRuleDefinition> nextTriggers,
            Map<ResourceLocation, MutationPoolDefinition> nextPools) {
        stages.clear(); stages.putAll(nextStages);
        triggerRules.clear(); triggerRules.putAll(nextTriggers);
        mutationPools.clear(); mutationPools.putAll(nextPools);
        revision++;
        setDirty();
    }

    public void replaceAuthoring(Map<ResourceLocation, StageDefinition> nextStages,
            Map<ResourceLocation, TriggerRuleDefinition> nextTriggers,
            Map<ResourceLocation, MutationPoolDefinition> nextPools,
            Map<ResourceLocation, WorldAwakenedPhaseMutationConfigurationDefinition> nextPhaseMutationConfigurations,
            Map<ResourceLocation, AscensionRewardDefinition> nextRewards,
            Map<ResourceLocation, AscensionOfferDefinition> nextOffers,
            Map<ResourceLocation, MobMutatorDefinition> nextMutators,
            Map<ResourceLocation, LootProfileDefinition> nextLootProfiles) {
        stages.clear(); stages.putAll(nextStages);
        triggerRules.clear(); triggerRules.putAll(nextTriggers);
        mutationPools.clear(); mutationPools.putAll(nextPools);
        phaseMutationConfigurations.clear(); phaseMutationConfigurations.putAll(nextPhaseMutationConfigurations);
        ascensionRewards.clear(); ascensionRewards.putAll(nextRewards);
        ascensionOffers.clear(); ascensionOffers.putAll(nextOffers);
        mobMutators.clear(); mobMutators.putAll(nextMutators);
        lootProfiles.clear(); lootProfiles.putAll(nextLootProfiles);
        revision++;
        setDirty();
    }

    public void replaceProfile(String kind, Map<ResourceLocation, StageDefinition> nextStages,
            Map<ResourceLocation, TriggerRuleDefinition> nextTriggers,
            Map<ResourceLocation, MutationPoolDefinition> nextPools) {
        profileKind = kind;
        replaceAll(nextStages, nextTriggers, nextPools);
    }

    public void replaceCopiedProfile(String kind, net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData copied) {
        rules.clear(); rules.putAll(copied.rules());
        ascensionRewards.clear(); ascensionRewards.putAll(copied.ascensionRewards());
        ascensionOffers.clear(); ascensionOffers.putAll(copied.ascensionOffers());
        mobMutators.clear(); mobMutators.putAll(copied.mobMutators());
        lootProfiles.clear(); lootProfiles.putAll(copied.lootProfiles());
        invasionProfiles.clear(); invasionProfiles.putAll(copied.invasionProfiles());
        integrationProfiles.clear(); integrationProfiles.putAll(copied.integrationProfiles());
        entityBossFlags.clear(); entityBossFlags.putAll(copied.entityBossFlags());
        replaceProfile(kind, copied.stages(), copied.triggerRules(), copied.mutationPools());
    }

    public void replaceEmptyProfile(Map<ResourceLocation, MobMutatorDefinition> availableMutators) {
        clearCopiedDefinitions(); mobMutators.putAll(availableMutators);
        replaceProfile("empty", Map.of(), Map.of(), Map.of());
    }

    public void clearCopiedDefinitions() {
        rules.clear(); ascensionRewards.clear(); ascensionOffers.clear(); mobMutators.clear(); lootProfiles.clear();
        invasionProfiles.clear(); integrationProfiles.clear(); entityBossFlags.clear();
    }
}

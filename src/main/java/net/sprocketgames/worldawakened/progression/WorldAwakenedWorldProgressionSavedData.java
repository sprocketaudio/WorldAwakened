package net.sprocketgames.worldawakened.progression;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class WorldAwakenedWorldProgressionSavedData extends SavedData
        implements WorldAwakenedMutableStageState, WorldAwakenedMutableTriggerState, WorldAwakenedMutableRuleState {
    private static final String DATA_NAME = "worldawakened_world_progression";
    private static final SavedData.Factory<WorldAwakenedWorldProgressionSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedWorldProgressionSavedData::new,
            WorldAwakenedWorldProgressionSavedData::load);

    static final String KEY_UNLOCKED_STAGES = "unlocked_stages";
    static final String KEY_UNLOCK_TIMESTAMPS = "unlock_timestamps";
    static final String KEY_UNLOCK_SOURCES = "unlock_sources";
    static final String KEY_ACTIVE_GROUP_STAGES = "active_group_stages";
    static final String KEY_TRIGGER_COOLDOWNS = "trigger_cooldowns";
    static final String KEY_CONSUMED_ONE_SHOT_TRIGGERS = "consumed_one_shot_triggers";
    static final String KEY_TRIGGER_COUNTERS = "trigger_counters";
    static final String KEY_RULE_COOLDOWNS = "rule_cooldowns";
    static final String KEY_CONSUMED_RULES = "consumed_rules";
    static final String KEY_INVASION_COOLDOWNS = "invasion_cooldowns";
    static final String KEY_WORLD_SCALARS = "world_scalars";

    private final Set<ResourceLocation> unlockedStages = new LinkedHashSet<>();
    private final Map<ResourceLocation, Long> unlockTimestamps = new LinkedHashMap<>();
    private final Map<ResourceLocation, String> unlockSources = new LinkedHashMap<>();
    private final Map<String, ResourceLocation> activeGroupStages = new LinkedHashMap<>();
    private final Map<String, Long> triggerCooldowns = new LinkedHashMap<>();
    private final Set<String> consumedOneShotTriggers = new LinkedHashSet<>();
    private final Map<String, Integer> triggerCounters = new LinkedHashMap<>();
    private final Map<String, Long> ruleCooldowns = new LinkedHashMap<>();
    private final Set<String> consumedRules = new LinkedHashSet<>();
    private final Map<String, Long> invasionCooldownTrackers = new LinkedHashMap<>();
    private final Map<String, Double> worldScalars = new LinkedHashMap<>();

    public static WorldAwakenedWorldProgressionSavedData get(ServerLevel level) {
        return canonicalStorageLevel(level).getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ServerLevel canonicalStorageLevel(ServerLevel level) {
        if (level.getServer() != null && level.getServer().overworld() != null) {
            return level.getServer().overworld();
        }
        return level;
    }

    private static WorldAwakenedWorldProgressionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return fromTag(tag);
    }

    static WorldAwakenedWorldProgressionSavedData fromTag(CompoundTag tag) {
        WorldAwakenedWorldProgressionSavedData data = new WorldAwakenedWorldProgressionSavedData();
        data.unlockedStages.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_UNLOCKED_STAGES));
        data.unlockTimestamps.putAll(WorldAwakenedProgressionNbt.readStageLongMap(tag, KEY_UNLOCK_TIMESTAMPS));
        data.unlockSources.putAll(WorldAwakenedProgressionNbt.readStageStringMap(tag, KEY_UNLOCK_SOURCES));
        data.activeGroupStages.putAll(WorldAwakenedProgressionNbt.readStringStageMap(tag, KEY_ACTIVE_GROUP_STAGES));
        data.triggerCooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, KEY_TRIGGER_COOLDOWNS));
        data.consumedOneShotTriggers.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CONSUMED_ONE_SHOT_TRIGGERS));
        data.triggerCounters.putAll(WorldAwakenedProgressionNbt.readStringIntMap(tag, KEY_TRIGGER_COUNTERS));
        data.ruleCooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, KEY_RULE_COOLDOWNS));
        data.consumedRules.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CONSUMED_RULES));
        data.invasionCooldownTrackers.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, KEY_INVASION_COOLDOWNS));
        data.worldScalars.putAll(WorldAwakenedProgressionNbt.readStringDoubleMap(tag, KEY_WORLD_SCALARS));
        return data;
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_UNLOCKED_STAGES, unlockedStages);
        WorldAwakenedProgressionNbt.writeStageLongMap(tag, KEY_UNLOCK_TIMESTAMPS, unlockTimestamps);
        WorldAwakenedProgressionNbt.writeStageStringMap(tag, KEY_UNLOCK_SOURCES, unlockSources);
        WorldAwakenedProgressionNbt.writeStringStageMap(tag, KEY_ACTIVE_GROUP_STAGES, activeGroupStages);
        WorldAwakenedProgressionNbt.writeStringLongMap(tag, KEY_TRIGGER_COOLDOWNS, triggerCooldowns);
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CONSUMED_ONE_SHOT_TRIGGERS, consumedOneShotTriggers);
        WorldAwakenedProgressionNbt.writeStringIntMap(tag, KEY_TRIGGER_COUNTERS, triggerCounters);
        WorldAwakenedProgressionNbt.writeStringLongMap(tag, KEY_RULE_COOLDOWNS, ruleCooldowns);
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CONSUMED_RULES, consumedRules);
        WorldAwakenedProgressionNbt.writeStringLongMap(tag, KEY_INVASION_COOLDOWNS, invasionCooldownTrackers);
        WorldAwakenedProgressionNbt.writeStringDoubleMap(tag, KEY_WORLD_SCALARS, worldScalars);
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.merge(toTag());
        return tag;
    }

    @Override
    public Set<ResourceLocation> unlockedStages() {
        return unlockedStages;
    }

    @Override
    public Map<ResourceLocation, Long> unlockTimestamps() {
        return unlockTimestamps;
    }

    @Override
    public Map<ResourceLocation, String> unlockSources() {
        return unlockSources;
    }

    @Override
    public Map<String, ResourceLocation> activeGroupStages() {
        return activeGroupStages;
    }

    public Map<String, Long> invasionCooldownTrackers() {
        return invasionCooldownTrackers;
    }

    public Map<String, Double> worldScalars() {
        return worldScalars;
    }

    @Override
    public Map<String, Long> triggerCooldowns() {
        return triggerCooldowns;
    }

    @Override
    public Set<String> consumedOneShotTriggers() {
        return consumedOneShotTriggers;
    }

    @Override
    public Map<String, Integer> triggerCounters() {
        return triggerCounters;
    }

    @Override
    public Map<String, Long> ruleCooldowns() {
        return ruleCooldowns;
    }

    @Override
    public Set<String> consumedRules() {
        return consumedRules;
    }

    @Override
    public void markDirty() {
        setDirty();
    }
}


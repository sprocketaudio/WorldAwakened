package net.sprocketgames.worldawakened.progression;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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
    private static final Map<ServerLevel, WorldAwakenedWorldProgressionSavedData> TRANSIENT_FALLBACK =
            Collections.synchronizedMap(new WeakHashMap<>());

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
    static final String KEY_ACTIVE_INVASION_PROFILE_ID = "active_invasion_profile_id";
    static final String KEY_ACTIVE_INVASION_DISPLAY_NAME = "active_invasion_display_name";
    static final String KEY_ACTIVE_INVASION_TAGS = "active_invasion_tags";
    static final String KEY_ACTIVE_INVASION_INSTANCE_ID = "active_invasion_instance_id";
    static final String KEY_ACTIVE_INVASION_STARTED_AT_MILLIS = "active_invasion_started_at_millis";
    static final String KEY_ACTIVE_INVASION_WARNING_UNTIL_MILLIS = "active_invasion_warning_until_millis";
    static final String KEY_ACTIVE_INVASION_ENDS_AT_MILLIS = "active_invasion_ends_at_millis";
    static final String KEY_ACTIVE_INVASION_DURATION_SECONDS = "active_invasion_duration_seconds";
    static final String KEY_ACTIVE_INVASION_WARNING_SECONDS = "active_invasion_warning_seconds";
    static final String KEY_ACTIVE_INVASION_PRESSURE_MODIFIER = "active_invasion_pressure_modifier";
    static final String KEY_ACTIVE_INVASION_REWARD_PROFILE_ID = "active_invasion_reward_profile_id";
    static final String KEY_INVASION_GLOBAL_COOLDOWN_UNTIL_MILLIS = "invasion_global_cooldown_until_millis";
    static final String KEY_INVASION_INSTANCE_COUNTER = "invasion_instance_counter";
    static final String KEY_WORLD_SCALARS = "world_scalars";
    static final String KEY_GLOBAL_DIFFICULTY_MODIFIER = "global_difficulty_modifier";
    static final String KEY_GLOBAL_DIFFICULTY_UPDATED_AT_MILLIS = "global_difficulty_updated_at_millis";
    static final String KEY_GLOBAL_DIFFICULTY_UPDATED_BY = "global_difficulty_updated_by";
    static final String KEY_CHALLENGE_WORLD_MODIFIER = "challenge_world_modifier";
    static final String KEY_CHALLENGE_WORLD_COOLDOWN_UNTIL_MILLIS = "challenge_world_cooldown_until_millis";
    static final String KEY_CHALLENGE_WORLD_CHANGE_COUNT = "challenge_world_change_count";
    static final String KEY_CHALLENGE_WORLD_UPDATED_AT_MILLIS = "challenge_world_updated_at_millis";
    static final String KEY_CHALLENGE_WORLD_UPDATED_BY = "challenge_world_updated_by";
    static final String KEY_CHALLENGE_WORLD_VOTE_ACTIVE = "challenge_world_vote_active";
    static final String KEY_CHALLENGE_WORLD_VOTE_TARGET = "challenge_world_vote_target";
    static final String KEY_CHALLENGE_WORLD_VOTE_INITIATOR = "challenge_world_vote_initiator";
    static final String KEY_CHALLENGE_WORLD_VOTE_STARTED_AT_MILLIS = "challenge_world_vote_started_at_millis";
    static final String KEY_CHALLENGE_WORLD_VOTE_TIMEOUT_AT_MILLIS = "challenge_world_vote_timeout_at_millis";
    static final String KEY_CHALLENGE_WORLD_VOTE_ELIGIBLE = "challenge_world_vote_eligible";
    static final String KEY_CHALLENGE_WORLD_VOTE_YES = "challenge_world_vote_yes";
    static final String KEY_CHALLENGE_WORLD_VOTE_NO = "challenge_world_vote_no";

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
    private String activeInvasionProfileId = "";
    private String activeInvasionDisplayName = "";
    private final Set<String> activeInvasionTags = new LinkedHashSet<>();
    private long activeInvasionInstanceId = 0L;
    private long activeInvasionStartedAtMillis = 0L;
    private long activeInvasionWarningUntilMillis = 0L;
    private long activeInvasionEndsAtMillis = 0L;
    private int activeInvasionDurationSeconds = 0;
    private int activeInvasionWarningSeconds = 0;
    private double activeInvasionPressureModifier = 1.0D;
    private String activeInvasionRewardProfileId = "";
    private long invasionGlobalCooldownUntilMillis = 0L;
    private long invasionInstanceCounter = 0L;
    private final Map<String, Double> worldScalars = new LinkedHashMap<>();
    private double globalDifficultyModifier = 1.0D;
    private long globalDifficultyUpdatedAtMillis = 0L;
    private String globalDifficultyUpdatedBy = "";
    private double challengeWorldModifier = 1.0D;
    private long challengeWorldCooldownUntilMillis = 0L;
    private int challengeWorldChangeCount = 0;
    private long challengeWorldUpdatedAtMillis = 0L;
    private String challengeWorldUpdatedBy = "";
    private boolean challengeWorldVoteActive = false;
    private double challengeWorldVoteTarget = 1.0D;
    private String challengeWorldVoteInitiator = "";
    private long challengeWorldVoteStartedAtMillis = 0L;
    private long challengeWorldVoteTimeoutAtMillis = 0L;
    private final Set<String> challengeWorldVoteEligible = new LinkedHashSet<>();
    private final Set<String> challengeWorldVoteYes = new LinkedHashSet<>();
    private final Set<String> challengeWorldVoteNo = new LinkedHashSet<>();

    public static WorldAwakenedWorldProgressionSavedData get(ServerLevel level) {
        ServerLevel storageLevel = canonicalStorageLevel(level);
        if (storageLevel.getDataStorage() == null) {
            return TRANSIENT_FALLBACK.computeIfAbsent(
                    storageLevel,
                    ignored -> new WorldAwakenedWorldProgressionSavedData());
        }
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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
        data.activeInvasionProfileId = tag.getString(KEY_ACTIVE_INVASION_PROFILE_ID);
        data.activeInvasionDisplayName = tag.getString(KEY_ACTIVE_INVASION_DISPLAY_NAME);
        data.activeInvasionTags.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_ACTIVE_INVASION_TAGS));
        data.activeInvasionInstanceId = tag.getLong(KEY_ACTIVE_INVASION_INSTANCE_ID);
        data.activeInvasionStartedAtMillis = tag.getLong(KEY_ACTIVE_INVASION_STARTED_AT_MILLIS);
        data.activeInvasionWarningUntilMillis = tag.getLong(KEY_ACTIVE_INVASION_WARNING_UNTIL_MILLIS);
        data.activeInvasionEndsAtMillis = tag.getLong(KEY_ACTIVE_INVASION_ENDS_AT_MILLIS);
        data.activeInvasionDurationSeconds = tag.getInt(KEY_ACTIVE_INVASION_DURATION_SECONDS);
        data.activeInvasionWarningSeconds = tag.getInt(KEY_ACTIVE_INVASION_WARNING_SECONDS);
        data.activeInvasionPressureModifier = tag.contains(KEY_ACTIVE_INVASION_PRESSURE_MODIFIER)
                ? tag.getDouble(KEY_ACTIVE_INVASION_PRESSURE_MODIFIER)
                : 1.0D;
        data.activeInvasionRewardProfileId = tag.getString(KEY_ACTIVE_INVASION_REWARD_PROFILE_ID);
        data.invasionGlobalCooldownUntilMillis = tag.getLong(KEY_INVASION_GLOBAL_COOLDOWN_UNTIL_MILLIS);
        data.invasionInstanceCounter = tag.getLong(KEY_INVASION_INSTANCE_COUNTER);
        data.worldScalars.putAll(WorldAwakenedProgressionNbt.readStringDoubleMap(tag, KEY_WORLD_SCALARS));
        data.globalDifficultyModifier = tag.contains(KEY_GLOBAL_DIFFICULTY_MODIFIER) ? tag.getDouble(KEY_GLOBAL_DIFFICULTY_MODIFIER) : 1.0D;
        data.globalDifficultyUpdatedAtMillis = tag.getLong(KEY_GLOBAL_DIFFICULTY_UPDATED_AT_MILLIS);
        data.globalDifficultyUpdatedBy = tag.getString(KEY_GLOBAL_DIFFICULTY_UPDATED_BY);
        data.challengeWorldModifier = tag.contains(KEY_CHALLENGE_WORLD_MODIFIER) ? tag.getDouble(KEY_CHALLENGE_WORLD_MODIFIER) : 1.0D;
        data.challengeWorldCooldownUntilMillis = tag.getLong(KEY_CHALLENGE_WORLD_COOLDOWN_UNTIL_MILLIS);
        data.challengeWorldChangeCount = tag.getInt(KEY_CHALLENGE_WORLD_CHANGE_COUNT);
        data.challengeWorldUpdatedAtMillis = tag.getLong(KEY_CHALLENGE_WORLD_UPDATED_AT_MILLIS);
        data.challengeWorldUpdatedBy = tag.getString(KEY_CHALLENGE_WORLD_UPDATED_BY);
        data.challengeWorldVoteActive = tag.getBoolean(KEY_CHALLENGE_WORLD_VOTE_ACTIVE);
        data.challengeWorldVoteTarget = tag.contains(KEY_CHALLENGE_WORLD_VOTE_TARGET) ? tag.getDouble(KEY_CHALLENGE_WORLD_VOTE_TARGET) : 1.0D;
        data.challengeWorldVoteInitiator = tag.getString(KEY_CHALLENGE_WORLD_VOTE_INITIATOR);
        data.challengeWorldVoteStartedAtMillis = tag.getLong(KEY_CHALLENGE_WORLD_VOTE_STARTED_AT_MILLIS);
        data.challengeWorldVoteTimeoutAtMillis = tag.getLong(KEY_CHALLENGE_WORLD_VOTE_TIMEOUT_AT_MILLIS);
        data.challengeWorldVoteEligible.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_ELIGIBLE));
        data.challengeWorldVoteYes.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_YES));
        data.challengeWorldVoteNo.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_NO));
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
        if (!activeInvasionProfileId.isBlank()) {
            tag.putString(KEY_ACTIVE_INVASION_PROFILE_ID, activeInvasionProfileId);
        }
        if (!activeInvasionDisplayName.isBlank()) {
            tag.putString(KEY_ACTIVE_INVASION_DISPLAY_NAME, activeInvasionDisplayName);
        }
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_ACTIVE_INVASION_TAGS, activeInvasionTags);
        tag.putLong(KEY_ACTIVE_INVASION_INSTANCE_ID, activeInvasionInstanceId);
        tag.putLong(KEY_ACTIVE_INVASION_STARTED_AT_MILLIS, activeInvasionStartedAtMillis);
        tag.putLong(KEY_ACTIVE_INVASION_WARNING_UNTIL_MILLIS, activeInvasionWarningUntilMillis);
        tag.putLong(KEY_ACTIVE_INVASION_ENDS_AT_MILLIS, activeInvasionEndsAtMillis);
        tag.putInt(KEY_ACTIVE_INVASION_DURATION_SECONDS, activeInvasionDurationSeconds);
        tag.putInt(KEY_ACTIVE_INVASION_WARNING_SECONDS, activeInvasionWarningSeconds);
        tag.putDouble(KEY_ACTIVE_INVASION_PRESSURE_MODIFIER, activeInvasionPressureModifier);
        if (!activeInvasionRewardProfileId.isBlank()) {
            tag.putString(KEY_ACTIVE_INVASION_REWARD_PROFILE_ID, activeInvasionRewardProfileId);
        }
        tag.putLong(KEY_INVASION_GLOBAL_COOLDOWN_UNTIL_MILLIS, invasionGlobalCooldownUntilMillis);
        tag.putLong(KEY_INVASION_INSTANCE_COUNTER, invasionInstanceCounter);
        WorldAwakenedProgressionNbt.writeStringDoubleMap(tag, KEY_WORLD_SCALARS, worldScalars);
        tag.putDouble(KEY_GLOBAL_DIFFICULTY_MODIFIER, globalDifficultyModifier);
        tag.putLong(KEY_GLOBAL_DIFFICULTY_UPDATED_AT_MILLIS, globalDifficultyUpdatedAtMillis);
        if (!globalDifficultyUpdatedBy.isBlank()) {
            tag.putString(KEY_GLOBAL_DIFFICULTY_UPDATED_BY, globalDifficultyUpdatedBy);
        }
        tag.putDouble(KEY_CHALLENGE_WORLD_MODIFIER, challengeWorldModifier);
        tag.putLong(KEY_CHALLENGE_WORLD_COOLDOWN_UNTIL_MILLIS, challengeWorldCooldownUntilMillis);
        tag.putInt(KEY_CHALLENGE_WORLD_CHANGE_COUNT, challengeWorldChangeCount);
        tag.putLong(KEY_CHALLENGE_WORLD_UPDATED_AT_MILLIS, challengeWorldUpdatedAtMillis);
        if (!challengeWorldUpdatedBy.isBlank()) {
            tag.putString(KEY_CHALLENGE_WORLD_UPDATED_BY, challengeWorldUpdatedBy);
        }
        tag.putBoolean(KEY_CHALLENGE_WORLD_VOTE_ACTIVE, challengeWorldVoteActive);
        tag.putDouble(KEY_CHALLENGE_WORLD_VOTE_TARGET, challengeWorldVoteTarget);
        if (!challengeWorldVoteInitiator.isBlank()) {
            tag.putString(KEY_CHALLENGE_WORLD_VOTE_INITIATOR, challengeWorldVoteInitiator);
        }
        tag.putLong(KEY_CHALLENGE_WORLD_VOTE_STARTED_AT_MILLIS, challengeWorldVoteStartedAtMillis);
        tag.putLong(KEY_CHALLENGE_WORLD_VOTE_TIMEOUT_AT_MILLIS, challengeWorldVoteTimeoutAtMillis);
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_ELIGIBLE, challengeWorldVoteEligible);
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_YES, challengeWorldVoteYes);
        WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CHALLENGE_WORLD_VOTE_NO, challengeWorldVoteNo);
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

    public boolean invasionActive() {
        return !activeInvasionProfileId.isBlank() && activeInvasionEndsAtMillis > 0L;
    }

    public String activeInvasionProfileId() {
        return activeInvasionProfileId;
    }

    public void setActiveInvasionProfileId(String activeInvasionProfileId) {
        this.activeInvasionProfileId = activeInvasionProfileId == null ? "" : activeInvasionProfileId;
    }

    public String activeInvasionDisplayName() {
        return activeInvasionDisplayName;
    }

    public void setActiveInvasionDisplayName(String activeInvasionDisplayName) {
        this.activeInvasionDisplayName = activeInvasionDisplayName == null ? "" : activeInvasionDisplayName;
    }

    public Set<String> activeInvasionTags() {
        return activeInvasionTags;
    }

    public long activeInvasionInstanceId() {
        return activeInvasionInstanceId;
    }

    public void setActiveInvasionInstanceId(long activeInvasionInstanceId) {
        this.activeInvasionInstanceId = Math.max(0L, activeInvasionInstanceId);
    }

    public long activeInvasionStartedAtMillis() {
        return activeInvasionStartedAtMillis;
    }

    public void setActiveInvasionStartedAtMillis(long activeInvasionStartedAtMillis) {
        this.activeInvasionStartedAtMillis = Math.max(0L, activeInvasionStartedAtMillis);
    }

    public long activeInvasionWarningUntilMillis() {
        return activeInvasionWarningUntilMillis;
    }

    public void setActiveInvasionWarningUntilMillis(long activeInvasionWarningUntilMillis) {
        this.activeInvasionWarningUntilMillis = Math.max(0L, activeInvasionWarningUntilMillis);
    }

    public long activeInvasionEndsAtMillis() {
        return activeInvasionEndsAtMillis;
    }

    public void setActiveInvasionEndsAtMillis(long activeInvasionEndsAtMillis) {
        this.activeInvasionEndsAtMillis = Math.max(0L, activeInvasionEndsAtMillis);
    }

    public int activeInvasionDurationSeconds() {
        return activeInvasionDurationSeconds;
    }

    public void setActiveInvasionDurationSeconds(int activeInvasionDurationSeconds) {
        this.activeInvasionDurationSeconds = Math.max(0, activeInvasionDurationSeconds);
    }

    public int activeInvasionWarningSeconds() {
        return activeInvasionWarningSeconds;
    }

    public void setActiveInvasionWarningSeconds(int activeInvasionWarningSeconds) {
        this.activeInvasionWarningSeconds = Math.max(0, activeInvasionWarningSeconds);
    }

    public double activeInvasionPressureModifier() {
        return activeInvasionPressureModifier;
    }

    public void setActiveInvasionPressureModifier(double activeInvasionPressureModifier) {
        this.activeInvasionPressureModifier = Double.isFinite(activeInvasionPressureModifier) && activeInvasionPressureModifier > 0.0D
                ? activeInvasionPressureModifier
                : 1.0D;
    }

    public String activeInvasionRewardProfileId() {
        return activeInvasionRewardProfileId;
    }

    public void setActiveInvasionRewardProfileId(String activeInvasionRewardProfileId) {
        this.activeInvasionRewardProfileId = activeInvasionRewardProfileId == null ? "" : activeInvasionRewardProfileId;
    }

    public long invasionGlobalCooldownUntilMillis() {
        return invasionGlobalCooldownUntilMillis;
    }

    public void setInvasionGlobalCooldownUntilMillis(long invasionGlobalCooldownUntilMillis) {
        this.invasionGlobalCooldownUntilMillis = Math.max(0L, invasionGlobalCooldownUntilMillis);
    }

    public long invasionInstanceCounter() {
        return invasionInstanceCounter;
    }

    public long nextInvasionInstanceId() {
        invasionInstanceCounter = Math.max(0L, invasionInstanceCounter) + 1L;
        return invasionInstanceCounter;
    }

    public void clearActiveInvasionState() {
        activeInvasionProfileId = "";
        activeInvasionDisplayName = "";
        activeInvasionTags.clear();
        activeInvasionInstanceId = 0L;
        activeInvasionStartedAtMillis = 0L;
        activeInvasionWarningUntilMillis = 0L;
        activeInvasionEndsAtMillis = 0L;
        activeInvasionDurationSeconds = 0;
        activeInvasionWarningSeconds = 0;
        activeInvasionPressureModifier = 1.0D;
        activeInvasionRewardProfileId = "";
    }

    public Map<String, Double> worldScalars() {
        return worldScalars;
    }

    public double globalDifficultyModifier() {
        return globalDifficultyModifier;
    }

    public void setGlobalDifficultyModifier(double globalDifficultyModifier) {
        this.globalDifficultyModifier = globalDifficultyModifier;
    }

    public long globalDifficultyUpdatedAtMillis() {
        return globalDifficultyUpdatedAtMillis;
    }

    public void setGlobalDifficultyUpdatedAtMillis(long globalDifficultyUpdatedAtMillis) {
        this.globalDifficultyUpdatedAtMillis = globalDifficultyUpdatedAtMillis;
    }

    public String globalDifficultyUpdatedBy() {
        return globalDifficultyUpdatedBy;
    }

    public void setGlobalDifficultyUpdatedBy(String globalDifficultyUpdatedBy) {
        this.globalDifficultyUpdatedBy = globalDifficultyUpdatedBy == null ? "" : globalDifficultyUpdatedBy;
    }

    public double challengeWorldModifier() {
        return challengeWorldModifier;
    }

    public void setChallengeWorldModifier(double challengeWorldModifier) {
        this.challengeWorldModifier = challengeWorldModifier;
    }

    public long challengeWorldCooldownUntilMillis() {
        return challengeWorldCooldownUntilMillis;
    }

    public void setChallengeWorldCooldownUntilMillis(long challengeWorldCooldownUntilMillis) {
        this.challengeWorldCooldownUntilMillis = challengeWorldCooldownUntilMillis;
    }

    public int challengeWorldChangeCount() {
        return challengeWorldChangeCount;
    }

    public void setChallengeWorldChangeCount(int challengeWorldChangeCount) {
        this.challengeWorldChangeCount = challengeWorldChangeCount;
    }

    public long challengeWorldUpdatedAtMillis() {
        return challengeWorldUpdatedAtMillis;
    }

    public void setChallengeWorldUpdatedAtMillis(long challengeWorldUpdatedAtMillis) {
        this.challengeWorldUpdatedAtMillis = challengeWorldUpdatedAtMillis;
    }

    public String challengeWorldUpdatedBy() {
        return challengeWorldUpdatedBy;
    }

    public void setChallengeWorldUpdatedBy(String challengeWorldUpdatedBy) {
        this.challengeWorldUpdatedBy = challengeWorldUpdatedBy == null ? "" : challengeWorldUpdatedBy;
    }

    public boolean challengeWorldVoteActive() {
        return challengeWorldVoteActive;
    }

    public void setChallengeWorldVoteActive(boolean challengeWorldVoteActive) {
        this.challengeWorldVoteActive = challengeWorldVoteActive;
    }

    public double challengeWorldVoteTarget() {
        return challengeWorldVoteTarget;
    }

    public void setChallengeWorldVoteTarget(double challengeWorldVoteTarget) {
        this.challengeWorldVoteTarget = challengeWorldVoteTarget;
    }

    public String challengeWorldVoteInitiator() {
        return challengeWorldVoteInitiator;
    }

    public void setChallengeWorldVoteInitiator(String challengeWorldVoteInitiator) {
        this.challengeWorldVoteInitiator = challengeWorldVoteInitiator == null ? "" : challengeWorldVoteInitiator;
    }

    public long challengeWorldVoteStartedAtMillis() {
        return challengeWorldVoteStartedAtMillis;
    }

    public void setChallengeWorldVoteStartedAtMillis(long challengeWorldVoteStartedAtMillis) {
        this.challengeWorldVoteStartedAtMillis = challengeWorldVoteStartedAtMillis;
    }

    public long challengeWorldVoteTimeoutAtMillis() {
        return challengeWorldVoteTimeoutAtMillis;
    }

    public void setChallengeWorldVoteTimeoutAtMillis(long challengeWorldVoteTimeoutAtMillis) {
        this.challengeWorldVoteTimeoutAtMillis = challengeWorldVoteTimeoutAtMillis;
    }

    public Set<String> challengeWorldVoteEligible() {
        return challengeWorldVoteEligible;
    }

    public Set<String> challengeWorldVoteYes() {
        return challengeWorldVoteYes;
    }

    public Set<String> challengeWorldVoteNo() {
        return challengeWorldVoteNo;
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


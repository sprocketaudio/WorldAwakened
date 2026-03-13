package net.sprocketgames.worldawakened.progression;

import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionOfferRuntime;

public final class WorldAwakenedPlayerProgressionSavedData extends SavedData {
    private static final String DATA_NAME = "worldawakened_player_progression";
    private static final SavedData.Factory<WorldAwakenedPlayerProgressionSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedPlayerProgressionSavedData::new,
            WorldAwakenedPlayerProgressionSavedData::load);

    private static final String KEY_PLAYERS = "players";
    private static final String KEY_PLAYER_UUID = "player_uuid";

    private final Map<UUID, PlayerStageState> playerStates = new LinkedHashMap<>();

    public static WorldAwakenedPlayerProgressionSavedData get(ServerLevel level) {
        return canonicalStorageLevel(level).getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ServerLevel canonicalStorageLevel(ServerLevel level) {
        if (level.getServer() != null && level.getServer().overworld() != null) {
            return level.getServer().overworld();
        }
        return level;
    }

    private static WorldAwakenedPlayerProgressionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return fromTag(tag);
    }

    static WorldAwakenedPlayerProgressionSavedData fromTag(CompoundTag tag) {
        WorldAwakenedPlayerProgressionSavedData data = new WorldAwakenedPlayerProgressionSavedData();
        ListTag players = tag.getList(KEY_PLAYERS, Tag.TAG_COMPOUND);
        for (Tag rawEntry : players) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            String rawUuid = entry.getString(KEY_PLAYER_UUID);
            UUID playerId;
            try {
                playerId = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            PlayerStageState state = new PlayerStageState(data);
            state.readFromTag(entry);
            data.playerStates.put(playerId, state);
        }
        return data;
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        ListTag players = new ListTag();
        playerStates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag playerTag = new CompoundTag();
                    playerTag.putString(KEY_PLAYER_UUID, entry.getKey().toString());
                    entry.getValue().writeToTag(playerTag);
                    players.add(playerTag);
                });
        tag.put(KEY_PLAYERS, players);
        return tag;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.merge(toTag());
        return tag;
    }

    public Optional<PlayerStageState> getIfPresent(UUID playerId) {
        return Optional.ofNullable(playerStates.get(playerId));
    }

    public PlayerStageState getOrCreate(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, ignored -> {
            setDirty();
            return new PlayerStageState(this);
        });
    }

    public Map<UUID, PlayerStageState> playerStates() {
        return Collections.unmodifiableMap(playerStates);
    }

    public static final class PlayerStageState implements WorldAwakenedMutableStageState, WorldAwakenedMutableTriggerState, WorldAwakenedMutableRuleState {
        private static final String KEY_UNLOCKED_STAGES = "unlocked_stages";
        private static final String KEY_UNLOCK_TIMESTAMPS = "unlock_timestamps";
        private static final String KEY_UNLOCK_SOURCES = "unlock_sources";
        private static final String KEY_ACTIVE_GROUP_STAGES = "active_group_stages";
        private static final String KEY_VISITED_DIMENSIONS = "visited_dimensions";
        private static final String KEY_TRIGGER_COOLDOWNS = "trigger_cooldowns";
        private static final String KEY_CONSUMED_ONE_SHOT_TRIGGERS = "consumed_one_shot_triggers";
        private static final String KEY_TRIGGER_COUNTERS = "trigger_counters";
        private static final String KEY_RULE_COOLDOWNS = "rule_cooldowns";
        private static final String KEY_CONSUMED_RULES = "consumed_rules";
        private static final String KEY_DEBUG_FLAGS = "debug_flags";
        private static final String KEY_PENDING_ASCENSION_OFFERS = "pending_ascension_offers";
        private static final String KEY_RESOLVED_ASCENSION_OFFERS = "resolved_ascension_offers";
        private static final String KEY_CHOSEN_ASCENSION_REWARDS = "chosen_ascension_rewards";
        private static final String KEY_FORFEITED_ASCENSION_REWARDS = "forfeited_ascension_rewards";
        private static final String KEY_PENDING_ASCENSION_OFFER_INSTANCES = "pending_ascension_offer_instances";
        private static final String KEY_RESOLVED_ASCENSION_OFFER_INSTANCES = "resolved_ascension_offer_instances";
        private static final String KEY_FORFEITED_ASCENSION_REWARDS_BY_OFFER = "forfeited_ascension_rewards_by_offer";
        private static final String KEY_ASCENSION_REWARD_UNLOCK_TIMESTAMPS = "ascension_reward_unlock_timestamps";
        private static final String KEY_ASCENSION_REWARD_SOURCES = "ascension_reward_sources";

        private final WorldAwakenedPlayerProgressionSavedData owner;

        private final Set<ResourceLocation> unlockedStages = new LinkedHashSet<>();
        private final Map<ResourceLocation, Long> unlockTimestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> unlockSources = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> activeGroupStages = new LinkedHashMap<>();

        private final Set<ResourceLocation> visitedDimensions = new LinkedHashSet<>();
        private final Map<String, Long> triggerCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedOneShotTriggers = new LinkedHashSet<>();
        private final Map<String, Integer> triggerCounters = new LinkedHashMap<>();
        private final Map<String, Long> ruleCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedRules = new LinkedHashSet<>();
        private final Set<String> debugInspectionFlags = new LinkedHashSet<>();
        private final Set<ResourceLocation> pendingAscensionOffers = new LinkedHashSet<>();
        private final Set<ResourceLocation> resolvedAscensionOffers = new LinkedHashSet<>();
        private final Set<ResourceLocation> chosenAscensionRewards = new LinkedHashSet<>();
        private final Set<ResourceLocation> forfeitedAscensionRewards = new LinkedHashSet<>();
        private final Map<String, WorldAwakenedAscensionOfferRuntime> pendingAscensionOfferInstances = new LinkedHashMap<>();
        private final Map<String, WorldAwakenedAscensionOfferRuntime> resolvedAscensionOfferInstances = new LinkedHashMap<>();
        private final Map<String, Set<ResourceLocation>> forfeitedAscensionRewardsByOffer = new LinkedHashMap<>();
        private final Map<ResourceLocation, Long> ascensionRewardUnlockTimestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> ascensionRewardSources = new LinkedHashMap<>();

        private PlayerStageState(WorldAwakenedPlayerProgressionSavedData owner) {
            this.owner = owner;
        }

        private void readFromTag(CompoundTag tag) {
            unlockedStages.clear();
            unlockTimestamps.clear();
            unlockSources.clear();
            activeGroupStages.clear();
            visitedDimensions.clear();
            triggerCooldowns.clear();
            consumedOneShotTriggers.clear();
            triggerCounters.clear();
            ruleCooldowns.clear();
            consumedRules.clear();
            debugInspectionFlags.clear();
            pendingAscensionOffers.clear();
            resolvedAscensionOffers.clear();
            chosenAscensionRewards.clear();
            forfeitedAscensionRewards.clear();
            pendingAscensionOfferInstances.clear();
            resolvedAscensionOfferInstances.clear();
            forfeitedAscensionRewardsByOffer.clear();
            ascensionRewardUnlockTimestamps.clear();
            ascensionRewardSources.clear();

            unlockedStages.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_UNLOCKED_STAGES));
            unlockTimestamps.putAll(WorldAwakenedProgressionNbt.readStageLongMap(tag, KEY_UNLOCK_TIMESTAMPS));
            unlockSources.putAll(WorldAwakenedProgressionNbt.readStageStringMap(tag, KEY_UNLOCK_SOURCES));
            activeGroupStages.putAll(WorldAwakenedProgressionNbt.readStringStageMap(tag, KEY_ACTIVE_GROUP_STAGES));
            visitedDimensions.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_VISITED_DIMENSIONS));
            triggerCooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, KEY_TRIGGER_COOLDOWNS));
            consumedOneShotTriggers.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CONSUMED_ONE_SHOT_TRIGGERS));
            triggerCounters.putAll(WorldAwakenedProgressionNbt.readStringIntMap(tag, KEY_TRIGGER_COUNTERS));
            ruleCooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, KEY_RULE_COOLDOWNS));
            consumedRules.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_CONSUMED_RULES));
            debugInspectionFlags.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, KEY_DEBUG_FLAGS));
            pendingAscensionOffers.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_PENDING_ASCENSION_OFFERS));
            resolvedAscensionOffers.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_RESOLVED_ASCENSION_OFFERS));
            chosenAscensionRewards.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_CHOSEN_ASCENSION_REWARDS));
            forfeitedAscensionRewards.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, KEY_FORFEITED_ASCENSION_REWARDS));

            ListTag pendingRuntime = tag.getList(KEY_PENDING_ASCENSION_OFFER_INSTANCES, Tag.TAG_COMPOUND);
            for (Tag raw : pendingRuntime) {
                if (!(raw instanceof CompoundTag entry)) {
                    continue;
                }
                WorldAwakenedAscensionOfferRuntime.fromTag(entry)
                        .ifPresent(runtime -> pendingAscensionOfferInstances.put(runtime.instanceId(), runtime));
            }

            ListTag resolvedRuntime = tag.getList(KEY_RESOLVED_ASCENSION_OFFER_INSTANCES, Tag.TAG_COMPOUND);
            for (Tag raw : resolvedRuntime) {
                if (!(raw instanceof CompoundTag entry)) {
                    continue;
                }
                WorldAwakenedAscensionOfferRuntime.fromTag(entry)
                        .ifPresent(runtime -> resolvedAscensionOfferInstances.put(runtime.instanceId(), runtime));
            }

            ListTag forfeitedByOffer = tag.getList(KEY_FORFEITED_ASCENSION_REWARDS_BY_OFFER, Tag.TAG_COMPOUND);
            for (Tag raw : forfeitedByOffer) {
                if (!(raw instanceof CompoundTag entry)) {
                    continue;
                }
                String instanceId = entry.getString("instance_id");
                if (instanceId.isBlank()) {
                    continue;
                }
                Set<ResourceLocation> rewards = WorldAwakenedProgressionNbt.readResourceLocationSet(entry, "rewards");
                if (!rewards.isEmpty()) {
                    forfeitedAscensionRewardsByOffer.put(instanceId, rewards);
                }
            }

            ascensionRewardUnlockTimestamps.putAll(WorldAwakenedProgressionNbt.readStageLongMap(tag, KEY_ASCENSION_REWARD_UNLOCK_TIMESTAMPS));
            ascensionRewardSources.putAll(WorldAwakenedProgressionNbt.readStageStringMap(tag, KEY_ASCENSION_REWARD_SOURCES));

            if (pendingAscensionOfferInstances.isEmpty() && !pendingAscensionOffers.isEmpty()) {
                for (ResourceLocation offerId : pendingAscensionOffers) {
                    String legacyInstanceId = syntheticLegacyInstanceId(offerId, "legacy_pending");
                    pendingAscensionOfferInstances.putIfAbsent(
                            legacyInstanceId,
                            WorldAwakenedAscensionOfferRuntime.pending(
                                    legacyInstanceId,
                                    offerId,
                                    offerId.toString(),
                                    0L,
                                    List.of()));
                }
            }
            if (resolvedAscensionOfferInstances.isEmpty() && !resolvedAscensionOffers.isEmpty()) {
                for (ResourceLocation offerId : resolvedAscensionOffers) {
                    String legacyInstanceId = syntheticLegacyInstanceId(offerId, "legacy_resolved");
                    resolvedAscensionOfferInstances.putIfAbsent(
                            legacyInstanceId,
                            WorldAwakenedAscensionOfferRuntime.pending(
                                    legacyInstanceId,
                                    offerId,
                                    offerId.toString(),
                                    0L,
                                    List.of()));
                }
            }
            migrateAscensionInstanceIds();
            rebuildAscensionSummarySets();
        }

        private void migrateAscensionInstanceIds() {
            LinkedHashSet<String> usedIds = new LinkedHashSet<>();
            Map<String, String> remappedIds = new LinkedHashMap<>();

            Map<String, WorldAwakenedAscensionOfferRuntime> migratedPending = migrateInstanceMap(
                    pendingAscensionOfferInstances,
                    usedIds,
                    remappedIds);
            Map<String, WorldAwakenedAscensionOfferRuntime> migratedResolved = migrateInstanceMap(
                    resolvedAscensionOfferInstances,
                    usedIds,
                    remappedIds);

            pendingAscensionOfferInstances.clear();
            pendingAscensionOfferInstances.putAll(migratedPending);
            resolvedAscensionOfferInstances.clear();
            resolvedAscensionOfferInstances.putAll(migratedResolved);

            if (!remappedIds.isEmpty()) {
                LinkedHashMap<String, Set<ResourceLocation>> migratedForfeits = new LinkedHashMap<>();
                for (Map.Entry<String, Set<ResourceLocation>> entry : forfeitedAscensionRewardsByOffer.entrySet()) {
                    String mappedId = remappedIds.getOrDefault(entry.getKey(), entry.getKey());
                    migratedForfeits.computeIfAbsent(mappedId, ignored -> new LinkedHashSet<>()).addAll(entry.getValue());
                }
                forfeitedAscensionRewardsByOffer.clear();
                forfeitedAscensionRewardsByOffer.putAll(migratedForfeits);
                markDirty();
            }
        }

        private Map<String, WorldAwakenedAscensionOfferRuntime> migrateInstanceMap(
                Map<String, WorldAwakenedAscensionOfferRuntime> source,
                Set<String> usedIds,
                Map<String, String> remappedIds) {
            LinkedHashMap<String, WorldAwakenedAscensionOfferRuntime> migrated = new LinkedHashMap<>();
            for (WorldAwakenedAscensionOfferRuntime runtime : source.values()) {
                String currentId = runtime.instanceId();
                String nextId = currentId;
                if (!WorldAwakenedAscensionOfferRuntime.isOpaqueInstanceId(currentId) || usedIds.contains(currentId)) {
                    nextId = migratedInstanceId(runtime, usedIds);
                }
                usedIds.add(nextId);
                remappedIds.put(currentId, nextId);
                migrated.put(nextId, nextId.equals(currentId) ? runtime : runtime.withInstanceId(nextId));
            }
            return migrated;
        }

        private static String migratedInstanceId(
                WorldAwakenedAscensionOfferRuntime runtime,
                Set<String> usedIds) {
            String seed = runtime.offerId()
                    + "|"
                    + runtime.sourceKey()
                    + "|"
                    + runtime.grantedAtMillis()
                    + "|"
                    + runtime.candidateRewards()
                    + "|"
                    + runtime.chosenRewardId().map(ResourceLocation::toString).orElse("")
                    + "|"
                    + runtime.resolvedAtMillis().orElse(0L);
            int salt = 0;
            String candidate = WorldAwakenedAscensionOfferRuntime.stableOpaqueInstanceId(seed, salt);
            while (usedIds.contains(candidate)) {
                salt++;
                candidate = WorldAwakenedAscensionOfferRuntime.stableOpaqueInstanceId(seed, salt);
            }
            return candidate;
        }

        private static String syntheticLegacyInstanceId(ResourceLocation offerId, String suffix) {
            return WorldAwakenedAscensionOfferRuntime.stableOpaqueInstanceId(offerId + "|" + suffix);
        }

        private void writeToTag(CompoundTag tag) {
            rebuildAscensionSummarySets();
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_UNLOCKED_STAGES, unlockedStages);
            WorldAwakenedProgressionNbt.writeStageLongMap(tag, KEY_UNLOCK_TIMESTAMPS, unlockTimestamps);
            WorldAwakenedProgressionNbt.writeStageStringMap(tag, KEY_UNLOCK_SOURCES, unlockSources);
            WorldAwakenedProgressionNbt.writeStringStageMap(tag, KEY_ACTIVE_GROUP_STAGES, activeGroupStages);
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_VISITED_DIMENSIONS, visitedDimensions);
            WorldAwakenedProgressionNbt.writeStringLongMap(tag, KEY_TRIGGER_COOLDOWNS, triggerCooldowns);
            WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CONSUMED_ONE_SHOT_TRIGGERS, consumedOneShotTriggers);
            WorldAwakenedProgressionNbt.writeStringIntMap(tag, KEY_TRIGGER_COUNTERS, triggerCounters);
            WorldAwakenedProgressionNbt.writeStringLongMap(tag, KEY_RULE_COOLDOWNS, ruleCooldowns);
            WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_CONSUMED_RULES, consumedRules);
            WorldAwakenedProgressionNbt.writeStringSet(tag, KEY_DEBUG_FLAGS, debugInspectionFlags);
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_PENDING_ASCENSION_OFFERS, pendingAscensionOffers);
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_RESOLVED_ASCENSION_OFFERS, resolvedAscensionOffers);
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_CHOSEN_ASCENSION_REWARDS, chosenAscensionRewards);
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag, KEY_FORFEITED_ASCENSION_REWARDS, forfeitedAscensionRewards);

            ListTag pendingRuntime = new ListTag();
            pendingAscensionOfferInstances.values().forEach(runtime -> pendingRuntime.add(runtime.toTag()));
            tag.put(KEY_PENDING_ASCENSION_OFFER_INSTANCES, pendingRuntime);

            ListTag resolvedRuntime = new ListTag();
            resolvedAscensionOfferInstances.values().forEach(runtime -> resolvedRuntime.add(runtime.toTag()));
            tag.put(KEY_RESOLVED_ASCENSION_OFFER_INSTANCES, resolvedRuntime);

            ListTag forfeitedByOffer = new ListTag();
            forfeitedAscensionRewardsByOffer.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        CompoundTag value = new CompoundTag();
                        value.putString("instance_id", entry.getKey());
                        WorldAwakenedProgressionNbt.writeResourceLocationSet(value, "rewards", entry.getValue());
                        forfeitedByOffer.add(value);
                    });
            tag.put(KEY_FORFEITED_ASCENSION_REWARDS_BY_OFFER, forfeitedByOffer);

            WorldAwakenedProgressionNbt.writeStageLongMap(tag, KEY_ASCENSION_REWARD_UNLOCK_TIMESTAMPS, ascensionRewardUnlockTimestamps);
            WorldAwakenedProgressionNbt.writeStageStringMap(tag, KEY_ASCENSION_REWARD_SOURCES, ascensionRewardSources);
        }

        private void rebuildAscensionSummarySets() {
            pendingAscensionOffers.clear();
            for (WorldAwakenedAscensionOfferRuntime runtime : pendingAscensionOfferInstances.values()) {
                pendingAscensionOffers.add(runtime.offerId());
            }

            resolvedAscensionOffers.clear();
            for (WorldAwakenedAscensionOfferRuntime runtime : resolvedAscensionOfferInstances.values()) {
                resolvedAscensionOffers.add(runtime.offerId());
                runtime.chosenRewardId().ifPresent(chosenAscensionRewards::add);
            }

            LinkedHashSet<ResourceLocation> mergedForfeited = new LinkedHashSet<>(forfeitedAscensionRewards);
            forfeitedAscensionRewardsByOffer.values().forEach(mergedForfeited::addAll);
            forfeitedAscensionRewards.clear();
            forfeitedAscensionRewards.addAll(mergedForfeited);
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

        public Set<ResourceLocation> visitedDimensions() {
            return visitedDimensions;
        }

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

        public Set<String> debugInspectionFlags() {
            return debugInspectionFlags;
        }

        public Set<ResourceLocation> pendingAscensionOffers() {
            return pendingAscensionOffers;
        }

        public Set<ResourceLocation> resolvedAscensionOffers() {
            return resolvedAscensionOffers;
        }

        public Set<ResourceLocation> chosenAscensionRewards() {
            return chosenAscensionRewards;
        }

        public Set<ResourceLocation> forfeitedAscensionRewards() {
            return forfeitedAscensionRewards;
        }

        public Map<String, WorldAwakenedAscensionOfferRuntime> pendingAscensionOfferInstances() {
            return pendingAscensionOfferInstances;
        }

        public Map<String, WorldAwakenedAscensionOfferRuntime> resolvedAscensionOfferInstances() {
            return resolvedAscensionOfferInstances;
        }

        public Map<String, Set<ResourceLocation>> forfeitedAscensionRewardsByOffer() {
            return forfeitedAscensionRewardsByOffer;
        }

        public Map<ResourceLocation, Long> ascensionRewardUnlockTimestamps() {
            return ascensionRewardUnlockTimestamps;
        }

        public Map<ResourceLocation, String> ascensionRewardSources() {
            return ascensionRewardSources;
        }

        @Override
        public void markDirty() {
            owner.setDirty();
        }
    }
}


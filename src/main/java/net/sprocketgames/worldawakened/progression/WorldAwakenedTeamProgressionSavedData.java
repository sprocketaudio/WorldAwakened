package net.sprocketgames.worldawakened.progression;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** FTB-team-owned phase/trigger/rule state only. Personal ascension data is never stored here. */
public final class WorldAwakenedTeamProgressionSavedData extends SavedData {
    private static final String DATA_NAME = "worldawakened_team_progression";
    private static final SavedData.Factory<WorldAwakenedTeamProgressionSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedTeamProgressionSavedData::new, WorldAwakenedTeamProgressionSavedData::load);
    private static final Map<ServerLevel, WorldAwakenedTeamProgressionSavedData> TRANSIENT =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, TeamState> teams = new LinkedHashMap<>();

    public static WorldAwakenedTeamProgressionSavedData get(ServerLevel level) {
        ServerLevel storage = level.getServer() != null && level.getServer().overworld() != null ? level.getServer().overworld() : level;
        if (storage.getDataStorage() == null) return TRANSIENT.computeIfAbsent(storage, ignored -> new WorldAwakenedTeamProgressionSavedData());
        return storage.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }
    public TeamState getOrCreate(String teamId) {
        return teams.computeIfAbsent(teamId, ignored -> { setDirty(); return new TeamState(this); });
    }
    public boolean hasHistory() { return teams.values().stream().anyMatch(TeamState::hasHistory); }
    public boolean hasMeaningfulHistory(Set<net.minecraft.resources.ResourceLocation> defaultStageIds) {
        return teams.values().stream().anyMatch(state -> WorldAwakenedProgressionHistory.hasMeaningfulHistory(state, defaultStageIds));
    }
    static WorldAwakenedTeamProgressionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldAwakenedTeamProgressionSavedData data = new WorldAwakenedTeamProgressionSavedData();
        for (Tag raw : tag.getList("teams", Tag.TAG_COMPOUND)) if (raw instanceof CompoundTag entry) {
            String key = entry.getString("key"); if (!key.isBlank()) data.teams.put(key, TeamState.read(data, entry));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        teams.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag value = new CompoundTag(); value.putString("key", entry.getKey()); entry.getValue().write(value); entries.add(value);
        });
        tag.put("teams", entries); return tag;
    }

    public static final class TeamState implements WorldAwakenedProgressionState {
        private final WorldAwakenedTeamProgressionSavedData owner;
        private final Set<net.minecraft.resources.ResourceLocation> unlocked = new LinkedHashSet<>();
        private final Map<net.minecraft.resources.ResourceLocation, Long> timestamps = new LinkedHashMap<>();
        private final Map<net.minecraft.resources.ResourceLocation, String> sources = new LinkedHashMap<>();
        private final Map<String, net.minecraft.resources.ResourceLocation> active = new LinkedHashMap<>();
        private final Map<String, Long> cooldowns = new LinkedHashMap<>();
        private final Set<String> consumed = new LinkedHashSet<>();
        private final Map<String, Integer> counters = new LinkedHashMap<>();
        private final Map<String, Long> ruleCooldowns = new LinkedHashMap<>();
        private final Set<String> consumedRules = new LinkedHashSet<>();
        private TeamState(WorldAwakenedTeamProgressionSavedData owner) { this.owner = owner; }
        static TeamState read(WorldAwakenedTeamProgressionSavedData owner, CompoundTag tag) {
            TeamState state = new TeamState(owner);
            state.unlocked.addAll(WorldAwakenedProgressionNbt.readResourceLocationSet(tag, "unlocked"));
            state.timestamps.putAll(WorldAwakenedProgressionNbt.readStageLongMap(tag, "timestamps"));
            state.sources.putAll(WorldAwakenedProgressionNbt.readStageStringMap(tag, "sources"));
            state.active.putAll(WorldAwakenedProgressionNbt.readStringStageMap(tag, "active"));
            state.cooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, "cooldowns"));
            state.consumed.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, "consumed"));
            state.counters.putAll(WorldAwakenedProgressionNbt.readStringIntMap(tag, "counters"));
            state.ruleCooldowns.putAll(WorldAwakenedProgressionNbt.readStringLongMap(tag, "rule_cooldowns"));
            state.consumedRules.addAll(WorldAwakenedProgressionNbt.readStringSet(tag, "consumed_rules")); return state;
        }
        void write(CompoundTag tag) {
            WorldAwakenedProgressionNbt.writeResourceLocationSet(tag,"unlocked",unlocked); WorldAwakenedProgressionNbt.writeStageLongMap(tag,"timestamps",timestamps);
            WorldAwakenedProgressionNbt.writeStageStringMap(tag,"sources",sources); WorldAwakenedProgressionNbt.writeStringStageMap(tag,"active",active);
            WorldAwakenedProgressionNbt.writeStringLongMap(tag,"cooldowns",cooldowns); WorldAwakenedProgressionNbt.writeStringSet(tag,"consumed",consumed);
            WorldAwakenedProgressionNbt.writeStringIntMap(tag,"counters",counters); WorldAwakenedProgressionNbt.writeStringLongMap(tag,"rule_cooldowns",ruleCooldowns);
            WorldAwakenedProgressionNbt.writeStringSet(tag,"consumed_rules",consumedRules);
        }
        boolean hasHistory() { return !unlocked.isEmpty() || !counters.isEmpty() || !consumed.isEmpty() || !consumedRules.isEmpty(); }
        @Override public Set<net.minecraft.resources.ResourceLocation> unlockedStages(){return unlocked;} @Override public Map<net.minecraft.resources.ResourceLocation,Long> unlockTimestamps(){return timestamps;}
        @Override public Map<net.minecraft.resources.ResourceLocation,String> unlockSources(){return sources;} @Override public Map<String,net.minecraft.resources.ResourceLocation> activeGroupStages(){return active;}
        @Override public Map<String,Long> triggerCooldowns(){return cooldowns;} @Override public Set<String> consumedOneShotTriggers(){return consumed;}
        @Override public Map<String,Integer> triggerCounters(){return counters;} @Override public Map<String,Long> ruleCooldowns(){return ruleCooldowns;}
        @Override public Set<String> consumedRules(){return consumedRules;} @Override public void markDirty(){owner.setDirty();}
    }
}

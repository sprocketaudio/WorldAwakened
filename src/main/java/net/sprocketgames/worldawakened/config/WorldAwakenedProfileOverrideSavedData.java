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

public final class WorldAwakenedProfileOverrideSavedData extends SavedData {
    private static final String DATA_NAME = "worldawakened_profile_overrides";
    private static final SavedData.Factory<WorldAwakenedProfileOverrideSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedProfileOverrideSavedData::new,
            WorldAwakenedProfileOverrideSavedData::load);
    private static final Map<ServerLevel, WorldAwakenedProfileOverrideSavedData> TRANSIENT_FALLBACK =
            Collections.synchronizedMap(new WeakHashMap<>());
    private long revision;
    private int discardedOverrideCount;
    private final Map<ResourceLocation, MutationPoolDefinition> mutationPools = new LinkedHashMap<>();

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
        if (!tag.contains("mutation_pools_json")) return data;
        try {
            JsonObject root = JsonParser.parseString(tag.getString("mutation_pools_json")).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                var decoded = MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue());
                MutationPoolDefinition definition = decoded.result().orElse(null);
                if (id != null && definition != null && id.equals(definition.id())) {
                    data.mutationPools.put(id, definition);
                } else {
                    data.discardedOverrideCount++;
                    WorldAwakenedMod.LOGGER.warn(
                            "Discarded incompatible mutation-pool override {}: {}. The datapack definition remains active.",
                            entry.getKey(), decoded.error().map(error -> error.message())
                                    .orElse(id == null ? "invalid resource ID" : "definition ID mismatch"));
                }
            }
        } catch (RuntimeException exception) {
            data.discardedOverrideCount++;
            WorldAwakenedMod.LOGGER.warn(
                    "Discarded malformed saved mutation-pool overrides; datapack definitions remain active.", exception);
            // Isolate malformed saved overrides; datapack definitions remain available.
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("revision", revision);
        JsonObject root = new JsonObject();
        mutationPools.forEach((id, definition) -> MutationPoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition)
                .result().ifPresent(json -> root.add(id.toString(), json)));
        tag.putString("mutation_pools_json", root.toString());
        return tag;
    }

    public long revision() { return revision; }
    public int discardedOverrideCount() { return discardedOverrideCount; }
    public Map<ResourceLocation, MutationPoolDefinition> mutationPools() { return Map.copyOf(mutationPools); }

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
}

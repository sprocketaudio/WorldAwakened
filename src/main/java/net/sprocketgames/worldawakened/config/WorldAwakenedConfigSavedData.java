package net.sprocketgames.worldawakened.config;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

public final class WorldAwakenedConfigSavedData extends SavedData {
    private static final String DATA_NAME = "worldawakened_native_config";
    private static final SavedData.Factory<WorldAwakenedConfigSavedData> FACTORY = new SavedData.Factory<>(
            WorldAwakenedConfigSavedData::new,
            WorldAwakenedConfigSavedData::load);
    private static final Map<ServerLevel, WorldAwakenedConfigSavedData> TRANSIENT_FALLBACK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private boolean initialized;
    private long revision;
    private WorldAwakenedEditorSettings settings = WorldAwakenedEditorSettings.builtInDefaults();

    public static WorldAwakenedConfigSavedData get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer() != null && level.getServer().overworld() != null
                ? level.getServer().overworld()
                : level;
        if (storageLevel.getDataStorage() == null) {
            return TRANSIENT_FALLBACK.computeIfAbsent(storageLevel, ignored -> new WorldAwakenedConfigSavedData());
        }
        return storageLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static WorldAwakenedConfigSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        return fromTag(tag);
    }

    static WorldAwakenedConfigSavedData fromTag(CompoundTag tag) {
        WorldAwakenedConfigSavedData data = new WorldAwakenedConfigSavedData();
        data.initialized = tag.getBoolean("initialized");
        data.revision = Math.max(0L, tag.getLong("revision"));
        data.settings = new WorldAwakenedEditorSettings(
                readBoolean(tag, "default_gameplay_enabled", true),
                readDouble(tag, "difficulty_intensity", 1.0D),
                readBoolean(tag, "mutations_enabled", true),
                readDouble(tag, "mutation_frequency_multiplier", 1.0D),
                readBoolean(tag, "allow_multiple_mutations", true),
                readInt(tag, "maximum_mutations_per_mob", 2),
                readBoolean(tag, "pressure_events_enabled", true),
                readDouble(tag, "event_frequency_multiplier", 1.0D),
                readBoolean(tag, "ascension_enabled", true),
                WorldAwakenedProgressionMode.parse(tag.contains("progression_mode")
                        ? tag.getString("progression_mode") : null)
                        .orElse(WorldAwakenedProgressionMode.PER_PLAYER));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("initialized", initialized);
        tag.putLong("revision", revision);
        tag.putBoolean("default_gameplay_enabled", settings.defaultGameplayEnabled());
        tag.putDouble("difficulty_intensity", settings.difficultyIntensity());
        tag.putBoolean("mutations_enabled", settings.mutationsEnabled());
        tag.putDouble("mutation_frequency_multiplier", settings.mutationFrequencyMultiplier());
        tag.putBoolean("allow_multiple_mutations", settings.allowMultipleMutations());
        tag.putInt("maximum_mutations_per_mob", settings.maximumMutationsPerMob());
        tag.putBoolean("pressure_events_enabled", settings.pressureEventsEnabled());
        tag.putDouble("event_frequency_multiplier", settings.eventFrequencyMultiplier());
        tag.putBoolean("ascension_enabled", settings.ascensionEnabled());
        tag.putString("progression_mode", settings.progressionMode().serializedName());
        return tag;
    }

    public boolean initialized() {
        return initialized;
    }

    public long revision() {
        return revision;
    }

    public WorldAwakenedEditorSettings settings() {
        return settings;
    }

    public void initialize(WorldAwakenedEditorSettings initialSettings) {
        if (initialized) {
            return;
        }
        initialized = true;
        revision = 1L;
        settings = initialSettings;
        setDirty();
    }

    public void apply(WorldAwakenedEditorSettings nextSettings) {
        initialized = true;
        revision = Math.max(1L, revision + 1L);
        settings = nextSettings;
        setDirty();
    }

    private static boolean readBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    private static double readDouble(CompoundTag tag, String key, double fallback) {
        return tag.contains(key) ? tag.getDouble(key) : fallback;
    }

    private static int readInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }
}

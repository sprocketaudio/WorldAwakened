package net.sprocketgames.worldawakened.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;

public final class WorldAwakenedConfigService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<ConfigSnapshot> ACTIVE = new AtomicReference<>(
            new ConfigSnapshot(0L, WorldAwakenedEditorSettings.fromCommonConfig(1.0D)));
    private static final ThreadLocal<WorldAwakenedEditorSettings> PREVIEW = new ThreadLocal<>();
    private static MinecraftServer boundServer;

    private WorldAwakenedConfigService() {
    }

    public static synchronized BindResult bind(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldAwakenedConfigSavedData data = WorldAwakenedConfigSavedData.get(level);
        boolean initialBuiltInState = WorldAwakenedCommonConfig.ENABLE_BUILT_IN_GAMEPLAY.get();
        if (!data.initialized()) {
            double persistedDifficulty = WorldAwakenedWorldProgressionSavedData.get(level).globalDifficultyModifier();
            data.initialize(WorldAwakenedEditorSettings.fromCommonConfig(persistedDifficulty));
        }
        List<String> persistedErrors = validate(data.settings());
        if (!persistedErrors.isEmpty()) {
            double minimum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MIN_VALUE.get();
            double maximum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MAX_VALUE.get();
            if (Double.isFinite(minimum) && Double.isFinite(maximum) && minimum <= maximum) {
                WorldAwakenedEditorSettings repaired = sanitize(data.settings());
                data.apply(repaired);
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "Repaired invalid persisted native configuration: errors={} new_revision={}",
                        String.join(" | ", persistedErrors),
                        data.revision());
            } else {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "Cannot repair native configuration while common difficulty bounds are invalid: errors={}",
                        String.join(" | ", persistedErrors));
            }
        }
        boundServer = server;
        ConfigSnapshot snapshot = new ConfigSnapshot(data.revision(), data.settings());
        ACTIVE.set(snapshot);
        WorldAwakenedLog.info(
                LOGGER,
                WorldAwakenedLogCategory.CONFIG,
                "Loaded native configuration revision={} built_in_gameplay={} mutation_frequency={} event_frequency={}",
                snapshot.revision(),
                snapshot.settings().defaultGameplayEnabled(),
                snapshot.settings().mutationFrequencyMultiplier(),
                snapshot.settings().eventFrequencyMultiplier());
        return new BindResult(snapshot, initialBuiltInState != snapshot.settings().defaultGameplayEnabled());
    }

    public static synchronized void unbind(MinecraftServer server) {
        if (boundServer != server) {
            return;
        }
        boundServer = null;
        ACTIVE.set(new ConfigSnapshot(0L, WorldAwakenedEditorSettings.fromCommonConfig(1.0D)));
    }

    public static ConfigSnapshot snapshot() {
        return ACTIVE.get();
    }

    public static WorldAwakenedEditorSettings settings() {
        WorldAwakenedEditorSettings preview = PREVIEW.get();
        return preview == null ? ACTIVE.get().settings() : preview;
    }

    static synchronized void resetForTests() {
        boundServer = null;
        PREVIEW.remove();
        ACTIVE.set(new ConfigSnapshot(0L, WorldAwakenedEditorSettings.builtInDefaults()));
    }

    public static <T> T withPreviewSettings(WorldAwakenedEditorSettings settings, Supplier<T> operation) {
        PREVIEW.set(settings);
        try {
            return operation.get();
        } finally {
            PREVIEW.remove();
        }
    }

    public static boolean isAuthorized(ServerPlayer player) {
        return player != null
                && (player.hasPermissions(2)
                        || player.getServer().isSingleplayerOwner(player.getGameProfile()));
    }

    public static synchronized ApplyResult apply(
            ServerLevel level,
            long expectedRevision,
            WorldAwakenedEditorSettings candidate,
            String actor) {
        WorldAwakenedConfigSavedData data = WorldAwakenedConfigSavedData.get(level);
        if (!data.initialized()) {
            data.initialize(WorldAwakenedEditorSettings.fromCommonConfig(
                    WorldAwakenedWorldProgressionSavedData.get(level).globalDifficultyModifier()));
        }
        ConfigSnapshot current = new ConfigSnapshot(data.revision(), data.settings());
        if (expectedRevision != current.revision()) {
            ACTIVE.set(current);
            return ApplyResult.rejected(
                    ApplyStatus.STALE_REVISION,
                    "Settings changed on the server. Review the refreshed values and apply again.",
                    current,
                    current);
        }

        List<String> errors = validate(candidate);
        if (!errors.isEmpty()) {
            return ApplyResult.rejected(
                    ApplyStatus.VALIDATION_FAILED,
                    String.join(" ", errors),
                    current,
                    current);
        }

        data.apply(candidate);
        ConfigSnapshot accepted = new ConfigSnapshot(data.revision(), data.settings());
        ACTIVE.set(accepted);
        mirrorDifficulty(level, candidate.difficultyIntensity(), actor);
        WorldAwakenedLog.info(
                LOGGER,
                WorldAwakenedLogCategory.CONFIG,
                "Applied native configuration revision={} actor={} built_in_gameplay={} mutations={} invasions={} ascension={}",
                accepted.revision(),
                actor,
                candidate.defaultGameplayEnabled(),
                candidate.mutationsEnabled(),
                candidate.pressureEventsEnabled(),
                candidate.ascensionEnabled());
        return ApplyResult.accepted(current, accepted);
    }

    public static synchronized ApplyResult updateDifficulty(
            ServerLevel level,
            double value,
            String actor) {
        ConfigSnapshot current = ensureLoaded(level);
        WorldAwakenedEditorSettings settings = current.settings();
        WorldAwakenedEditorSettings candidate = new WorldAwakenedEditorSettings(
                settings.defaultGameplayEnabled(),
                value,
                settings.mutationsEnabled(),
                settings.mutationFrequencyMultiplier(),
                settings.allowMultipleMutations(),
                settings.maximumMutationsPerMob(),
                settings.pressureEventsEnabled(),
                settings.eventFrequencyMultiplier(),
                settings.ascensionEnabled());
        return apply(level, current.revision(), candidate, actor);
    }

    private static ConfigSnapshot ensureLoaded(ServerLevel level) {
        WorldAwakenedConfigSavedData data = WorldAwakenedConfigSavedData.get(level);
        if (!data.initialized()) {
            data.initialize(WorldAwakenedEditorSettings.fromCommonConfig(
                    WorldAwakenedWorldProgressionSavedData.get(level).globalDifficultyModifier()));
        }
        ConfigSnapshot current = new ConfigSnapshot(data.revision(), data.settings());
        ACTIVE.set(current);
        return current;
    }

    public static List<String> validate(WorldAwakenedEditorSettings candidate) {
        if (candidate == null) {
            return List.of("Configuration payload is missing.");
        }
        double minimum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MIN_VALUE.get();
        double maximum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MAX_VALUE.get();
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            return List.of("The server difficulty bounds are invalid; fix the common configuration first.");
        }
        return candidate.validationErrors(minimum, maximum);
    }

    private static void mirrorDifficulty(ServerLevel level, double value, String actor) {
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        worldData.setGlobalDifficultyModifier(value);
        worldData.setGlobalDifficultyUpdatedAtMillis(System.currentTimeMillis());
        worldData.setGlobalDifficultyUpdatedBy(actor);
        worldData.markDirty();
    }

    private static WorldAwakenedEditorSettings sanitize(WorldAwakenedEditorSettings settings) {
        double difficultyMinimum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MIN_VALUE.get();
        double difficultyMaximum = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MAX_VALUE.get();
        double difficultyFallback = WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_VALUE.get();
        double difficulty = finiteOr(settings.difficultyIntensity(), difficultyFallback);
        difficulty = clamp(difficulty, difficultyMinimum, difficultyMaximum);
        double mutationFrequency = clamp(
                finiteOr(settings.mutationFrequencyMultiplier(), 1.0D),
                WorldAwakenedEditorSettings.MIN_MUTATION_FREQUENCY,
                WorldAwakenedEditorSettings.MAX_MUTATION_FREQUENCY);
        int maximumMutations = Math.max(
                WorldAwakenedEditorSettings.MIN_MUTATIONS_PER_MOB,
                Math.min(WorldAwakenedEditorSettings.MAX_MUTATIONS_PER_MOB, settings.maximumMutationsPerMob()));
        double eventFrequency = clamp(
                finiteOr(settings.eventFrequencyMultiplier(), 1.0D),
                WorldAwakenedEditorSettings.MIN_EVENT_FREQUENCY,
                WorldAwakenedEditorSettings.MAX_EVENT_FREQUENCY);
        return new WorldAwakenedEditorSettings(
                settings.defaultGameplayEnabled(),
                difficulty,
                settings.mutationsEnabled(),
                mutationFrequency,
                settings.allowMultipleMutations(),
                maximumMutations,
                settings.pressureEventsEnabled(),
                eventFrequency,
                settings.ascensionEnabled());
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record ConfigSnapshot(long revision, WorldAwakenedEditorSettings settings) {
    }

    public record BindResult(ConfigSnapshot snapshot, boolean requiresDatapackReload) {
    }

    public enum ApplyStatus {
        ACCEPTED,
        STALE_REVISION,
        VALIDATION_FAILED
    }

    public record ApplyResult(
            ApplyStatus status,
            String message,
            ConfigSnapshot previous,
            ConfigSnapshot current) {
        static ApplyResult accepted(ConfigSnapshot previous, ConfigSnapshot current) {
            return new ApplyResult(ApplyStatus.ACCEPTED, "Settings applied successfully.", previous, current);
        }

        static ApplyResult rejected(
                ApplyStatus status,
                String message,
                ConfigSnapshot previous,
                ConfigSnapshot current) {
            return new ApplyResult(status, message, previous, current);
        }

        public boolean accepted() {
            return status == ApplyStatus.ACCEPTED;
        }

        public boolean requiresDatapackReload() {
            return previous.settings().defaultGameplayEnabled() != current.settings().defaultGameplayEnabled();
        }
    }
}

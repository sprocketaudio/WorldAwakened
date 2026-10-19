package net.sprocketgames.worldawakened.config;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

public record WorldAwakenedEditorSettings(
        boolean defaultGameplayEnabled,
        double difficultyIntensity,
        boolean mutationsEnabled,
        double mutationFrequencyMultiplier,
        boolean allowMultipleMutations,
        int maximumMutationsPerMob,
        boolean pressureEventsEnabled,
        double eventFrequencyMultiplier,
        boolean ascensionEnabled,
        WorldAwakenedProgressionMode progressionMode) {
    public static final double MIN_DIFFICULTY_INTENSITY = 0.01D;
    public static final double MAX_DIFFICULTY_INTENSITY = 10.0D;
    public static final double MIN_MUTATION_FREQUENCY = 0.0D;
    public static final double MAX_MUTATION_FREQUENCY = 4.0D;
    public static final int MIN_MUTATIONS_PER_MOB = 1;
    public static final int MAX_MUTATIONS_PER_MOB = 8;
    public static final double MIN_EVENT_FREQUENCY = 0.25D;
    public static final double MAX_EVENT_FREQUENCY = 4.0D;

    public WorldAwakenedEditorSettings {
        progressionMode = progressionMode == null ? WorldAwakenedProgressionMode.PER_PLAYER : progressionMode;
    }

    public static WorldAwakenedEditorSettings builtInDefaults() {
        return new WorldAwakenedEditorSettings(
                true,
                1.0D,
                true,
                1.0D,
                true,
                2,
                true,
                1.0D,
                true,
                WorldAwakenedProgressionMode.PER_PLAYER);
    }

    public WorldAwakenedEditorSettings(boolean defaultGameplayEnabled, double difficultyIntensity, boolean mutationsEnabled,
            double mutationFrequencyMultiplier, boolean allowMultipleMutations, int maximumMutationsPerMob,
            boolean pressureEventsEnabled, double eventFrequencyMultiplier, boolean ascensionEnabled) {
        this(defaultGameplayEnabled, difficultyIntensity, mutationsEnabled, mutationFrequencyMultiplier,
                allowMultipleMutations, maximumMutationsPerMob, pressureEventsEnabled, eventFrequencyMultiplier,
                ascensionEnabled, WorldAwakenedProgressionMode.PER_PLAYER);
    }

    public static WorldAwakenedEditorSettings fromCommonConfig(double persistedDifficultyIntensity) {
        return new WorldAwakenedEditorSettings(
                WorldAwakenedCommonConfig.ENABLE_BUILT_IN_GAMEPLAY.get(),
                persistedDifficultyIntensity,
                WorldAwakenedCommonConfig.ENABLE_MUTATORS.get(),
                1.0D,
                WorldAwakenedCommonConfig.MAX_MUTATORS_PER_MOB.get() > 1,
                Math.max(MIN_MUTATIONS_PER_MOB, WorldAwakenedCommonConfig.MAX_MUTATORS_PER_MOB.get()),
                WorldAwakenedCommonConfig.ENABLE_INVASIONS.get(),
                1.0D,
                WorldAwakenedCommonConfig.ENABLE_ASCENSION.get(),
                WorldAwakenedProgressionMode.parse(WorldAwakenedCommonConfig.PROGRESSION_MODE.get())
                        .orElse(WorldAwakenedProgressionMode.PER_PLAYER));
    }

    public List<String> validationErrors(double difficultyMinimum, double difficultyMaximum) {
        List<String> errors = new ArrayList<>();
        if (!Double.isFinite(difficultyIntensity)
                || difficultyIntensity < MIN_DIFFICULTY_INTENSITY
                || difficultyIntensity > MAX_DIFFICULTY_INTENSITY
                || difficultyIntensity < difficultyMinimum
                || difficultyIntensity > difficultyMaximum) {
            errors.add("Difficulty intensity must be between " + difficultyMinimum + " and " + difficultyMaximum + ".");
        }
        if (!Double.isFinite(mutationFrequencyMultiplier)
                || mutationFrequencyMultiplier < MIN_MUTATION_FREQUENCY
                || mutationFrequencyMultiplier > MAX_MUTATION_FREQUENCY) {
            errors.add("Mutation frequency must be between 0.0 and 4.0.");
        }
        if (maximumMutationsPerMob < MIN_MUTATIONS_PER_MOB
                || maximumMutationsPerMob > MAX_MUTATIONS_PER_MOB) {
            errors.add("Maximum mutations per mob must be between 1 and 8.");
        }
        if (!Double.isFinite(eventFrequencyMultiplier)
                || eventFrequencyMultiplier < MIN_EVENT_FREQUENCY
                || eventFrequencyMultiplier > MAX_EVENT_FREQUENCY) {
            errors.add("Event frequency must be between 0.25 and 4.0.");
        }
        return List.copyOf(errors);
    }

    public int effectiveMutationCap() {
        return allowMultipleMutations ? maximumMutationsPerMob : 1;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("default_gameplay_enabled", defaultGameplayEnabled);
        root.addProperty("difficulty_intensity", difficultyIntensity);
        root.addProperty("mutations_enabled", mutationsEnabled);
        root.addProperty("mutation_frequency_multiplier", mutationFrequencyMultiplier);
        root.addProperty("allow_multiple_mutations", allowMultipleMutations);
        root.addProperty("maximum_mutations_per_mob", maximumMutationsPerMob);
        root.addProperty("pressure_events_enabled", pressureEventsEnabled);
        root.addProperty("event_frequency_multiplier", eventFrequencyMultiplier);
        root.addProperty("ascension_enabled", ascensionEnabled);
        root.addProperty("progression_mode", progressionMode.serializedName());
        return root;
    }

    public static WorldAwakenedEditorSettings fromJson(JsonObject root) {
        return new WorldAwakenedEditorSettings(
                root.get("default_gameplay_enabled").getAsBoolean(),
                root.get("difficulty_intensity").getAsDouble(),
                root.get("mutations_enabled").getAsBoolean(),
                root.get("mutation_frequency_multiplier").getAsDouble(),
                root.get("allow_multiple_mutations").getAsBoolean(),
                root.get("maximum_mutations_per_mob").getAsInt(),
                root.get("pressure_events_enabled").getAsBoolean(),
                root.get("event_frequency_multiplier").getAsDouble(),
                root.get("ascension_enabled").getAsBoolean(),
                root.has("progression_mode")
                        ? WorldAwakenedProgressionMode.parse(root.get("progression_mode").getAsString())
                                .orElseThrow(() -> new IllegalArgumentException("Unsupported progression_mode; use per_server or per_player."))
                        : WorldAwakenedProgressionMode.PER_PLAYER);
    }
}

package net.sprocketgames.worldawakened.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WorldAwakenedCommonConfig {
    private WorldAwakenedCommonConfig() {
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_MOD;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_COMMANDS;
    public static final ModConfigSpec.BooleanValue VALIDATION_LOGGING;

    public static final ModConfigSpec.ConfigValue<String> PROGRESSION_MODE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_STAGE_UNLOCKS;
    public static final ModConfigSpec.BooleanValue ALLOW_HIDDEN_STAGES_IN_DEBUG;

    public static final ModConfigSpec.BooleanValue ENABLE_MUTATORS;
    public static final ModConfigSpec.IntValue MAX_MUTATORS_PER_MOB;
    public static final ModConfigSpec.BooleanValue RESPECT_BOSS_BLACKLIST;
    public static final ModConfigSpec.BooleanValue APPLY_ON_SPAWN_ONLY;

    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_SCALING;
    public static final ModConfigSpec.BooleanValue ALLOW_PACK_SIZE_ADJUSTMENTS;
    public static final ModConfigSpec.BooleanValue ALLOW_SPECIAL_REINFORCEMENTS;
    public static final ModConfigSpec.DoubleValue NATURAL_SPAWN_SCALING_CAP;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPAWN_PRESSURE_DIMENSION_BASELINES;

    public static final ModConfigSpec.BooleanValue DIFFICULTY_GLOBAL_ENABLED;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_GLOBAL_VALUE;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_GLOBAL_MIN_VALUE;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_GLOBAL_MAX_VALUE;

    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DIFFICULTY_CHALLENGE_SCOPE_MODE;
    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_ALLOW_PLAYER_ADJUSTMENT;
    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_ALLOW_RAISE;
    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_ALLOW_LOWER;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_DEFAULT_VALUE;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_MIN_VALUE;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_MAX_VALUE;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_STEP;
    public static final ModConfigSpec.IntValue DIFFICULTY_CHALLENGE_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue DIFFICULTY_CHALLENGE_MAX_CHANGES_PER_PLAYER;
    public static final ModConfigSpec.IntValue DIFFICULTY_CHALLENGE_MAX_WORLD_CHANGES;
    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_REQUIRE_VOTE_IN_GLOBAL;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_CHALLENGE_VOTE_THRESHOLD;
    public static final ModConfigSpec.IntValue DIFFICULTY_CHALLENGE_VOTE_TIMEOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue DIFFICULTY_CHALLENGE_ADMIN_OVERRIDE;

    public static final ModConfigSpec.IntValue MAXIMUM_RULES_EVALUATED_PER_EVENT;
    public static final ModConfigSpec.IntValue MAXIMUM_ACTIONS_PER_RULE;

    public static final ModConfigSpec.BooleanValue ENABLE_LOOT_EVOLUTION;
    public static final ModConfigSpec.BooleanValue INJECT_ONLY;
    public static final ModConfigSpec.BooleanValue ALLOW_ENTRY_REPLACEMENT;

    public static final ModConfigSpec.BooleanValue ENABLE_INVASIONS;
    public static final ModConfigSpec.IntValue GLOBAL_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WARNING_SECONDS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_INVASIONS;

    public static final ModConfigSpec.BooleanValue ENABLE_ASCENSION;
    public static final ModConfigSpec.BooleanValue ONE_PENDING_OFFER_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue REMIND_PENDING_OFFERS;
    public static final ModConfigSpec.BooleanValue SHOW_ASCENSION_NOTIFICATIONS;

    public static final ModConfigSpec.BooleanValue AUTO_DETECT;
    public static final ModConfigSpec.BooleanValue DEFAULT_ENABLE_DETECTED_INTEGRATIONS;

    public static final ModConfigSpec.BooleanValue APOTHEOSIS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> APOTHEOSIS_MODE;
    public static final ModConfigSpec.ConfigValue<String> APOTHEOSIS_LOOT_UNSAFE_MODE_POLICY;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_TIER_CONDITIONS;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_TIER_STAGE_UNLOCKS;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_TIER_LOOT_SCALING;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_TIER_INVASION_SCALING;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_TIER_MUTATOR_SCALING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("general");
        ENABLE_MOD = builder.define("enable_mod", true);
        DEBUG_LOGGING = builder.define("debug_logging", false);
        ENABLE_DEBUG_COMMANDS = builder.define("enable_debug_commands", true);
        VALIDATION_LOGGING = builder.define("validation_logging", true);
        builder.pop();

        builder.push("progression");
        PROGRESSION_MODE = builder.define("mode", "global");
        ANNOUNCE_STAGE_UNLOCKS = builder.define("announce_stage_unlocks", true);
        ALLOW_HIDDEN_STAGES_IN_DEBUG = builder.define("allow_hidden_stages_in_debug", true);
        builder.pop();

        builder.push("mutators");
        ENABLE_MUTATORS = builder.define("enable_mutators", true);
        MAX_MUTATORS_PER_MOB = builder.comment(
                        "Global default cap for mutators applied per spawned mob. "
                                + "Mutation pools may override this with max_mutators_per_entity.")
                .defineInRange("max_mutators_per_mob", 2, 0, 16);
        RESPECT_BOSS_BLACKLIST = builder.define("respect_boss_blacklist", true);
        APPLY_ON_SPAWN_ONLY = builder.define("apply_on_spawn_only", true);
        builder.pop();

        builder.push("spawning");
        ENABLE_SPAWN_SCALING = builder.define("enable_spawn_scaling", true);
        ALLOW_PACK_SIZE_ADJUSTMENTS = builder.define("allow_pack_size_adjustments", true);
        ALLOW_SPECIAL_REINFORCEMENTS = builder.define("allow_special_reinforcements", true);
        NATURAL_SPAWN_SCALING_CAP = builder.defineInRange("natural_spawn_scaling_cap", 2.0D, 0.1D, 16.0D);
        SPAWN_PRESSURE_DIMENSION_BASELINES = builder.comment(
                        "Optional per-dimension spawn pressure baselines using entries in dimension=value form.",
                        "Example: minecraft:overworld=1.0")
                .defineListAllowEmpty(
                        List.of("pressure_dimension_baselines"),
                        () -> List.of(
                                "minecraft:overworld=1.0",
                                "minecraft:the_nether=1.25",
                                "minecraft:the_end=1.5"),
                        value -> value instanceof String);
        builder.pop();

        builder.push("difficulty");
        builder.push("global");
        DIFFICULTY_GLOBAL_ENABLED = builder.define("enabled", true);
        DIFFICULTY_GLOBAL_VALUE = builder.defineInRange("value", 1.0D, 0.01D, 10.0D);
        DIFFICULTY_GLOBAL_MIN_VALUE = builder.defineInRange("min_value", 0.75D, 0.01D, 10.0D);
        DIFFICULTY_GLOBAL_MAX_VALUE = builder.defineInRange("max_value", 1.50D, 0.01D, 10.0D);
        builder.pop();

        builder.push("challenge");
        DIFFICULTY_CHALLENGE_ENABLED = builder.define("enabled", true);
        DIFFICULTY_CHALLENGE_SCOPE_MODE = builder.define("scope_mode", "auto");
        DIFFICULTY_CHALLENGE_ALLOW_PLAYER_ADJUSTMENT = builder.define("allow_player_adjustment", true);
        DIFFICULTY_CHALLENGE_ALLOW_RAISE = builder.define("allow_raise", true);
        DIFFICULTY_CHALLENGE_ALLOW_LOWER = builder.define("allow_lower", true);
        DIFFICULTY_CHALLENGE_DEFAULT_VALUE = builder.defineInRange("default_value", 1.0D, 0.01D, 10.0D);
        DIFFICULTY_CHALLENGE_MIN_VALUE = builder.defineInRange("min_value", 0.75D, 0.01D, 10.0D);
        DIFFICULTY_CHALLENGE_MAX_VALUE = builder.defineInRange("max_value", 1.50D, 0.01D, 10.0D);
        DIFFICULTY_CHALLENGE_STEP = builder.defineInRange("step", 0.10D, 0.0001D, 10.0D);
        DIFFICULTY_CHALLENGE_COOLDOWN_MINUTES = builder.defineInRange("cooldown_minutes", 120, 0, Integer.MAX_VALUE);
        DIFFICULTY_CHALLENGE_MAX_CHANGES_PER_PLAYER = builder.defineInRange("max_changes_per_player", 5, 0, Integer.MAX_VALUE);
        DIFFICULTY_CHALLENGE_MAX_WORLD_CHANGES = builder.defineInRange("max_world_changes", 10, 0, Integer.MAX_VALUE);
        DIFFICULTY_CHALLENGE_REQUIRE_VOTE_IN_GLOBAL = builder.define("require_vote_in_global", true);
        DIFFICULTY_CHALLENGE_VOTE_THRESHOLD = builder.defineInRange("vote_threshold", 0.60D, 0.0D, 1.0D);
        DIFFICULTY_CHALLENGE_VOTE_TIMEOUT_SECONDS = builder.defineInRange("vote_timeout_seconds", 120, 1, Integer.MAX_VALUE);
        DIFFICULTY_CHALLENGE_ADMIN_OVERRIDE = builder.define("admin_override", true);
        builder.pop();
        builder.pop();

        builder.push("performance");
        MAXIMUM_RULES_EVALUATED_PER_EVENT = builder.defineInRange("maximum_rules_evaluated_per_event", 50, 1, 2048);
        MAXIMUM_ACTIONS_PER_RULE = builder.defineInRange("maximum_actions_per_rule", 10, 1, 128);
        builder.pop();

        builder.push("loot");
        ENABLE_LOOT_EVOLUTION = builder.define("enable_loot_evolution", true);
        INJECT_ONLY = builder.define("inject_only", true);
        ALLOW_ENTRY_REPLACEMENT = builder.define("allow_entry_replacement", false);
        builder.pop();

        builder.push("invasions");
        ENABLE_INVASIONS = builder.define("enable_invasions", true);
        GLOBAL_COOLDOWN_MINUTES = builder.defineInRange("global_cooldown_minutes", 90, 0, Integer.MAX_VALUE);
        WARNING_SECONDS = builder.defineInRange("warning_seconds", 20, 0, Integer.MAX_VALUE);
        MAX_CONCURRENT_INVASIONS = builder.defineInRange("max_concurrent_invasions", 1, 0, 16);
        builder.pop();

        builder.push("ascension");
        ENABLE_ASCENSION = builder.define("enable_ascension", true);
        ONE_PENDING_OFFER_PER_PLAYER = builder.define("one_pending_offer_per_player", true);
        REMIND_PENDING_OFFERS = builder.define("remind_pending_offers", true);
        SHOW_ASCENSION_NOTIFICATIONS = builder.define("show_ascension_notifications", true);
        builder.pop();

        builder.push("compat");
        AUTO_DETECT = builder.define("auto_detect", true);
        DEFAULT_ENABLE_DETECTED_INTEGRATIONS = builder.define("default_enable_detected_integrations", true);
        builder.push("apotheosis");
        APOTHEOSIS_ENABLED = builder.define("enabled", true);
        APOTHEOSIS_MODE = builder.define("mode", "hybrid");
        APOTHEOSIS_LOOT_UNSAFE_MODE_POLICY = builder.define("loot_unsafe_mode_policy", "block");
        ALLOW_WORLD_TIER_CONDITIONS = builder.define("allow_world_tier_conditions", true);
        ALLOW_WORLD_TIER_STAGE_UNLOCKS = builder.define("allow_world_tier_stage_unlocks", true);
        ALLOW_WORLD_TIER_LOOT_SCALING = builder.define("allow_world_tier_loot_scaling", true);
        ALLOW_WORLD_TIER_INVASION_SCALING = builder.define("allow_world_tier_invasion_scaling", true);
        ALLOW_WORLD_TIER_MUTATOR_SCALING = builder.define("allow_world_tier_mutator_scaling", true);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }
}


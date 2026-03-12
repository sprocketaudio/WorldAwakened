package net.sprocketgames.worldawakened.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WorldAwakenedCommonConfig {
    private WorldAwakenedCommonConfig() {
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_MOD;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ModConfigSpec.BooleanValue VALIDATION_LOGGING;

    public static final ModConfigSpec.ConfigValue<String> PROGRESSION_MODE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_STAGE_UNLOCKS;
    public static final ModConfigSpec.BooleanValue ALLOW_STAGE_REGRESSION;
    public static final ModConfigSpec.BooleanValue ALLOW_HIDDEN_STAGES_IN_DEBUG;

    public static final ModConfigSpec.BooleanValue ENABLE_MUTATORS;
    public static final ModConfigSpec.IntValue MAX_MUTATORS_PER_MOB;
    public static final ModConfigSpec.BooleanValue RESPECT_BOSS_BLACKLIST;
    public static final ModConfigSpec.BooleanValue APPLY_ON_SPAWN_ONLY;

    public static final ModConfigSpec.BooleanValue ENABLE_SPAWN_SCALING;
    public static final ModConfigSpec.BooleanValue ALLOW_PACK_SIZE_ADJUSTMENTS;
    public static final ModConfigSpec.BooleanValue ALLOW_SPECIAL_REINFORCEMENTS;
    public static final ModConfigSpec.DoubleValue NATURAL_SPAWN_SCALING_CAP;

    public static final ModConfigSpec.BooleanValue ENABLE_LOOT_EVOLUTION;
    public static final ModConfigSpec.BooleanValue INJECT_ONLY;
    public static final ModConfigSpec.BooleanValue ALLOW_ENTRY_REPLACEMENT;

    public static final ModConfigSpec.BooleanValue ENABLE_INVASIONS;
    public static final ModConfigSpec.IntValue GLOBAL_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WARNING_SECONDS;
    public static final ModConfigSpec.IntValue MAX_CONCURRENT_INVASIONS;

    public static final ModConfigSpec.BooleanValue AUTO_DETECT;
    public static final ModConfigSpec.BooleanValue DEFAULT_ENABLE_DETECTED_INTEGRATIONS;

    public static final ModConfigSpec.BooleanValue APOTHEOSIS_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> APOTHEOSIS_MODE;
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
        VALIDATION_LOGGING = builder.define("validation_logging", true);
        builder.pop();

        builder.push("progression");
        PROGRESSION_MODE = builder.define("mode", "global");
        ANNOUNCE_STAGE_UNLOCKS = builder.define("announce_stage_unlocks", true);
        ALLOW_STAGE_REGRESSION = builder.define("allow_stage_regression", false);
        ALLOW_HIDDEN_STAGES_IN_DEBUG = builder.define("allow_hidden_stages_in_debug", true);
        builder.pop();

        builder.push("mutators");
        ENABLE_MUTATORS = builder.define("enable_mutators", true);
        MAX_MUTATORS_PER_MOB = builder.defineInRange("max_mutators_per_mob", 2, 0, 16);
        RESPECT_BOSS_BLACKLIST = builder.define("respect_boss_blacklist", true);
        APPLY_ON_SPAWN_ONLY = builder.define("apply_on_spawn_only", true);
        builder.pop();

        builder.push("spawning");
        ENABLE_SPAWN_SCALING = builder.define("enable_spawn_scaling", true);
        ALLOW_PACK_SIZE_ADJUSTMENTS = builder.define("allow_pack_size_adjustments", true);
        ALLOW_SPECIAL_REINFORCEMENTS = builder.define("allow_special_reinforcements", true);
        NATURAL_SPAWN_SCALING_CAP = builder.defineInRange("natural_spawn_scaling_cap", 2.0D, 0.1D, 16.0D);
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

        builder.push("compat");
        AUTO_DETECT = builder.define("auto_detect", true);
        DEFAULT_ENABLE_DETECTED_INTEGRATIONS = builder.define("default_enable_detected_integrations", true);
        builder.push("apotheosis");
        APOTHEOSIS_ENABLED = builder.define("enabled", true);
        APOTHEOSIS_MODE = builder.define("mode", "hybrid");
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


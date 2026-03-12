package net.sprocketgames.worldawakened.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class WorldAwakenedClientConfig {
    private WorldAwakenedClientConfig() {
    }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_AFFIX_NAMES;
    public static final ModConfigSpec.BooleanValue SHOW_STAGE_TOASTS;
    public static final ModConfigSpec.BooleanValue SHOW_INVASION_WARNING_OVERLAY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("client");
        SHOW_AFFIX_NAMES = builder.define("show_affix_names", true);
        SHOW_STAGE_TOASTS = builder.define("show_stage_toasts", true);
        SHOW_INVASION_WARNING_OVERLAY = builder.define("show_invasion_warning_overlay", true);
        builder.pop();
        SPEC = builder.build();
    }
}


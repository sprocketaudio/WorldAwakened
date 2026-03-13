package net.sprocketgames.worldawakened.config;

public final class WorldAwakenedFeatureGates {
    private WorldAwakenedFeatureGates() {
    }

    public static boolean modEnabled() {
        return WorldAwakenedCommonConfig.ENABLE_MOD.get();
    }

    public static boolean validationLoggingEnabled() {
        return WorldAwakenedCommonConfig.VALIDATION_LOGGING.get();
    }

    public static boolean debugLoggingEnabled() {
        return WorldAwakenedCommonConfig.DEBUG_LOGGING.get();
    }

    public static boolean apotheosisEnabled() {
        return WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get();
    }

    public static boolean ascensionEnabled() {
        return WorldAwakenedCommonConfig.ENABLE_ASCENSION.get();
    }
}


package net.sprocketgames.worldawakened.debug;

import org.slf4j.Logger;

import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;

public final class WorldAwakenedLog {
    private WorldAwakenedLog() {
    }

    public static void info(Logger logger, WorldAwakenedLogCategory category, String message, Object... args) {
        logger.info(prefix(category) + message, args);
    }

    public static void warn(Logger logger, WorldAwakenedLogCategory category, String message, Object... args) {
        logger.warn(prefix(category) + message, args);
    }

    public static void error(Logger logger, WorldAwakenedLogCategory category, String message, Object... args) {
        logger.error(prefix(category) + message, args);
    }

    public static void debug(Logger logger, WorldAwakenedLogCategory category, String message, Object... args) {
        if (WorldAwakenedFeatureGates.debugLoggingEnabled()) {
            logger.debug(prefix(category) + message, args);
        }
    }

    private static String prefix(WorldAwakenedLogCategory category) {
        return "[WorldAwakened:" + category.name() + "] ";
    }
}


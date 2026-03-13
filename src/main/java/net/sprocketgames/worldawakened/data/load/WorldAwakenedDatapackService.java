package net.sprocketgames.worldawakened.data.load;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;

public final class WorldAwakenedDatapackService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AtomicReference<WorldAwakenedDatapackSnapshot> snapshot = new AtomicReference<>(WorldAwakenedDatapackSnapshot.empty());
    private final AtomicLong generationCounter = new AtomicLong(0L);

    public WorldAwakenedDatapackSnapshot currentSnapshot() {
        return snapshot.get();
    }

    public WorldAwakenedDatapackSnapshot compile(ResourceManager resourceManager) {
        return WorldAwakenedDatapackLoader.load(resourceManager, LOGGER);
    }

    public void publish(WorldAwakenedDatapackSnapshot next, String source) {
        WorldAwakenedDatapackSnapshot previous = snapshot.get();
        long generation = generationCounter.incrementAndGet();
        WorldAwakenedDatapackSnapshot published = next.withGeneration(generation);
        snapshot.set(published);
        WorldAwakenedLog.info(
                LOGGER,
                WorldAwakenedLogCategory.DATA_LOAD,
                "Published datapack snapshot from {} at {} generation={} previous_generation={}",
                source,
                published.loadedAt(),
                published.generation(),
                previous.generation());
    }

    public WorldAwakenedDatapackSnapshot pinSnapshot(WorldAwakenedDatapackSnapshot pinned, String context) {
        WorldAwakenedDatapackSnapshot live = snapshot.get();
        if (live.generation() != pinned.generation()) {
            WorldAwakenedLog.warn(
                    LOGGER,
                    WorldAwakenedLogCategory.DATA_LOAD,
                    "Blocked mixed compiled graph state: code={} context={} pinned_generation={} pinned_loaded_at={} live_generation={} live_loaded_at={}",
                    WorldAwakenedDiagnosticCodes.COMPILED_GRAPH_MIXED_STATE_BLOCKED,
                    context,
                    pinned.generation(),
                    pinned.loadedAt(),
                    live.generation(),
                    live.loadedAt());
        }
        return pinned;
    }

    public WorldAwakenedDatapackSnapshot reload(ResourceManager resourceManager, String source) {
        WorldAwakenedDatapackSnapshot next = compile(resourceManager);
        publish(next, source);
        return next;
    }

    public WorldAwakenedDatapackSnapshot reloadFromServer(MinecraftServer server, String source) {
        return reload(server.getResourceManager(), source);
    }
}


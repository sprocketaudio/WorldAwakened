package net.sprocketgames.worldawakened.data.load;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;

public final class WorldAwakenedDatapackService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final AtomicReference<WorldAwakenedDatapackSnapshot> snapshot = new AtomicReference<>(WorldAwakenedDatapackSnapshot.empty());

    public WorldAwakenedDatapackSnapshot currentSnapshot() {
        return snapshot.get();
    }

    public WorldAwakenedDatapackSnapshot compile(ResourceManager resourceManager) {
        return WorldAwakenedDatapackLoader.load(resourceManager, LOGGER);
    }

    public void publish(WorldAwakenedDatapackSnapshot next, String source) {
        snapshot.set(next);
        WorldAwakenedLog.info(LOGGER, WorldAwakenedLogCategory.DATA_LOAD, "Published datapack snapshot from {} at {}", source, next.loadedAt());
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


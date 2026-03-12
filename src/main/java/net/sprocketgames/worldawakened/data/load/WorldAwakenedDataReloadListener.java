package net.sprocketgames.worldawakened.data.load;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class WorldAwakenedDataReloadListener extends SimplePreparableReloadListener<WorldAwakenedDatapackSnapshot> {
    private final WorldAwakenedDatapackService datapackService;

    public WorldAwakenedDataReloadListener(WorldAwakenedDatapackService datapackService) {
        this.datapackService = datapackService;
    }

    @Override
    protected WorldAwakenedDatapackSnapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.push("worldawakened_prepare");
        WorldAwakenedDatapackSnapshot snapshot = datapackService.compile(resourceManager);
        profiler.pop();
        return snapshot;
    }

    @Override
    protected void apply(WorldAwakenedDatapackSnapshot object, ResourceManager resourceManager, ProfilerFiller profiler) {
        profiler.push("worldawakened_apply");
        datapackService.publish(object, "resource_reload_apply");
        profiler.pop();
    }
}


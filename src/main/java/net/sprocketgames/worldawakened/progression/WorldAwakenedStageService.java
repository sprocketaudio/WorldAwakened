package net.sprocketgames.worldawakened.progression;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.event.WorldAwakenedStageUnlockedEvent;

public final class WorldAwakenedStageService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WorldAwakenedDatapackService datapackService;
    private final AtomicReference<CachedStageRegistry> registryCache = new AtomicReference<>(
            new CachedStageRegistry(Instant.EPOCH, WorldAwakenedStageRegistry.empty()));

    public WorldAwakenedStageService(WorldAwakenedDatapackService datapackService) {
        this.datapackService = datapackService;
    }

    public WorldAwakenedStageRegistry stageRegistry() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CachedStageRegistry cached = registryCache.get();
        if (cached.loadedAt().equals(snapshot.loadedAt())) {
            return cached.registry();
        }

        WorldAwakenedStageRegistry rebuilt = WorldAwakenedStageRegistry.from(snapshot.data().stages());
        registryCache.set(new CachedStageRegistry(snapshot.loadedAt(), rebuilt));
        logAliasConflicts(rebuilt);
        return rebuilt;
    }

    public boolean isStageUnlocked(ServerLevel level, ResourceLocation stageId) {
        return isStageUnlocked(level, null, stageId);
    }

    public boolean isStageUnlocked(ServerLevel level, ServerPlayer player, ResourceLocation stageId) {
        SelectedStageState selectedState = selectStageState(level, player);
        return WorldAwakenedStageStateEngine.isStageUnlocked(stageRegistry(), selectedState.stageState(), stageId);
    }

    public WorldAwakenedStageMutationResult unlockStage(ServerLevel level, ResourceLocation stageId, String source) {
        return unlockStage(level, null, stageId, source);
    }

    public WorldAwakenedStageMutationResult unlockStage(ServerLevel level, ServerPlayer player, ResourceLocation stageId, String source) {
        SelectedStageState selectedState = selectStageState(level, player);
        String normalizedSource = source == null ? "unknown" : source;
        WorldAwakenedStageMutationResult result = WorldAwakenedStageStateEngine.unlockStage(
                stageRegistry(),
                selectedState.stageState(),
                stageId,
                normalizedSource,
                System.currentTimeMillis());

        if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED && result.resolvedStageId().isPresent()) {
            NeoForge.EVENT_BUS.post(new WorldAwakenedStageUnlockedEvent(
                    level,
                    player,
                    result.resolvedStageId().get(),
                    normalizedSource,
                    selectedState.configuredMode(),
                    selectedState.effectiveMode(),
                    selectedState.usedWorldFallback()));
        }

        return result;
    }

    public WorldAwakenedStageMutationResult lockStage(ServerLevel level, ResourceLocation stageId) {
        return lockStage(level, null, stageId);
    }

    public WorldAwakenedStageMutationResult lockStage(ServerLevel level, ServerPlayer player, ResourceLocation stageId) {
        SelectedStageState selectedState = selectStageState(level, player);
        return WorldAwakenedStageStateEngine.lockStage(
                stageRegistry(),
                selectedState.stageState(),
                stageId,
                true);
    }

    public Set<ResourceLocation> getUnlockedStages(ServerLevel level) {
        return getUnlockedStages(level, null);
    }

    public Set<ResourceLocation> getUnlockedStages(ServerLevel level, ServerPlayer player) {
        SelectedStageState selectedState = selectStageState(level, player);
        return Set.copyOf(selectedState.stageState().unlockedStages());
    }

    public Optional<ResourceLocation> getHighestStageInGroup(ServerLevel level, String groupId) {
        return getHighestStageInGroup(level, null, groupId);
    }

    public Optional<ResourceLocation> getHighestStageInGroup(ServerLevel level, ServerPlayer player, String groupId) {
        SelectedStageState selectedState = selectStageState(level, player);
        return WorldAwakenedStageStateEngine.getHighestUnlockedStageInGroup(
                stageRegistry(),
                selectedState.stageState(),
                groupId);
    }

    public WorldAwakenedEffectiveStageContext getEffectiveStageContext(ServerLevel level, ServerPlayer player, Entity entity) {
        SelectedStageState selectedState = selectStageState(level, player);
        WorldAwakenedStageRegistry registry = stageRegistry();
        Set<ResourceLocation> unlockedStages = Set.copyOf(selectedState.stageState().unlockedStages());
        Set<ResourceLocation> inactiveStages = registry.inactiveStageIds(unlockedStages);

        return new WorldAwakenedEffectiveStageContext(
                selectedState.configuredMode(),
                selectedState.effectiveMode(),
                selectedState.usedWorldFallback(),
                unlockedStages,
                inactiveStages,
                selectedState.stageState().activeGroupStages());
    }

    private SelectedStageState selectStageState(ServerLevel level, ServerPlayer player) {
        WorldAwakenedProgressionMode configuredMode = WorldAwakenedProgressionMode.fromConfig(WorldAwakenedCommonConfig.PROGRESSION_MODE.get());

        if (configuredMode == WorldAwakenedProgressionMode.PER_PLAYER && player != null) {
            WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(level);
            return new SelectedStageState(
                    playerData.getOrCreate(player.getUUID()),
                    configuredMode,
                    WorldAwakenedProgressionMode.PER_PLAYER,
                    false);
        }

        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        boolean useFallback = configuredMode != WorldAwakenedProgressionMode.GLOBAL;
        return new SelectedStageState(
                worldData,
                configuredMode,
                WorldAwakenedProgressionMode.GLOBAL,
                useFallback);
    }

    private static void logAliasConflicts(WorldAwakenedStageRegistry registry) {
        if (registry.aliasConflicts().isEmpty()) {
            return;
        }
        registry.aliasConflicts().forEach((alias, ids) -> WorldAwakenedLog.warn(
                LOGGER,
                WorldAwakenedLogCategory.CORE,
                "Stage alias conflict for {} resolved deterministically to {} (candidates={})",
                alias,
                registry.aliasMappings().get(alias),
                ids));
    }

    private record CachedStageRegistry(Instant loadedAt, WorldAwakenedStageRegistry registry) {
    }

    private record SelectedStageState(
            WorldAwakenedMutableStageState stageState,
            WorldAwakenedProgressionMode configuredMode,
            WorldAwakenedProgressionMode effectiveMode,
            boolean usedWorldFallback) {
    }
}


package net.sprocketgames.worldawakened.debug;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionStateEditor;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;

public final class WorldAwakenedDebugCommandService {
    private final WorldAwakenedStageService stageService;
    private final WorldAwakenedAscensionService ascensionService;

    public WorldAwakenedDebugCommandService(
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService) {
        this.stageService = stageService;
        this.ascensionService = ascensionService;
    }

    public WorldAwakenedProgressionStateEditor.StageResetSummary resetWorldStages(ServerLevel level) {
        return WorldAwakenedProgressionStateEditor.resetStages(WorldAwakenedWorldProgressionSavedData.get(level));
    }

    public WorldAwakenedProgressionStateEditor.StageResetSummary resetPlayerStages(ServerPlayer player) {
        return WorldAwakenedProgressionStateEditor.resetStages(playerState(player));
    }

    public WorldAwakenedStageMutationResult clearWorldStage(ServerLevel level, ResourceLocation stageId) {
        return WorldAwakenedProgressionStateEditor.clearStage(
                stageService.stageRegistry(),
                WorldAwakenedWorldProgressionSavedData.get(level),
                stageId);
    }

    public WorldAwakenedStageMutationResult clearPlayerStage(ServerPlayer player, ResourceLocation stageId) {
        return WorldAwakenedProgressionStateEditor.clearStage(
                stageService.stageRegistry(),
                playerState(player),
                stageId);
    }

    public WorldAwakenedProgressionStateEditor.TriggerResetSummary resetWorldTriggers(ServerLevel level) {
        return WorldAwakenedProgressionStateEditor.resetTriggers(WorldAwakenedWorldProgressionSavedData.get(level));
    }

    public WorldAwakenedProgressionStateEditor.TriggerResetSummary resetPlayerTriggers(ServerPlayer player) {
        return WorldAwakenedProgressionStateEditor.resetTriggers(playerState(player));
    }

    public boolean clearWorldTrigger(ServerLevel level, ResourceLocation triggerId) {
        return WorldAwakenedProgressionStateEditor.clearTrigger(WorldAwakenedWorldProgressionSavedData.get(level), triggerId);
    }

    public boolean clearPlayerTrigger(ServerPlayer player, ResourceLocation triggerId) {
        return WorldAwakenedProgressionStateEditor.clearTrigger(playerState(player), triggerId);
    }

    public WorldAwakenedProgressionStateEditor.RuleResetSummary resetWorldRules(ServerLevel level) {
        return WorldAwakenedProgressionStateEditor.resetRules(WorldAwakenedWorldProgressionSavedData.get(level));
    }

    public WorldAwakenedProgressionStateEditor.RuleResetSummary resetPlayerRules(ServerPlayer player) {
        return WorldAwakenedProgressionStateEditor.resetRules(playerState(player));
    }

    public boolean clearWorldRule(ServerLevel level, ResourceLocation ruleId) {
        return WorldAwakenedProgressionStateEditor.clearRule(WorldAwakenedWorldProgressionSavedData.get(level), ruleId);
    }

    public boolean clearPlayerRule(ServerPlayer player, ResourceLocation ruleId) {
        return WorldAwakenedProgressionStateEditor.clearRule(playerState(player), ruleId);
    }

    public WorldAwakenedAscensionService.ResetSummary resetPlayerAscension(ServerPlayer player) {
        return ascensionService.resetAll(player.serverLevel(), player);
    }

    public boolean clearPlayerAscensionInstance(ServerPlayer player, String instanceId) {
        return ascensionService.clearOfferInstance(player.serverLevel(), player, instanceId);
    }

    private static WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState(ServerPlayer player) {
        return WorldAwakenedPlayerProgressionSavedData.get(player.serverLevel()).getOrCreate(player.getUUID());
    }
}

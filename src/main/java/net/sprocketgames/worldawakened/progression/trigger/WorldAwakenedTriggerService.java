package net.sprocketgames.worldawakened.progression.trigger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.data.definition.SourceScope;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedMutableTriggerState;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationStatus;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedWorldProgressionSavedData;
import net.sprocketgames.worldawakened.progression.event.WorldAwakenedNamedTriggerEvent;
import net.sprocketgames.worldawakened.rules.WorldAwakenedRuleRunResult;
import net.sprocketgames.worldawakened.rules.WorldAwakenedRuleService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedEntityContextView;

public final class WorldAwakenedTriggerService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<CompiledTriggerAction> ACTION_PRIORITY_ORDER = Comparator
            .comparingInt(CompiledTriggerAction::priority)
            .reversed()
            .thenComparingInt(CompiledTriggerAction::authoredIndex);

    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final WorldAwakenedRuleService ruleService;
    private final WorldAwakenedAscensionService ascensionService;

    public WorldAwakenedTriggerService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedRuleService ruleService,
            WorldAwakenedAscensionService ascensionService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
        this.ruleService = ruleService;
        this.ascensionService = ascensionService;
    }

    public WorldAwakenedTriggerRunResult onPlayerEnteredDimension(ServerPlayer player, ResourceLocation dimensionId) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.PLAYER_ENTER_DIMENSION,
                player.serverLevel(),
                player,
                null,
                dimensionId,
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                false,
                Optional.empty()),
                Optional.empty(),
                true);
    }

    public WorldAwakenedTriggerRunResult onAdvancementCompleted(ServerPlayer player, ResourceLocation advancementId) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.ADVANCEMENT_COMPLETED,
                player.serverLevel(),
                player,
                null,
                player.serverLevel().dimension().location(),
                Optional.of(advancementId),
                Optional.empty(),
                Set.of(),
                false,
                Optional.empty()),
                Optional.empty(),
                true);
    }

    public WorldAwakenedTriggerRunResult onEntityKilled(ServerLevel level, ServerPlayer killer, Entity killedEntity) {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType());
        Set<ResourceLocation> entityTags = collectEntityTags(killedEntity);
        boolean mappedBoss = snapshot
                .data()
                .bossClassifier()
                .isBoss(new EntityContext(entityId, entityTags, mobCategory(killedEntity)));

        WorldAwakenedTriggerRunResult entityKilledResult = evaluateAndApply(snapshot, new TriggerEventContext(
                WorldAwakenedTriggerTypes.ENTITY_KILLED,
                level,
                killer,
                killedEntity,
                level.dimension().location(),
                Optional.empty(),
                Optional.of(entityId),
                entityTags,
                mappedBoss,
                Optional.empty()),
                Optional.empty(),
                true);

        WorldAwakenedTriggerRunResult bossKilledResult = evaluateAndApply(snapshot, new TriggerEventContext(
                WorldAwakenedTriggerTypes.BOSS_KILLED,
                level,
                killer,
                killedEntity,
                level.dimension().location(),
                Optional.empty(),
                Optional.of(entityId),
                entityTags,
                mappedBoss,
                Optional.empty()),
                Optional.empty(),
                false);

        return sum(entityKilledResult, bossKilledResult);
    }

    public WorldAwakenedTriggerRunResult onItemCrafted(ServerPlayer player) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.ITEM_CRAFTED,
                player.serverLevel(),
                player,
                null,
                player.serverLevel().dimension().location(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                false,
                Optional.empty()),
                Optional.empty(),
                true);
    }

    public WorldAwakenedTriggerRunResult onBlockPlaced(ServerPlayer player) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.BLOCK_PLACED,
                player.serverLevel(),
                player,
                null,
                player.serverLevel().dimension().location(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                false,
                Optional.empty()),
                Optional.empty(),
                true);
    }

    public WorldAwakenedTriggerRunResult onBlockBroken(ServerPlayer player) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.BLOCK_BROKEN,
                player.serverLevel(),
                player,
                null,
                player.serverLevel().dimension().location(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                false,
                Optional.empty()),
                Optional.empty(),
                true);
    }

    public WorldAwakenedTriggerRunResult fireManualTrigger(ServerLevel level, ServerPlayer player, ResourceLocation triggerRuleId) {
        return evaluateAndApply(new TriggerEventContext(
                WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                level,
                player,
                null,
                level.dimension().location(),
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                false,
                Optional.of(triggerRuleId)),
                Optional.of(triggerRuleId),
                true);
    }

    private WorldAwakenedTriggerRunResult evaluateAndApply(
            TriggerEventContext eventContext,
            Optional<ResourceLocation> targetedRuleId,
            boolean runRules) {
        return evaluateAndApply(datapackService.currentSnapshot(), eventContext, targetedRuleId, runRules);
    }

    private WorldAwakenedTriggerRunResult evaluateAndApply(
            WorldAwakenedDatapackSnapshot pinnedSnapshot,
            TriggerEventContext eventContext,
            Optional<ResourceLocation> targetedRuleId,
            boolean runRules) {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.pinSnapshot(
                pinnedSnapshot,
                "trigger.evaluate_and_apply:" + eventContext.triggerType());

        long nowMillis = System.currentTimeMillis();
        StageSnapshots stageSnapshots = readStageSnapshots(eventContext.level(), eventContext.player());
        TriggerStateSnapshots triggerSnapshots = readTriggerSnapshots(eventContext.level(), eventContext.player());
        WorldAwakenedStageRegistry stageRegistry = stageService.stageRegistry();

        WorldAwakenedTriggerMatchResult matchResult = snapshot.data().triggerRules().isEmpty()
                ? new WorldAwakenedTriggerMatchResult(0, List.of())
                : WorldAwakenedTriggerMatcher.match(
                        snapshot.data().triggerRules().values(),
                        stageRegistry,
                        new WorldAwakenedTriggerMatchContext(
                                eventContext.triggerType(),
                                targetedRuleId,
                                eventContext.player() != null,
                                eventContext.dimensionId(),
                                eventContext.advancementId(),
                                eventContext.entityId(),
                                eventContext.entityTags(),
                                eventContext.bossFlagMapMatch(),
                                eventContext.manualTriggerId(),
                                nowMillis,
                                stageSnapshots.worldStageSnapshot(),
                                stageSnapshots.playerStageSnapshot(),
                                triggerSnapshots.worldSnapshot(),
                                triggerSnapshots.playerSnapshot()));

        int stageUnlocks = 0;
        int stageLocks = 0;
        int emittedEvents = 0;
        int counterUpdates = 0;
        int executedRules = 0;

        for (WorldAwakenedTriggerMatchResult.MatchedRule matchedRule : matchResult.matchedRules()) {
            TriggerRuleDefinition rule = matchedRule.rule();
            SourceScope scope = matchedRule.effectiveScope();
            WorldAwakenedMutableTriggerState liveTriggerState = selectLiveTriggerState(eventContext.level(), eventContext.player(), scope);
            if (liveTriggerState == null) {
                continue;
            }

            executedRules++;
            ServerPlayer scopedPlayer = scope == SourceScope.PLAYER ? eventContext.player() : null;
            for (CompiledTriggerAction action : compileActions(rule.actions())) {
                switch (action.actionPath()) {
                    case "unlock_stage" -> {
                        Optional<ResourceLocation> stageIdOpt = readResourceLocation(action.parameters(), "stage");
                        if (stageIdOpt.isEmpty()) {
                            continue;
                        }
                        WorldAwakenedStageMutationResult result = stageService.unlockStage(
                                eventContext.level(),
                                scopedPlayer,
                                stageIdOpt.get(),
                                "trigger:" + rule.id());
                        if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED) {
                            stageUnlocks++;
                        }
                    }
                    case "lock_stage" -> {
                        Optional<ResourceLocation> stageIdOpt = readResourceLocation(action.parameters(), "stage");
                        if (stageIdOpt.isEmpty()) {
                            continue;
                        }
                        WorldAwakenedStageMutationResult result = stageService.lockStage(
                                eventContext.level(),
                                scopedPlayer,
                                stageIdOpt.get());
                        if (result.status() == WorldAwakenedStageMutationStatus.LOCKED) {
                            stageLocks++;
                        }
                    }
                    case "emit_named_event" -> {
                        Optional<ResourceLocation> eventIdOpt = readResourceLocation(action.parameters(), "event");
                        if (eventIdOpt.isEmpty()) {
                            continue;
                        }
                        NeoForge.EVENT_BUS.post(new WorldAwakenedNamedTriggerEvent(
                                eventContext.level(),
                                scopedPlayer,
                                rule.id(),
                                eventIdOpt.get(),
                                scope));
                        emittedEvents++;
                    }
                    case "increment_counter" -> {
                        String counterKey = readString(action.parameters(), "counter")
                                .orElse(rule.id().toString());
                        int amount = readInt(action.parameters(), "amount").orElse(1);
                        liveTriggerState.triggerCounters().merge(counterKey, amount, Integer::sum);
                        liveTriggerState.markDirty();
                        counterUpdates++;
                    }
                    case "send_warning_message" -> {
                        if (scopedPlayer == null) {
                            continue;
                        }
                        String message = readMessageText(action.parameters());
                        if (!message.isBlank()) {
                            scopedPlayer.sendSystemMessage(Component.literal(message));
                        }
                    }
                    case "grant_ascension_offer" -> {
                        if (scopedPlayer == null) {
                            continue;
                        }
                        Optional<ResourceLocation> offerId = readResourceLocation(action.parameters(), "offer");
                        if (offerId.isEmpty()) {
                            continue;
                        }
                        ascensionService.grantOfferFromRule(
                                eventContext.level(),
                                scopedPlayer,
                                offerId.get(),
                                "trigger:" + rule.id());
                    }
                    default -> WorldAwakenedLog.debug(
                            LOGGER,
                            WorldAwakenedLogCategory.PIPELINE,
                            "Skipping unsupported trigger action {} on {}",
                            action.typeId(),
                            rule.id());
                }
            }

            long cooldownDuration = WorldAwakenedTriggerMatcher.cooldownDurationMillis(rule);
            if (cooldownDuration > 0L) {
                liveTriggerState.triggerCooldowns().put(rule.id().toString(), nowMillis + cooldownDuration);
                liveTriggerState.markDirty();
            }
            if (rule.oneShot()) {
                liveTriggerState.consumedOneShotTriggers().add(rule.id().toString());
                liveTriggerState.markDirty();
            }
        }

        WorldAwakenedRuleRunResult ruleResult = runRules
                ? ruleService.evaluate(new WorldAwakenedRuleService.RuleEventContext(
                        eventContext.triggerType().toString(),
                        eventContext.level(),
                        eventContext.player(),
                        eventContext.entity(),
                        eventContext.dimensionId(),
                        eventContext.entityTags(),
                        eventContext.bossFlagMapMatch(),
                        false,
                        nowMillis,
                        eventContext.level().getGameTime(),
                        stageSnapshots.worldStageSnapshot(),
                        stageSnapshots.playerStageSnapshot(),
                        Optional.empty()))
                : WorldAwakenedRuleRunResult.empty("none");

        return new WorldAwakenedTriggerRunResult(
                ruleResult.traceId(),
                matchResult.evaluatedRules(),
                matchResult.matchedCount(),
                executedRules,
                stageUnlocks,
                stageLocks,
                emittedEvents,
                counterUpdates,
                ruleResult.evaluatedRules(),
                ruleResult.matchedRules(),
                ruleResult.executedRules(),
                ruleResult.stageUnlocks(),
                ruleResult.stageLocks());
    }

    private StageSnapshots readStageSnapshots(ServerLevel level, ServerPlayer player) {
        Set<ResourceLocation> worldStages = stageService.getUnlockedStages(level);
        Set<ResourceLocation> playerStages = player == null ? Set.of() : stageService.getUnlockedStages(level, player);
        return new StageSnapshots(worldStages, playerStages);
    }

    private TriggerStateSnapshots readTriggerSnapshots(ServerLevel level, ServerPlayer player) {
        WorldAwakenedWorldProgressionSavedData worldData = WorldAwakenedWorldProgressionSavedData.get(level);
        WorldAwakenedTriggerStateSnapshot worldSnapshot = new WorldAwakenedTriggerStateSnapshot(
                Map.copyOf(worldData.triggerCooldowns()),
                Set.copyOf(worldData.consumedOneShotTriggers()));

        if (player == null) {
            return new TriggerStateSnapshots(worldSnapshot, WorldAwakenedTriggerStateSnapshot.empty());
        }

        WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(level);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState playerState = playerData.getOrCreate(player.getUUID());
        WorldAwakenedTriggerStateSnapshot playerSnapshot = new WorldAwakenedTriggerStateSnapshot(
                Map.copyOf(playerState.triggerCooldowns()),
                Set.copyOf(playerState.consumedOneShotTriggers()));
        return new TriggerStateSnapshots(worldSnapshot, playerSnapshot);
    }

    private WorldAwakenedMutableTriggerState selectLiveTriggerState(
            ServerLevel level,
            ServerPlayer player,
            SourceScope scope) {
        if (scope == SourceScope.PLAYER) {
            if (player == null) {
                return null;
            }
            WorldAwakenedPlayerProgressionSavedData playerData = WorldAwakenedPlayerProgressionSavedData.get(level);
            return playerData.getOrCreate(player.getUUID());
        }
        return WorldAwakenedWorldProgressionSavedData.get(level);
    }

    private static Set<ResourceLocation> collectEntityTags(Entity entity) {
        LinkedHashSet<ResourceLocation> tags = new LinkedHashSet<>();
        entity.getType().builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location()));
        return Set.copyOf(tags);
    }

    private static String mobCategory(Entity entity) {
        MobCategory category = entity.getType().getCategory();
        return category == null ? "misc" : category.getName();
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            ResourceLocation parsed = parseResourceLocation(object.get(key).getAsString());
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                String value = object.get(key).getAsString();
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> readInt(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return Optional.of(object.get(key).getAsInt());
            }
        }
        return Optional.empty();
    }

    private static String readMessageText(JsonObject action) {
        if (!action.has("message")) {
            return "";
        }
        JsonElement message = action.get("message");
        if (message.isJsonPrimitive()) {
            return message.getAsString();
        }
        return message.toString();
    }

    private static ResourceLocation parseResourceLocation(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(rawValue);
        if (parsed != null) {
            return parsed;
        }
        if (!rawValue.contains(":")) {
            return ResourceLocation.tryParse("worldawakened:" + rawValue);
        }
        return null;
    }

    private static List<CompiledTriggerAction> compileActions(List<JsonElement> rawActions) {
        List<CompiledTriggerAction> compiled = new ArrayList<>(rawActions.size());
        for (int index = 0; index < rawActions.size(); index++) {
            JsonElement action = rawActions.get(index);
            if (!action.isJsonObject()) {
                continue;
            }
            JsonObject node = action.getAsJsonObject();
            if (!isNodeEnabled(node)) {
                continue;
            }
            Optional<ResourceLocation> typeOpt = readResourceLocation(node, "type");
            if (typeOpt.isEmpty()) {
                continue;
            }
            int priority = readInt(node, "priority").orElse(0);
            compiled.add(new CompiledTriggerAction(
                    typeOpt.get(),
                    typeOpt.get().getPath().toLowerCase(Locale.ROOT),
                    readParametersObject(node),
                    priority,
                    index));
        }
        compiled.sort(ACTION_PRIORITY_ORDER);
        return List.copyOf(compiled);
    }
    private static JsonObject readParametersObject(JsonObject object) {
        if (!object.has("parameters") || !object.get("parameters").isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject("parameters");
    }
    private static boolean isNodeEnabled(JsonObject node) {
        if (!node.has("enabled")) {
            return true;
        }
        JsonElement enabled = node.get("enabled");
        if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            return true;
        }
        return enabled.getAsBoolean();
    }

    private static WorldAwakenedTriggerRunResult sum(WorldAwakenedTriggerRunResult first, WorldAwakenedTriggerRunResult second) {
        String trace = "none".equals(first.traceId()) ? second.traceId()
                : ("none".equals(second.traceId()) ? first.traceId() : first.traceId() + "," + second.traceId());
        return new WorldAwakenedTriggerRunResult(
                trace,
                first.evaluatedRules() + second.evaluatedRules(),
                first.matchedRules() + second.matchedRules(),
                first.executedRules() + second.executedRules(),
                first.stageUnlocks() + second.stageUnlocks(),
                first.stageLocks() + second.stageLocks(),
                first.emittedEvents() + second.emittedEvents(),
                first.counterUpdates() + second.counterUpdates(),
                first.evaluatedGenericRules() + second.evaluatedGenericRules(),
                first.matchedGenericRules() + second.matchedGenericRules(),
                first.executedGenericRules() + second.executedGenericRules(),
                first.genericRuleStageUnlocks() + second.genericRuleStageUnlocks(),
                first.genericRuleStageLocks() + second.genericRuleStageLocks());
    }

    private record TriggerEventContext(
            ResourceLocation triggerType,
            ServerLevel level,
            ServerPlayer player,
            Entity entity,
            ResourceLocation dimensionId,
            Optional<ResourceLocation> advancementId,
            Optional<ResourceLocation> entityId,
            Set<ResourceLocation> entityTags,
            boolean bossFlagMapMatch,
            Optional<ResourceLocation> manualTriggerId) {
    }

    private record StageSnapshots(Set<ResourceLocation> worldStageSnapshot, Set<ResourceLocation> playerStageSnapshot) {
    }

    private record TriggerStateSnapshots(
            WorldAwakenedTriggerStateSnapshot worldSnapshot,
            WorldAwakenedTriggerStateSnapshot playerSnapshot) {
    }

    private record EntityContext(
            ResourceLocation entityId,
            Set<ResourceLocation> entityTags,
            String mobCategory) implements WorldAwakenedEntityContextView {
    }

    private record CompiledTriggerAction(
            ResourceLocation typeId,
            String actionPath,
            JsonObject parameters,
            int priority,
            int authoredIndex) {
    }
}

package net.sprocketgames.worldawakened.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionOfferRuntime;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionRewardEffects;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierService;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.difficulty.WorldAwakenedEffectiveDifficultyScalarService;
import net.sprocketgames.worldawakened.debug.WorldAwakenedComponentDebugFormatter;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDebugCommandService;
import net.sprocketgames.worldawakened.invasion.WorldAwakenedInvasionService;
import net.sprocketgames.worldawakened.loot.WorldAwakenedLootService;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedGlowStyleState;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutatorService;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutationProvenance;
import net.sprocketgames.worldawakened.network.WorldAwakenedNetwork;
import net.sprocketgames.worldawakened.progression.WorldAwakenedEffectiveStageContext;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionStateEditor;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationStatus;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.trigger.WorldAwakenedTriggerRunResult;
import net.sprocketgames.worldawakened.progression.trigger.WorldAwakenedTriggerService;
import net.sprocketgames.worldawakened.progression.trigger.WorldAwakenedTriggerTypes;
import net.sprocketgames.worldawakened.rules.WorldAwakenedRuleService;

public final class WorldAwakenedCommands {
    private static final double AIMED_MOB_INSPECT_RANGE = 64.0D;

    private WorldAwakenedCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedTriggerService triggerService,
            WorldAwakenedRuleService ruleService,
            WorldAwakenedAscensionService ascensionService,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedLootService lootService,
            WorldAwakenedInvasionService invasionService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        WorldAwakenedDebugCommandService debugCommandService = new WorldAwakenedDebugCommandService(stageService, ascensionService);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wa")
                .then(Commands.literal("reload")
                        .then(Commands.literal("validate")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runReloadValidate(context.getSource(), datapackService, ascensionService))))
                .then(buildStageTree(stageService))
                .then(buildTriggerTree(datapackService, triggerService))
                .then(buildDumpTree(ruleService))
                .then(Commands.literal("compat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> runCompatList(context.getSource()))))
                .then(buildInvasionTree(datapackService, invasionService))
                .then(buildLootTree(datapackService, lootService))
                .then(buildAscensionTree(datapackService, ascensionService))
                .then(buildDifficultyTree(difficultyScalarService))
                .then(buildMobTree(mutatorService));

        if (WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            root.then(buildDebugTree(
                    datapackService,
                    stageService,
                    ascensionService,
                    mutatorService,
                    lootService,
                    invasionService,
                    difficultyScalarService,
                    debugCommandService));
        }

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildStageTree(WorldAwakenedStageService stageService) {
        return Commands.literal("stage")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> runStageList(
                                context.getSource(),
                                stageService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> runStageList(
                                        context.getSource(),
                                        stageService,
                                        EntityArgument.getPlayer(context, "player"))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runStageList(
                                                context.getSource(),
                                                stageService,
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("global")
                                .executes(context -> runStageList(
                                        context.getSource(),
                                        stageService,
                                        null))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestStageIds(stageService))
                                .executes(context -> runStageUnlock(
                                        context.getSource(),
                                        stageService,
                                        ResourceLocationArgument.getId(context, "id"),
                                        sourcePlayer(context.getSource())))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runStageUnlock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> runStageUnlock(
                                                        context.getSource(),
                                                        stageService,
                                                        ResourceLocationArgument.getId(context, "id"),
                                                        EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("global")
                                        .executes(context -> runStageUnlock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                null)))))
                .then(Commands.literal("lock")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestStageIds(stageService))
                                .executes(context -> runStageLock(
                                        context.getSource(),
                                        stageService,
                                        ResourceLocationArgument.getId(context, "id"),
                                        sourcePlayer(context.getSource())))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runStageLock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> runStageLock(
                                                        context.getSource(),
                                                        stageService,
                                                        ResourceLocationArgument.getId(context, "id"),
                                                        EntityArgument.getPlayer(context, "player")))))
                                .then(Commands.literal("global")
                                        .executes(context -> runStageLock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                null)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTriggerTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedTriggerService triggerService) {
        return Commands.literal("trigger")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("fire")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestTriggerIds(datapackService))
                                .executes(context -> runTriggerFire(
                                        context.getSource(),
                                        datapackService,
                                        triggerService,
                                        ResourceLocationArgument.getId(context, "id"),
                                        sourcePlayer(context.getSource())))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runTriggerFire(
                                                context.getSource(),
                                                datapackService,
                                                triggerService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                EntityArgument.getPlayer(context, "player")))
                                        .then(Commands.literal("dimension")
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(context -> runTriggerFire(
                                                                context.getSource(),
                                                                datapackService,
                                                                triggerService,
                                                                ResourceLocationArgument.getId(context, "id"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                DimensionArgument.getDimension(context, "dimension"))))))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> runTriggerFire(
                                                        context.getSource(),
                                                        datapackService,
                                                        triggerService,
                                                        ResourceLocationArgument.getId(context, "id"),
                                                        EntityArgument.getPlayer(context, "player")))
                                                .then(Commands.literal("dimension")
                                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                                .executes(context -> runTriggerFire(
                                                                        context.getSource(),
                                                                        datapackService,
                                                                        triggerService,
                                                                        ResourceLocationArgument.getId(context, "id"),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        DimensionArgument.getDimension(context, "dimension")))))))
                                .then(Commands.literal("global")
                                        .executes(context -> runTriggerFire(
                                                context.getSource(),
                                                datapackService,
                                                triggerService,
                                                ResourceLocationArgument.getId(context, "id"),
                                                null))
                                        .then(Commands.literal("dimension")
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(context -> runTriggerFire(
                                                                context.getSource(),
                                                                datapackService,
                                                                triggerService,
                                                                ResourceLocationArgument.getId(context, "id"),
                                                                null,
                                                                DimensionArgument.getDimension(context, "dimension"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDumpTree(WorldAwakenedRuleService ruleService) {
        LiteralArgumentBuilder<CommandSourceStack> activeRules = Commands.literal("active_rules")
                .executes(context -> runDumpActiveRules(
                        context.getSource(),
                        ruleService,
                        sourcePlayer(context.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> runDumpActiveRules(
                                context.getSource(),
                                ruleService,
                                EntityArgument.getPlayer(context, "player")))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDumpActiveRules(
                                                context.getSource(),
                                                ruleService,
                                                EntityArgument.getPlayer(context, "player"),
                                                DimensionArgument.getDimension(context, "dimension"))))))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> runDumpActiveRules(
                                        context.getSource(),
                                        ruleService,
                                        EntityArgument.getPlayer(context, "player")))
                                .then(Commands.literal("dimension")
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> runDumpActiveRules(
                                                        context.getSource(),
                                                        ruleService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        DimensionArgument.getDimension(context, "dimension")))))))
                .then(Commands.literal("global")
                        .executes(context -> runDumpActiveRules(
                                context.getSource(),
                                ruleService,
                                null))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDumpActiveRules(
                                                context.getSource(),
                                                ruleService,
                                                null,
                                                DimensionArgument.getDimension(context, "dimension"))))));
        return Commands.literal("dump")
                .requires(source -> source.hasPermission(2))
                .then(activeRules);
    }

    private static int runReloadValidate(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService) {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.reloadFromServer(source.getServer(), "command:/wa reload validate");
        int reconciledPlayers = ascensionService.reconcileAllOnlinePlayers(source.getServer(), "datapack_reload");
        sendOperatorSummary(source,
                "World Awakened reload validation complete: " + snapshot.validationSummary().toCompactString(),
                true);
        sendOperatorDetail(source, "ascension_reconciled_players=" + reconciledPlayers);
        if (!snapshot.validationSummary().diagnostics().isEmpty()
                && (snapshot.validationSummary().errorCount() > 0
                        || snapshot.validationSummary().warningCount() > 0
                        || showVerboseOperatorDetails())) {
            sendOperatorDetail(source, "first_diagnostic=" + snapshot.validationSummary().diagnostics().get(0).asLogLine());
        }
        if (showVerboseOperatorDetails() && !snapshot.validationSummary().traceEvents().isEmpty()) {
            sendOperatorDetail(source, "first_trace=" + snapshot.validationSummary().traceEvents().get(0).asLogLine());
        }
        return snapshot.validationSummary().errorCount() == 0 ? 1 : 0;
    }

    private static int runStageList(
            CommandSourceStack source,
            WorldAwakenedStageService stageService,
            ServerPlayer targetPlayer) {
        ServerLevel level = targetPlayer != null
                ? targetPlayer.serverLevel()
                : requireCommandLevel(source, "World Awakened stage operations require a server level context");
        if (level == null) {
            return 0;
        }

        WorldAwakenedEffectiveStageContext context = stageService.getEffectiveStageContext(level, targetPlayer, source.getEntity());
        WorldAwakenedStageRegistry registry = stageService.stageRegistry();
        String targetLabel = targetPlayer == null
                ? "global"
                : "player=" + targetPlayer.getGameProfile().getName();

        sendOperatorSummary(
                source,
                "World Awakened stage state: target=" + targetLabel
                        + ", configured=" + context.configuredMode().serializedName()
                        + ", effective=" + context.effectiveMode().serializedName()
                        + ", fallback=" + context.usedWorldFallback()
                        + ", unlocked=" + context.unlockedStages().size(),
                false);

        for (StageDefinition stage : registry.orderedStages()) {
            boolean unlocked = context.unlockedStages().contains(stage.id());
            String lockState = unlocked ? "UNLOCKED" : "LOCKED";
            MutableComponent line = Component.literal(" - [" + lockState + "] ")
                    .append(displayComponent(stage.displayName(), source, stage.id().toString()).withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", stage.id().toString(), "Copy stage ID"));
            sendInspectLine(source, line);
            if (showVerboseOperatorDetails()) {
                String group = stage.progressionGroup().map(value -> " group=" + value).orElse("");
                String hidden = stage.visibleToPlayers() ? "" : " hidden=true";
                sendOperatorDetail(source, Component.literal("id=" + stage.id()
                        + group
                        + " policy="
                        + stage.unlockPolicy().name().toLowerCase(Locale.ROOT)
                        + hidden)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        for (ResourceLocation inactiveStage : context.inactiveUnlockedStages()) {
            sendInspectLine(source, Component.literal(" - [UNLOCKED][INACTIVE] ")
                    .append(Component.literal(inactiveStage.toString()).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", inactiveStage.toString(), "Copy inactive stage ID")));
        }

        return context.unlockedStages().size();
    }

    private static int runStageUnlock(
            CommandSourceStack source,
            WorldAwakenedStageService stageService,
            ResourceLocation stageId,
            ServerPlayer targetPlayer) {
        ServerLevel level = targetPlayer != null
                ? targetPlayer.serverLevel()
                : requireCommandLevel(source, "World Awakened stage operations require a server level context");
        if (level == null) {
            return 0;
        }

        WorldAwakenedStageMutationResult result = stageService.unlockStage(
                level,
                targetPlayer,
                stageId,
                "command:/wa stage unlock");
        return reportStageMutation(source, result, targetPlayer);
    }

    private static int runStageLock(
            CommandSourceStack source,
            WorldAwakenedStageService stageService,
            ResourceLocation stageId,
            ServerPlayer targetPlayer) {
        ServerLevel level = targetPlayer != null
                ? targetPlayer.serverLevel()
                : requireCommandLevel(source, "World Awakened stage operations require a server level context");
        if (level == null) {
            return 0;
        }

        WorldAwakenedStageMutationResult result = stageService.lockStage(level, targetPlayer, stageId);
        return reportStageMutation(source, result, targetPlayer);
    }

    private static int runTriggerFire(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedTriggerService triggerService,
            ResourceLocation triggerRuleId,
            ServerPlayer targetPlayer) {
        ServerLevel level = targetPlayer != null
                ? targetPlayer.serverLevel()
                : requireCommandLevel(source, "World Awakened trigger operations require a server level context");
        if (level == null) {
            return 0;
        }
        return runTriggerFire(source, datapackService, triggerService, triggerRuleId, targetPlayer, level);
    }

    private static int runTriggerFire(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedTriggerService triggerService,
            ResourceLocation triggerRuleId,
            ServerPlayer targetPlayer,
            ServerLevel level) {
        if (level == null) {
            sendOperatorFailure(source, "This command needs a world context.");
            return 0;
        }

        TriggerRuleDefinition triggerRule = datapackService.currentSnapshot().data().triggerRules().get(triggerRuleId);
        if (triggerRule == null) {
            sendOperatorFailure(source, "That trigger is not loaded: " + triggerRuleId);
            return 0;
        }
        if (!triggerRule.triggerType().equals(WorldAwakenedTriggerTypes.MANUAL_DEBUG)) {
            sendOperatorFailure(source, "That trigger cannot be fired from the command line: " + triggerRuleId);
            return 0;
        }

        WorldAwakenedTriggerRunResult result = triggerService.fireManualTrigger(level, targetPlayer, triggerRuleId);
        String targetLabel = targetPlayer == null
                ? "global"
                : "player=" + targetPlayer.getGameProfile().getName();
        sendOperatorSummary(
                source,
                "World Awakened trigger fire "
                        + triggerRuleId
                        + " target="
                        + targetLabel
                        + " dimension="
                        + level.dimension().location()
                        + ": matched="
                        + result.matchedRules()
                        + ", executed="
                        + result.executedRules()
                        + ", unlocks="
                        + result.stageUnlocks(),
                true);
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, Component.literal("trace="
                    + result.traceId()
                    + " evaluated="
                    + result.evaluatedRules()
                    + ", matched="
                    + result.matchedRules()
                    + ", executed="
                    + result.executedRules()
                    + ", unlocks="
                    + result.stageUnlocks()
                    + ", emits="
                    + result.emittedEvents()
                    + ", counters="
                    + result.counterUpdates()
                    + ", rules_eval="
                    + result.evaluatedGenericRules()
                    + ", rules_matched="
                    + result.matchedGenericRules()
                    + ", rules_executed="
                    + result.executedGenericRules()
                    + ", rules_unlocks="
                    + result.genericRuleStageUnlocks()
                    + ", rules_locks="
                    + result.genericRuleStageLocks())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return result.executedRules();
    }

    private static int runDumpActiveRules(
            CommandSourceStack source,
            WorldAwakenedRuleService ruleService,
            ServerPlayer targetPlayer) {
        ServerLevel level = targetPlayer != null
                ? targetPlayer.serverLevel()
                : requireCommandLevel(source, "World Awakened rule inspection requires a server level context");
        if (level == null) {
            return 0;
        }
        return runDumpActiveRules(source, ruleService, targetPlayer, level);
    }

    private static int runDumpActiveRules(
            CommandSourceStack source,
            WorldAwakenedRuleService ruleService,
            ServerPlayer targetPlayer,
            ServerLevel level) {
        if (level == null) {
            sendOperatorFailure(source, "This command needs a world context.");
            return 0;
        }

        var views = ruleService.inspectActiveRules(level, targetPlayer);
        long activeCount = views.stream().filter(WorldAwakenedRuleService.ActiveRuleView::eligible).count();
        String targetLabel = targetPlayer == null
                ? "global"
                : "player=" + targetPlayer.getGameProfile().getName();
        sendOperatorSummary(source, "World Awakened active rules: target="
                + targetLabel
                + " dimension="
                + level.dimension().location()
                + " eligible="
                + activeCount
                + "/"
                + views.size()
                + (targetPlayer == null ? " scope=world" : " scope=player+world"),
                false);

        for (WorldAwakenedRuleService.ActiveRuleView view : views) {
            MutableComponent line = Component.literal(" - ")
                    .append(Component.literal(view.ruleId().toString()).withStyle(view.eligible() ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(Component.literal(view.eligible() ? "[eligible]" : "[inactive]")
                            .withStyle(view.eligible() ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(" scope=" + view.executionScope().name().toLowerCase(Locale.ROOT)))
                    .append(Component.literal(" priority=" + view.priority()));
            if (view.consumed()) {
                line.append(Component.literal(" consumed").withStyle(ChatFormatting.DARK_GRAY));
            }
            sendInspectLine(source, line);
            if (showVerboseOperatorDetails()) {
                String reason = view.rejectionReason().map(Enum::name).orElse("none");
                sendOperatorDetail(source, Component.literal("cooldown_ms="
                        + view.cooldownRemainingMillis()
                        + " reason="
                        + reason
                        + " detail="
                        + view.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        return (int) activeCount;
    }

    private static int runCompatList(CommandSourceStack source) {
        boolean autoDetect = WorldAwakenedCommonConfig.AUTO_DETECT.get();
        boolean defaultEnableDetected = WorldAwakenedCommonConfig.DEFAULT_ENABLE_DETECTED_INTEGRATIONS.get();
        boolean apotheosisLoaded = ModList.get().isLoaded("apotheosis");
        boolean apotheosisEnabled = WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get();
        boolean apotheosisActive = apotheosisLoaded && apotheosisEnabled;

        sendOperatorSummary(source,
                "World Awakened compatibility: auto_detect=" + autoDetect
                        + ", enable_detected_integrations=" + defaultEnableDetected,
                false);
        sendOperatorSummary(source,
                "Apotheosis: "
                        + (apotheosisActive ? "active" : apotheosisLoaded ? "loaded but disabled" : "not loaded")
                        + ", mode="
                        + WorldAwakenedCommonConfig.APOTHEOSIS_MODE.get(),
                false);
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, "world_tier_conditions="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_CONDITIONS.get()
                    + ", stage_unlocks="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_STAGE_UNLOCKS.get()
                    + ", loot_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_LOOT_SCALING.get()
                    + ", invasion_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_INVASION_SCALING.get()
                    + ", mutator_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_MUTATOR_SCALING.get());
        }

        return apotheosisActive ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAscensionTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService) {
        return Commands.literal("ascension")
                .then(Commands.literal("list")
                        .executes(context -> runAscensionList(
                                context.getSource(),
                                datapackService,
                                ascensionService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runAscensionList(
                                        context.getSource(),
                                        datapackService,
                                        ascensionService,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("pending")
                        .executes(context -> runAscensionPending(
                                context.getSource(),
                                datapackService,
                                ascensionService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runAscensionPending(
                                        context.getSource(),
                                        datapackService,
                                        ascensionService,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("open")
                        .executes(context -> runAscensionOpen(
                                context.getSource(),
                                ascensionService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runAscensionOpen(
                                        context.getSource(),
                                        ascensionService,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("inspect")
                        .executes(context -> runAscensionInspect(
                                context.getSource(),
                                datapackService,
                                ascensionService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runAscensionInspect(
                                        context.getSource(),
                                        datapackService,
                                        ascensionService,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("reconcile")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> runAscensionReconcile(
                                        context.getSource(),
                                        ascensionService,
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("suppress")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reward")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                                .suggests(suggestChosenRewardIds(ascensionService))
                                                .executes(context -> runAscensionSuppressReward(
                                                        context.getSource(),
                                                        datapackService,
                                                        ascensionService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "reward_id"))))))
                        .then(Commands.literal("component")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                                .suggests(suggestChosenRewardIds(ascensionService))
                                                .then(Commands.argument("component_key", StringArgumentType.word())
                                                        .suggests(suggestSuppressibleComponentKeys(ascensionService))
                                                        .executes(context -> runAscensionSuppressComponent(
                                                                context.getSource(),
                                                                datapackService,
                                                                ascensionService,
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "reward_id"),
                                                                StringArgumentType.getString(context, "component_key"))))))))
                .then(Commands.literal("unsuppress")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reward")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                                .suggests(suggestSuppressedRewardIds(ascensionService))
                                                .executes(context -> runAscensionUnsuppressReward(
                                                        context.getSource(),
                                                        datapackService,
                                                        ascensionService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "reward_id"))))))
                        .then(Commands.literal("component")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                                .suggests(suggestChosenRewardIds(ascensionService))
                                                .then(Commands.argument("component_key", StringArgumentType.word())
                                                        .suggests(suggestSuppressedComponentKeys(ascensionService))
                                                        .executes(context -> runAscensionUnsuppressComponent(
                                                                context.getSource(),
                                                                datapackService,
                                                                ascensionService,
                                                                EntityArgument.getPlayer(context, "player"),
                                                                ResourceLocationArgument.getId(context, "reward_id"),
                                                                StringArgumentType.getString(context, "component_key"))))))))
                .then(Commands.literal("grant_offer")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("offer_id", ResourceLocationArgument.id())
                                        .suggests(suggestOfferIds(datapackService))
                                        .executes(context -> runAscensionGrantOffer(
                                                context.getSource(),
                                                datapackService,
                                                ascensionService,
                                                EntityArgument.getPlayer(context, "player"),
                                                ResourceLocationArgument.getId(context, "offer_id"))))))
                .then(Commands.literal("choose")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("instance_id", StringArgumentType.word())
                                        .suggests(suggestPendingInstanceIds(ascensionService))
                                        .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                                .suggests(suggestPendingRewardIds(ascensionService))
                                                .executes(context -> runAscensionChoose(
                                                        context.getSource(),
                                                        datapackService,
                                                        ascensionService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "instance_id"),
                                                        ResourceLocationArgument.getId(context, "reward_id")))))))
                .then(Commands.literal("active")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                        .suggests(suggestActiveRewardIds(ascensionService))
                                        .executes(context -> runAscensionChooseActive(
                                                context.getSource(),
                                                datapackService,
                                                ascensionService,
                                                EntityArgument.getPlayer(context, "player"),
                                                ResourceLocationArgument.getId(context, "reward_id"))))))
                .then(Commands.literal("reopen")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("instance_id", StringArgumentType.word())
                                        .suggests(suggestResolvedInstanceIds(ascensionService))
                                        .executes(context -> runAscensionReopen(
                                                context.getSource(),
                                                ascensionService,
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "instance_id"))))))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("instance_id", StringArgumentType.word())
                                        .suggests(suggestAnyInstanceIds(ascensionService))
                                        .executes(context -> runAscensionClear(
                                                context.getSource(),
                                                ascensionService,
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "instance_id"))))))
                .then(Commands.literal("revoke")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("reward_id", ResourceLocationArgument.id())
                                        .suggests(suggestChosenRewardIds(ascensionService))
                                        .executes(context -> runAscensionRevoke(
                                                context.getSource(),
                                                datapackService,
                                                ascensionService,
                                                EntityArgument.getPlayer(context, "player"),
                                                ResourceLocationArgument.getId(context, "reward_id"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMobTree(WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("mob")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspect")
                        .executes(context -> runMobInspectLookedAt(
                                context.getSource(),
                                mutatorService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(context -> runMobInspect(
                                        context.getSource(),
                                        mutatorService,
                                        EntityArgument.getEntity(context, "target")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDifficultyTree(
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        return Commands.literal("difficulty")
                .then(Commands.literal("global")
                        .then(Commands.literal("get")
                                .executes(context -> runDifficultyGlobalGet(context.getSource(), difficultyScalarService)))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(context -> runDifficultyGlobalSet(
                                                context.getSource(),
                                                difficultyScalarService,
                                                DoubleArgumentType.getDouble(context, "value")))))
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runDifficultyGlobalReset(context.getSource(), difficultyScalarService))))
                .then(Commands.literal("personal")
                        .then(Commands.literal("get")
                                .executes(context -> runDifficultyPersonalGet(context.getSource(), difficultyScalarService)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(context -> runDifficultyPersonalSet(
                                                context.getSource(),
                                                difficultyScalarService,
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("world")
                        .then(Commands.literal("get")
                                .executes(context -> runDifficultyWorldGet(context.getSource(), difficultyScalarService)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(context -> runDifficultyWorldSet(
                                                context.getSource(),
                                                difficultyScalarService,
                                                DoubleArgumentType.getDouble(context, "value"))))))
                .then(Commands.literal("vote")
                        .then(Commands.literal("yes")
                                .executes(context -> runDifficultyVote(context.getSource(), difficultyScalarService, true)))
                        .then(Commands.literal("no")
                                .executes(context -> runDifficultyVote(context.getSource(), difficultyScalarService, false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildInvasionTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedInvasionService invasionService) {
        return Commands.literal("invasion")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("profile_id", ResourceLocationArgument.id())
                                .suggests(suggestEnabledInvasionProfileIds(invasionService))
                                .executes(context -> runInvasionStart(
                                        context.getSource(),
                                        invasionService,
                                        ResourceLocationArgument.getId(context, "profile_id")))))
                .then(Commands.literal("stop")
                        .executes(context -> runInvasionStop(
                                context.getSource(),
                                invasionService)))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("active")
                                .executes(context -> runInvasionInspectActive(
                                        context.getSource(),
                                        invasionService)))
                        .then(Commands.literal("profile")
                                .then(Commands.argument("profile_id", ResourceLocationArgument.id())
                                        .suggests(suggestInvasionProfileIds(datapackService))
                                        .executes(context -> runInvasionInspectProfile(
                                                context.getSource(),
                                                datapackService,
                                                invasionService,
                                                ResourceLocationArgument.getId(context, "profile_id"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLootTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedLootService lootService) {
        return Commands.literal("loot")
                .requires(source -> source.hasPermission(2))
                .then(buildLootEvaluateBranch(lootService))
                .then(buildLootForceProfileBranch(datapackService, lootService));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLootEvaluateBranch(
            WorldAwakenedLootService lootService) {
        return Commands.literal("evaluate")
                .then(Commands.argument("target_type", StringArgumentType.word())
                        .suggests(suggestLootTargetTypes())
                        .then(Commands.argument("target_id", ResourceLocationArgument.id())
                                .executes(context -> runLootEvaluate(
                                        context.getSource(),
                                        lootService,
                                        StringArgumentType.getString(context, "target_type"),
                                        ResourceLocationArgument.getId(context, "target_id"),
                                        sourcePlayer(context.getSource()),
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runLootEvaluate(
                                                context.getSource(),
                                                lootService,
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                sourcePlayer(context.getSource()),
                                                DimensionArgument.getDimension(context, "dimension"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runLootEvaluate(
                                                context.getSource(),
                                                lootService,
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                EntityArgument.getPlayer(context, "player"),
                                                null))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> runLootEvaluate(
                                                        context.getSource(),
                                                        lootService,
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        DimensionArgument.getDimension(context, "dimension")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLootForceProfileBranch(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedLootService lootService) {
        return Commands.literal("force_profile")
                .then(Commands.argument("profile_id", ResourceLocationArgument.id())
                        .suggests(suggestLootProfileIds(datapackService))
                        .then(Commands.argument("target_type", StringArgumentType.word())
                                .suggests(suggestLootTargetTypes())
                                .then(Commands.argument("target_id", ResourceLocationArgument.id())
                                        .executes(context -> runLootForceProfile(
                                                context.getSource(),
                                                lootService,
                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                sourcePlayer(context.getSource()),
                                                null))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> runLootForceProfile(
                                                        context.getSource(),
                                                        lootService,
                                                        ResourceLocationArgument.getId(context, "profile_id"),
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        sourcePlayer(context.getSource()),
                                                        DimensionArgument.getDimension(context, "dimension"))))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> runLootForceProfile(
                                                        context.getSource(),
                                                        lootService,
                                                        ResourceLocationArgument.getId(context, "profile_id"),
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        null))
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(context -> runLootForceProfile(
                                                                context.getSource(),
                                                                lootService,
                                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                                StringArgumentType.getString(context, "target_type"),
                                                                ResourceLocationArgument.getId(context, "target_id"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                DimensionArgument.getDimension(context, "dimension"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedLootService lootService,
            WorldAwakenedInvasionService invasionService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            WorldAwakenedDebugCommandService debugCommandService) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
                .requires(source -> source.hasPermission(2) && WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());

        LiteralArgumentBuilder<CommandSourceStack> resetBranch = Commands.literal("reset")
                .then(buildDebugResetGlobalBranch("global", debugCommandService))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("stages")
                                        .executes(context -> runDebugResetPlayerStages(
                                                context.getSource(),
                                                debugCommandService,
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("triggers")
                                        .executes(context -> runDebugResetPlayerTriggers(
                                                context.getSource(),
                                                debugCommandService,
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("rules")
                                        .executes(context -> runDebugResetPlayerRules(
                                                context.getSource(),
                                                debugCommandService,
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("ascension")
                                        .executes(context -> runDebugResetPlayerAscension(
                                                context.getSource(),
                                                debugCommandService,
                                                EntityArgument.getPlayer(context, "player"))))
                                .then(Commands.literal("all")
                                        .executes(context -> runDebugResetPlayerAll(
                                                context.getSource(),
                                                debugCommandService,
                                                EntityArgument.getPlayer(context, "player"))))));

        LiteralArgumentBuilder<CommandSourceStack> clearBranch = Commands.literal("clear")
                .then(buildDebugClearGlobalBranch("global", datapackService, stageService, debugCommandService))
                .then(Commands.literal("player")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("stage")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests(suggestStageIds(stageService))
                                                .executes(context -> runDebugClearPlayerStage(
                                                        context.getSource(),
                                                        debugCommandService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "id")))))
                                .then(Commands.literal("trigger")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests(suggestTriggerIds(datapackService))
                                                .executes(context -> runDebugClearPlayerTrigger(
                                                        context.getSource(),
                                                        debugCommandService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "id")))))
                                .then(Commands.literal("rule")
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests(suggestRuleIds(datapackService))
                                                .executes(context -> runDebugClearPlayerRule(
                                                        context.getSource(),
                                                        debugCommandService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        ResourceLocationArgument.getId(context, "id")))))
                                .then(Commands.literal("ascension_instance")
                                        .then(Commands.argument("instance_id", StringArgumentType.word())
                                                .suggests(suggestAnyInstanceIds(ascensionService))
                                                .executes(context -> runDebugClearPlayerAscensionInstance(
                                                        context.getSource(),
                                                        debugCommandService,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "instance_id")))))));

        debug.then(resetBranch);
        debug.then(clearBranch);
        debug.then(buildDebugMutatorsTree(datapackService, mutatorService));
        debug.then(buildDebugSpawnTree(mutatorService));
        debug.then(buildDebugLootTree(datapackService, lootService));
        debug.then(buildDebugInvasionTree(invasionService));
        debug.then(buildDebugDifficultyTree(difficultyScalarService));
        debug.then(buildDebugPressureTree(mutatorService, difficultyScalarService));
        return debug;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugResetGlobalBranch(
            String literal,
            WorldAwakenedDebugCommandService debugCommandService) {
        return Commands.literal(literal)
                .then(Commands.literal("stages")
                        .executes(context -> runDebugResetWorldStages(context.getSource(), debugCommandService)))
                .then(Commands.literal("triggers")
                        .executes(context -> runDebugResetWorldTriggers(context.getSource(), debugCommandService)))
                .then(Commands.literal("rules")
                        .executes(context -> runDebugResetWorldRules(context.getSource(), debugCommandService)))
                .then(Commands.literal("all")
                        .executes(context -> runDebugResetWorldAll(context.getSource(), debugCommandService)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugClearGlobalBranch(
            String literal,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedDebugCommandService debugCommandService) {
        return Commands.literal(literal)
                .then(Commands.literal("stage")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestStageIds(stageService))
                                .executes(context -> runDebugClearWorldStage(
                                        context.getSource(),
                                        debugCommandService,
                                        ResourceLocationArgument.getId(context, "id")))))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestTriggerIds(datapackService))
                                .executes(context -> runDebugClearWorldTrigger(
                                        context.getSource(),
                                        debugCommandService,
                                        ResourceLocationArgument.getId(context, "id")))))
                .then(Commands.literal("rule")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(suggestRuleIds(datapackService))
                                .executes(context -> runDebugClearWorldRule(
                                        context.getSource(),
                                        debugCommandService,
                                        ResourceLocationArgument.getId(context, "id")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugMutatorsTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("mutators")
                .then(Commands.literal("summary")
                        .executes(context -> runDebugMutatorSummary(context.getSource(), mutatorService)))
                .then(buildDebugMutatorsEvaluateBranch(mutatorService))
                .then(buildDebugMutatorsForcePoolBranch(datapackService, mutatorService))
                .then(buildDebugMutatorsForceMutatorBranch(datapackService, mutatorService));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugMutatorsEvaluateBranch(
            WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("evaluate")
                .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                        .suggests(suggestEntityTypeIds())
                        .executes(context -> runDebugMutatorEvaluate(
                                context.getSource(),
                                mutatorService,
                                ResourceLocationArgument.getId(context, "entity_id"),
                                null,
                                null,
                                null,
                                null))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> runDebugMutatorEvaluate(
                                        context.getSource(),
                                        mutatorService,
                                        ResourceLocationArgument.getId(context, "entity_id"),
                                        DimensionArgument.getDimension(context, "dimension"),
                                        null,
                                        null,
                                        null))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugMutatorEvaluate(
                                                                context.getSource(),
                                                                mutatorService,
                                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                                DimensionArgument.getDimension(context, "dimension"),
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z")))))))
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                .executes(context -> runDebugMutatorEvaluate(
                                                        context.getSource(),
                                                        mutatorService,
                                                        ResourceLocationArgument.getId(context, "entity_id"),
                                                        null,
                                                        DoubleArgumentType.getDouble(context, "x"),
                                                        DoubleArgumentType.getDouble(context, "y"),
                                                        DoubleArgumentType.getDouble(context, "z")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugMutatorsForcePoolBranch(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("force_pool")
                .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                        .suggests(suggestEntityTypeIds())
                        .then(Commands.argument("pool_id", ResourceLocationArgument.id())
                                .suggests(suggestMutationPoolIds(datapackService))
                                .executes(context -> runDebugMutatorForcePool(
                                        context.getSource(),
                                        mutatorService,
                                        ResourceLocationArgument.getId(context, "entity_id"),
                                        ResourceLocationArgument.getId(context, "pool_id"),
                                        null,
                                        null,
                                        null,
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDebugMutatorForcePool(
                                                context.getSource(),
                                                mutatorService,
                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                ResourceLocationArgument.getId(context, "pool_id"),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                null,
                                                null,
                                                null))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> runDebugMutatorForcePool(
                                                                        context.getSource(),
                                                                        mutatorService,
                                                                        ResourceLocationArgument.getId(context, "entity_id"),
                                                                        ResourceLocationArgument.getId(context, "pool_id"),
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z")))))))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugMutatorForcePool(
                                                                context.getSource(),
                                                                mutatorService,
                                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                                ResourceLocationArgument.getId(context, "pool_id"),
                                                                null,
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugMutatorsForceMutatorBranch(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("force_mutator")
                .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                        .suggests(suggestEntityTypeIds())
                        .then(Commands.argument("mutator_id", ResourceLocationArgument.id())
                                .suggests(suggestMutatorIds(datapackService))
                                .executes(context -> runDebugMutatorForceMutator(
                                        context.getSource(),
                                        mutatorService,
                                        ResourceLocationArgument.getId(context, "entity_id"),
                                        ResourceLocationArgument.getId(context, "mutator_id"),
                                        null,
                                        null,
                                        null,
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDebugMutatorForceMutator(
                                                context.getSource(),
                                                mutatorService,
                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                ResourceLocationArgument.getId(context, "mutator_id"),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                null,
                                                null,
                                                null))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> runDebugMutatorForceMutator(
                                                                        context.getSource(),
                                                                        mutatorService,
                                                                        ResourceLocationArgument.getId(context, "entity_id"),
                                                                        ResourceLocationArgument.getId(context, "mutator_id"),
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z")))))))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugMutatorForceMutator(
                                                                context.getSource(),
                                                                mutatorService,
                                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                                ResourceLocationArgument.getId(context, "mutator_id"),
                                                                null,
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugSpawnTree(
            WorldAwakenedMutatorService mutatorService) {
        return Commands.literal("spawn")
                .then(Commands.literal("test")
                        .then(Commands.argument("entity_id", ResourceLocationArgument.id())
                                .suggests(suggestEntityTypeIds())
                                .executes(context -> runDebugSpawnTest(
                                        context.getSource(),
                                        mutatorService,
                                        ResourceLocationArgument.getId(context, "entity_id"),
                                        null,
                                        null,
                                        null,
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDebugSpawnTest(
                                                context.getSource(),
                                                mutatorService,
                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                null,
                                                null,
                                                null))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> runDebugSpawnTest(
                                                                        context.getSource(),
                                                                        mutatorService,
                                                                        ResourceLocationArgument.getId(context, "entity_id"),
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z")))))))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugSpawnTest(
                                                                context.getSource(),
                                                                mutatorService,
                                                                ResourceLocationArgument.getId(context, "entity_id"),
                                                                null,
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugLootTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedLootService lootService) {
        return Commands.literal("loot")
                .then(buildDebugLootEvaluateBranch(lootService))
                .then(buildDebugLootForceProfileBranch(datapackService, lootService));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugLootEvaluateBranch(
            WorldAwakenedLootService lootService) {
        return Commands.literal("evaluate")
                .then(Commands.argument("target_type", StringArgumentType.word())
                        .suggests(suggestLootTargetTypes())
                        .then(Commands.argument("target_id", ResourceLocationArgument.id())
                                .executes(context -> runDebugLootEvaluate(
                                        context.getSource(),
                                        lootService,
                                        StringArgumentType.getString(context, "target_type"),
                                        ResourceLocationArgument.getId(context, "target_id"),
                                        sourcePlayer(context.getSource()),
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDebugLootEvaluate(
                                                context.getSource(),
                                                lootService,
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                sourcePlayer(context.getSource()),
                                                DimensionArgument.getDimension(context, "dimension"))))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> runDebugLootEvaluate(
                                                context.getSource(),
                                                lootService,
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                EntityArgument.getPlayer(context, "player"),
                                                null))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> runDebugLootEvaluate(
                                                        context.getSource(),
                                                        lootService,
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        DimensionArgument.getDimension(context, "dimension")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugLootForceProfileBranch(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedLootService lootService) {
        return Commands.literal("force_profile")
                .then(Commands.argument("profile_id", ResourceLocationArgument.id())
                        .suggests(suggestLootProfileIds(datapackService))
                        .then(Commands.argument("target_type", StringArgumentType.word())
                                .suggests(suggestLootTargetTypes())
                                .then(Commands.argument("target_id", ResourceLocationArgument.id())
                                        .executes(context -> runDebugLootForceProfile(
                                                context.getSource(),
                                                lootService,
                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                StringArgumentType.getString(context, "target_type"),
                                                ResourceLocationArgument.getId(context, "target_id"),
                                                sourcePlayer(context.getSource()),
                                                null))
                                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                .executes(context -> runDebugLootForceProfile(
                                                        context.getSource(),
                                                        lootService,
                                                        ResourceLocationArgument.getId(context, "profile_id"),
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        sourcePlayer(context.getSource()),
                                                        DimensionArgument.getDimension(context, "dimension"))))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> runDebugLootForceProfile(
                                                        context.getSource(),
                                                        lootService,
                                                        ResourceLocationArgument.getId(context, "profile_id"),
                                                        StringArgumentType.getString(context, "target_type"),
                                                        ResourceLocationArgument.getId(context, "target_id"),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        null))
                                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                                        .executes(context -> runDebugLootForceProfile(
                                                                context.getSource(),
                                                                lootService,
                                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                                StringArgumentType.getString(context, "target_type"),
                                                                ResourceLocationArgument.getId(context, "target_id"),
                                                                EntityArgument.getPlayer(context, "player"),
                                                                DimensionArgument.getDimension(context, "dimension"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugInvasionTree(
            WorldAwakenedInvasionService invasionService) {
        return Commands.literal("invasion")
                .then(Commands.literal("evaluate")
                        .then(Commands.argument("profile_id", ResourceLocationArgument.id())
                                .suggests(suggestEnabledInvasionProfileIds(invasionService))
                                .executes(context -> runDebugInvasionEvaluate(
                                        context.getSource(),
                                        invasionService,
                                        ResourceLocationArgument.getId(context, "profile_id"),
                                        null,
                                        null,
                                        null,
                                        null))
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(context -> runDebugInvasionEvaluate(
                                                context.getSource(),
                                                invasionService,
                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                DimensionArgument.getDimension(context, "dimension"),
                                                null,
                                                null,
                                                null))
                                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                .executes(context -> runDebugInvasionEvaluate(
                                                                        context.getSource(),
                                                                        invasionService,
                                                                        ResourceLocationArgument.getId(context, "profile_id"),
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z")))))))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugInvasionEvaluate(
                                                                context.getSource(),
                                                                invasionService,
                                                                ResourceLocationArgument.getId(context, "profile_id"),
                                                                null,
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugDifficultyTree(
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        return Commands.literal("difficulty")
                .then(Commands.literal("scalar")
                        .executes(context -> runDebugDifficultyScalar(
                                context.getSource(),
                                difficultyScalarService,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> runDebugDifficultyScalar(
                                        context.getSource(),
                                        difficultyScalarService,
                                        EntityArgument.getPlayer(context, "player")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugPressureTree(
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        return Commands.literal("pressure")
                .then(Commands.literal("last")
                        .executes(context -> runDebugPressureLast(
                                context.getSource(),
                                mutatorService,
                                difficultyScalarService)))
                .then(Commands.literal("replay")
                        .then(Commands.argument("id", LongArgumentType.longArg(1L))
                                .suggests(suggestPressureSnapshotIds(mutatorService))
                                .executes(context -> runDebugPressureReplay(
                                        context.getSource(),
                                        mutatorService,
                                        difficultyScalarService,
                                        LongArgumentType.getLong(context, "id")))))
                .then(Commands.literal("evaluate")
                        .executes(context -> runDebugPressureEvaluate(
                                context.getSource(),
                                difficultyScalarService,
                                null,
                                null,
                                null,
                                null,
                                sourcePlayer(context.getSource())))
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(context -> runDebugPressureEvaluate(
                                        context.getSource(),
                                        difficultyScalarService,
                                        DimensionArgument.getDimension(context, "dimension"),
                                        null,
                                        null,
                                        null,
                                        sourcePlayer(context.getSource())))
                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(context -> runDebugPressureEvaluate(
                                                                context.getSource(),
                                                                difficultyScalarService,
                                                                DimensionArgument.getDimension(context, "dimension"),
                                                                DoubleArgumentType.getDouble(context, "x"),
                                                                DoubleArgumentType.getDouble(context, "y"),
                                                                DoubleArgumentType.getDouble(context, "z"),
                                                                sourcePlayer(context.getSource())))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(context -> runDebugPressureEvaluate(
                                                                        context.getSource(),
                                                                        difficultyScalarService,
                                                                        DimensionArgument.getDimension(context, "dimension"),
                                                                        DoubleArgumentType.getDouble(context, "x"),
                                                                        DoubleArgumentType.getDouble(context, "y"),
                                                                        DoubleArgumentType.getDouble(context, "z"),
                                                                        EntityArgument.getPlayer(context, "player")))))))));
    }

    private static int runMobInspect(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            Entity target) {
        if (!(target instanceof Mob mob)) {
            sendOperatorFailure(source, Component.literal("Target must be a mob entity.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_MUTATOR_TARGET_INVALID)));
            return 0;
        }

        WorldAwakenedMutatorService.MutationInspectView inspect = mutatorService.inspectEntity(mob);
        sendInspectLine(source, "Mob inspect: entity="
                + inspect.entityTypeId()
                + " uuid="
                + mob.getStringUUID()
                + " has_provenance="
                + inspect.hasProvenance());
        sendInspectLine(source, " - mutation_pool=" + inspect.sourcePoolId().map(ResourceLocation::toString).orElse("<none>"));
        sendInspectLine(source, " - source_rules=" + formatResourceLocations(inspect.sourceRuleIds()));
        sendInspectLine(source, " - applied_mutators=" + formatResourceLocations(inspect.mutatorIds()));
        sendInspectLine(source, " - applied_components=" + formatResourceLocations(inspect.componentIds()));
        sendInspectLine(source, " - mutation_stage_context=" + formatResourceLocations(inspect.stageContext()));
        sendInspectLine(source, " - mutation_trace_id=" + (inspect.traceId().isBlank() ? "<none>" : inspect.traceId()));
        sendInspectLine(source, " - mutation_depth="
                + inspect.mutationDepth()
                + " origin_marker="
                + (inspect.originMarker().isBlank() ? "<none>" : inspect.originMarker())
                + " pipeline_processed="
                + inspect.pipelineProcessed());
        sendInspectLine(source, " - provenance_keys="
                + WorldAwakenedMutationProvenance.WA_MUTATION_SOURCE_POOL
                + ", "
                + WorldAwakenedMutationProvenance.WA_MUTATION_IDS
                + ", "
                + WorldAwakenedMutationProvenance.WA_MUTATION_COMPONENTS
                + ", "
                + WorldAwakenedMutationProvenance.WA_MUTATION_STAGE_CONTEXT
                + ", "
                + WorldAwakenedMutationProvenance.WA_MUTATION_TRACE_ID
                + ", "
                + WorldAwakenedMutationProvenance.WA_MUTATION_DEPTH
                + ", "
                + WorldAwakenedMutationProvenance.WA_ORIGIN);
        sendInspectLine(source, " - resolvable_mutators=" + formatResourceLocations(inspect.resolvedMutatorIds()));
        sendInspectLine(source, " - missing_mutators=" + formatResourceLocations(inspect.missingMutatorIds()));
        sendInspectLine(source, " - glow_style");
        if (inspect.glowStyle().isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            WorldAwakenedGlowStyleState.GlowStyleDefinition glowStyle = inspect.glowStyle().get();
            sendInspectLine(source, Component.literal("   color="
                    + WorldAwakenedGlowStyleState.formatColorHex(glowStyle.colorRgb())
                    + " brightness="
                    + formatNumber(glowStyle.brightness())
                    + " see_through_walls="
                    + glowStyle.seeThroughWalls()
                    + " pulse="
                    + glowStyle.pulse()
                    + " pulse_speed="
                    + formatNumber(glowStyle.pulseSpeed())
                    + " pulse_strength="
                    + formatNumber(glowStyle.pulseStrength())).withStyle(ChatFormatting.DARK_GRAY));
        }
        sendInspectLine(source, " - particle_visual_emitters");
        if (inspect.particleVisualEmitters().isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (net.sprocketgames.worldawakened.mutator.WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter
                    : inspect.particleVisualEmitters()) {
                String visualKey = emitter.kind()
                        == net.sprocketgames.worldawakened.mutator.WorldAwakenedVisualParticleEmitters.EmitterKind.EFFECT_VISUAL
                                ? "effect_type"
                                : "particle";
                String colorField = emitter.colorOverrideRgb()
                        .map(color -> " color=" + String.format(java.util.Locale.ROOT, "#%06x", color))
                        .orElse("");
                String sizeField = emitter.sizeOverride()
                        .map(size -> " size=" + formatNumber(size))
                        .orElse("");
                sendInspectLine(source, Component.literal("   "
                        + visualKey
                        + "="
                        + emitter.registryId()
                        + colorField
                        + sizeField
                        + " count="
                        + emitter.count()
                        + " interval_ticks="
                        + emitter.intervalTicks()
                        + " offset_x="
                        + formatNumber(emitter.offsetX())
                        + " offset_y="
                        + formatNumber(emitter.offsetY())
                        + " offset_z="
                        + formatNumber(emitter.offsetZ())
                        + " speed="
                        + formatNumber(emitter.speed())).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        sendInspectLine(source, " - failed_closed_components");
        if (inspect.failedComponents().isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (WorldAwakenedMutationProvenance.ComponentFailureEntry failure : inspect.failedComponents()) {
                sendInspectLine(source, Component.literal("   mutator="
                        + failure.mutatorId().map(ResourceLocation::toString).orElse("<none>")
                        + " component="
                        + failure.componentType()
                        + " code="
                        + failure.code()
                        + " detail="
                        + failure.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        sendInspectLine(source, " - final_attribute_deltas");
        if (inspect.attributes().isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (WorldAwakenedMutatorService.AttributeInspection attribute : inspect.attributes()) {
                double delta = attribute.currentValue() - attribute.baseValue();
                sendInspectLine(source, Component.literal("   attribute="
                        + attribute.attributeId()
                        + " base="
                        + formatNumber(attribute.baseValue())
                        + " current="
                        + formatNumber(attribute.currentValue())
                        + " delta="
                        + formatNumber(delta)
                        + " wa_modifiers="
                        + attribute.waOwnedModifiers().size()).withStyle(ChatFormatting.DARK_GRAY));
                for (WorldAwakenedMutatorService.AttributeModifierInspection modifier : attribute.waOwnedModifiers()) {
                    sendInspectLine(source, Component.literal("      modifier="
                            + modifier.modifierId()
                            + " amount="
                            + formatNumber(modifier.amount())
                            + " op="
                            + modifier.operation()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        sendInspectLine(source, Component.literal(" - foreign_state_preserved=true (World Awakened preserves non-WA entity state by default)")
                .withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int runMobInspectLookedAt(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ServerPlayer player) {
        if (player == null) {
            sendOperatorFailure(source, Component.literal("This form requires a player source. Use /wa mob inspect <target> from console or automation.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return 0;
        }

        Mob lookedAtMob = resolveLookedAtMob(player);
        if (lookedAtMob == null) {
            sendOperatorFailure(source, Component.literal("You are not aiming at a mob. Use /wa mob inspect <target> for an explicit entity or selector.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_MUTATOR_TARGET_INVALID)));
            return 0;
        }
        return runMobInspect(source, mutatorService, lookedAtMob);
    }

    private static Mob resolveLookedAtMob(ServerPlayer player) {
        double range = Math.max(AIMED_MOB_INSPECT_RANGE, player.entityInteractionRange());
        Vec3 eyePosition = player.getEyePosition();
        Vec3 viewVector = player.getViewVector(1.0F);
        Vec3 end = eyePosition.add(viewVector.scale(range));
        HitResult blockHit = player.level().clip(new ClipContext(
                eyePosition,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));
        double maxDistanceSqr = range * range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
            maxDistanceSqr = eyePosition.distanceToSqr(end);
        }

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player,
                eyePosition,
                end,
                player.getBoundingBox().expandTowards(viewVector.scale(range)).inflate(1.0D),
                entity -> entity instanceof Mob && !entity.isSpectator() && entity.isPickable(),
                maxDistanceSqr);
        return entityHit != null && entityHit.getEntity() instanceof Mob mob ? mob : null;
    }

    private static int runDebugMutatorSummary(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService) {
        WorldAwakenedMutatorService.MutatorDebugSummary summary = mutatorService.debugSummary();
        source.sendSuccess(
                () -> Component.literal("Mutator summary: generation="
                        + summary.generation()
                        + " pools="
                        + summary.poolCount()
                        + " mutators="
                        + summary.mutatorCount()
                        + " selector_entity_id_buckets="
                        + summary.selectorEntityIdBuckets()
                        + " selector_entity_tag_buckets="
                        + summary.selectorEntityTagBuckets()
                        + " wildcard_pools="
                        + summary.wildcardPools()),
                false);
        return 1;
    }

    private static int runDebugMutatorEvaluate(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ResourceLocation entityTypeId,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z) {
        EntityType<?> entityType = resolveEntityType(source, entityTypeId);
        if (entityType == null) {
            return 0;
        }
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug mutators evaluate");
        if (target.isEmpty()) {
            return 0;
        }
        WorldAwakenedMutatorService.MutatorRunResult result = mutatorService.debugEvaluate(
                target.get().level(),
                entityType,
                target.get().position());
        emitMutatorRunResult(source, result, "evaluate", false);
        return 1;
    }

    private static int runDebugMutatorForcePool(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ResourceLocation entityTypeId,
            ResourceLocation poolId,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z) {
        EntityType<?> entityType = resolveEntityType(source, entityTypeId);
        if (entityType == null) {
            return 0;
        }
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug mutators force_pool");
        if (target.isEmpty()) {
            return 0;
        }
        WorldAwakenedMutatorService.MutatorRunResult result = mutatorService.debugForcePool(
                target.get().level(),
                entityType,
                target.get().position(),
                poolId);
        emitMutatorRunResult(source, result, "force", true);
        return 1;
    }

    private static int runDebugMutatorForceMutator(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ResourceLocation entityTypeId,
            ResourceLocation mutatorId,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z) {
        EntityType<?> entityType = resolveEntityType(source, entityTypeId);
        if (entityType == null) {
            return 0;
        }
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug mutators force_mutator");
        if (target.isEmpty()) {
            return 0;
        }
        WorldAwakenedMutatorService.MutatorRunResult result = mutatorService.debugForceMutator(
                target.get().level(),
                entityType,
                target.get().position(),
                mutatorId);
        emitMutatorRunResult(source, result, "force", true);
        return 1;
    }

    private static int runDebugSpawnTest(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ResourceLocation entityTypeId,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z) {
        EntityType<?> entityType = resolveEntityType(source, entityTypeId);
        if (entityType == null) {
            return 0;
        }
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug spawn test");
        if (target.isEmpty()) {
            return 0;
        }
        WorldAwakenedMutatorService.MutatorRunResult result = mutatorService.debugSpawnTest(
                target.get().level(),
                entityType,
                target.get().position());
        emitMutatorRunResult(source, result, "live_test", false);
        return result.spawnAdded() ? 1 : 0;
    }

    private static int runInvasionStart(
            CommandSourceStack source,
            WorldAwakenedInvasionService invasionService,
            ResourceLocation profileId) {
        ServerLevel level = requireCommandLevel(source, "World Awakened invasion start requires a server level context.");
        if (level == null) {
            return 0;
        }

        WorldAwakenedInvasionService.InvasionStartResult result =
                invasionService.startInvasionFromCommand(level, profileId);
        if (!result.success()) {
            String code = result.code().isBlank()
                    ? WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID
                    : result.code();
            sendOperatorFailure(source, Component.literal("Could not start invasion: "
                    + describeInvasionRejection(code, result.detail()))
                    .append(debugCodeSuffix(code)));
            sendOperatorDetail(source, "trace_id="
                    + result.traceId()
                    + " code="
                    + code
                    + " detail="
                    + result.detail());
            return 0;
        }

        WorldAwakenedInvasionService.InvasionContextSnapshot context = result.context()
                .orElseGet(() -> invasionService.contextSnapshot(level));
        sendOperatorSummary(source, Component.literal("Invasion started: profile="
                + context.profileId().map(ResourceLocation::toString).orElse(profileId.toString())
                + " instance="
                + context.instanceId()
                + " warning_active="
                + context.warningActive()
                + " remaining_seconds="
                + context.remainingDurationSeconds()
                + " pressure_modifier="
                + formatNumber(context.pressureModifier())
                + " ")
                .append(copyButton("Copy Trace", result.traceId(), "Copy invasion trace ID")), true);
        sendOperatorDetail(source, "display_name="
                + (context.displayName().isBlank() ? "<none>" : context.displayName())
                + " reward_profile="
                + context.rewardProfile().map(ResourceLocation::toString).orElse("<none>")
                + " tags="
                + formatStringSet(context.tags()));
        sendOperatorDetail(source, "trace_id=" + result.traceId());
        return 1;
    }

    private static int runInvasionStop(
            CommandSourceStack source,
            WorldAwakenedInvasionService invasionService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened invasion stop requires a server level context.");
        if (level == null) {
            return 0;
        }

        WorldAwakenedInvasionService.InvasionStopResult result =
                invasionService.stopActiveInvasionFromCommand(level);
        if (!result.success()) {
            String code = result.code().isBlank()
                    ? WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID
                    : result.code();
            sendOperatorFailure(source, Component.literal("Could not stop invasion: "
                    + describeInvasionRejection(code, result.detail()))
                    .append(debugCodeSuffix(code)));
            sendOperatorDetail(source, "trace_id="
                    + result.traceId()
                    + " code="
                    + code
                    + " detail="
                    + result.detail());
            return 0;
        }

        WorldAwakenedInvasionService.InvasionContextSnapshot stopped = result.stoppedContext()
                .orElseGet(() -> invasionService.contextSnapshot(level));
        sendOperatorSummary(source, Component.literal("Invasion stopped: profile="
                + stopped.profileId().map(ResourceLocation::toString).orElse("<unknown>")
                + " instance="
                + stopped.instanceId()
                + " ")
                .append(copyButton("Copy Trace", result.traceId(), "Copy invasion trace ID")), true);
        sendOperatorDetail(source, "stopped_display_name="
                + (stopped.displayName().isBlank() ? "<none>" : stopped.displayName())
                + " stopped_tags="
                + formatStringSet(stopped.tags()));
        sendOperatorDetail(source, "trace_id=" + result.traceId());
        return 1;
    }

    private static int runInvasionInspectActive(
            CommandSourceStack source,
            WorldAwakenedInvasionService invasionService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened invasion inspect requires a server level context.");
        if (level == null) {
            return 0;
        }

        WorldAwakenedInvasionService.InvasionContextSnapshot context = invasionService.contextSnapshot(level);
        if (!context.invasionActive()) {
            sendOperatorSummary(source, "No active invasion. global_cooldown_remaining_ms="
                    + context.globalCooldownRemainingMillis()
                    + " loaded_profiles="
                    + invasionService.loadedProfileIds().size(), false);
            return 0;
        }

        sendOperatorSummary(source, "Invasion active: profile="
                + context.profileId().map(ResourceLocation::toString).orElse("<unknown>")
                + " display_name="
                + (context.displayName().isBlank() ? "<none>" : context.displayName())
                + " instance="
                + context.instanceId()
                + " warning_active="
                + context.warningActive()
                + " remaining_seconds="
                + context.remainingDurationSeconds()
                + " pressure_modifier="
                + formatNumber(context.pressureModifier()), false);
        sendInspectLine(source, " - reward_profile=" + context.rewardProfile().map(ResourceLocation::toString).orElse("<none>"));
        sendInspectLine(source, " - tags=" + formatStringSet(context.tags()));
        sendInspectLine(source, " - started_at_millis=" + context.startedAtMillis());
        sendInspectLine(source, " - profile_cooldown_remaining_ms=" + context.profileCooldownRemainingMillis());
        sendInspectLine(source, " - global_cooldown_remaining_ms=" + context.globalCooldownRemainingMillis());
        return 1;
    }

    private static int runInvasionInspectProfile(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedInvasionService invasionService,
            ResourceLocation profileId) {
        ServerLevel level = requireCommandLevel(source, "World Awakened invasion inspect requires a server level context.");
        if (level == null) {
            return 0;
        }

        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        var profile = snapshot.data().invasionProfiles().get(profileId);
        if (profile == null) {
            sendOperatorFailure(source, Component.literal("That invasion profile is not loaded: " + profileId)
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_INVASION_PROFILE_NOT_FOUND)));
            return 0;
        }

        WorldAwakenedInvasionService.InvasionContextSnapshot active = invasionService.contextSnapshot(level);
        boolean activeProfile = active.invasionActive()
                && active.profileId().map(profileId::equals).orElse(false);

        sendOperatorSummary(source, "Invasion profile: id="
                + profile.id()
                + " enabled="
                + profile.enabled()
                + " trigger_mode="
                + profile.triggerMode().name().toLowerCase(Locale.ROOT)
                + " active="
                + activeProfile, false);
        sendInspectLine(source, " - min_players="
                + profile.minPlayers()
                + " duration_seconds="
                + profile.durationSeconds()
                + " warning_seconds="
                + profile.warningSeconds().map(String::valueOf).orElse("<config>")
                + " cooldown_seconds="
                + profile.cooldownSeconds().map(String::valueOf).orElse("<none>"));
        sendInspectLine(source, " - pressure_modifier="
                + formatNumber(profile.pressureModifier())
                + " reward_profile="
                + profile.rewardProfile().map(ResourceLocation::toString).orElse("<none>"));
        sendInspectLine(source, " - dimensions=" + formatResourceLocations(profile.dimensions()));
        sendInspectLine(source, " - biome_filters=" + formatResourceLocations(profile.biomeFilters()));
        sendInspectLine(source, " - tags=" + formatStringList(profile.tags()));
        sendInspectLine(source, " - conditions=" + profile.conditions().size());
        sendInspectLine(source, " - stage_filters=" + profile.stageFilters().map(JsonElement::toString).orElse("<none>"));
        if (activeProfile) {
            sendInspectLine(source, " - active_remaining_seconds="
                    + active.remainingDurationSeconds()
                    + " warning_active="
                    + active.warningActive()
                    + " live_pressure_modifier="
                    + formatNumber(active.pressureModifier()));
        }
        return 1;
    }

    private static int runDebugInvasionEvaluate(
            CommandSourceStack source,
            WorldAwakenedInvasionService invasionService,
            ResourceLocation profileId,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z) {
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug invasion evaluate");
        if (target.isEmpty()) {
            return 0;
        }

        WorldAwakenedInvasionService.InvasionEvaluateResult result = invasionService.debugEvaluateProfile(
                target.get().level(),
                profileId,
                target.get().level().dimension().location(),
                target.get().position());
        sendDebugHeader(source, Component.literal("Invasion debug evaluate: trace_id="
                + result.traceId())
                .append(Component.literal(" "))
                .append(copyButton("Copy Trace", result.traceId(), "Copy invasion trace ID")));
        sendDebugSection(source, "target",
                "profile="
                        + result.profileId()
                        + " dimension="
                        + target.get().level().dimension().location()
                        + " pos="
                        + formatBlockPos(target.get().position()));
        sendDebugSection(source, "active_context",
                formatInvasionContextSummary(result.activeContext()));

        if (!result.profileFound()) {
            String code = result.code().isBlank()
                    ? WorldAwakenedDiagnosticCodes.DEBUG_INVASION_PROFILE_NOT_FOUND
                    : result.code();
            sendOperatorFailure(source, Component.literal("Invasion evaluate failed: "
                    + describeInvasionRejection(code, result.detail()))
                    .append(debugCodeSuffix(code)));
            sendDebugSection(source, "error", "code=" + code + " detail=" + result.detail());
            return 0;
        }

        WorldAwakenedInvasionService.ProfileEligibility eligibility = result.eligibility().orElseThrow();
        sendDebugSection(source, "eligibility",
                "eligible="
                        + eligibility.eligible()
                        + " trigger_mode="
                        + eligibility.triggerMode().name().toLowerCase(Locale.ROOT)
                        + " players="
                        + eligibility.onlinePlayers()
                        + "/"
                        + eligibility.minPlayers()
                        + " global_cooldown_remaining_ms="
                        + eligibility.globalCooldownRemainingMillis()
                        + " profile_cooldown_remaining_ms="
                        + eligibility.profileCooldownRemainingMillis());
        sendDebugSection(source, "eligibility_rejections", formatStringList(eligibility.rejectionReasons()));

        WorldAwakenedInvasionService.InvasionPoolSummary poolSummary = result.poolSummary().orElseThrow();
        sendDebugSection(source, "pool_newly_eligible", formatResourceLocations(poolSummary.newlyEligiblePools()));
        sendDebugSection(source, "pool_already_eligible", formatResourceLocations(poolSummary.alreadyEligiblePools()));
        sendDebugSection(source, "pool_rejected", formatInvasionRejectedPools(poolSummary.rejectedPools()));
        return 1;
    }

    private static int runLootEvaluate(
            CommandSourceStack source,
            WorldAwakenedLootService lootService,
            String rawTargetType,
            ResourceLocation targetId,
            ServerPlayer player,
            ServerLevel explicitLevel) {
        Optional<WorldAwakenedLootService.LootTargetType> targetType =
                WorldAwakenedLootService.LootTargetType.fromString(rawTargetType);
        if (targetType.isEmpty()) {
            sendOperatorFailure(source, Component.literal("Unsupported loot target type: " + rawTargetType)
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID)));
            return 0;
        }

        ServerLevel level = explicitLevel;
        if (level == null) {
            level = requireCommandLevel(source, "World Awakened loot evaluate requires a server level context.");
            if (level == null) {
                return 0;
            }
        }

        WorldAwakenedLootService.LootRunResult result =
                lootService.debugEvaluate(level, targetType.get(), targetId, player);
        return emitLootOperatorResult(source, result, "evaluate");
    }

    private static int runLootForceProfile(
            CommandSourceStack source,
            WorldAwakenedLootService lootService,
            ResourceLocation profileId,
            String rawTargetType,
            ResourceLocation targetId,
            ServerPlayer player,
            ServerLevel explicitLevel) {
        Optional<WorldAwakenedLootService.LootTargetType> targetType =
                WorldAwakenedLootService.LootTargetType.fromString(rawTargetType);
        if (targetType.isEmpty()) {
            sendOperatorFailure(source, Component.literal("Unsupported loot target type: " + rawTargetType)
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID)));
            return 0;
        }

        ServerLevel level = explicitLevel;
        if (level == null) {
            level = requireCommandLevel(source, "World Awakened loot force_profile requires a server level context.");
            if (level == null) {
                return 0;
            }
        }

        WorldAwakenedLootService.LootRunResult result =
                lootService.debugForceProfile(level, profileId, targetType.get(), targetId, player);
        return emitLootOperatorResult(source, result, "force_profile");
    }

    private static int runDebugLootEvaluate(
            CommandSourceStack source,
            WorldAwakenedLootService lootService,
            String rawTargetType,
            ResourceLocation targetId,
            ServerPlayer player,
            ServerLevel explicitLevel) {
        Optional<WorldAwakenedLootService.LootTargetType> targetType =
                WorldAwakenedLootService.LootTargetType.fromString(rawTargetType);
        if (targetType.isEmpty()) {
            sendOperatorFailure(source, Component.literal("Unsupported loot target type: " + rawTargetType)
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID)));
            return 0;
        }

        ServerLevel level = explicitLevel;
        if (level == null) {
            level = requireCommandLevel(source, "World Awakened debug loot evaluate requires a server level context.");
            if (level == null) {
                return 0;
            }
        }

        WorldAwakenedLootService.LootRunResult result =
                lootService.debugEvaluate(level, targetType.get(), targetId, player);
        return emitLootRunResult(source, result, "evaluate");
    }

    private static int runDebugLootForceProfile(
            CommandSourceStack source,
            WorldAwakenedLootService lootService,
            ResourceLocation profileId,
            String rawTargetType,
            ResourceLocation targetId,
            ServerPlayer player,
            ServerLevel explicitLevel) {
        Optional<WorldAwakenedLootService.LootTargetType> targetType =
                WorldAwakenedLootService.LootTargetType.fromString(rawTargetType);
        if (targetType.isEmpty()) {
            sendOperatorFailure(source, Component.literal("Unsupported loot target type: " + rawTargetType)
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID)));
            return 0;
        }

        ServerLevel level = explicitLevel;
        if (level == null) {
            level = requireCommandLevel(source, "World Awakened debug loot force_profile requires a server level context.");
            if (level == null) {
                return 0;
            }
        }

        WorldAwakenedLootService.LootRunResult result =
                lootService.debugForceProfile(level, profileId, targetType.get(), targetId, player);
        return emitLootRunResult(source, result, "force_profile");
    }

    private static int emitLootOperatorResult(
            CommandSourceStack source,
            WorldAwakenedLootService.LootRunResult result,
            String mode) {
        if (result.skipped()) {
            String code = result.skipCode().isBlank()
                    ? WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID
                    : result.skipCode();
            sendOperatorFailure(source, Component.literal("Loot " + mode + " skipped: "
                    + describeLootSkipReason(result.skipCode(), result.skipDetail()))
                    .append(debugCodeSuffix(code)));
            sendOperatorDetail(source, "code=" + code + " detail=" + result.skipDetail());
            return 0;
        }

        long matched = result.profileDecisions().stream().filter(WorldAwakenedLootService.ProfileDecision::matched).count();
        sendOperatorSummary(source, Component.literal("Loot " + mode
                + ": target="
                + result.context().targetType().serialized()
                + ":"
                + result.context().targetId()
                + " matched="
                + matched
                + "/"
                + result.candidateProfiles().size()
                + " operations="
                + result.operations().size()
                + " rewards="
                + result.finalOutcome().size()
                + " live_applied="
                + result.liveApplied()
                + " ")
                .append(copyButton("Copy Trace", result.traceId(), "Copy loot trace ID")), false);
        sendOperatorDetail(source, "trace_id="
                + result.traceId()
                + " event="
                + result.sourceEventId()
                + " player="
                + result.context().playerName().orElse("<none>")
                + " dimension="
                + result.context().dimensionId());
        sendOperatorDetail(source, "candidates=" + formatResourceLocations(result.candidateProfiles()));
        sendOperatorDetail(source, "matched=" + formatLootMatched(result.profileDecisions()));
        sendOperatorDetail(source, "rejected=" + formatLootRejected(result.profileDecisions()));
        sendOperatorDetail(source, "operations=" + formatLootOperations(result.operations()));
        sendOperatorDetail(source, "outcome="
                + formatLootRewards(result.finalOutcome())
                + " applied_once="
                + result.appliedOnce());
        return 1;
    }

    private static int emitLootRunResult(
            CommandSourceStack source,
            WorldAwakenedLootService.LootRunResult result,
            String mode) {
        sendDebugHeader(source, Component.literal("Loot debug: mode="
                + mode
                + " trace_id="
                + result.traceId())
                .append(Component.literal(" "))
                .append(copyButton("Copy Trace", result.traceId(), "Copy loot trace ID")));
        sendDebugSection(source, "event", result.sourceEventId().toString());
        sendDebugSection(source, "context",
                "target_type="
                        + result.context().targetType().serialized()
                        + " target_id="
                        + result.context().targetId()
                        + " loot_table="
                        + result.context().lootTableId().map(ResourceLocation::toString).orElse("<none>")
                        + " entity_type="
                        + result.context().entityTypeId().map(ResourceLocation::toString).orElse("<none>")
                        + " mutated="
                        + result.context().entityIsMutated()
                        + " mutation_tags="
                        + formatResourceLocations(result.context().mutationTags())
                        + " player="
                        + result.context().playerName().orElse("<none>")
                        + " dimension="
                        + result.context().dimensionId());
        sendDebugSection(source, "stage_context",
                "world="
                        + formatResourceLocations(result.context().worldStageSnapshot())
                        + " player="
                        + formatResourceLocations(result.context().playerStageSnapshot()));
        sendDebugSection(source, "compat",
                "apotheosis_active=" + result.context().apotheosisCompatActive());
        sendDebugSection(source, "candidates", formatResourceLocations(result.candidateProfiles()));
        sendDebugSection(source, "matched", formatLootMatched(result.profileDecisions()));
        sendDebugSection(source, "rejected", formatLootRejected(result.profileDecisions()));
        sendDebugSection(source, "operations", formatLootOperations(result.operations()));
        sendDebugSection(source, "outcome",
                "applied_once="
                        + result.appliedOnce()
                        + " live_applied="
                        + result.liveApplied()
                        + " final="
                        + formatLootRewards(result.finalOutcome()));

        if (result.skipped()) {
            sendOperatorFailure(source, Component.literal("Loot debug request was skipped: "
                    + result.skipDetail())
                    .append(debugCodeSuffix(result.skipCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID
                            : result.skipCode())));
            return 0;
        }
        return 1;
    }

    private static int runDifficultyGlobalGet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty global get requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.GlobalModifierState state = difficultyScalarService.globalState(level);
        if (!state.enabled()) {
            sendOperatorFailure(source, Component.literal("Global difficulty is unavailable: "
                    + describeDifficultyRejection(state.diagnosticCode(), state.diagnosticDetail()))
                    .append(debugCodeSuffix(state.diagnosticCode().isBlank()
                            ? WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID
                            : state.diagnosticCode())));
            sendOperatorDetail(source, "detail=" + state.diagnosticDetail());
            return 0;
        }
        sendOperatorSummary(source, "Difficulty global: value=" + formatNumber(state.value()), false);
        sendOperatorDetail(source, "default="
                + formatNumber(state.defaultValue())
                + " bounds=["
                + formatNumber(state.minValue())
                + ", "
                + formatNumber(state.maxValue())
                + "]");
        return 1;
    }

    private static int runDifficultyGlobalSet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            double value) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty global set requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.MutationResult result =
                difficultyScalarService.setGlobalModifier(level, value, actorName(source));
        return emitDifficultyMutationResult(source, result, "global");
    }

    private static int runDifficultyGlobalReset(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty global reset requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.MutationResult result =
                difficultyScalarService.resetGlobalModifier(level, actorName(source));
        return emitDifficultyMutationResult(source, result, "global");
    }

    private static int runDifficultyPersonalGet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty personal get requires a server level context.");
        ServerPlayer player = sourcePlayer(source);
        if (level == null || player == null) {
            sendOperatorFailure(source, Component.literal("Personal difficulty commands require a player source.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID)));
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.ChallengeReadResult readResult =
                difficultyScalarService.readChallengeState(
                        level,
                        player,
                        WorldAwakenedEffectiveDifficultyScalarService.ChallengeScope.PLAYER);
        return emitChallengeReadResult(source, readResult, "personal");
    }

    private static int runDifficultyPersonalSet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            double value) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty personal set requires a server level context.");
        ServerPlayer player = sourcePlayer(source);
        if (level == null || player == null) {
            sendOperatorFailure(source, Component.literal("Personal difficulty set requires a player source.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID)));
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.MutationResult result =
                difficultyScalarService.setChallengeModifier(
                        level,
                        player,
                        source.hasPermission(2),
                        WorldAwakenedEffectiveDifficultyScalarService.ChallengeScope.PLAYER,
                        value,
                        actorName(source));
        return emitDifficultyMutationResult(source, result, "personal");
    }

    private static int runDifficultyWorldGet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty world get requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.ChallengeReadResult readResult =
                difficultyScalarService.readChallengeState(
                        level,
                        sourcePlayer(source),
                        WorldAwakenedEffectiveDifficultyScalarService.ChallengeScope.WORLD);
        return emitChallengeReadResult(source, readResult, "world");
    }

    private static int runDifficultyWorldSet(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            double value) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty world set requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.MutationResult result =
                difficultyScalarService.setChallengeModifier(
                        level,
                        sourcePlayer(source),
                        source.hasPermission(2),
                        WorldAwakenedEffectiveDifficultyScalarService.ChallengeScope.WORLD,
                        value,
                        actorName(source));
        return emitDifficultyMutationResult(source, result, "world");
    }

    private static int runDifficultyVote(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            boolean voteYes) {
        ServerLevel level = requireCommandLevel(source, "World Awakened difficulty vote requires a server level context.");
        ServerPlayer player = sourcePlayer(source);
        if (level == null || player == null) {
            sendOperatorFailure(source, Component.literal("Vote commands require a player source.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID)));
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.MutationResult result =
                difficultyScalarService.submitVote(level, player, voteYes, player.getGameProfile().getName());
        return emitDifficultyMutationResult(source, result, "vote");
    }

    private static int runDebugDifficultyScalar(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            ServerPlayer targetPlayer) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug difficulty scalar requires a server level context.");
        if (level == null) {
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown breakdown =
                difficultyScalarService.resolveDifficultyScalar(
                        level,
                        targetPlayer,
                        1.0D,
                        Map.of(),
                        0.0D,
                        WorldAwakenedCommonConfig.NATURAL_SPAWN_SCALING_CAP.get(),
                        "debug_difficulty_scalar");
        sendDebugHeader(source, "Difficulty scalar debug");
        sendDebugSection(source, "context",
                "player="
                        + (targetPlayer == null ? "<none>" : targetPlayer.getGameProfile().getName())
                        + " scope="
                        + breakdown.challengeScopeUsed());
        sendDebugSection(source, "modifiers",
                "global="
                        + formatNumber(breakdown.globalModifier())
                        + " challenge="
                        + formatNumber(breakdown.challengeModifier())
                        + " dimension_baseline="
                        + formatNumber(breakdown.dimensionBaseline()));
        sendDebugSection(source, "integration_scalars",
                breakdown.integrationScalars().isEmpty() ? "<none>" : breakdown.integrationScalars().toString());
        sendDebugSection(source, "effective",
                "clamped="
                        + formatNumber(breakdown.clampedEffectiveValue())
                        + " unclamped="
                        + formatNumber(breakdown.unclampedEffectiveValue())
                        + " clamp_reason="
                        + (breakdown.clampReason().isBlank() ? "<none>" : breakdown.clampReason()));
        sendDebugSection(source, "policy_gates",
                breakdown.policyGatesConsulted().isEmpty() ? "<none>" : String.join(", ", breakdown.policyGatesConsulted()));
        return 1;
    }

    private static int runDebugPressureLast(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        Optional<WorldAwakenedMutatorService.PressureEvaluationSnapshot> snapshot = mutatorService.latestPressureSnapshot();
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("No captured pressure snapshots are available yet. Run /wa debug spawn test <entity_id> first, or wait for natural spawns.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return 0;
        }
        return emitPressureSnapshotReplay(source, difficultyScalarService, snapshot.get());
    }

    private static int runDebugPressureReplay(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            long snapshotId) {
        Optional<WorldAwakenedMutatorService.PressureEvaluationSnapshot> snapshot = mutatorService.pressureSnapshot(snapshotId);
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("Unknown pressure snapshot id: " + snapshotId + " ")
                    .append(suggestCommandButton("Show Last", "/wa debug pressure last", "Prefill /wa debug pressure last"))
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return 0;
        }
        return emitPressureSnapshotReplay(source, difficultyScalarService, snapshot.get());
    }

    private static int emitPressureSnapshotReplay(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            WorldAwakenedMutatorService.PressureEvaluationSnapshot snapshot) {
        sendDebugHeader(source, Component.literal("Pressure snapshot replay: id="
                + snapshot.snapshotId()
                + " trace="
                + snapshot.traceId())
                .append(Component.literal(" "))
                .append(copyButton("Copy ID", Long.toString(snapshot.snapshotId()), "Copy snapshot ID"))
                .append(Component.literal(" "))
                .append(suggestCommandButton(
                        "Replay",
                        "/wa debug pressure replay " + snapshot.snapshotId(),
                        "Prefill replay command for this snapshot")));
        sendDebugSection(source, "capture",
                "mode=" + snapshot.mode()
                        + " captured_at_millis=" + snapshot.capturedAtMillis()
                        + " source=" + snapshot.sourceKey());
        sendDebugSection(source, "context",
                "dimension="
                        + snapshot.dimensionId()
                        + " pos="
                        + formatBlockPos(snapshot.position())
                        + " biome="
                        + snapshot.biomeId().map(ResourceLocation::toString).orElse("<unknown>")
                        + " entity="
                        + snapshot.entityTypeId()
                        + " category="
                        + snapshot.mobCategory());
        sendDebugSection(source, "selection",
                "origin="
                        + snapshot.spawnOrigin()
                        + " selected_pool="
                        + snapshot.selectedPoolId()
                        + " progression_mode="
                        + snapshot.progressionMode()
                        + " stage_context="
                        + formatResourceLocations(snapshot.stageContext()));
        sendDebugSection(source, "attribution",
                snapshot.attributedPlayer()
                        .map(player -> player.name() + "(" + player.uuid() + ")")
                        .orElse("<none>"));
        sendDebugSection(source, "captured_chance",
                "base="
                        + formatNumber(snapshot.basePressure())
                        + " effective="
                        + formatNumber(snapshot.effectivePressure())
                        + " roll_mode="
                        + snapshot.rollMode()
                        + " rolled="
                        + (snapshot.rolledValue().isPresent()
                                ? formatNumber(snapshot.rolledValue().getAsDouble())
                                : "<none>")
                        + " passed="
                        + snapshot.chancePassed());
        sendDebugSection(source, "captured_scalar",
                "base="
                        + formatNumber(snapshot.scalarBreakdown().baseValue())
                        + " dimension_baseline="
                        + formatNumber(snapshot.scalarBreakdown().dimensionBaseline())
                        + " global_modifier="
                        + formatNumber(snapshot.scalarBreakdown().globalModifier())
                        + " challenge_modifier="
                        + formatNumber(snapshot.scalarBreakdown().challengeModifier())
                        + " effective="
                        + formatNumber(snapshot.scalarBreakdown().clampedEffectiveValue()));
        sendDebugSection(source, "captured_policy",
                "gates="
                        + (snapshot.scalarBreakdown().policyGatesConsulted().isEmpty()
                                ? "<none>"
                                : String.join(", ", snapshot.scalarBreakdown().policyGatesConsulted()))
                        + " category_data_available="
                        + snapshot.categoryRestrictionDataAvailable()
                        + " category_allowed="
                        + snapshot.categoryAllowed()
                        + " peaceful_blocked="
                        + snapshot.peacefulBlocked());

        if (source.getServer() == null) {
            sendOperatorFailure(source, Component.literal("Replay failed: server context is unavailable.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return 0;
        }
        ServerLevel replayLevel = null;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (level.dimension().location().equals(snapshot.dimensionId())) {
                replayLevel = level;
                break;
            }
        }
        if (replayLevel == null) {
            sendOperatorFailure(source, Component.literal("Replay failed: dimension is not loaded: " + snapshot.dimensionId())
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return 0;
        }

        ServerPlayer replayPlayer = null;
        boolean missingReplayPlayer = false;
        if (snapshot.attributedPlayer().isPresent()) {
            try {
                UUID playerUuid = UUID.fromString(snapshot.attributedPlayer().get().uuid());
                replayPlayer = source.getServer().getPlayerList().getPlayer(playerUuid);
                missingReplayPlayer = replayPlayer == null;
            } catch (IllegalArgumentException ignored) {
                missingReplayPlayer = true;
            }
        }

        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown replayBreakdown =
                difficultyScalarService.resolveSpawnPressureScalar(
                        replayLevel,
                        replayPlayer,
                        snapshot.dimensionId(),
                        snapshot.basePressure(),
                        snapshot.scalarBreakdown().integrationScalars(),
                        0.0D,
                        1.0D,
                        new WorldAwakenedEffectiveDifficultyScalarService.SpawnPressureContext(
                                snapshot.categoryRestrictionDataAvailable(),
                                snapshot.categoryAllowed(),
                                snapshot.peacefulBlocked(),
                                snapshot.sourceKey()));
        String replayPlayerName = replayPlayer == null ? "<none>" : replayPlayer.getGameProfile().getName();
        boolean replayMissingCapturedPlayer = missingReplayPlayer;
        sendDebugSection(source, "replay_scalar",
                "base="
                        + formatNumber(replayBreakdown.baseValue())
                        + " dimension_baseline="
                        + formatNumber(replayBreakdown.dimensionBaseline())
                        + " global_modifier="
                        + formatNumber(replayBreakdown.globalModifier())
                        + " challenge_modifier="
                        + formatNumber(replayBreakdown.challengeModifier())
                        + " clamped="
                        + formatNumber(replayBreakdown.clampedEffectiveValue())
                        + " unclamped="
                        + formatNumber(replayBreakdown.unclampedEffectiveValue())
                        + " clamp_reason="
                        + (replayBreakdown.clampReason().isBlank() ? "<none>" : replayBreakdown.clampReason()));
        sendDebugSection(source, "replay_policy",
                "gates="
                        + (replayBreakdown.policyGatesConsulted().isEmpty()
                                ? "<none>"
                                : String.join(", ", replayBreakdown.policyGatesConsulted()))
                        + " replay_player="
                        + replayPlayerName
                        + " missing_captured_player_context="
                        + replayMissingCapturedPlayer);
        return 1;
    }

    private static int runDebugPressureEvaluate(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z,
            ServerPlayer player) {
        Optional<SpawnCommandTarget> target = resolveSpawnCommandTarget(
                source,
                explicitLevel,
                x,
                y,
                z,
                "/wa debug pressure evaluate");
        if (target.isEmpty()) {
            return 0;
        }
        boolean peacefulBlocked = target.get().level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown breakdown =
                difficultyScalarService.resolveSpawnPressureScalar(
                        target.get().level(),
                        player,
                        target.get().level().dimension().location(),
                        1.0D,
                        Map.of(),
                        0.0D,
                        WorldAwakenedCommonConfig.NATURAL_SPAWN_SCALING_CAP.get(),
                        new WorldAwakenedEffectiveDifficultyScalarService.SpawnPressureContext(
                                true,
                                true,
                                peacefulBlocked,
                                "debug_pressure_evaluate"));
        sendDebugHeader(source, "Pressure evaluate debug");
        sendDebugSection(source, "context",
                "dimension="
                        + target.get().level().dimension().location()
                        + " pos="
                        + formatBlockPos(target.get().position())
                        + " player="
                        + (player == null ? "<none>" : player.getGameProfile().getName()));
        sendDebugSection(source, "modifiers",
                "base="
                        + formatNumber(breakdown.baseValue())
                        + " dimension_baseline="
                        + formatNumber(breakdown.dimensionBaseline())
                        + " global_modifier="
                        + formatNumber(breakdown.globalModifier())
                        + " challenge_modifier="
                        + formatNumber(breakdown.challengeModifier()));
        sendDebugSection(source, "integration_scalars",
                breakdown.integrationScalars().isEmpty() ? "<none>" : breakdown.integrationScalars().toString());
        sendDebugSection(source, "effective",
                "clamped="
                        + formatNumber(breakdown.clampedEffectiveValue())
                        + " unclamped="
                        + formatNumber(breakdown.unclampedEffectiveValue())
                        + " clamp_reason="
                        + (breakdown.clampReason().isBlank() ? "<none>" : breakdown.clampReason()));
        sendDebugSection(source, "policy",
                "rejections="
                        + (breakdown.policyGatesConsulted().isEmpty() ? "<none>" : String.join(", ", breakdown.policyGatesConsulted()))
                        + " peaceful_blocked="
                        + breakdown.peacefulGateBlocked()
                        + " category_data_available="
                        + breakdown.categoryRestrictionDataAvailable()
                        + " category_blocked="
                        + breakdown.categoryGateBlocked());
        return 1;
    }

    private static void emitMutatorRunResult(
            CommandSourceStack source,
            WorldAwakenedMutatorService.MutatorRunResult result,
            String commandMode,
            boolean randomnessBypassed) {
        sendDebugHeader(source, Component.literal("Mutator debug: mode="
                + commandMode
                + " trace_id="
                + result.traceId())
                .append(Component.literal(" "))
                .append(copyButton("Copy Trace", result.traceId(), "Copy mutation trace ID")));
        sendDebugSection(source, "execution",
                "dry_run=" + result.dryRun() + " live_applied=" + result.liveApplied() + " skipped=" + result.skipped());
        sendDebugSection(source, "target",
                "entity="
                        + result.entityTypeId()
                        + " dimension="
                        + result.dimensionId()
                        + " pos="
                        + formatBlockPos(result.position()));
        sendDebugSection(source, "spawn_context",
                formatMutatorSpawnContext(
                        result.spawnOrigin(),
                        result.progressionMode(),
                        result.attributedPlayer()));
        sendDebugSection(source, "stage_context", formatResourceLocations(result.stageContext()));
        sendDebugSection(source, "external_scalars",
                formatScalarBreakdownInline(
                        result.chanceResult().flatMap(WorldAwakenedMutatorService.MutationChanceResult::scalarBreakdown)));
        sendDebugSection(source, "config_gates",
                "mod_enabled="
                        + net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates.modEnabled()
                        + ", mutators_enabled="
                        + WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()
                        + ", debug_commands_enabled="
                        + WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        sendDebugSection(source, "selector",
                "indexed_candidates="
                        + result.indexedCandidatePoolCount()
                        + "/total_pools="
                        + result.totalPoolCount()
                        + " candidate_pools="
                        + formatResourceLocations(result.indexedCandidatePoolIds()));
        sendDebugSection(source, "pools.candidate", formatResourceLocations(result.eligiblePoolIds()));
        sendDebugSection(source, "pools.rejected", formatRejectedObjects(result.rejectedPools(), "pool"));
        sendDebugSection(source, "pools.selected", result.selectedPoolId().map(ResourceLocation::toString).orElse("<none>"));
        sendDebugSection(source, "chance", formatMutationChanceResult(result.chanceResult()));
        sendDebugSection(source, "limits",
                "requested_mutator_cap="
                        + result.requestedMutatorCap()
                        + " enforced_mutator_cap="
                        + result.enforcedMutatorCap());
        sendDebugSection(source, "mutators.candidate", formatResourceLocations(result.eligibleMutatorIds()));
        sendDebugSection(source, "mutators.rejected", formatRejectedObjects(result.rejectedMutators(), "mutator"));
        sendDebugSection(source, "mutators.selected", formatResourceLocations(result.selectedMutatorIds()));
        sendDebugSection(source, "components.applied", formatAppliedMutations(result.appliedMutations()));
        sendDebugSection(source, "components.failed", formatComponentFailures(result.componentFailures()));
        if (result.skipped()) {
            sendDebugSection(source, "outcome",
                    "final=skipped code="
                            + result.skipCode()
                            + " detail="
                            + result.skipDetail()
                            + " randomness_bypassed="
                            + randomnessBypassed);
            return;
        }
        if (result.spawnedEntityUuid().isPresent()) {
            sendDebugSection(source, "live_spawn",
                    "added="
                            + result.spawnAdded()
                            + " entity_uuid="
                            + result.spawnedEntityUuid().get());
        }
        String outcome = result.liveApplied()
                ? "applied"
                : result.chanceResult().isPresent() && !result.chanceResult().get().passed()
                        ? "chance_failed"
                        : result.selectedMutatorIds().isEmpty()
                        ? "no_mutator_selected"
                        : "evaluated";
        sendDebugSection(source, "outcome", "final=" + outcome + " randomness_bypassed=" + randomnessBypassed);
    }

    static String formatMutationChanceResult(
            Optional<WorldAwakenedMutatorService.MutationChanceResult> chanceResult) {
        if (chanceResult.isEmpty()) {
            return "<none>";
        }
        WorldAwakenedMutatorService.MutationChanceResult result = chanceResult.get();
        String rolled = switch (result.rollMode()) {
            case ROLLED -> result.rolledValue().isPresent()
                    ? formatNumber(result.rolledValue().getAsDouble())
                    : "<missing>";
            case SKIPPED -> "<skipped>";
            case BYPASSED -> "<bypassed>";
        };
        return "mutation_chance="
                + formatNumber(result.mutationChance())
                + " rolled="
                + rolled
                + " passed="
                + result.passed();
    }

    static String formatMutatorSpawnContext(
            String spawnOrigin,
            WorldAwakenedProgressionMode progressionMode,
            Optional<WorldAwakenedMutatorService.AttributedPlayerView> attributedPlayer) {
        String playerText = attributedPlayer
                .map(player -> player.name() + "(" + player.uuid() + ")")
                .orElse("<none>");
        return "origin=" + spawnOrigin
                + " progression_mode=" + progressionMode.serializedName()
                + " attributed_player=" + playerText;
    }

    private static Optional<SpawnCommandTarget> resolveSpawnCommandTarget(
            CommandSourceStack source,
            ServerLevel explicitLevel,
            Double x,
            Double y,
            Double z,
            String commandPath) {
        ServerLevel level = explicitLevel != null
                ? explicitLevel
                : requireCommandLevel(source, "This command requires a server level context.");
        if (level == null) {
            return Optional.empty();
        }
        boolean hasX = x != null;
        boolean hasY = y != null;
        boolean hasZ = z != null;
        if (hasX != hasY || hasX != hasZ) {
            sendOperatorFailure(source, Component.literal("Coordinates must provide x, y, and z together.")
                    .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID)));
            return Optional.empty();
        }
        BlockPos position = hasX
                ? BlockPos.containing(x, y, z)
                : BlockPos.containing(source.getPosition());
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, Component.literal("context: command="
                    + commandPath
                    + " dimension="
                    + level.dimension().location()
                    + " pos="
                    + formatBlockPos(position)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return Optional.of(new SpawnCommandTarget(level, position));
    }

    private static EntityType<?> resolveEntityType(CommandSourceStack source, ResourceLocation entityTypeId) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId);
        if (entityType.isPresent()) {
            return entityType.get();
        }
        sendOperatorFailure(source, Component.literal("Unknown entity type: " + entityTypeId)
                .append(debugCodeSuffix(WorldAwakenedDiagnosticCodes.DEBUG_MUTATOR_TARGET_INVALID)));
        return null;
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatRejectedObjects(
            List<WorldAwakenedMutatorService.RejectedObject> rejected,
            String label) {
        if (rejected.isEmpty()) {
            return "<none>";
        }
        return rejected.stream()
                .map(entry -> label
                        + "="
                        + entry.objectId()
                        + " code="
                        + entry.code()
                        + " detail="
                        + entry.detail())
                .collect(Collectors.joining(" | "));
    }

    private static String formatAppliedMutations(List<WorldAwakenedMutatorService.AppliedMutation> appliedMutations) {
        if (appliedMutations.isEmpty()) {
            return "<none>";
        }
        return appliedMutations.stream()
                .map(applied -> applied.mutatorId() + " -> [" + formatResourceLocations(applied.appliedComponentTypes()) + "]")
                .collect(Collectors.joining(" | "));
    }

    private static String formatComponentFailures(List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failures) {
        if (failures.isEmpty()) {
            return "<none>";
        }
        return failures.stream()
                .map(failure -> "mutator="
                        + failure.mutatorId().map(ResourceLocation::toString).orElse("<none>")
                        + " component="
                        + failure.componentType()
                        + " code="
                        + failure.code())
                .collect(Collectors.joining(" | "));
    }

    private static String formatResourceLocations(List<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) {
            return "<none>";
        }
        return ids.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String formatResourceLocations(Set<ResourceLocation> ids) {
        if (ids == null || ids.isEmpty()) {
            return "<none>";
        }
        return ids.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String formatStringSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return "<none>";
        }
        return values.stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private static String formatStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "<none>";
        }
        return values.stream()
                .map(value -> value == null || value.isBlank() ? "<blank>" : value)
                .collect(Collectors.joining(", "));
    }

    private static String formatInvasionContextSummary(WorldAwakenedInvasionService.InvasionContextSnapshot context) {
        return "active="
                + context.invasionActive()
                + " profile="
                + context.profileId().map(ResourceLocation::toString).orElse("<none>")
                + " display_name="
                + (context.displayName().isBlank() ? "<none>" : context.displayName())
                + " instance="
                + context.instanceId()
                + " warning_active="
                + context.warningActive()
                + " remaining_seconds="
                + context.remainingDurationSeconds()
                + " pressure_modifier="
                + formatNumber(context.pressureModifier())
                + " reward_profile="
                + context.rewardProfile().map(ResourceLocation::toString).orElse("<none>")
                + " tags="
                + formatStringSet(context.tags())
                + " global_cooldown_remaining_ms="
                + context.globalCooldownRemainingMillis()
                + " profile_cooldown_remaining_ms="
                + context.profileCooldownRemainingMillis();
    }

    private static String formatInvasionRejectedPools(List<WorldAwakenedInvasionService.PoolDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return "<none>";
        }
        return decisions.stream()
                .map(decision -> decision.poolId() + " detail=" + decision.detail())
                .collect(Collectors.joining(" | "));
    }

    private static String formatLootMatched(List<WorldAwakenedLootService.ProfileDecision> decisions) {
        List<WorldAwakenedLootService.ProfileDecision> matched = decisions.stream()
                .filter(WorldAwakenedLootService.ProfileDecision::matched)
                .toList();
        if (matched.isEmpty()) {
            return "<none>";
        }
        return matched.stream()
                .map(decision -> decision.profileId()
                        + " mode="
                        + decision.requestedMode().name().toLowerCase(Locale.ROOT)
                        + "->"
                        + decision.resolvedMode().name().toLowerCase(Locale.ROOT)
                        + " reward="
                        + decision.selectedReward().map(reward -> reward.itemId() + "x" + reward.count()).orElse("<none>")
                        + " fallback="
                        + decision.fallbackAction())
                .collect(Collectors.joining(" | "));
    }

    private static String formatLootRejected(List<WorldAwakenedLootService.ProfileDecision> decisions) {
        List<WorldAwakenedLootService.ProfileDecision> rejected = decisions.stream()
                .filter(decision -> !decision.matched())
                .toList();
        if (rejected.isEmpty()) {
            return "<none>";
        }
        return rejected.stream()
                .map(decision -> decision.profileId()
                        + " reason="
                        + decision.reasonCategory()
                        + " code="
                        + (decision.diagnosticCode().isBlank() ? "<none>" : decision.diagnosticCode())
                        + " detail="
                        + decision.detail())
                .collect(Collectors.joining(" | "));
    }

    private static String formatLootOperations(List<WorldAwakenedLootService.ResolvedOperation> operations) {
        if (operations.isEmpty()) {
            return "<none>";
        }
        return operations.stream()
                .map(operation -> operation.profileId()
                        + ":"
                        + operation.mode().name().toLowerCase(Locale.ROOT)
                        + " reward="
                        + operation.selectedReward().itemId()
                        + "x"
                        + operation.selectedReward().count()
                        + " fallback="
                        + operation.fallbackAction())
                .collect(Collectors.joining(" | "));
    }

    private static String formatLootRewards(List<WorldAwakenedLootService.RewardItem> rewards) {
        if (rewards.isEmpty()) {
            return "<none>";
        }
        return rewards.stream()
                .map(reward -> reward.itemId() + "x" + reward.count())
                .collect(Collectors.joining(", "));
    }

    private static String describeLootSkipReason(String code, String detail) {
        if (WorldAwakenedDiagnosticCodes.DEBUG_LOOT_TARGET_INVALID.equals(code)) {
            return "target context is not valid for this loot operation";
        }
        if (WorldAwakenedDiagnosticCodes.DEBUG_LOOT_PROFILE_NOT_FOUND.equals(code)) {
            return "the requested forced profile is not loaded";
        }
        if (WorldAwakenedDiagnosticCodes.REWARD_EVENT_UNSUPPORTED.equals(code)) {
            return "this source event cannot produce World Awakened loot rewards";
        }
        if (WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_OVERRIDE_BLOCKED.equals(code)) {
            return "the requested loot operation is blocked by the active Apotheosis safety policy";
        }
        if (WorldAwakenedDiagnosticCodes.APOTHEOSIS_LOOT_MODE_UNSAFE.equals(code)) {
            return "the requested loot mode is unsafe for Apotheosis-sensitive targets";
        }
        if (detail == null || detail.isBlank()) {
            return "request was skipped";
        }
        return detail.replace('_', ' ');
    }

    private static String describeInvasionRejection(String code, String detail) {
        if (WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE.equals(code)) {
            return "the invasion system is disabled by configuration";
        }
        if (WorldAwakenedDiagnosticCodes.DEBUG_INVASION_PROFILE_NOT_FOUND.equals(code)) {
            return "that invasion profile is not loaded or is disabled";
        }
        if (!WorldAwakenedDiagnosticCodes.DEBUG_INVASION_STATE_INVALID.equals(code)) {
            return detail == null || detail.isBlank() ? "request was rejected" : detail.replace('_', ' ');
        }
        if (detail == null || detail.isBlank()) {
            return "request was rejected by invasion state validation";
        }
        if (detail.startsWith("already_active:")) {
            return "an invasion is already active";
        }
        if (detail.equals("no_active_invasion")) {
            return "there is no active invasion";
        }
        if (detail.equals("max_concurrent_invasions=0")) {
            return "the configured max concurrent invasions is 0";
        }
        if (detail.startsWith("ineligible:")) {
            return describeInvasionEligibilityReasons(detail.substring("ineligible:".length()));
        }
        return detail.replace('_', ' ');
    }

    private static String describeInvasionEligibilityReasons(String rawReasons) {
        if (rawReasons == null || rawReasons.isBlank()) {
            return "the selected profile is not eligible at this time";
        }
        String[] reasons = rawReasons.split("\\|");
        if (reasons.length == 0) {
            return "the selected profile is not eligible at this time";
        }
        String first = reasons[0];
        if (first.startsWith("global_cooldown_active_millis:")) {
            return "global invasion cooldown is still active";
        }
        if (first.startsWith("profile_cooldown_active_millis:")) {
            return "that invasion profile is still on cooldown";
        }
        if (first.startsWith("player_count_below_min:")) {
            return "online player count is below the profile minimum";
        }
        if (first.startsWith("dimension_not_allowed:")) {
            return "this dimension is not allowed by the profile";
        }
        if (first.startsWith("biome_not_allowed:")) {
            return "this biome is not allowed by the profile";
        }
        if (first.equals("biome_unavailable")) {
            return "biome context is unavailable for this request";
        }
        if (first.equals("stage_filters_rejected")) {
            return "stage filters do not match current progression state";
        }
        if (first.startsWith("trigger_mode_not_random_periodic:")) {
            return "the profile trigger mode is not eligible for scheduler evaluation";
        }
        if (first.startsWith("profile_condition")) {
            return "one or more profile conditions did not match";
        }
        return first.replace('_', ' ');
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatScalarBreakdownInline(
            Optional<WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown> breakdownOpt) {
        if (breakdownOpt.isEmpty()) {
            return "<none>";
        }
        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown breakdown = breakdownOpt.get();
        return "dimension_baseline="
                + formatNumber(breakdown.dimensionBaseline())
                + " global="
                + formatNumber(breakdown.globalModifier())
                + " challenge="
                + formatNumber(breakdown.challengeModifier())
                + " effective="
                + formatNumber(breakdown.clampedEffectiveValue())
                + " scope="
                + breakdown.challengeScopeUsed()
                + " policy_gates="
                + (breakdown.policyGatesConsulted().isEmpty() ? "<none>" : String.join("|", breakdown.policyGatesConsulted()));
    }

    private static int emitChallengeReadResult(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService.ChallengeReadResult result,
            String scopeLabel) {
        if (!result.success()) {
            sendOperatorFailure(source, Component.literal("Could not read " + scopeLabel + " difficulty: "
                    + describeDifficultyRejection(result.code(), result.detail()))
                    .append(debugCodeSuffix(result.code().isBlank()
                            ? WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID
                            : result.code())));
            sendOperatorDetail(source, "detail=" + result.detail());
            return 0;
        }
        WorldAwakenedEffectiveDifficultyScalarService.ChallengeModifierState state = result.state();
        String maxChanges = state.maxChanges() <= 0 ? "unlimited" : Integer.toString(state.maxChanges());
        String voteState = state.voteState().isPresent() ? "active" : "inactive";
        sendOperatorSummary(source,
                "Difficulty "
                        + scopeLabel
                        + ": value="
                        + formatNumber(state.value())
                        + " scope="
                        + state.resolvedScope()
                        + " cooldown="
                        + formatCooldownForOperator(state.cooldownRemainingMillis())
                        + " changes="
                        + state.changeCount()
                        + "/"
                        + maxChanges
                        + " vote="
                        + voteState,
                false);
        sendOperatorDetail(source, "bounds=["
                + formatNumber(state.minValue())
                + ", "
                + formatNumber(state.maxValue())
                + "] step="
                + formatNumber(state.step())
                + " allow_raise="
                + state.allowRaise()
                + " allow_lower="
                + state.allowLower()
                + " allow_player_adjustment="
                + state.allowPlayerAdjustment()
                + " vote_required="
                + state.voteRequired()
                + " cooldown_remaining_millis="
                + state.cooldownRemainingMillis());
        if (state.voteState().isPresent()) {
            WorldAwakenedEffectiveDifficultyScalarService.ChallengeVoteState vote = state.voteState().get();
            sendOperatorDetail(source, "active_vote=target="
                    + formatNumber(vote.targetValue())
                    + " yes="
                    + vote.yesVotes().size()
                    + " no="
                    + vote.noVotes().size()
                    + " eligible="
                    + vote.eligibleVoters().size()
                    + " timeout_at_millis="
                    + vote.timeoutAtMillis());
        } else {
            sendOperatorDetail(source, "active_vote=<none>");
        }
        return 1;
    }

    private static int emitDifficultyMutationResult(
            CommandSourceStack source,
            WorldAwakenedEffectiveDifficultyScalarService.MutationResult result,
            String branch) {
        if (!result.success()) {
            String code = result.code().isBlank() ? WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID : result.code();
            sendOperatorFailure(source, Component.literal("Could not update " + branch + " difficulty: "
                    + describeDifficultyRejection(result.code(), result.detail()))
                    .append(debugCodeSuffix(result.code().isBlank() ? WorldAwakenedDiagnosticCodes.DEBUG_DIFFICULTY_SCOPE_INVALID : result.code())));
            sendOperatorDetail(source, "code=" + code + " detail=" + result.detail());
            return 0;
        }
        if (result.committed()) {
            sendOperatorSummary(source,
                    "Difficulty " + branch + " updated: "
                            + formatNumber(result.previousValue())
                            + " -> "
                            + formatNumber(result.currentValue()),
                    true);
            return 1;
        }
        if (result.voteStarted()) {
            String targetValue = result.voteState()
                    .map(WorldAwakenedEffectiveDifficultyScalarService.ChallengeVoteState::targetValue)
                    .map(WorldAwakenedCommands::formatNumber)
                    .orElse(formatNumber(result.currentValue()));
            sendOperatorSummary(source,
                    Component.literal("Difficulty " + branch + " vote started: target="
                            + targetValue
                            + " ")
                            .append(suggestCommandButton(
                                    "Vote Yes",
                                    "/wa difficulty vote yes",
                                    "Prefill /wa difficulty vote yes"))
                            .append(Component.literal(" "))
                            .append(suggestCommandButton(
                                    "Vote No",
                                    "/wa difficulty vote no",
                                    "Prefill /wa difficulty vote no")),
                    true);
            return 1;
        }
        if (result.voteRecorded()) {
            sendOperatorSummary(source,
                    "Difficulty " + branch + " " + describeDifficultyVoteResult(result.code(), result.detail()) + ".",
                    true);
            sendOperatorDetail(source, "code="
                    + (result.code().isBlank() ? "<none>" : result.code())
                    + " detail="
                    + (result.detail().isBlank() ? "<none>" : result.detail()));
            return 1;
        }
        sendOperatorSummary(source, "Difficulty " + branch + " unchanged: " + formatNumber(result.currentValue()), false);
        return 0;
    }

    private static SuggestionProvider<CommandSourceStack> suggestEntityTypeIds() {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                BuiltInRegistries.ENTITY_TYPE.keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestMutationPoolIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().mutationPools().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestMutatorIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().mobMutators().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestInvasionProfileIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().invasionProfiles().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestEnabledInvasionProfileIds(WorldAwakenedInvasionService invasionService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                invasionService.loadedProfileIds(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestLootProfileIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().lootProfiles().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestLootTargetTypes() {
        return (context, builder) -> suggestStrings(WorldAwakenedLootService.LootTargetType.serializedValues(), builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestStageIds(WorldAwakenedStageService stageService) {
        return (context, builder) -> {
            List<ResourceLocation> ids = new ArrayList<>(stageService.stageRegistry().canonicalStageIds());
            ids.addAll(stageService.stageRegistry().aliasMappings().keySet());
            return SharedSuggestionProvider.suggestResource(ids, builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestTriggerIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().triggerRules().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestRuleIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().rules().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestOfferIds(WorldAwakenedDatapackService datapackService) {
        return (context, builder) -> SharedSuggestionProvider.suggestResource(
                datapackService.currentSnapshot().data().ascensionOffers().keySet(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestPendingInstanceIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            return suggestStrings(
                    ascensionService.pendingOffers(player.serverLevel(), player).stream()
                            .map(WorldAwakenedAscensionOfferRuntime::instanceId)
                            .toList(),
                    builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestResolvedInstanceIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            return suggestStrings(
                    ascensionService.resolvedOffers(player.serverLevel(), player).stream()
                            .map(WorldAwakenedAscensionOfferRuntime::instanceId)
                            .toList(),
                    builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestAnyInstanceIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            List<String> ids = new ArrayList<>();
            ids.addAll(ascensionService.pendingOffers(player.serverLevel(), player).stream()
                    .map(WorldAwakenedAscensionOfferRuntime::instanceId)
                    .toList());
            ids.addAll(ascensionService.resolvedOffers(player.serverLevel(), player).stream()
                    .map(WorldAwakenedAscensionOfferRuntime::instanceId)
                    .toList());
            return suggestStrings(ids, builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestPressureSnapshotIds(WorldAwakenedMutatorService mutatorService) {
        return (context, builder) -> suggestStrings(
                mutatorService.pressureSnapshotIds().stream()
                        .map(String::valueOf)
                        .toList(),
                builder);
    }

    private static SuggestionProvider<CommandSourceStack> suggestPendingRewardIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            String instanceId = StringArgumentType.getString(context, "instance_id");
            Optional<WorldAwakenedAscensionOfferRuntime> runtime = ascensionService.pendingOffers(player.serverLevel(), player).stream()
                    .filter(candidate -> candidate.instanceId().equals(instanceId))
                    .findFirst();
            return SharedSuggestionProvider.suggestResource(
                    runtime.map(WorldAwakenedAscensionOfferRuntime::candidateRewards).orElse(List.of()),
                    builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestActiveRewardIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            Optional<WorldAwakenedAscensionOfferRuntime> runtime = ascensionService.activePendingOffer(player.serverLevel(), player);
            return SharedSuggestionProvider.suggestResource(
                    runtime.map(WorldAwakenedAscensionOfferRuntime::candidateRewards).orElse(List.of()),
                    builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestChosenRewardIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(player.serverLevel())
                    .getOrCreate(player.getUUID());
            return SharedSuggestionProvider.suggestResource(state.chosenAscensionRewards(), builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestSuppressedRewardIds(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(player.serverLevel())
                    .getOrCreate(player.getUUID());
            return SharedSuggestionProvider.suggestResource(state.suppressedAscensionRewards(), builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestSuppressibleComponentKeys(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            ResourceLocation rewardId = ResourceLocationArgument.getId(context, "reward_id");
            return suggestStrings(
                    ascensionService.suppressibleComponentKeys(player.serverLevel(), player, rewardId),
                    builder);
        };
    }

    private static SuggestionProvider<CommandSourceStack> suggestSuppressedComponentKeys(WorldAwakenedAscensionService ascensionService) {
        return (context, builder) -> {
            ServerPlayer player = EntityArgument.getPlayer(context, "player");
            ResourceLocation rewardId = ResourceLocationArgument.getId(context, "reward_id");
            return suggestStrings(
                    ascensionService.suppressedComponentKeys(player.serverLevel(), player, rewardId),
                    builder);
        };
    }

    private static CompletableFuture<Suggestions> suggestStrings(List<String> values, SuggestionsBuilder builder) {
        for (String value : values) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static void sendOperatorSummary(CommandSourceStack source, String message, boolean broadcastToOps) {
        sendOperatorSummary(source, Component.literal(message), broadcastToOps);
    }

    private static void sendOperatorSummary(CommandSourceStack source, Component message, boolean broadcastToOps) {
        source.sendSuccess(() -> message, broadcastToOps);
    }

    private static void sendOperatorFailure(CommandSourceStack source, String message) {
        sendOperatorFailure(source, Component.literal(message));
    }

    private static void sendOperatorFailure(CommandSourceStack source, Component message) {
        source.sendFailure(message);
    }

    private static void sendOperatorDetail(CommandSourceStack source, String message) {
        if (!showVerboseOperatorDetails() || message == null || message.isBlank()) {
            return;
        }
        sendOperatorDetail(source, Component.literal(message).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void sendOperatorDetail(CommandSourceStack source, Component message) {
        if (!showVerboseOperatorDetails()) {
            return;
        }
        MutableComponent line = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY).append(message);
        source.sendSuccess(() -> line, false);
    }

    private static void sendDebugHeader(CommandSourceStack source, String message) {
        sendDebugHeader(source, Component.literal(message));
    }

    private static void sendDebugHeader(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static void sendDebugSection(CommandSourceStack source, String section, String message) {
        sendDebugSection(source, section, Component.literal(message));
    }

    private static void sendDebugSection(CommandSourceStack source, String section, Component message) {
        MutableComponent line = Component.literal(" - " + section + ": ").append(message);
        source.sendSuccess(() -> line, false);
    }

    private static void sendInspectLine(CommandSourceStack source, String message) {
        sendInspectLine(source, Component.literal(message));
    }

    private static void sendInspectLine(CommandSourceStack source, Component message) {
        source.sendSuccess(() -> message, false);
    }

    private static boolean showVerboseOperatorDetails() {
        return WorldAwakenedCommonConfig.DEBUG_LOGGING.get();
    }

    private static MutableComponent displayComponent(JsonElement element, CommandSourceStack source, String fallback) {
        if (element == null || element.isJsonNull()) {
            return Component.literal(fallback);
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            return Component.literal(value.isBlank() ? fallback : value);
        }
        try {
            MutableComponent parsed = Component.Serializer.fromJson(element, source.getServer().registryAccess());
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return Component.literal(fallback.isBlank() ? element.toString() : fallback);
    }

    private static MutableComponent offerDisplayComponent(
            WorldAwakenedDatapackService datapackService,
            CommandSourceStack source,
            ResourceLocation offerId) {
        AscensionOfferDefinition offer = datapackService.currentSnapshot().data().ascensionOffers().get(offerId);
        if (offer == null) {
            return Component.literal(offerId.toString());
        }
        return displayComponent(offer.displayName(), source, offer.id().toString());
    }

    private static MutableComponent rewardDisplayComponent(
            WorldAwakenedDatapackService datapackService,
            CommandSourceStack source,
            ResourceLocation rewardId) {
        AscensionRewardDefinition reward = datapackService.currentSnapshot().data().ascensionRewards().get(rewardId);
        if (reward == null) {
            return Component.literal(rewardId.toString());
        }
        return displayComponent(reward.displayName(), source, reward.id().toString());
    }

    private static String rewardPlainText(WorldAwakenedDatapackService datapackService, ResourceLocation rewardId) {
        AscensionRewardDefinition reward = datapackService.currentSnapshot().data().ascensionRewards().get(rewardId);
        if (reward == null) {
            return rewardId.toString();
        }
        JsonElement displayName = reward.displayName();
        if (displayName != null && displayName.isJsonPrimitive()) {
            String value = displayName.getAsString();
            if (!value.isBlank()) {
                return value;
            }
        }
        return rewardId.toString();
    }

    private static MutableComponent pendingChoicesLine(
            WorldAwakenedDatapackService datapackService,
            CommandSourceStack source,
            WorldAwakenedAscensionOfferRuntime runtime,
            String choosePrefix) {
        MutableComponent line = Component.literal("   Choices: ").withStyle(ChatFormatting.GRAY);
        for (int index = 0; index < runtime.candidateRewards().size(); index++) {
            ResourceLocation rewardId = runtime.candidateRewards().get(index);
            if (index > 0) {
                line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton("Pick", choosePrefix + rewardId, "Prefill choose command for " + rewardId));
        }
        return line;
    }

    private static MutableComponent copyButton(String label, String value, String hoverText) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GRAY)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value)));
    }

    private static MutableComponent suggestCommandButton(String label, String command, String hoverText) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private static MutableComponent runCommandButton(String label, String command, String hoverText) {
        return Component.literal("[" + label + "]").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText)))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static MutableComponent debugCodeSuffix(String detail) {
        if (!showVerboseOperatorDetails() || detail == null || detail.isBlank()) {
            return Component.empty();
        }
        return Component.literal(" [" + detail + "]").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static String describeAscensionDetail(String detail) {
        return switch (detail) {
            case "idempotent_instance" -> "that offer was already granted from the same source; clear or reset it before granting it again";
            case "unknown_or_disabled_offer" -> "that offer is not loaded or is disabled";
            case "offer_conditions_not_met" -> "that offer is not currently eligible for this player";
            case "no_reward_pool" -> "that offer does not have any rewards configured";
            case "no_eligible_rewards" -> "that offer has no rewards left that can still be offered";
            case "invalid_request" -> "that request is missing something it needs";
            case "ascension_disabled" -> "ascension is disabled";
            case "missing_player" -> "choose a player for that command";
            case "pending_instance_missing_or_stale" -> "that offer is no longer pending";
            case "reward_not_in_offer" -> "that reward is not one of the current choices";
            case "offer_or_reward_missing" -> "that offer or reward is no longer available";
            case "reward_ineligible" -> "that reward is no longer eligible";
            default -> detail == null || detail.isBlank() ? "unknown reason" : detail.replace('_', ' ');
        };
    }

    private static String describeSuppressionDetail(String detail) {
        return switch (detail) {
            case "invalid_request" -> "that suppression request is missing required fields";
            case "reward_not_owned" -> "that player does not own the requested reward";
            case "reward_already_suppressed" -> "that reward is already suppressed";
            case "reward_not_suppressed" -> "that reward is not currently suppressed";
            case "component_target_unknown" -> "that component key was not found for the selected reward";
            case "component_not_suppressible" -> "that component cannot be suppressed independently";
            case "component_already_suppressed" -> "that component is already suppressed";
            case "component_not_suppressed" -> "that component is not currently suppressed";
            case "suppression_group_required" -> "that component requires grouped suppression";
            case "suppression_invalid_partial" -> "that suppression would create an invalid partial state";
            case "suppressed_definition_missing" -> "the reward definition is missing so suppression cannot be evaluated";
            default -> detail == null || detail.isBlank() ? "unknown reason" : detail.replace('_', ' ');
        };
    }

    private static String describeDifficultyRejection(String code, String detail) {
        String normalizedDetail = detail == null ? "" : detail.trim();
        if (code == null || code.isBlank()) {
            return describeDifficultyFallbackDetail(normalizedDetail);
        }
        return switch (code) {
            case "bounds" -> describeDifficultyBounds(normalizedDetail);
            case "scope_invalid", WorldAwakenedDiagnosticCodes.CHALLENGE_SCOPE_INVALID ->
                    describeDifficultyScopeRejection(normalizedDetail);
            case "policy_disallows_change" -> "server policy does not allow this difficulty change";
            case "cooldown_active" -> describeDifficultyCooldown(normalizedDetail);
            case "usage_exhausted" -> describeDifficultyUsageRejection(normalizedDetail);
            case "step_invalid", WorldAwakenedDiagnosticCodes.CHALLENGE_STEP_INVALID ->
                    describeDifficultyStepRejection(normalizedDetail);
            case "unauthorized" -> describeDifficultyUnauthorizedRejection(normalizedDetail);
            case "vote_required" -> "a world vote is required before this change can be applied";
            case "vote_inactive" -> describeDifficultyVoteInactive(normalizedDetail);
            case "vote_active" -> "a challenge vote is already active; finish or wait for that vote first";
            case WorldAwakenedDiagnosticCodes.DIFFICULTY_GLOBAL_INVALID ->
                    "global difficulty config is invalid; check difficulty.global min/max/default values";
            case WorldAwakenedDiagnosticCodes.CHALLENGE_BOUNDS_INVALID ->
                    "challenge config bounds/default are invalid; check difficulty.challenge min/max/default";
            case WorldAwakenedDiagnosticCodes.CHALLENGE_VOTE_CONFIG_INVALID ->
                    "challenge vote config is invalid; check threshold and timeout settings";
            case WorldAwakenedDiagnosticCodes.CHALLENGE_MODE_UNSUPPORTED ->
                    "challenge scope mode is unsupported; use auto, player, or world";
            default -> describeDifficultyFallbackDetail(normalizedDetail.isBlank() ? code : normalizedDetail);
        };
    }

    private static String describeDifficultyFallbackDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "this path is disabled by current server policy";
        }
        return switch (detail) {
            case "global_modifier_disabled_or_invalid" ->
                    "global difficulty modifier is disabled or config is invalid";
            case "challenge_disabled_or_invalid" ->
                    "challenge modifier is disabled or config is invalid";
            default -> {
                if (detail.contains("unscoped")) {
                    yield "challenge scope mode is unset/invalid; use auto, player, or world";
                }
                yield detail.replace('_', ' ');
            }
        };
    }

    private static String describeDifficultyBounds(String detail) {
        if (detail == null || detail.isBlank()) {
            return "value is out of bounds";
        }
        if ("value must be finite".equals(detail)) {
            return "value must be a real number";
        }
        if (detail.contains("within [")) {
            int start = detail.indexOf('[');
            int end = detail.indexOf(']');
            if (start >= 0 && end > start) {
                return "value must be within " + detail.substring(start, end + 1);
            }
        }
        return "value is out of bounds";
    }

    private static String describeDifficultyScopeRejection(String detail) {
        if (detail == null || detail.isBlank()) {
            return "requested scope is not valid for current policy";
        }
        if (detail.contains("resolved scope world")) {
            return "current policy resolves challenge to world scope; use /wa difficulty world ...";
        }
        if (detail.contains("resolved scope player") || detail.contains("resolved player scope")) {
            return "current policy resolves challenge to personal scope; use /wa difficulty personal ...";
        }
        if (detail.contains("player context") || detail.contains("player source")) {
            return "this command needs a player context for personal scope";
        }
        return "requested scope is not valid for current policy";
    }

    private static String describeDifficultyCooldown(String detail) {
        long remainingMillis = parseCooldownRemainingMillis(detail);
        if (remainingMillis > 0L) {
            return "cooldown is still active (" + formatCooldownForOperator(remainingMillis) + " remaining)";
        }
        return "cooldown is still active";
    }

    private static long parseCooldownRemainingMillis(String detail) {
        if (detail == null || detail.isBlank()) {
            return 0L;
        }
        String prefix = "cooldown_remaining_millis=";
        if (!detail.startsWith(prefix)) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(detail.substring(prefix.length()).trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String describeDifficultyUsageRejection(String detail) {
        if (detail == null || detail.isBlank()) {
            return "change limit has been reached";
        }
        if (detail.contains("player")) {
            return "personal change limit has been reached";
        }
        if (detail.contains("world")) {
            return "world change limit has been reached";
        }
        return "change limit has been reached";
    }

    private static String describeDifficultyStepRejection(String detail) {
        if (detail == null || detail.isBlank()) {
            return "value must align to the configured step grid";
        }
        if (detail.contains("step")) {
            return "value must align to the configured step grid around default difficulty";
        }
        return "value must align to the configured step grid";
    }

    private static String describeDifficultyUnauthorizedRejection(String detail) {
        if (detail == null || detail.isBlank()) {
            return "you are not authorized for this action";
        }
        if (detail.contains("eligible for this vote")) {
            return "you are not eligible to vote on this change";
        }
        return "you are not authorized for this action";
    }

    private static String describeDifficultyVoteInactive(String detail) {
        if (detail == null || detail.isBlank()) {
            return "no active vote exists for this action";
        }
        if (detail.contains("not active for current policy")) {
            return "voting is not enabled for the current world-scope policy";
        }
        return "no active vote exists for this action";
    }

    private static String describeDifficultyVoteResult(String code, String detail) {
        if (code == null || code.isBlank()) {
            return "vote recorded";
        }
        return switch (code) {
            case "vote_failed" -> "vote failed (" + (detail == null || detail.isBlank() ? "threshold not reached" : detail) + ")";
            default -> code + (detail == null || detail.isBlank() ? "" : " (" + detail + ")");
        };
    }

    private static String formatCooldownForOperator(long cooldownRemainingMillis) {
        if (cooldownRemainingMillis <= 0L) {
            return "ready";
        }
        long totalSeconds = Math.max(1L, cooldownRemainingMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0L) {
            return seconds + "s";
        }
        if (seconds == 0L) {
            return minutes + "m";
        }
        return minutes + "m" + seconds + "s";
    }

    private static int runAscensionReconcile(
            CommandSourceStack source,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            sendOperatorFailure(source, "Choose a player for /wa ascension reconcile.");
            return 0;
        }
        ascensionService.reconcilePlayerRewards(target.serverLevel(), target, "ascension_manual_reconcile");
        sendOperatorSummary(source,
                Component.literal("Reconciled ascension rewards for ")
                        .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)),
                true);
        return 1;
    }

    private static int runAscensionSuppressReward(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId) {
        WorldAwakenedAscensionService.SuppressionMutationResult result =
                ascensionService.suppressReward(target.serverLevel(), target, rewardId);
        if (result.changed()) {
            sendOperatorSummary(source, Component.literal("Suppressed ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for "))
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)), true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("code="
                        + result.diagnosticCode()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not suppress reward: " + describeSuppressionDetail(result.detail()))
                .append(debugCodeSuffix(result.detail())));
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, "code=" + result.diagnosticCode());
        }
        return 0;
    }

    private static int runAscensionUnsuppressReward(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId) {
        WorldAwakenedAscensionService.SuppressionMutationResult result =
                ascensionService.unsuppressReward(target.serverLevel(), target, rewardId);
        if (result.changed()) {
            sendOperatorSummary(source, Component.literal("Re-enabled ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" for "))
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)), true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("code="
                        + result.diagnosticCode()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not re-enable reward: " + describeSuppressionDetail(result.detail()))
                .append(debugCodeSuffix(result.detail())));
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, "code=" + result.diagnosticCode());
        }
        return 0;
    }

    private static int runAscensionSuppressComponent(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId,
            String componentKey) {
        WorldAwakenedAscensionService.SuppressionMutationResult result =
                ascensionService.suppressComponent(target.serverLevel(), target, rewardId, componentKey);
        if (result.changed()) {
            sendOperatorSummary(source, Component.literal("Suppressed component state for ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" on "))
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)), true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("keys="
                        + result.componentKeys()
                        + " code="
                        + result.diagnosticCode()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not suppress component: " + describeSuppressionDetail(result.detail()))
                .append(debugCodeSuffix(result.detail())));
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, "code=" + result.diagnosticCode());
        }
        return 0;
    }

    private static int runAscensionUnsuppressComponent(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId,
            String componentKey) {
        WorldAwakenedAscensionService.SuppressionMutationResult result =
                ascensionService.unsuppressComponent(target.serverLevel(), target, rewardId, componentKey);
        if (result.changed()) {
            sendOperatorSummary(source, Component.literal("Re-enabled component state for ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" on "))
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)), true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("keys="
                        + result.componentKeys()
                        + " code="
                        + result.diagnosticCode()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not re-enable component: " + describeSuppressionDetail(result.detail()))
                .append(debugCodeSuffix(result.detail())));
        if (showVerboseOperatorDetails()) {
            sendOperatorDetail(source, "code=" + result.diagnosticCode());
        }
        return 0;
    }

    private static int runAscensionReopen(
            CommandSourceStack source,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            String instanceId) {
        if (ascensionService.reopenOffer(target.serverLevel(), target, instanceId)) {
            MutableComponent message = Component.literal("Reopened ascension offer for ")
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", instanceId, instanceId))
                    .append(Component.literal(" "))
                    .append(runCommandButton("Open", "/wa ascension open " + target.getGameProfile().getName(), "Open the active ascension offer"));
            sendOperatorSummary(source, message, true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, "instance=" + instanceId);
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("That offer cannot be reopened because it is already pending or no longer exists.")
                .append(debugCodeSuffix("instance_not_found_or_already_pending")));
        return 0;
    }

    private static int runAscensionClear(
            CommandSourceStack source,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            String instanceId) {
        if (ascensionService.clearOfferInstance(target.serverLevel(), target, instanceId)) {
            MutableComponent message = Component.literal("Removed ascension offer for ")
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", instanceId, instanceId));
            sendOperatorSummary(source, message, true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, "instance=" + instanceId);
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("That offer was not found.").append(debugCodeSuffix("instance_not_found")));
        return 0;
    }

    private static int runDebugResetWorldStages(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug stage reset requires a server level context");
        if (level == null) {
            return 0;
        }
        WorldAwakenedProgressionStateEditor.StageResetSummary summary = debugCommandService.resetWorldStages(level);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset global stages: cleared="
                + summary.clearedStages()), true);
        return summary.clearedStages();
    }

    private static int runDebugResetWorldTriggers(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug trigger reset requires a server level context");
        if (level == null) {
            return 0;
        }
        WorldAwakenedProgressionStateEditor.TriggerResetSummary summary = debugCommandService.resetWorldTriggers(level);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset global triggers: cooldowns="
                + summary.clearedCooldowns()
                + ", consumed="
                + summary.clearedConsumed()
                + ", counters="
                + summary.clearedCounters()), true);
        return summary.totalCleared();
    }

    private static int runDebugResetWorldRules(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug rule reset requires a server level context");
        if (level == null) {
            return 0;
        }
        WorldAwakenedProgressionStateEditor.RuleResetSummary summary = debugCommandService.resetWorldRules(level);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset global rules: cooldowns="
                + summary.clearedCooldowns()
                + ", consumed="
                + summary.clearedConsumed()), true);
        return summary.totalCleared();
    }

    private static int runDebugResetWorldAll(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug global reset requires a server level context");
        if (level == null) {
            return 0;
        }
        WorldAwakenedProgressionStateEditor.StageResetSummary stages = debugCommandService.resetWorldStages(level);
        WorldAwakenedProgressionStateEditor.TriggerResetSummary triggers = debugCommandService.resetWorldTriggers(level);
        WorldAwakenedProgressionStateEditor.RuleResetSummary rules = debugCommandService.resetWorldRules(level);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset global all: stages="
                + stages.clearedStages()
                + ", trigger_entries="
                + triggers.totalCleared()
                + ", rule_entries="
                + rules.totalCleared()), true);
        return stages.clearedStages() + triggers.totalCleared() + rules.totalCleared();
    }

    private static int runDebugResetPlayerStages(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target) {
        WorldAwakenedProgressionStateEditor.StageResetSummary summary = debugCommandService.resetPlayerStages(target);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset player stages: player="
                + target.getGameProfile().getName()
                + " cleared="
                + summary.clearedStages()), true);
        return summary.clearedStages();
    }

    private static int runDebugResetPlayerTriggers(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target) {
        WorldAwakenedProgressionStateEditor.TriggerResetSummary summary = debugCommandService.resetPlayerTriggers(target);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset player triggers: player="
                + target.getGameProfile().getName()
                + " cooldowns="
                + summary.clearedCooldowns()
                + ", consumed="
                + summary.clearedConsumed()
                + ", counters="
                + summary.clearedCounters()), true);
        return summary.totalCleared();
    }

    private static int runDebugResetPlayerRules(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target) {
        WorldAwakenedProgressionStateEditor.RuleResetSummary summary = debugCommandService.resetPlayerRules(target);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset player rules: player="
                + target.getGameProfile().getName()
                + " cooldowns="
                + summary.clearedCooldowns()
                + ", consumed="
                + summary.clearedConsumed()), true);
        return summary.totalCleared();
    }

    private static int runDebugResetPlayerAscension(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target) {
        WorldAwakenedAscensionService.ResetSummary summary = debugCommandService.resetPlayerAscension(target);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset player ascension: player="
                + target.getGameProfile().getName()
                + " pending="
                + summary.pendingOffers()
                + ", resolved="
                + summary.resolvedOffers()
                + ", chosen="
                + summary.chosenRewards()
                + ", forfeited="
                + summary.forfeitedRewards()), true);
        return summary.totalCleared();
    }

    private static int runDebugResetPlayerAll(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target) {
        WorldAwakenedProgressionStateEditor.StageResetSummary stages = debugCommandService.resetPlayerStages(target);
        WorldAwakenedProgressionStateEditor.TriggerResetSummary triggers = debugCommandService.resetPlayerTriggers(target);
        WorldAwakenedProgressionStateEditor.RuleResetSummary rules = debugCommandService.resetPlayerRules(target);
        WorldAwakenedAscensionService.ResetSummary ascension = debugCommandService.resetPlayerAscension(target);
        source.sendSuccess(() -> Component.literal("World Awakened debug reset player all: player="
                + target.getGameProfile().getName()
                + " stages="
                + stages.clearedStages()
                + ", trigger_entries="
                + triggers.totalCleared()
                + ", rule_entries="
                + rules.totalCleared()
                + ", ascension_entries="
                + ascension.totalCleared()), true);
        return stages.clearedStages() + triggers.totalCleared() + rules.totalCleared() + ascension.totalCleared();
    }

    private static int runDebugClearWorldStage(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ResourceLocation stageId) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug stage clear requires a server level context");
        if (level == null) {
            return 0;
        }
        return reportStageMutation(source, debugCommandService.clearWorldStage(level, stageId), null);
    }

    private static int runDebugClearWorldTrigger(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ResourceLocation triggerId) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug trigger clear requires a server level context");
        if (level == null) {
            return 0;
        }
        if (debugCommandService.clearWorldTrigger(level, triggerId)) {
            source.sendSuccess(() -> Component.literal("World Awakened debug cleared global trigger state: " + triggerId), true);
            return 1;
        }
        source.sendFailure(Component.literal("World Awakened debug trigger state not found: " + triggerId));
        return 0;
    }

    private static int runDebugClearWorldRule(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ResourceLocation ruleId) {
        ServerLevel level = requireCommandLevel(source, "World Awakened debug rule clear requires a server level context");
        if (level == null) {
            return 0;
        }
        if (debugCommandService.clearWorldRule(level, ruleId)) {
            source.sendSuccess(() -> Component.literal("World Awakened debug cleared global rule state: " + ruleId), true);
            return 1;
        }
        source.sendFailure(Component.literal("World Awakened debug rule state not found: " + ruleId));
        return 0;
    }

    private static int runDebugClearPlayerStage(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target,
            ResourceLocation stageId) {
        return reportStageMutation(source, debugCommandService.clearPlayerStage(target, stageId), target);
    }

    private static int runDebugClearPlayerTrigger(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target,
            ResourceLocation triggerId) {
        if (debugCommandService.clearPlayerTrigger(target, triggerId)) {
            source.sendSuccess(() -> Component.literal("World Awakened debug cleared player trigger state: player="
                    + target.getGameProfile().getName()
                    + " trigger="
                    + triggerId), true);
            return 1;
        }
        source.sendFailure(Component.literal("World Awakened debug player trigger state not found: " + triggerId));
        return 0;
    }

    private static int runDebugClearPlayerRule(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target,
            ResourceLocation ruleId) {
        if (debugCommandService.clearPlayerRule(target, ruleId)) {
            source.sendSuccess(() -> Component.literal("World Awakened debug cleared player rule state: player="
                    + target.getGameProfile().getName()
                    + " rule="
                    + ruleId), true);
            return 1;
        }
        source.sendFailure(Component.literal("World Awakened debug player rule state not found: " + ruleId));
        return 0;
    }

    private static int runDebugClearPlayerAscensionInstance(
            CommandSourceStack source,
            WorldAwakenedDebugCommandService debugCommandService,
            ServerPlayer target,
            String instanceId) {
        if (debugCommandService.clearPlayerAscensionInstance(target, instanceId)) {
            source.sendSuccess(() -> Component.literal("World Awakened debug cleared ascension instance: player="
                    + target.getGameProfile().getName()
                    + " instance="
                    + instanceId), true);
            return 1;
        }
        source.sendFailure(Component.literal("World Awakened debug ascension instance not found: " + instanceId));
        return 0;
    }

    private static int runAscensionList(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            sendOperatorFailure(source, "Choose a player for /wa ascension list.");
            return 0;
        }

        ServerLevel level = target.serverLevel();
        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(level, target);
        List<WorldAwakenedAscensionOfferRuntime> resolved = ascensionService.resolvedOffers(level, target);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(target.getUUID());

        sendOperatorSummary(source, "Ascension state for "
                + target.getGameProfile().getName()
                + ": pending="
                + pending.size()
                + ", resolved="
                + resolved.size()
                + ", chosen="
                + state.chosenAscensionRewards().size(),
                false);
        for (ResourceLocation chosen : state.chosenAscensionRewards()) {
            sendInspectLine(source, Component.literal(" - chosen: ")
                    .append(rewardDisplayComponent(datapackService, source, chosen).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", chosen.toString(), chosen.toString())));
        }

        return state.chosenAscensionRewards().size();
    }

    private static int runAscensionPending(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            sendOperatorFailure(source, "Choose a player for /wa ascension pending.");
            return 0;
        }

        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(target.serverLevel(), target);
        if (pending.isEmpty()) {
            sendOperatorSummary(source, target.getGameProfile().getName() + " has no pending ascension offers.", false);
            return 0;
        }
        sendOperatorSummary(source, "Pending ascension offers for "
                + target.getGameProfile().getName()
                + ": "
                + pending.size(), false);
        for (WorldAwakenedAscensionOfferRuntime runtime : pending) {
            String choosePrefix = "/wa ascension choose " + target.getGameProfile().getName() + " " + runtime.instanceId() + " ";
            MutableComponent line = Component.literal(" - ")
                    .append(offerDisplayComponent(datapackService, source, runtime.offerId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(runCommandButton("Open", "/wa ascension open " + target.getGameProfile().getName(), "Open the active ascension offer"))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", runtime.instanceId(), runtime.instanceId()));
            sendInspectLine(source, line);
            sendInspectLine(source, pendingChoicesLine(datapackService, source, runtime, choosePrefix));
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("offer_id="
                        + runtime.offerId()
                        + " instance="
                        + runtime.instanceId()
                        + " source="
                        + runtime.sourceKey()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return pending.size();
    }

    private static int runAscensionOpen(
            CommandSourceStack source,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            sendOperatorFailure(source, "Choose a player for /wa ascension open.");
            return 0;
        }

        return ascensionService.activeOfferView(target.serverLevel(), target)
                .map(view -> {
                    WorldAwakenedNetwork.sendOpenAscensionOffer(target, view);
                    if (!samePlayerActor(source, target) || showVerboseOperatorDetails()) {
                        sendOperatorSummary(source, Component.literal("Opened ")
                                .append(Component.literal(view.title()).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" for "))
                                .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)),
                                true);
                    }
                    if (showVerboseOperatorDetails()) {
                        sendOperatorDetail(source, Component.literal("offer_id="
                                + view.offerId()
                                + " instance="
                                + view.instanceId())
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    sendOperatorFailure(source, target.getGameProfile().getName() + " has no pending ascension offers.");
                    return 0;
                });
    }

    private static int runAscensionGrantOffer(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation offerId) {
        WorldAwakenedAscensionService.GrantResult result = ascensionService.grantOfferFromCommand(target.serverLevel(), target, offerId);
        if (result.status() == WorldAwakenedAscensionService.GrantStatus.GRANTED) {
            if (!samePlayerActor(source, target) || showVerboseOperatorDetails()) {
                MutableComponent message = Component.literal("Granted ")
                        .append(offerDisplayComponent(datapackService, source, offerId).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" to "))
                        .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA));
                sendOperatorSummary(source, message, true);
            }
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("offer_id="
                        + offerId
                        + " instance="
                        + result.instanceId()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not grant ")
                .append(offerDisplayComponent(datapackService, source, offerId))
                .append(Component.literal(": " + describeAscensionDetail(result.detail())))
                .append(debugCodeSuffix(result.detail())));
        return 0;
    }

    private static int runAscensionChoose(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            String instanceId,
            ResourceLocation rewardId) {
        if (instanceId == null || instanceId.isBlank()) {
            sendOperatorFailure(source, "Choose an offer instance first.");
            return 0;
        }
        Optional<WorldAwakenedAscensionOfferRuntime> runtime = ascensionService.pendingOffers(target.serverLevel(), target).stream()
                .filter(candidate -> candidate.instanceId().equals(instanceId))
                .findFirst();
        WorldAwakenedAscensionService.ChooseResult result = ascensionService.chooseReward(
                target.serverLevel(),
                target,
                instanceId,
                rewardId,
                "command");
        if (result.status() == WorldAwakenedAscensionService.ChooseStatus.ACCEPTED) {
            if (!samePlayerActor(source, target) || showVerboseOperatorDetails()) {
                MutableComponent message = Component.literal(target.getGameProfile().getName() + " chose ")
                        .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA));
                runtime.ifPresent(value -> message.append(Component.literal(" from "))
                        .append(offerDisplayComponent(datapackService, source, value.offerId()).withStyle(ChatFormatting.AQUA)));
                sendOperatorSummary(source, message, true);
            }
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("reward_id="
                        + rewardId
                        + " instance="
                        + instanceId).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("Could not choose that reward: " + describeAscensionDetail(result.detail()))
                .append(debugCodeSuffix(result.detail())));
        return 0;
    }

    private static int runAscensionRevoke(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId) {
        WorldAwakenedAscensionService.RevokeSummary summary = ascensionService.revokeReward(target.serverLevel(), target, rewardId);
        if (summary.changed()) {
            MutableComponent message = Component.literal("Revoked ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" from "))
                    .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA));
            sendOperatorSummary(source, message, true);
            if (showVerboseOperatorDetails()) {
                sendOperatorDetail(source, Component.literal("reward_id="
                        + rewardId
                        + " reopened_offers="
                        + summary.reopenedOffers()
                        + " loose_reward_cleanup="
                        + summary.removedLooseRewardOnly()).withStyle(ChatFormatting.DARK_GRAY));
            }
            return 1;
        }
        sendOperatorFailure(source, Component.literal("That reward is not active on this player.").append(debugCodeSuffix("reward_not_owned")));
        return 0;
    }

    private static int runAscensionInspect(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            sendOperatorFailure(source, "Choose a player for /wa ascension inspect.");
            return 0;
        }

        ServerLevel level = target.serverLevel();
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(target.getUUID());
        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(level, target);
        List<WorldAwakenedAscensionOfferRuntime> resolved = ascensionService.resolvedOffers(level, target);
        Map<ResourceLocation, WorldAwakenedAscensionService.RewardSuppressionView> suppressionViews = new LinkedHashMap<>();
        for (ResourceLocation rewardId : state.chosenAscensionRewards()) {
            suppressionViews.put(rewardId, ascensionService.inspectRewardSuppression(level, target, rewardId));
        }
        Map<ResourceLocation, ResourceLocation> ownedCarriers = WorldAwakenedOwnedCarrierService.snapshot(target);
        List<OwnedModifierView> liveModifiers = collectOwnedModifierViews(target);
        List<FailedClosedRewardComponentView> failedClosed = collectFailedClosedRewardComponents(
                datapackService.currentSnapshot(),
                target,
                state,
                ownedCarriers,
                suppressionViews);
        List<ForeignModifierView> foreignModifiers = collectForeignModifierViews(target);

        sendOperatorSummary(source, "Ascension inspect for "
                + target.getGameProfile().getName()
                + ": pending="
                + pending.size()
                + ", resolved="
                + resolved.size()
                + ", chosen="
                + state.chosenAscensionRewards().size()
                + ", suppressed_rewards="
                + state.suppressedAscensionRewards().size()
                + ", forfeited="
                + state.forfeitedAscensionRewards().size(), false);

        sendInspectLine(source, " - Pending offers");
        for (WorldAwakenedAscensionOfferRuntime runtime : pending) {
            String choosePrefix = "/wa ascension choose " + target.getGameProfile().getName() + " " + runtime.instanceId() + " ";
            MutableComponent line = Component.literal("   ")
                    .append(offerDisplayComponent(datapackService, source, runtime.offerId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(runCommandButton("Open", "/wa ascension open " + target.getGameProfile().getName(), "Open the active ascension offer"))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", runtime.instanceId(), runtime.instanceId()))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton("Choose", choosePrefix, "Prefill a choose command for this offer"));
            sendInspectLine(source, line);
            sendInspectLine(source, pendingChoicesLine(datapackService, source, runtime, choosePrefix));
            sendInspectLine(source, Component.literal("   offer_id="
                    + runtime.offerId()
                    + " instance="
                    + runtime.instanceId()
                    + " source="
                    + runtime.sourceKey()).withStyle(ChatFormatting.DARK_GRAY));
        }

        sendInspectLine(source, " - Resolved offers");
        for (WorldAwakenedAscensionOfferRuntime runtime : resolved) {
            MutableComponent line = Component.literal("   ")
                    .append(offerDisplayComponent(datapackService, source, runtime.offerId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" -> "))
                    .append(runtime.chosenRewardId()
                            .map(rewardId -> rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                            .orElse(Component.literal("<none>").withStyle(ChatFormatting.DARK_GRAY)))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", runtime.instanceId(), runtime.instanceId()))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton(
                            "Reopen",
                            "/wa ascension reopen " + target.getGameProfile().getName() + " " + runtime.instanceId(),
                            "Prefill a reopen command"))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton(
                            "Clear",
                            "/wa ascension clear " + target.getGameProfile().getName() + " " + runtime.instanceId(),
                            "Prefill a clear command"));
            sendInspectLine(source, line);
            sendInspectLine(source, Component.literal("   offer_id="
                    + runtime.offerId()
                    + " instance="
                    + runtime.instanceId()
                    + " chosen_id="
                    + runtime.chosenRewardId().map(ResourceLocation::toString).orElse("<none>")).withStyle(ChatFormatting.DARK_GRAY));
        }

        sendInspectLine(source, " - Chosen rewards");
        for (ResourceLocation rewardId : state.chosenAscensionRewards()) {
            var rewardDefinition = datapackService.currentSnapshot().data().ascensionRewards().get(rewardId);
            WorldAwakenedAscensionService.RewardSuppressionView suppressionViewRaw = suppressionViews.get(rewardId);
            final WorldAwakenedAscensionService.RewardSuppressionView suppressionView = suppressionViewRaw != null
                    ? suppressionViewRaw
                    : new WorldAwakenedAscensionService.RewardSuppressionView(
                            rewardId,
                            true,
                            false,
                            WorldAwakenedAscensionService.RewardLiveState.UNKNOWN,
                            Set.of(),
                            Set.of(),
                            Set.of(),
                            "",
                            "",
                            false);
            if (rewardDefinition == null) {
                sendInspectLine(source, Component.literal("   " + rewardId + " (missing definition, state=missing_definition)")
                        .withStyle(ChatFormatting.RED));
                continue;
            }
            String suppressCommand = "/wa ascension suppress reward " + target.getGameProfile().getName() + " " + rewardId;
            String unsuppressCommand = "/wa ascension unsuppress reward " + target.getGameProfile().getName() + " " + rewardId;
            MutableComponent rewardLine = Component.literal("   ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", rewardId.toString(), rewardId.toString()))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton("Suppress", suppressCommand, "Prefill reward suppression"))
                    .append(Component.literal(" "))
                    .append(suggestCommandButton("Unsuppress", unsuppressCommand, "Prefill reward unsuppression"));
            sendInspectLine(source, rewardLine);
            List<ResourceLocation> componentTypes = rewardDefinition.components().stream()
                    .map(component -> component.type())
                    .toList();
            String debugText = WorldAwakenedComponentDebugFormatter.formatChosenAscensionReward(rewardId, componentTypes);
            sendInspectLine(source, Component.literal("   " + debugText.replace(System.lineSeparator(), " | "))
                    .withStyle(ChatFormatting.DARK_GRAY));
            String sourceValue = state.ascensionRewardSources().getOrDefault(rewardId, "<unknown>");
            long unlockTimestamp = state.ascensionRewardUnlockTimestamps().getOrDefault(rewardId, 0L);
            sendInspectLine(source, Component.literal("   source=" + sourceValue + " unlocked_at=" + unlockTimestamp)
                    .withStyle(ChatFormatting.DARK_GRAY));
            sendInspectLine(source, Component.literal("   state=" + suppressionView.liveState().name().toLowerCase(Locale.ROOT)
                    + " reward_suppressed=" + suppressionView.rewardSuppressed()
                    + " grouped_suppression=" + suppressionView.groupedSuppressionActive()).withStyle(ChatFormatting.DARK_GRAY));
            sendInspectLine(source, Component.literal("   suppressed_component_keys="
                    + suppressionView.configuredSuppressedComponentKeys()
                    + " effective="
                    + suppressionView.effectiveSuppressedComponentKeys()).withStyle(ChatFormatting.DARK_GRAY));
            if (!suppressionView.missingSuppressedComponentKeys().isEmpty()) {
                sendInspectLine(source, Component.literal("   code="
                        + WorldAwakenedDiagnosticCodes.ASC_SUPPRESSED_DEFINITION_MISSING
                        + " missing_component_keys="
                        + suppressionView.missingSuppressedComponentKeys()).withStyle(ChatFormatting.RED));
            }
            if (suppressionView.liveState() == WorldAwakenedAscensionService.RewardLiveState.SUPPRESSION_REJECTED_INVALID_GROUP_STATE
                    || suppressionView.liveState() == WorldAwakenedAscensionService.RewardLiveState.SUPPRESSION_REJECTED_NOT_INDEPENDENTLY_SUPPORTED) {
                sendInspectLine(source, Component.literal("   code="
                        + suppressionView.rejectionCode()
                        + " detail="
                        + suppressionView.rejectionDetail()).withStyle(ChatFormatting.RED));
            }
            List<String> suppressibleComponentKeys = ascensionService.suppressibleComponentKeys(level, target, rewardId);
            if (!suppressibleComponentKeys.isEmpty()) {
                String componentHint = suppressibleComponentKeys.stream().collect(Collectors.joining(", "));
                sendInspectLine(source, Component.literal("   suppressible_component_keys=" + componentHint).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        sendInspectLine(source, " - Active owned carriers");
        if (ownedCarriers.isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            ownedCarriers.forEach((stableKey, carrierId) -> sendInspectLine(
                    source,
                    Component.literal("   carrier=" + carrierId + " key=" + stableKey).withStyle(ChatFormatting.DARK_GRAY)));
        }

        sendInspectLine(source, " - Live WA-owned modifiers");
        if (liveModifiers.isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (OwnedModifierView modifier : liveModifiers) {
                sendInspectLine(source, Component.literal("   attribute="
                        + modifier.attributeId()
                        + " modifier="
                        + modifier.modifierId()
                        + " amount="
                        + modifier.amount()
                        + " op="
                        + modifier.operation()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        sendInspectLine(source, " - Failed-closed reward components");
        if (failedClosed.isEmpty()) {
            sendInspectLine(source, Component.literal("   <none>").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (FailedClosedRewardComponentView failure : failedClosed) {
                sendInspectLine(source, Component.literal("   reward="
                        + failure.rewardId()
                        + " component="
                        + failure.componentType()
                        + " code="
                        + failure.code()
                        + " detail="
                        + failure.detail()).withStyle(ChatFormatting.RED));
            }
        }

        sendInspectLine(source, " - Foreign state intentionally preserved");
        if (foreignModifiers.isEmpty()) {
            sendInspectLine(
                    source,
                    Component.literal("   No foreign modifiers detected on WA-managed attributes. Foreign effects, visuals, and unrelated mod state are still preserved by design.")
                            .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (ForeignModifierView modifier : foreignModifiers) {
                sendInspectLine(source, Component.literal("   code="
                        + WorldAwakenedDiagnosticCodes.PLAYER_FOREIGN_MODIFIER_PRESERVED
                        + " attribute="
                        + modifier.attributeId()
                        + " modifier="
                        + modifier.modifierId()).withStyle(ChatFormatting.DARK_GRAY));
            }
            sendInspectLine(
                    source,
                    Component.literal("   Third-party modifiers are untouched. Foreign effects and visual state are also preserved unless a documented compat contract says otherwise.")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }

        sendInspectLine(source, " - Forfeited rewards by offer");
        state.forfeitedAscensionRewardsByOffer().forEach((instanceId, rewards) -> {
            String rewardNames = rewards.stream()
                    .map(rewardId -> rewardPlainText(datapackService, rewardId))
                    .collect(Collectors.joining(", "));
            sendInspectLine(
                    source,
                    Component.literal("   " + rewardNames)
                            .append(Component.literal(" "))
                            .append(copyButton("Copy Instance", instanceId, instanceId)));
            sendInspectLine(
                    source,
                    Component.literal("   instance=" + instanceId + " reward_ids=" + rewards).withStyle(ChatFormatting.DARK_GRAY));
        });

        return state.chosenAscensionRewards().size();
    }

    private static int runAscensionChooseActive(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target,
            ResourceLocation rewardId) {
        Optional<WorldAwakenedAscensionOfferRuntime> runtime = ascensionService.activePendingOffer(target.serverLevel(), target);
        if (runtime.isEmpty()) {
            sendOperatorFailure(source, Component.literal(target.getGameProfile().getName() + " has no pending ascension offers.")
                    .append(debugCodeSuffix("no_pending_offer")));
            return 0;
        }
        return runAscensionChoose(
                source,
                datapackService,
                ascensionService,
                target,
                runtime.get().instanceId(),
                rewardId);
    }

    private static String formatStageResult(WorldAwakenedStageMutationResult result) {
        return formatStageResult(result, null);
    }

    private static String formatStageResult(WorldAwakenedStageMutationResult result, ServerPlayer targetPlayer) {
        ResourceLocation primaryStageId = result.resolvedStageId().orElse(result.requestedStageId());
        String targetSuffix = targetPlayer == null ? "" : " for " + targetPlayer.getGameProfile().getName();
        return switch (result.status()) {
            case UNLOCKED -> "Unlocked stage " + primaryStageId + targetSuffix + replacedStageSuffix(result);
            case ALREADY_UNLOCKED -> "Stage " + primaryStageId + " was already unlocked" + targetSuffix + ".";
            case LOCKED -> "Locked stage " + primaryStageId + targetSuffix + ".";
            case ALREADY_LOCKED -> "Stage " + primaryStageId + " was already locked" + targetSuffix + ".";
            case BLOCKED -> "Could not change stage " + primaryStageId + targetSuffix + ": "
                    + describeStageMutationDetail(result.message());
            case INVALID -> "Could not change stage " + result.requestedStageId() + targetSuffix + ": "
                    + describeStageMutationDetail(result.message());
        };
    }

    private static ServerLevel requireCommandLevel(CommandSourceStack source, String failureMessage) {
        ServerLevel level = source.getLevel();
        if (level != null) {
            return level;
        }
        level = source.getServer().overworld();
        if (level == null) {
            sendOperatorFailure(source, failureMessage);
            return null;
        }
        return level;
    }

    private static int reportStageMutation(
            CommandSourceStack source,
            WorldAwakenedStageMutationResult result,
            ServerPlayer targetPlayer) {
        String message = formatStageResult(result, targetPlayer);
        if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED
                || result.status() == WorldAwakenedStageMutationStatus.LOCKED) {
            sendOperatorSummary(source, message, true);
            sendVerboseStageMutationDetails(source, result);
            return 1;
        }
        if (result.status() == WorldAwakenedStageMutationStatus.ALREADY_LOCKED
                || result.status() == WorldAwakenedStageMutationStatus.ALREADY_UNLOCKED) {
            sendOperatorSummary(source, message, false);
            sendVerboseStageMutationDetails(source, result);
            return 0;
        }
        sendOperatorFailure(source, message);
        sendVerboseStageMutationDetails(source, result);
        return 0;
    }

    private static void sendVerboseStageMutationDetails(
            CommandSourceStack source,
            WorldAwakenedStageMutationResult result) {
        if (!showVerboseOperatorDetails()) {
            return;
        }
        String resolved = result.resolvedStageId().map(ResourceLocation::toString).orElse("<none>");
        String replaced = result.replacedStageId().map(ResourceLocation::toString).orElse("<none>");
        sendOperatorDetail(source, Component.literal("requested="
                + result.requestedStageId()
                + " resolved="
                + resolved
                + " replaced="
                + replaced
                + " detail="
                + result.message()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String replacedStageSuffix(WorldAwakenedStageMutationResult result) {
        return result.replacedStageId()
                .map(replaced -> " (replaced " + replaced + ")")
                .orElse(".");
    }

    private static String describeStageMutationDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "unknown reason";
        }
        if (detail.startsWith("Unknown stage id or alias: ")) {
            return "that stage ID is not loaded";
        }
        if (detail.startsWith("Resolved stage is missing from registry: ")) {
            return "that stage is missing from the loaded registry";
        }
        if (detail.contains("policy is exclusive_group")) {
            return "another exclusive stage in that progression group is already active";
        }
        if (detail.equals("Stage regression is disabled by config")) {
            return "stage locking is disabled by configuration";
        }
        return detail.replace("exclusive_group", "exclusive group");
    }

    private static boolean samePlayerActor(CommandSourceStack source, ServerPlayer target) {
        return target != null
                && source.getEntity() instanceof ServerPlayer player
                && player.getUUID().equals(target.getUUID());
    }

    private static ServerPlayer sourcePlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static String actorName(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.getGameProfile().getName();
        }
        return source.getTextName();
    }

    private static List<OwnedModifierView> collectOwnedModifierViews(ServerPlayer target) {
        List<OwnedModifierView> views = new ArrayList<>();
        for (Holder<Attribute> attribute : WorldAwakenedAscensionRewardEffects.managedAttributes()) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            String attributeId = attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>");
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (WorldAwakenedAscensionRewardEffects.isWorldAwakenedOwnedModifier(modifier.id())) {
                    views.add(new OwnedModifierView(
                            attributeId,
                            modifier.id().toString(),
                            modifier.amount(),
                            modifier.operation().name().toLowerCase(Locale.ROOT)));
                }
            }
        }
        return views;
    }

    private static List<ForeignModifierView> collectForeignModifierViews(ServerPlayer target) {
        List<ForeignModifierView> views = new ArrayList<>();
        for (Holder<Attribute> attribute : WorldAwakenedAscensionRewardEffects.managedAttributes()) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            String attributeId = attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>");
            for (AttributeModifier modifier : instance.getModifiers()) {
                if (!WorldAwakenedAscensionRewardEffects.isWorldAwakenedOwnedModifier(modifier.id())) {
                    views.add(new ForeignModifierView(attributeId, modifier.id().toString()));
                }
            }
        }
        return views;
    }

    private static List<FailedClosedRewardComponentView> collectFailedClosedRewardComponents(
            WorldAwakenedDatapackSnapshot snapshot,
            ServerPlayer target,
            WorldAwakenedPlayerProgressionSavedData.PlayerStageState state,
            Map<ResourceLocation, ResourceLocation> ownedCarriers,
            Map<ResourceLocation, WorldAwakenedAscensionService.RewardSuppressionView> suppressionViews) {
        List<FailedClosedRewardComponentView> failures = new ArrayList<>();
        for (ResourceLocation rewardId : state.chosenAscensionRewards()) {
            WorldAwakenedAscensionService.RewardSuppressionView suppressionView = suppressionViews.get(rewardId);
            if (suppressionView != null
                    && suppressionView.liveState() == WorldAwakenedAscensionService.RewardLiveState.SUPPRESSED) {
                continue;
            }
            AscensionRewardDefinition rewardDefinition = snapshot.data().ascensionRewards().get(rewardId);
            if (rewardDefinition == null) {
                failures.add(new FailedClosedRewardComponentView(
                        rewardId.toString(),
                        "<definition>",
                        WorldAwakenedDiagnosticCodes.RUNTIME_INSTANCE_MISSING_DEFINITION,
                        "saved reward ownership remains, but the definition is missing so no live projection is applied"));
                continue;
            }
            for (int index = 0; index < rewardDefinition.components().size(); index++) {
                var component = rewardDefinition.components().get(index);
                if (!component.enabled()) {
                    continue;
                }
                String componentKey = index + "|" + component.type();
                if (suppressionView != null && suppressionView.effectiveSuppressedComponentKeys().contains(componentKey)) {
                    continue;
                }
                ResourceLocation componentType = component.type();
                Optional<Holder<Attribute>> attribute = WorldAwakenedAscensionRewardEffects.managedAttributeForComponent(componentType);
                if (attribute.isPresent()) {
                    if (!component.parameters().has("amount")
                            || !component.parameters().get("amount").isJsonPrimitive()
                            || !component.parameters().get("amount").getAsJsonPrimitive().isNumber()) {
                        failures.add(new FailedClosedRewardComponentView(
                                rewardId.toString(),
                                componentType.toString(),
                                WorldAwakenedDiagnosticCodes.ASCENSION_RECONCILE_COMPONENT_PARAM_INVALID,
                                "parameters.amount is missing or non-numeric"));
                        continue;
                    }
                    if (target.getAttribute(attribute.get()) == null) {
                        failures.add(new FailedClosedRewardComponentView(
                                rewardId.toString(),
                                componentType.toString(),
                                WorldAwakenedDiagnosticCodes.PLAYER_ATTRIBUTE_SURFACE_MISSING,
                                "required attribute surface is unavailable"));
                        continue;
                    }
                    ResourceLocation expectedKey = WorldAwakenedAscensionRewardEffects.stableOwnedKeyForComponent(rewardId, index);
                    boolean present = collectOwnedModifierViews(target).stream()
                            .anyMatch(view -> view.modifierId().equals(expectedKey.toString()));
                    if (!present) {
                        failures.add(new FailedClosedRewardComponentView(
                                rewardId.toString(),
                                componentType.toString(),
                                WorldAwakenedDiagnosticCodes.RECONCILE_OWNERSHIP_VIOLATION,
                                "expected WA-owned modifier projection is missing after reconcile"));
                    }
                    continue;
                }

                Optional<ResourceLocation> carrierId = WorldAwakenedAscensionRewardEffects.ownedCarrierForComponent(componentType);
                if (carrierId.isPresent()) {
                    ResourceLocation stableKey = WorldAwakenedAscensionRewardEffects.stableOwnedKeyForComponent(rewardId, index);
                    ResourceLocation actualCarrier = ownedCarriers.get(stableKey);
                    if (actualCarrier == null) {
                        failures.add(new FailedClosedRewardComponentView(
                                rewardId.toString(),
                                componentType.toString(),
                                carrierId.get().toString().contains("night_vision")
                                        ? WorldAwakenedDiagnosticCodes.VISUAL_CARRIER_UNAVAILABLE
                                        : WorldAwakenedDiagnosticCodes.GAMEPLAY_CARRIER_UNAVAILABLE,
                                "required WA-owned carrier is not active"));
                    } else if (!actualCarrier.equals(carrierId.get())) {
                        failures.add(new FailedClosedRewardComponentView(
                                rewardId.toString(),
                                componentType.toString(),
                                WorldAwakenedDiagnosticCodes.RECONCILE_OWNERSHIP_VIOLATION,
                                "active carrier does not match the expected carrier type"));
                    }
                    continue;
                }

                failures.add(new FailedClosedRewardComponentView(
                        rewardId.toString(),
                        componentType.toString(),
                        componentType.getPath().endsWith("_passive")
                                ? WorldAwakenedDiagnosticCodes.REWARD_CARRIER_TYPE_MISSING
                                : WorldAwakenedDiagnosticCodes.REWARD_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        "no safe WA-owned runtime surface exists for this component type"));
            }
        }
        return failures;
    }

    private record SpawnCommandTarget(ServerLevel level, BlockPos position) {
    }

    private record OwnedModifierView(String attributeId, String modifierId, double amount, String operation) {
    }

    private record FailedClosedRewardComponentView(String rewardId, String componentType, String code, String detail) {
    }

    private record ForeignModifierView(String attributeId, String modifierId) {
    }
}


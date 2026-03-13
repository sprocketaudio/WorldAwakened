package net.sprocketgames.worldawakened.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionOfferRuntime;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedComponentDebugFormatter;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDebugCommandService;
import net.sprocketgames.worldawakened.network.WorldAwakenedNetwork;
import net.sprocketgames.worldawakened.progression.WorldAwakenedEffectiveStageContext;
import net.sprocketgames.worldawakened.progression.WorldAwakenedPlayerProgressionSavedData;
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
    private WorldAwakenedCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedTriggerService triggerService,
            WorldAwakenedRuleService ruleService,
            WorldAwakenedAscensionService ascensionService) {
        WorldAwakenedDebugCommandService debugCommandService = new WorldAwakenedDebugCommandService(stageService, ascensionService);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wa")
                .then(Commands.literal("reload")
                        .then(Commands.literal("validate")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runReloadValidate(context.getSource(), datapackService))))
                .then(buildStageTree(stageService))
                .then(buildTriggerTree(datapackService, triggerService))
                .then(buildDumpTree(ruleService))
                .then(Commands.literal("compat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> runCompatList(context.getSource()))))
                .then(buildAscensionTree(datapackService, ascensionService));

        if (WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            root.then(buildDebugTree(datapackService, stageService, ascensionService, debugCommandService));
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

    private static int runReloadValidate(CommandSourceStack source, WorldAwakenedDatapackService datapackService) {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.reloadFromServer(source.getServer(), "command:/wa reload validate");
        source.sendSuccess(
                () -> Component.literal("World Awakened reload validation complete: " + snapshot.validationSummary().toCompactString()),
                true);
        if (!snapshot.validationSummary().diagnostics().isEmpty()
                && (snapshot.validationSummary().errorCount() > 0
                        || snapshot.validationSummary().warningCount() > 0
                        || showVerboseOperatorDetails())) {
            source.sendSuccess(
                    () -> Component.literal("First diagnostic: " + snapshot.validationSummary().diagnostics().get(0).asLogLine()),
                    false);
        }
        if (showVerboseOperatorDetails() && !snapshot.validationSummary().traceEvents().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("First trace: " + snapshot.validationSummary().traceEvents().get(0).asLogLine()),
                    false);
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

        source.sendSuccess(
                () -> Component.literal("World Awakened stage state: target=" + targetLabel
                        + ", configured=" + context.configuredMode().serializedName()
                        + ", effective=" + context.effectiveMode().serializedName()
                        + ", fallback=" + context.usedWorldFallback()
                        + ", unlocked=" + context.unlockedStages().size()),
                false);

        for (StageDefinition stage : registry.orderedStages()) {
            boolean unlocked = context.unlockedStages().contains(stage.id());
            String lockState = unlocked ? "UNLOCKED" : "LOCKED";
            MutableComponent line = Component.literal(" - [" + lockState + "] ")
                    .append(displayComponent(stage.displayName(), source, stage.id().toString()).withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", stage.id().toString(), "Copy stage ID"));
            source.sendSuccess(() -> line, false);
            if (showVerboseOperatorDetails()) {
                String group = stage.progressionGroup().map(value -> " group=" + value).orElse("");
                String hidden = stage.visibleToPlayers() ? "" : " hidden=true";
                source.sendSuccess(
                        () -> Component.literal("   id=" + stage.id()
                                + group
                                + " policy="
                                + stage.unlockPolicy().name().toLowerCase(Locale.ROOT)
                                + hidden)
                                .withStyle(ChatFormatting.DARK_GRAY),
                        false);
            }
        }

        for (ResourceLocation inactiveStage : context.inactiveUnlockedStages()) {
            source.sendSuccess(
                    () -> Component.literal(" - [UNLOCKED][INACTIVE] ")
                            .append(Component.literal(inactiveStage.toString()).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" "))
                            .append(copyButton("Copy ID", inactiveStage.toString(), "Copy inactive stage ID")),
                    false);
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
            source.sendFailure(Component.literal("This command needs a world context."));
            return 0;
        }

        TriggerRuleDefinition triggerRule = datapackService.currentSnapshot().data().triggerRules().get(triggerRuleId);
        if (triggerRule == null) {
            source.sendFailure(Component.literal("That trigger is not loaded: " + triggerRuleId));
            return 0;
        }
        if (!triggerRule.triggerType().equals(WorldAwakenedTriggerTypes.MANUAL_DEBUG)) {
            source.sendFailure(Component.literal("That trigger cannot be fired from the command line: " + triggerRuleId));
            return 0;
        }

        WorldAwakenedTriggerRunResult result = triggerService.fireManualTrigger(level, targetPlayer, triggerRuleId);
        String targetLabel = targetPlayer == null
                ? "global"
                : "player=" + targetPlayer.getGameProfile().getName();
        source.sendSuccess(
                () -> Component.literal("World Awakened trigger fire "
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
                        + result.stageUnlocks()),
                true);
        if (showVerboseOperatorDetails()) {
            source.sendSuccess(
                    () -> Component.literal("   trace="
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
                            .withStyle(ChatFormatting.DARK_GRAY),
                    false);
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
            source.sendFailure(Component.literal("This command needs a world context."));
            return 0;
        }

        var views = ruleService.inspectActiveRules(level, targetPlayer);
        long activeCount = views.stream().filter(WorldAwakenedRuleService.ActiveRuleView::eligible).count();
        String targetLabel = targetPlayer == null
                ? "global"
                : "player=" + targetPlayer.getGameProfile().getName();
        source.sendSuccess(() -> Component.literal("World Awakened active rules: target="
                + targetLabel
                + " dimension="
                + level.dimension().location()
                + " eligible="
                + activeCount
                + "/"
                + views.size()
                + (targetPlayer == null ? " scope=world" : " scope=player+world")),
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
            source.sendSuccess(() -> line, false);
            if (showVerboseOperatorDetails()) {
                String reason = view.rejectionReason().map(Enum::name).orElse("none");
                source.sendSuccess(() -> Component.literal("   cooldown_ms="
                        + view.cooldownRemainingMillis()
                        + " reason="
                        + reason
                        + " detail="
                        + view.detail()).withStyle(ChatFormatting.DARK_GRAY), false);
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

        source.sendSuccess(() -> Component.literal("World Awakened compatibility: auto_detect="
                + autoDetect
                + ", enable_detected_integrations="
                + defaultEnableDetected),
                false);
        source.sendSuccess(() -> Component.literal(" - Apotheosis: "
                + (apotheosisActive ? "active" : apotheosisLoaded ? "loaded but disabled" : "not loaded")
                + ", mode="
                + WorldAwakenedCommonConfig.APOTHEOSIS_MODE.get()),
                false);
        if (showVerboseOperatorDetails()) {
            source.sendSuccess(() -> Component.literal("   world_tier_conditions="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_CONDITIONS.get()
                    + ", stage_unlocks="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_STAGE_UNLOCKS.get()
                    + ", loot_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_LOOT_SCALING.get()
                    + ", invasion_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_INVASION_SCALING.get()
                    + ", mutator_scaling="
                    + WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_MUTATOR_SCALING.get()),
                    false);
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

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebugTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService,
            WorldAwakenedDebugCommandService debugCommandService) {
        return Commands.literal("debug")
                .requires(source -> source.hasPermission(2) && WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get())
                .then(Commands.literal("reset")
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
                                                        EntityArgument.getPlayer(context, "player")))))))
                .then(Commands.literal("clear")
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
                                                                StringArgumentType.getString(context, "instance_id"))))))));
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

    private static CompletableFuture<Suggestions> suggestStrings(List<String> values, SuggestionsBuilder builder) {
        for (String value : values) {
            builder.suggest(value);
        }
        return builder.buildFuture();
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
            source.sendSuccess(() -> message, true);
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   instance=" + instanceId).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("That offer cannot be reopened because it is already pending or no longer exists.")
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
            source.sendSuccess(() -> message, true);
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   instance=" + instanceId).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("That offer was not found.").append(debugCodeSuffix("instance_not_found")));
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
            source.sendFailure(Component.literal("Choose a player for /wa ascension list."));
            return 0;
        }

        ServerLevel level = target.serverLevel();
        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(level, target);
        List<WorldAwakenedAscensionOfferRuntime> resolved = ascensionService.resolvedOffers(level, target);
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(target.getUUID());

        source.sendSuccess(() -> Component.literal("Ascension state for "
                + target.getGameProfile().getName()
                + ": pending="
                + pending.size()
                + ", resolved="
                + resolved.size()
                + ", chosen="
                + state.chosenAscensionRewards().size()),
                false);
        for (ResourceLocation chosen : state.chosenAscensionRewards()) {
            source.sendSuccess(() -> Component.literal(" - chosen: ")
                    .append(rewardDisplayComponent(datapackService, source, chosen).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", chosen.toString(), chosen.toString())), false);
        }

        return state.chosenAscensionRewards().size();
    }

    private static int runAscensionPending(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.literal("Choose a player for /wa ascension pending."));
            return 0;
        }

        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(target.serverLevel(), target);
        if (pending.isEmpty()) {
            source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " has no pending ascension offers."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Pending ascension offers for "
                + target.getGameProfile().getName()
                + ": "
                + pending.size()), false);
        for (WorldAwakenedAscensionOfferRuntime runtime : pending) {
            String choosePrefix = "/wa ascension choose " + target.getGameProfile().getName() + " " + runtime.instanceId() + " ";
            MutableComponent line = Component.literal(" - ")
                    .append(offerDisplayComponent(datapackService, source, runtime.offerId()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(runCommandButton("Open", "/wa ascension open " + target.getGameProfile().getName(), "Open the active ascension offer"))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy Instance", runtime.instanceId(), runtime.instanceId()));
            source.sendSuccess(() -> line, false);
            source.sendSuccess(() -> pendingChoicesLine(datapackService, source, runtime, choosePrefix), false);
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   offer_id="
                        + runtime.offerId()
                        + " instance="
                        + runtime.instanceId()
                        + " source="
                        + runtime.sourceKey()).withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return pending.size();
    }

    private static int runAscensionOpen(
            CommandSourceStack source,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.literal("Choose a player for /wa ascension open."));
            return 0;
        }

        return ascensionService.activeOfferView(target.serverLevel(), target)
                .map(view -> {
                    WorldAwakenedNetwork.sendOpenAscensionOffer(target, view);
                    if (!samePlayerActor(source, target) || showVerboseOperatorDetails()) {
                        source.sendSuccess(() -> Component.literal("Opened ")
                                .append(Component.literal(view.title()).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" for "))
                                .append(Component.literal(target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA)),
                                true);
                    }
                    if (showVerboseOperatorDetails()) {
                        source.sendSuccess(() -> Component.literal("   offer_id="
                                + view.offerId()
                                + " instance="
                                + view.instanceId())
                                .withStyle(ChatFormatting.DARK_GRAY), false);
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    source.sendFailure(Component.literal(target.getGameProfile().getName() + " has no pending ascension offers."));
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
                source.sendSuccess(() -> message, true);
            }
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   offer_id="
                        + offerId
                        + " instance="
                        + result.instanceId()
                        + " detail="
                        + result.detail()).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("Could not grant ")
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
            source.sendFailure(Component.literal("Choose an offer instance first."));
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
                source.sendSuccess(() -> message, true);
            }
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   reward_id="
                        + rewardId
                        + " instance="
                        + instanceId).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("Could not choose that reward: " + describeAscensionDetail(result.detail()))
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
            source.sendSuccess(() -> message, true);
            if (showVerboseOperatorDetails()) {
                source.sendSuccess(() -> Component.literal("   reward_id="
                        + rewardId
                        + " reopened_offers="
                        + summary.reopenedOffers()
                        + " loose_reward_cleanup="
                        + summary.removedLooseRewardOnly()).withStyle(ChatFormatting.DARK_GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.literal("That reward is not active on this player.").append(debugCodeSuffix("reward_not_owned")));
        return 0;
    }

    private static int runAscensionInspect(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.literal("Choose a player for /wa ascension inspect."));
            return 0;
        }

        ServerLevel level = target.serverLevel();
        WorldAwakenedPlayerProgressionSavedData.PlayerStageState state = WorldAwakenedPlayerProgressionSavedData.get(level).getOrCreate(target.getUUID());
        List<WorldAwakenedAscensionOfferRuntime> pending = ascensionService.pendingOffers(level, target);
        List<WorldAwakenedAscensionOfferRuntime> resolved = ascensionService.resolvedOffers(level, target);

        source.sendSuccess(() -> Component.literal("Ascension inspect for "
                + target.getGameProfile().getName()
                + ": pending="
                + pending.size()
                + ", resolved="
                + resolved.size()
                + ", chosen="
                + state.chosenAscensionRewards().size()
                + ", forfeited="
                + state.forfeitedAscensionRewards().size()), false);

        source.sendSuccess(() -> Component.literal(" - Pending offers"), false);
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
            source.sendSuccess(() -> line, false);
            source.sendSuccess(() -> pendingChoicesLine(datapackService, source, runtime, choosePrefix), false);
            source.sendSuccess(() -> Component.literal("   offer_id="
                    + runtime.offerId()
                    + " instance="
                    + runtime.instanceId()
                    + " source="
                    + runtime.sourceKey()).withStyle(ChatFormatting.DARK_GRAY), false);
        }

        source.sendSuccess(() -> Component.literal(" - Resolved offers"), false);
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
            source.sendSuccess(() -> line, false);
            source.sendSuccess(() -> Component.literal("   offer_id="
                    + runtime.offerId()
                    + " instance="
                    + runtime.instanceId()
                    + " chosen_id="
                    + runtime.chosenRewardId().map(ResourceLocation::toString).orElse("<none>")).withStyle(ChatFormatting.DARK_GRAY), false);
        }

        source.sendSuccess(() -> Component.literal(" - Chosen rewards"), false);
        for (ResourceLocation rewardId : state.chosenAscensionRewards()) {
            var rewardDefinition = datapackService.currentSnapshot().data().ascensionRewards().get(rewardId);
            if (rewardDefinition == null) {
                source.sendSuccess(() -> Component.literal("   " + rewardId + " (missing definition)").withStyle(ChatFormatting.RED), false);
                continue;
            }
            source.sendSuccess(() -> Component.literal("   ")
                    .append(rewardDisplayComponent(datapackService, source, rewardId).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" "))
                    .append(copyButton("Copy ID", rewardId.toString(), rewardId.toString())), false);
            List<ResourceLocation> componentTypes = rewardDefinition.components().stream()
                    .map(component -> component.type())
                    .toList();
            String debugText = WorldAwakenedComponentDebugFormatter.formatChosenAscensionReward(rewardId, componentTypes);
            source.sendSuccess(() -> Component.literal("   " + debugText.replace(System.lineSeparator(), " | "))
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            String sourceValue = state.ascensionRewardSources().getOrDefault(rewardId, "<unknown>");
            long unlockTimestamp = state.ascensionRewardUnlockTimestamps().getOrDefault(rewardId, 0L);
            source.sendSuccess(() -> Component.literal("   source=" + sourceValue + " unlocked_at=" + unlockTimestamp)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        source.sendSuccess(() -> Component.literal(" - Forfeited rewards by offer"), false);
        state.forfeitedAscensionRewardsByOffer().forEach((instanceId, rewards) -> {
            String rewardNames = rewards.stream()
                    .map(rewardId -> rewardPlainText(datapackService, rewardId))
                    .collect(Collectors.joining(", "));
            source.sendSuccess(
                    () -> Component.literal("   " + rewardNames)
                            .append(Component.literal(" "))
                            .append(copyButton("Copy Instance", instanceId, instanceId)),
                    false);
            source.sendSuccess(
                    () -> Component.literal("   instance=" + instanceId + " reward_ids=" + rewards).withStyle(ChatFormatting.DARK_GRAY),
                    false);
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
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " has no pending ascension offers.")
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
            source.sendFailure(Component.literal(failureMessage));
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
            source.sendSuccess(() -> Component.literal(message), true);
            sendVerboseStageMutationDetails(source, result);
            return 1;
        }
        if (result.status() == WorldAwakenedStageMutationStatus.ALREADY_LOCKED
                || result.status() == WorldAwakenedStageMutationStatus.ALREADY_UNLOCKED) {
            source.sendSuccess(() -> Component.literal(message), false);
            sendVerboseStageMutationDetails(source, result);
            return 0;
        }
        source.sendFailure(Component.literal(message));
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
        source.sendSuccess(() -> Component.literal("   requested="
                + result.requestedStageId()
                + " resolved="
                + resolved
                + " replaced="
                + replaced
                + " detail="
                + result.message()).withStyle(ChatFormatting.DARK_GRAY), false);
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
}


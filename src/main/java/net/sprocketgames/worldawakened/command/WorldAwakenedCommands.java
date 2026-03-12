package net.sprocketgames.worldawakened.command;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.progression.WorldAwakenedEffectiveStageContext;
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
            WorldAwakenedRuleService ruleService) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("wa")
                .then(Commands.literal("reload")
                        .then(Commands.literal("validate")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> runReloadValidate(context.getSource(), datapackService))))
                .then(Commands.literal("stage")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .executes(context -> runStageList(context.getSource(), stageService)))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> runStageUnlock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id")))))
                        .then(Commands.literal("lock")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> runStageLock(
                                                context.getSource(),
                                                stageService,
                                                ResourceLocationArgument.getId(context, "id"))))))
                .then(Commands.literal("trigger")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("fire")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(context -> runTriggerFire(
                                                context.getSource(),
                                                datapackService,
                                                triggerService,
                                                ResourceLocationArgument.getId(context, "id"))))))
                .then(Commands.literal("dump")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("active_rules")
                                .executes(context -> runDumpActiveRules(
                                        context.getSource(),
                                        ruleService))));

        dispatcher.register(root);
    }

    private static int runReloadValidate(CommandSourceStack source, WorldAwakenedDatapackService datapackService) {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.reloadFromServer(source.getServer(), "command:/wa reload validate");
        source.sendSuccess(
                () -> Component.literal("World Awakened reload validation complete: " + snapshot.validationSummary().toCompactString()),
                true);
        if (!snapshot.validationSummary().diagnostics().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("First diagnostic: " + snapshot.validationSummary().diagnostics().get(0).asLogLine()),
                    false);
        }
        if (!snapshot.validationSummary().traceEvents().isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("First trace: " + snapshot.validationSummary().traceEvents().get(0).asLogLine()),
                    false);
        }
        return snapshot.validationSummary().errorCount() == 0 ? 1 : 0;
    }

    private static int runStageList(CommandSourceStack source, WorldAwakenedStageService stageService) {
        ServerLevel level = source.getServer().overworld();
        if (level == null) {
            source.sendFailure(Component.literal("World Awakened stage operations require an overworld instance"));
            return 0;
        }

        ServerPlayer player = sourcePlayer(source);
        WorldAwakenedEffectiveStageContext context = stageService.getEffectiveStageContext(level, player, source.getEntity());
        WorldAwakenedStageRegistry registry = stageService.stageRegistry();

        source.sendSuccess(
                () -> Component.literal("World Awakened stage state: configured=" + context.configuredMode().serializedName()
                        + ", effective=" + context.effectiveMode().serializedName()
                        + ", fallback=" + context.usedWorldFallback()
                        + ", unlocked=" + context.unlockedStages().size()),
                false);

        for (StageDefinition stage : registry.orderedStages()) {
            boolean unlocked = context.unlockedStages().contains(stage.id());
            String lockState = unlocked ? "UNLOCKED" : "LOCKED";
            String group = stage.progressionGroup().map(value -> " group=" + value).orElse("");
            String hidden = stage.visibleToPlayers() ? "" : " hidden=true";
            source.sendSuccess(
                    () -> Component.literal(" - [" + lockState + "] " + stage.id()
                            + group
                            + " policy=" + stage.unlockPolicy().name().toLowerCase(Locale.ROOT)
                            + hidden),
                    false);
        }

        for (ResourceLocation inactiveStage : context.inactiveUnlockedStages()) {
            source.sendSuccess(
                    () -> Component.literal(" - [UNLOCKED][INACTIVE] " + inactiveStage),
                    false);
        }

        return context.unlockedStages().size();
    }

    private static int runStageUnlock(
            CommandSourceStack source,
            WorldAwakenedStageService stageService,
            ResourceLocation stageId) {
        ServerLevel level = source.getServer().overworld();
        if (level == null) {
            source.sendFailure(Component.literal("World Awakened stage operations require an overworld instance"));
            return 0;
        }

        WorldAwakenedStageMutationResult result = stageService.unlockStage(
                level,
                sourcePlayer(source),
                stageId,
                "command:/wa stage unlock");

        if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED) {
            source.sendSuccess(() -> Component.literal(formatStageResult(result)), true);
            return 1;
        }
        if (result.status() == WorldAwakenedStageMutationStatus.ALREADY_UNLOCKED) {
            source.sendSuccess(() -> Component.literal(formatStageResult(result)), false);
            return 0;
        }

        source.sendFailure(Component.literal(formatStageResult(result)));
        return 0;
    }

    private static int runStageLock(
            CommandSourceStack source,
            WorldAwakenedStageService stageService,
            ResourceLocation stageId) {
        ServerLevel level = source.getServer().overworld();
        if (level == null) {
            source.sendFailure(Component.literal("World Awakened stage operations require an overworld instance"));
            return 0;
        }

        WorldAwakenedStageMutationResult result = stageService.lockStage(level, sourcePlayer(source), stageId);
        if (result.status() == WorldAwakenedStageMutationStatus.LOCKED) {
            source.sendSuccess(() -> Component.literal(formatStageResult(result)), true);
            return 1;
        }
        if (result.status() == WorldAwakenedStageMutationStatus.ALREADY_LOCKED) {
            source.sendSuccess(() -> Component.literal(formatStageResult(result)), false);
            return 0;
        }

        source.sendFailure(Component.literal(formatStageResult(result)));
        return 0;
    }

    private static int runTriggerFire(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedTriggerService triggerService,
            ResourceLocation triggerRuleId) {
        ServerLevel level = source.getServer().overworld();
        if (level == null) {
            source.sendFailure(Component.literal("World Awakened trigger operations require an overworld instance"));
            return 0;
        }

        TriggerRuleDefinition triggerRule = datapackService.currentSnapshot().data().triggerRules().get(triggerRuleId);
        if (triggerRule == null) {
            source.sendFailure(Component.literal("Unknown trigger rule id: " + triggerRuleId));
            return 0;
        }
        if (!triggerRule.triggerType().equals(WorldAwakenedTriggerTypes.MANUAL_DEBUG)) {
            source.sendFailure(Component.literal("Trigger rule is not manual_debug: " + triggerRuleId));
            return 0;
        }

        WorldAwakenedTriggerRunResult result = triggerService.fireManualTrigger(level, sourcePlayer(source), triggerRuleId);
        source.sendSuccess(
                () -> Component.literal("World Awakened trigger fire "
                        + triggerRuleId
                        + " trace="
                        + result.traceId()
                        + ": evaluated="
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
                        + result.genericRuleStageLocks()),
                true);
        return result.executedRules();
    }

    private static int runDumpActiveRules(
            CommandSourceStack source,
            WorldAwakenedRuleService ruleService) {
        ServerLevel level = source.getServer().overworld();
        if (level == null) {
            source.sendFailure(Component.literal("World Awakened rule inspection requires an overworld instance"));
            return 0;
        }

        ServerPlayer player = sourcePlayer(source);
        var views = ruleService.inspectActiveRules(level, player);
        long activeCount = views.stream().filter(WorldAwakenedRuleService.ActiveRuleView::eligible).count();
        source.sendSuccess(() -> Component.literal("World Awakened active rules: eligible="
                + activeCount
                + "/"
                + views.size()
                + (player == null ? " scope=world" : " scope=player+world")),
                false);

        for (WorldAwakenedRuleService.ActiveRuleView view : views) {
            String reason = view.rejectionReason().map(Enum::name).orElse("none");
            source.sendSuccess(() -> Component.literal(" - "
                    + view.ruleId()
                    + " scope="
                    + view.executionScope().name().toLowerCase(Locale.ROOT)
                    + " priority="
                    + view.priority()
                    + " eligible="
                    + view.eligible()
                    + " cooldown_ms="
                    + view.cooldownRemainingMillis()
                    + " consumed="
                    + view.consumed()
                    + " reason="
                    + reason
                    + " detail="
                    + view.detail()),
                    false);
        }

        return (int) activeCount;
    }

    private static String formatStageResult(WorldAwakenedStageMutationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("World Awakened stage ").append(result.status().name().toLowerCase(Locale.ROOT));
        result.resolvedStageId().ifPresent(resolved -> builder.append(": ").append(resolved));
        result.replacedStageId().ifPresent(replaced -> builder.append(" (replaced ").append(replaced).append(")"));
        if (!result.message().isBlank()) {
            builder.append(" - ").append(result.message());
        }
        return builder.toString();
    }

    private static ServerPlayer sourcePlayer(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }
}


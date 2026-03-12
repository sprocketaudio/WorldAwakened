package net.sprocketgames.worldawakened.rules.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.ExecutionScope;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;

class WorldAwakenedRuleEngineTest {
    @Test
    void singlePassSnapshotDoesNotSeeStageUnlockFromEarlierRule() {
        RuleDefinition unlockStage = rule(
                "testpack:unlock_stage",
                100,
                1.0D,
                List.of(),
                List.of(node("{\"type\":\"worldawakened:unlock_stage\",\"stage\":\"testpack:awakened\"}")),
                Optional.empty());
        RuleDefinition gatedByUnlockedStage = rule(
                "testpack:gated",
                10,
                1.0D,
                List.of(node("{\"type\":\"worldawakened:stage_unlocked\",\"stage\":\"testpack:awakened\"}")),
                List.of(node("{\"type\":\"worldawakened:send_warning_message\",\"message\":\"ready\"}")),
                Optional.empty());

        WorldAwakenedStageRegistry stageRegistry = WorldAwakenedStageRegistry.from(Map.of(
                id("testpack:awakened"),
                stage("testpack:awakened")));

        var evaluation = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(unlockStage, gatedByUnlockedStage)),
                stageRegistry,
                context(Set.of(), Set.of(), WorldAwakenedRuleStateSnapshot.empty(), WorldAwakenedRuleStateSnapshot.empty()),
                true);

        assertEquals(2, evaluation.evaluatedRules());
        assertEquals(1, evaluation.matchedCount());
        assertEquals(id("testpack:unlock_stage"), evaluation.matchedRules().get(0).rule().id());
        assertTrue(evaluation.decisions().stream()
                .anyMatch(decision -> decision.rule().id().equals(id("testpack:gated"))
                        && decision.rejectionReason().orElse(null) == WorldAwakenedRejectionReason.STAGE_CONDITION_FAILED));
    }

    @Test
    void worldContextConditionFailsClosedWhenPlayerDistanceUnavailable() {
        RuleDefinition distanceRule = rule(
                "testpack:distance_gate",
                0,
                1.0D,
                List.of(node("{\"type\":\"worldawakened:player_distance_from_spawn\",\"min\":128}")),
                List.of(node("{\"type\":\"worldawakened:send_warning_message\",\"message\":\"far enough\"}")),
                Optional.empty());

        var evaluation = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(distanceRule)),
                WorldAwakenedStageRegistry.empty(),
                new ContextBuilder(
                        Set.of(),
                        Set.of(),
                        WorldAwakenedRuleStateSnapshot.empty(),
                        WorldAwakenedRuleStateSnapshot.empty())
                                .withPlayerDistance(OptionalDouble.empty())
                                .build(),
                true);

        assertEquals(0, evaluation.matchedCount());
        assertEquals(
                WorldAwakenedRejectionReason.WORLD_CONTEXT_CONDITION_UNAVAILABLE,
                evaluation.decisions().get(0).rejectionReason().orElseThrow());
    }

    @Test
    void sameContextWithinTickProducesStableChanceOutcome() {
        RuleDefinition chanceRule = rule(
                "testpack:chance_rule",
                0,
                0.35D,
                List.of(),
                List.of(node("{\"type\":\"worldawakened:send_warning_message\",\"message\":\"chance\"}")),
                Optional.empty());

        WorldAwakenedRuleMatchContext firstContext = context(
                Set.of(),
                Set.of(),
                WorldAwakenedRuleStateSnapshot.empty(),
                WorldAwakenedRuleStateSnapshot.empty());

        var first = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(chanceRule)),
                WorldAwakenedStageRegistry.empty(),
                firstContext,
                true);
        var second = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(chanceRule)),
                WorldAwakenedStageRegistry.empty(),
                firstContext,
                true);

        assertEquals(first.decisions().get(0).matched(), second.decisions().get(0).matched());
        assertEquals(
                first.decisions().get(0).chanceRoll().orElseThrow(),
                second.decisions().get(0).chanceRoll().orElseThrow(),
                0.0D);

        WorldAwakenedRuleMatchContext sameTickDifferentWallClock = new ContextBuilder(
                Set.of(),
                Set.of(),
                WorldAwakenedRuleStateSnapshot.empty(),
                WorldAwakenedRuleStateSnapshot.empty())
                        .withNowMillis(50_000L)
                        .build();
        var third = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(chanceRule)),
                WorldAwakenedStageRegistry.empty(),
                sameTickDifferentWallClock,
                true);
        assertEquals(
                first.decisions().get(0).chanceRoll().orElseThrow(),
                third.decisions().get(0).chanceRoll().orElseThrow(),
                0.0D);
    }

    @Test
    void cooldownAndConsumedChecksShortCircuitBeforeChance() {
        RuleDefinition rule = rule(
                "testpack:cooldown_gate",
                0,
                0.1D,
                List.of(),
                List.of(node("{\"type\":\"worldawakened:send_warning_message\",\"message\":\"x\"}")),
                Optional.of(node("{\"seconds\":30}")));

        WorldAwakenedRuleStateSnapshot cooldownSnapshot = new WorldAwakenedRuleStateSnapshot(
                Map.of("testpack:cooldown_gate", 99_999L),
                Set.of());
        var cooldownEval = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(rule)),
                WorldAwakenedStageRegistry.empty(),
                new ContextBuilder(Set.of(), Set.of(), cooldownSnapshot, WorldAwakenedRuleStateSnapshot.empty())
                        .withNowMillis(1_000L)
                        .build(),
                true);
        assertFalse(cooldownEval.decisions().get(0).chanceRoll().isPresent());
        assertEquals(WorldAwakenedRejectionReason.COOLDOWN_ACTIVE, cooldownEval.decisions().get(0).rejectionReason().orElseThrow());

        WorldAwakenedRuleStateSnapshot consumedSnapshot = new WorldAwakenedRuleStateSnapshot(
                Map.of(),
                Set.of("testpack:cooldown_gate"));
        var consumedEval = WorldAwakenedRuleEngine.evaluate(
                WorldAwakenedRuleEngine.compile(List.of(rule)),
                WorldAwakenedStageRegistry.empty(),
                new ContextBuilder(Set.of(), Set.of(), consumedSnapshot, WorldAwakenedRuleStateSnapshot.empty())
                        .withNowMillis(1_000L)
                        .build(),
                true);
        assertFalse(consumedEval.decisions().get(0).chanceRoll().isPresent());
        assertEquals(WorldAwakenedRejectionReason.ONE_SHOT_CONSUMED, consumedEval.decisions().get(0).rejectionReason().orElseThrow());
    }

    private static WorldAwakenedRuleMatchContext context(
            Set<ResourceLocation> worldStages,
            Set<ResourceLocation> playerStages,
            WorldAwakenedRuleStateSnapshot worldRuleState,
            WorldAwakenedRuleStateSnapshot playerRuleState) {
        return new ContextBuilder(worldStages, playerStages, worldRuleState, playerRuleState).build();
    }

    private static RuleDefinition rule(
            String id,
            int priority,
            double chance,
            List<JsonElement> conditions,
            List<JsonElement> actions,
            Optional<JsonElement> cooldown) {
        return new RuleDefinition(
                1,
                id(id),
                true,
                priority,
                conditions,
                actions,
                1.0D,
                chance,
                cooldown,
                ExecutionScope.WORLD,
                List.of());
    }

    private static StageDefinition stage(String id) {
        return new StageDefinition(
                1,
                id(id),
                List.of(),
                JsonParser.parseString("\"" + id + "\""),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                true,
                true,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE,
                false);
    }

    private static JsonElement node(String rawJson) {
        return JsonParser.parseString(rawJson);
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static final class ContextBuilder {
        private final Set<ResourceLocation> worldStages;
        private final Set<ResourceLocation> playerStages;
        private final WorldAwakenedRuleStateSnapshot worldRuleState;
        private final WorldAwakenedRuleStateSnapshot playerRuleState;
        private OptionalDouble playerDistance = OptionalDouble.of(256.0D);
        private long nowMillis = 1_000L;

        private ContextBuilder(
                Set<ResourceLocation> worldStages,
                Set<ResourceLocation> playerStages,
                WorldAwakenedRuleStateSnapshot worldRuleState,
                WorldAwakenedRuleStateSnapshot playerRuleState) {
            this.worldStages = worldStages;
            this.playerStages = playerStages;
            this.worldRuleState = worldRuleState;
            this.playerRuleState = playerRuleState;
        }

        private ContextBuilder withPlayerDistance(OptionalDouble playerDistance) {
            this.playerDistance = playerDistance;
            return this;
        }

        private ContextBuilder withNowMillis(long nowMillis) {
            this.nowMillis = nowMillis;
            return this;
        }

        private WorldAwakenedRuleMatchContext build() {
            return new WorldAwakenedRuleMatchContext(
                    "test_event",
                    Optional.empty(),
                    id("minecraft:overworld"),
                    Optional.of(id("minecraft:plains")),
                    Optional.empty(),
                    Set.of(),
                    Optional.of("12345678-1234-1234-1234-123456789012"),
                    Optional.empty(),
                    true,
                    false,
                    false,
                    false,
                    OptionalLong.of(20L),
                    playerDistance,
                    3,
                    OptionalDouble.of(1.0D),
                    nowMillis,
                    40L,
                    worldStages,
                    playerStages,
                    worldRuleState,
                    playerRuleState,
                    Set.of(),
                    Set.of(),
                    Set.of("neoforge"),
                    Map.of("general.enable_mod", true, "compat.apotheosis.enabled", true),
                    false,
                    Optional.empty());
        }
    }
}

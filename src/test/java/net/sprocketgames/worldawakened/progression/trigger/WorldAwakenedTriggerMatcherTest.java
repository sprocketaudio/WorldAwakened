package net.sprocketgames.worldawakened.progression.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.SourceScope;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;

class WorldAwakenedTriggerMatcherTest {
    @Test
    void ordersMatchesByPriorityAfterEligibilityChecks() {
        TriggerRuleDefinition lowPriority = triggerRule(
                "testpack:low_priority",
                5,
                WorldAwakenedTriggerTypes.PLAYER_ENTER_DIMENSION,
                SourceScope.PLAYER,
                List.of(
                        node("{\"type\":\"worldawakened:current_dimension\",\"dimension\":\"minecraft:the_nether\"}"),
                        node("{\"type\":\"worldawakened:stage_locked\",\"stage\":\"testpack:nether_opened\"}")),
                Optional.empty(),
                false);
        TriggerRuleDefinition highPriority = triggerRule(
                "testpack:high_priority",
                30,
                WorldAwakenedTriggerTypes.PLAYER_ENTER_DIMENSION,
                SourceScope.PLAYER,
                List.of(
                        node("{\"type\":\"worldawakened:current_dimension\",\"dimension\":\"minecraft:the_nether\"}"),
                        node("{\"type\":\"worldawakened:stage_locked\",\"stage\":\"testpack:nether_opened\"}")),
                Optional.empty(),
                false);

        WorldAwakenedStageRegistry stageRegistry = WorldAwakenedStageRegistry.from(Map.of(
                id("testpack:nether_opened"),
                stage("testpack:nether_opened")));

        WorldAwakenedTriggerMatchResult result = WorldAwakenedTriggerMatcher.match(
                List.of(lowPriority, highPriority),
                stageRegistry,
                context(
                        WorldAwakenedTriggerTypes.PLAYER_ENTER_DIMENSION,
                        Optional.empty(),
                        true,
                        id("minecraft:the_nether"),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        false,
                        Optional.empty(),
                        1000L,
                        Set.of(),
                        Set.of(),
                        new WorldAwakenedTriggerStateSnapshot(Map.of(), Set.of()),
                        new WorldAwakenedTriggerStateSnapshot(Map.of(), Set.of())));

        assertEquals(2, result.matchedCount());
        assertEquals(id("testpack:high_priority"), result.matchedRules().get(0).rule().id());
        assertEquals(id("testpack:low_priority"), result.matchedRules().get(1).rule().id());
    }

    @Test
    void rejectsRulesBlockedByCooldownAndOneShot() {
        TriggerRuleDefinition rule = triggerRule(
                "testpack:manual_once",
                10,
                WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                SourceScope.WORLD,
                List.of(),
                Optional.of(node("{\"seconds\":10}")),
                true);

        WorldAwakenedTriggerMatchResult result = WorldAwakenedTriggerMatcher.match(
                List.of(rule),
                WorldAwakenedStageRegistry.empty(),
                context(
                        WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                        Optional.of(rule.id()),
                        false,
                        id("minecraft:overworld"),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        false,
                        Optional.of(rule.id()),
                        1000L,
                        Set.of(),
                        Set.of(),
                        new WorldAwakenedTriggerStateSnapshot(Map.of(
                                rule.id().toString(), 1500L), Set.of(rule.id().toString())),
                        WorldAwakenedTriggerStateSnapshot.empty()));

        assertEquals(0, result.matchedCount());
    }

    @Test
    void bossTriggerMatchesExplicitEntityRuleWithoutBossFlagMapHit() {
        TriggerRuleDefinition rule = triggerRule(
                "testpack:dragon_boss",
                0,
                WorldAwakenedTriggerTypes.BOSS_KILLED,
                SourceScope.WORLD,
                List.of(node("{\"type\":\"worldawakened:entity_type\",\"entity\":\"minecraft:ender_dragon\"}")),
                Optional.empty(),
                false);

        WorldAwakenedTriggerMatchResult result = WorldAwakenedTriggerMatcher.match(
                List.of(rule),
                WorldAwakenedStageRegistry.empty(),
                context(
                        WorldAwakenedTriggerTypes.BOSS_KILLED,
                        Optional.empty(),
                        false,
                        id("minecraft:the_end"),
                        Optional.empty(),
                        Optional.of(id("minecraft:ender_dragon")),
                        Set.of(),
                        false,
                        Optional.empty(),
                        1000L,
                        Set.of(),
                        Set.of(),
                        WorldAwakenedTriggerStateSnapshot.empty(),
                        WorldAwakenedTriggerStateSnapshot.empty()));

        assertEquals(1, result.matchedCount());
        assertEquals(rule.id(), result.matchedRules().get(0).rule().id());
    }

    @Test
    void manualTargetingLimitsEvaluationToSelectedRule() {
        TriggerRuleDefinition first = triggerRule(
                "testpack:manual_one",
                20,
                WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                SourceScope.WORLD,
                List.of(),
                Optional.empty(),
                false);
        TriggerRuleDefinition second = triggerRule(
                "testpack:manual_two",
                10,
                WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                SourceScope.WORLD,
                List.of(),
                Optional.empty(),
                false);

        WorldAwakenedTriggerMatchResult result = WorldAwakenedTriggerMatcher.match(
                List.of(first, second),
                WorldAwakenedStageRegistry.empty(),
                context(
                        WorldAwakenedTriggerTypes.MANUAL_DEBUG,
                        Optional.of(second.id()),
                        false,
                        id("minecraft:overworld"),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(),
                        false,
                        Optional.of(second.id()),
                        1000L,
                        Set.of(),
                        Set.of(),
                        WorldAwakenedTriggerStateSnapshot.empty(),
                        WorldAwakenedTriggerStateSnapshot.empty()));

        assertEquals(1, result.evaluatedRules());
        assertEquals(1, result.matchedCount());
        assertTrue(result.matchedRules().stream().allMatch(match -> match.rule().id().equals(second.id())));
    }

    private static WorldAwakenedTriggerMatchContext context(
            ResourceLocation triggerType,
            Optional<ResourceLocation> targetRuleId,
            boolean hasPlayerContext,
            ResourceLocation dimensionId,
            Optional<ResourceLocation> advancementId,
            Optional<ResourceLocation> entityId,
            Set<ResourceLocation> entityTags,
            boolean bossFlagMapMatch,
            Optional<ResourceLocation> manualTriggerId,
            long nowMillis,
            Set<ResourceLocation> worldStages,
            Set<ResourceLocation> playerStages,
            WorldAwakenedTriggerStateSnapshot worldTriggerState,
            WorldAwakenedTriggerStateSnapshot playerTriggerState) {
        return new WorldAwakenedTriggerMatchContext(
                triggerType,
                targetRuleId,
                hasPlayerContext,
                dimensionId,
                advancementId,
                entityId,
                entityTags,
                bossFlagMapMatch,
                manualTriggerId,
                nowMillis,
                worldStages,
                playerStages,
                worldTriggerState,
                playerTriggerState);
    }

    private static TriggerRuleDefinition triggerRule(
            String id,
            int priority,
            ResourceLocation triggerType,
            SourceScope sourceScope,
            List<JsonElement> conditions,
            Optional<JsonElement> cooldown,
            boolean oneShot) {
        return new TriggerRuleDefinition(
                1,
                id(id),
                true,
                priority,
                triggerType,
                sourceScope,
                conditions,
                List.of(node("{\"type\":\"worldawakened:increment_counter\",\"counter\":\"test\"}")),
                cooldown,
                oneShot);
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
}

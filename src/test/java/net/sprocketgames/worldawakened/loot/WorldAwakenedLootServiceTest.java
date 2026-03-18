package net.sprocketgames.worldawakened.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedLootServiceTest {
    @Test
    void evaluateDebugContextMatchesInjectProfile() {
        LootProfileDefinition profile = decodeProfile("""
                {
                  "id": "wa_test:inject_bonus",
                  "target_loot_tables": ["minecraft:chests/simple_dungeon"],
                  "replace_mode": "inject",
                  "entries": [
                    { "type": "item", "item": "minecraft:diamond", "weight": 1, "min": 1, "max": 1 }
                  ]
                }
                """);
        WorldAwakenedLootService service = newService(profile);

        ResourceLocation lootTable = ResourceLocation.parse("minecraft:chests/simple_dungeon");
        WorldAwakenedLootService.LootContextSnapshot context = new WorldAwakenedLootService.LootContextSnapshot(
                ResourceLocation.parse("worldawakened:debug_loot_evaluate"),
                WorldAwakenedLootService.LootTargetType.LOOT_TABLE,
                lootTable,
                Optional.of(lootTable),
                Optional.empty(),
                Set.of(),
                false,
                false,
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                ResourceLocation.parse("minecraft:overworld"),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(12L),
                Set.of(),
                Set.of(),
                WorldAwakenedStageRegistry.empty(),
                false,
                Optional.empty(),
                Set.of(),
                false,
                Set.of("minecraft"),
                Map.of("loot.enable_loot_evolution", true),
                "test-seed",
                true);

        WorldAwakenedLootService.LootRunResult result =
                service.evaluateDebugContext(context, Optional.empty(), java.util.List.of());

        assertFalse(result.skipped());
        assertTrue(result.appliedOnce());
        assertEquals(1, result.candidateProfiles().size());
        assertEquals(1, result.profileDecisions().stream().filter(WorldAwakenedLootService.ProfileDecision::matched).count());
        assertEquals(1, result.finalOutcome().size());
        assertEquals("minecraft:diamond", result.finalOutcome().get(0).itemId().toString());
    }

    @Test
    void evaluateDebugContextBlocksDestructiveModeByDefaultPolicy() {
        LootProfileDefinition profile = decodeProfile("""
                {
                  "id": "wa_test:replace_bonus",
                  "target_loot_tables": ["minecraft:chests/simple_dungeon"],
                  "replace_mode": "replace_entries",
                  "entries": [
                    { "type": "item", "item": "minecraft:diamond", "weight": 1, "min": 1, "max": 1 }
                  ]
                }
                """);
        WorldAwakenedLootService service = newService(profile);

        ResourceLocation lootTable = ResourceLocation.parse("minecraft:chests/simple_dungeon");
        WorldAwakenedLootService.LootContextSnapshot context = new WorldAwakenedLootService.LootContextSnapshot(
                ResourceLocation.parse("worldawakened:debug_loot_evaluate"),
                WorldAwakenedLootService.LootTargetType.LOOT_TABLE,
                lootTable,
                Optional.of(lootTable),
                Optional.empty(),
                Set.of(),
                false,
                false,
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                ResourceLocation.parse("minecraft:overworld"),
                Optional.empty(),
                Optional.empty(),
                OptionalLong.of(12L),
                Set.of(),
                Set.of(),
                WorldAwakenedStageRegistry.empty(),
                false,
                Optional.empty(),
                Set.of(),
                false,
                Set.of("minecraft"),
                Map.of("loot.enable_loot_evolution", true),
                "test-seed",
                true);

        WorldAwakenedLootService.LootRunResult result = service.evaluateDebugContext(
                context,
                Optional.empty(),
                java.util.List.of(new WorldAwakenedLootService.RewardItem(
                        ResourceLocation.parse("minecraft:stick"),
                        1)));

        assertFalse(result.skipped());
        assertEquals(0, result.profileDecisions().stream().filter(WorldAwakenedLootService.ProfileDecision::matched).count());
        assertTrue(result.profileDecisions().stream()
                .anyMatch(decision -> decision.diagnosticCode().equals("WA_REWARD_POLICY_DESTRUCTIVE_MODE_BLOCKED")));
        assertEquals(1, result.finalOutcome().size());
        assertEquals("minecraft:stick", result.finalOutcome().get(0).itemId().toString());
    }

    private static WorldAwakenedLootService newService(LootProfileDefinition profile) {
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        when(stageService.stageRegistry()).thenReturn(WorldAwakenedStageRegistry.empty());
        when(datapackService.currentSnapshot()).thenReturn(snapshotWithProfiles(profile));
        return new WorldAwakenedLootService(datapackService, stageService);
    }

    private static WorldAwakenedDatapackSnapshot snapshotWithProfiles(LootProfileDefinition... profiles) {
        Map<ResourceLocation, LootProfileDefinition> lootProfiles = new LinkedHashMap<>();
        for (LootProfileDefinition profile : profiles) {
            lootProfiles.put(profile.id(), profile);
        }
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.copyOf(lootProfiles),
                Map.of(),
                Map.of(),
                Map.of(),
                WorldAwakenedDataDrivenBossClassifier.fromMaps(java.util.List.of()));
        return new WorldAwakenedDatapackSnapshot(
                1L,
                Instant.now(),
                data,
                WorldAwakenedValidationSummary.empty());
    }

    private static LootProfileDefinition decodeProfile(String rawJson) {
        var json = JsonParser.parseString(rawJson);
        return LootProfileDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .result()
                .orElseThrow(() -> new AssertionError("Profile failed to decode"));
    }
}

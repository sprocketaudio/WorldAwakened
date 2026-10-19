package net.sprocketgames.worldawakened.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedMutableStageState;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageStateEngine;
import net.sprocketgames.worldawakened.rarity.RarityDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedLootServiceTest {
    @Test
    void evaluateDebugContextMatchesInjectProfile() {
        LootProfileDefinition profile = decodeProfile("""
                {
                  "id": "wa_test:inject_bonus",
                  "profile_type": "chest",
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
                OptionalDouble.empty(),
                Optional.empty(),
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
                  "profile_type": "chest",
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
                OptionalDouble.empty(),
                Optional.empty(),
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

    @Test
    void standardLootBandsFollowTheActivePhaseNotCumulativeUnlockHistory() throws IOException {
        Path root = projectRoot().resolve(Path.of("src", "main", "resources", "data", "worldawakened"));
        Map<ResourceLocation, StageDefinition> stages = new LinkedHashMap<>();
        List<ResourceLocation> order = List.of(
                ResourceLocation.parse("worldawakened:defaults/dormant"),
                ResourceLocation.parse("worldawakened:defaults/stirring"),
                ResourceLocation.parse("worldawakened:defaults/unease"),
                ResourceLocation.parse("worldawakened:defaults/nether_reached"),
                ResourceLocation.parse("worldawakened:defaults/gathering_storm"),
                ResourceLocation.parse("worldawakened:defaults/end_reached"),
                ResourceLocation.parse("worldawakened:defaults/world_awakened"));
        for (ResourceLocation id : order) {
            Path file = root.resolve("stages/defaults/" + id.getPath().substring("defaults/".length()) + ".json");
            StageDefinition stage = StageDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                    .result().orElseThrow();
            stages.put(stage.id(), stage);
        }
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(stages);
        List<LootProfileDefinition> profiles = new ArrayList<>();
        for (String file : List.of("early_mutation_bonus.json", "midgame_mutation_bonus.json", "late_mutation_bonus.json")) {
            profiles.add(decodeProfile(Files.readString(root.resolve("loot_profiles/defaults/" + file))));
        }
        WorldAwakenedLootService service = newService(registry, profiles.toArray(LootProfileDefinition[]::new));
        InMemoryStageState state = new InMemoryStageState();

        for (int index = 0; index < order.size(); index++) {
            WorldAwakenedStageStateEngine.unlockStage(registry, state, order.get(index), "test", index + 1L);
            Set<ResourceLocation> active = registry.activeStageIds(state.unlockedStages(), state.activeGroupStages());
            String expected = switch (index) {
                case 1, 2 -> "worldawakened:defaults/early_mutation_bonus";
                case 3, 4 -> "worldawakened:defaults/midgame_mutation_bonus";
                case 5, 6 -> "worldawakened:defaults/late_mutation_bonus";
                default -> null;
            };
            for (LootProfileDefinition profile : profiles) {
                WorldAwakenedLootService.LootRunResult evaluation = service.evaluateDebugContext(
                        entityContext(registry, active), Optional.of(profile.id()), List.of());
                WorldAwakenedLootService.ProfileDecision decision = evaluation.profileDecisions().getFirst();
                if (profile.id().toString().equals(expected)) {
                    assertNotEquals("stage_filter_failed", decision.reasonCategory(),
                            "The selected standard band must accept its active phase at step " + index);
                } else {
                    assertEquals("stage_filter_failed", decision.reasonCategory(),
                            "Inactive standard bands must be rejected by the active phase at step " + index);
                }
            }
        }
    }

    @Test
    void mutationRarityFilterOnlyControlsEligibilityAndDoesNotAlterItemChance() {
        LootProfileDefinition profile = decodeProfile("""
                {
                  "id": "wa_test:rare_bonus",
                  "profile_type": "mob",
                  "target_loot_tables": ["minecraft:entities/zombie"],
                  "mutation_rarity_filters": { "any_of": ["worldawakened:rare"] },
                  "authoring": { "item_chance": 1.0 },
                  "entries": [
                    { "type": "item", "item": "minecraft:diamond", "weight": 1, "min": 1, "max": 1 }
                  ]
                }
                """);
        WorldAwakenedLootService service = newService(profile);
        WorldAwakenedLootService.LootContextSnapshot rare = entityContext(
                WorldAwakenedStageRegistry.empty(), Set.of(), Optional.of(ResourceLocation.parse("worldawakened:rare")));
        WorldAwakenedLootService.LootContextSnapshot common = entityContext(
                WorldAwakenedStageRegistry.empty(), Set.of(), Optional.of(ResourceLocation.parse("worldawakened:common")));

        WorldAwakenedLootService.LootRunResult rareResult = service.evaluateDebugContext(rare, Optional.empty(), List.of());
        WorldAwakenedLootService.LootRunResult commonResult = service.evaluateDebugContext(common, Optional.empty(), List.of());

        assertEquals(1, rareResult.finalOutcome().size(), "matching rarity keeps the authored 100% item chance");
        assertTrue(commonResult.profileDecisions().stream()
                .anyMatch(decision -> "mutation_rarity_filter_failed".equals(decision.reasonCategory())));
    }

    @Test
    void minimumMutationRarityFilterUsesConfiguredThresholdsWithoutChangingAuthoredLoot() {
        LootProfileDefinition profile = decodeProfile("""
                {
                  "id": "wa_test:rare_or_higher_bonus",
                  "profile_type": "mob",
                  "target_loot_tables": ["minecraft:entities/zombie"],
                  "mutation_rarity_filters": { "minimum_rarity": "wa_test:rare" },
                  "authoring": { "item_chance": 1.0 },
                  "entries": [
                    { "type": "item", "item": "minecraft:diamond", "weight": 1, "min": 1, "max": 1 }
                  ]
                }
                """);
        ResourceLocation common = ResourceLocation.parse("wa_test:common");
        ResourceLocation rare = ResourceLocation.parse("wa_test:rare");
        ResourceLocation epic = ResourceLocation.parse("wa_test:epic");
        Map<ResourceLocation, RarityDefinition> rarities = Map.of(
                common, new RarityDefinition(1, common, true, new JsonPrimitive("Common"), 0),
                rare, new RarityDefinition(1, rare, true, new JsonPrimitive("Rare"), 20),
                epic, new RarityDefinition(1, epic, true, new JsonPrimitive("Epic"), 35));
        WorldAwakenedLootService service = newService(rarities, profile);

        WorldAwakenedLootService.LootRunResult commonResult = service.evaluateDebugContext(
                entityContext(WorldAwakenedStageRegistry.empty(), Set.of(), Optional.of(common)), Optional.empty(), List.of());
        WorldAwakenedLootService.LootRunResult rareResult = service.evaluateDebugContext(
                entityContext(WorldAwakenedStageRegistry.empty(), Set.of(), Optional.of(rare)), Optional.empty(), List.of());
        WorldAwakenedLootService.LootRunResult epicResult = service.evaluateDebugContext(
                entityContext(WorldAwakenedStageRegistry.empty(), Set.of(), Optional.of(epic)), Optional.empty(), List.of());

        assertTrue(commonResult.profileDecisions().stream()
                .anyMatch(decision -> "mutation_rarity_filter_failed".equals(decision.reasonCategory())));
        assertEquals(1, rareResult.finalOutcome().size(), "matching the configured threshold keeps the authored 100% chance");
        assertEquals(1, epicResult.finalOutcome().size(), "higher configured rarity remains eligible");
    }

    private static WorldAwakenedLootService newService(LootProfileDefinition profile) {
        return newService(WorldAwakenedStageRegistry.empty(), profile);
    }

    private static WorldAwakenedLootService newService(
            Map<ResourceLocation, RarityDefinition> rarities,
            LootProfileDefinition... profiles) {
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        when(stageService.stageRegistry()).thenReturn(WorldAwakenedStageRegistry.empty());
        when(datapackService.currentSnapshot()).thenReturn(snapshotWithProfiles(rarities, profiles));
        return new WorldAwakenedLootService(datapackService, stageService);
    }

    private static WorldAwakenedLootService newService(WorldAwakenedStageRegistry registry, LootProfileDefinition... profiles) {
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        when(stageService.stageRegistry()).thenReturn(registry);
        when(datapackService.currentSnapshot()).thenReturn(snapshotWithProfiles(profiles));
        return new WorldAwakenedLootService(datapackService, stageService);
    }

    private static WorldAwakenedLootService.LootContextSnapshot entityContext(
            WorldAwakenedStageRegistry registry, Set<ResourceLocation> activeStages) {
        return entityContext(registry, activeStages, Optional.empty());
    }

    private static WorldAwakenedLootService.LootContextSnapshot entityContext(
            WorldAwakenedStageRegistry registry, Set<ResourceLocation> activeStages,
            Optional<ResourceLocation> rarity) {
        ResourceLocation zombie = ResourceLocation.parse("minecraft:zombie");
        return new WorldAwakenedLootService.LootContextSnapshot(
                ResourceLocation.parse("worldawakened:debug_loot_evaluate"),
                WorldAwakenedLootService.LootTargetType.ENTITY, zombie, Optional.of(ResourceLocation.parse("minecraft:entities/zombie")),
                Optional.of(zombie), Set.of(), false, true, Set.of(), rarity, Optional.empty(), Optional.empty(),
                ResourceLocation.parse("minecraft:overworld"), Optional.empty(), Optional.empty(), OptionalLong.of(12L),
                activeStages, Set.of(), registry, false, Optional.empty(), Set.of(), false, OptionalDouble.empty(), Optional.empty(),
                Set.of("minecraft"), Map.of("loot.enable_loot_evolution", true), "standard-phase-test", true);
    }

    private static WorldAwakenedDatapackSnapshot snapshotWithProfiles(LootProfileDefinition... profiles) {
        return snapshotWithProfiles(Map.of(), profiles);
    }

    private static WorldAwakenedDatapackSnapshot snapshotWithProfiles(
            Map<ResourceLocation, RarityDefinition> rarities,
            LootProfileDefinition... profiles) {
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
                Map.copyOf(rarities),
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

    private static Path projectRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) return candidate;
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the project root");
    }

    private static final class InMemoryStageState implements WorldAwakenedMutableStageState {
        private final Set<ResourceLocation> unlockedStages = new LinkedHashSet<>();
        private final Map<ResourceLocation, Long> timestamps = new LinkedHashMap<>();
        private final Map<ResourceLocation, String> sources = new LinkedHashMap<>();
        private final Map<String, ResourceLocation> activeGroups = new LinkedHashMap<>();
        @Override public Set<ResourceLocation> unlockedStages() { return unlockedStages; }
        @Override public Map<ResourceLocation, Long> unlockTimestamps() { return timestamps; }
        @Override public Map<ResourceLocation, String> unlockSources() { return sources; }
        @Override public Map<String, ResourceLocation> activeGroupStages() { return activeGroups; }
        @Override public void markDirty() { }
    }
}

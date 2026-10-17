package net.sprocketgames.worldawakened.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedCompatibilityServiceTest {
    private enum ProbeTier {
        LOW,
        MID,
        HIGH
    }

    @Test
    void integrationActivationAppliesDeterministicGateComposition() {
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1,
                Optional.of(id("worldawakened:integration/apotheosis")),
                "apotheosis",
                Optional.of("Apotheosis"),
                true,
                "compat.apotheosis.enabled",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(id("minecraft:chests/simple_dungeon")),
                List.of(),
                Optional.empty());

        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(profile.id(), profile),
                Map.of(),
                WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        WorldAwakenedDatapackSnapshot snapshot =
                new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());

        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(snapshot);

        WorldAwakenedCompatibilityService service = new WorldAwakenedCompatibilityService(datapackService);
        WorldAwakenedCompatibilityService.IntegrationActivation activation =
                service.integrationActivationByModId("apotheosis").orElseThrow();

        boolean expectedActive = activation.autoDetectEnabled()
                && activation.enabledEffective()
                && activation.modLoaded()
                && activation.configGateEnabled();
        assertEquals(expectedActive, activation.active());
        if (!activation.active()) {
            assertFalse(activation.inactiveReason().isBlank());
        }
        assertTrue(service.scalarProviderKeys().contains(WorldAwakenedCompatibilityService.APOTHEOSIS_PROVIDER_KEY));
    }

    @Test
    void scalarProviderEvaluationFailsClosedForUnknownOrUnavailableProvider() {
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(WorldAwakenedDatapackSnapshot.empty());

        WorldAwakenedCompatibilityService service = new WorldAwakenedCompatibilityService(datapackService);

        WorldAwakenedCompatibilityService.ScalarProviderEvaluation unknown =
                service.evaluateScalarProvider("not_a_provider", null, null);
        assertFalse(unknown.found());
        assertFalse(unknown.available());
        assertEquals(WorldAwakenedDiagnosticCodes.DEBUG_SCALAR_PROVIDER_NOT_FOUND, unknown.diagnosticCode());

        WorldAwakenedCompatibilityService.ScalarProviderEvaluation unavailable =
                service.evaluateScalarProvider(WorldAwakenedCompatibilityService.APOTHEOSIS_PROVIDER_KEY, null, null);
        assertTrue(unavailable.found());
        assertFalse(unavailable.available());
        assertFalse(unavailable.diagnosticCode().isBlank());
    }

    @Test
    void coerceTierValueAcceptsEnumAndKnownTierNames() {
        assertEquals(2.0D, WorldAwakenedCompatibilityService.coerceTierValue(ProbeTier.HIGH).orElseThrow());
        assertEquals(1.0D, WorldAwakenedCompatibilityService.coerceTierValue("frontier").orElseThrow());
        assertEquals(3.0D, WorldAwakenedCompatibilityService.coerceTierValue("apotheosis:summit").orElseThrow());
    }

    @Test
    void coerceTierValueRejectsUnknownToken() {
        assertTrue(WorldAwakenedCompatibilityService.coerceTierValue("not_a_tier").isEmpty());
    }

    @Test
    void apotheosisDerivedStageHooksExposeCompiledThresholds() {
        ResourceLocation stage2 = id("testpack:apoth_tier_2");
        ResourceLocation stage4 = id("testpack:apoth_tier_4");
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1,
                Optional.of(id("worldawakened:integration/apotheosis")),
                "apotheosis",
                Optional.of("Apotheosis"),
                true,
                "compat.apotheosis.enabled",
                List.of(
                        node("""
                                {
                                  "type": "worldawakened:tier_threshold_unlock",
                                  "parameters": {
                                    "tier": 2,
                                    "unlock_stage": "testpack:apoth_tier_2"
                                  }
                                }
                                """),
                        node("""
                                {
                                  "type": "worldawakened:tier_threshold_unlock",
                                  "enabled": false,
                                  "parameters": {
                                    "tier": 3,
                                    "unlock_stage": "testpack:disabled"
                                  }
                                }
                                """),
                        node("""
                                {
                                  "type": "worldawakened:tier_threshold_unlock",
                                  "parameters": {
                                    "tier": 4,
                                    "unlock_stage": "testpack:apoth_tier_4"
                                  }
                                }
                                """)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());

        WorldAwakenedCompatibilityService service = serviceWith(profile, Map.of(
                stage2, stage(stage2),
                stage4, stage(stage4)));

        List<WorldAwakenedCompatibilityService.DerivedStageHookView> hooks = service.apotheosisDerivedStageHooks();

        assertEquals(2, hooks.size());
        assertEquals(2.0D, hooks.get(0).tierThreshold());
        assertEquals(stage2, hooks.get(0).unlockStage());
        assertEquals(4.0D, hooks.get(1).tierThreshold());
        assertEquals(stage4, hooks.get(1).unlockStage());
    }

    @Test
    void applyApotheosisDerivedStageHooksUnlocksOnlyMatchedThresholds() {
        ResourceLocation stage2 = id("testpack:apoth_tier_2");
        ResourceLocation stage4 = id("testpack:apoth_tier_4");
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1,
                Optional.of(id("worldawakened:integration/apotheosis")),
                "apotheosis",
                Optional.of("Apotheosis"),
                true,
                "compat.apotheosis.enabled",
                List.of(
                        node("""
                                {
                                  "type": "worldawakened:tier_threshold_unlock",
                                  "parameters": {
                                    "tier": 2,
                                    "unlock_stage": "testpack:apoth_tier_2"
                                  }
                                }
                                """),
                        node("""
                                {
                                  "type": "worldawakened:tier_threshold_unlock",
                                  "parameters": {
                                    "tier": 4,
                                    "unlock_stage": "testpack:apoth_tier_4"
                                  }
                                }
                                """)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());

        WorldAwakenedCompatibilityService service = serviceWith(profile, Map.of(
                stage2, stage(stage2),
                stage4, stage(stage4)));
        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        net.minecraft.server.level.ServerLevel level = mock(net.minecraft.server.level.ServerLevel.class);
        net.minecraft.server.level.ServerPlayer player = mock(net.minecraft.server.level.ServerPlayer.class);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.randomUUID(), "Dev"));
        when(stageService.unlockStage(eq(level), eq(player), eq(stage2), anyString()))
                .thenReturn(WorldAwakenedStageMutationResult.unlocked(stage2, stage2, Optional.empty(), "ok"));

        WorldAwakenedCompatibilityService.DerivedStageReconcileSummary summary =
                service.applyApotheosisDerivedStageHooks(level, player, 3.0D, stageService, "test");

        assertTrue(summary.evaluated());
        assertTrue(summary.tierAvailable());
        assertEquals(2, summary.hookCount());
        assertEquals(1, summary.matchedHookCount());
        assertEquals(1, summary.unlockedStageCount());
        verify(stageService).unlockStage(eq(level), eq(player), eq(stage2), anyString());
        verify(stageService, never()).unlockStage(eq(level), eq(player), eq(stage4), anyString());
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static com.google.gson.JsonElement node(String json) {
        return JsonParser.parseString(json);
    }

    private static StageDefinition stage(ResourceLocation id) {
        return new StageDefinition(
                1,
                id,
                List.of(),
                new JsonPrimitive(id.toString()),
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

    private static WorldAwakenedCompatibilityService serviceWith(
            IntegrationProfileDefinition profile,
            Map<ResourceLocation, StageDefinition> stages) {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(
                stages,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(profile.id(), profile),
                Map.of(),
                WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        WorldAwakenedDatapackSnapshot snapshot =
                new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(snapshot);
        return new WorldAwakenedCompatibilityService(datapackService);
    }
}

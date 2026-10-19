package net.sprocketgames.worldawakened.data.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnostic;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;

class WorldAwakenedDatapackLoaderIntegrationProfileValidationTest {
    @Test
    void validateIntegrationStageHooksRejectsUnsupportedHookType() {
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1,
                Optional.of(id("testpack:apotheosis_profile")),
                "apotheosis",
                Optional.of("Apotheosis"),
                true,
                "compat.apotheosis.enabled",
                List.of(node("""
                        {
                          "type": "worldawakened:not_supported",
                          "parameters": {
                            "tier": 2,
                            "unlock_stage": "testpack:apoth_stage"
                          }
                        }
                        """)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());

        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();
        WorldAwakenedDatapackLoader.validateIntegrationStageHooks(
                java.util.Map.of(profile.id(), profile),
                registry(stage("testpack:apoth_stage", List.of())),
                collector);

        List<WorldAwakenedDiagnostic> diagnostics = collector.build().diagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals(WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID, diagnostics.get(0).code());
    }

    @Test
    void validateIntegrationStageHooksRejectsMissingStageReference() {
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1,
                Optional.of(id("testpack:apotheosis_profile")),
                "apotheosis",
                Optional.of("Apotheosis"),
                true,
                "compat.apotheosis.enabled",
                List.of(node("""
                        {
                          "type": "worldawakened:tier_threshold_unlock",
                          "parameters": {
                            "tier": 2,
                            "unlock_stage": "testpack:missing_stage"
                          }
                        }
                        """)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());

        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();
        WorldAwakenedDatapackLoader.validateIntegrationStageHooks(
                java.util.Map.of(profile.id(), profile),
                registry(stage("testpack:other_stage", List.of())),
                collector);

        List<WorldAwakenedDiagnostic> diagnostics = collector.build().diagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals(WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING, diagnostics.get(0).code());
        assertTrue(diagnostics.get(0).message().contains("missing stage"));
    }

    @Test
    void validateIntegrationStageHooksAcceptsStageAlias() {
        IntegrationProfileDefinition profile = new IntegrationProfileDefinition(
                1, Optional.of(id("testpack:profile")), "apotheosis", Optional.empty(), true, "compat.apotheosis.enabled",
                List.of(node("""
                        { "type": "worldawakened:tier_threshold_unlock", "parameters": { "tier": 2, "unlock_stage": "testpack:old_stage" } }
                        """)), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty());
        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();

        WorldAwakenedDatapackLoader.validateIntegrationStageHooks(
                java.util.Map.of(profile.id(), profile), registry(stage("testpack:renamed_stage", List.of("testpack:old_stage"))), collector);

        assertTrue(collector.build().diagnostics().isEmpty());
    }

    private static WorldAwakenedStageRegistry registry(StageDefinition stage) {
        return WorldAwakenedStageRegistry.from(java.util.Map.of(stage.id(), stage));
    }

    private static StageDefinition stage(String stageId, List<String> aliases) {
        return new StageDefinition(1, id(stageId), aliases.stream().map(WorldAwakenedDatapackLoaderIntegrationProfileValidationTest::id).toList(),
                JsonParser.parseString("\"Stage\""), Optional.empty(), Optional.empty(), Optional.empty(), 0, true, true,
                List.of(), Optional.empty(), Optional.empty(), StageUnlockPolicy.CUMULATIVE, false);
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static JsonElement node(String json) {
        return JsonParser.parseString(json);
    }
}

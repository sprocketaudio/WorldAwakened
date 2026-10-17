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
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnostic;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;

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
                Set.of(id("testpack:apoth_stage")),
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
                Set.of(id("testpack:other_stage")),
                collector);

        List<WorldAwakenedDiagnostic> diagnostics = collector.build().diagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals(WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING, diagnostics.get(0).code());
        assertTrue(diagnostics.get(0).message().contains("missing stage"));
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private static JsonElement node(String json) {
        return JsonParser.parseString(json);
    }
}

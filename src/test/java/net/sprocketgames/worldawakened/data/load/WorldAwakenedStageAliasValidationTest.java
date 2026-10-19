package net.sprocketgames.worldawakened.data.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;

class WorldAwakenedStageAliasValidationTest {
    @Test
    void acceptsAUniqueAliasAndAllowsAliasReferences() {
        StageDefinition stage = stage("testpack:renamed", List.of("testpack:old"));
        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();

        Map<ResourceLocation, StageDefinition> accepted = WorldAwakenedDatapackLoader.dropConflictingStages(Map.of(stage.id(), stage), collector);
        Map<ResourceLocation, StageDefinition> referenced = WorldAwakenedDatapackLoader.dropObjectsMissingStageRefs(
                "stages", accepted, accepted, StageDefinition::id, ignored -> List.of(id("testpack:old")), collector);

        assertEquals(Map.of(stage.id(), stage), accepted);
        assertEquals(Map.of(stage.id(), stage), referenced);
        assertTrue(collector.build().diagnostics().isEmpty());
    }

    @Test
    void rejectsSharedAliasesDeterministically() {
        StageDefinition first = stage("testpack:first", List.of("testpack:old"));
        StageDefinition second = stage("testpack:second", List.of("testpack:old"));
        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();

        Map<ResourceLocation, StageDefinition> accepted = WorldAwakenedDatapackLoader.dropConflictingStages(
                Map.of(first.id(), first, second.id(), second), collector);

        assertTrue(accepted.isEmpty());
        assertEquals(2, collector.build().diagnostics().stream()
                .filter(diagnostic -> WorldAwakenedDiagnosticCodes.STAGE_ALIAS_CONFLICT.equals(diagnostic.code())).count());
    }

    @Test
    void rejectsAliasThatShadowsCanonicalIdWithoutRemovingCanonicalStage() {
        StageDefinition canonical = stage("testpack:existing", List.of());
        StageDefinition aliasOwner = stage("testpack:renamed", List.of("testpack:existing"));
        WorldAwakenedValidationSummary.Builder collector = new WorldAwakenedValidationSummary.Builder();

        Map<ResourceLocation, StageDefinition> accepted = WorldAwakenedDatapackLoader.dropConflictingStages(
                Map.of(canonical.id(), canonical, aliasOwner.id(), aliasOwner), collector);

        assertEquals(Map.of(canonical.id(), canonical), accepted);
    }

    private static StageDefinition stage(String value, List<String> aliases) {
        return new StageDefinition(1, id(value), aliases.stream().map(WorldAwakenedStageAliasValidationTest::id).toList(),
                JsonParser.parseString("\"Stage\""), Optional.empty(), Optional.empty(), Optional.empty(), 0, true, true,
                List.of(), Optional.empty(), Optional.empty(), StageUnlockPolicy.CUMULATIVE, false);
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

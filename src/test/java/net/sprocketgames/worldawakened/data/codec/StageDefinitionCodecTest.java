package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.StageDefinition;

class StageDefinitionCodecTest {
    @Test
    void decodesMinimalStageDefinition() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:baseline",
                  "display_name": "Baseline"
                }
                """);

        var result = StageDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Stage definition should decode");
        StageDefinition stage = result.result().orElseThrow();
        assertEquals("testpack:baseline", stage.id().toString());
        assertEquals(1, stage.schemaVersion());
        assertTrue(stage.visibleToPlayers());
    }

    @Test
    void decodesAdvancedStageAliases() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:renamed_stage",
                  "aliases": ["testpack:old_stage"],
                  "display_name": "Renamed"
                }
                """);

        StageDefinition stage = StageDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(List.of("testpack:old_stage"), stage.aliases().stream().map(Object::toString).toList());
    }
}


package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;

class InvasionProfileDefinitionCodecTest {
    @Test
    void decodesMinimalInvasionProfileDefinition() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:night_surge",
                  "display_name": "Night Surge",
                  "trigger_mode": "random_periodic",
                  "spawn_budget": 20,
                  "spawn_composition": [
                    {"entity": "minecraft:zombie", "weight": 10}
                  ]
                }
                """);

        var result = InvasionProfileDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Invasion profile should decode");

        InvasionProfileDefinition profile = result.result().orElseThrow();
        assertEquals("testpack:night_surge", profile.id().toString());
        assertEquals(1, profile.waveCount());
        assertEquals(20, profile.spawnBudget());
        assertEquals(1, profile.spawnComposition().size());
    }
}


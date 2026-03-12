package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;

class MobMutatorDefinitionCodecTest {
    @Test
    void decodesMutatorComponents() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:berserker",
                  "display_name": "Berserker",
                  "weight": 10,
                  "components": [
                    {
                      "type": "worldawakened:max_health_multiplier",
                      "parameters": {
                        "multiplier": 1.3
                      }
                    },
                    {
                      "type": "worldawakened:movement_speed_bonus",
                      "parameters": {
                        "amount": 0.1
                      }
                    }
                  ]
                }
                """);

        var result = MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Mutator definition should decode");

        MobMutatorDefinition mutator = result.result().orElseThrow();
        assertEquals("testpack:berserker", mutator.id().toString());
        assertEquals(2, mutator.components().size());
        assertEquals(1, mutator.maxStackCount());
        assertTrue(mutator.applicationContexts().contains("on_spawn"));
        assertTrue(mutator.exclusiveWith().isEmpty());
    }

}


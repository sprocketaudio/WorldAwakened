package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootReplaceMode;

class LootProfileDefinitionCodecTest {
    @Test
    void decodesArbitraryAuthoredLootProfileId() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:custom_tower_rewards",
                  "target_loot_tables": ["minecraft:chests/simple_dungeon"],
                  "replace_mode": "add_bonus_pool",
                  "entries": [
                    {
                      "type": "item",
                      "item": "minecraft:gold_ingot",
                      "weight": 8
                    }
                  ]
                }
                """);

        var result = LootProfileDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Loot profile should decode");

        LootProfileDefinition profile = result.result().orElseThrow();
        assertEquals("testpack:custom_tower_rewards", profile.id().toString());
        assertEquals(LootReplaceMode.ADD_BONUS_POOL, profile.replaceMode());
        assertEquals(1, profile.targetLootTables().size());
        assertEquals(1, profile.entries().size());
    }
}

package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootReplaceMode;
import net.sprocketgames.worldawakened.data.definition.LootProfileType;

class LootProfileDefinitionCodecTest {
    @Test
    void decodesArbitraryAuthoredLootProfileId() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:custom_tower_rewards",
                  "profile_type": "chest",
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

    @Test
    void decodesExplicitMobProfileAndIndependentXpDefinition() {
        var result = LootProfileDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"testpack:mob_xp","profile_type":"mob","authoring":{"target_entities":["minecraft:zombie"],"item_chance":0.25,"bonus_xp":{"mode":"extra","min":2,"max":4,"chance":0.5}},"entries":[]}
                """));
        assertTrue(result.result().isPresent());
        LootProfileDefinition profile = result.result().orElseThrow();
        assertEquals(LootProfileType.MOB, profile.profileType());
        assertEquals(1, profile.authoring().targetEntities().size());
        assertEquals(0.25D, profile.authoring().itemChance());
        assertTrue(profile.authoring().bonusXp().isPresent());
    }

    @Test
    void decodesExactMutationRarityEligibilityFilterWithoutChangingLootSettings() {
        var result = LootProfileDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"testpack:rare_only","profile_type":"mob","target_loot_tables":["minecraft:entities/zombie"],"mutation_rarity_filters":{"any_of":["testpack:rare"],"none_of":["testpack:cursed"]},"entries":[{"type":"item","item":"minecraft:diamond","weight":3}]}
                """));
        assertTrue(result.result().isPresent());
        LootProfileDefinition profile = result.result().orElseThrow();
        assertTrue(profile.mutationRarityFilters().isPresent());
        assertEquals("testpack:rare", profile.mutationRarityFilters().orElseThrow().getAsJsonObject()
                .getAsJsonArray("any_of").get(0).getAsString());
        assertEquals(3, profile.entries().getFirst().getAsJsonObject().get("weight").getAsInt());
    }
}

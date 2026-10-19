package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class WorldAwakenedLootProfileEditorConversionTest {
    @Test
    void convertsMobLootToChestLootWithoutLosingSharedAuthoring() {
        JsonObject original = JsonParser.parseString("""
                {
                  "id":"test:mob_rewards", "profile_type":"mob", "enabled":false,
                  "authoring":{"display_name":"Kept name", "description":"Kept description", "item_chance":0.25,
                    "target_entities":["minecraft:zombie"], "eligible_dimensions":["minecraft:overworld"],
                    "bonus_xp":{"enabled":true,"min":2,"max":4,"chance":0.5}},
                  "conditions":[{"type":"worldawakened:entity_is_mutated","parameters":{}},
                    {"type":"worldawakened:world_day_gte","parameters":{"value":3}}],
                  "stage_filters":{"required_any":["test:phase"]},
                  "entries":[{"type":"item","item":"minecraft:iron_nugget","weight":3,"min":1,"max":2}]
                }
                """).getAsJsonObject();

        JsonObject converted = WorldAwakenedClientConfigPayloadHandler.convertLootProfileTypeForEditor(original, "chest");

        assertEquals("chest", converted.get("profile_type").getAsString());
        assertFalse(converted.has("target_loot_tables"));
        assertFalse(converted.getAsJsonObject("authoring").has("target_entities"));
        assertFalse(converted.getAsJsonObject("authoring").has("bonus_xp"));
        assertEquals("Kept name", converted.getAsJsonObject("authoring").get("display_name").getAsString());
        assertEquals("Kept description", converted.getAsJsonObject("authoring").get("description").getAsString());
        assertEquals(0.25D, converted.getAsJsonObject("authoring").get("item_chance").getAsDouble());
        assertEquals(1, converted.getAsJsonArray("entries").size());
        assertEquals(1, converted.getAsJsonArray("conditions").size());
        assertEquals("worldawakened:world_day_gte", converted.getAsJsonArray("conditions").get(0)
                .getAsJsonObject().get("type").getAsString());
        assertTrue(original.getAsJsonObject("authoring").has("target_entities"), "Cancel leaves the original draft untouched.");
        assertTrue(original.getAsJsonObject("authoring").has("bonus_xp"), "Cancel leaves XP settings untouched.");
    }

    @Test
    void convertsChestLootToMobLootWithoutLosingSharedAuthoring() {
        JsonObject original = JsonParser.parseString("""
                {
                  "id":"test:chest_rewards", "profile_type":"chest", "enabled":true,
                  "target_loot_tables":["minecraft:chests/simple_dungeon"],
                  "authoring":{"display_name":"Kept name", "description":"Kept description", "item_chance":0.6,
                    "eligible_dimensions":["minecraft:the_nether"]},
                  "stage_filters":{"required_any":["test:phase"]},
                  "entries":[{"type":"item","item":"minecraft:arrow","weight":2,"min":1,"max":4}]
                }
                """).getAsJsonObject();

        JsonObject converted = WorldAwakenedClientConfigPayloadHandler.convertLootProfileTypeForEditor(original, "mob");

        assertEquals("mob", converted.get("profile_type").getAsString());
        assertFalse(converted.has("target_loot_tables"));
        assertFalse(converted.getAsJsonObject("authoring").has("target_entities"));
        assertEquals("Kept name", converted.getAsJsonObject("authoring").get("display_name").getAsString());
        assertEquals(0.6D, converted.getAsJsonObject("authoring").get("item_chance").getAsDouble());
        assertEquals("minecraft:the_nether", converted.getAsJsonObject("authoring").getAsJsonArray("eligible_dimensions")
                .get(0).getAsString());
        assertEquals("test:phase", converted.getAsJsonObject("stage_filters").getAsJsonArray("required_any")
                .get(0).getAsString());
        assertEquals("minecraft:arrow", converted.getAsJsonArray("entries").get(0).getAsJsonObject()
                .get("item").getAsString());
        assertTrue(original.has("target_loot_tables"), "Discard can restore the original chest draft from the server snapshot.");
    }
}

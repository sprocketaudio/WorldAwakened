package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class WorldAwakenedStandardProgressionPresentationTest {
    @Test
    void everyStandardStageTranslationResolvesToAReadableName() throws Exception {
        JsonObject language = resource("/assets/worldawakened/lang/en_us.json");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("dormant", "Dormant");
        expected.put("stirring", "Stirring");
        expected.put("unease", "Unease");
        expected.put("nether_reached", "Ignition");
        expected.put("gathering_storm", "Gathering Storm");
        expected.put("end_reached", "Fracture");
        expected.put("world_awakened", "World Awakened");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonObject stage = resource("/data/worldawakened/stages/defaults/" + entry.getKey() + ".json");
            String key = stage.getAsJsonObject("display_name").get("translate").getAsString();
            assertTrue(language.has(key), () -> "Missing English translation for " + key);
            assertEquals(entry.getValue(), language.get(key).getAsString());
        }
    }

    @Test
    void standardStagesDeclareCanonicalProgressionOrderIncludingDormantFirst() throws Exception {
        String[] paths = {"dormant", "stirring", "unease", "nether_reached", "gathering_storm",
                "end_reached", "world_awakened"};
        int previous = Integer.MIN_VALUE;
        for (String path : paths) {
            JsonObject stage = resource("/data/worldawakened/stages/defaults/" + path + ".json");
            int current = stage.get("sort_index").getAsInt();
            assertTrue(current > previous, "Stage sort order is not canonical at " + path);
            previous = current;
        }
    }

    @Test
    void editorTreatsCodecOmittedSortIndexAsExistingStageDefaultZero() {
        JsonObject codecEncodedDefault = new JsonObject();
        JsonObject explicitLaterStage = new JsonObject();
        explicitLaterStage.addProperty("sort_index", 60);

        assertEquals(0, WorldAwakenedClientConfigPayloadHandler.existingStageSortIndex(codecEncodedDefault));
        assertEquals(60, WorldAwakenedClientConfigPayloadHandler.existingStageSortIndex(explicitLaterStage));
    }

    @Test
    void eachStandardMilestoneTriggerIsDirectOnlyForItsIntendedStage() throws Exception {
        Map<String, String> expected = Map.of(
                "world_day_three", "stirring",
                "mine_diamond", "unease",
                "reach_nether", "nether_reached",
                "obtain_blaze_rod", "gathering_storm",
                "reach_end", "end_reached",
                "kill_dragon", "world_awakened");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            JsonObject trigger = resource("/data/worldawakened/trigger_rules/defaults/" + entry.getKey() + ".json");
            String intended = "worldawakened:defaults/" + entry.getValue();
            assertTrue(WorldAwakenedClientConfigPayloadHandler.isDirectTriggerForStage(trigger, intended),
                    () -> entry.getKey() + " was not direct for " + intended);
            assertTrue(expected.values().stream().filter(stage -> !stage.equals(entry.getValue()))
                    .noneMatch(stage -> WorldAwakenedClientConfigPayloadHandler.isDirectTriggerForStage(
                            trigger, "worldawakened:defaults/" + stage)),
                    () -> entry.getKey() + " was incorrectly direct for a catch-up stage");
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = WorldAwakenedStandardProgressionPresentationTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing test resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}

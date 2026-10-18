package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class WorldAwakenedEditorStageTriggerTest {
    @Test
    void cumulativeCatchUpActionIsNotMistakenForAnEarlierStagesDirectMilestone() {
        JsonObject dragonTrigger = JsonParser.parseString("""
                {
                  "trigger_type":"worldawakened:boss_killed",
                  "conditions":[
                    {"type":"worldawakened:entity_type","parameters":{"entity":"minecraft:ender_dragon"}},
                    {"type":"worldawakened:stage_locked","parameters":{"stage":"worldawakened:world_awakened"}}
                  ],
                  "actions":[
                    {"type":"worldawakened:unlock_stage","parameters":{"stage":"worldawakened:unease"}},
                    {"type":"worldawakened:unlock_stage","parameters":{"stage":"worldawakened:world_awakened"}}
                  ]
                }
                """).getAsJsonObject();

        assertFalse(WorldAwakenedClientConfigPayloadHandler.isDirectTriggerForStage(
                dragonTrigger, "worldawakened:unease"));
        assertTrue(WorldAwakenedClientConfigPayloadHandler.isDirectTriggerForStage(
                dragonTrigger, "worldawakened:world_awakened"));
    }

    @Test
    void triggerWithoutMatchingStageLockedGuardIsNotDirect() {
        JsonObject trigger = JsonParser.parseString("""
                {
                  "conditions":[{"type":"worldawakened:stage_locked","parameters":{"stage":"test:other"}}],
                  "actions":[{"type":"worldawakened:unlock_stage","parameters":{"stage":"test:target"}}]
                }
                """).getAsJsonObject();

        assertFalse(WorldAwakenedClientConfigPayloadHandler.isDirectTriggerForStage(trigger, "test:target"));
    }

    @Test
    void multiConditionEditorTriggersShareStableGateMetadata() {
        var conditions = List.of(
                new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(1, "20"),
                new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(2, "minecraft:the_nether"));

        var triggers = WorldAwakenedClientConfigPayloadHandler.buildEditorUnlockTriggers(
                null, "test:phase", conditions, true, 20, "test:main", false);

        assertEquals(2, triggers.size());
        JsonObject first = triggers.get(0).getAsJsonObject();
        JsonObject second = triggers.get(1).getAsJsonObject();
        assertEquals("player", first.get("source_scope").getAsString());
        assertFalse(first.get("one_shot").getAsBoolean());
        JsonObject firstGate = first.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonObject("parameters");
        JsonObject secondGate = second.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonObject("parameters");
        assertEquals(firstGate.get("group_token").getAsString(), secondGate.get("group_token").getAsString());
        assertEquals("all", firstGate.get("mode").getAsString());
        assertEquals(2, firstGate.getAsJsonArray("condition_tokens").size());
    }

    @Test
    void editingConditionChangesCompletionGroupToken() {
        var original = WorldAwakenedClientConfigPayloadHandler.buildEditorUnlockTriggers(null, "test:phase",
                List.of(new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(1, "20"),
                        new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(2, "minecraft:the_nether")),
                true, 20, "test:main", false);
        var edited = WorldAwakenedClientConfigPayloadHandler.buildEditorUnlockTriggers(null, "test:phase",
                List.of(new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(1, "30"),
                        new WorldAwakenedClientConfigPayloadHandler.UnlockConditionDraft(2, "minecraft:the_nether")),
                true, 20, "test:main", false);

        assertFalse(gateGroup(original).equals(gateGroup(edited)));
    }

    private static String gateGroup(com.google.gson.JsonArray triggers) {
        return triggers.get(0).getAsJsonObject().getAsJsonArray("actions").get(0).getAsJsonObject()
                .getAsJsonObject("parameters").get("group_token").getAsString();
    }
}

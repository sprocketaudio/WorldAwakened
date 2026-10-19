package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.config.WorldAwakenedProfileOverrideSavedData;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;

class WorldAwakenedPhaseOrderingTest {
    private static final List<String> STANDARD = List.of(
            "dormant", "stirring", "unease", "nether_reached", "gathering_storm", "end_reached", "world_awakened");

    @Test
    void selectedRowLeavesItsVisibleArrowAreaClickable() {
        assertTrue(WorldAwakenedPhaseOrdering.rowOwnsPoint(10, 20, 200, 34, 54, 40, 28));
        assertFalse(WorldAwakenedPhaseOrdering.rowOwnsPoint(10, 20, 200, 34, 54, 185, 40),
                "The row must not consume a lower-line click inside the arrow reserve");
        assertTrue(WorldAwakenedPhaseOrdering.rowOwnsPoint(10, 20, 200, 34, 54, 185, 25),
                "The upper On/Off line remains part of the selectable row");
    }

    @Test
    void savedManualFormerStartingPhaseCanMoveLater() throws Exception {
        Map<ResourceLocation, StageDefinition> definitions = standardDefinitions();
        List<WorldAwakenedPhaseOrdering.Entry> entries = definitions.values().stream()
                .map(stage -> new WorldAwakenedPhaseOrdering.Entry(stage.id().toString(), stage.sortIndex(),
                        false, true))
                .toList();

        assertTrue(WorldAwakenedClientConfigPayloadHandler.simplePhaseOrderEditable(
                new JsonArray(), id("dormant")), "A Manual phase deliberately has no direct unlock trigger");
        WorldAwakenedPhaseOrdering.MoveResult moved = WorldAwakenedPhaseOrdering.move(entries, id("dormant"), 1);

        assertTrue(moved.moved());
        assertEquals(standardIds(List.of("stirring", "dormant", "unease", "nether_reached", "gathering_storm",
                "end_reached", "world_awakened")), entryIds(moved.order()));
    }

    @Test
    void realStandardProfileMovesOnePhaseAndPersistsThatOrderThroughSavedData() throws Exception {
        Map<ResourceLocation, StageDefinition> definitions = standardDefinitions();
        List<WorldAwakenedPhaseOrdering.Entry> entries = definitions.values().stream()
                .map(stage -> new WorldAwakenedPhaseOrdering.Entry(stage.id().toString(), stage.sortIndex(),
                        stage.defaultUnlocked(), true))
                .toList();

        assertEquals(standardIds(STANDARD), entryIds(WorldAwakenedPhaseOrdering.ordered(entries)));
        assertTrue(WorldAwakenedPhaseOrdering.canMove(entries, id("dormant"), 1),
                "Default-unlocked state is independent from authored display order");
        assertTrue(WorldAwakenedPhaseOrdering.canMove(entries, id("stirring"), -1),
                "A phase may move across a default-unlocked cumulative phase");
        assertTrue(WorldAwakenedPhaseOrdering.canMove(entries, id("stirring"), 1));
        WorldAwakenedPhaseOrdering.MoveResult movedStarting = WorldAwakenedPhaseOrdering.move(
                entries, id("dormant"), 1);
        assertEquals(List.of(id("stirring"), id("dormant")), entryIds(movedStarting.order()).subList(0, 2));
        assertTrue(movedStarting.order().get(1).starting(), "Moving does not alter default-unlocked state");

        WorldAwakenedPhaseOrdering.MoveResult moved = WorldAwakenedPhaseOrdering.move(entries, id("stirring"), 1);
        assertTrue(moved.moved());
        assertEquals(standardIds(List.of("dormant", "unease", "stirring", "nether_reached", "gathering_storm",
                "end_reached", "world_awakened")), entryIds(moved.order()));

        Map<ResourceLocation, StageDefinition> authored = new LinkedHashMap<>();
        definitions.forEach((stageId, stage) -> authored.put(stageId,
                withSort(stage, moved.sortIndexes().get(stageId.toString()))));
        WorldAwakenedProfileOverrideSavedData saved = new WorldAwakenedProfileOverrideSavedData();
        saved.replaceAll(authored, Map.of(), Map.of());
        CompoundTag persisted = saved.save(new CompoundTag(), null);

        Map<ResourceLocation, StageDefinition> restarted = decodePersistedStages(persisted);
        assertEquals(standardIds(List.of("dormant", "unease", "stirring", "nether_reached", "gathering_storm",
                "end_reached", "world_awakened")), WorldAwakenedStageRegistry.from(restarted).orderedStages().stream()
                        .map(stage -> stage.id().toString()).toList());
        assertEquals(0, restarted.get(ResourceLocation.parse(id("dormant"))).sortIndex());
        assertEquals(20, restarted.get(ResourceLocation.parse(id("stirring"))).sortIndex());
        assertEquals(10, restarted.get(ResourceLocation.parse(id("unease"))).sortIndex());
    }

    private static Map<ResourceLocation, StageDefinition> standardDefinitions() throws Exception {
        Map<ResourceLocation, StageDefinition> result = new LinkedHashMap<>();
        for (String path : STANDARD) {
            try (var stream = WorldAwakenedPhaseOrderingTest.class.getResourceAsStream(
                    "/data/worldawakened/stages/defaults/" + path + ".json")) {
                StageDefinition stage = StageDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
                        .getOrThrow(message -> new IllegalArgumentException(message));
                result.put(stage.id(), stage);
            }
        }
        return result;
    }

    private static Map<ResourceLocation, StageDefinition> decodePersistedStages(CompoundTag persisted) {
        JsonObject root = JsonParser.parseString(persisted.getString("stages_json")).getAsJsonObject();
        Map<ResourceLocation, StageDefinition> result = new LinkedHashMap<>();
        root.entrySet().forEach(entry -> {
            StageDefinition stage = StageDefinition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                    .getOrThrow(message -> new IllegalArgumentException(message));
            result.put(stage.id(), stage);
        });
        return result;
    }

    private static StageDefinition withSort(StageDefinition stage, int sortIndex) {
        return new StageDefinition(stage.schemaVersion(), stage.id(), stage.aliases(), stage.displayName(),
                stage.shortName(), stage.description(), stage.icon(), sortIndex, stage.visibleToPlayers(),
                stage.enabled(), stage.tags(), stage.style(), stage.progressionGroup(), stage.unlockPolicy(),
                stage.defaultUnlocked());
    }

    private static List<String> standardIds(List<String> paths) {
        return paths.stream().map(WorldAwakenedPhaseOrderingTest::id).toList();
    }

    private static List<String> entryIds(List<WorldAwakenedPhaseOrdering.Entry> entries) {
        return entries.stream().map(WorldAwakenedPhaseOrdering.Entry::id).toList();
    }

    private static String id(String path) {
        return "worldawakened:defaults/" + path;
    }
}

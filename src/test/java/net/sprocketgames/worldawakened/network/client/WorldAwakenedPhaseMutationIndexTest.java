package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class WorldAwakenedPhaseMutationIndexTest {
    private static final String PREFIX = "worldawakened:defaults/";

    @Test
    void standardAppearsAsSevenUniquePhasesWithExpectedDimensionsAndPools() {
        List<String> stages = List.of("dormant", "stirring", "unease", "nether_reached", "gathering_storm",
                "end_reached", "world_awakened").stream().map(value -> PREFIX + value).toList();
        Map<String, JsonObject> pools = new LinkedHashMap<>();
        add(pools, "overworld_intro", "minecraft:overworld", .02);
        add(pools, "s2_overworld", "minecraft:overworld", .04);
        add(pools, "s3_overworld", "minecraft:overworld", .06);
        add(pools, "nether_escalation", "minecraft:the_nether", .08);
        add(pools, "s4_overworld", "minecraft:overworld", .08);
        add(pools, "s4_nether", "minecraft:the_nether", .11);
        add(pools, "s5_overworld", "minecraft:overworld", .10);
        add(pools, "s5_nether", "minecraft:the_nether", .13);
        add(pools, "end_escalation", "minecraft:the_end", .14);
        add(pools, "s6_overworld", "minecraft:overworld", .13);
        add(pools, "s6_nether", "minecraft:the_nether", .16);
        add(pools, "s6_end", "minecraft:the_end", .18);

        var index = WorldAwakenedPhaseMutationIndex.build(stages, pools, new JsonArray());

        assertEquals(7, index.configurationsByStage().size());
        assertTrue(index.configurations(PREFIX + "dormant").isEmpty());
        assertEquals(List.of("minecraft:overworld"), dimensions(index, "stirring"));
        assertEquals(List.of("minecraft:overworld"), dimensions(index, "unease"));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), dimensions(index, "nether_reached"));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether"), dimensions(index, "gathering_storm"));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"), dimensions(index, "end_reached"));
        assertEquals(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"),
                index.configurations(PREFIX + "world_awakened").stream()
                        .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId).toList());
        assertEquals(List.of("s6_overworld", "s6_nether", "s6_end"), index.configurations(PREFIX + "world_awakened")
                .stream().map(value -> value.poolId().substring(value.poolId().lastIndexOf('/') + 1)).toList());
    }

    private static List<String> dimensions(WorldAwakenedPhaseMutationIndex.Index index, String stagePath) {
        return index.configurations(PREFIX + stagePath).stream()
                .map(WorldAwakenedPhaseMutationIndex.Configuration::dimensionId).toList();
    }

    @Test
    void phaseMetadataSupportsMultipleNamedConfigurationsWithoutDuplicatingPhase() {
        String stage = "test:awakening"; Map<String, JsonObject> pools = new LinkedHashMap<>();
        pools.put("test:default", pool("test:default", "minecraft:overworld", .08));
        pools.put("test:skeletons", pool("test:skeletons", "minecraft:overworld", .25));
        JsonObject track = new JsonObject(); track.addProperty("id", WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID);
        track.addProperty("display_name", "Phase mob configurations"); JsonArray tiers = new JsonArray();
        tiers.add(tier(stage, "test:default", "minecraft:overworld", "Default Mobs", true));
        tiers.add(tier(stage, "test:skeletons", "minecraft:overworld", "Dangerous Skeletons", false));
        track.add("tiers", tiers); JsonArray tracks = new JsonArray(); tracks.add(track);

        var index = WorldAwakenedPhaseMutationIndex.build(List.of(stage), pools, tracks);

        assertEquals(1, index.configurationsByStage().size());
        assertEquals(List.of("Default Mobs", "Dangerous Skeletons"), index.configurations(stage).stream()
                .map(WorldAwakenedPhaseMutationIndex.Configuration::name).toList());
    }

    @Test
    void explicitMetadataCanDeactivateAndRenameBundledConfigurationWithoutDeletingIt() {
        String stage = PREFIX + "stirring", poolId = PREFIX + "overworld_intro";
        Map<String, JsonObject> pools = new LinkedHashMap<>(); add(pools, "overworld_intro", "minecraft:overworld", .02);
        JsonObject track = new JsonObject(); track.addProperty("id", WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID);
        JsonObject tier = tier(stage, poolId, "minecraft:overworld", "Early Overworld", true);
        tier.addProperty("active", false); JsonArray tiers = new JsonArray(); tiers.add(tier); track.add("tiers", tiers);
        JsonArray tracks = new JsonArray(); tracks.add(track);

        var configurations = WorldAwakenedPhaseMutationIndex.build(List.of(stage), pools, tracks).configurations(stage);

        assertEquals(1, configurations.size());
        assertEquals("Early Overworld", configurations.getFirst().name());
        assertTrue(!configurations.getFirst().active());
    }

    @Test
    void disabledBundledConfigurationRemainsPresentAndDistinctFromRemovedMetadata() {
        String stage = PREFIX + "unease", poolId = PREFIX + "s2_overworld";
        Map<String, JsonObject> pools = new LinkedHashMap<>();
        JsonObject disabled = pool(poolId, "minecraft:overworld", .04);
        disabled.addProperty("enabled", false); pools.put(poolId, disabled);

        var inferred = WorldAwakenedPhaseMutationIndex.build(List.of(stage), pools, new JsonArray()).configurations(stage);

        assertEquals(1, inferred.size());
        assertTrue(inferred.getFirst().active(), "Disabled controls gameplay, not configuration membership");

        JsonObject track = new JsonObject(); track.addProperty("id", WorldAwakenedPhaseMutationIndex.PHASE_TRACK_ID);
        JsonObject removed = tier(stage, poolId, "minecraft:overworld", "Default Mobs", true);
        removed.addProperty("active", false); JsonArray tiers = new JsonArray(); tiers.add(removed); track.add("tiers", tiers);
        JsonArray tracks = new JsonArray(); tracks.add(track);

        var explicitlyRemoved = WorldAwakenedPhaseMutationIndex.build(List.of(stage), pools, tracks).configurations(stage);
        assertEquals(1, explicitlyRemoved.size());
        assertTrue(!explicitlyRemoved.getFirst().active(), "Only explicit phase metadata represents removal");
    }

    @Test
    void pressureEventsRemainSeparateFromOrdinaryPhaseConfigurations() {
        String stage = PREFIX + "stirring";
        Map<String, JsonObject> pools = new LinkedHashMap<>();
        add(pools, "overworld_intro", "minecraft:overworld", .02);
        add(pools, "invasion_restless_night", "minecraft:overworld", .18);
        add(pools, "invasion_nether_surge", "minecraft:the_nether", .20);
        add(pools, "invasion_awakened_hunt", "minecraft:overworld", .22);

        var index = WorldAwakenedPhaseMutationIndex.build(List.of(stage), pools, new JsonArray());

        assertEquals(List.of(PREFIX + "overworld_intro"), index.configurations(stage).stream()
                .map(WorldAwakenedPhaseMutationIndex.Configuration::poolId).toList());
        assertEquals(List.of(PREFIX + "invasion_restless_night", PREFIX + "invasion_nether_surge",
                PREFIX + "invasion_awakened_hunt"), index.eventPoolIds());
    }

    private static void add(Map<String, JsonObject> pools, String path, String dimension, double chance) {
        pools.put(PREFIX + path, pool(PREFIX + path, dimension, chance));
    }
    private static JsonObject pool(String id, String dimension, double chance) {
        JsonObject pool = new JsonObject(); pool.addProperty("id", id); pool.addProperty("enabled", true);
        pool.addProperty("mutation_chance", chance); JsonArray dimensions = new JsonArray(); dimensions.add(dimension);
        pool.add("eligible_dimensions", dimensions); pool.add("conditions", new JsonArray()); return pool;
    }
    private static JsonObject tier(String stage, String pool, String dimension, String name, boolean isDefault) {
        JsonObject tier = new JsonObject(); tier.addProperty("stage_id", stage); tier.addProperty("pool_id", pool);
        tier.addProperty("dimension", dimension); tier.addProperty("configuration_name", name);
        tier.addProperty("default_configuration", isDefault); return tier;
    }
}

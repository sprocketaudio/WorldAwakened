package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class WorldAwakenedEditorTrackJsonTest {
    @Test
    void codecOmittedEmptyTiersIsAValidEmptyCollection() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "worldawakened:world/tracks/test");

        JsonArray tiers = WorldAwakenedEditorTrackJson.tiers(track);

        assertTrue(tiers.isEmpty());
        assertTrue(!track.has("tiers"), "Read-only access must not mutate a server snapshot");
    }

    @Test
    void firstTierCanBeAttachedWhenCodecOmittedEmptyTiers() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "worldawakened:world/tracks/test");
        JsonObject tier = new JsonObject();
        tier.addProperty("stage_id", "worldawakened:world/stage/awakening");
        tier.addProperty("pool_id", "worldawakened:world/pool/awakening");

        WorldAwakenedEditorTrackJson.mutableTiers(track).add(tier);

        assertEquals(1, WorldAwakenedEditorTrackJson.tiers(track).size());
        assertEquals("worldawakened:world/stage/awakening",
                WorldAwakenedEditorTrackJson.tiers(track).get(0).getAsJsonObject().get("stage_id").getAsString());
    }

    @Test
    void malformedTiersProducesAnActionableError() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "worldawakened:world/tracks/test");
        track.addProperty("tiers", "not-an-array");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> WorldAwakenedEditorTrackJson.tiers(track));

        assertTrue(error.getMessage().contains("worldawakened:world/tracks/test"));
        assertTrue(error.getMessage().contains("'tiers' must be an array"));
    }

    @Test
    void orphanedTierMappingsAreRemovedWithoutTouchingAvailableConfigurations() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "worldawakened:world/phase_mutations");
        JsonArray tiers = new JsonArray();
        tiers.add(tier("worldawakened:defaults/overworld_intro"));
        tiers.add(tier("worldawakened:world/phases/stirring/the_end/default_mobs"));
        track.add("tiers", tiers);
        JsonArray tracks = new JsonArray();
        tracks.add(track);

        JsonArray repaired = WorldAwakenedEditorTrackJson.withoutMissingPoolReferences(tracks,
                Set.of("worldawakened:defaults/overworld_intro"));

        JsonArray repairedTiers = WorldAwakenedEditorTrackJson.tiers(repaired.get(0).getAsJsonObject());
        assertEquals(1, repairedTiers.size());
        assertEquals("worldawakened:defaults/overworld_intro",
                repairedTiers.get(0).getAsJsonObject().get("pool_id").getAsString());
        assertEquals(2, WorldAwakenedEditorTrackJson.tiers(track).size(), "repair must not mutate the server snapshot");
    }

    private static JsonObject tier(String poolId) {
        JsonObject tier = new JsonObject();
        tier.addProperty("stage_id", "worldawakened:defaults/stirring");
        tier.addProperty("pool_id", poolId);
        return tier;
    }
}

package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class WorldAwakenedEditorTierDraftsTest {
    @Test
    void addsFirstTierWhenEmptyArrayWasOmittedByCodec() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "test:track");

        WorldAwakenedEditorTierDrafts.appendTier(track, "test:first_stage", "test:first_pool");

        assertEquals(1, WorldAwakenedEditorTrackJson.tiers(track).size());
        assertEquals("test:first_stage", WorldAwakenedEditorTrackJson.tiers(track).get(0).getAsJsonObject()
                .get("stage_id").getAsString());
    }

    @Test
    void copiedPreviousPoolRetainsSettingsButGetsIndependentIdentity() {
        JsonObject previous = new JsonObject();
        previous.addProperty("id", "test:first_pool");
        JsonObject selection = new JsonObject(); selection.addProperty("mutation_chance", 0.21D);
        previous.add("selection", selection);

        JsonObject copy = WorldAwakenedEditorTierDrafts.copyPool(previous, "test:second_pool");
        copy.getAsJsonObject("selection").addProperty("mutation_chance", 0.35D);

        assertEquals("test:second_pool", copy.get("id").getAsString());
        assertEquals(0.21D, previous.getAsJsonObject("selection").get("mutation_chance").getAsDouble());
        assertFalse(previous == copy);
    }

    @Test
    void incompleteTierIsRejectedBeforeItCanBecomeAuthoringState() {
        JsonObject track = new JsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> WorldAwakenedEditorTierDrafts.appendTier(track, "", "test:pool"));
        assertThrows(IllegalArgumentException.class,
                () -> WorldAwakenedEditorTierDrafts.appendTier(track, "test:stage", null));
    }
}

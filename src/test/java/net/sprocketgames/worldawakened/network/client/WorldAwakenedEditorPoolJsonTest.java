package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;

class WorldAwakenedEditorPoolJsonTest {
    @Test
    void readsMutationChanceFromCanonicalFlatCodecShape() {
        JsonObject pool = new JsonObject();
        pool.addProperty("mutation_chance", 0.21D);

        assertEquals(0.21D, WorldAwakenedClientConfigPayloadHandler.poolChance(pool));
    }

    @Test
    void startEmptyTierCreatesCanonicalPoolAcceptedByServerCodec() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "test:track");
        JsonObject pool = WorldAwakenedClientConfigPayloadHandler.defaultPool(
                "test:first_pool", track, "worldawakened:defaults/weathered");

        assertFalse(pool.has("selection"), "MutationPoolDefinition uses a flat selection map codec");
        assertEquals(0.05D, pool.get("mutation_chance").getAsDouble());
        var decoded = MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, pool);
        assertTrue(decoded.result().isPresent(), () -> decoded.error().map(Object::toString).orElse("decode failed"));
    }

    @Test
    void blankSpecialisedTemplateDoesNotCloneMobsOrMutators() {
        JsonObject track = new JsonObject();
        track.addProperty("id", "test:phase_track");

        JsonObject pool = WorldAwakenedClientConfigPayloadHandler.defaultPool("test:new_config", track, null);

        assertTrue(pool.getAsJsonArray("eligible_entities").isEmpty());
        assertTrue(pool.getAsJsonArray("mutators").isEmpty());
    }
}

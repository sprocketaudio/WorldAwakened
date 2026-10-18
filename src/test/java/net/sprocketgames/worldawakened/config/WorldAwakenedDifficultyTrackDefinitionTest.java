package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

class WorldAwakenedDifficultyTrackDefinitionTest {
    @Test
    void missingOptionalCollectionsDecodeToEmptyLists() {
        var result = WorldAwakenedDifficultyTrackDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"test:track","display_name":"Test"}
                """));

        assertTrue(result.result().isPresent());
        assertTrue(result.result().orElseThrow().tiers().isEmpty());
        assertTrue(result.result().orElseThrow().eligibleDimensions().isEmpty());
    }

    @Test
    void malformedOptionalCollectionIsRejectedByTheServerCodec() {
        var result = WorldAwakenedDifficultyTrackDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"id":"test:track","display_name":"Test","tiers":"broken"}
                """));

        assertTrue(result.error().isPresent());
    }

    @Test
    void missingRequiredIdentityIsRejectedByTheServerCodec() {
        var result = WorldAwakenedDifficultyTrackDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"display_name":"Test","tiers":[]}
                """));

        assertTrue(result.error().isPresent());
    }
}

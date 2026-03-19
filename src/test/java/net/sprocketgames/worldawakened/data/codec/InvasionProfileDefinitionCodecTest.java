package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;

class InvasionProfileDefinitionCodecTest {
    @Test
    void decodesMinimalInvasionProfileDefinition() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:night_surge",
                  "display_name": "Night Surge",
                  "trigger_mode": "random_periodic",
                  "duration_seconds": 120
                }
                """);

        var result = InvasionProfileDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Invasion profile should decode");

        InvasionProfileDefinition profile = result.result().orElseThrow();
        assertEquals("testpack:night_surge", profile.id().toString());
        assertEquals("Night Surge", profile.displayName().getAsString());
        assertEquals(120, profile.durationSeconds());
        assertEquals(1, profile.minPlayers());
        assertEquals(1.0D, profile.pressureModifier());
        assertTrue(profile.cooldownSeconds().isEmpty());
        assertTrue(profile.warningSeconds().isEmpty());
    }
}


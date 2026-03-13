package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.AscensionComponentSuppressionPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;

class AscensionRewardDefinitionCodecTest {
    @Test
    void decodesMinimalRewardDefinitionWithDefaults() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:reward_toughness",
                  "display_name": "Stone Skin",
                  "components": [
                    {
                      "type": "worldawakened:armor_bonus",
                      "parameters": {
                        "amount": 2.0
                      }
                    }
                  ]
                }
                """);

        var result = AscensionRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Ascension reward should decode");

        AscensionRewardDefinition reward = result.result().orElseThrow();
        assertEquals("testpack:reward_toughness", reward.id().toString());
        assertEquals(1, reward.components().size());
        assertEquals("worldawakened:armor_bonus", reward.components().getFirst().type().toString());
        assertTrue(!reward.components().getFirst().suppressibleIndividually());
        assertTrue(reward.components().getFirst().suppressionGroup().isEmpty());
        assertEquals(AscensionComponentSuppressionPolicy.REWARD_ONLY, reward.components().getFirst().suppressionPolicy());
        assertEquals(1, reward.maxRank());
        assertTrue(reward.tags().isEmpty());
        assertTrue(reward.offerWeight().isEmpty());
    }

    @Test
    void keepsRewardOnlyPolicyWhenPolicyIsOmittedEvenIfMetadataFlagIsTrue() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:reward_stride",
                  "display_name": "Stride",
                  "components": [
                    {
                      "type": "worldawakened:movement_speed_bonus",
                      "parameters": {
                        "amount": 0.08
                      },
                      "suppressible_individually": true
                    }
                  ]
                }
                """);

        var result = AscensionRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Ascension reward should decode");

        var component = result.result().orElseThrow().components().getFirst();
        assertTrue(component.suppressibleIndividually());
        assertEquals(AscensionComponentSuppressionPolicy.REWARD_ONLY, component.suppressionPolicy());
        assertEquals(AscensionComponentSuppressionPolicy.REWARD_ONLY, component.effectiveSuppressionPolicy());
    }
}


package net.sprocketgames.worldawakened.data.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferMode;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferRepeatPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardRepeatPolicy;

class AscensionOfferDefinitionCodecTest {
    @Test
    void decodesMinimalOfferDefinitionWithDefaults() {
        var json = JsonParser.parseString("""
                {
                  "id": "testpack:offer_stage_1",
                  "display_name": "First Adaptation",
                  "candidate_rewards": ["testpack:reward_toughness"]
                }
                """);

        var result = AscensionOfferDefinition.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isPresent(), "Ascension offer should decode");

        AscensionOfferDefinition offer = result.result().orElseThrow();
        assertEquals("testpack:offer_stage_1", offer.id().toString());
        assertEquals(2, offer.choiceCount());
        assertEquals(1, offer.selectionCount());
        assertEquals(AscensionOfferMode.EXPLICIT_LIST, offer.offerMode());
        assertEquals(AscensionOfferRepeatPolicy.ONCE_PER_PLAYER, offer.offerRepeatPolicy());
        assertEquals(AscensionRewardRepeatPolicy.BLOCK_ALL, offer.rewardRepeatPolicy());
        assertTrue(offer.candidateRewardTags().isEmpty());
    }

    @Test
    void decodesAscensionEffectGenerationPool() {
        var result = AscensionOfferDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "id": "testpack:generated_offer",
                  "display_name": "Generated Adaptation",
                  "choice_count": 3,
                  "effect_generation_pool": "testpack:ascension_effects"
                }
                """));

        assertTrue(result.result().isPresent());
        assertEquals("testpack:ascension_effects", result.result().orElseThrow()
                .effectGenerationPool().orElseThrow().toString());
    }
}


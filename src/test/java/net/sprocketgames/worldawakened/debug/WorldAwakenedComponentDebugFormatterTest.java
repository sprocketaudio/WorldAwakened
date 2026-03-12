package net.sprocketgames.worldawakened.debug;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedComponentDebugFormatterTest {
    @Test
    void formatsMutationWithComponentList() {
        String output = WorldAwakenedComponentDebugFormatter.formatAppliedMutation(
                ResourceLocation.fromNamespaceAndPath("testpack", "summoner_juggernaut"),
                List.of(
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "reinforcement_summon"),
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "max_health_multiplier"),
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "armor_bonus")));

        assertTrue(output.contains("Applied Mutation: testpack:summoner_juggernaut"));
        assertTrue(output.contains("Components: worldawakened:reinforcement_summon, worldawakened:max_health_multiplier, worldawakened:armor_bonus"));
    }

    @Test
    void formatsAscensionRewardWithComponentList() {
        String output = WorldAwakenedComponentDebugFormatter.formatChosenAscensionReward(
                ResourceLocation.fromNamespaceAndPath("testpack", "unyielding_hunter"),
                List.of(
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "hostile_wall_sense"),
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "debuff_resistance"),
                        ResourceLocation.fromNamespaceAndPath("worldawakened", "attack_damage_bonus")));

        assertTrue(output.contains("Chosen Ascension Reward: testpack:unyielding_hunter"));
        assertTrue(output.contains("Components: worldawakened:hostile_wall_sense, worldawakened:debuff_resistance, worldawakened:attack_damage_bonus"));
    }
}

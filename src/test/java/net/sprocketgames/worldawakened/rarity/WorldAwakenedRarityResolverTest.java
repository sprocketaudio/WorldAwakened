package net.sprocketgames.worldawakened.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedRarityResolverTest {
    @Test
    void resolvesTheHighestEnabledThresholdWithoutTyingPowerToSelectionWeight() {
        RarityDefinition common = tier("test:common", 0);
        RarityDefinition rare = tier("test:rare", 5);
        RarityDefinition epic = tier("test:epic", 8);
        Map<ResourceLocation, RarityDefinition> tiers = Map.of(common.id(), common, rare.id(), rare, epic.id(), epic);

        assertEquals(Optional.of(common.id()), WorldAwakenedRarityResolver.resolve(0, tiers));
        assertEquals(Optional.of(rare.id()), WorldAwakenedRarityResolver.resolve(5, tiers));
        assertEquals(Optional.of(rare.id()), WorldAwakenedRarityResolver.resolve(7, tiers));
        assertEquals(Optional.of(epic.id()), WorldAwakenedRarityResolver.resolve(8, tiers));
    }

    private static RarityDefinition tier(String id, int threshold) {
        return new RarityDefinition(1, ResourceLocation.parse(id), true,
                JsonParser.parseString("\"" + id + "\""), threshold);
    }
}

package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldAwakenedClientConfigPayloadHandlerTest {
    @Test
    void filterLabelsKeepTheirCompleteReadableTextForMarqueeRendering() {
        assertEquals("Gathering Storm", WorldAwakenedClientConfigPayloadHandler.filterLabel("gathering_storm"));
        assertEquals("The Nether", WorldAwakenedClientConfigPayloadHandler.filterLabel("minecraft:the_nether"));
    }

    @Test
    void probabilityPreviewUsesConditionalAdditionalChance() {
        assertEquals(
                "Theoretical maximum: none 89.00%  |  1 mutation 10.78%  |  2 mutations 0.22% (before runtime scaling, eligibility, and compatibility)",
                WorldAwakenedClientConfigPayloadHandler.distributionSummary(0.11D, 0.02D, 2));
        assertEquals(
                "Theoretical maximum: none 89.00%  |  1 mutation 11.00% (before runtime scaling, eligibility, and compatibility)",
                WorldAwakenedClientConfigPayloadHandler.distributionSummary(0.11D, 1.0D, 1));
    }

    @Test
    void compactProbabilityPreviewKeepsHighMutationCountsReadable() {
        assertEquals(
                "None: 86.00%    1: 13.30%    2: 0.67%    3: 0.03%    4: <0.01%    5: <0.01%",
                WorldAwakenedClientConfigPayloadHandler.compactDistributionSummary(0.14D, 0.05D, 5));
    }

    @Test
    void combinationModeHelpExplainsBothSelectionModels() {
        String tooltip = WorldAwakenedClientConfigPayloadHandler.combinationModeTooltip(false);
        assertTrue(tooltip.contains("Any Compatible"));
        assertTrue(tooltip.contains("any compatible combination"));
        assertTrue(tooltip.contains("Curated"));
        assertTrue(tooltip.contains("Only combinations you've created"));
        assertTrue(tooltip.contains("Additional mutation chance"));
    }

    @Test
    void mobSecondaryTextKeepsRegistryIdentityVisuallySecondary() {
        assertEquals("minecraft:endermite · Minecraft",
                WorldAwakenedClientConfigPayloadHandler.entitySecondary("minecraft:endermite", "minecraft"));
        assertEquals("examplemod:crystal_beast · Examplemod",
                WorldAwakenedClientConfigPayloadHandler.entitySecondary("examplemod:crystal_beast", "examplemod"));
    }
}

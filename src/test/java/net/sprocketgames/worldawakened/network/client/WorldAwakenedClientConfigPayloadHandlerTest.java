package net.sprocketgames.worldawakened.network.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.sprocketgames.worldawakened.ascension.component.WorldAwakenedAscensionComponentRegistry;

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
    void phasePolicyControlCyclesThroughEveryRuntimePolicy() {
        assertEquals("Cumulative", WorldAwakenedClientConfigPayloadHandler.phasePolicyLabel(0));
        assertEquals("Exclusive", WorldAwakenedClientConfigPayloadHandler.phasePolicyLabel(1));
        assertEquals("Replace", WorldAwakenedClientConfigPayloadHandler.phasePolicyLabel(2));
        assertEquals(1, WorldAwakenedClientConfigPayloadHandler.nextPhasePolicy(0));
        assertEquals(2, WorldAwakenedClientConfigPayloadHandler.nextPhasePolicy(1));
        assertEquals(0, WorldAwakenedClientConfigPayloadHandler.nextPhasePolicy(2));
    }

    @Test
    void mobSecondaryTextKeepsRegistryIdentityVisuallySecondary() {
        assertEquals("minecraft:endermite · Minecraft",
                WorldAwakenedClientConfigPayloadHandler.entitySecondary("minecraft:endermite", "minecraft"));
        assertEquals("examplemod:crystal_beast · Examplemod",
                WorldAwakenedClientConfigPayloadHandler.entitySecondary("examplemod:crystal_beast", "examplemod"));
    }

    @Test
    void ascensionPickerShowsEveryRegisteredEffectButOnlyEnablesLiveHandlers() {
        assertEquals(29, WorldAwakenedClientConfigPayloadHandler.ascensionChoiceEffectCount());
        assertTrue(WorldAwakenedClientConfigPayloadHandler.ascensionChoiceEffectIds().containsAll(
                WorldAwakenedAscensionComponentRegistry.registeredIds().stream()
                        .filter(id -> id.getNamespace().equals("worldawakened"))
                        .map(Object::toString)
                        .collect(Collectors.toSet())));
        assertTrue(WorldAwakenedClientConfigPayloadHandler
                .ascensionChoiceEffectHasLiveRuntimeHandler("worldawakened:max_health_bonus"));
        assertTrue(WorldAwakenedClientConfigPayloadHandler
                .ascensionChoiceEffectHasLiveRuntimeHandler("worldawakened:night_vision_passive"));
        assertFalse(WorldAwakenedClientConfigPayloadHandler
                .ascensionChoiceEffectHasLiveRuntimeHandler("worldawakened:fall_damage_reduction"));
        assertFalse(WorldAwakenedClientConfigPayloadHandler
                .ascensionChoiceEffectHasLiveRuntimeHandler("worldawakened:life_steal_minor"));
    }
}

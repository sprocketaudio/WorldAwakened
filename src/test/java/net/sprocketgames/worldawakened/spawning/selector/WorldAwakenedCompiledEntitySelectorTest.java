package net.sprocketgames.worldawakened.spawning.selector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldAwakenedCompiledEntitySelectorTest {
    @Test
    void appliesBlacklistAfterPositiveMatches() {
        WorldAwakenedEntitySelectorDefinition definition = new WorldAwakenedEntitySelectorDefinition(
                List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie")),
                List.of("minecraft:raiders"),
                List.of(),
                List.of(),
                List.of(ResourceLocation.fromNamespaceAndPath("minecraft", "zombie")),
                List.of());
        WorldAwakenedCompiledEntitySelector selector = WorldAwakenedCompiledEntitySelector.compile(definition);

        WorldAwakenedEntityContextView zombie = new TestEntityContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "zombie"),
                Set.of(),
                "monster");

        assertFalse(selector.matches(zombie));
    }

    @Test
    void matchesNamespaceSelector() {
        WorldAwakenedEntitySelectorDefinition definition = new WorldAwakenedEntitySelectorDefinition(
                List.of(),
                List.of(),
                List.of("minecraft"),
                List.of(),
                List.of(),
                List.of());
        WorldAwakenedCompiledEntitySelector selector = WorldAwakenedCompiledEntitySelector.compile(definition);

        WorldAwakenedEntityContextView skeleton = new TestEntityContext(
                ResourceLocation.fromNamespaceAndPath("minecraft", "skeleton"),
                Set.of(),
                "monster");

        assertTrue(selector.matches(skeleton));
    }

    private record TestEntityContext(ResourceLocation entityId, Set<ResourceLocation> entityTags, String mobCategory)
            implements WorldAwakenedEntityContextView {
    }
}


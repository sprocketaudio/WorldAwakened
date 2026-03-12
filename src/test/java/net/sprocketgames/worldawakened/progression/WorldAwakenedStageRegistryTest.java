package net.sprocketgames.worldawakened.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;

class WorldAwakenedStageRegistryTest {
    @Test
    void resolvesAliasToCanonicalStageId() {
        StageDefinition stage = stage(
                "testpack:baseline",
                List.of("testpack:starter"),
                0,
                Optional.of("mainline"),
                StageUnlockPolicy.CUMULATIVE);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(
                java.util.Map.of(stage.id(), stage));

        ResourceLocation alias = id("testpack:starter");
        ResourceLocation canonical = id("testpack:baseline");
        assertEquals(Optional.of(canonical), registry.resolveCanonicalId(alias));
        assertEquals(Optional.of(canonical), registry.resolveCanonicalId(canonical));
    }

    @Test
    void detectsInactiveUnlockedStages() {
        StageDefinition stage = stage(
                "testpack:baseline",
                List.of(),
                0,
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE);
        WorldAwakenedStageRegistry registry = WorldAwakenedStageRegistry.from(
                java.util.Map.of(stage.id(), stage));

        Set<ResourceLocation> inactive = registry.inactiveStageIds(Set.of(
                id("testpack:baseline"),
                id("testpack:legacy_removed")));
        assertEquals(Set.of(id("testpack:legacy_removed")), inactive);
    }

    private static StageDefinition stage(
            String id,
            List<String> aliases,
            int sortIndex,
            Optional<String> group,
            StageUnlockPolicy policy) {
        List<ResourceLocation> aliasIds = aliases.stream().map(WorldAwakenedStageRegistryTest::id).toList();
        return new StageDefinition(
                1,
                id(id),
                aliasIds,
                JsonParser.parseString("\"" + id + "\""),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                sortIndex,
                true,
                true,
                List.of(),
                Optional.empty(),
                group,
                policy,
                false);
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        assertTrue(parts.length == 2, "Expected namespace:path id");
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}


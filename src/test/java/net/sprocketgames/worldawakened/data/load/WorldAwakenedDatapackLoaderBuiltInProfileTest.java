package net.sprocketgames.worldawakened.data.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;

class WorldAwakenedDatapackLoaderBuiltInProfileTest {
    @Test
    void disabledBuiltInProfileDropsOnlyDefaultsIdFamily() {
        StageDefinition bundled = stage("worldawakened:defaults/dormant");
        StageDefinition frameworkAuthored = stage("worldawakened:custom/server_stage");
        StageDefinition packAuthored = stage("testpack:defaults/dormant");
        Map<ResourceLocation, StageDefinition> definitions = Map.of(
                bundled.id(), bundled,
                frameworkAuthored.id(), frameworkAuthored,
                packAuthored.id(), packAuthored);

        Map<ResourceLocation, StageDefinition> filtered =
                WorldAwakenedDatapackLoader.filterBundledDefaults(definitions, false);

        assertEquals(Map.of(
                frameworkAuthored.id(), frameworkAuthored,
                packAuthored.id(), packAuthored), filtered);
        assertSame(definitions, WorldAwakenedDatapackLoader.filterBundledDefaults(definitions, true));
    }

    private static StageDefinition stage(String value) {
        String[] parts = value.split(":", 2);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        return new StageDefinition(
                1,
                id,
                List.of(),
                JsonParser.parseString("\"" + value + "\""),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                true,
                true,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                StageUnlockPolicy.CUMULATIVE,
                false);
    }
}

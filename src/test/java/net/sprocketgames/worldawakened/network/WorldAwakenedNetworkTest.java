package net.sprocketgames.worldawakened.network;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;

class WorldAwakenedNetworkTest {
    private static final ResourceLocation ID = ResourceLocation.parse("worldawakened:defaults/test");

    @Test
    void editorSnapshotPrefersUncompiledAuthoredThenSourceDefinitions() {
        MutationPoolDefinition runtime = pool(.01D);
        MutationPoolDefinition source = pool(.10D);
        MutationPoolDefinition authored = pool(.25D);

        assertSame(authored, WorldAwakenedNetwork.authoringPoolDefinition(runtime, Map.of(ID, authored), Map.of(ID, source)));
        assertSame(source, WorldAwakenedNetwork.authoringPoolDefinition(runtime, Map.of(), Map.of(ID, source)));
        assertSame(runtime, WorldAwakenedNetwork.authoringPoolDefinition(runtime, Map.of(), Map.of()));
    }

    @Test
    void destructiveResetRestoresEveryBuiltInEditorSetting() {
        WorldAwakenedEditorSettings changed = new WorldAwakenedEditorSettings(
                false, 1.4D, false, .35D, false, 7, false, 2.0D, false);

        assertEquals(WorldAwakenedEditorSettings.builtInDefaults(),
                WorldAwakenedNetwork.profileOperationSettings("reset_defaults", changed));
    }

    @Test
    void selectingStandardPreservesGlobalEditorSettings() {
        WorldAwakenedEditorSettings changed = new WorldAwakenedEditorSettings(
                false, 1.4D, false, .35D, false, 7, false, 2.0D, false);

        assertEquals(new WorldAwakenedEditorSettings(true, 1.4D, false, .35D, false, 7, false, 2.0D, false),
                WorldAwakenedNetwork.profileOperationSettings("standard", changed));
    }

    private static MutationPoolDefinition pool(double chance) {
        JsonObject mutator = new JsonObject();
        mutator.addProperty("id", "worldawakened:defaults/weathered");
        mutator.addProperty("weight", 1);
        return new MutationPoolDefinition(1, ID, true,
                new MutationPoolDefinition.MutationSelection(1, chance, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(ResourceLocation.parse("minecraft:zombie")), List.of(mutator));
    }
}

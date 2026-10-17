package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedProfileOverrideServiceTest {
    private static final ResourceLocation POOL_ID = ResourceLocation.parse("test:pool");
    private static final ResourceLocation MUTATOR_ID = ResourceLocation.parse("test:mutator");

    @AfterEach
    void reset() {
        WorldAwakenedProfileOverrideService.unbind();
    }

    @Test
    void savedOverridesRoundTripThroughNbt() {
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.put(pool(0.25D));
        CompoundTag tag = data.save(new CompoundTag(), null);

        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(tag);

        assertEquals(1L, restored.revision());
        assertEquals(0.25D, restored.mutationPools().get(POOL_ID).mutationChance());
    }

    @Test
    void obsoleteSavedPoolShapeIsDiscardedWithoutReplacingDatapackContent() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("revision", 7L);
        tag.putString("mutation_pools_json", """
                {"test:pool":{"schema_version":1,"id":"test:pool","enabled":true,"weight":1,
                "mutation_chance":0.25,"conditions":[],"eligible_dimensions":[],"eligible_biomes":[],
                "eligible_entities":[],"mutators":[{"id":"test:mutator","weight":1}]}}
                """);

        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(tag);

        assertTrue(restored.mutationPools().isEmpty());
        assertEquals(1, restored.discardedOverrideCount());
        assertEquals(7L, restored.revision());
    }

    @Test
    void unifiedSelectionAndCuratedCombinationRoundTripThroughNbt() {
        ResourceLocation secondId = ResourceLocation.parse("test:second");
        JsonObject first = new JsonObject(); first.addProperty("id", MUTATOR_ID.toString()); first.addProperty("weight", 3);
        JsonObject second = new JsonObject(); second.addProperty("id", secondId.toString()); second.addProperty("weight", 1);
        var combination = new MutationPoolDefinition.CuratedCombination(
                ResourceLocation.parse("test:rare_pair"), 7, List.of(MUTATOR_ID, secondId),
                List.of(ResourceLocation.parse("minecraft:zombie")));
        MutationPoolDefinition unified = new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(10, 0.11D, 0.02D, 2,
                        MutationPoolDefinition.CombinationMode.CURATED, List.of(combination)),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(ResourceLocation.parse("minecraft:zombie")), List.of(first, second));
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.put(unified);

        MutationPoolDefinition restored = WorldAwakenedProfileOverrideSavedData.fromTag(
                data.save(new CompoundTag(), null)).mutationPools().get(POOL_ID);

        assertEquals(0.02D, restored.additionalMutationChance());
        assertEquals(MutationPoolDefinition.CombinationMode.CURATED, restored.combinationMode());
        assertEquals(7, restored.rareCombinations().getFirst().weight());
        assertEquals(List.of(MUTATOR_ID, secondId), restored.rareCombinations().getFirst().mutators());
    }

    @Test
    void applyPublishesOnlyMatchingRevisionAndRevertRestoresSource() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        WorldAwakenedDatapackSnapshot source = snapshot(pool(0.10D));

        var applied = WorldAwakenedProfileOverrideService.apply(level, 0L, pool(0.40D), source);
        var stale = WorldAwakenedProfileOverrideService.apply(level, 0L, pool(0.60D), source);
        WorldAwakenedDatapackSnapshot overlaid = WorldAwakenedProfileOverrideService.overlay(source);
        var reverted = WorldAwakenedProfileOverrideService.revert(level, 1L, POOL_ID, source);

        assertTrue(applied.accepted());
        assertFalse(stale.accepted());
        assertEquals(0.40D, overlaid.data().mutationPools().get(POOL_ID).mutationChance());
        assertTrue(reverted.accepted());
        assertEquals(0.10D, WorldAwakenedProfileOverrideService.overlay(source).data().mutationPools().get(POOL_ID).mutationChance());
    }

    @Test
    void validationRejectsMissingMutatorAndOutOfRangeChance() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.overworld()).thenReturn(level);
        MutationPoolDefinition invalid = new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(1, 1.5D, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()), false, false,
                List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(JsonParser.parseString("{\"id\":\"test:missing\",\"weight\":0}")));

        List<String> errors = WorldAwakenedProfileOverrideService.validate(server, invalid, snapshot(pool(0.1D)));

        assertTrue(errors.stream().anyMatch(error -> error.contains("100%")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("Unknown mutator")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("weights")));
    }

    private static MutationPoolDefinition pool(double chance) {
        JsonObject mutator = new JsonObject();
        mutator.addProperty("id", MUTATOR_ID.toString());
        mutator.addProperty("weight", 10);
        return new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(10, chance, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(mutator));
    }

    private static WorldAwakenedDatapackSnapshot snapshot(MutationPoolDefinition pool) {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(MUTATOR_ID, mock(MobMutatorDefinition.class)), Map.of(POOL_ID, pool), Map.of(), Map.of(), Map.of(),
                Map.of(), WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        return new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
    }
}

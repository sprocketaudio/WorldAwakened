package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedMutatorMutationChanceTest {
    @Test
    void poolCodecDefaultsMutationChanceToOneWhenOmitted() {
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:default_chance_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": ["testpack:chance_mutator"]
                }
                """);

        assertEquals(1.0D, pool.mutationChance(), 0.00001D);
    }

    @Test
    void mutationChanceZeroStopsMutationAfterPoolSelection() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:chance_mutator",
                  "display_name": "Chance Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:max_health_bonus",
                      "parameters": {
                        "amount": 4.0
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:chance_zero_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutation_chance": 0.0,
                  "mutators": [
                    { "id": "testpack:chance_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(1200L);
        Mob zombie = mockMob(EntityType.ZOMBIE);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.selectedPoolId().isPresent(), "expected selected pool before chance gate");
        assertTrue(result.chanceResult().isPresent(), "expected chance_result payload");
        assertFalse(result.chanceResult().get().passed(), "expected mutation chance to fail");
        assertEquals(WorldAwakenedMutatorService.MutationChanceRollMode.SKIPPED, result.chanceResult().get().rollMode());
        assertEquals(WorldAwakenedDiagnosticCodes.MUTATION_CHANCE_FAILED, result.skipCode());
        assertTrue(result.selectedMutatorIds().isEmpty(), "mutator selection should not run after chance failure");
        assertFalse(result.liveApplied(), "chance failure should not apply mutation");
    }

    private static WorldAwakenedMutatorService createService(
            MobMutatorDefinition mutator,
            MutationPoolDefinition pool) {
        WorldAwakenedDatapackSnapshot snapshot = new WorldAwakenedDatapackSnapshot(
                1L,
                Instant.now(),
                new WorldAwakenedCompiledData(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(mutator.id(), mutator),
                        Map.of(pool.id(), pool),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        WorldAwakenedDataDrivenBossClassifier.fromMaps(java.util.List.of())),
                WorldAwakenedValidationSummary.empty());

        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(snapshot);
        when(datapackService.pinSnapshot(eq(snapshot), anyString())).thenReturn(snapshot);

        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        when(stageService.getUnlockedStages(any(ServerLevel.class))).thenReturn(Set.of());
        when(stageService.stageRegistry()).thenReturn(WorldAwakenedStageRegistry.from(Map.of()));

        return new WorldAwakenedMutatorService(datapackService, stageService);
    }

    private static ServerLevel mockLevel(long gameTime) {
        ServerLevel level = mock(ServerLevel.class);
        ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(overworld);
        when(level.getDayTime()).thenReturn(0L);
        when(level.getGameTime()).thenReturn(gameTime);
        @SuppressWarnings("unchecked")
        Holder<Biome> biomeHolder = mock(Holder.class);
        when(biomeHolder.unwrapKey()).thenReturn(Optional.empty());
        when(level.getBiome(any(BlockPos.class))).thenReturn(biomeHolder);
        return level;
    }

    private static Mob mockMob(EntityType<?> entityType) {
        Mob mob = mock(Mob.class);
        CompoundTag persistentData = new CompoundTag();
        @SuppressWarnings("unchecked")
        EntityType<? extends Entity> typedEntity = (EntityType<? extends Entity>) entityType;
        doReturn(typedEntity).when(mob).getType();
        when(mob.blockPosition()).thenReturn(BlockPos.ZERO);
        when(mob.getPersistentData()).thenReturn(persistentData);
        return mob;
    }

    private static MobMutatorDefinition parseMutator(String json) {
        JsonElement element = JsonParser.parseString(json);
        return MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
    }

    private static MutationPoolDefinition parsePool(String json) {
        JsonElement element = JsonParser.parseString(json);
        return MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow();
    }
}

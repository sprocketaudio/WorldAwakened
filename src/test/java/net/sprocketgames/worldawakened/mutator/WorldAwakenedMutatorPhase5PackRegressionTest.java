package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedMutatorPhase5PackRegressionTest {
    private static final Path PHASE5_PACK_ROOT = resolvePhase5PackRoot();

    @Test
    void liveSpawnPathAppliesPhase5SkeletonMutationWhenGatePackIsLoaded() throws Exception {
        StageDefinition phase5Gate = parseStage("stages/phase5_gate.json");
        MobMutatorDefinition skeletonGuard = parseMutator("mob_mutators/skeleton_guard.json");
        MutationPoolDefinition skeletonPool = parsePool("mutation_pools/skeleton_pool.json");

        WorldAwakenedDatapackSnapshot snapshot = new WorldAwakenedDatapackSnapshot(
                1L,
                Instant.now(),
                new WorldAwakenedCompiledData(
                        Map.of(phase5Gate.id(), phase5Gate),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(skeletonGuard.id(), skeletonGuard),
                        Map.of(skeletonPool.id(), skeletonPool),
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
        when(stageService.getUnlockedStages(org.mockito.ArgumentMatchers.any(ServerLevel.class)))
                .thenReturn(Set.of(phase5Gate.id()));
        when(stageService.stageRegistry()).thenReturn(WorldAwakenedStageRegistry.from(Map.of(phase5Gate.id(), phase5Gate)));

        WorldAwakenedMutatorService service = new WorldAwakenedMutatorService(datapackService, stageService);

        ServerLevel level = mock(ServerLevel.class);
        ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(overworld);
        when(level.getDayTime()).thenReturn(0L);
        when(level.getGameTime()).thenReturn(1200L);
        Holder<Biome> biomeHolder = mock(Holder.class);
        when(biomeHolder.unwrapKey()).thenReturn(Optional.empty());
        when(level.getBiome(org.mockito.ArgumentMatchers.any())).thenReturn(biomeHolder);

        Mob skeleton = mock(Mob.class);
        CompoundTag persistentData = new CompoundTag();
        @SuppressWarnings("unchecked")
        EntityType<? extends Entity> skeletonType = (EntityType<? extends Entity>) EntityType.SKELETON;
        doReturn(skeletonType).when(skeleton).getType();
        when(skeleton.blockPosition()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        when(skeleton.getPersistentData()).thenReturn(persistentData);
        AttributeInstance maxHealth = mock(AttributeInstance.class);
        AttributeInstance knockbackResistance = mock(AttributeInstance.class);
        when(skeleton.getAttribute(Attributes.MAX_HEALTH)).thenReturn(maxHealth);
        when(skeleton.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).thenReturn(knockbackResistance);
        when(skeleton.getMaxHealth()).thenReturn(20.0F);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, skeleton, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.selectedPoolId().isPresent(), "expected skeleton pool to be selected");
        assertEquals(ResourceLocation.parse("wa_p5_test:skeleton_pool"), result.selectedPoolId().get());
        assertEquals(List.of(ResourceLocation.parse("wa_p5_test:skeleton_guard")), result.selectedMutatorIds());
        assertTrue(result.liveApplied(), "expected at least one mutation component to apply");
        assertTrue(
                persistentData.contains(WorldAwakenedMutationProvenance.WA_MUTATION_SOURCE_POOL),
                "expected provenance to be written for live-applied mutation");
    }

    private static StageDefinition parseStage(String relativePath) throws Exception {
        JsonElement json = readJson(PHASE5_PACK_ROOT.resolve(relativePath));
        return StageDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static MobMutatorDefinition parseMutator(String relativePath) throws Exception {
        JsonElement json = readJson(PHASE5_PACK_ROOT.resolve(relativePath));
        return MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static MutationPoolDefinition parsePool(String relativePath) throws Exception {
        JsonElement json = readJson(PHASE5_PACK_ROOT.resolve(relativePath));
        return MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static JsonElement readJson(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static Path resolvePhase5PackRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve(Path.of(
                    "dev_datapacks",
                    "worldawakened_phase5_test",
                    "data",
                    "wa_p5_test"));
            if (Files.exists(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not resolve dev_datapacks/worldawakened_phase5_test/data/wa_p5_test from test working directory");
    }
}

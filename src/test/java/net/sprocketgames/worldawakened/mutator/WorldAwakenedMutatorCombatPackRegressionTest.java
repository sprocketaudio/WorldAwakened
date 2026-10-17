package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedMutatorCombatPackRegressionTest {
    private static final Path PACK_ROOT = resolvePackRoot();

    @Test
    void deterministicDevPackAppliesSingleAndCombinedCombatState() throws Exception {
        MobMutatorDefinition vampiric = parseMutator("mob_mutators/vampiric.json");
        MobMutatorDefinition volatileMutator = parseMutator("mob_mutators/volatile.json");
        MobMutatorDefinition plagueTouch = parseMutator("mob_mutators/plague_touch.json");
        MutationPoolDefinition zombiePool = parsePool("mutation_pools/vampiric_zombie.json");
        MutationPoolDefinition skeletonPool = parsePool("mutation_pools/vampiric_volatile_skeleton.json");
        MutationPoolDefinition strayPool = parsePool("mutation_pools/plague_vampiric_stray.json");
        WorldAwakenedMutatorService service = service(
                List.of(vampiric, volatileMutator, plagueTouch),
                List.of(zombiePool, skeletonPool, strayPool));

        CompoundTag zombieState = new CompoundTag();
        Mob zombie = mob(EntityType.ZOMBIE, zombieState);
        WorldAwakenedMutatorService.MutatorRunResult zombieResult = service.onMobSpawn(
                level(), zombie, WorldAwakenedMobSpawnOrigin.OTHER);
        assertTrue(zombieResult.liveApplied());
        assertEquals(0.20D, WorldAwakenedMutationCombatState.lifeStealRatio(zombieState), 0.000001D);
        assertEquals(1, WorldAwakenedVisualParticleEmitters.readEmitters(zombieState).size());
        service.reconcileMutationName(zombie);
        ArgumentCaptor<Component> zombieName = ArgumentCaptor.forClass(Component.class);
        verify(zombie).setCustomName(zombieName.capture());
        assertTrue(zombieName.getValue().getString().startsWith("Vampiric "));
        verify(zombie).setCustomNameVisible(true);

        CompoundTag skeletonState = new CompoundTag();
        Mob skeleton = mob(EntityType.SKELETON, skeletonState);
        WorldAwakenedMutatorService.MutatorRunResult skeletonResult = service.onMobSpawn(
                level(), skeleton, WorldAwakenedMobSpawnOrigin.OTHER);
        assertTrue(skeletonResult.liveApplied());
        assertEquals(2, skeletonResult.selectedMutatorIds().size());
        assertEquals(0.20D, WorldAwakenedMutationCombatState.lifeStealRatio(skeletonState), 0.000001D);
        assertEquals(3.0D, WorldAwakenedMutationCombatState.consumeDeathExplosionPower(skeletonState).orElseThrow());
        assertEquals(2, WorldAwakenedVisualParticleEmitters.readEmitters(skeletonState).size());
        service.reconcileMutationName(skeleton);
        ArgumentCaptor<Component> skeletonName = ArgumentCaptor.forClass(Component.class);
        verify(skeleton).setCustomName(skeletonName.capture());
        assertTrue(skeletonName.getValue().getString().contains("Vampiric"));
        assertTrue(skeletonName.getValue().getString().contains("Volatile"));

        CompoundTag strayState = new CompoundTag();
        WorldAwakenedMutatorService.MutatorRunResult strayResult = service.onMobSpawn(
                level(), mob(EntityType.STRAY, strayState), WorldAwakenedMobSpawnOrigin.OTHER);
        assertEquals(2, strayResult.selectedMutatorIds().size());
        assertEquals(0.20D, WorldAwakenedMutationCombatState.lifeStealRatio(strayState), 0.000001D);
        assertEquals(ResourceLocation.parse("minecraft:poison"),
                WorldAwakenedMutationCombatState.onHitEffects(strayState).getFirst().effectId());
        assertEquals(2, WorldAwakenedVisualParticleEmitters.readEmitters(strayState).size());

        Mob alreadyNamed = mob(EntityType.ZOMBIE, zombieState.copy());
        when(alreadyNamed.hasCustomName()).thenReturn(true);
        service.reconcileMutationName(alreadyNamed);
        verify(alreadyNamed, never()).setCustomName(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unmutatedMobGetsNoCombatStateOrDynamicName() {
        WorldAwakenedMutatorService service = service(List.of(), List.of());
        CompoundTag state = new CompoundTag();
        Mob zombie = mob(EntityType.ZOMBIE, state);

        WorldAwakenedMutatorService.MutatorRunResult result = service.onMobSpawn(
                level(), zombie, WorldAwakenedMobSpawnOrigin.OTHER);
        service.reconcileMutationName(zombie);

        assertTrue(!result.liveApplied());
        assertTrue(!WorldAwakenedMutationCombatState.hasDamageDealtAbility(state));
        assertTrue(WorldAwakenedMutationCombatState.consumeDeathExplosionPower(state).isEmpty());
        verify(zombie, never()).setCustomName(org.mockito.ArgumentMatchers.any());
        verify(zombie, never()).setCustomNameVisible(true);
    }

    private static WorldAwakenedMutatorService service(
            List<MobMutatorDefinition> mutators,
            List<MutationPoolDefinition> pools) {
        Map<ResourceLocation, MobMutatorDefinition> mutatorMap = new LinkedHashMap<>();
        mutators.forEach(value -> mutatorMap.put(value.id(), value));
        Map<ResourceLocation, MutationPoolDefinition> poolMap = new LinkedHashMap<>();
        pools.forEach(value -> poolMap.put(value.id(), value));
        WorldAwakenedDatapackSnapshot snapshot = new WorldAwakenedDatapackSnapshot(
                1L,
                Instant.now(),
                new WorldAwakenedCompiledData(
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), mutatorMap, poolMap,
                        Map.of(), Map.of(), Map.of(), Map.of(),
                        WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of())),
                WorldAwakenedValidationSummary.empty());
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(snapshot);
        when(datapackService.pinSnapshot(eq(snapshot), anyString())).thenReturn(snapshot);
        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        when(stageService.getUnlockedStages(org.mockito.ArgumentMatchers.any(ServerLevel.class))).thenReturn(Set.of());
        when(stageService.stageRegistry()).thenReturn(WorldAwakenedStageRegistry.from(Map.of()));
        return new WorldAwakenedMutatorService(datapackService, stageService);
    }

    private static ServerLevel level() {
        ServerLevel level = mock(ServerLevel.class);
        ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(overworld);
        when(level.getDayTime()).thenReturn(0L);
        when(level.getGameTime()).thenReturn(1200L);
        @SuppressWarnings("unchecked")
        Holder<Biome> biome = mock(Holder.class);
        when(biome.unwrapKey()).thenReturn(Optional.empty());
        when(level.getBiome(org.mockito.ArgumentMatchers.any())).thenReturn(biome);
        return level;
    }

    private static Mob mob(EntityType<?> type, CompoundTag persistentData) {
        Mob mob = mock(Mob.class);
        @SuppressWarnings("unchecked")
        EntityType<? extends Entity> castType = (EntityType<? extends Entity>) type;
        doReturn(castType).when(mob).getType();
        when(mob.blockPosition()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        when(mob.getPersistentData()).thenReturn(persistentData);
        return mob;
    }

    private static MobMutatorDefinition parseMutator(String relativePath) throws Exception {
        return MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, readJson(PACK_ROOT.resolve(relativePath))).getOrThrow();
    }

    private static MutationPoolDefinition parsePool(String relativePath) throws Exception {
        return MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, readJson(PACK_ROOT.resolve(relativePath))).getOrThrow();
    }

    private static JsonElement readJson(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        }
    }

    private static Path resolvePackRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 6 && cursor != null; depth++) {
            Path candidate = cursor.resolve(Path.of(
                    "dev_datapacks", "worldawakened_combat_test", "data", "combat_test"));
            if (Files.exists(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Could not resolve the combat test datapack from the test working directory");
    }
}

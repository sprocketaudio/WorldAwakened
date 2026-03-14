package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

class WorldAwakenedMutatorVisualComponentTest {
    @Test
    void appliesMovementSpeedMultiplierComponent() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:speed_mutator",
                  "display_name": "Speed Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:movement_speed_multiplier",
                      "parameters": {
                        "multiplier": 1.25
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:speed_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:speed_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(1200L);
        Mob zombie = mockMob(EntityType.ZOMBIE);
        AttributeInstance movementSpeed = mock(AttributeInstance.class);
        when(zombie.getAttribute(Attributes.MOVEMENT_SPEED)).thenReturn(movementSpeed);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected movement_speed_multiplier to apply");
        assertEquals(
                List.of(ResourceLocation.parse("worldawakened:movement_speed_multiplier")),
                result.appliedMutations().getFirst().appliedComponentTypes());

        ArgumentCaptor<AttributeModifier> modifierCaptor = ArgumentCaptor.forClass(AttributeModifier.class);
        verify(movementSpeed).addPermanentModifier(modifierCaptor.capture());
        AttributeModifier modifier = modifierCaptor.getValue();
        assertEquals(0.25D, modifier.amount(), 0.0001D);
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, modifier.operation());
    }

    @Test
    void storesGlowStyleStateForInspection() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:glow_mutator",
                  "display_name": "Glow Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:glow_style",
                      "parameters": {
                        "color": "#66ff66",
                        "brightness": 0.85,
                        "see_through_walls": false,
                        "pulse": true,
                        "pulse_speed": 1.25,
                        "pulse_strength": 0.2
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:glow_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:glow_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(30L);
        Mob zombie = mockMob(EntityType.ZOMBIE);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected glow_style to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertTrue(inspect.glowStyle().isPresent(), "expected glow_style inspection state");
        WorldAwakenedGlowStyleState.GlowStyleDefinition glowStyle = inspect.glowStyle().get();
        assertEquals(0x66FF66, glowStyle.colorRgb());
        assertEquals(0.85F, glowStyle.brightness(), 0.0001F);
        assertFalse(glowStyle.seeThroughWalls());
        assertTrue(glowStyle.pulse());
        assertEquals(1.25F, glowStyle.pulseSpeed(), 0.0001F);
        assertEquals(0.2F, glowStyle.pulseStrength(), 0.0001F);
    }

    @Test
    void clampsGlowStyleBrightnessAboveOneToOne() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:clamped_glow_mutator",
                  "display_name": "Clamped Glow Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:glow_style",
                      "parameters": {
                        "color": "#66ff66",
                        "brightness": 4.0,
                        "see_through_walls": false,
                        "pulse": false
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:clamped_glow_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:clamped_glow_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(30L);
        Mob zombie = mockMob(EntityType.ZOMBIE);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected glow_style to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertTrue(inspect.glowStyle().isPresent(), "expected glow_style inspection state");
        assertEquals(1.0F, inspect.glowStyle().get().brightness(), 0.0001F);
    }

    @Test
    void storesAndEmitsEffectParticlesUsingVanillaEffectVisuals() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:visual_mutator",
                  "display_name": "Visual Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:effect_particles",
                      "parameters": {
                        "effect_type": "minecraft:strength",
                        "count": 3,
                        "interval_ticks": 5
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:visual_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:visual_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(15L);
        Mob zombie = mockMob(EntityType.ZOMBIE);
        when(zombie.getId()).thenReturn(0);
        when(zombie.getX()).thenReturn(1.0D);
        when(zombie.getY()).thenReturn(2.0D);
        when(zombie.getZ()).thenReturn(3.0D);
        when(zombie.getBbHeight()).thenReturn(1.8F);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected effect_particles to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertEquals(1, inspect.particleVisualEmitters().size());
        WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter = inspect.particleVisualEmitters().getFirst();
        assertEquals(WorldAwakenedVisualParticleEmitters.EmitterKind.EFFECT_VISUAL, emitter.kind());
        assertEquals(ResourceLocation.parse("minecraft:strength"), emitter.registryId());
        assertEquals(3, emitter.count());
        assertEquals(5, emitter.intervalTicks());
        assertTrue(emitter.colorOverrideRgb().isEmpty(), "expected no color override by default");

        service.onMobTick(level, zombie);
        double expectedParticleY = 2.0D + (1.8F * 0.5D);

        ArgumentCaptor<net.minecraft.core.particles.ParticleOptions> particleCaptor =
                ArgumentCaptor.forClass(net.minecraft.core.particles.ParticleOptions.class);
        verify(level).sendParticles(
                particleCaptor.capture(),
                eq(1.0D),
                eq(expectedParticleY),
                eq(3.0D),
                eq(3),
                eq(0.25D),
                eq(0.40D),
                eq(0.25D),
                eq(0.01D));
        assertEquals(
                ResourceLocation.parse("minecraft:entity_effect"),
                BuiltInRegistries.PARTICLE_TYPE.getKey(particleCaptor.getValue().getType()));
        assertTrue(
                particleCaptor.getValue() instanceof ColorParticleOption,
                "expected effect visual particles to resolve to ColorParticleOption");
        ColorParticleOption colorParticle = (ColorParticleOption) particleCaptor.getValue();
        assertEquals(1.0D, colorParticle.getAlpha(), 0.001D, "expected non-ambient effect alpha");
    }

    @Test
    void storesAndEmitsEffectParticlesWithColorOverride() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:visual_color_mutator",
                  "display_name": "Visual Color Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:effect_particles",
                      "parameters": {
                        "effect_type": "minecraft:strength",
                        "color": "#66ff66",
                        "count": 3,
                        "interval_ticks": 5
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:visual_color_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:visual_color_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(15L);
        Mob zombie = mockMob(EntityType.ZOMBIE);
        when(zombie.getId()).thenReturn(0);
        when(zombie.getX()).thenReturn(1.0D);
        when(zombie.getY()).thenReturn(2.0D);
        when(zombie.getZ()).thenReturn(3.0D);
        when(zombie.getBbHeight()).thenReturn(1.8F);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected effect_particles to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertEquals(1, inspect.particleVisualEmitters().size());
        WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter = inspect.particleVisualEmitters().getFirst();
        assertEquals(Optional.of(0x66FF66), emitter.colorOverrideRgb());

        service.onMobTick(level, zombie);
        ArgumentCaptor<net.minecraft.core.particles.ParticleOptions> particleCaptor =
                ArgumentCaptor.forClass(net.minecraft.core.particles.ParticleOptions.class);
        verify(level).sendParticles(
                particleCaptor.capture(),
                eq(1.0D),
                eq(2.0D + (1.8F * 0.5D)),
                eq(3.0D),
                eq(3),
                eq(0.25D),
                eq(0.40D),
                eq(0.25D),
                eq(0.01D));
        assertTrue(particleCaptor.getValue() instanceof ColorParticleOption, "expected ColorParticleOption");
        ColorParticleOption colorParticle = (ColorParticleOption) particleCaptor.getValue();
        assertEquals(0.4D, colorParticle.getRed(), 0.01D);
        assertEquals(1.0D, colorParticle.getGreen(), 0.01D);
        assertEquals(0.4D, colorParticle.getBlue(), 0.01D);
        assertEquals(1.0D, colorParticle.getAlpha(), 0.001D);
    }

    @Test
    void scalesEffectParticleOffsetsForLargeMobs() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:slime_visual_mutator",
                  "display_name": "Slime Visual Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:slime"],
                  "components": [
                    {
                      "type": "worldawakened:effect_particles",
                      "parameters": {
                        "effect_type": "minecraft:water_breathing",
                        "count": 3,
                        "interval_ticks": 5
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:slime_visual_pool",
                  "eligible_entities": ["minecraft:slime"],
                  "mutators": [
                    { "id": "testpack:slime_visual_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(15L);
        Mob slime = mockMob(EntityType.SLIME);
        when(slime.getId()).thenReturn(0);
        when(slime.getX()).thenReturn(1.0D);
        when(slime.getY()).thenReturn(2.0D);
        when(slime.getZ()).thenReturn(3.0D);
        float bbWidth = 2.04F;
        float bbHeight = 2.04F;
        when(slime.getBbWidth()).thenReturn(bbWidth);
        when(slime.getBbHeight()).thenReturn(bbHeight);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, slime, WorldAwakenedMobSpawnOrigin.OTHER);
        assertTrue(result.liveApplied(), "expected effect_particles to apply");

        service.onMobTick(level, slime);
        double expectedParticleY = 2.0D + (bbHeight * 0.5D);
        double expectedOffsetX = Math.min(4.0D, Math.max(0.25D, Math.max(0.0D, bbWidth * 0.5D) * 0.9D));
        double expectedOffsetY = 0.40D;
        double expectedOffsetZ = expectedOffsetX;

        verify(level).sendParticles(
                any(net.minecraft.core.particles.ParticleOptions.class),
                eq(1.0D),
                eq(expectedParticleY),
                eq(3.0D),
                eq(3),
                eq(expectedOffsetX),
                eq(expectedOffsetY),
                eq(expectedOffsetZ),
                eq(0.01D));
    }

    @Test
    void storesAndEmitsAmbientParticlesUsingSimpleParticleType() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:flame_mutator",
                  "display_name": "Flame Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:ambient_particles",
                      "parameters": {
                        "particle": "minecraft:flame",
                        "count": 2,
                        "offset_x": 0.2,
                        "offset_y": 0.4,
                        "offset_z": 0.2,
                        "speed": 0.01,
                        "interval_ticks": 8
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:flame_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:flame_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(24L);
        Mob zombie = mockMob(EntityType.ZOMBIE);
        when(zombie.getId()).thenReturn(0);
        when(zombie.getX()).thenReturn(4.0D);
        when(zombie.getY()).thenReturn(5.0D);
        when(zombie.getZ()).thenReturn(6.0D);
        when(zombie.getBbHeight()).thenReturn(1.8F);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected ambient_particles to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertEquals(1, inspect.particleVisualEmitters().size());
        WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter = inspect.particleVisualEmitters().getFirst();
        assertEquals(WorldAwakenedVisualParticleEmitters.EmitterKind.PARTICLE, emitter.kind());
        assertEquals(ResourceLocation.parse("minecraft:flame"), emitter.registryId());
        assertEquals(2, emitter.count());
        assertEquals(8, emitter.intervalTicks());
        assertTrue(emitter.colorOverrideRgb().isEmpty());
        assertTrue(emitter.sizeOverride().isEmpty());

        service.onMobTick(level, zombie);
        double expectedParticleY = 5.0D + (1.8F * 0.5D);

        ArgumentCaptor<net.minecraft.core.particles.ParticleOptions> particleCaptor =
                ArgumentCaptor.forClass(net.minecraft.core.particles.ParticleOptions.class);
        verify(level).sendParticles(
                particleCaptor.capture(),
                eq(4.0D),
                eq(expectedParticleY),
                eq(6.0D),
                eq(2),
                eq(0.2D),
                eq(0.4D),
                eq(0.2D),
                eq(0.01D));
        assertEquals(
                ResourceLocation.parse("minecraft:flame"),
                BuiltInRegistries.PARTICLE_TYPE.getKey(particleCaptor.getValue().getType()));
    }

    @Test
    void storesAndEmitsAmbientDustParticlesWithColorAndSize() {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:dust_mutator",
                  "display_name": "Dust Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:ambient_particles",
                      "parameters": {
                        "particle": "minecraft:dust",
                        "color": "#33ff66",
                        "size": 0.8,
                        "count": 4,
                        "offset_x": 0.3,
                        "offset_y": 0.6,
                        "offset_z": 0.3,
                        "speed": 0.01,
                        "interval_ticks": 8
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:dust_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:dust_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel(24L);
        Mob zombie = mockMob(EntityType.ZOMBIE);
        when(zombie.getId()).thenReturn(0);
        when(zombie.getX()).thenReturn(4.0D);
        when(zombie.getY()).thenReturn(5.0D);
        when(zombie.getZ()).thenReturn(6.0D);
        when(zombie.getBbHeight()).thenReturn(1.8F);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected ambient_particles to apply");
        WorldAwakenedMutatorService.MutationInspectView inspect = service.inspectEntity(zombie);
        assertEquals(1, inspect.particleVisualEmitters().size());
        WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter = inspect.particleVisualEmitters().getFirst();
        assertEquals(WorldAwakenedVisualParticleEmitters.EmitterKind.PARTICLE, emitter.kind());
        assertEquals(ResourceLocation.parse("minecraft:dust"), emitter.registryId());
        assertEquals(Optional.of(0x33FF66), emitter.colorOverrideRgb());
        assertEquals(Optional.of(0.8F), emitter.sizeOverride());

        service.onMobTick(level, zombie);
        double expectedParticleY = 5.0D + (1.8F * 0.5D);

        ArgumentCaptor<net.minecraft.core.particles.ParticleOptions> particleCaptor =
                ArgumentCaptor.forClass(net.minecraft.core.particles.ParticleOptions.class);
        verify(level).sendParticles(
                particleCaptor.capture(),
                eq(4.0D),
                eq(expectedParticleY),
                eq(6.0D),
                eq(4),
                eq(0.3D),
                eq(0.6D),
                eq(0.3D),
                eq(0.01D));
        assertTrue(particleCaptor.getValue() instanceof DustParticleOptions, "expected DustParticleOptions");
        DustParticleOptions dust = (DustParticleOptions) particleCaptor.getValue();
        assertEquals(0.2D, dust.getColor().x, 0.01D);
        assertEquals(1.0D, dust.getColor().y, 0.01D);
        assertEquals(0.4D, dust.getColor().z, 0.01D);
        assertEquals(0.8D, dust.getScale(), 0.001D);
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
                        WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of())),
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

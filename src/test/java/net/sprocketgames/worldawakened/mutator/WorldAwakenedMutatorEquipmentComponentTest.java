package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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

class WorldAwakenedMutatorEquipmentComponentTest {
    @Test
    void appliesEquipItemComponentWithEnchantmentsAndDropChance() throws Exception {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:equip_mutator",
                  "display_name": "Equip Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:equip_item",
                      "parameters": {
                        "item": "minecraft:iron_sword",
                        "enchantments": [
                          { "id": "minecraft:sharpness", "level": 3 }
                        ],
                        "drop_chance": 0.35
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:equip_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:equip_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel();
        Mob zombie = mockMob(EntityType.ZOMBIE);
        when(zombie.canUseSlot(EquipmentSlot.MAINHAND)).thenReturn(true);
        when(zombie.canHoldItem(any(ItemStack.class))).thenReturn(true);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected equip_item to apply");
        assertEquals(List.of(ResourceLocation.parse("testpack:equip_mutator")), result.selectedMutatorIds());
        assertTrue(result.componentFailures().isEmpty(), () -> "Expected no component failures but got " + result.componentFailures());

        ArgumentCaptor<ItemStack> stackCaptor = ArgumentCaptor.forClass(ItemStack.class);
        verify(zombie).setItemSlot(eq(EquipmentSlot.MAINHAND), stackCaptor.capture());
        verify(zombie).setDropChance(EquipmentSlot.MAINHAND, 0.35F);

        ItemStack equipped = stackCaptor.getValue();
        assertEquals(Items.IRON_SWORD, equipped.getItem());
        Holder<Enchantment> sharpness = enchantmentRegistry().getOrThrow(
                ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:sharpness")));
        assertEquals(3, EnchantmentHelper.getTagEnchantmentLevel(sharpness, equipped));
    }

    @Test
    void skipsUnsupportedEquipItemButKeepsOtherComponents() throws Exception {
        MobMutatorDefinition mutator = parseMutator("""
                {
                  "id": "testpack:hybrid_mutator",
                  "display_name": "Hybrid Mutator",
                  "weight": 1,
                  "eligible_entities": ["minecraft:zombie"],
                  "components": [
                    {
                      "type": "worldawakened:max_health_bonus",
                      "parameters": {
                        "amount": 4.0
                      }
                    },
                    {
                      "type": "worldawakened:equip_item",
                      "parameters": {
                        "item": "minecraft:shield",
                        "slot": "offhand"
                      }
                    }
                  ]
                }
                """);
        MutationPoolDefinition pool = parsePool("""
                {
                  "id": "testpack:hybrid_pool",
                  "eligible_entities": ["minecraft:zombie"],
                  "mutators": [
                    { "id": "testpack:hybrid_mutator", "weight": 1 }
                  ]
                }
                """);

        WorldAwakenedMutatorService service = createService(mutator, pool);
        ServerLevel level = mockLevel();
        Mob zombie = mockMob(EntityType.ZOMBIE);
        AttributeInstance maxHealth = mock(AttributeInstance.class);
        when(zombie.getAttribute(Attributes.MAX_HEALTH)).thenReturn(maxHealth);
        when(zombie.getMaxHealth()).thenReturn(24.0F);
        when(zombie.canUseSlot(EquipmentSlot.OFFHAND)).thenReturn(false);

        WorldAwakenedMutatorService.MutatorRunResult result =
                service.onMobSpawn(level, zombie, WorldAwakenedMobSpawnOrigin.OTHER);

        assertTrue(result.liveApplied(), "expected max_health_bonus to keep the mutation live");
        assertEquals(List.of(ResourceLocation.parse("testpack:hybrid_mutator")), result.selectedMutatorIds());
        assertEquals(1, result.appliedMutations().size());
        assertEquals(
                List.of(ResourceLocation.parse("worldawakened:max_health_bonus")),
                result.appliedMutations().getFirst().appliedComponentTypes());
        assertTrue(
                result.componentFailures().stream().anyMatch(failure ->
                        failure.componentType().equals(ResourceLocation.parse("worldawakened:equip_item"))
                                && failure.code().equals(WorldAwakenedDiagnosticCodes.MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE)
                                && failure.detail().contains("offhand")),
                () -> "Expected failed-closed equip_item component but got " + result.componentFailures());
        verify(zombie, never()).setItemSlot(eq(EquipmentSlot.OFFHAND), any(ItemStack.class));
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

    private static ServerLevel mockLevel() {
        ServerLevel level = mock(ServerLevel.class);
        ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
        when(level.dimension()).thenReturn(overworld);
        when(level.getDayTime()).thenReturn(0L);
        when(level.getGameTime()).thenReturn(1200L);
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
        RegistryAccess registryAccess = mock(RegistryAccess.class);
        HolderLookup.Provider provider = VanillaRegistries.createLookup();
        when(registryAccess.lookup(eq(Registries.ENCHANTMENT))).thenReturn(provider.lookup(Registries.ENCHANTMENT));
        when(mob.registryAccess()).thenReturn(registryAccess);
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

    private static HolderLookup.RegistryLookup<Enchantment> enchantmentRegistry() {
        return VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT);
    }
}

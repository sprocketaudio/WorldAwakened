package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.google.gson.JsonObject;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutatorService;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutationCombatState;

class WorldAwakenedCommandsMobInspectCommandTest {
    @Test
    void buildMobTreeSupportsAimedAndExplicitInspectForms() {
        WorldAwakenedMutatorService mutatorService = mock(WorldAwakenedMutatorService.class);

        LiteralArgumentBuilder<CommandSourceStack> mobTree = invokeBuildMobTree(mutatorService);
        CommandNode<CommandSourceStack> inspectNode = mobTree.build().getChild("inspect");

        assertNotNull(inspectNode, "Expected /wa mob inspect literal");
        assertNotNull(inspectNode.getCommand(), "Expected no-arg /wa mob inspect aimed-at form");
        assertNotNull(inspectNode.getChild("target"), "Expected explicit /wa mob inspect <target> form");
    }

    @Test
    void aimedInspectFailsWithoutPlayerSource() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        WorldAwakenedMutatorService mutatorService = mock(WorldAwakenedMutatorService.class);

        int result = invokeRunMobInspectLookedAt(source, mutatorService, null);

        assertTrue(result == 0, "Expected aimed inspect to fail without a player source");
        verify(source).sendFailure(any());
        verifyNoInteractions(mutatorService);
    }

    @Test
    void explicitInspectExplainsWhenSelectorResolvedThePlayer() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        WorldAwakenedMutatorService mutatorService = mock(WorldAwakenedMutatorService.class);
        Entity playerTarget = mock(Entity.class);
        doReturn(EntityType.PLAYER).when(playerTarget).getType();

        int result = invokeRunMobInspect(source, mutatorService, playerTarget);

        assertTrue(result == 0, "Expected a player target to be rejected");
        verify(source).sendFailure(argThat(message -> message.getString().contains("minecraft:player")));
        verifyNoInteractions(mutatorService);
    }

    @Test
    void inspectVitalsExposeCurrentMaximumAndAbsorptionHealth() {
        Mob mob = mock(Mob.class);
        doReturn(7.5F).when(mob).getHealth();
        doReturn(20.0F).when(mob).getMaxHealth();
        doReturn(2.0F).when(mob).getAbsorptionAmount();

        assertTrue(WorldAwakenedCommands.formatMobVitals(mob)
                .equals("health=7.500/20.000 absorption=2.000"));
    }

    @Test
    void inspectCombatStateExposesResolvedAbilityValues() {
        Mob mob = mock(Mob.class);
        CompoundTag state = new CompoundTag();
        JsonObject effect = new JsonObject();
        effect.addProperty("effect", "minecraft:slowness");
        effect.addProperty("duration_ticks", 60);
        effect.addProperty("amplifier", 0);
        effect.addProperty("chance", 0.35D);
        JsonObject lifeSteal = new JsonObject();
        lifeSteal.addProperty("amount", 0.2D);
        JsonObject explosion = new JsonObject();
        explosion.addProperty("amount", 3.0D);
        WorldAwakenedMutationCombatState.appendOnHitEffect(state, effect);
        WorldAwakenedMutationCombatState.appendLifeSteal(state, lifeSteal);
        WorldAwakenedMutationCombatState.appendDeathExplosion(state, explosion);
        doReturn(state).when(mob).getPersistentData();

        assertTrue(WorldAwakenedCommands.formatMobCombatState(mob).contains(
                "on_hit_effect=minecraft:slowness duration_ticks=60 amplifier=0 chance=0.350"));
        assertTrue(WorldAwakenedCommands.formatMobCombatState(mob).contains("life_steal_ratio=0.200"));
        assertTrue(WorldAwakenedCommands.formatMobCombatState(mob).contains(
                "death_explosion_power=3.000 triggered=false"));
    }

    @Test
    void inspectSummarizesEquipmentAndActiveEffects() {
        Mob mob = mock(Mob.class);
        doReturn(new ItemStack(Items.IRON_SWORD)).when(mob).getItemBySlot(EquipmentSlot.MAINHAND);
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.BODY)) {
            doReturn(ItemStack.EMPTY).when(mob).getItemBySlot(slot);
        }
        doReturn(List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)))
                .when(mob).getActiveEffects();

        assertTrue(WorldAwakenedCommands.formatMobEquipment(mob).getFirst().contains("minecraft:iron_sword"));
        assertTrue(WorldAwakenedCommands.formatMobActiveEffects(mob).getFirst()
                .equals("minecraft:slowness amplifier=1 remaining_ticks=60"));
    }

    @SuppressWarnings("unchecked")
    private static LiteralArgumentBuilder<CommandSourceStack> invokeBuildMobTree(
            WorldAwakenedMutatorService mutatorService) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    "buildMobTree",
                    WorldAwakenedMutatorService.class);
            method.setAccessible(true);
            return (LiteralArgumentBuilder<CommandSourceStack>) method.invoke(null, mutatorService);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke buildMobTree", exception);
        }
    }

    private static int invokeRunMobInspectLookedAt(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            ServerPlayer player) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    "runMobInspectLookedAt",
                    CommandSourceStack.class,
                    WorldAwakenedMutatorService.class,
                    ServerPlayer.class);
            method.setAccessible(true);
            return (int) method.invoke(null, source, mutatorService, player);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke runMobInspectLookedAt", exception);
        }
    }

    private static int invokeRunMobInspect(
            CommandSourceStack source,
            WorldAwakenedMutatorService mutatorService,
            Entity target) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    "runMobInspect",
                    CommandSourceStack.class,
                    WorldAwakenedMutatorService.class,
                    Entity.class);
            method.setAccessible(true);
            return (int) method.invoke(null, source, mutatorService, target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke runMobInspect", exception);
        }
    }
}

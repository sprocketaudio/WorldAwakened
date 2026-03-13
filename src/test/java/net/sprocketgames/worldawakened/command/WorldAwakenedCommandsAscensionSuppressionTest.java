package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mojang.authlib.GameProfile;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;

class WorldAwakenedCommandsAscensionSuppressionTest {
    @Test
    void suppressRewardCommandDelegatesToServiceAndReturnsSuccess() {
        TestContext context = context();
        ResourceLocation rewardId = id("wa_test:grave_step");
        when(context.ascensionService().suppressReward(context.level(), context.player(), rewardId))
                .thenReturn(new WorldAwakenedAscensionService.SuppressionMutationResult(
                        true,
                        "suppression_applied",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED,
                        rewardId,
                        Set.of()));

        int result = invokeRewardHandler("runAscensionSuppressReward", context, rewardId);

        assertEquals(1, result);
        verify(context.ascensionService()).suppressReward(context.level(), context.player(), rewardId);
        verify(context.source(), atLeastOnce()).sendSuccess(any(), eq(true));
    }

    @Test
    void unsuppressRewardCommandDelegatesToServiceAndReturnsFailure() {
        TestContext context = context();
        ResourceLocation rewardId = id("wa_test:grave_step");
        when(context.ascensionService().unsuppressReward(context.level(), context.player(), rewardId))
                .thenReturn(new WorldAwakenedAscensionService.SuppressionMutationResult(
                        false,
                        "reward_not_suppressed",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                        rewardId,
                        Set.of()));

        int result = invokeRewardHandler("runAscensionUnsuppressReward", context, rewardId);

        assertEquals(0, result);
        verify(context.ascensionService()).unsuppressReward(context.level(), context.player(), rewardId);
        verify(context.source()).sendFailure(any());
    }

    @Test
    void suppressComponentCommandDelegatesToServiceAndReturnsSuccess() {
        TestContext context = context();
        ResourceLocation rewardId = id("wa_test:grave_step");
        String componentKey = "0|worldawakened:movement_speed_bonus";
        when(context.ascensionService().suppressComponent(context.level(), context.player(), rewardId, componentKey))
                .thenReturn(new WorldAwakenedAscensionService.SuppressionMutationResult(
                        true,
                        "suppression_applied",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_APPLIED,
                        rewardId,
                        Set.of(componentKey)));

        int result = invokeComponentHandler("runAscensionSuppressComponent", context, rewardId, componentKey);

        assertEquals(1, result);
        verify(context.ascensionService()).suppressComponent(context.level(), context.player(), rewardId, componentKey);
        verify(context.source(), atLeastOnce()).sendSuccess(any(), eq(true));
    }

    @Test
    void unsuppressComponentCommandDelegatesToServiceAndReturnsFailure() {
        TestContext context = context();
        ResourceLocation rewardId = id("wa_test:grave_step");
        String componentKey = "0|worldawakened:movement_speed_bonus";
        when(context.ascensionService().unsuppressComponent(context.level(), context.player(), rewardId, componentKey))
                .thenReturn(new WorldAwakenedAscensionService.SuppressionMutationResult(
                        false,
                        "component_not_suppressed",
                        WorldAwakenedDiagnosticCodes.ASC_SUPPRESSION_REMOVED,
                        rewardId,
                        Set.of(componentKey)));

        int result = invokeComponentHandler("runAscensionUnsuppressComponent", context, rewardId, componentKey);

        assertEquals(0, result);
        verify(context.ascensionService()).unsuppressComponent(context.level(), context.player(), rewardId, componentKey);
        verify(context.source()).sendFailure(any());
    }

    private static int invokeRewardHandler(String methodName, TestContext context, ResourceLocation rewardId) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    methodName,
                    CommandSourceStack.class,
                    WorldAwakenedDatapackService.class,
                    WorldAwakenedAscensionService.class,
                    ServerPlayer.class,
                    ResourceLocation.class);
            method.setAccessible(true);
            return (int) method.invoke(
                    null,
                    context.source(),
                    context.datapackService(),
                    context.ascensionService(),
                    context.player(),
                    rewardId);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke command handler " + methodName, exception);
        }
    }

    private static int invokeComponentHandler(
            String methodName,
            TestContext context,
            ResourceLocation rewardId,
            String componentKey) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    methodName,
                    CommandSourceStack.class,
                    WorldAwakenedDatapackService.class,
                    WorldAwakenedAscensionService.class,
                    ServerPlayer.class,
                    ResourceLocation.class,
                    String.class);
            method.setAccessible(true);
            return (int) method.invoke(
                    null,
                    context.source(),
                    context.datapackService(),
                    context.ascensionService(),
                    context.player(),
                    rewardId,
                    componentKey);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke command handler " + methodName, exception);
        }
    }

    private static TestContext context() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.serverLevel()).thenReturn(level);
        when(player.getGameProfile()).thenReturn(new GameProfile(UUID.fromString("12345678-1234-1234-1234-123456789012"), "CmdTest"));

        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(WorldAwakenedDatapackSnapshot.empty());

        WorldAwakenedAscensionService ascensionService = mock(WorldAwakenedAscensionService.class);
        return new TestContext(source, datapackService, ascensionService, player, level);
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }

    private record TestContext(
            CommandSourceStack source,
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedAscensionService ascensionService,
            ServerPlayer player,
            ServerLevel level) {
    }
}

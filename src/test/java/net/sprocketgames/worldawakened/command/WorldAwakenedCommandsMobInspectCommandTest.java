package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutatorService;

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
}

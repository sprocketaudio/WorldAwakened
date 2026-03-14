package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDebugCommandService;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedMutatorService;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;

class WorldAwakenedCommandsDebugTreeStructureTest {
    @Test
    void buildDebugTreeIncludesMutatorAndSpawnTopLevelBranches() {
        WorldAwakenedDatapackService datapackService = mock(WorldAwakenedDatapackService.class);
        when(datapackService.currentSnapshot()).thenReturn(WorldAwakenedDatapackSnapshot.empty());

        WorldAwakenedStageService stageService = mock(WorldAwakenedStageService.class);
        WorldAwakenedAscensionService ascensionService = mock(WorldAwakenedAscensionService.class);
        WorldAwakenedMutatorService mutatorService = mock(WorldAwakenedMutatorService.class);
        WorldAwakenedDebugCommandService debugCommandService =
                new WorldAwakenedDebugCommandService(stageService, ascensionService);

        LiteralArgumentBuilder<CommandSourceStack> debugTree = invokeBuildDebugTree(
                datapackService,
                stageService,
                ascensionService,
                mutatorService,
                debugCommandService);

        Set<String> topLevelChildren = debugTree.build().getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());

        assertTrue(topLevelChildren.contains("clear"), "Expected /wa debug clear");
        assertTrue(topLevelChildren.contains("reset"), "Expected /wa debug reset");
        assertTrue(topLevelChildren.contains("mutators"), "Expected /wa debug mutators");
        assertTrue(topLevelChildren.contains("spawn"), "Expected /wa debug spawn");
    }

    @SuppressWarnings("unchecked")
    private static LiteralArgumentBuilder<CommandSourceStack> invokeBuildDebugTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedDebugCommandService debugCommandService) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    "buildDebugTree",
                    WorldAwakenedDatapackService.class,
                    WorldAwakenedStageService.class,
                    WorldAwakenedAscensionService.class,
                    WorldAwakenedMutatorService.class,
                    WorldAwakenedDebugCommandService.class);
            method.setAccessible(true);
            return (LiteralArgumentBuilder<CommandSourceStack>) method.invoke(
                    null,
                    datapackService,
                    stageService,
                    ascensionService,
                    mutatorService,
                    debugCommandService);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke buildDebugTree", exception);
        }
    }
}

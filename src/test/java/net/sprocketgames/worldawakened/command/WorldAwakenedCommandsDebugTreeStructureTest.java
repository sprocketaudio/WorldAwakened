package net.sprocketgames.worldawakened.command;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.difficulty.WorldAwakenedEffectiveDifficultyScalarService;
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
        when(mutatorService.pressureSnapshotIds()).thenReturn(List.of(7L, 8L));
        WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService = new WorldAwakenedEffectiveDifficultyScalarService();
        WorldAwakenedDebugCommandService debugCommandService =
                new WorldAwakenedDebugCommandService(stageService, ascensionService);

        LiteralArgumentBuilder<CommandSourceStack> debugTree = invokeBuildDebugTree(
                datapackService,
                stageService,
                ascensionService,
                mutatorService,
                difficultyScalarService,
                debugCommandService);

        Set<String> topLevelChildren = debugTree.build().getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());

        assertTrue(topLevelChildren.contains("clear"), "Expected /wa debug clear");
        assertTrue(topLevelChildren.contains("reset"), "Expected /wa debug reset");
        assertTrue(topLevelChildren.contains("mutators"), "Expected /wa debug mutators");
        assertTrue(topLevelChildren.contains("spawn"), "Expected /wa debug spawn");
        assertTrue(topLevelChildren.contains("difficulty"), "Expected /wa debug difficulty");
        assertTrue(topLevelChildren.contains("pressure"), "Expected /wa debug pressure");

        CommandNode<CommandSourceStack> pressureNode = debugTree.build().getChild("pressure");
        Set<String> pressureChildren = pressureNode.getChildren().stream()
                .map(CommandNode::getName)
                .collect(Collectors.toSet());
        assertTrue(pressureChildren.contains("evaluate"), "Expected /wa debug pressure evaluate");
        assertTrue(pressureChildren.contains("last"), "Expected /wa debug pressure last");
        assertTrue(pressureChildren.contains("replay"), "Expected /wa debug pressure replay");

        CommandNode<CommandSourceStack> replayNode = pressureNode.getChild("replay");
        @SuppressWarnings("unchecked")
        ArgumentCommandNode<CommandSourceStack, Long> idNode =
                (ArgumentCommandNode<CommandSourceStack, Long>) replayNode.getChild("id");
        assertTrue(idNode.getCustomSuggestions() != null, "Expected /wa debug pressure replay <id> to provide suggestions");
    }

    @SuppressWarnings("unchecked")
    private static LiteralArgumentBuilder<CommandSourceStack> invokeBuildDebugTree(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedAscensionService ascensionService,
            WorldAwakenedMutatorService mutatorService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService,
            WorldAwakenedDebugCommandService debugCommandService) {
        try {
            Method method = WorldAwakenedCommands.class.getDeclaredMethod(
                    "buildDebugTree",
                    WorldAwakenedDatapackService.class,
                    WorldAwakenedStageService.class,
                    WorldAwakenedAscensionService.class,
                    WorldAwakenedMutatorService.class,
                    WorldAwakenedEffectiveDifficultyScalarService.class,
                    WorldAwakenedDebugCommandService.class);
            method.setAccessible(true);
            return (LiteralArgumentBuilder<CommandSourceStack>) method.invoke(
                    null,
                    datapackService,
                    stageService,
                    ascensionService,
                    mutatorService,
                    difficultyScalarService,
                    debugCommandService);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke buildDebugTree", exception);
        }
    }
}

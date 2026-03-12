package net.sprocketgames.worldawakened.debug;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedComponentDebugFormatter {
    private WorldAwakenedComponentDebugFormatter() {
    }

    public static String formatAppliedMutation(ResourceLocation definitionId, List<ResourceLocation> componentTypes) {
        return "Applied Mutation: " + definitionId + System.lineSeparator()
                + "Components: " + formatComponentList(componentTypes);
    }

    public static String formatChosenAscensionReward(ResourceLocation definitionId, List<ResourceLocation> componentTypes) {
        return "Chosen Ascension Reward: " + definitionId + System.lineSeparator()
                + "Components: " + formatComponentList(componentTypes);
    }

    public static String formatComponentList(List<ResourceLocation> componentTypes) {
        if (componentTypes.isEmpty()) {
            return "<none>";
        }
        return componentTypes.stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
    }
}

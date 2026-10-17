package net.sprocketgames.worldawakened.data.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;

class WorldAwakenedMutationPoolValidationTest {
    private static final ResourceLocation FIRST = ResourceLocation.parse("test:first");
    private static final ResourceLocation SECOND = ResourceLocation.parse("test:second");
    private static final ResourceLocation ZOMBIE = ResourceLocation.parse("minecraft:zombie");

    @Test
    void acceptsACompatibleCombinationRestrictedWithinTheParentPool() {
        var errors = WorldAwakenedMutationPoolValidation.validate(pool(
                List.of(FIRST, SECOND), List.of(ZOMBIE)),
                Map.of(FIRST, mutator(FIRST, List.of(), "first"),
                        SECOND, mutator(SECOND, List.of(), "second")));

        assertTrue(errors.isEmpty(), () -> String.join("; ", errors));
    }

    @Test
    void rejectsDuplicatesAndEntitiesOutsideTheParentPool() {
        var errors = WorldAwakenedMutationPoolValidation.validate(pool(
                List.of(FIRST, FIRST), List.of(ResourceLocation.parse("minecraft:husk"))),
                Map.of(FIRST, mutator(FIRST, List.of(), "first"),
                        SECOND, mutator(SECOND, List.of(), "second")));

        assertTrue(errors.stream().anyMatch(error -> error.contains("duplicate mutators")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("outside the parent pool")));
    }

    @Test
    void rejectsExclusiveAndOverLimitStackingCombinations() {
        var exclusiveErrors = WorldAwakenedMutationPoolValidation.validate(pool(
                List.of(FIRST, SECOND), List.of()),
                Map.of(FIRST, mutator(FIRST, List.of(SECOND), "a"),
                        SECOND, mutator(SECOND, List.of(), "b")));
        var stackingErrors = WorldAwakenedMutationPoolValidation.validate(pool(
                List.of(FIRST, SECOND), List.of()),
                Map.of(FIRST, mutator(FIRST, List.of(), "shared"),
                        SECOND, mutator(SECOND, List.of(), "shared")));

        assertTrue(exclusiveErrors.stream().anyMatch(error -> error.contains("exclusive")));
        assertTrue(stackingErrors.stream().anyMatch(error -> error.contains("stacking group")));
    }

    private static MutationPoolDefinition pool(
            List<ResourceLocation> combinationMutators, List<ResourceLocation> combinationEntities) {
        JsonObject first = new JsonObject(); first.addProperty("id", FIRST.toString()); first.addProperty("weight", 2);
        JsonObject second = new JsonObject(); second.addProperty("id", SECOND.toString()); second.addProperty("weight", 1);
        var combination = new MutationPoolDefinition.CuratedCombination(
                ResourceLocation.parse("test:combination"), 1, combinationMutators, combinationEntities);
        return new MutationPoolDefinition(1, ResourceLocation.parse("test:pool"), true,
                new MutationPoolDefinition.MutationSelection(1, 0.1D, 0.2D, 2,
                        MutationPoolDefinition.CombinationMode.CURATED, List.of(combination)),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(ZOMBIE), List.of(first, second));
    }

    private static MobMutatorDefinition mutator(
            ResourceLocation id, List<ResourceLocation> exclusive, String stackingGroup) {
        MobMutatorDefinition definition = mock(MobMutatorDefinition.class);
        when(definition.id()).thenReturn(id);
        when(definition.exclusiveWith()).thenReturn(exclusive);
        when(definition.stackingGroup()).thenReturn(Optional.of(stackingGroup));
        when(definition.maxStackCount()).thenReturn(1);
        return definition;
    }
}

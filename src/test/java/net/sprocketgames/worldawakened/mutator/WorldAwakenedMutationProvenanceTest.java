package net.sprocketgames.worldawakened.mutator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class WorldAwakenedMutationProvenanceTest {
    @Test
    void writesAndReadsRequiredProvenanceFields() {
        CompoundTag tag = new CompoundTag();
        WorldAwakenedMutationProvenance.MutationProvenancePayload payload =
                new WorldAwakenedMutationProvenance.MutationProvenancePayload(
                        Optional.of(ResourceLocation.parse("testpack:night_pool")),
                        List.of(ResourceLocation.parse("testpack:night_rule")),
                        List.of(ResourceLocation.parse("testpack:berserker"), ResourceLocation.parse("testpack:bulwark")),
                        List.of(ResourceLocation.parse("worldawakened:max_health_bonus")),
                        List.of(ResourceLocation.parse("testpack:stage_two")),
                        "WA-T123",
                        0,
                        WorldAwakenedMutationProvenance.ORIGIN_SPAWN_PIPELINE,
                        List.of());

        WorldAwakenedMutationProvenance.writeProvenance(tag, payload);
        WorldAwakenedMutationProvenance.MutationProvenanceView read = WorldAwakenedMutationProvenance.read(tag);

        assertTrue(read.hasProvenance());
        assertEquals(Optional.of(ResourceLocation.parse("testpack:night_pool")), read.sourcePoolId());
        assertEquals(List.of(ResourceLocation.parse("testpack:berserker"), ResourceLocation.parse("testpack:bulwark")), read.mutationIds());
        assertEquals(List.of(ResourceLocation.parse("worldawakened:max_health_bonus")), read.componentIds());
        assertEquals(List.of(ResourceLocation.parse("testpack:stage_two")), read.stageContext());
        assertEquals("WA-T123", read.traceId());
    }

    @Test
    void pipelineMarkerAndMutatorOriginSupportRecursionGuard() {
        CompoundTag tag = new CompoundTag();

        WorldAwakenedMutationProvenance.markPipelineProcessed(tag);
        assertTrue(WorldAwakenedMutationProvenance.isPipelineProcessed(tag));

        WorldAwakenedMutationProvenance.markMutatorSpawnOrigin(tag, 0);
        assertEquals(1, WorldAwakenedMutationProvenance.mutationDepth(tag));
        assertTrue(WorldAwakenedMutationProvenance.isRecursionBlocked(tag));
    }

    @Test
    void persistsResolvedComponentParametersRatherThanOnlyDefinitionIds() {
        CompoundTag tag = new CompoundTag();
        JsonObject parameters = new JsonObject();
        parameters.addProperty("damage", 8);
        parameters.addProperty("cooldown_ticks", 300);
        WorldAwakenedMutationProvenance.ResolvedMutationComponent component =
                new WorldAwakenedMutationProvenance.ResolvedMutationComponent(
                        ResourceLocation.parse("testpack:lightning_strike"),
                        ResourceLocation.parse("worldawakened:lightning_package"), parameters, 11);
        parameters.addProperty("damage", 99);

        WorldAwakenedMutationProvenance.writeProvenance(tag,
                new WorldAwakenedMutationProvenance.MutationProvenancePayload(
                        Optional.empty(), List.of(), List.of(component.sourceEffectId()), List.of(component.componentType()),
                        List.of(component), List.of(), "WA-T124", 0,
                        WorldAwakenedMutationProvenance.ORIGIN_SPAWN_PIPELINE, List.of(), "instance", 11,
                        Optional.of(ResourceLocation.parse("worldawakened:rare")), "generated"));

        WorldAwakenedMutationProvenance.MutationProvenanceView read = WorldAwakenedMutationProvenance.read(tag);
        assertEquals(1, read.resolvedComponents().size());
        assertEquals("8", read.resolvedComponents().getFirst().parameters().get("damage").getAsString());
        assertEquals("300", read.resolvedComponents().getFirst().parameters().get("cooldown_ticks").getAsString());
        assertEquals(11, read.resolvedComponents().getFirst().power());
        assertEquals("generated", read.generationSource());
    }

    @Test
    void pendingSpawnOriginIsConsumedAndCleared() {
        CompoundTag tag = new CompoundTag();

        WorldAwakenedMutationProvenance.markPendingSpawnOrigin(tag, WorldAwakenedMobSpawnOrigin.TRIAL_SPAWNER);

        assertEquals(
                WorldAwakenedMobSpawnOrigin.TRIAL_SPAWNER,
                WorldAwakenedMutationProvenance.consumePendingSpawnOrigin(tag));
        assertEquals(
                WorldAwakenedMobSpawnOrigin.OTHER,
                WorldAwakenedMutationProvenance.consumePendingSpawnOrigin(tag));
        assertFalse(tag.contains(WorldAwakenedMutationProvenance.WA_PENDING_SPAWN_ORIGIN));
    }
}

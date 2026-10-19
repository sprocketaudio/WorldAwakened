package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentSuppressionPolicy;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;

class WorldAwakenedAscensionOfferRuntimeTest {
    @Test
    void roundTripsPendingAndResolvedRuntimeState() {
        ResourceLocation offerId = id("testpack:offer_1");
        ResourceLocation rewardA = id("testpack:reward_a");
        ResourceLocation rewardB = id("testpack:reward_b");
        String instanceId = WorldAwakenedAscensionOfferRuntime.stableOpaqueInstanceId("testpack:offer_1|stage:testpack:baseline");

        WorldAwakenedAscensionOfferRuntime pending = WorldAwakenedAscensionOfferRuntime.pending(
                instanceId,
                offerId,
                "stage:testpack:baseline",
                100L,
                List.of(rewardA, rewardB));
        WorldAwakenedAscensionOfferRuntime resolved = pending.resolve(rewardA, 200L);

        CompoundTag encoded = resolved.toTag();
        WorldAwakenedAscensionOfferRuntime decoded = WorldAwakenedAscensionOfferRuntime.fromTag(encoded).orElseThrow();

        assertEquals(resolved.instanceId(), decoded.instanceId());
        assertEquals(resolved.offerId(), decoded.offerId());
        assertEquals(resolved.sourceKey(), decoded.sourceKey());
        assertEquals(resolved.grantedAtMillis(), decoded.grantedAtMillis());
        assertEquals(resolved.candidateRewards(), decoded.candidateRewards());
        assertEquals(resolved.chosenRewardId(), decoded.chosenRewardId());
        assertTrue(decoded.resolvedAtMillis().isPresent());
        assertEquals(200L, decoded.resolvedAtMillis().getAsLong());
    }

    @Test
    void persistsResolvedGeneratedChoiceComponentsAcrossDefinitionChanges() {
        ResourceLocation offerId = id("testpack:generated_offer");
        ResourceLocation rewardId = id("worldawakened:generated/wao_test0001/0");
        JsonObject parameters = new JsonObject();
        parameters.addProperty("amount", 2.0D);
        AscensionRewardDefinition frozen = new AscensionRewardDefinition(
                1, rewardId, true,
                List.of(new AscensionComponentDefinition(id("worldawakened:max_health_bonus"), true, 0, parameters,
                        List.of(), List.of(), List.of(), false, Optional.empty(),
                        AscensionComponentSuppressionPolicy.REWARD_ONLY)),
                new AscensionRewardDefinition.RewardPresentation(new JsonPrimitive("Frozen Choice"), Optional.empty(),
                        Optional.empty(), Optional.of("worldawakened:uncommon"), List.of("generated"), Optional.empty()),
                new AscensionRewardDefinition.RewardWeights(Optional.empty(), Optional.empty()),
                new AscensionRewardDefinition.RewardConstraints(Optional.empty(), List.of(), List.of(), List.of(), 1));

        WorldAwakenedAscensionOfferRuntime runtime = WorldAwakenedAscensionOfferRuntime.pending(
                "wao_test0001", offerId, "test", 1L, List.of(rewardId),
                List.of(new WorldAwakenedAscensionOfferRuntime.ResolvedChoice(rewardId, frozen))).resolve(rewardId, 2L);
        WorldAwakenedAscensionOfferRuntime decoded =
                WorldAwakenedAscensionOfferRuntime.fromTag(runtime.toTag()).orElseThrow();

        assertEquals("2.0", decoded.resolvedChoice(rewardId).orElseThrow().components().getFirst()
                .parameters().get("amount").getAsString());
        assertEquals(Optional.of(rewardId), decoded.chosenRewardId());

        parameters.addProperty("amount", 99.0D);
        assertEquals("2.0", decoded.resolvedChoice(rewardId).orElseThrow().components().getFirst()
                .parameters().get("amount").getAsString(), "the saved choice must not retain a mutable definition reference");
    }

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

package net.sprocketgames.worldawakened.ascension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

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

    private static ResourceLocation id(String id) {
        String[] parts = id.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

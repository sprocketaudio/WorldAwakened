package net.sprocketgames.worldawakened.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WorldAwakenedOwnedClientVisualCarrierServiceTest {
    @Test
    void resolveNightVisionScalePrefersVanillaWhenPresent() {
        float scale = WorldAwakenedOwnedClientVisualCarrierService.resolveNightVisionScale(true, 0.83F, true);

        assertEquals(0.83F, scale, 0.0001F);
    }

    @Test
    void resolveNightVisionScaleUsesOwnedFallbackWhenOnlyCarrierIsActive() {
        float scale = WorldAwakenedOwnedClientVisualCarrierService.resolveNightVisionScale(false, 0.0F, true);

        assertEquals(1.0F, scale, 0.0001F);
    }

    @Test
    void resolveNightVisionScaleFallsBackToZeroWhenNothingIsActive() {
        float scale = WorldAwakenedOwnedClientVisualCarrierService.resolveNightVisionScale(false, 0.0F, false);

        assertEquals(0.0F, scale, 0.0001F);
    }

    @Test
    void vanillaNightVisionLiftLeavesColorUntouchedWhenScaleIsZero() {
        Vector3f color = WorldAwakenedOwnedClientVisualCarrierService.vanillaNightVisionLift(0.2F, 0.4F, 0.1F, 0.0F);

        assertEquals(0.2F, color.x(), 0.0001F);
        assertEquals(0.4F, color.y(), 0.0001F);
        assertEquals(0.1F, color.z(), 0.0001F);
    }

    @Test
    void vanillaNightVisionLiftNormalizesTowardBrightestChannelLikeVanilla() {
        Vector3f color = WorldAwakenedOwnedClientVisualCarrierService.vanillaNightVisionLift(0.2F, 0.4F, 0.1F, 1.0F);

        assertEquals(0.5F, color.x(), 0.0001F);
        assertEquals(1.0F, color.y(), 0.0001F);
        assertEquals(0.25F, color.z(), 0.0001F);
    }

    @Test
    void vanillaNightVisionLiftInterpolatesWithPartialScale() {
        Vector3f color = WorldAwakenedOwnedClientVisualCarrierService.vanillaNightVisionLift(0.2F, 0.4F, 0.1F, 0.5F);

        assertEquals(0.35F, color.x(), 0.0001F);
        assertEquals(0.7F, color.y(), 0.0001F);
        assertEquals(0.175F, color.z(), 0.0001F);
    }
}

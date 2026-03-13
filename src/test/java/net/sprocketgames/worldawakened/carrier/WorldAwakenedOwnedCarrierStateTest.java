package net.sprocketgames.worldawakened.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class WorldAwakenedOwnedCarrierStateTest {
    @Test
    void stateTracksOwnedCarriersByStableKey() {
        WorldAwakenedOwnedCarrierState state = new WorldAwakenedOwnedCarrierState();
        ResourceLocation key = id("worldawakened:ascension/test_reward/0");
        ResourceLocation fireCarrier = id("worldawakened:fire_resistance_passive");
        ResourceLocation nightCarrier = id("worldawakened:night_vision_passive");

        state.put(key, fireCarrier);
        assertTrue(state.hasCarrier(fireCarrier));
        assertEquals(fireCarrier, state.snapshot().get(key));

        state.put(key, nightCarrier);
        assertFalse(state.hasCarrier(fireCarrier));
        assertEquals(nightCarrier, state.snapshot().get(key));

        state.clear();
        assertTrue(state.snapshot().isEmpty());
    }

    @Test
    void stateStreamCodecRoundTripsStableKeys() {
        WorldAwakenedOwnedCarrierState state = new WorldAwakenedOwnedCarrierState();
        state.put(id("worldawakened:ascension/test_reward/0"), id("worldawakened:night_vision_passive"));
        state.put(id("worldawakened:ascension/test_reward/1"), id("worldawakened:fire_resistance_passive"));

        RegistryFriendlyByteBuf writeBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WorldAwakenedOwnedCarrierState.STREAM_CODEC.encode(writeBuffer, state);

        RegistryFriendlyByteBuf readBuffer = new RegistryFriendlyByteBuf(writeBuffer.copy(), RegistryAccess.EMPTY);
        WorldAwakenedOwnedCarrierState decoded = WorldAwakenedOwnedCarrierState.STREAM_CODEC.decode(readBuffer);

        assertEquals(state.snapshot(), decoded.snapshot());
    }

    private static ResourceLocation id(String value) {
        String[] parts = value.split(":", 2);
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}

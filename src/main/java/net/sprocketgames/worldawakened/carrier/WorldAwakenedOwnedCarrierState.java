package net.sprocketgames.worldawakened.carrier;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedOwnedCarrierState {
    private static final StreamCodec<RegistryFriendlyByteBuf, LinkedHashMap<ResourceLocation, ResourceLocation>> MAP_STREAM_CODEC = StreamCodec.of(
            (buffer, map) -> {
                buffer.writeVarInt(map.size());
                map.forEach((stableKey, carrierId) -> {
                    ResourceLocation.STREAM_CODEC.encode(buffer, stableKey);
                    ResourceLocation.STREAM_CODEC.encode(buffer, carrierId);
                });
            },
            buffer -> {
                int size = buffer.readVarInt();
                LinkedHashMap<ResourceLocation, ResourceLocation> map = new LinkedHashMap<>();
                for (int index = 0; index < size; index++) {
                    ResourceLocation stableKey = ResourceLocation.STREAM_CODEC.decode(buffer);
                    ResourceLocation carrierId = ResourceLocation.STREAM_CODEC.decode(buffer);
                    map.put(stableKey, carrierId);
                }
                return map;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedOwnedCarrierState> STREAM_CODEC = StreamCodec.composite(
            MAP_STREAM_CODEC,
            WorldAwakenedOwnedCarrierState::mutableSnapshot,
            WorldAwakenedOwnedCarrierState::fromMap);

    private final Map<ResourceLocation, ResourceLocation> carriersByStableKey = new LinkedHashMap<>();

    public static WorldAwakenedOwnedCarrierState fromMap(Map<ResourceLocation, ResourceLocation> carriersByStableKey) {
        WorldAwakenedOwnedCarrierState state = new WorldAwakenedOwnedCarrierState();
        state.carriersByStableKey.putAll(carriersByStableKey);
        return state;
    }

    public boolean clear() {
        if (carriersByStableKey.isEmpty()) {
            return false;
        }
        carriersByStableKey.clear();
        return true;
    }

    public boolean put(ResourceLocation stableKey, ResourceLocation carrierId) {
        ResourceLocation previous = carriersByStableKey.put(stableKey, carrierId);
        return !carrierId.equals(previous);
    }

    public boolean hasCarrier(ResourceLocation carrierId) {
        return carriersByStableKey.containsValue(carrierId);
    }

    public Map<ResourceLocation, ResourceLocation> snapshot() {
        return Map.copyOf(carriersByStableKey);
    }

    LinkedHashMap<ResourceLocation, ResourceLocation> mutableSnapshot() {
        return new LinkedHashMap<>(carriersByStableKey);
    }
}

package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedGlowStylePayload(
        int entityId,
        boolean active,
        int colorRgb,
        float brightness,
        boolean seeThroughWalls,
        boolean pulse,
        float pulseSpeed,
        float pulseStrength) implements CustomPacketPayload {
    public static final Type<WorldAwakenedGlowStylePayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("glow_style_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedGlowStylePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.entityId());
                buffer.writeBoolean(payload.active());
                buffer.writeInt(payload.colorRgb());
                buffer.writeFloat(payload.brightness());
                buffer.writeBoolean(payload.seeThroughWalls());
                buffer.writeBoolean(payload.pulse());
                buffer.writeFloat(payload.pulseSpeed());
                buffer.writeFloat(payload.pulseStrength());
            },
            buffer -> new WorldAwakenedGlowStylePayload(
                    buffer.readInt(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readFloat(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readFloat(),
                    buffer.readFloat()));

    @Override
    public Type<WorldAwakenedGlowStylePayload> type() {
        return TYPE;
    }
}

package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedOpenConfigPayload(String payloadJson) implements CustomPacketPayload {
    public static final Type<WorldAwakenedOpenConfigPayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("open_native_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedOpenConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(1_048_576),
                    WorldAwakenedOpenConfigPayload::payloadJson,
                    WorldAwakenedOpenConfigPayload::new);

    @Override
    public Type<WorldAwakenedOpenConfigPayload> type() {
        return TYPE;
    }
}

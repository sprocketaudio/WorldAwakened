package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedOpenAscensionOfferPayload(String payloadJson) implements CustomPacketPayload {
    public static final Type<WorldAwakenedOpenAscensionOfferPayload> TYPE = new Type<>(WorldAwakenedConstants.id("open_ascension_offer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedOpenAscensionOfferPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            WorldAwakenedOpenAscensionOfferPayload::payloadJson,
            WorldAwakenedOpenAscensionOfferPayload::new);

    @Override
    public Type<WorldAwakenedOpenAscensionOfferPayload> type() {
        return TYPE;
    }
}

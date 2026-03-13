package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedSelectAscensionRewardPayload(
        String offerInstanceId,
        String rewardId) implements CustomPacketPayload {
    public static final Type<WorldAwakenedSelectAscensionRewardPayload> TYPE = new Type<>(WorldAwakenedConstants.id("select_ascension_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedSelectAscensionRewardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            WorldAwakenedSelectAscensionRewardPayload::offerInstanceId,
            ByteBufCodecs.STRING_UTF8,
            WorldAwakenedSelectAscensionRewardPayload::rewardId,
            WorldAwakenedSelectAscensionRewardPayload::new);

    @Override
    public Type<WorldAwakenedSelectAscensionRewardPayload> type() {
        return TYPE;
    }
}

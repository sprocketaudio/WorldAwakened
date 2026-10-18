package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedProfileOperationPayload(long settingsRevision, long profileRevision, String operation)
        implements CustomPacketPayload {
    public static final Type<WorldAwakenedProfileOperationPayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("profile_operation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedProfileOperationPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, WorldAwakenedProfileOperationPayload::settingsRevision,
                    ByteBufCodecs.VAR_LONG, WorldAwakenedProfileOperationPayload::profileRevision,
                    ByteBufCodecs.stringUtf8(32), WorldAwakenedProfileOperationPayload::operation,
                    WorldAwakenedProfileOperationPayload::new);
    @Override public Type<WorldAwakenedProfileOperationPayload> type() { return TYPE; }
}

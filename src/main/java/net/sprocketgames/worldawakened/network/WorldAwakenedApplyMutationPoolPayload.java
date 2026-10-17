package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedApplyMutationPoolPayload(long baseRevision, String poolJson, boolean revert)
        implements CustomPacketPayload {
    public static final Type<WorldAwakenedApplyMutationPoolPayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("apply_mutation_pool_override"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedApplyMutationPoolPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, WorldAwakenedApplyMutationPoolPayload::baseRevision,
                    ByteBufCodecs.stringUtf8(262_144), WorldAwakenedApplyMutationPoolPayload::poolJson,
                    ByteBufCodecs.BOOL, WorldAwakenedApplyMutationPoolPayload::revert,
                    WorldAwakenedApplyMutationPoolPayload::new);

    @Override
    public Type<WorldAwakenedApplyMutationPoolPayload> type() { return TYPE; }
}

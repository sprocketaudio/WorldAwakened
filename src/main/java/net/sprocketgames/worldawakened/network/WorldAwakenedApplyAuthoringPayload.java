package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

/** Atomic world-authored stage, trigger, pool and difficulty-track draft. */
public record WorldAwakenedApplyAuthoringPayload(long baseRevision, String authoringJson)
        implements CustomPacketPayload {
    public static final Type<WorldAwakenedApplyAuthoringPayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("apply_profile_authoring"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedApplyAuthoringPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_LONG, WorldAwakenedApplyAuthoringPayload::baseRevision,
                    ByteBufCodecs.stringUtf8(1_048_576), WorldAwakenedApplyAuthoringPayload::authoringJson,
                    WorldAwakenedApplyAuthoringPayload::new);

    @Override public Type<WorldAwakenedApplyAuthoringPayload> type() { return TYPE; }
}

package net.sprocketgames.worldawakened.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public record WorldAwakenedApplyConfigPayload(long baseRevision, String settingsJson)
        implements CustomPacketPayload {
    public static final Type<WorldAwakenedApplyConfigPayload> TYPE =
            new Type<>(WorldAwakenedConstants.id("apply_native_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldAwakenedApplyConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    WorldAwakenedApplyConfigPayload::baseRevision,
                    ByteBufCodecs.STRING_UTF8,
                    WorldAwakenedApplyConfigPayload::settingsJson,
                    WorldAwakenedApplyConfigPayload::new);

    @Override
    public Type<WorldAwakenedApplyConfigPayload> type() {
        return TYPE;
    }
}

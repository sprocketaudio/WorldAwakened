package net.sprocketgames.worldawakened.carrier;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;

public final class WorldAwakenedOwnedCarrierAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(
            NeoForgeRegistries.ATTACHMENT_TYPES,
            WorldAwakenedConstants.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WorldAwakenedOwnedCarrierState>> ACTIVE_CARRIERS =
            ATTACHMENT_TYPES.register(
                    "active_carriers",
                    () -> AttachmentType.builder(WorldAwakenedOwnedCarrierState::new)
                            .sync((holder, recipient) -> holder == recipient, WorldAwakenedOwnedCarrierState.STREAM_CODEC)
                            .build());

    private WorldAwakenedOwnedCarrierAttachments() {
    }
}

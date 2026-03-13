package net.sprocketgames.worldawakened.network;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.sprocketgames.worldawakened.WorldAwakenedMod;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;

public final class WorldAwakenedNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NETWORK_VERSION = "1";

    private WorldAwakenedNetwork() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                WorldAwakenedOpenAscensionOfferPayload.TYPE,
                WorldAwakenedOpenAscensionOfferPayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleOpenAscensionOffer);
        registrar.playToServer(
                WorldAwakenedSelectAscensionRewardPayload.TYPE,
                WorldAwakenedSelectAscensionRewardPayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleSelectAscensionReward);
    }

    public static void sendOpenAscensionOffer(ServerPlayer player, WorldAwakenedAscensionService.OpenOfferView view) {
        PacketDistributor.sendToPlayer(player, new WorldAwakenedOpenAscensionOfferPayload(toJson(view).toString()));
    }

    private static void handleOpenAscensionOffer(WorldAwakenedOpenAscensionOfferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.sprocketgames.worldawakened.network.client.WorldAwakenedClientAscensionPayloadHandler");
                handlerClass.getMethod("handleOpenOffer", String.class).invoke(null, payload.payloadJson());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side: no client handler present.
            } catch (ReflectiveOperationException exception) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Failed to dispatch open ascension offer payload to client handler: {}",
                        exception.toString());
            }
        });
    }

    private static void handleSelectAscensionReward(WorldAwakenedSelectAscensionRewardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!WorldAwakenedFeatureGates.ascensionEnabled()) {
                return;
            }
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            ResourceLocation rewardId = ResourceLocation.tryParse(payload.rewardId());
            if (rewardId == null) {
                WorldAwakenedLog.debug(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Rejected ascension selection packet with invalid reward id '{}' from {}",
                        payload.rewardId(),
                        player.getGameProfile().getName());
                return;
            }

            WorldAwakenedAscensionService.ChooseResult result = WorldAwakenedMod.ASCENSION_SERVICE.chooseReward(
                    player.serverLevel(),
                    player,
                    payload.offerInstanceId(),
                    rewardId,
                    "packet");
            if (result.status() != WorldAwakenedAscensionService.ChooseStatus.ACCEPTED) {
                WorldAwakenedLog.debug(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Rejected ascension selection packet from {} reason={}",
                        player.getGameProfile().getName(),
                        result.detail());
            }
        });
    }

    private static JsonObject toJson(WorldAwakenedAscensionService.OpenOfferView view) {
        JsonObject root = new JsonObject();
        root.addProperty("instance_id", view.instanceId());
        root.addProperty("offer_id", view.offerId().toString());
        root.addProperty("title", view.title());
        root.addProperty("description", view.description());

        JsonArray rewards = new JsonArray();
        for (WorldAwakenedAscensionService.RewardChoiceView reward : view.rewards()) {
            JsonObject rewardNode = new JsonObject();
            rewardNode.addProperty("id", reward.rewardId().toString());
            rewardNode.addProperty("title", reward.title());
            rewardNode.addProperty("description", reward.description());
            rewardNode.addProperty("rarity", reward.rarity());
            rewards.add(rewardNode);
        }
        root.add("rewards", rewards);
        return root;
    }
}

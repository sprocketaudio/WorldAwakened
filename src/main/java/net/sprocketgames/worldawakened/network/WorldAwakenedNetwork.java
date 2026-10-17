package net.sprocketgames.worldawakened.network;

import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.sprocketgames.worldawakened.WorldAwakenedMod;
import net.sprocketgames.worldawakened.ascension.WorldAwakenedAscensionService;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedConfigService;
import net.sprocketgames.worldawakened.config.WorldAwakenedEditorSettings;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.config.WorldAwakenedProfileOverrideService;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedGlowStyleState;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;

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
        registrar.playToClient(
                WorldAwakenedGlowStylePayload.TYPE,
                WorldAwakenedGlowStylePayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleGlowStyleState);
        registrar.playToClient(
                WorldAwakenedOpenConfigPayload.TYPE,
                WorldAwakenedOpenConfigPayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleOpenConfig);
        registrar.playToServer(
                WorldAwakenedApplyConfigPayload.TYPE,
                WorldAwakenedApplyConfigPayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleApplyConfig);
        registrar.playToServer(
                WorldAwakenedApplyMutationPoolPayload.TYPE,
                WorldAwakenedApplyMutationPoolPayload.STREAM_CODEC,
                WorldAwakenedNetwork::handleApplyMutationPool);
    }

    public static void sendOpenAscensionOffer(ServerPlayer player, WorldAwakenedAscensionService.OpenOfferView view) {
        PacketDistributor.sendToPlayer(player, new WorldAwakenedOpenAscensionOfferPayload(toJson(view).toString()));
    }

    public static void sendGlowStyleState(ServerPlayer player, Mob mob) {
        PacketDistributor.sendToPlayer(player, toGlowStylePayload(mob));
    }

    public static void sendGlowStyleStateToTracking(Mob mob) {
        PacketDistributor.sendToPlayersTrackingEntity(mob, toGlowStylePayload(mob));
    }

    public static void sendOpenConfig(ServerPlayer player) {
        sendConfigSnapshot(player, "ready", "");
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

    private static void handleGlowStyleState(WorldAwakenedGlowStylePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("net.sprocketgames.worldawakened.network.client.WorldAwakenedClientGlowStylePayloadHandler");
                handlerClass.getMethod(
                        "handleGlowStyleState",
                        int.class,
                        boolean.class,
                        int.class,
                        float.class,
                        boolean.class,
                        boolean.class,
                        float.class,
                        float.class)
                        .invoke(
                                null,
                                payload.entityId(),
                                payload.active(),
                                payload.colorRgb(),
                                payload.brightness(),
                                payload.seeThroughWalls(),
                                payload.pulse(),
                                payload.pulseSpeed(),
                                payload.pulseStrength());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side: no client handler present.
            } catch (ReflectiveOperationException exception) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.PIPELINE,
                        "Failed to dispatch glow style payload to client handler: {}",
                        exception.toString());
            }
        });
    }

    private static void handleOpenConfig(WorldAwakenedOpenConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName(
                        "net.sprocketgames.worldawakened.network.client.WorldAwakenedClientConfigPayloadHandler");
                handlerClass.getMethod("handleSnapshot", String.class).invoke(null, payload.payloadJson());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side: no client handler present.
            } catch (ReflectiveOperationException exception) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "Failed to dispatch native config payload to client handler: {}",
                        exception.toString());
            }
        });
    }

    private static void handleApplyConfig(WorldAwakenedApplyConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!WorldAwakenedConfigService.isAuthorized(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "World Awakened configuration update rejected: operator permission required."));
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.CONFIG,
                        "Rejected unauthorized native configuration packet: code={} player={}",
                        WorldAwakenedDiagnosticCodes.NATIVE_CONFIG_PERMISSION_DENIED,
                        player.getGameProfile().getName());
                return;
            }

            WorldAwakenedEditorSettings candidate;
            try {
                JsonObject root = JsonParser.parseString(payload.settingsJson()).getAsJsonObject();
                candidate = WorldAwakenedEditorSettings.fromJson(root);
            } catch (RuntimeException exception) {
                sendConfigSnapshot(player, "validation_failed", "The submitted settings payload is malformed.");
                return;
            }

            var errors = WorldAwakenedConfigService.validate(candidate);
            if (!errors.isEmpty()) {
                sendConfigSnapshot(player, "validation_failed", String.join(" ", errors));
                return;
            }

            WorldAwakenedConfigService.ConfigSnapshot before = WorldAwakenedConfigService.snapshot();
            if (payload.baseRevision() != before.revision()) {
                sendConfigSnapshot(
                        player,
                        "stale_revision",
                        "Settings changed on the server. Review the refreshed values and apply again.");
                return;
            }

            WorldAwakenedDatapackSnapshot candidateDatapack = null;
            if (before.settings().defaultGameplayEnabled() != candidate.defaultGameplayEnabled()) {
                candidateDatapack = WorldAwakenedConfigService.withPreviewSettings(
                        candidate,
                        () -> WorldAwakenedMod.DATAPACK_SERVICE.compile(player.getServer().getResourceManager()));
                int currentErrors = WorldAwakenedMod.DATAPACK_SERVICE.currentSnapshot().validationSummary().errorCount();
                if (candidateDatapack.validationSummary().errorCount() > currentErrors) {
                    sendConfigSnapshot(
                            player,
                            "validation_failed",
                            "Enabling this profile introduced datapack validation errors; no settings were changed.");
                    return;
                }
            }

            WorldAwakenedConfigService.ApplyResult result = WorldAwakenedConfigService.apply(
                    player.serverLevel(),
                    payload.baseRevision(),
                    candidate,
                    player.getGameProfile().getName());
            if (!result.accepted()) {
                sendConfigSnapshot(player, result.status().name().toLowerCase(java.util.Locale.ROOT), result.message());
                return;
            }
            if (candidateDatapack != null) {
                WorldAwakenedMod.DATAPACK_SERVICE.publish(candidateDatapack, "native_config_apply");
            }
            if (result.previous().settings().pressureEventsEnabled()
                    && !result.current().settings().pressureEventsEnabled()
                    && WorldAwakenedMod.INVASION_SERVICE.contextSnapshot(player.serverLevel()).invasionActive()) {
                WorldAwakenedMod.INVASION_SERVICE.stopActiveInvasionFromCommand(player.serverLevel());
            }
            sendConfigSnapshot(player, "applied", result.message());
        });
    }

    private static void handleApplyMutationPool(WorldAwakenedApplyMutationPoolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!WorldAwakenedConfigService.isAuthorized(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "World Awakened profile update rejected: operator permission required."));
                return;
            }
            MutationPoolDefinition candidate;
            try {
                candidate = MutationPoolDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.poolJson()))
                        .getOrThrow(message -> new IllegalArgumentException(message));
            } catch (RuntimeException exception) {
                sendConfigSnapshot(player, "validation_failed", "The submitted mutation pool is malformed.");
                return;
            }
            WorldAwakenedDatapackSnapshot source = WorldAwakenedMod.DATAPACK_SERVICE.sourceSnapshot();
            WorldAwakenedProfileOverrideService.ApplyResult result = payload.revert()
                    ? WorldAwakenedProfileOverrideService.revert(player.serverLevel(), payload.baseRevision(), candidate.id(), source)
                    : WorldAwakenedProfileOverrideService.apply(player.serverLevel(), payload.baseRevision(), candidate, source);
            if (result.accepted()) {
                WorldAwakenedMod.DATAPACK_SERVICE.publish(source, payload.revert()
                        ? "native_profile_revert" : "native_profile_apply");
                sendConfigSnapshot(player, "applied", result.message());
            } else {
                sendConfigSnapshot(player, "validation_failed", result.message());
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

    private static void sendConfigSnapshot(ServerPlayer player, String status, String message) {
        WorldAwakenedConfigService.ConfigSnapshot snapshot = WorldAwakenedConfigService.snapshot();
        WorldAwakenedEditorSettings settings = snapshot.settings();
        JsonObject root = new JsonObject();
        root.addProperty("status", status);
        root.addProperty("diagnostic_code", configDiagnosticCode(status));
        root.addProperty("message", message);
        root.addProperty("editable", WorldAwakenedConfigService.isAuthorized(player));
        root.addProperty("revision", snapshot.revision());
        root.addProperty("active_profile", settings.defaultGameplayEnabled() ? "Standard" : "Custom datapacks only");
        WorldAwakenedDatapackSnapshot datapack = WorldAwakenedMod.DATAPACK_SERVICE.currentSnapshot();
        WorldAwakenedProfileOverrideService.State overrides = WorldAwakenedProfileOverrideService.state();
        root.addProperty("profile_revision", overrides.revision());
        root.addProperty("profile_summary", datapack.data().stages().size() + " stages • "
                + datapack.data().mutationPools().size() + " pools • " + datapack.data().mobMutators().size()
                + " mutators • " + datapack.data().ascensionRewards().size() + " Ascension rewards • "
                + datapack.data().invasionProfiles().size() + " pressure events");

        WorldAwakenedProgressionMode mode = WorldAwakenedProgressionMode.fromConfig(
                WorldAwakenedCommonConfig.PROGRESSION_MODE.get());
        int unlocked = WorldAwakenedMod.STAGE_SERVICE.getUnlockedStages(player.serverLevel(), player).size();
        int total = WorldAwakenedMod.STAGE_SERVICE.stageRegistry().orderedStages().size();
        root.addProperty("progression_mode", mode.serializedName());
        root.addProperty("progression_summary", unlocked + " of " + total + " stages unlocked");
        root.addProperty("progression_editable", false);
        root.addProperty(
                "progression_note",
                "Progression mode changes require a saved-history migration and are not available in this milestone.");
        root.add("settings", settings.toJson());
        root.addProperty(
                "difficulty_intensity_available",
                WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_ENABLED.get());
        root.addProperty(
                "pressure_events_available",
                WorldAwakenedCommonConfig.MAX_CONCURRENT_INVASIONS.get() > 0);

        JsonObject bounds = new JsonObject();
        bounds.addProperty("difficulty_min", WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MIN_VALUE.get());
        bounds.addProperty("difficulty_max", WorldAwakenedCommonConfig.DIFFICULTY_GLOBAL_MAX_VALUE.get());
        bounds.addProperty("mutation_frequency_min", WorldAwakenedEditorSettings.MIN_MUTATION_FREQUENCY);
        bounds.addProperty("mutation_frequency_max", WorldAwakenedEditorSettings.MAX_MUTATION_FREQUENCY);
        bounds.addProperty("maximum_mutations_min", WorldAwakenedEditorSettings.MIN_MUTATIONS_PER_MOB);
        bounds.addProperty("maximum_mutations_max", WorldAwakenedEditorSettings.MAX_MUTATIONS_PER_MOB);
        bounds.addProperty("event_frequency_min", WorldAwakenedEditorSettings.MIN_EVENT_FREQUENCY);
        bounds.addProperty("event_frequency_max", WorldAwakenedEditorSettings.MAX_EVENT_FREQUENCY);
        root.add("bounds", bounds);

        JsonArray pools = new JsonArray();
        datapack.data().mutationPools().values().stream()
                .sorted(java.util.Comparator.comparing(pool -> pool.id().toString()))
                .forEach(pool -> MutationPoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE, pool).result().ifPresent(json -> {
                    JsonObject node = new JsonObject();
                    node.add("definition", json);
                    boolean overridden = overrides.mutationPools().containsKey(pool.id());
                    node.addProperty("overridden", overridden);
                    node.addProperty("origin", poolOrigin(player, pool.id(), overridden));
                    pools.add(node);
                }));
        root.add("mutation_pools", pools);

        JsonArray mutators = new JsonArray();
        datapack.data().mobMutators().values().stream()
                .sorted(java.util.Comparator.comparing(mutator -> mutator.id().toString()))
                .forEach(mutator -> mutators.add(mutatorSummary(mutator)));
        root.add("mutators", mutators);

        JsonArray entities = new JsonArray();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    if (!WorldAwakenedProfileOverrideService.livingEntityIds().contains(entry.getKey().location())) return;
                    JsonObject node = new JsonObject();
                    node.addProperty("id", entry.getKey().location().toString());
                    node.addProperty("name", entry.getValue().getDescription().getString());
                    node.addProperty("namespace", entry.getKey().location().getNamespace());
                    entities.add(node);
                });
        root.add("living_entities", entities);
        JsonArray dimensions = new JsonArray();
        player.getServer().levelKeys().stream().map(key -> key.location().toString()).sorted().forEach(dimensions::add);
        root.add("dimensions", dimensions);
        PacketDistributor.sendToPlayer(player, new WorldAwakenedOpenConfigPayload(root.toString()));
    }

    private static JsonObject mutatorSummary(MobMutatorDefinition mutator) {
        JsonObject node = new JsonObject();
        node.addProperty("id", mutator.id().toString());
        node.addProperty("name", readableJsonText(mutator.displayName(), mutator.id()));
        node.addProperty("rarity", mutator.rarity().orElse("common"));
        node.addProperty("stacking_group", mutator.stackingGroup().orElse("none"));
        node.addProperty("max_stack_count", mutator.maxStackCount());
        JsonArray exclusive = new JsonArray();
        mutator.exclusiveWith().forEach(id -> exclusive.add(id.toString()));
        node.add("exclusive_with", exclusive);
        JsonArray components = new JsonArray();
        mutator.components().forEach(component -> components.add(component.type().toString()));
        node.add("components", components);
        return node;
    }

    private static String poolOrigin(ServerPlayer player, ResourceLocation id, boolean overridden) {
        if (overridden) return "World override";
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), "mutation_pools/" + id.getPath() + ".json");
        var stack = player.getServer().getResourceManager().getResourceStack(resourceId);
        if (!stack.isEmpty()) {
            String pack = stack.getLast().sourcePackId();
            boolean bundled = id.getNamespace().equals("worldawakened") && id.getPath().startsWith("defaults/")
                    && pack.toLowerCase(java.util.Locale.ROOT).contains("worldawakened");
            return bundled ? "Bundled Standard" : "Datapack: " + pack;
        }
        return id.getNamespace().equals("worldawakened") && id.getPath().startsWith("defaults/")
                ? "Bundled Standard" : "External datapack";
    }

    private static String readableJsonText(com.google.gson.JsonElement value, ResourceLocation fallback) {
        if (value != null && value.isJsonPrimitive()) return value.getAsString();
        if (value != null && value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("text")) return object.get("text").getAsString();
            // Server-side translation lookup returns the key itself. The client editor needs a
            // readable fallback even when a language entry is available only on the client.
            if (object.has("translate")) return readableDefinitionId(fallback);
        }
        return readableDefinitionId(fallback);
    }

    private static String readableDefinitionId(ResourceLocation fallback) {
        String path = fallback.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        StringBuilder result = new StringBuilder();
        for (String word : path.split("_")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String configDiagnosticCode(String status) {
        return switch (status) {
            case "stale_revision" -> WorldAwakenedDiagnosticCodes.NATIVE_CONFIG_REVISION_STALE;
            case "validation_failed" -> WorldAwakenedDiagnosticCodes.NATIVE_CONFIG_VALIDATION_FAILED;
            case "apply_failed" -> WorldAwakenedDiagnosticCodes.NATIVE_CONFIG_APPLY_FAILED;
            default -> "";
        };
    }

    private static WorldAwakenedGlowStylePayload toGlowStylePayload(Mob mob) {
        return WorldAwakenedGlowStyleState.read(mob.getPersistentData())
                .map(style -> new WorldAwakenedGlowStylePayload(
                        mob.getId(),
                        true,
                        style.colorRgb(),
                        style.brightness(),
                        style.seeThroughWalls(),
                        style.pulse(),
                        style.pulseSpeed(),
                        style.pulseStrength()))
                .orElseGet(() -> new WorldAwakenedGlowStylePayload(
                        mob.getId(),
                        false,
                        0,
                        0.0F,
                        false,
                        false,
                        0.0F,
                        0.0F));
    }
}

package net.sprocketgames.worldawakened.ascension;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierIds;
import net.sprocketgames.worldawakened.carrier.WorldAwakenedOwnedCarrierService;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;

public final class WorldAwakenedAscensionRewardEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<Holder<Attribute>> MANAGED_ATTRIBUTES = Set.of(
            Attributes.MAX_HEALTH,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.ATTACK_DAMAGE,
            Attributes.MOVEMENT_SPEED,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.LUCK);

    void clear(ServerPlayer player) {
        WorldAwakenedOwnedCarrierService.clearOwnedCarriers(player);
        for (Holder<Attribute> attribute : MANAGED_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            List<AttributeModifier> existing = new ArrayList<>(instance.getModifiers());
            for (AttributeModifier modifier : existing) {
                ResourceLocation modifierId = modifier.id();
                if (isWorldAwakenedOwnedModifier(modifierId)) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    void apply(ServerPlayer player, AscensionRewardDefinition reward, Set<String> suppressedComponentKeys) {
        Set<String> suppressed = suppressedComponentKeys == null ? Set.of() : new LinkedHashSet<>(suppressedComponentKeys);
        for (int index = 0; index < reward.components().size(); index++) {
            AscensionComponentDefinition component = reward.components().get(index);
            if (!component.enabled()) {
                continue;
            }
            if (suppressed.contains(WorldAwakenedAscensionComponentKeys.componentKey(index, component))) {
                continue;
            }
            String typePath = component.type().getPath().toLowerCase(Locale.ROOT);
            switch (typePath) {
                case "max_health_bonus" -> applyFlatModifier(player, Attributes.MAX_HEALTH, component, reward.id(), index);
                case "armor_bonus" -> applyFlatModifier(player, Attributes.ARMOR, component, reward.id(), index);
                case "armor_toughness_bonus" -> applyFlatModifier(player, Attributes.ARMOR_TOUGHNESS, component, reward.id(), index);
                case "attack_damage_bonus" -> applyFlatModifier(player, Attributes.ATTACK_DAMAGE, component, reward.id(), index);
                case "movement_speed_bonus" -> applyFlatModifier(player, Attributes.MOVEMENT_SPEED, component, reward.id(), index);
                case "knockback_resistance_bonus" -> applyFlatModifier(player, Attributes.KNOCKBACK_RESISTANCE, component, reward.id(), index);
                case "luck_bonus" -> applyFlatModifier(player, Attributes.LUCK, component, reward.id(), index);
                case "fire_resistance_passive" -> applyOwnedCarrier(
                        player,
                        reward.id(),
                        index,
                        WorldAwakenedOwnedCarrierIds.FIRE_RESISTANCE_PASSIVE);
                case "night_vision_passive" -> applyOwnedCarrier(
                        player,
                        reward.id(),
                        index,
                        WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE);
                default -> logComponentFailure(
                        player,
                        reward.id(),
                        component.type(),
                        component.type().getPath().endsWith("_passive")
                                ? WorldAwakenedDiagnosticCodes.REWARD_CARRIER_TYPE_MISSING
                                : WorldAwakenedDiagnosticCodes.REWARD_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        "no safe WA-owned runtime surface is implemented for this component type");
            }
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static void applyFlatModifier(
            ServerPlayer player,
            Holder<Attribute> attribute,
            AscensionComponentDefinition component,
            ResourceLocation rewardId,
            int index) {
        if (!component.parameters().has("amount")
                || !component.parameters().get("amount").isJsonPrimitive()
                || !component.parameters().get("amount").getAsJsonPrimitive().isNumber()) {
            logComponentFailure(
                    player,
                    rewardId,
                    component.type(),
                    WorldAwakenedDiagnosticCodes.ASCENSION_RECONCILE_COMPONENT_PARAM_INVALID,
                    "missing or non-numeric parameters.amount");
            return;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            logComponentFailure(
                    player,
                    rewardId,
                    component.type(),
                    WorldAwakenedDiagnosticCodes.PLAYER_ATTRIBUTE_SURFACE_MISSING,
                    "attribute " + attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>") + " is unavailable");
            return;
        }

        double amount = component.parameters().get("amount").getAsDouble();
        ResourceLocation modifierId = stableOwnedKey(rewardId, index);
        instance.removeModifier(modifierId);
        instance.addPermanentModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void applyOwnedCarrier(
            ServerPlayer player,
            ResourceLocation rewardId,
            int componentIndex,
            ResourceLocation carrierId) {
        WorldAwakenedOwnedCarrierService.applyOwnedCarrier(
                player,
                stableOwnedKey(rewardId, componentIndex),
                carrierId);
    }

    private static ResourceLocation stableOwnedKey(ResourceLocation rewardId, int index) {
        return ResourceLocation.fromNamespaceAndPath(
                WorldAwakenedConstants.MOD_ID,
                "ascension/" + rewardId.getNamespace() + "/" + rewardId.getPath() + "/" + index);
    }

    public static Set<Holder<Attribute>> managedAttributes() {
        return MANAGED_ATTRIBUTES;
    }

    public static Optional<Holder<Attribute>> managedAttributeForComponent(ResourceLocation componentType) {
        return switch (componentType.getPath().toLowerCase(Locale.ROOT)) {
            case "max_health_bonus" -> Optional.of(Attributes.MAX_HEALTH);
            case "armor_bonus" -> Optional.of(Attributes.ARMOR);
            case "armor_toughness_bonus" -> Optional.of(Attributes.ARMOR_TOUGHNESS);
            case "attack_damage_bonus" -> Optional.of(Attributes.ATTACK_DAMAGE);
            case "movement_speed_bonus" -> Optional.of(Attributes.MOVEMENT_SPEED);
            case "knockback_resistance_bonus" -> Optional.of(Attributes.KNOCKBACK_RESISTANCE);
            case "luck_bonus" -> Optional.of(Attributes.LUCK);
            default -> Optional.empty();
        };
    }

    public static Optional<ResourceLocation> ownedCarrierForComponent(ResourceLocation componentType) {
        return switch (componentType.getPath().toLowerCase(Locale.ROOT)) {
            case "fire_resistance_passive" -> Optional.of(WorldAwakenedOwnedCarrierIds.FIRE_RESISTANCE_PASSIVE);
            case "night_vision_passive" -> Optional.of(WorldAwakenedOwnedCarrierIds.NIGHT_VISION_PASSIVE);
            default -> Optional.empty();
        };
    }

    public static ResourceLocation stableOwnedKeyForComponent(ResourceLocation rewardId, int index) {
        return stableOwnedKey(rewardId, index);
    }

    public static boolean isWorldAwakenedOwnedModifier(ResourceLocation modifierId) {
        return modifierId.getNamespace().equals(WorldAwakenedConstants.MOD_ID)
                && modifierId.getPath().startsWith("ascension/");
    }

    private static void logComponentFailure(
            ServerPlayer player,
            ResourceLocation rewardId,
            ResourceLocation componentType,
            String code,
            String detail) {
        WorldAwakenedLog.warn(
                LOGGER,
                WorldAwakenedLogCategory.PIPELINE,
                "Skipped ascension component during reconcile: code={} player={} reward={} component={} detail={}",
                code,
                player.getGameProfile().getName(),
                rewardId,
                componentType,
                detail);
    }
}

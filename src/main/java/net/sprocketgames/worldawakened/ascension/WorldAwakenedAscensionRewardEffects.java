package net.sprocketgames.worldawakened.ascension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;
import net.sprocketgames.worldawakened.data.definition.AscensionComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;

final class WorldAwakenedAscensionRewardEffects {
    private static final int PASSIVE_EFFECT_DURATION_TICKS = 20 * 15;
    private static final Set<Holder<Attribute>> MANAGED_ATTRIBUTES = Set.of(
            Attributes.MAX_HEALTH,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.ATTACK_DAMAGE,
            Attributes.MOVEMENT_SPEED,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.LUCK);

    void clear(ServerPlayer player) {
        for (Holder<Attribute> attribute : MANAGED_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            List<AttributeModifier> existing = new ArrayList<>(instance.getModifiers());
            for (AttributeModifier modifier : existing) {
                ResourceLocation modifierId = modifier.id();
                if (modifierId.getNamespace().equals(WorldAwakenedConstants.MOD_ID)
                        && modifierId.getPath().startsWith("ascension/")) {
                    instance.removeModifier(modifier);
                }
            }
        }
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.removeEffect(MobEffects.NIGHT_VISION);
    }

    void apply(ServerPlayer player, AscensionRewardDefinition reward) {
        for (int index = 0; index < reward.components().size(); index++) {
            AscensionComponentDefinition component = reward.components().get(index);
            if (!component.enabled()) {
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
                case "fire_resistance_passive" -> applyPassiveEffect(player, MobEffects.FIRE_RESISTANCE);
                case "night_vision_passive" -> applyPassiveEffect(player, MobEffects.NIGHT_VISION);
                default -> {
                    // Unsupported component behavior is intentionally inert until dedicated handlers are implemented.
                }
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
            return;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        double amount = component.parameters().get("amount").getAsDouble();
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(
                WorldAwakenedConstants.MOD_ID,
                "ascension/" + rewardId.getNamespace() + "/" + rewardId.getPath() + "/" + index);
        instance.removeModifier(modifierId);
        instance.addPermanentModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void applyPassiveEffect(ServerPlayer player, Holder<MobEffect> effect) {
        player.addEffect(new MobEffectInstance(effect, PASSIVE_EFFECT_DURATION_TICKS, 0, true, false, false));
    }
}

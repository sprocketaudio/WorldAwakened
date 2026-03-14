package net.sprocketgames.worldawakened.mutator.component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedGlowStyleState;
import net.sprocketgames.worldawakened.mutator.WorldAwakenedVisualParticleEmitters;

public final class WorldAwakenedMutationComponentRegistry {
    private static final Map<ResourceLocation, WorldAwakenedMutationComponentType> TYPES = new LinkedHashMap<>();
    private static final net.minecraft.core.HolderLookup.Provider VANILLA_LOOKUP = VanillaRegistries.createLookup();

    static {
        register(type("max_health_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("max_health_multiplier", WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("attack_damage_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("attack_damage_multiplier", WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("armor_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("armor_multiplier", WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("armor_toughness_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("movement_speed_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("movement_speed_multiplier", WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("follow_range_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("knockback_resistance_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("wall_sense"));
        register(type("target_range_bonus", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("pursuit_speed_boost", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("anti_kite_behavior"));
        register(type("debuff_resistance", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("damage_type_resistance", WorldAwakenedMutationComponentRegistry::requireDamageType));
        register(type("temporary_shield", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("equip_item", true, WorldAwakenedMutationComponentRegistry::requireEquipItem));
        register(type("projectile_resistance", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("on_hit_effect", WorldAwakenedMutationComponentRegistry::requireEffect));
        register(type("life_steal", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("reinforcement_summon", WorldAwakenedMutationComponentRegistry::requireSummonTarget));
        register(type("summon_cooldown", WorldAwakenedMutationComponentRegistry::requireCooldownSeconds));
        register(type("summon_cap", WorldAwakenedMutationComponentRegistry::requireSummonCap));
        register(type("death_spawn", WorldAwakenedMutationComponentRegistry::requireSummonTarget));
        register(type("burst_movement", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("projectile_modifier"));
        register(type("fire_package", Set.of(id("frost_package"), id("lightning_package"), id("poison_package"))));
        register(type("frost_package", Set.of(id("fire_package"), id("lightning_package"), id("poison_package"))));
        register(type("lightning_package", Set.of(id("fire_package"), id("frost_package"), id("poison_package"))));
        register(type("poison_package", Set.of(id("fire_package"), id("frost_package"), id("lightning_package"))));
        register(type("damage_aura", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("death_explosion", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("retaliation_thorns", WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("glow_style", WorldAwakenedGlowStyleState::validateParameters));
        register(type("effect_particles", true, WorldAwakenedVisualParticleEmitters::validateEffectParticles));
        register(type("ambient_particles", true, WorldAwakenedVisualParticleEmitters::validateAmbientParticles));
    }

    private WorldAwakenedMutationComponentRegistry() {
    }

    public static Optional<WorldAwakenedMutationComponentType> lookup(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id));
        }
    }

    public static Set<ResourceLocation> registeredIds() {
        synchronized (TYPES) {
            return Set.copyOf(TYPES.keySet());
        }
    }

    public static RegistrationResult register(WorldAwakenedMutationComponentType componentType) {
        Objects.requireNonNull(componentType, "componentType");
        Objects.requireNonNull(componentType.id(), "componentType.id");
        Objects.requireNonNull(componentType.parameterValidator(), "componentType.parameterValidator");
        synchronized (TYPES) {
            if (TYPES.containsKey(componentType.id())) {
                return RegistrationResult.ALREADY_REGISTERED;
            }
            TYPES.put(componentType.id(), componentType);
            return RegistrationResult.REGISTERED;
        }
    }

    private static WorldAwakenedMutationComponentType type(String path) {
        return type(path, false, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(String path, boolean allowDuplicates) {
        return type(path, allowDuplicates, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(String path, Set<ResourceLocation> incompatibleWith) {
        return type(path, false, incompatibleWith, parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(
            String path,
            WorldAwakenedMutationComponentType.ParameterValidator validator) {
        return type(path, false, Set.of(), validator);
    }

    private static WorldAwakenedMutationComponentType type(
            String path,
            boolean allowDuplicates,
            WorldAwakenedMutationComponentType.ParameterValidator validator) {
        return type(path, allowDuplicates, Set.of(), validator);
    }

    private static WorldAwakenedMutationComponentType type(
            String path,
            boolean allowDuplicates,
            Set<ResourceLocation> incompatibleWith,
            WorldAwakenedMutationComponentType.ParameterValidator validator) {
        return new WorldAwakenedMutationComponentType(
                id(path),
                allowDuplicates,
                Set.copyOf(incompatibleWith),
                validator);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("worldawakened", path);
    }

    private static Optional<String> requireAmount(JsonObject parameters) {
        return requireNumber(parameters, "amount");
    }

    private static Optional<String> requireMultiplier(JsonObject parameters) {
        Optional<String> error = requireNumber(parameters, "multiplier");
        if (error.isPresent()) {
            return error;
        }
        if (parameters.get("multiplier").getAsDouble() <= 0.0D) {
            return Optional.of("parameters.multiplier must be > 0");
        }
        return Optional.empty();
    }

    private static Optional<String> requireDamageType(JsonObject parameters) {
        if (!parameters.has("damage_type") || !parameters.get("damage_type").isJsonPrimitive()) {
            return Optional.of("parameters.damage_type is required");
        }
        String value = parameters.getAsJsonPrimitive("damage_type").getAsString();
        return value.isBlank() ? Optional.of("parameters.damage_type must not be blank") : Optional.empty();
    }

    private static Optional<String> requireEffect(JsonObject parameters) {
        if (!parameters.has("effect") || !parameters.get("effect").isJsonPrimitive()) {
            return Optional.of("parameters.effect is required");
        }
        String value = parameters.getAsJsonPrimitive("effect").getAsString();
        return value.isBlank() ? Optional.of("parameters.effect must not be blank") : Optional.empty();
    }

    private static Optional<String> requireSummonTarget(JsonObject parameters) {
        boolean hasEntity = parameters.has("entity") && parameters.get("entity").isJsonPrimitive()
                && !parameters.getAsJsonPrimitive("entity").getAsString().isBlank();
        boolean hasTag = parameters.has("entity_tag") && parameters.get("entity_tag").isJsonPrimitive()
                && !parameters.getAsJsonPrimitive("entity_tag").getAsString().isBlank();
        return hasEntity || hasTag
                ? Optional.empty()
                : Optional.of("parameters.entity or parameters.entity_tag is required");
    }

    private static Optional<String> requireCooldownSeconds(JsonObject parameters) {
        Optional<String> error = requireNumber(parameters, "seconds");
        if (error.isPresent()) {
            return error;
        }
        if (parameters.get("seconds").getAsDouble() <= 0.0D) {
            return Optional.of("parameters.seconds must be > 0");
        }
        return Optional.empty();
    }

    private static Optional<String> requireSummonCap(JsonObject parameters) {
        Optional<String> error = requireNumber(parameters, "max");
        if (error.isPresent()) {
            return error;
        }
        if (parameters.get("max").getAsInt() < 1) {
            return Optional.of("parameters.max must be >= 1");
        }
        return Optional.empty();
    }

    private static Optional<String> requireEquipItem(JsonObject parameters) {
        if (!parameters.has("item") || !parameters.get("item").isJsonPrimitive()) {
            return Optional.of("parameters.item is required");
        }
        String rawItemId = parameters.getAsJsonPrimitive("item").getAsString();
        if (rawItemId.isBlank()) {
            return Optional.of("parameters.item must not be blank");
        }
        ResourceLocation itemId = ResourceLocation.tryParse(rawItemId);
        if (itemId == null) {
            return Optional.of("parameters.item must be a valid resource location");
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || BuiltInRegistries.ITEM.getKey(item) == BuiltInRegistries.ITEM.getDefaultKey()) {
            return Optional.of("parameters.item must reference a registered non-air item");
        }

        if (parameters.has("slot")) {
            if (!parameters.get("slot").isJsonPrimitive()) {
                return Optional.of("parameters.slot must be a string");
            }
            String rawSlot = parameters.getAsJsonPrimitive("slot").getAsString();
            if (rawSlot.isBlank()) {
                return Optional.of("parameters.slot must not be blank");
            }
            if (!rawSlot.equalsIgnoreCase("auto") && parseEquipmentSlot(rawSlot).isEmpty()) {
                return Optional.of("parameters.slot must be one of auto, mainhand, offhand, head, chest, legs, feet, body");
            }
        }

        if (parameters.has("drop_chance")) {
            Optional<String> error = requireNumber(parameters, "drop_chance");
            if (error.isPresent()) {
                return error;
            }
            double dropChance = parameters.get("drop_chance").getAsDouble();
            if (dropChance < 0.0D || dropChance > 1.0D) {
                return Optional.of("parameters.drop_chance must be between 0 and 1");
            }
        }

        if (!parameters.has("enchantments")) {
            return Optional.empty();
        }
        JsonElement rawEnchantments = parameters.get("enchantments");
        if (!rawEnchantments.isJsonArray()) {
            return Optional.of("parameters.enchantments must be an array");
        }
        JsonArray enchantments = rawEnchantments.getAsJsonArray();
        for (int index = 0; index < enchantments.size(); index++) {
            JsonElement rawEntry = enchantments.get(index);
            if (!rawEntry.isJsonObject()) {
                return Optional.of("parameters.enchantments[" + index + "] must be an object");
            }
            JsonObject entry = rawEntry.getAsJsonObject();
            if (!entry.has("id") || !entry.get("id").isJsonPrimitive()) {
                return Optional.of("parameters.enchantments[" + index + "].id is required");
            }
            String rawEnchantmentId = entry.getAsJsonPrimitive("id").getAsString();
            if (rawEnchantmentId.isBlank()) {
                return Optional.of("parameters.enchantments[" + index + "].id must not be blank");
            }
            ResourceLocation enchantmentId = ResourceLocation.tryParse(rawEnchantmentId);
            if (enchantmentId == null) {
                return Optional.of("parameters.enchantments[" + index + "].id must be a valid resource location");
            }
            if (enchantmentLookup().get(ResourceKey.create(Registries.ENCHANTMENT, enchantmentId)).isEmpty()) {
                return Optional.of("parameters.enchantments[" + index + "].id must reference a registered enchantment");
            }
            if (!entry.has("level") || !entry.get("level").isJsonPrimitive() || !entry.getAsJsonPrimitive("level").isNumber()) {
                return Optional.of("parameters.enchantments[" + index + "].level must be numeric");
            }
            if (entry.get("level").getAsInt() < 1) {
                return Optional.of("parameters.enchantments[" + index + "].level must be >= 1");
            }
        }

        return Optional.empty();
    }

    private static net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> enchantmentLookup() {
        return VANILLA_LOOKUP.lookupOrThrow(Registries.ENCHANTMENT);
    }

    private static Optional<String> requireNumber(JsonObject parameters, String key) {
        if (!parameters.has(key) || !parameters.get(key).isJsonPrimitive() || !parameters.getAsJsonPrimitive(key).isNumber()) {
            return Optional.of("parameters." + key + " must be numeric");
        }
        return Optional.empty();
    }

    private static Optional<EquipmentSlot> parseEquipmentSlot(String raw) {
        return switch (raw.toLowerCase()) {
            case "mainhand", "main_hand" -> Optional.of(EquipmentSlot.MAINHAND);
            case "offhand", "off_hand" -> Optional.of(EquipmentSlot.OFFHAND);
            case "head", "helmet" -> Optional.of(EquipmentSlot.HEAD);
            case "chest", "chestplate" -> Optional.of(EquipmentSlot.CHEST);
            case "legs", "leggings" -> Optional.of(EquipmentSlot.LEGS);
            case "feet", "boots" -> Optional.of(EquipmentSlot.FEET);
            case "body" -> Optional.of(EquipmentSlot.BODY);
            default -> Optional.empty();
        };
    }

    public enum RegistrationResult {
        REGISTERED,
        ALREADY_REGISTERED
    }
}

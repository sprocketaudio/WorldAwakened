package net.sprocketgames.worldawakened.mutator.component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedMutationComponentRegistry {
    private static final Map<ResourceLocation, WorldAwakenedMutationComponentType> TYPES = new LinkedHashMap<>();

    static {
        register(type("max_health_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("max_health_multiplier", 2, WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("attack_damage_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("attack_damage_multiplier", 2, WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("armor_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("armor_multiplier", 2, WorldAwakenedMutationComponentRegistry::requireMultiplier));
        register(type("armor_toughness_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("movement_speed_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("follow_range_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("knockback_resistance_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("wall_sense", 1));
        register(type("target_range_bonus", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("pursuit_speed_boost", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("anti_kite_behavior", 1));
        register(type("debuff_resistance", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("damage_type_resistance", 1, WorldAwakenedMutationComponentRegistry::requireDamageType));
        register(type("temporary_shield", 2, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("projectile_resistance", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("on_hit_effect", 1, WorldAwakenedMutationComponentRegistry::requireEffect));
        register(type("life_steal", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("reinforcement_summon", 3, WorldAwakenedMutationComponentRegistry::requireSummonTarget));
        register(type("summon_cooldown", 1, WorldAwakenedMutationComponentRegistry::requireCooldownSeconds));
        register(type("summon_cap", 1, WorldAwakenedMutationComponentRegistry::requireSummonCap));
        register(type("death_spawn", 3, WorldAwakenedMutationComponentRegistry::requireSummonTarget));
        register(type("burst_movement", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("projectile_modifier", 2));
        register(type("fire_package", 2, Set.of(id("frost_package"), id("lightning_package"), id("poison_package"))));
        register(type("frost_package", 2, Set.of(id("fire_package"), id("lightning_package"), id("poison_package"))));
        register(type("lightning_package", 2, Set.of(id("fire_package"), id("frost_package"), id("poison_package"))));
        register(type("poison_package", 2, Set.of(id("fire_package"), id("frost_package"), id("lightning_package"))));
        register(type("damage_aura", 2, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("death_explosion", 2, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("retaliation_thorns", 1, WorldAwakenedMutationComponentRegistry::requireAmount));
        register(type("glow_style", 1));
        register(type("ambient_particles", 1, true));
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

    private static WorldAwakenedMutationComponentType type(String path, int budgetCost) {
        return type(path, budgetCost, false, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(String path, int budgetCost, boolean allowDuplicates) {
        return type(path, budgetCost, allowDuplicates, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(String path, int budgetCost, Set<ResourceLocation> incompatibleWith) {
        return type(path, budgetCost, false, incompatibleWith, parameters -> Optional.empty());
    }

    private static WorldAwakenedMutationComponentType type(
            String path,
            int budgetCost,
            WorldAwakenedMutationComponentType.ParameterValidator validator) {
        return type(path, budgetCost, false, Set.of(), validator);
    }

    private static WorldAwakenedMutationComponentType type(
            String path,
            int budgetCost,
            boolean allowDuplicates,
            Set<ResourceLocation> incompatibleWith,
            WorldAwakenedMutationComponentType.ParameterValidator validator) {
        return new WorldAwakenedMutationComponentType(
                id(path),
                allowDuplicates,
                Math.max(0, budgetCost),
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

    private static Optional<String> requireNumber(JsonObject parameters, String key) {
        if (!parameters.has(key) || !parameters.get(key).isJsonPrimitive() || !parameters.getAsJsonPrimitive(key).isNumber()) {
            return Optional.of("parameters." + key + " must be numeric");
        }
        return Optional.empty();
    }

    public enum RegistrationResult {
        REGISTERED,
        ALREADY_REGISTERED
    }
}

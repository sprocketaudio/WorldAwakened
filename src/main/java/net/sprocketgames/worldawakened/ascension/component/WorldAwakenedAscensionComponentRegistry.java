package net.sprocketgames.worldawakened.ascension.component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedAscensionComponentRegistry {
    private static final Map<ResourceLocation, WorldAwakenedAscensionComponentType> TYPES = new LinkedHashMap<>();

    static {
        register(type("max_health_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("armor_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("armor_toughness_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("attack_damage_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("movement_speed_bonus", true, WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("knockback_resistance_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("luck_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("fire_resistance_passive"));
        register(type("fall_damage_reduction", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("debuff_resistance", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("damage_type_resistance", WorldAwakenedAscensionComponentRegistry::requireDamageType));
        register(type("healing_efficiency_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("extra_revival_buffer", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("step_height_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("sprint_efficiency", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("jump_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("water_mobility_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("night_vision_passive", true));
        register(type("hostile_wall_sense"));
        register(type("loot_detection"));
        register(type("crit_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("attack_reach_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("on_hit_effect_passive", WorldAwakenedAscensionComponentRegistry::requireEffect));
        register(type("life_steal_minor", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("xp_gain_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("loot_quality_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("invasion_reward_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("mutation_resistance_bonus", WorldAwakenedAscensionComponentRegistry::requireAmount));
        register(type("elite_detection"));
    }

    private WorldAwakenedAscensionComponentRegistry() {
    }

    public static Optional<WorldAwakenedAscensionComponentType> lookup(ResourceLocation id) {
        synchronized (TYPES) {
            return Optional.ofNullable(TYPES.get(id));
        }
    }

    public static Set<ResourceLocation> registeredIds() {
        synchronized (TYPES) {
            return Set.copyOf(TYPES.keySet());
        }
    }

    public static RegistrationResult register(WorldAwakenedAscensionComponentType componentType) {
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

    private static WorldAwakenedAscensionComponentType type(String path) {
        return type(path, false, false, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedAscensionComponentType type(String path, boolean suppressibleIndividually) {
        return type(path, false, suppressibleIndividually, Set.of(), parameters -> Optional.empty());
    }

    private static WorldAwakenedAscensionComponentType type(
            String path,
            WorldAwakenedAscensionComponentType.ParameterValidator validator) {
        return type(path, false, false, Set.of(), validator);
    }

    private static WorldAwakenedAscensionComponentType type(
            String path,
            boolean suppressibleIndividually,
            WorldAwakenedAscensionComponentType.ParameterValidator validator) {
        return type(path, false, suppressibleIndividually, Set.of(), validator);
    }

    private static WorldAwakenedAscensionComponentType type(
            String path,
            boolean allowDuplicates,
            boolean suppressibleIndividually,
            Set<ResourceLocation> incompatibleWith,
            WorldAwakenedAscensionComponentType.ParameterValidator validator) {
        return new WorldAwakenedAscensionComponentType(
                id(path),
                allowDuplicates,
                suppressibleIndividually,
                Set.copyOf(incompatibleWith),
                validator);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("worldawakened", path);
    }

    private static Optional<String> requireAmount(JsonObject parameters) {
        if (!parameters.has("amount") || !parameters.get("amount").isJsonPrimitive()
                || !parameters.getAsJsonPrimitive("amount").isNumber()) {
            return Optional.of("parameters.amount must be numeric");
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

    public enum RegistrationResult {
        REGISTERED,
        ALREADY_REGISTERED
    }
}

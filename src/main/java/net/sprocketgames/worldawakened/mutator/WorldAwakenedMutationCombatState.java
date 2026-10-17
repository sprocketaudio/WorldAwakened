package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistent, already-compiled combat behavior attached to a mutated mob.
 * Combat event paths read this bounded state and never parse datapack JSON.
 */
public final class WorldAwakenedMutationCombatState {
    static final String ON_HIT_EFFECTS = "WA_MUTATION_ON_HIT_EFFECTS";
    static final String LIFE_STEAL_RATIO = "WA_MUTATION_LIFE_STEAL_RATIO";
    static final String DEATH_EXPLOSION_POWER = "WA_MUTATION_DEATH_EXPLOSION_POWER";
    static final String DEATH_EXPLOSION_TRIGGERED = "WA_MUTATION_DEATH_EXPLOSION_TRIGGERED";

    public static final int DEFAULT_EFFECT_DURATION_TICKS = 100;
    public static final int MAX_EFFECT_DURATION_TICKS = 12_000;
    public static final int MAX_EFFECT_AMPLIFIER = 255;
    public static final int MAX_ON_HIT_EFFECTS = 8;
    public static final double MAX_LIFE_STEAL_RATIO = 1.0D;
    public static final double MAX_DEATH_EXPLOSION_POWER = 8.0D;

    private WorldAwakenedMutationCombatState() {
    }

    public static Optional<String> appendOnHitEffect(CompoundTag tag, JsonObject parameters) {
        Optional<OnHitEffect> parsed = parseOnHitEffect(parameters);
        if (parsed.isEmpty()) {
            return Optional.of("on_hit_effect parameters are invalid");
        }
        ListTag effects = tag.contains(ON_HIT_EFFECTS, Tag.TAG_LIST)
                ? tag.getList(ON_HIT_EFFECTS, Tag.TAG_COMPOUND)
                : new ListTag();
        if (effects.size() >= MAX_ON_HIT_EFFECTS) {
            return Optional.of("on-hit effect runtime budget exceeded (max=" + MAX_ON_HIT_EFFECTS + ")");
        }
        OnHitEffect effect = parsed.get();
        CompoundTag entry = new CompoundTag();
        entry.putString("effect", effect.effectId().toString());
        entry.putInt("duration_ticks", effect.durationTicks());
        entry.putInt("amplifier", effect.amplifier());
        entry.putDouble("chance", effect.chance());
        effects.add(entry);
        tag.put(ON_HIT_EFFECTS, effects);
        return Optional.empty();
    }

    public static Optional<String> appendLifeSteal(CompoundTag tag, JsonObject parameters) {
        OptionalDouble amount = boundedNumber(parameters, "amount", 0.0D, MAX_LIFE_STEAL_RATIO);
        if (amount.isEmpty() || amount.getAsDouble() <= 0.0D) {
            return Optional.of("parameters.amount must be > 0 and <= " + MAX_LIFE_STEAL_RATIO);
        }
        double current = tag.contains(LIFE_STEAL_RATIO, Tag.TAG_DOUBLE) ? tag.getDouble(LIFE_STEAL_RATIO) : 0.0D;
        tag.putDouble(LIFE_STEAL_RATIO, Math.min(MAX_LIFE_STEAL_RATIO, current + amount.getAsDouble()));
        return Optional.empty();
    }

    public static Optional<String> appendDeathExplosion(CompoundTag tag, JsonObject parameters) {
        OptionalDouble amount = boundedNumber(parameters, "amount", 0.0D, MAX_DEATH_EXPLOSION_POWER);
        if (amount.isEmpty() || amount.getAsDouble() <= 0.0D) {
            return Optional.of("parameters.amount must be > 0 and <= " + MAX_DEATH_EXPLOSION_POWER);
        }
        double current = tag.contains(DEATH_EXPLOSION_POWER, Tag.TAG_DOUBLE)
                ? tag.getDouble(DEATH_EXPLOSION_POWER)
                : 0.0D;
        tag.putDouble(DEATH_EXPLOSION_POWER, Math.max(current, amount.getAsDouble()));
        return Optional.empty();
    }

    public static List<OnHitEffect> onHitEffects(CompoundTag tag) {
        if (!tag.contains(ON_HIT_EFFECTS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag stored = tag.getList(ON_HIT_EFFECTS, Tag.TAG_COMPOUND);
        List<OnHitEffect> effects = new ArrayList<>(Math.min(stored.size(), MAX_ON_HIT_EFFECTS));
        for (int index = 0; index < stored.size() && effects.size() < MAX_ON_HIT_EFFECTS; index++) {
            CompoundTag entry = stored.getCompound(index);
            ResourceLocation effectId = ResourceLocation.tryParse(entry.getString("effect"));
            if (effectId == null) {
                continue;
            }
            int durationTicks = clamp(entry.getInt("duration_ticks"), 1, MAX_EFFECT_DURATION_TICKS);
            int amplifier = clamp(entry.getInt("amplifier"), 0, MAX_EFFECT_AMPLIFIER);
            double chance = clamp(entry.getDouble("chance"), 0.0D, 1.0D);
            effects.add(new OnHitEffect(effectId, durationTicks, amplifier, chance));
        }
        return List.copyOf(effects);
    }

    public static double lifeStealRatio(CompoundTag tag) {
        if (!tag.contains(LIFE_STEAL_RATIO, Tag.TAG_DOUBLE)) {
            return 0.0D;
        }
        return clamp(tag.getDouble(LIFE_STEAL_RATIO), 0.0D, MAX_LIFE_STEAL_RATIO);
    }

    public static boolean hasDamageDealtAbility(CompoundTag tag) {
        return tag.contains(ON_HIT_EFFECTS, Tag.TAG_LIST) || lifeStealRatio(tag) > 0.0D;
    }

    public static OptionalDouble consumeDeathExplosionPower(CompoundTag tag) {
        if (deathExplosionTriggered(tag)) {
            return OptionalDouble.empty();
        }
        OptionalDouble power = deathExplosionPower(tag);
        if (power.isEmpty()) {
            return OptionalDouble.empty();
        }
        tag.putBoolean(DEATH_EXPLOSION_TRIGGERED, true);
        return power;
    }

    public static OptionalDouble deathExplosionPower(CompoundTag tag) {
        if (!tag.contains(DEATH_EXPLOSION_POWER, Tag.TAG_DOUBLE)) {
            return OptionalDouble.empty();
        }
        double power = clamp(tag.getDouble(DEATH_EXPLOSION_POWER), 0.0D, MAX_DEATH_EXPLOSION_POWER);
        return power > 0.0D ? OptionalDouble.of(power) : OptionalDouble.empty();
    }

    public static boolean deathExplosionTriggered(CompoundTag tag) {
        return tag.getBoolean(DEATH_EXPLOSION_TRIGGERED);
    }

    public static Optional<OnHitEffect> parseOnHitEffect(JsonObject parameters) {
        if (!parameters.has("effect") || !parameters.get("effect").isJsonPrimitive()) {
            return Optional.empty();
        }
        ResourceLocation effectId = ResourceLocation.tryParse(parameters.get("effect").getAsString());
        if (effectId == null) {
            return Optional.empty();
        }
        if (!validOptionalInteger(parameters, "duration_ticks")
                || !validOptionalInteger(parameters, "amplifier")
                || !validOptionalNumber(parameters, "chance")) {
            return Optional.empty();
        }
        int durationTicks = optionalInt(parameters, "duration_ticks", DEFAULT_EFFECT_DURATION_TICKS);
        int amplifier = optionalInt(parameters, "amplifier", 0);
        double chance = optionalDouble(parameters, "chance", 1.0D);
        if (durationTicks < 1 || durationTicks > MAX_EFFECT_DURATION_TICKS
                || amplifier < 0 || amplifier > MAX_EFFECT_AMPLIFIER
                || !Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            return Optional.empty();
        }
        return Optional.of(new OnHitEffect(effectId, durationTicks, amplifier, chance));
    }

    private static boolean validOptionalInteger(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return true;
        }
        if (!parameters.get(key).isJsonPrimitive() || !parameters.getAsJsonPrimitive(key).isNumber()) {
            return false;
        }
        double value = parameters.get(key).getAsDouble();
        return Double.isFinite(value) && value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static boolean validOptionalNumber(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return true;
        }
        return parameters.get(key).isJsonPrimitive()
                && parameters.getAsJsonPrimitive(key).isNumber()
                && Double.isFinite(parameters.get(key).getAsDouble());
    }

    private static OptionalDouble boundedNumber(JsonObject parameters, String key, double minimum, double maximum) {
        if (!parameters.has(key)
                || !parameters.get(key).isJsonPrimitive()
                || !parameters.getAsJsonPrimitive(key).isNumber()) {
            return OptionalDouble.empty();
        }
        double value = parameters.get(key).getAsDouble();
        return Double.isFinite(value) && value >= minimum && value <= maximum
                ? OptionalDouble.of(value)
                : OptionalDouble.empty();
    }

    private static int optionalInt(JsonObject parameters, String key, int fallback) {
        return parameters.has(key)
                && parameters.get(key).isJsonPrimitive()
                && parameters.getAsJsonPrimitive(key).isNumber()
                        ? parameters.get(key).getAsInt()
                        : fallback;
    }

    private static double optionalDouble(JsonObject parameters, String key, double fallback) {
        return parameters.has(key)
                && parameters.get(key).isJsonPrimitive()
                && parameters.getAsJsonPrimitive(key).isNumber()
                        ? parameters.get(key).getAsDouble()
                        : fallback;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record OnHitEffect(ResourceLocation effectId, int durationTicks, int amplifier, double chance) {
    }
}

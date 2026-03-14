package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.joml.Vector3f;

public final class WorldAwakenedVisualParticleEmitters {
    public static final String WA_VISUAL_PARTICLE_EMITTERS = "WA_VISUAL_PARTICLE_EMITTERS";

    private static final int DEFAULT_COUNT = 4;
    private static final double DEFAULT_OFFSET_X = 0.25D;
    private static final double DEFAULT_OFFSET_Y = 0.40D;
    private static final double DEFAULT_OFFSET_Z = 0.25D;
    private static final double DEFAULT_SPEED = 0.01D;
    private static final int DEFAULT_INTERVAL_TICKS = 12;

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 32;
    private static final double MIN_OFFSET = 0.0D;
    private static final double MAX_OFFSET = 4.0D;
    private static final double MIN_SPEED = 0.0D;
    private static final double MAX_SPEED = 4.0D;
    private static final int MIN_INTERVAL_TICKS = 1;
    private static final int MAX_INTERVAL_TICKS = 200;
    private static final int EFFECT_PARTICLE_ALPHA = 255;
    private static final ResourceLocation DUST_PARTICLE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "dust");
    private static final int DEFAULT_DUST_COLOR_RGB = 0xFF0000;
    private static final float DEFAULT_DUST_SIZE = 1.0F;
    private static final float MIN_DUST_SIZE = 0.01F;
    private static final float MAX_DUST_SIZE = 4.0F;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private WorldAwakenedVisualParticleEmitters() {
    }

    public static Optional<String> validateAmbientParticles(JsonObject parameters) {
        return parseAmbientParticlesInternal(parameters).error();
    }

    public static Optional<String> validateEffectParticles(JsonObject parameters) {
        return parseEffectParticlesInternal(parameters).error();
    }

    public static Optional<EmitterDefinition> parseAmbientParticles(JsonObject parameters) {
        ParseResult result = parseAmbientParticlesInternal(parameters);
        return result.error().isPresent() ? Optional.empty() : result.emitter();
    }

    public static Optional<EmitterDefinition> parseEffectParticles(JsonObject parameters) {
        ParseResult result = parseEffectParticlesInternal(parameters);
        return result.error().isPresent() ? Optional.empty() : result.emitter();
    }

    public static void appendEmitter(CompoundTag tag, EmitterDefinition emitter) {
        ListTag emitters = tag.contains(WA_VISUAL_PARTICLE_EMITTERS, Tag.TAG_LIST)
                ? tag.getList(WA_VISUAL_PARTICLE_EMITTERS, Tag.TAG_COMPOUND)
                : new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("kind", emitter.kind().serializedName());
        entry.putString("id", emitter.registryId().toString());
        entry.putInt("count", emitter.count());
        entry.putDouble("offset_x", emitter.offsetX());
        entry.putDouble("offset_y", emitter.offsetY());
        entry.putDouble("offset_z", emitter.offsetZ());
        entry.putDouble("speed", emitter.speed());
        entry.putInt("interval_ticks", emitter.intervalTicks());
        emitter.colorOverrideRgb().ifPresent(color -> entry.putInt("color_rgb", sanitizeColor(color)));
        emitter.sizeOverride().ifPresent(size -> entry.putFloat("size", clampFloat(size, MIN_DUST_SIZE, MAX_DUST_SIZE)));
        emitters.add(entry);
        tag.put(WA_VISUAL_PARTICLE_EMITTERS, emitters);
    }

    public static List<EmitterDefinition> readEmitters(CompoundTag tag) {
        if (!tag.contains(WA_VISUAL_PARTICLE_EMITTERS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag emitters = tag.getList(WA_VISUAL_PARTICLE_EMITTERS, Tag.TAG_COMPOUND);
        List<EmitterDefinition> resolved = new ArrayList<>(emitters.size());
        for (int index = 0; index < emitters.size(); index++) {
            CompoundTag entry = emitters.getCompound(index);
            Optional<EmitterKind> kind = EmitterKind.fromSerializedName(entry.getString("kind"));
            ResourceLocation registryId = ResourceLocation.tryParse(entry.getString("id"));
            if (kind.isEmpty() || registryId == null) {
                continue;
            }
            resolved.add(new EmitterDefinition(
                    kind.get(),
                    registryId,
                    clampInt(readInt(entry, "count", DEFAULT_COUNT), MIN_COUNT, MAX_COUNT),
                    clampDouble(readDouble(entry, "offset_x", DEFAULT_OFFSET_X), MIN_OFFSET, MAX_OFFSET),
                    clampDouble(readDouble(entry, "offset_y", DEFAULT_OFFSET_Y), MIN_OFFSET, MAX_OFFSET),
                    clampDouble(readDouble(entry, "offset_z", DEFAULT_OFFSET_Z), MIN_OFFSET, MAX_OFFSET),
                    clampDouble(readDouble(entry, "speed", DEFAULT_SPEED), MIN_SPEED, MAX_SPEED),
                    clampInt(readInt(entry, "interval_ticks", DEFAULT_INTERVAL_TICKS), MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS),
                    entry.contains("color_rgb", Tag.TAG_ANY_NUMERIC)
                            ? Optional.of(sanitizeColor(entry.getInt("color_rgb")))
                            : Optional.empty(),
                    entry.contains("size", Tag.TAG_ANY_NUMERIC)
                            ? Optional.of(clampFloat(entry.getFloat("size"), MIN_DUST_SIZE, MAX_DUST_SIZE))
                            : Optional.empty()));
        }
        return List.copyOf(resolved);
    }

    private static ParseResult parseAmbientParticlesInternal(JsonObject parameters) {
        if (parameters.has("effect") || parameters.has("effect_type")) {
            return ParseResult.error("parameters.effect_type is not supported for ambient_particles; use effect_particles");
        }
        Optional<String> unexpectedField = firstUnexpectedField(
                parameters,
                Set.of("particle", "color", "size", "count", "offset_x", "offset_y", "offset_z", "speed", "interval_ticks"));
        if (unexpectedField.isPresent()) {
            return ParseResult.error("parameters." + unexpectedField.get() + " is not supported for ambient_particles");
        }

        Optional<EmitterDefinition> emitter = parseParticleEmitter(parameters);
        if (emitter.isEmpty()) {
            return ParseResult.error(
                    "parameters.particle must reference a registered simple particle type; use effect_particles for vanilla mob-effect visuals");
        }

        Optional<Integer> count = readBoundedInt(parameters, "count", MIN_COUNT, MAX_COUNT);
        if (count.isEmpty() && parameters.has("count")) {
            return ParseResult.error("parameters.count must be an integer between 1 and 32");
        }
        Optional<Double> offsetX = readBoundedDouble(parameters, "offset_x", MIN_OFFSET, MAX_OFFSET);
        if (offsetX.isEmpty() && parameters.has("offset_x")) {
            return ParseResult.error("parameters.offset_x must be a number between 0 and 4");
        }
        Optional<Double> offsetY = readBoundedDouble(parameters, "offset_y", MIN_OFFSET, MAX_OFFSET);
        if (offsetY.isEmpty() && parameters.has("offset_y")) {
            return ParseResult.error("parameters.offset_y must be a number between 0 and 4");
        }
        Optional<Double> offsetZ = readBoundedDouble(parameters, "offset_z", MIN_OFFSET, MAX_OFFSET);
        if (offsetZ.isEmpty() && parameters.has("offset_z")) {
            return ParseResult.error("parameters.offset_z must be a number between 0 and 4");
        }
        Optional<Double> speed = readBoundedDouble(parameters, "speed", MIN_SPEED, MAX_SPEED);
        if (speed.isEmpty() && parameters.has("speed")) {
            return ParseResult.error("parameters.speed must be a number between 0 and 4");
        }
        Optional<Integer> intervalTicks = readBoundedInt(parameters, "interval_ticks", MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        if (intervalTicks.isEmpty() && parameters.has("interval_ticks")) {
            return ParseResult.error("parameters.interval_ticks must be an integer between 1 and 200");
        }
        boolean isDust = emitter.get().registryId().equals(DUST_PARTICLE_ID);
        Optional<Integer> colorOverride = Optional.empty();
        Optional<Float> sizeOverride = Optional.empty();
        if (isDust) {
            colorOverride = readOptionalHexColor(parameters, "color");
            if (colorOverride.isEmpty() && parameters.has("color")) {
                return ParseResult.error("parameters.color must match #RRGGBB");
            }
            sizeOverride = readBoundedFloat(parameters, "size", MIN_DUST_SIZE, MAX_DUST_SIZE);
            if (sizeOverride.isEmpty() && parameters.has("size")) {
                return ParseResult.error("parameters.size must be a number between 0.01 and 4.0");
            }
        }

        EmitterDefinition resolved = emitter.get();
        return ParseResult.success(new EmitterDefinition(
                resolved.kind(),
                resolved.registryId(),
                count.orElse(DEFAULT_COUNT),
                offsetX.orElse(DEFAULT_OFFSET_X),
                offsetY.orElse(DEFAULT_OFFSET_Y),
                offsetZ.orElse(DEFAULT_OFFSET_Z),
                speed.orElse(DEFAULT_SPEED),
                intervalTicks.orElse(DEFAULT_INTERVAL_TICKS),
                colorOverride,
                sizeOverride));
    }

    private static ParseResult parseEffectParticlesInternal(JsonObject parameters) {
        if (parameters.has("particle")) {
            return ParseResult.error("parameters.particle is not supported for effect_particles; use ambient_particles");
        }
        if (parameters.has("effect")) {
            return ParseResult.error("parameters.effect is not supported for effect_particles; use effect_type");
        }
        Optional<String> unsupportedEffectField = firstPresent(
                parameters,
                "offset_x",
                "offset_y",
                "offset_z",
                "speed");
        if (unsupportedEffectField.isPresent()) {
            return ParseResult.error("parameters." + unsupportedEffectField.get() + " is not supported for effect_particles");
        }
        Optional<String> unexpectedField = firstUnexpectedField(
                parameters,
                Set.of("effect_type", "color", "count", "interval_ticks"));
        if (unexpectedField.isPresent()) {
            return ParseResult.error("parameters." + unexpectedField.get() + " is not supported for effect_particles");
        }

        Optional<EmitterDefinition> emitter = parseEffectEmitter(parameters);
        if (emitter.isEmpty()) {
            return ParseResult.error("parameters.effect_type must reference a registered mob effect");
        }
        Optional<Integer> colorOverride = readOptionalHexColor(parameters, "color");
        if (colorOverride.isEmpty() && parameters.has("color")) {
            return ParseResult.error("parameters.color must match #RRGGBB");
        }

        Optional<Integer> count = readBoundedInt(parameters, "count", MIN_COUNT, MAX_COUNT);
        if (count.isEmpty() && parameters.has("count")) {
            return ParseResult.error("parameters.count must be an integer between 1 and 32");
        }
        Optional<Integer> intervalTicks = readBoundedInt(parameters, "interval_ticks", MIN_INTERVAL_TICKS, MAX_INTERVAL_TICKS);
        if (intervalTicks.isEmpty() && parameters.has("interval_ticks")) {
            return ParseResult.error("parameters.interval_ticks must be an integer between 1 and 200");
        }

        EmitterDefinition resolved = emitter.get();
        return ParseResult.success(new EmitterDefinition(
                resolved.kind(),
                resolved.registryId(),
                count.orElse(DEFAULT_COUNT),
                DEFAULT_OFFSET_X,
                DEFAULT_OFFSET_Y,
                DEFAULT_OFFSET_Z,
                DEFAULT_SPEED,
                intervalTicks.orElse(DEFAULT_INTERVAL_TICKS),
                colorOverride,
                Optional.empty()));
    }

    private static Optional<EmitterDefinition> parseParticleEmitter(JsonObject parameters) {
        Optional<ResourceLocation> particleId = readResourceLocation(parameters, "particle");
        if (particleId.isEmpty()) {
            return Optional.empty();
        }
        ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId.get()).orElse(null);
        if (!(particleType instanceof SimpleParticleType)) {
            if (!DUST_PARTICLE_ID.equals(particleId.get())) {
                return Optional.empty();
            }
        }
        return Optional.of(new EmitterDefinition(
                EmitterKind.PARTICLE,
                particleId.get(),
                DEFAULT_COUNT,
                DEFAULT_OFFSET_X,
                DEFAULT_OFFSET_Y,
                DEFAULT_OFFSET_Z,
                DEFAULT_SPEED,
                DEFAULT_INTERVAL_TICKS,
                Optional.empty(),
                Optional.empty()));
    }

    private static Optional<EmitterDefinition> parseEffectEmitter(JsonObject parameters) {
        Optional<ResourceLocation> effectId = readResourceLocation(parameters, "effect_type");
        if (effectId.isEmpty()) {
            return Optional.empty();
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectId.get()).orElse(null);
        if (effect == null) {
            return Optional.empty();
        }
        return Optional.of(new EmitterDefinition(
                EmitterKind.EFFECT_VISUAL,
                effectId.get(),
                DEFAULT_COUNT,
                DEFAULT_OFFSET_X,
                DEFAULT_OFFSET_Y,
                DEFAULT_OFFSET_Z,
                DEFAULT_SPEED,
                DEFAULT_INTERVAL_TICKS,
                Optional.empty(),
                Optional.empty()));
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = raw.getAsString();
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }

    private static Optional<String> firstPresent(JsonObject parameters, String... keys) {
        for (String key : keys) {
            if (parameters.has(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstUnexpectedField(JsonObject parameters, Set<String> allowedKeys) {
        for (String key : parameters.keySet()) {
            if (!allowedKeys.contains(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> readBoundedInt(JsonObject parameters, String key, int min, int max) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        int value = raw.getAsInt();
        return value < min || value > max ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Double> readBoundedDouble(JsonObject parameters, String key, double min, double max) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        double value = raw.getAsDouble();
        return value < min || value > max ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Float> readBoundedFloat(JsonObject parameters, String key, float min, float max) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        float value = raw.getAsFloat();
        return value < min || value > max ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Integer> readOptionalHexColor(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = raw.getAsString();
        if (!HEX_COLOR_PATTERN.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(sanitizeColor(Integer.parseInt(value.substring(1), 16)));
    }

    private static int readInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_INT) ? tag.getInt(key) : fallback;
    }

    private static double readDouble(CompoundTag tag, String key, double fallback) {
        return tag.contains(key, Tag.TAG_DOUBLE) ? tag.getDouble(key) : fallback;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int sanitizeColor(int colorRgb) {
        return colorRgb & 0x00FFFFFF;
    }

    public enum EmitterKind {
        PARTICLE("particle"),
        EFFECT_VISUAL("effect_visual");

        private final String serializedName;

        EmitterKind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        static Optional<EmitterKind> fromSerializedName(String value) {
            for (EmitterKind kind : values()) {
                if (kind.serializedName.equalsIgnoreCase(value)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public record EmitterDefinition(
            EmitterKind kind,
            ResourceLocation registryId,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double speed,
            int intervalTicks,
            Optional<Integer> colorOverrideRgb,
            Optional<Float> sizeOverride) {
        public Optional<ParticleOptions> resolveParticleOptions() {
            return switch (kind) {
                case PARTICLE -> {
                    if (registryId.equals(DUST_PARTICLE_ID)) {
                        int color = sanitizeColor(colorOverrideRgb.orElse(DEFAULT_DUST_COLOR_RGB));
                        float size = clampFloat(sizeOverride.orElse(DEFAULT_DUST_SIZE), MIN_DUST_SIZE, MAX_DUST_SIZE);
                        Vector3f dustColor = Vec3.fromRGB24(color).toVector3f();
                        yield Optional.of(new DustParticleOptions(dustColor, size));
                    }
                    ParticleType<?> particleType = BuiltInRegistries.PARTICLE_TYPE.getOptional(registryId).orElse(null);
                    yield particleType instanceof SimpleParticleType simpleParticleType
                            ? Optional.of(simpleParticleType)
                            : Optional.empty();
                }
                case EFFECT_VISUAL -> {
                    if (colorOverrideRgb.isPresent()) {
                        yield Optional.of(ColorParticleOption.create(
                                ParticleTypes.ENTITY_EFFECT,
                                FastColor.ARGB32.color(EFFECT_PARTICLE_ALPHA, sanitizeColor(colorOverrideRgb.get()))));
                    }
                    MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(registryId).orElse(null);
                    if (effect == null) {
                        yield Optional.empty();
                    }
                    Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
                    yield Optional.of(new MobEffectInstance(holder, 0, 0, false, true, false).getParticleOptions());
                }
            };
        }
    }

    private record ParseResult(
            Optional<EmitterDefinition> emitter,
            Optional<String> error) {
        private static ParseResult success(EmitterDefinition emitter) {
            return new ParseResult(Optional.of(emitter), Optional.empty());
        }

        private static ParseResult error(String error) {
            return new ParseResult(Optional.empty(), Optional.of(error));
        }
    }
}

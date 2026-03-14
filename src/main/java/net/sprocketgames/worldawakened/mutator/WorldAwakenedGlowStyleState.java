package net.sprocketgames.worldawakened.mutator;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class WorldAwakenedGlowStyleState {
    public static final String WA_GLOW_STYLE = "WA_GLOW_STYLE";

    public static final int DEFAULT_COLOR_RGB = 0x66FF66;
    public static final float MIN_BRIGHTNESS = 0.0F;
    public static final float MAX_BRIGHTNESS = 1.0F;
    public static final float DEFAULT_BRIGHTNESS = 1.0F;
    public static final boolean DEFAULT_SEE_THROUGH_WALLS = false;
    public static final boolean DEFAULT_PULSE = false;
    public static final float MIN_PULSE_SPEED = 0.25F;
    public static final float MAX_PULSE_SPEED = 3.0F;
    public static final float DEFAULT_PULSE_SPEED = 1.0F;
    public static final float MIN_PULSE_STRENGTH = 0.05F;
    public static final float MAX_PULSE_STRENGTH = 0.35F;
    public static final float DEFAULT_PULSE_STRENGTH = 0.12F;

    private static final String FIELD_COLOR = "color";
    private static final String FIELD_BRIGHTNESS = "brightness";
    private static final String FIELD_SEE_THROUGH_WALLS = "see_through_walls";
    private static final String FIELD_PULSE = "pulse";
    private static final String FIELD_PULSE_SPEED = "pulse_speed";
    private static final String FIELD_PULSE_STRENGTH = "pulse_strength";

    private static final String TAG_COLOR_RGB = "color_rgb";
    private static final String TAG_BRIGHTNESS = "brightness";
    private static final String TAG_SEE_THROUGH_WALLS = "see_through_walls";
    private static final String TAG_PULSE = "pulse";
    private static final String TAG_PULSE_SPEED = "pulse_speed";
    private static final String TAG_PULSE_STRENGTH = "pulse_strength";

    private static final Set<String> SUPPORTED_FIELDS = Set.of(
            FIELD_COLOR,
            FIELD_BRIGHTNESS,
            FIELD_SEE_THROUGH_WALLS,
            FIELD_PULSE,
            FIELD_PULSE_SPEED,
            FIELD_PULSE_STRENGTH);
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private WorldAwakenedGlowStyleState() {
    }

    public static Optional<String> validateParameters(JsonObject parameters) {
        return parseParameters(parameters).error();
    }

    public static Optional<GlowStyleDefinition> fromParameters(JsonObject parameters) {
        ParseResult parsed = parseParameters(parameters);
        return parsed.error().isPresent() ? Optional.empty() : parsed.state();
    }

    public static void write(CompoundTag entityTag, GlowStyleDefinition style) {
        CompoundTag root = new CompoundTag();
        root.putInt(TAG_COLOR_RGB, sanitizeColor(style.colorRgb()));
        root.putFloat(TAG_BRIGHTNESS, clampBrightness(style.brightness()));
        root.putBoolean(TAG_SEE_THROUGH_WALLS, style.seeThroughWalls());
        root.putBoolean(TAG_PULSE, style.pulse());
        root.putFloat(TAG_PULSE_SPEED, clampPulseSpeed(style.pulseSpeed()));
        root.putFloat(TAG_PULSE_STRENGTH, clampPulseStrength(style.pulseStrength()));
        entityTag.put(WA_GLOW_STYLE, root);
    }

    public static void clear(CompoundTag entityTag) {
        entityTag.remove(WA_GLOW_STYLE);
    }

    public static Optional<GlowStyleDefinition> read(CompoundTag entityTag) {
        if (!entityTag.contains(WA_GLOW_STYLE, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        CompoundTag root = entityTag.getCompound(WA_GLOW_STYLE);
        int colorRgb = root.contains(TAG_COLOR_RGB, Tag.TAG_INT)
                ? sanitizeColor(root.getInt(TAG_COLOR_RGB))
                : DEFAULT_COLOR_RGB;
        float brightness = root.contains(TAG_BRIGHTNESS, Tag.TAG_ANY_NUMERIC)
                ? clampBrightness(root.getFloat(TAG_BRIGHTNESS))
                : DEFAULT_BRIGHTNESS;
        boolean seeThroughWalls = root.contains(TAG_SEE_THROUGH_WALLS, Tag.TAG_BYTE)
                ? root.getBoolean(TAG_SEE_THROUGH_WALLS)
                : DEFAULT_SEE_THROUGH_WALLS;
        boolean pulse = root.contains(TAG_PULSE, Tag.TAG_BYTE)
                ? root.getBoolean(TAG_PULSE)
                : DEFAULT_PULSE;
        float pulseSpeed = root.contains(TAG_PULSE_SPEED, Tag.TAG_ANY_NUMERIC)
                ? clampPulseSpeed(root.getFloat(TAG_PULSE_SPEED))
                : DEFAULT_PULSE_SPEED;
        float pulseStrength = root.contains(TAG_PULSE_STRENGTH, Tag.TAG_ANY_NUMERIC)
                ? clampPulseStrength(root.getFloat(TAG_PULSE_STRENGTH))
                : DEFAULT_PULSE_STRENGTH;
        return Optional.of(new GlowStyleDefinition(
                colorRgb,
                brightness,
                seeThroughWalls,
                pulse,
                pulseSpeed,
                pulseStrength));
    }

    public static String formatColorHex(int colorRgb) {
        return String.format(Locale.ROOT, "#%06x", sanitizeColor(colorRgb));
    }

    private static ParseResult parseParameters(JsonObject parameters) {
        for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
            if (!SUPPORTED_FIELDS.contains(entry.getKey())) {
                return ParseResult.error("parameters." + entry.getKey() + " is not supported for glow_style");
            }
        }

        int colorRgb = DEFAULT_COLOR_RGB;
        if (parameters.has(FIELD_COLOR)) {
            JsonElement rawColor = parameters.get(FIELD_COLOR);
            if (!rawColor.isJsonPrimitive() || !rawColor.getAsJsonPrimitive().isString()) {
                return ParseResult.error("parameters.color must be a hex RGB string like #66ff66");
            }
            String colorString = rawColor.getAsString();
            if (!HEX_COLOR_PATTERN.matcher(colorString).matches()) {
                return ParseResult.error("parameters.color must match #RRGGBB");
            }
            colorRgb = Integer.parseInt(colorString.substring(1), 16);
        }

        Optional<Float> brightness = readOptionalNumber(parameters, FIELD_BRIGHTNESS);
        if (brightness.isEmpty() && parameters.has(FIELD_BRIGHTNESS)) {
            return ParseResult.error("parameters.brightness must be numeric");
        }
        float resolvedBrightness = clampBrightness(brightness.orElse(DEFAULT_BRIGHTNESS));

        Optional<Boolean> seeThroughWalls = readOptionalBoolean(parameters, FIELD_SEE_THROUGH_WALLS);
        if (seeThroughWalls.isEmpty() && parameters.has(FIELD_SEE_THROUGH_WALLS)) {
            return ParseResult.error("parameters.see_through_walls must be boolean");
        }

        Optional<Boolean> pulse = readOptionalBoolean(parameters, FIELD_PULSE);
        if (pulse.isEmpty() && parameters.has(FIELD_PULSE)) {
            return ParseResult.error("parameters.pulse must be boolean");
        }

        Optional<Float> pulseSpeed = readOptionalNumber(parameters, FIELD_PULSE_SPEED);
        if (pulseSpeed.isEmpty() && parameters.has(FIELD_PULSE_SPEED)) {
            return ParseResult.error("parameters.pulse_speed must be numeric");
        }
        float resolvedPulseSpeed = pulseSpeed.orElse(DEFAULT_PULSE_SPEED);
        if (resolvedPulseSpeed < MIN_PULSE_SPEED || resolvedPulseSpeed > MAX_PULSE_SPEED) {
            return ParseResult.error("parameters.pulse_speed must be between 0.25 and 3.0");
        }

        Optional<Float> pulseStrength = readOptionalNumber(parameters, FIELD_PULSE_STRENGTH);
        if (pulseStrength.isEmpty() && parameters.has(FIELD_PULSE_STRENGTH)) {
            return ParseResult.error("parameters.pulse_strength must be numeric");
        }
        float resolvedPulseStrength = pulseStrength.orElse(DEFAULT_PULSE_STRENGTH);
        if (resolvedPulseStrength < MIN_PULSE_STRENGTH || resolvedPulseStrength > MAX_PULSE_STRENGTH) {
            return ParseResult.error("parameters.pulse_strength must be between 0.05 and 0.35");
        }

        return ParseResult.success(new GlowStyleDefinition(
                colorRgb,
                resolvedBrightness,
                seeThroughWalls.orElse(DEFAULT_SEE_THROUGH_WALLS),
                pulse.orElse(DEFAULT_PULSE),
                resolvedPulseSpeed,
                resolvedPulseStrength));
    }

    private static Optional<Float> readOptionalNumber(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        return Optional.of(raw.getAsFloat());
    }

    private static Optional<Boolean> readOptionalBoolean(JsonObject parameters, String key) {
        if (!parameters.has(key)) {
            return Optional.empty();
        }
        JsonElement raw = parameters.get(key);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) {
            return Optional.empty();
        }
        return Optional.of(raw.getAsBoolean());
    }

    private static int sanitizeColor(int colorRgb) {
        return colorRgb & 0x00FFFFFF;
    }

    private static float clampBrightness(float value) {
        return clamp(value, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
    }

    private static float clampPulseSpeed(float value) {
        return clamp(value, MIN_PULSE_SPEED, MAX_PULSE_SPEED);
    }

    private static float clampPulseStrength(float value) {
        return clamp(value, MIN_PULSE_STRENGTH, MAX_PULSE_STRENGTH);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ParseResult(
            Optional<GlowStyleDefinition> state,
            Optional<String> error) {
        private static ParseResult success(GlowStyleDefinition state) {
            return new ParseResult(Optional.of(state), Optional.empty());
        }

        private static ParseResult error(String error) {
            return new ParseResult(Optional.empty(), Optional.of(error));
        }
    }

    public record GlowStyleDefinition(
            int colorRgb,
            float brightness,
            boolean seeThroughWalls,
            boolean pulse,
            float pulseSpeed,
            float pulseStrength) {
    }
}

package net.sprocketgames.worldawakened.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum WorldAwakenedGenerationMode {
    CURATED("curated"), GENERATED("generated"), MIXED("mixed");
    public static final Codec<WorldAwakenedGenerationMode> CODEC = Codec.STRING.comapFlatMap(value -> {
        for (WorldAwakenedGenerationMode mode : values()) if (mode.serializedName.equals(value)) return DataResult.success(mode);
        return DataResult.error(() -> "Unknown generation mode: " + value);
    }, WorldAwakenedGenerationMode::serializedName);
    private final String serializedName;
    WorldAwakenedGenerationMode(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }
}

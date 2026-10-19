package net.sprocketgames.worldawakened.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** The runtime system a configured effect instance can be generated for. */
public enum WorldAwakenedEffectScope {
    MOB_MUTATION("mob_mutation"),
    ASCENSION("ascension");

    public static final Codec<WorldAwakenedEffectScope> CODEC = Codec.STRING.comapFlatMap(value -> {
        for (WorldAwakenedEffectScope scope : values()) {
            if (scope.serializedName.equals(value)) {
                return DataResult.success(scope);
            }
        }
        return DataResult.error(() -> "Unknown WorldAwakened effect scope: " + value);
    }, WorldAwakenedEffectScope::serializedName);

    private final String serializedName;

    WorldAwakenedEffectScope(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}

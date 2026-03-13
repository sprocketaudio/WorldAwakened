package net.sprocketgames.worldawakened.data.definition;

import java.util.Locale;

import com.mojang.serialization.Codec;

public enum AscensionComponentSuppressionPolicy {
    REWARD_ONLY("reward_only"),
    INDEPENDENT("independent"),
    GROUPED("grouped");

    private static final Codec<AscensionComponentSuppressionPolicy> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                for (AscensionComponentSuppressionPolicy policy : values()) {
                    if (policy.serializedName.equalsIgnoreCase(value)) {
                        return com.mojang.serialization.DataResult.success(policy);
                    }
                }
                return com.mojang.serialization.DataResult.error(() -> "Unknown suppression_policy: " + value);
            },
            AscensionComponentSuppressionPolicy::serializedName);

    private final String serializedName;

    AscensionComponentSuppressionPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Codec<AscensionComponentSuppressionPolicy> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return serializedName.toLowerCase(Locale.ROOT);
    }
}

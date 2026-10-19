package net.sprocketgames.worldawakened.data.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Independent mob-kill XP reward; it never competes with weighted item entries. */
public record LootBonusXpDefinition(
        boolean enabled,
        LootBonusXpMode mode,
        int minimum,
        int maximum,
        double multiplier,
        double chance) {
    public static final Codec<LootBonusXpDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(LootBonusXpDefinition::enabled),
            net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs.enumCodec(LootBonusXpMode.class)
                    .optionalFieldOf("mode", LootBonusXpMode.EXTRA).forGetter(LootBonusXpDefinition::mode),
            Codec.INT.optionalFieldOf("min", 0).forGetter(LootBonusXpDefinition::minimum),
            Codec.INT.optionalFieldOf("max", 0).forGetter(LootBonusXpDefinition::maximum),
            Codec.DOUBLE.optionalFieldOf("multiplier", 1.0D).forGetter(LootBonusXpDefinition::multiplier),
            Codec.DOUBLE.optionalFieldOf("chance", 1.0D).forGetter(LootBonusXpDefinition::chance))
            .apply(instance, LootBonusXpDefinition::new));
}

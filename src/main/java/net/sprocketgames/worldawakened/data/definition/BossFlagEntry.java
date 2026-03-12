package net.sprocketgames.worldawakened.data.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record BossFlagEntry(ResourceLocation entity, boolean isBoss) {
    public static final Codec<BossFlagEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("entity").forGetter(BossFlagEntry::entity),
            Codec.BOOL.optionalFieldOf("is_boss", true).forGetter(BossFlagEntry::isBoss))
            .apply(instance, BossFlagEntry::new));
}


package net.sprocketgames.worldawakened.config;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

/**
 * World-authoring metadata which groups ordinary mutation pools into an ordered
 * difficulty track. It deliberately has no runtime state of its own.
 */
public record WorldAwakenedDifficultyTrackDefinition(
        ResourceLocation id,
        String displayName,
        String description,
        boolean enabled,
        List<ResourceLocation> eligibleDimensions,
        List<Tier> tiers) {
    public static final Codec<WorldAwakenedDifficultyTrackDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(WorldAwakenedDifficultyTrackDefinition::id),
            Codec.STRING.fieldOf("display_name").forGetter(WorldAwakenedDifficultyTrackDefinition::displayName),
            Codec.STRING.optionalFieldOf("description", "").forGetter(WorldAwakenedDifficultyTrackDefinition::description),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(WorldAwakenedDifficultyTrackDefinition::enabled),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("eligible_dimensions", List.of())
                    .forGetter(WorldAwakenedDifficultyTrackDefinition::eligibleDimensions),
            Tier.CODEC.listOf().optionalFieldOf("tiers", List.of()).forGetter(WorldAwakenedDifficultyTrackDefinition::tiers))
            .apply(instance, WorldAwakenedDifficultyTrackDefinition::new));

    public WorldAwakenedDifficultyTrackDefinition {
        eligibleDimensions = List.copyOf(eligibleDimensions);
        tiers = List.copyOf(tiers);
    }

    public record Tier(ResourceLocation stageId, ResourceLocation poolId, Optional<ResourceLocation> dimension,
            String configurationName, boolean defaultConfiguration, boolean active) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("stage_id").forGetter(Tier::stageId),
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("pool_id").forGetter(Tier::poolId),
                WorldAwakenedJsonCodecs.RESOURCE_LOCATION.optionalFieldOf("dimension").forGetter(Tier::dimension),
                Codec.STRING.optionalFieldOf("configuration_name", "Default Mobs").forGetter(Tier::configurationName),
                Codec.BOOL.optionalFieldOf("default_configuration", true).forGetter(Tier::defaultConfiguration),
                Codec.BOOL.optionalFieldOf("active", true).forGetter(Tier::active))
                .apply(instance, Tier::new));

        public Tier(ResourceLocation stageId, ResourceLocation poolId) {
            this(stageId, poolId, Optional.empty(), "Default Mobs", true, true);
        }
    }
}

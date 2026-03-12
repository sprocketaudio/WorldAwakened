package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record IntegrationProfileDefinition(
        int schemaVersion,
        Optional<ResourceLocation> configuredId,
        String modId,
        Optional<String> displayName,
        boolean enabledByDefault,
        String configKey,
        List<JsonElement> stageHooks,
        List<ResourceLocation> triggerHooks,
        List<String> entityTags,
        List<String> bossTags,
        List<ResourceLocation> lootTargets,
        List<JsonElement> specialConditions,
        Optional<String> notes) implements WorldAwakenedDataDefinition {
    public static final Codec<IntegrationProfileDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(IntegrationProfileDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.optionalFieldOf("id").forGetter(IntegrationProfileDefinition::configuredId),
            Codec.STRING.fieldOf("mod_id").forGetter(IntegrationProfileDefinition::modId),
            Codec.STRING.optionalFieldOf("display_name").forGetter(IntegrationProfileDefinition::displayName),
            Codec.BOOL.optionalFieldOf("enabled_by_default", true).forGetter(IntegrationProfileDefinition::enabledByDefault),
            Codec.STRING.fieldOf("config_key").forGetter(IntegrationProfileDefinition::configKey),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("stage_hooks", List.of())
                    .forGetter(IntegrationProfileDefinition::stageHooks),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("trigger_hooks", List.of())
                    .forGetter(IntegrationProfileDefinition::triggerHooks),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("entity_tags", List.of()).forGetter(IntegrationProfileDefinition::entityTags),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("boss_tags", List.of()).forGetter(IntegrationProfileDefinition::bossTags),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("loot_targets", List.of()).forGetter(IntegrationProfileDefinition::lootTargets),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("special_conditions", List.of())
                    .forGetter(IntegrationProfileDefinition::specialConditions),
            Codec.STRING.optionalFieldOf("notes").forGetter(IntegrationProfileDefinition::notes))
            .apply(instance, IntegrationProfileDefinition::new));

    @Override
    public ResourceLocation id() {
        if (configuredId.isPresent()) {
            return configuredId.get();
        }
        String sanitized = modId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-.]", "_");
        return ResourceLocation.fromNamespaceAndPath("worldawakened", "integration/" + sanitized);
    }

    @Override
    public boolean enabled() {
        return enabledByDefault;
    }
}


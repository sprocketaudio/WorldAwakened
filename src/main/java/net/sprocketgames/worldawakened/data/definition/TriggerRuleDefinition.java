package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record TriggerRuleDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        int priority,
        ResourceLocation triggerType,
        SourceScope sourceScope,
        List<JsonElement> conditions,
        List<JsonElement> actions,
        Optional<JsonElement> cooldown,
        boolean oneShot) implements WorldAwakenedDataDefinition {
    public static final Codec<TriggerRuleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(TriggerRuleDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(TriggerRuleDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(TriggerRuleDefinition::enabled),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(TriggerRuleDefinition::priority),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("trigger_type").forGetter(TriggerRuleDefinition::triggerType),
            WorldAwakenedJsonCodecs.enumCodec(SourceScope.class).optionalFieldOf("source_scope", SourceScope.WORLD)
                    .forGetter(TriggerRuleDefinition::sourceScope),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(TriggerRuleDefinition::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("actions").forGetter(TriggerRuleDefinition::actions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("cooldown").forGetter(TriggerRuleDefinition::cooldown),
            Codec.BOOL.optionalFieldOf("one_shot", false).forGetter(TriggerRuleDefinition::oneShot))
            .apply(instance, TriggerRuleDefinition::new));
}


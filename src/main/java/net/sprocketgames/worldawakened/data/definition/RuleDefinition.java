package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record RuleDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        int priority,
        List<JsonElement> conditions,
        List<JsonElement> actions,
        double weight,
        double chance,
        Optional<JsonElement> cooldown,
        ExecutionScope executionScope,
        List<String> tags) implements WorldAwakenedDataDefinition {
    public static final Codec<RuleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(RuleDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(RuleDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RuleDefinition::enabled),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(RuleDefinition::priority),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("conditions").forGetter(RuleDefinition::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("actions").forGetter(RuleDefinition::actions),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0D).forGetter(RuleDefinition::weight),
            Codec.DOUBLE.optionalFieldOf("chance", 1.0D).forGetter(RuleDefinition::chance),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("cooldown").forGetter(RuleDefinition::cooldown),
            WorldAwakenedJsonCodecs.enumCodec(ExecutionScope.class).optionalFieldOf("execution_scope", ExecutionScope.WORLD)
                    .forGetter(RuleDefinition::executionScope),
            WorldAwakenedJsonCodecs.STRING_LIST.optionalFieldOf("tags", List.of()).forGetter(RuleDefinition::tags))
            .apply(instance, RuleDefinition::new));
}


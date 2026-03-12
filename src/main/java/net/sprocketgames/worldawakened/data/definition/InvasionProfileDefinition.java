package net.sprocketgames.worldawakened.data.definition;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.codec.WorldAwakenedJsonCodecs;

public record InvasionProfileDefinition(
        int schemaVersion,
        ResourceLocation id,
        boolean enabled,
        InvasionTriggerMode triggerMode,
        InvasionPresentation presentation,
        InvasionFilters filters,
        InvasionSchedule schedule,
        InvasionComposition composition,
        InvasionSafety safety,
        Optional<ResourceLocation> rewardProfile) implements WorldAwakenedDataDefinition {
    private static final MapCodec<InvasionPresentation> PRESENTATION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.fieldOf("display_name").forGetter(InvasionPresentation::displayName),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("description").forGetter(InvasionPresentation::description))
            .apply(instance, InvasionPresentation::new));

    private static final MapCodec<InvasionFilters> FILTERS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().optionalFieldOf("conditions", List.of()).forGetter(InvasionFilters::conditions),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("stage_filters").forGetter(InvasionFilters::stageFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("pressure_tier_filters").forGetter(InvasionFilters::pressureTierFilters),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("apotheosis_tier_filters")
                    .forGetter(InvasionFilters::apotheosisTierFilters),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("dimensions", List.of()).forGetter(InvasionFilters::dimensions),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("biome_filters", List.of()).forGetter(InvasionFilters::biomeFilters))
            .apply(instance, InvasionFilters::new));

    private static final MapCodec<InvasionSchedule> SCHEDULE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("min_players", 1).forGetter(InvasionSchedule::minPlayers),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("cooldown").forGetter(InvasionSchedule::cooldown),
            Codec.INT.optionalFieldOf("warning_time", 20).forGetter(InvasionSchedule::warningTimeSeconds),
            Codec.INT.optionalFieldOf("wave_count", 1).forGetter(InvasionSchedule::waveCount),
            Codec.INT.optionalFieldOf("wave_interval", 200).forGetter(InvasionSchedule::waveIntervalTicks))
            .apply(instance, InvasionSchedule::new));

    private static final MapCodec<InvasionComposition> COMPOSITION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("spawn_budget").forGetter(InvasionComposition::spawnBudget),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.listOf().fieldOf("spawn_composition").forGetter(InvasionComposition::spawnComposition),
            Codec.DOUBLE.optionalFieldOf("elite_chance", 0.0D).forGetter(InvasionComposition::eliteChance),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION_LIST.optionalFieldOf("mutator_pool_refs", List.of())
                    .forGetter(InvasionComposition::mutatorPoolRefs),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("boss_wave").forGetter(InvasionComposition::bossWave))
            .apply(instance, InvasionComposition::new));

    private static final MapCodec<InvasionSafety> SAFETY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("max_active_entities").forGetter(InvasionSafety::maxActiveEntities),
            WorldAwakenedJsonCodecs.JSON_ELEMENT.optionalFieldOf("safe_zone_rules").forGetter(InvasionSafety::safeZoneRules))
            .apply(instance, InvasionSafety::new));

    public static final Codec<InvasionProfileDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("schema_version", 1).forGetter(InvasionProfileDefinition::schemaVersion),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.fieldOf("id").forGetter(InvasionProfileDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(InvasionProfileDefinition::enabled),
            WorldAwakenedJsonCodecs.enumCodec(InvasionTriggerMode.class).fieldOf("trigger_mode").forGetter(InvasionProfileDefinition::triggerMode),
            PRESENTATION_CODEC.forGetter(InvasionProfileDefinition::presentation),
            FILTERS_CODEC.forGetter(InvasionProfileDefinition::filters),
            SCHEDULE_CODEC.forGetter(InvasionProfileDefinition::schedule),
            COMPOSITION_CODEC.forGetter(InvasionProfileDefinition::composition),
            SAFETY_CODEC.forGetter(InvasionProfileDefinition::safety),
            WorldAwakenedJsonCodecs.RESOURCE_LOCATION.optionalFieldOf("reward_profile").forGetter(InvasionProfileDefinition::rewardProfile))
            .apply(instance, InvasionProfileDefinition::new));

    public JsonElement displayName() {
        return presentation.displayName();
    }

    public Optional<JsonElement> description() {
        return presentation.description();
    }

    public List<JsonElement> conditions() {
        return filters.conditions();
    }

    public Optional<JsonElement> stageFilters() {
        return filters.stageFilters();
    }

    public Optional<JsonElement> pressureTierFilters() {
        return filters.pressureTierFilters();
    }

    public Optional<JsonElement> apotheosisTierFilters() {
        return filters.apotheosisTierFilters();
    }

    public List<ResourceLocation> dimensions() {
        return filters.dimensions();
    }

    public List<ResourceLocation> biomeFilters() {
        return filters.biomeFilters();
    }

    public int minPlayers() {
        return schedule.minPlayers();
    }

    public Optional<JsonElement> cooldown() {
        return schedule.cooldown();
    }

    public int warningTimeSeconds() {
        return schedule.warningTimeSeconds();
    }

    public int waveCount() {
        return schedule.waveCount();
    }

    public int waveIntervalTicks() {
        return schedule.waveIntervalTicks();
    }

    public int spawnBudget() {
        return composition.spawnBudget();
    }

    public List<JsonElement> spawnComposition() {
        return composition.spawnComposition();
    }

    public double eliteChance() {
        return composition.eliteChance();
    }

    public List<ResourceLocation> mutatorPoolRefs() {
        return composition.mutatorPoolRefs();
    }

    public Optional<JsonElement> bossWave() {
        return composition.bossWave();
    }

    public Optional<Integer> maxActiveEntities() {
        return safety.maxActiveEntities();
    }

    public Optional<JsonElement> safeZoneRules() {
        return safety.safeZoneRules();
    }

    public record InvasionPresentation(
            JsonElement displayName,
            Optional<JsonElement> description) {
    }

    public record InvasionFilters(
            List<JsonElement> conditions,
            Optional<JsonElement> stageFilters,
            Optional<JsonElement> pressureTierFilters,
            Optional<JsonElement> apotheosisTierFilters,
            List<ResourceLocation> dimensions,
            List<ResourceLocation> biomeFilters) {
    }

    public record InvasionSchedule(
            int minPlayers,
            Optional<JsonElement> cooldown,
            int warningTimeSeconds,
            int waveCount,
            int waveIntervalTicks) {
    }

    public record InvasionComposition(
            int spawnBudget,
            List<JsonElement> spawnComposition,
            double eliteChance,
            List<ResourceLocation> mutatorPoolRefs,
            Optional<JsonElement> bossWave) {
    }

    public record InvasionSafety(
            Optional<Integer> maxActiveEntities,
            Optional<JsonElement> safeZoneRules) {
    }
}


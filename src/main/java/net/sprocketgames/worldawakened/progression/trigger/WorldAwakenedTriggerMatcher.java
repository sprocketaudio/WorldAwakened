package net.sprocketgames.worldawakened.progression.trigger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.SourceScope;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;

public final class WorldAwakenedTriggerMatcher {
    private static final Comparator<TriggerRuleDefinition> PRIORITY_ORDER = Comparator
            .comparingInt(TriggerRuleDefinition::priority)
            .reversed()
            .thenComparing(definition -> definition.id().toString());

    private WorldAwakenedTriggerMatcher() {
    }

    public static WorldAwakenedTriggerMatchResult match(
            Collection<TriggerRuleDefinition> rules,
            WorldAwakenedStageRegistry stageRegistry,
            WorldAwakenedTriggerMatchContext context) {
        List<TriggerRuleDefinition> candidates = rules.stream()
                .filter(TriggerRuleDefinition::enabled)
                .filter(rule -> context.targetedRuleId().map(id -> id.equals(rule.id())).orElse(true))
                .filter(rule -> triggerTypeMatches(rule, context))
                .sorted(PRIORITY_ORDER)
                .toList();

        List<WorldAwakenedTriggerMatchResult.MatchedRule> matched = new ArrayList<>();
        for (TriggerRuleDefinition candidate : candidates) {
            SourceScope scope = normalizeScope(candidate.sourceScope());
            if (scope == SourceScope.PLAYER && !context.hasPlayerContext()) {
                continue;
            }

            Set<ResourceLocation> stageSnapshot = scope == SourceScope.PLAYER
                    ? context.playerStageSnapshot()
                    : context.worldStageSnapshot();
            if (!conditionsMatch(candidate.conditions(), context, stageSnapshot, stageRegistry)) {
                continue;
            }

            WorldAwakenedTriggerStateSnapshot triggerSnapshot = scope == SourceScope.PLAYER
                    ? context.playerTriggerStateSnapshot()
                    : context.worldTriggerStateSnapshot();
            if (isCooldownActive(candidate, context.nowMillis(), triggerSnapshot)) {
                continue;
            }
            if (candidate.oneShot() && triggerSnapshot.consumedOneShotRules().contains(candidate.id().toString())) {
                continue;
            }

            matched.add(new WorldAwakenedTriggerMatchResult.MatchedRule(candidate, scope));
        }

        return new WorldAwakenedTriggerMatchResult(candidates.size(), List.copyOf(matched));
    }

    public static long cooldownDurationMillis(TriggerRuleDefinition rule) {
        return cooldownDurationMillis(rule.cooldown());
    }

    private static long cooldownDurationMillis(Optional<JsonElement> cooldown) {
        if (cooldown.isEmpty()) {
            return 0L;
        }

        JsonElement raw = cooldown.get();
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            return Math.max(0L, Math.round(raw.getAsDouble() * 1000.0D));
        }
        if (!raw.isJsonObject()) {
            return 0L;
        }

        JsonObject object = raw.getAsJsonObject();
        if (hasNumeric(object, "milliseconds", "millis", "ms")) {
            return Math.max(0L, readLong(object, "milliseconds", "millis", "ms").orElse(0L));
        }
        if (hasNumeric(object, "seconds", "sec", "s")) {
            return Math.max(0L, readLong(object, "seconds", "sec", "s").orElse(0L) * 1000L);
        }
        if (hasNumeric(object, "minutes", "min", "m")) {
            return Math.max(0L, readLong(object, "minutes", "min", "m").orElse(0L) * 60_000L);
        }
        if (hasNumeric(object, "ticks", "tick")) {
            return Math.max(0L, readLong(object, "ticks", "tick").orElse(0L) * 50L);
        }
        return 0L;
    }

    private static SourceScope normalizeScope(SourceScope scope) {
        return scope == SourceScope.PLAYER ? SourceScope.PLAYER : SourceScope.WORLD;
    }

    private static boolean triggerTypeMatches(TriggerRuleDefinition rule, WorldAwakenedTriggerMatchContext context) {
        if (!rule.triggerType().equals(context.triggerType())) {
            return false;
        }
        if (!WorldAwakenedTriggerTypes.BOSS_KILLED.equals(rule.triggerType())) {
            return true;
        }

        Set<ResourceLocation> explicitEntities = readEntitySelectors(rule.conditions());
        if (!explicitEntities.isEmpty()) {
            return context.entityId().map(explicitEntities::contains).orElse(false);
        }

        Set<ResourceLocation> explicitTags = readEntityTagSelectors(rule.conditions());
        if (!explicitTags.isEmpty()) {
            for (ResourceLocation tag : context.entityTags()) {
                if (explicitTags.contains(tag)) {
                    return true;
                }
            }
            return false;
        }

        return context.bossFlagMapMatch();
    }

    private static boolean conditionsMatch(
            List<JsonElement> conditions,
            WorldAwakenedTriggerMatchContext context,
            Set<ResourceLocation> activeStages,
            WorldAwakenedStageRegistry stageRegistry) {
        for (JsonElement condition : conditions) {
            if (!condition.isJsonObject()) {
                return false;
            }
            JsonObject node = condition.getAsJsonObject();
            if (!isNodeEnabled(node)) {
                continue;
            }
            Optional<ResourceLocation> typeOpt = readType(node);
            if (typeOpt.isEmpty()) {
                return false;
            }
            JsonObject parameters = readParametersObject(node);

            String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
            boolean matched = switch (path) {
                case "stage_unlocked" -> readResourceLocation(parameters, "stage")
                        .map(stageId -> containsStage(activeStages, stageRegistry, stageId))
                        .orElse(false);
                case "stage_locked" -> readResourceLocation(parameters, "stage")
                        .map(stageId -> !containsStage(activeStages, stageRegistry, stageId))
                        .orElse(false);
                case "current_dimension" -> readResourceLocation(parameters, "dimension")
                        .map(context.dimensionId()::equals)
                        .orElse(false);
                case "advancement_completed" -> readResourceLocation(parameters, "advancement")
                        .map(id -> context.advancementId().map(id::equals).orElse(false))
                        .orElse(false);
                case "entity_type" -> readResourceLocation(parameters, "entity")
                        .map(id -> context.entityId().map(id::equals).orElse(false))
                        .orElse(false);
                case "entity_tag" -> readTagSelector(parameters, "tag")
                        .map(tag -> context.entityTags().contains(tag))
                        .orElse(false);
                case "manual_trigger" -> readResourceLocation(parameters, "trigger_id")
                        .map(id -> context.manualTriggerId().map(id::equals).orElse(false))
                        .orElse(false);
                case "boss_killed" -> context.bossFlagMapMatch();
                default -> false;
            };

            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsStage(
            Set<ResourceLocation> activeStages,
            WorldAwakenedStageRegistry stageRegistry,
            ResourceLocation requestedId) {
        Optional<ResourceLocation> canonical = stageRegistry.resolveCanonicalId(requestedId);
        if (canonical.isPresent()) {
            return activeStages.contains(canonical.get());
        }
        return activeStages.contains(requestedId);
    }

    private static boolean isCooldownActive(
            TriggerRuleDefinition rule,
            long nowMillis,
            WorldAwakenedTriggerStateSnapshot snapshot) {
        if (cooldownDurationMillis(rule) <= 0L) {
            return false;
        }
        long cooldownUntil = snapshot.cooldowns().getOrDefault(rule.id().toString(), 0L);
        return cooldownUntil > nowMillis;
    }

    private static Set<ResourceLocation> readEntitySelectors(List<JsonElement> conditions) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (JsonElement condition : conditions) {
            if (!condition.isJsonObject()) {
                continue;
            }
            JsonObject object = condition.getAsJsonObject();
            if (!isNodeEnabled(object)) {
                continue;
            }
            Optional<ResourceLocation> typeOpt = readType(object);
            if (typeOpt.isEmpty()) {
                continue;
            }
            String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
            if (!"entity_type".equals(path)) {
                continue;
            }
            readResourceLocation(readParametersObject(object), "entity").ifPresent(ids::add);
        }
        return ids;
    }

    private static Set<ResourceLocation> readEntityTagSelectors(List<JsonElement> conditions) {
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (JsonElement condition : conditions) {
            if (!condition.isJsonObject()) {
                continue;
            }
            JsonObject object = condition.getAsJsonObject();
            if (!isNodeEnabled(object)) {
                continue;
            }
            Optional<ResourceLocation> typeOpt = readType(object);
            if (typeOpt.isEmpty()) {
                continue;
            }
            if (!"entity_tag".equals(typeOpt.get().getPath().toLowerCase(Locale.ROOT))) {
                continue;
            }
            readTagSelector(readParametersObject(object), "tag").ifPresent(tags::add);
        }
        return tags;
    }

    private static Optional<ResourceLocation> readType(JsonObject object) {
        return readResourceLocation(object, "type");
    }
    private static JsonObject readParametersObject(JsonObject object) {
        if (!object.has("parameters") || !object.get("parameters").isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject("parameters");
    }
    private static boolean isNodeEnabled(JsonObject node) {
        if (!node.has("enabled")) {
            return true;
        }
        JsonElement enabled = node.get("enabled");
        if (!enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            return true;
        }
        return enabled.getAsBoolean();
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            String rawValue = object.get(key).getAsString();
            ResourceLocation parsed = parseResourceLocation(rawValue);
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> readTagSelector(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            String rawValue = object.get(key).getAsString();
            if (rawValue.startsWith("#")) {
                rawValue = rawValue.substring(1);
            }
            ResourceLocation parsed = parseResourceLocation(rawValue);
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static ResourceLocation parseResourceLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        if (!raw.contains(":")) {
            return ResourceLocation.tryParse("worldawakened:" + raw);
        }
        return null;
    }

    private static boolean hasNumeric(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Long> readLong(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isNumber()) {
                return Optional.of(object.get(key).getAsLong());
            }
        }
        return Optional.empty();
    }
}

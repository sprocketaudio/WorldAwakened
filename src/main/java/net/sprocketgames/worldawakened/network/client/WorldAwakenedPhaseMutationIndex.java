package net.sprocketgames.worldawakened.network.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Builds the simple World Phase -> dimension -> named configuration view without duplicating pools. */
final class WorldAwakenedPhaseMutationIndex {
    static final String PHASE_TRACK_ID = "worldawakened:world/phase_mutations";
    private static final String DEFAULTS = "worldawakened:defaults/";

    private WorldAwakenedPhaseMutationIndex() {}

    static Index build(Collection<String> orderedStageIds, Map<String, JsonObject> pools, JsonArray tracks) {
        LinkedHashMap<String, List<Configuration>> byStage = new LinkedHashMap<>();
        orderedStageIds.forEach(id -> byStage.put(id, new ArrayList<>()));
        Set<String> claimedPools = new LinkedHashSet<>();

        addStandard(byStage, pools, claimedPools, "stirring", "minecraft:overworld", "overworld_intro");
        addStandard(byStage, pools, claimedPools, "unease", "minecraft:overworld", "s2_overworld");
        addStandard(byStage, pools, claimedPools, "nether_reached", "minecraft:overworld", "s3_overworld");
        addStandard(byStage, pools, claimedPools, "nether_reached", "minecraft:the_nether", "nether_escalation");
        addStandard(byStage, pools, claimedPools, "gathering_storm", "minecraft:overworld", "s4_overworld");
        addStandard(byStage, pools, claimedPools, "gathering_storm", "minecraft:the_nether", "s4_nether");
        addStandard(byStage, pools, claimedPools, "end_reached", "minecraft:overworld", "s5_overworld");
        addStandard(byStage, pools, claimedPools, "end_reached", "minecraft:the_nether", "s5_nether");
        addStandard(byStage, pools, claimedPools, "end_reached", "minecraft:the_end", "end_escalation");
        addStandard(byStage, pools, claimedPools, "world_awakened", "minecraft:overworld", "s6_overworld");
        addStandard(byStage, pools, claimedPools, "world_awakened", "minecraft:the_nether", "s6_nether");
        addStandard(byStage, pools, claimedPools, "world_awakened", "minecraft:the_end", "s6_end");

        for (JsonElement trackValue : tracks) {
            if (!trackValue.isJsonObject()) continue;
            JsonObject track = trackValue.getAsJsonObject();
            boolean trackEnabled = !track.has("enabled") || track.get("enabled").getAsBoolean();
            boolean phaseTrack = PHASE_TRACK_ID.equals(string(track, "id", ""));
            String trackName = string(track, "display_name", "Legacy configuration");
            for (JsonElement tierValue : WorldAwakenedEditorTrackJson.tiers(track)) {
                if (!tierValue.isJsonObject()) continue;
                JsonObject tier = tierValue.getAsJsonObject();
                if (!tier.has("stage_id") || !tier.has("pool_id")) continue;
                String stageId = tier.get("stage_id").getAsString();
                String poolId = tier.get("pool_id").getAsString();
                JsonObject pool = pools.get(poolId); if (pool == null) continue;
                claimedPools.add(poolId);
                boolean active = trackEnabled && (!tier.has("active") || tier.get("active").getAsBoolean());
                boolean defaultConfiguration = !tier.has("default_configuration") || tier.get("default_configuration").getAsBoolean();
                String name = string(tier, "configuration_name", defaultConfiguration ? "Default Mobs" : trackName);
                if (tier.has("dimension")) {
                    add(byStage, new Configuration(stageId, tier.get("dimension").getAsString(), name, poolId,
                            defaultConfiguration, active, !phaseTrack));
                } else {
                    List<String> dimensions = dimensions(pool);
                    if (dimensions.isEmpty()) add(byStage, new Configuration(stageId, "", name, poolId,
                            defaultConfiguration, active, true));
                    else dimensions.forEach(dimension -> add(byStage, new Configuration(stageId, dimension, name, poolId,
                            defaultConfiguration, active, true)));
                }
            }
        }

        for (Map.Entry<String, JsonObject> entry : pools.entrySet()) {
            if (claimedPools.contains(entry.getKey()) || entry.getKey().startsWith(DEFAULTS + "invasion_")) continue;
            List<String> stages = unlockedStages(entry.getValue()); List<String> dimensions = dimensions(entry.getValue());
            if (stages.size() == 1 && dimensions.size() == 1) {
                add(byStage, new Configuration(stages.getFirst(), dimensions.getFirst(), "Default Mobs", entry.getKey(),
                        true, true, false));
                claimedPools.add(entry.getKey());
            }
        }

        List<String> events = pools.keySet().stream().filter(id -> id.startsWith(DEFAULTS + "invasion_")).toList();
        LinkedHashMap<String, List<Configuration>> immutable = new LinkedHashMap<>();
        byStage.forEach((stage, values) -> immutable.put(stage, List.copyOf(values)));
        return new Index(Map.copyOf(immutable), List.copyOf(events));
    }

    private static void addStandard(Map<String, List<Configuration>> byStage, Map<String, JsonObject> pools,
            Set<String> claimed, String stagePath, String dimension, String poolPath) {
        String stageId = DEFAULTS + stagePath, poolId = DEFAULTS + poolPath;
        if (!pools.containsKey(poolId)) return;
        // Membership and gameplay enablement are separate states. A disabled source pool
        // remains visible and editable until explicit phase metadata removes it.
        add(byStage, new Configuration(stageId, dimension, "Default Mobs", poolId, true, true, false));
        claimed.add(poolId);
    }

    private static void add(Map<String, List<Configuration>> byStage, Configuration value) {
        byStage.computeIfAbsent(value.stageId(), ignored -> new ArrayList<>());
        List<Configuration> values = byStage.get(value.stageId());
        for (int index = 0; index < values.size(); index++) {
            Configuration existing = values.get(index);
            if (existing.dimensionId().equals(value.dimensionId()) && existing.poolId().equals(value.poolId())) {
                // Explicit phase metadata supersedes the inferred bundled presentation entry. This lets an
                // operator deactivate or rename a bundled configuration without deleting its source pool.
                values.set(index, value);
                return;
            }
        }
        values.add(value);
    }

    private static List<String> dimensions(JsonObject pool) {
        List<String> values = new ArrayList<>();
        if (pool.has("eligible_dimensions") && pool.get("eligible_dimensions").isJsonArray())
            pool.getAsJsonArray("eligible_dimensions").forEach(value -> values.add(value.getAsString()));
        return List.copyOf(values);
    }

    private static List<String> unlockedStages(JsonObject pool) {
        List<String> values = new ArrayList<>();
        if (!pool.has("conditions") || !pool.get("conditions").isJsonArray()) return values;
        for (JsonElement conditionValue : pool.getAsJsonArray("conditions")) {
            if (!conditionValue.isJsonObject()) continue;
            JsonObject condition = conditionValue.getAsJsonObject();
            if (!string(condition, "type", "").endsWith("stage_unlocked") || !condition.has("parameters")) continue;
            JsonObject parameters = condition.getAsJsonObject("parameters");
            if (parameters.has("stage")) values.add(parameters.get("stage").getAsString());
        }
        return List.copyOf(values);
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    record Configuration(String stageId, String dimensionId, String name, String poolId,
            boolean defaultConfiguration, boolean active, boolean legacyReview) {}
    record Index(Map<String, List<Configuration>> configurationsByStage, List<String> eventPoolIds) {
        List<Configuration> configurations(String stageId) {
            return configurationsByStage.getOrDefault(stageId, List.of());
        }
    }
}

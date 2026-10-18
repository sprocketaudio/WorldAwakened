package net.sprocketgames.worldawakened.network.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Non-destructive editor presentation groups for canonical mutation-pool IDs. */
final class WorldAwakenedPoolBrowserGrouping {
    private static final String DEFAULTS = "worldawakened:defaults/";

    private WorldAwakenedPoolBrowserGrouping() {}

    static List<Group> groups(Collection<String> availablePoolIds, JsonArray authoredTracks) {
        Set<String> available = new LinkedHashSet<>(availablePoolIds);
        Set<String> claimed = new LinkedHashSet<>();
        List<Group> result = new ArrayList<>();
        addStandard(result, claimed, available, "standard_overworld", "Overworld Progression", List.of(
                entry("worldawakened:defaults/stirring", DEFAULTS + "overworld_intro"),
                entry("worldawakened:defaults/unease", DEFAULTS + "s2_overworld"),
                entry("worldawakened:defaults/nether_reached", DEFAULTS + "s3_overworld"),
                entry("worldawakened:defaults/gathering_storm", DEFAULTS + "s4_overworld"),
                entry("worldawakened:defaults/end_reached", DEFAULTS + "s5_overworld"),
                entry("worldawakened:defaults/world_awakened", DEFAULTS + "s6_overworld")));
        addStandard(result, claimed, available, "standard_nether", "Nether Progression", List.of(
                entry("worldawakened:defaults/nether_reached", DEFAULTS + "nether_escalation"),
                entry("worldawakened:defaults/gathering_storm", DEFAULTS + "s4_nether"),
                entry("worldawakened:defaults/end_reached", DEFAULTS + "s5_nether"),
                entry("worldawakened:defaults/world_awakened", DEFAULTS + "s6_nether")));
        addStandard(result, claimed, available, "standard_end", "End Progression", List.of(
                entry("worldawakened:defaults/end_reached", DEFAULTS + "end_escalation"),
                entry("worldawakened:defaults/world_awakened", DEFAULTS + "s6_end")));
        addStandard(result, claimed, available, "standard_events", "Pressure Event Pools", List.of(
                entry(null, DEFAULTS + "invasion_restless_night"),
                entry(null, DEFAULTS + "invasion_nether_surge"),
                entry(null, DEFAULTS + "invasion_awakened_hunt")));

        for (JsonElement value : authoredTracks) {
            if (!value.isJsonObject()) continue;
            JsonObject track = value.getAsJsonObject();
            String id = string(track, "id", "worldawakened:world/tracks/unknown");
            List<Entry> entries = new ArrayList<>();
            for (JsonElement tierValue : WorldAwakenedEditorTrackJson.tiers(track)) {
                if (!tierValue.isJsonObject()) continue;
                JsonObject tier = tierValue.getAsJsonObject();
                if (!tier.has("stage_id") || !tier.has("pool_id")) continue;
                String poolId = tier.get("pool_id").getAsString();
                entries.add(entry(tier.get("stage_id").getAsString(), poolId));
                claimed.add(poolId);
            }
            result.add(new Group(id, string(track, "display_name", readableId(id)), Kind.CUSTOM,
                    track.deepCopy(), List.copyOf(entries)));
        }

        List<Entry> ungrouped = available.stream().filter(id -> !claimed.contains(id)).sorted()
                .map(id -> entry(null, id)).toList();
        if (!ungrouped.isEmpty()) result.add(new Group("ungrouped", "Ungrouped Datapack Pools", Kind.UNGROUPED,
                null, ungrouped));
        return List.copyOf(result);
    }

    private static void addStandard(List<Group> groups, Set<String> claimed, Set<String> available,
            String id, String name, List<Entry> intended) {
        List<Entry> present = intended.stream().filter(entry -> available.contains(entry.poolId())).toList();
        if (present.isEmpty()) return;
        present.forEach(entry -> claimed.add(entry.poolId()));
        groups.add(new Group(id, name, Kind.STANDARD, null, present));
    }

    private static Entry entry(String stageId, String poolId) { return new Entry(stageId, poolId); }
    private static String string(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
    private static String readableId(String id) {
        int slash = id.lastIndexOf('/'); String value = slash >= 0 ? id.substring(slash + 1) : id;
        StringBuilder result = new StringBuilder();
        for (String word : value.split("_")) {
            if (!result.isEmpty()) result.append(' ');
            if (!word.isEmpty()) result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    enum Kind { STANDARD, CUSTOM, UNGROUPED }
    record Entry(String stageId, String poolId) {}
    record Group(String id, String name, Kind kind, JsonObject track, List<Entry> entries) {
        Group { entries = List.copyOf(entries); }
        boolean custom() { return kind == Kind.CUSTOM; }
    }
}

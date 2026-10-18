package net.sprocketgames.worldawakened.network.client;

import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Shared client-side accessors for the codec-backed difficulty-track shape. */
final class WorldAwakenedEditorTrackJson {
    private WorldAwakenedEditorTrackJson() {}

    static JsonArray tiers(JsonObject track) {
        JsonElement value = track.get("tiers");
        if (value == null || value.isJsonNull()) return new JsonArray();
        if (!value.isJsonArray()) throw malformed(track, "tiers", "an array");
        return value.getAsJsonArray();
    }

    static JsonArray mutableTiers(JsonObject track) {
        JsonElement value = track.get("tiers");
        if (value == null || value.isJsonNull()) {
            JsonArray tiers = new JsonArray();
            track.add("tiers", tiers);
            return tiers;
        }
        if (!value.isJsonArray()) throw malformed(track, "tiers", "an array");
        return value.getAsJsonArray();
    }

    /** Removes only unusable tier mappings whose pool is absent from both source and authored definitions. */
    static JsonArray withoutMissingPoolReferences(JsonArray tracks, Set<String> availablePoolIds) {
        JsonArray result = new JsonArray();
        for (JsonElement value : tracks) {
            JsonObject track = value.getAsJsonObject().deepCopy();
            JsonArray kept = new JsonArray();
            for (JsonElement tierValue : tiers(track)) {
                JsonObject tier = tierValue.getAsJsonObject();
                if (tier.has("pool_id") && availablePoolIds.contains(tier.get("pool_id").getAsString()))
                    kept.add(tier.deepCopy());
            }
            track.add("tiers", kept);
            result.add(track);
        }
        return result;
    }

    private static IllegalArgumentException malformed(JsonObject track, String field, String expected) {
        String id = track.has("id") && track.get("id").isJsonPrimitive()
                ? track.get("id").getAsString() : "<unknown>";
        return new IllegalArgumentException("Difficulty track " + id + " field '" + field + "' must be " + expected + ".");
    }
}

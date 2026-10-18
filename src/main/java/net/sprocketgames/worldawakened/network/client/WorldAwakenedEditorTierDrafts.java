package net.sprocketgames.worldawakened.network.client;

import com.google.gson.JsonObject;

/** Small, side-effect-explicit operations used by the Add Tier authoring flow. */
final class WorldAwakenedEditorTierDrafts {
    private WorldAwakenedEditorTierDrafts() {}

    static void appendTier(JsonObject track, String stageId, String poolId) {
        if (stageId == null || stageId.isBlank()) throw new IllegalArgumentException("A tier requires a stage ID");
        if (poolId == null || poolId.isBlank()) throw new IllegalArgumentException("A tier requires a pool ID");
        JsonObject tier = new JsonObject();
        tier.addProperty("stage_id", stageId);
        tier.addProperty("pool_id", poolId);
        WorldAwakenedEditorTrackJson.mutableTiers(track).add(tier);
    }

    static JsonObject copyPool(JsonObject previous, String newPoolId) {
        JsonObject result = previous.deepCopy();
        result.addProperty("id", newPoolId);
        return result;
    }
}

package net.sprocketgames.worldawakened.mutator;

import net.minecraft.world.entity.MobSpawnType;

enum WorldAwakenedMobSpawnOrigin {
    OTHER("other"),
    SPAWNER("spawner"),
    TRIAL_SPAWNER("trial_spawner");

    private final String serializedName;

    WorldAwakenedMobSpawnOrigin(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    static WorldAwakenedMobSpawnOrigin fromMobSpawnType(MobSpawnType spawnType) {
        return switch (spawnType) {
            case SPAWNER -> SPAWNER;
            case TRIAL_SPAWNER -> TRIAL_SPAWNER;
            default -> OTHER;
        };
    }

    static WorldAwakenedMobSpawnOrigin fromSerializedName(String raw) {
        for (WorldAwakenedMobSpawnOrigin origin : values()) {
            if (origin.serializedName.equals(raw)) {
                return origin;
            }
        }
        return OTHER;
    }
}

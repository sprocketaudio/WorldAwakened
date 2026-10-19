package net.sprocketgames.worldawakened.data.definition;

/** Explicit native-authoring/runtime context for a loot profile. */
public enum LootProfileType {
    MOB,
    CHEST;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}

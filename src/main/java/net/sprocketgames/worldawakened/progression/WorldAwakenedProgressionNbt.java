package net.sprocketgames.worldawakened.progression;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class WorldAwakenedProgressionNbt {
    private WorldAwakenedProgressionNbt() {
    }

    static Set<ResourceLocation> readResourceLocationSet(CompoundTag tag, String key) {
        Set<ResourceLocation> values = new LinkedHashSet<>();
        ListTag entries = tag.getList(key, Tag.TAG_STRING);
        for (Tag entry : entries) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) {
                values.add(id);
            }
        }
        return values;
    }

    static void writeResourceLocationSet(CompoundTag tag, String key, Set<ResourceLocation> values) {
        ListTag entries = new ListTag();
        values.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(id -> entries.add(StringTag.valueOf(id)));
        tag.put(key, entries);
    }

    static Set<String> readStringSet(CompoundTag tag, String key) {
        Set<String> values = new LinkedHashSet<>();
        ListTag entries = tag.getList(key, Tag.TAG_STRING);
        for (Tag entry : entries) {
            String value = entry.getAsString();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    static void writeStringSet(CompoundTag tag, String key, Set<String> values) {
        ListTag entries = new ListTag();
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .forEach(value -> entries.add(StringTag.valueOf(value)));
        tag.put(key, entries);
    }

    static Map<ResourceLocation, Long> readStageLongMap(CompoundTag tag, String key) {
        Map<ResourceLocation, Long> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            ResourceLocation stageId = ResourceLocation.tryParse(entry.getString("id"));
            if (stageId != null) {
                values.put(stageId, entry.getLong("value"));
            }
        }
        return values;
    }

    static void writeStageLongMap(CompoundTag tag, String key, Map<ResourceLocation, Long> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("id", entry.getKey().toString());
                    valueTag.putLong("value", entry.getValue());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }

    static Map<ResourceLocation, String> readStageStringMap(CompoundTag tag, String key) {
        Map<ResourceLocation, String> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            ResourceLocation stageId = ResourceLocation.tryParse(entry.getString("id"));
            if (stageId != null) {
                values.put(stageId, entry.getString("value"));
            }
        }
        return values;
    }

    static void writeStageStringMap(CompoundTag tag, String key, Map<ResourceLocation, String> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("id", entry.getKey().toString());
                    valueTag.putString("value", entry.getValue());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }

    static Map<String, ResourceLocation> readStringStageMap(CompoundTag tag, String key) {
        Map<String, ResourceLocation> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            String mapKey = entry.getString("key");
            if (mapKey.isBlank()) {
                continue;
            }
            ResourceLocation stageId = ResourceLocation.tryParse(entry.getString("value"));
            if (stageId != null) {
                values.put(mapKey, stageId);
            }
        }
        return values;
    }

    static void writeStringStageMap(CompoundTag tag, String key, Map<String, ResourceLocation> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("key", entry.getKey());
                    valueTag.putString("value", entry.getValue().toString());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }

    static Map<String, Long> readStringLongMap(CompoundTag tag, String key) {
        Map<String, Long> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            String mapKey = entry.getString("key");
            if (!mapKey.isBlank()) {
                values.put(mapKey, entry.getLong("value"));
            }
        }
        return values;
    }

    static void writeStringLongMap(CompoundTag tag, String key, Map<String, Long> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("key", entry.getKey());
                    valueTag.putLong("value", entry.getValue());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }

    static Map<String, Double> readStringDoubleMap(CompoundTag tag, String key) {
        Map<String, Double> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            String mapKey = entry.getString("key");
            if (!mapKey.isBlank()) {
                values.put(mapKey, entry.getDouble("value"));
            }
        }
        return values;
    }

    static Map<String, Integer> readStringIntMap(CompoundTag tag, String key) {
        Map<String, Integer> values = new LinkedHashMap<>();
        ListTag entries = tag.getList(key, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entries) {
            if (!(rawEntry instanceof CompoundTag entry)) {
                continue;
            }
            String mapKey = entry.getString("key");
            if (!mapKey.isBlank()) {
                values.put(mapKey, entry.getInt("value"));
            }
        }
        return values;
    }

    static void writeStringDoubleMap(CompoundTag tag, String key, Map<String, Double> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("key", entry.getKey());
                    valueTag.putDouble("value", entry.getValue());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }

    static void writeStringIntMap(CompoundTag tag, String key, Map<String, Integer> values) {
        ListTag entries = new ListTag();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag valueTag = new CompoundTag();
                    valueTag.putString("key", entry.getKey());
                    valueTag.putInt("value", entry.getValue());
                    entries.add(valueTag);
                });
        tag.put(key, entries);
    }
}


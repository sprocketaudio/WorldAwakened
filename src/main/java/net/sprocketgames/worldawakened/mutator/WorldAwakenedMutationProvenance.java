package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class WorldAwakenedMutationProvenance {
    public static final String WA_MUTATION_SOURCE_POOL = "WA_MUTATION_SOURCE_POOL";
    public static final String WA_MUTATION_SOURCE_RULES = "WA_MUTATION_SOURCE_RULES";
    public static final String WA_MUTATION_IDS = "WA_MUTATION_IDS";
    public static final String WA_MUTATION_COMPONENTS = "WA_MUTATION_COMPONENTS";
    public static final String WA_MUTATION_STAGE_CONTEXT = "WA_MUTATION_STAGE_CONTEXT";
    public static final String WA_MUTATION_TRACE_ID = "WA_MUTATION_TRACE_ID";
    public static final String WA_MUTATION_FAILED_COMPONENTS = "WA_MUTATION_FAILED_COMPONENTS";
    public static final String WA_MUTATION_DEPTH = "WA_MUTATION_DEPTH";
    public static final String WA_ORIGIN = "WA_ORIGIN";
    public static final String WA_PENDING_SPAWN_ORIGIN = "WA_PENDING_SPAWN_ORIGIN";
    public static final String WA_MUTATION_PIPELINE_PROCESSED = "WA_MUTATION_PIPELINE_PROCESSED";
    public static final String ORIGIN_MUTATOR_SPAWN = "worldawakened:mutator_spawn";
    public static final String ORIGIN_SPAWN_PIPELINE = "worldawakened:spawn_pipeline";

    private WorldAwakenedMutationProvenance() {
    }

    public static void writeProvenance(CompoundTag tag, MutationProvenancePayload payload) {
        if (payload.sourcePoolId().isPresent()) {
            tag.putString(WA_MUTATION_SOURCE_POOL, payload.sourcePoolId().get().toString());
        } else {
            tag.remove(WA_MUTATION_SOURCE_POOL);
        }
        tag.put(WA_MUTATION_SOURCE_RULES, writeResourceLocationList(payload.sourceRuleIds()));
        tag.put(WA_MUTATION_IDS, writeResourceLocationList(payload.mutationIds()));
        tag.put(WA_MUTATION_COMPONENTS, writeResourceLocationList(payload.componentIds()));
        tag.put(WA_MUTATION_STAGE_CONTEXT, writeResourceLocationList(payload.stageContext()));
        if (!payload.traceId().isBlank()) {
            tag.putString(WA_MUTATION_TRACE_ID, payload.traceId());
        } else {
            tag.remove(WA_MUTATION_TRACE_ID);
        }
        tag.putInt(WA_MUTATION_DEPTH, Math.max(0, payload.mutationDepth()));
        if (!payload.originMarker().isBlank()) {
            tag.putString(WA_ORIGIN, payload.originMarker());
        } else {
            tag.remove(WA_ORIGIN);
        }
        tag.put(WA_MUTATION_FAILED_COMPONENTS, writeComponentFailures(payload.failedComponents()));
    }

    public static MutationProvenanceView read(CompoundTag tag) {
        Optional<ResourceLocation> sourcePoolId = Optional.empty();
        if (tag.contains(WA_MUTATION_SOURCE_POOL, Tag.TAG_STRING)) {
            sourcePoolId = Optional.ofNullable(ResourceLocation.tryParse(tag.getString(WA_MUTATION_SOURCE_POOL)));
        }

        List<ResourceLocation> sourceRules = readResourceLocationList(tag, WA_MUTATION_SOURCE_RULES);
        List<ResourceLocation> mutationIds = readResourceLocationList(tag, WA_MUTATION_IDS);
        List<ResourceLocation> componentIds = readResourceLocationList(tag, WA_MUTATION_COMPONENTS);
        List<ResourceLocation> stageContext = readResourceLocationList(tag, WA_MUTATION_STAGE_CONTEXT);
        String traceId = tag.contains(WA_MUTATION_TRACE_ID, Tag.TAG_STRING)
                ? tag.getString(WA_MUTATION_TRACE_ID)
                : "";
        int depth = tag.contains(WA_MUTATION_DEPTH, Tag.TAG_INT)
                ? Math.max(0, tag.getInt(WA_MUTATION_DEPTH))
                : 0;
        String origin = tag.contains(WA_ORIGIN, Tag.TAG_STRING)
                ? tag.getString(WA_ORIGIN)
                : "";
        List<ComponentFailureEntry> failedComponents = readComponentFailures(tag, WA_MUTATION_FAILED_COMPONENTS);
        boolean hasProvenance = sourcePoolId.isPresent()
                || !mutationIds.isEmpty()
                || !componentIds.isEmpty()
                || !traceId.isBlank();
        return new MutationProvenanceView(
                hasProvenance,
                sourcePoolId,
                sourceRules,
                mutationIds,
                componentIds,
                stageContext,
                traceId,
                depth,
                origin,
                failedComponents,
                isPipelineProcessed(tag));
    }

    public static void markPipelineProcessed(CompoundTag tag) {
        tag.putBoolean(WA_MUTATION_PIPELINE_PROCESSED, true);
    }

    public static boolean isPipelineProcessed(CompoundTag tag) {
        return tag.contains(WA_MUTATION_PIPELINE_PROCESSED, Tag.TAG_BYTE)
                && tag.getBoolean(WA_MUTATION_PIPELINE_PROCESSED);
    }

    public static void markMutatorSpawnOrigin(CompoundTag tag, int parentDepth) {
        tag.putString(WA_ORIGIN, ORIGIN_MUTATOR_SPAWN);
        tag.putInt(WA_MUTATION_DEPTH, Math.max(1, parentDepth + 1));
    }

    public static void markPendingSpawnOrigin(CompoundTag tag, WorldAwakenedMobSpawnOrigin origin) {
        if (origin == WorldAwakenedMobSpawnOrigin.OTHER) {
            tag.remove(WA_PENDING_SPAWN_ORIGIN);
            return;
        }
        tag.putString(WA_PENDING_SPAWN_ORIGIN, origin.serializedName());
    }

    public static WorldAwakenedMobSpawnOrigin consumePendingSpawnOrigin(CompoundTag tag) {
        if (!tag.contains(WA_PENDING_SPAWN_ORIGIN, Tag.TAG_STRING)) {
            return WorldAwakenedMobSpawnOrigin.OTHER;
        }
        WorldAwakenedMobSpawnOrigin origin =
                WorldAwakenedMobSpawnOrigin.fromSerializedName(tag.getString(WA_PENDING_SPAWN_ORIGIN));
        tag.remove(WA_PENDING_SPAWN_ORIGIN);
        return origin;
    }

    public static boolean isRecursionBlocked(CompoundTag tag) {
        if (!tag.contains(WA_ORIGIN, Tag.TAG_STRING)) {
            return false;
        }
        String origin = tag.getString(WA_ORIGIN);
        if (!ORIGIN_MUTATOR_SPAWN.equals(origin)) {
            return false;
        }
        int depth = tag.contains(WA_MUTATION_DEPTH, Tag.TAG_INT) ? Math.max(0, tag.getInt(WA_MUTATION_DEPTH)) : 0;
        return depth >= 1;
    }

    public static int mutationDepth(CompoundTag tag) {
        if (!tag.contains(WA_MUTATION_DEPTH, Tag.TAG_INT)) {
            return 0;
        }
        return Math.max(0, tag.getInt(WA_MUTATION_DEPTH));
    }

    private static ListTag writeResourceLocationList(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.stream()
                .filter(java.util.Objects::nonNull)
                .map(ResourceLocation::toString)
                .map(StringTag::valueOf)
                .forEach(list::add);
        return list;
    }

    private static List<ResourceLocation> readResourceLocationList(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            ResourceLocation parsed = ResourceLocation.tryParse(list.getString(index));
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        return List.copyOf(ids);
    }

    private static ListTag writeComponentFailures(List<ComponentFailureEntry> failures) {
        ListTag list = new ListTag();
        for (ComponentFailureEntry failure : failures) {
            CompoundTag entry = new CompoundTag();
            if (failure.mutatorId().isPresent()) {
                entry.putString("mutator", failure.mutatorId().get().toString());
            }
            entry.putString("component", failure.componentType().toString());
            entry.putString("code", failure.code());
            entry.putString("detail", failure.detail());
            list.add(entry);
        }
        return list;
    }

    private static List<ComponentFailureEntry> readComponentFailures(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<ComponentFailureEntry> failures = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation componentType = ResourceLocation.tryParse(entry.getString("component"));
            if (componentType == null) {
                continue;
            }
            Optional<ResourceLocation> mutatorId = Optional.empty();
            if (entry.contains("mutator", Tag.TAG_STRING)) {
                mutatorId = Optional.ofNullable(ResourceLocation.tryParse(entry.getString("mutator")));
            }
            String code = entry.contains("code", Tag.TAG_STRING) ? entry.getString("code") : "";
            String detail = entry.contains("detail", Tag.TAG_STRING) ? entry.getString("detail") : "";
            failures.add(new ComponentFailureEntry(mutatorId, componentType, code, detail));
        }
        failures.sort(Comparator
                .comparing((ComponentFailureEntry entry) -> entry.mutatorId().map(ResourceLocation::toString).orElse(""))
                .thenComparing(entry -> entry.componentType().toString())
                .thenComparing(ComponentFailureEntry::code)
                .thenComparing(ComponentFailureEntry::detail));
        return List.copyOf(failures);
    }

    public record MutationProvenancePayload(
            Optional<ResourceLocation> sourcePoolId,
            List<ResourceLocation> sourceRuleIds,
            List<ResourceLocation> mutationIds,
            List<ResourceLocation> componentIds,
            List<ResourceLocation> stageContext,
            String traceId,
            int mutationDepth,
            String originMarker,
            List<ComponentFailureEntry> failedComponents) {
    }

    public record ComponentFailureEntry(
            Optional<ResourceLocation> mutatorId,
            ResourceLocation componentType,
            String code,
            String detail) {
    }

    public record MutationProvenanceView(
            boolean hasProvenance,
            Optional<ResourceLocation> sourcePoolId,
            List<ResourceLocation> sourceRuleIds,
            List<ResourceLocation> mutationIds,
            List<ResourceLocation> componentIds,
            List<ResourceLocation> stageContext,
            String traceId,
            int mutationDepth,
            String originMarker,
            List<ComponentFailureEntry> failedComponents,
            boolean pipelineProcessed) {
    }
}

package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    public static final String WA_MUTATION_INSTANCE_ID = "WA_MUTATION_INSTANCE_ID";
    public static final String WA_MUTATION_TOTAL_POWER = "WA_MUTATION_TOTAL_POWER";
    public static final String WA_MUTATION_RARITY = "WA_MUTATION_RARITY";
    public static final String WA_MUTATION_RESOLVED_COMPONENTS = "WA_MUTATION_RESOLVED_COMPONENTS";
    public static final String WA_MUTATION_GENERATION_SOURCE = "WA_MUTATION_GENERATION_SOURCE";
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
        tag.put(WA_MUTATION_RESOLVED_COMPONENTS, writeResolvedComponents(payload.resolvedComponents()));
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
        if (!payload.instanceId().isBlank()) tag.putString(WA_MUTATION_INSTANCE_ID, payload.instanceId());
        else tag.remove(WA_MUTATION_INSTANCE_ID);
        tag.putInt(WA_MUTATION_TOTAL_POWER, Math.max(0, payload.totalPower()));
        payload.rarityId().ifPresentOrElse(value -> tag.putString(WA_MUTATION_RARITY, value.toString()),
                () -> tag.remove(WA_MUTATION_RARITY));
        if (!payload.generationSource().isBlank()) tag.putString(WA_MUTATION_GENERATION_SOURCE, payload.generationSource());
        else tag.remove(WA_MUTATION_GENERATION_SOURCE);
    }

    public static MutationProvenanceView read(CompoundTag tag) {
        Optional<ResourceLocation> sourcePoolId = Optional.empty();
        if (tag.contains(WA_MUTATION_SOURCE_POOL, Tag.TAG_STRING)) {
            sourcePoolId = Optional.ofNullable(ResourceLocation.tryParse(tag.getString(WA_MUTATION_SOURCE_POOL)));
        }

        List<ResourceLocation> sourceRules = readResourceLocationList(tag, WA_MUTATION_SOURCE_RULES);
        List<ResourceLocation> mutationIds = readResourceLocationList(tag, WA_MUTATION_IDS);
        List<ResourceLocation> componentIds = readResourceLocationList(tag, WA_MUTATION_COMPONENTS);
        List<ResolvedMutationComponent> resolvedComponents = readResolvedComponents(tag, WA_MUTATION_RESOLVED_COMPONENTS);
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
        String instanceId = tag.contains(WA_MUTATION_INSTANCE_ID, Tag.TAG_STRING) ? tag.getString(WA_MUTATION_INSTANCE_ID) : "";
        int totalPower = tag.contains(WA_MUTATION_TOTAL_POWER, Tag.TAG_INT) ? Math.max(0, tag.getInt(WA_MUTATION_TOTAL_POWER)) : 0;
        Optional<ResourceLocation> rarityId = tag.contains(WA_MUTATION_RARITY, Tag.TAG_STRING)
                ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString(WA_MUTATION_RARITY))) : Optional.empty();
        String generationSource = tag.contains(WA_MUTATION_GENERATION_SOURCE, Tag.TAG_STRING)
                ? tag.getString(WA_MUTATION_GENERATION_SOURCE) : "curated";
        boolean hasProvenance = sourcePoolId.isPresent()
                || !mutationIds.isEmpty()
                || !componentIds.isEmpty()
                || !resolvedComponents.isEmpty()
                || !traceId.isBlank();
        return new MutationProvenanceView(
                hasProvenance,
                sourcePoolId,
                sourceRules,
                mutationIds,
                componentIds,
                resolvedComponents,
                stageContext,
                traceId,
                depth,
                origin,
                failedComponents,
                isPipelineProcessed(tag),
                instanceId,
                totalPower,
                rarityId,
                generationSource);
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

    private static ListTag writeResolvedComponents(List<ResolvedMutationComponent> components) {
        ListTag list = new ListTag();
        for (ResolvedMutationComponent component : components) {
            CompoundTag entry = new CompoundTag();
            entry.putString("source", component.sourceEffectId().toString());
            entry.putString("type", component.componentType().toString());
            entry.putString("parameters", component.parameters().toString());
            entry.putInt("power", Math.max(0, component.power()));
            list.add(entry);
        }
        return list;
    }

    private static List<ResolvedMutationComponent> readResolvedComponents(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<ResolvedMutationComponent> components = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            ResourceLocation source = ResourceLocation.tryParse(entry.getString("source"));
            ResourceLocation type = ResourceLocation.tryParse(entry.getString("type"));
            if (source == null || type == null || !entry.contains("parameters", Tag.TAG_STRING)) {
                continue;
            }
            try {
                if (!JsonParser.parseString(entry.getString("parameters")).isJsonObject()) {
                    continue;
                }
                JsonObject parameters = JsonParser.parseString(entry.getString("parameters")).getAsJsonObject();
                components.add(new ResolvedMutationComponent(
                        source, type, parameters, Math.max(0, entry.getInt("power"))));
            } catch (RuntimeException ignored) {
                // Older or externally edited entity data must not prevent an entity from loading.
            }
        }
        return List.copyOf(components);
    }

    public record MutationProvenancePayload(
            Optional<ResourceLocation> sourcePoolId,
            List<ResourceLocation> sourceRuleIds,
            List<ResourceLocation> mutationIds,
            List<ResourceLocation> componentIds,
            List<ResolvedMutationComponent> resolvedComponents,
            List<ResourceLocation> stageContext,
            String traceId,
            int mutationDepth,
            String originMarker,
            List<ComponentFailureEntry> failedComponents,
            String instanceId,
            int totalPower,
            Optional<ResourceLocation> rarityId,
            String generationSource) {
        public MutationProvenancePayload(
                Optional<ResourceLocation> sourcePoolId, List<ResourceLocation> sourceRuleIds,
                List<ResourceLocation> mutationIds, List<ResourceLocation> componentIds,
                List<ResourceLocation> stageContext, String traceId, int mutationDepth,
                String originMarker, List<ComponentFailureEntry> failedComponents) {
            this(sourcePoolId, sourceRuleIds, mutationIds, componentIds, List.of(), stageContext, traceId, mutationDepth,
                    originMarker, failedComponents, "", 0, Optional.empty(), "curated");
        }

        /** Compatibility constructor for persisted-result callers that predate explicit source persistence. */
        public MutationProvenancePayload(
                Optional<ResourceLocation> sourcePoolId, List<ResourceLocation> sourceRuleIds,
                List<ResourceLocation> mutationIds, List<ResourceLocation> componentIds,
                List<ResolvedMutationComponent> resolvedComponents, List<ResourceLocation> stageContext,
                String traceId, int mutationDepth, String originMarker, List<ComponentFailureEntry> failedComponents,
                String instanceId, int totalPower, Optional<ResourceLocation> rarityId) {
            this(sourcePoolId, sourceRuleIds, mutationIds, componentIds, resolvedComponents, stageContext, traceId,
                    mutationDepth, originMarker, failedComponents, instanceId, totalPower, rarityId, "curated");
        }
    }

    public record ResolvedMutationComponent(
            ResourceLocation sourceEffectId,
            ResourceLocation componentType,
            JsonObject parameters,
            int power) {
        public ResolvedMutationComponent {
            parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
        }
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
            List<ResolvedMutationComponent> resolvedComponents,
            List<ResourceLocation> stageContext,
            String traceId,
            int mutationDepth,
            String originMarker,
            List<ComponentFailureEntry> failedComponents,
            boolean pipelineProcessed,
            String instanceId,
            int totalPower,
            Optional<ResourceLocation> rarityId,
            String generationSource) {
    }
}

package net.sprocketgames.worldawakened.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonElement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.data.validation.WorldAwakenedMutationPoolValidation;

public final class WorldAwakenedProfileOverrideService {
    public static final int MAX_MUTATORS_PER_ENTITY = 8;
    private static final AtomicReference<State> ACTIVE = new AtomicReference<>(new State(0L, Map.of()));
    private static final AtomicReference<Set<ResourceLocation>> LIVING_ENTITY_IDS = new AtomicReference<>(Set.of());

    private WorldAwakenedProfileOverrideService() {}

    public static synchronized boolean bind(MinecraftServer server) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(server.overworld());
        ACTIVE.set(new State(data.revision(), data.mutationPools()));
        java.util.LinkedHashSet<ResourceLocation> living = new java.util.LinkedHashSet<>();
        BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                .sorted(java.util.Comparator.comparing(entry -> entry.getKey().location().toString()))
                .forEach(entry -> {
                    if (isLivingType(server.overworld(), entry.getValue())) living.add(entry.getKey().location());
                });
        LIVING_ENTITY_IDS.set(Set.copyOf(living));
        return !data.mutationPools().isEmpty();
    }

    public static synchronized void unbind() { ACTIVE.set(new State(0L, Map.of())); LIVING_ENTITY_IDS.set(Set.of()); }
    public static State state() { return ACTIVE.get(); }
    public static Set<ResourceLocation> livingEntityIds() { return LIVING_ENTITY_IDS.get(); }

    public static WorldAwakenedDatapackSnapshot overlay(WorldAwakenedDatapackSnapshot source) {
        Map<ResourceLocation, MutationPoolDefinition> overrides = ACTIVE.get().mutationPools();
        if (overrides.isEmpty()) return source;
        LinkedHashMap<ResourceLocation, MutationPoolDefinition> pools = new LinkedHashMap<>(source.data().mutationPools());
        overrides.forEach((id, definition) -> {
            if (pools.containsKey(id)) pools.put(id, definition);
        });
        WorldAwakenedCompiledData data = source.data();
        return new WorldAwakenedDatapackSnapshot(source.generation(), Instant.now(), new WorldAwakenedCompiledData(
                data.stages(), data.triggerRules(), data.rules(), data.ascensionRewards(), data.ascensionOffers(),
                data.mobMutators(), Map.copyOf(pools), data.lootProfiles(), data.invasionProfiles(),
                data.integrationProfiles(), data.entityBossFlags(), data.bossClassifier()), source.validationSummary());
    }

    public static List<String> validate(MinecraftServer server, MutationPoolDefinition candidate,
            WorldAwakenedDatapackSnapshot source) {
        List<String> errors = new ArrayList<>();
        if (!source.data().mutationPools().containsKey(candidate.id())) {
            return List.of("The source mutation pool no longer exists.");
        }
        errors.addAll(WorldAwakenedMutationPoolValidation.validate(candidate, source.data().mobMutators()));
        int maximum = candidate.maxMutatorsPerEntity();
        if (maximum < 1 || maximum > MAX_MUTATORS_PER_ENTITY) {
            errors.add("Maximum mutations per mob must be between 1 and " + MAX_MUTATORS_PER_ENTITY + ".");
        }
        for (ResourceLocation entityId : candidate.eligibleEntities()) {
            if (!LIVING_ENTITY_IDS.get().contains(entityId)) {
                errors.add("Unknown or non-living entity: " + entityId);
            }
        }
        for (ResourceLocation dimensionId : candidate.eligibleDimensions()) {
            if (server.levelKeys().stream().noneMatch(key -> key.location().equals(dimensionId))) {
                errors.add("Dimension is not available on this server: " + dimensionId);
            }
        }
        return List.copyOf(errors);
    }

    public static synchronized ApplyResult apply(ServerLevel level, long expectedRevision,
            MutationPoolDefinition candidate, WorldAwakenedDatapackSnapshot source) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(new State(data.revision(), data.mutationPools()));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed pool and try again.");
        }
        List<String> errors = validate(level.getServer(), candidate, source);
        if (!errors.isEmpty()) return new ApplyResult(false, String.join(" ", errors));
        data.put(candidate);
        ACTIVE.set(new State(data.revision(), data.mutationPools()));
        return new ApplyResult(true, "Mutation pool applied successfully.");
    }

    public static synchronized ApplyResult revert(ServerLevel level, long expectedRevision, ResourceLocation id,
            WorldAwakenedDatapackSnapshot source) {
        WorldAwakenedProfileOverrideSavedData data = WorldAwakenedProfileOverrideSavedData.get(level);
        if (expectedRevision != data.revision()) {
            ACTIVE.set(new State(data.revision(), data.mutationPools()));
            return new ApplyResult(false, "The profile changed on the server. Review the refreshed values and try again.");
        }
        if (!source.data().mutationPools().containsKey(id)) return new ApplyResult(false, "The source pool no longer exists.");
        data.remove(id);
        ACTIVE.set(new State(data.revision(), data.mutationPools()));
        return new ApplyResult(true, "Mutation pool reverted to its source definition.");
    }

    public static boolean isLivingType(ServerLevel level, EntityType<?> type) {
        Entity entity = null;
        try {
            entity = type.create(level);
            return entity instanceof LivingEntity;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (entity != null) entity.discard();
        }
    }

    public static ResourceLocation mutatorId(JsonElement entry) {
        if (entry == null) return null;
        if (entry.isJsonPrimitive()) return ResourceLocation.tryParse(entry.getAsString());
        if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
            return ResourceLocation.tryParse(entry.getAsJsonObject().get("id").getAsString());
        }
        return null;
    }

    public static int mutatorWeight(JsonElement entry) {
        return entry != null && entry.isJsonObject() && entry.getAsJsonObject().has("weight")
                ? entry.getAsJsonObject().get("weight").getAsInt() : 1;
    }

    public record State(long revision, Map<ResourceLocation, MutationPoolDefinition> mutationPools) {
        public State { mutationPools = Map.copyOf(mutationPools); }
    }
    public record ApplyResult(boolean accepted, String message) {}
}

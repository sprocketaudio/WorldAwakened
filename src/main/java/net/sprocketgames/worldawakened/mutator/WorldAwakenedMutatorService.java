package net.sprocketgames.worldawakened.mutator;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.WorldAwakenedConstants;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationComponentDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.difficulty.WorldAwakenedEffectiveDifficultyScalarService;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentRegistry;
import net.sprocketgames.worldawakened.mutator.component.WorldAwakenedMutationComponentType;
import net.sprocketgames.worldawakened.progression.WorldAwakenedProgressionMode;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageRegistry;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedEntityContextView;

public final class WorldAwakenedMutatorService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MUTATORS_PER_SPAWN_BUDGET = 8;
    private static final int MAX_COMPONENTS_PER_MUTATOR_BUDGET = 10;
    private static final int MAX_PRESSURE_SNAPSHOTS = 96;
    private static final double PLAYER_SPAWN_ATTRIBUTION_RADIUS = 128.0D;
    private static final Set<String> SUPPORTED_APPLICATION_CONTEXTS = Set.of("on_spawn");
    private static final Set<Holder<Attribute>> MANAGED_ATTRIBUTES = Set.of(
            Attributes.MAX_HEALTH,
            Attributes.ATTACK_DAMAGE,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.MOVEMENT_SPEED,
            Attributes.FOLLOW_RANGE,
            Attributes.KNOCKBACK_RESISTANCE);

    private final WorldAwakenedDatapackService datapackService;
    private final WorldAwakenedStageService stageService;
    private final WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService;
    private final AtomicReference<CachedCompiledGraph> cache = new AtomicReference<>(new CachedCompiledGraph(0L, CompiledGraph.empty()));
    private final AtomicLong traceCounter = new AtomicLong(0L);
    private final AtomicLong pressureSnapshotCounter = new AtomicLong(0L);
    private final Deque<PressureEvaluationSnapshot> pressureSnapshots = new ArrayDeque<>();

    public WorldAwakenedMutatorService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService) {
        this(datapackService, stageService, new WorldAwakenedEffectiveDifficultyScalarService());
    }

    public WorldAwakenedMutatorService(
            WorldAwakenedDatapackService datapackService,
            WorldAwakenedStageService stageService,
            WorldAwakenedEffectiveDifficultyScalarService difficultyScalarService) {
        this.datapackService = datapackService;
        this.stageService = stageService;
        this.difficultyScalarService = difficultyScalarService;
    }

    public MutatorRunResult onMobSpawn(ServerLevel level, Mob mob, WorldAwakenedMobSpawnOrigin spawnOrigin) {
        EvaluationRequest request = new EvaluationRequest(
                MutationRunMode.LIVE_SPAWN,
                level,
                mob.blockPosition(),
                mob.getType(),
                mob,
                spawnOrigin,
                false,
                Optional.empty(),
                Optional.empty());
        return evaluateInternal(request);
    }

    public MutatorRunResult debugEvaluate(ServerLevel level, EntityType<?> entityType, BlockPos pos) {
        EvaluationRequest request = new EvaluationRequest(
                MutationRunMode.EVALUATE,
                level,
                pos,
                entityType,
                null,
                WorldAwakenedMobSpawnOrigin.OTHER,
                true,
                Optional.empty(),
                Optional.empty());
        return evaluateInternal(request);
    }

    public MutatorRunResult debugForcePool(
            ServerLevel level,
            EntityType<?> entityType,
            BlockPos pos,
            ResourceLocation forcedPoolId) {
        EvaluationRequest request = new EvaluationRequest(
                MutationRunMode.FORCE_POOL,
                level,
                pos,
                entityType,
                null,
                WorldAwakenedMobSpawnOrigin.OTHER,
                true,
                Optional.of(forcedPoolId),
                Optional.empty());
        return evaluateInternal(request);
    }

    public MutatorRunResult debugForceMutator(
            ServerLevel level,
            EntityType<?> entityType,
            BlockPos pos,
            ResourceLocation forcedMutatorId) {
        EvaluationRequest request = new EvaluationRequest(
                MutationRunMode.FORCE_MUTATOR,
                level,
                pos,
                entityType,
                null,
                WorldAwakenedMobSpawnOrigin.OTHER,
                true,
                Optional.empty(),
                Optional.of(forcedMutatorId));
        return evaluateInternal(request);
    }

    public MutatorRunResult debugSpawnTest(
            ServerLevel level,
            EntityType<?> entityType,
            BlockPos pos) {
        Entity created = entityType.create(level);
        if (!(created instanceof Mob mob)) {
            return MutatorRunResult.skipped(
                    nextTraceId(),
                    MutationRunMode.LIVE_TEST,
                    true,
                    WorldAwakenedDiagnosticCodes.DEBUG_MUTATOR_TARGET_INVALID,
                    "spawn test requires an entity type that creates a Mob",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entityType),
                    level.dimension().location(),
                    pos);
        }
        mob.moveTo(
                pos.getX() + 0.5D,
                pos.getY(),
                pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F,
                0.0F);
        MutatorRunResult result = evaluateInternal(new EvaluationRequest(
                MutationRunMode.LIVE_TEST,
                level,
                pos,
                entityType,
                mob,
                WorldAwakenedMobSpawnOrigin.OTHER,
                false,
                Optional.empty(),
                Optional.empty()));
        boolean added = level.addFreshEntity(mob);
        return result.withSpawnOutcome(added, Optional.of(mob.getStringUUID()));
    }

    public void markMutatorSpawnOrigin(Entity entity, int parentDepth) {
        WorldAwakenedMutationProvenance.markMutatorSpawnOrigin(entity.getPersistentData(), parentDepth);
    }

    public Optional<PressureEvaluationSnapshot> latestPressureSnapshot() {
        synchronized (pressureSnapshots) {
            return Optional.ofNullable(pressureSnapshots.peekLast());
        }
    }

    public Optional<PressureEvaluationSnapshot> pressureSnapshot(long snapshotId) {
        synchronized (pressureSnapshots) {
            for (PressureEvaluationSnapshot snapshot : pressureSnapshots) {
                if (snapshot.snapshotId() == snapshotId) {
                    return Optional.of(snapshot);
                }
            }
        }
        return Optional.empty();
    }

    public List<Long> pressureSnapshotIds() {
        synchronized (pressureSnapshots) {
            List<Long> ids = new ArrayList<>(pressureSnapshots.size());
            for (PressureEvaluationSnapshot snapshot : pressureSnapshots) {
                ids.add(snapshot.snapshotId());
            }
            return List.copyOf(ids);
        }
    }

    public void onMobTick(ServerLevel level, Mob mob) {
        List<WorldAwakenedVisualParticleEmitters.EmitterDefinition> emitters =
                WorldAwakenedVisualParticleEmitters.readEmitters(mob.getPersistentData());
        if (emitters.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        for (WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter : emitters) {
            int interval = Math.max(1, emitter.intervalTicks());
            int phase = Math.floorMod(mob.getId(), interval);
            if (Math.floorMod(gameTime + phase, (long) interval) != 0L) {
                continue;
            }
            Optional<net.minecraft.core.particles.ParticleOptions> particleOptions = emitter.resolveParticleOptions();
            if (particleOptions.isEmpty()) {
                continue;
            }
            ParticleOffsets offsets = resolveEmissionOffsets(mob, emitter);
            level.sendParticles(
                    particleOptions.get(),
                    mob.getX(),
                    mob.getY() + (mob.getBbHeight() * 0.5D),
                    mob.getZ(),
                    emitter.count(),
                    offsets.x(),
                    offsets.y(),
                    offsets.z(),
                    emitter.speed());
        }
    }

    private static ParticleOffsets resolveEmissionOffsets(
            Mob mob,
            WorldAwakenedVisualParticleEmitters.EmitterDefinition emitter) {
        double x = emitter.offsetX();
        double y = emitter.offsetY();
        double z = emitter.offsetZ();
        if (emitter.kind() == WorldAwakenedVisualParticleEmitters.EmitterKind.EFFECT_VISUAL) {
            // Effect-style visuals should remain visible on larger mobs (for example slimes) instead of
            // getting lost inside the body volume when using small fixed offsets.
            double halfWidth = Math.max(0.0D, mob.getBbWidth() * 0.5D);
            x = Math.max(x, halfWidth * 0.9D);
            z = Math.max(z, halfWidth * 0.9D);
            x = Math.min(x, 4.0D);
            z = Math.min(z, 4.0D);
        }
        return new ParticleOffsets(x, y, z);
    }

    private record ParticleOffsets(double x, double y, double z) {
    }

    public MutationInspectView inspectEntity(Entity entity) {
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        WorldAwakenedMutationProvenance.MutationProvenanceView provenance = WorldAwakenedMutationProvenance.read(entity.getPersistentData());
        Optional<WorldAwakenedGlowStyleState.GlowStyleDefinition> glowStyle =
                WorldAwakenedGlowStyleState.read(entity.getPersistentData());
        List<WorldAwakenedVisualParticleEmitters.EmitterDefinition> particleVisualEmitters =
                WorldAwakenedVisualParticleEmitters.readEmitters(entity.getPersistentData());
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        Set<ResourceLocation> loadedMutatorIds = snapshot.data().mobMutators().keySet();
        List<ResourceLocation> resolvedMutators = new ArrayList<>();
        List<ResourceLocation> missingMutators = new ArrayList<>();
        for (ResourceLocation mutatorId : provenance.mutationIds()) {
            if (loadedMutatorIds.contains(mutatorId)) {
                resolvedMutators.add(mutatorId);
            } else {
                missingMutators.add(mutatorId);
            }
        }
        resolvedMutators.sort(Comparator.comparing(ResourceLocation::toString));
        missingMutators.sort(Comparator.comparing(ResourceLocation::toString));

        List<AttributeInspection> attributeInspections = entity instanceof LivingEntity livingEntity
                ? inspectManagedAttributes(livingEntity)
                : List.of();
        return new MutationInspectView(
                provenance.hasProvenance(),
                entityTypeId,
                provenance.sourcePoolId(),
                provenance.sourceRuleIds(),
                provenance.mutationIds(),
                provenance.componentIds(),
                provenance.stageContext(),
                provenance.traceId(),
                provenance.mutationDepth(),
                provenance.originMarker(),
                provenance.pipelineProcessed(),
                resolvedMutators,
                missingMutators,
                provenance.failedComponents(),
                glowStyle,
                particleVisualEmitters,
                attributeInspections);
    }

    public MutatorDebugSummary debugSummary() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CompiledGraph graph = compiledGraph(datapackService.pinSnapshot(snapshot, "mutator.debug_summary"));
        return new MutatorDebugSummary(
                snapshot.generation(),
                graph.poolsById().size(),
                graph.mutatorsById().size(),
                graph.selectorIndex().poolIdsByEntityId().size(),
                graph.selectorIndex().poolIdsByEntityTag().size(),
                graph.selectorIndex().wildcardPools().size());
    }

    private MutatorRunResult evaluateInternal(EvaluationRequest request) {
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(request.entityType());
        ResourceLocation dimensionId = request.level().dimension().location();
        String traceId = nextTraceId();
        if (!WorldAwakenedFeatureGates.modEnabled() || !WorldAwakenedCommonConfig.ENABLE_MUTATORS.get()) {
            return MutatorRunResult.skipped(
                    traceId,
                    request.mode(),
                    request.dryRun(),
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    "mutator system is disabled by configuration",
                    entityTypeId,
                    dimensionId,
                    request.pos());
        }

        WorldAwakenedDatapackSnapshot pinnedSnapshot = datapackService.pinSnapshot(
                datapackService.currentSnapshot(),
                "mutator.evaluate:" + request.mode().name().toLowerCase(Locale.ROOT));
        CompiledGraph graph = compiledGraph(pinnedSnapshot);
        SpawnStageResolution stageResolution = resolveSpawnStageResolution(request.level(), request.pos());
        if (stageResolution.attributionRequiredButMissing()) {
            return new MutatorRunResult(
                    traceId,
                    request.mode(),
                    request.dryRun(),
                    false,
                    true,
                    WorldAwakenedDiagnosticCodes.SPAWN_CONTEXT_INVALIDATED,
                    "per_player progression mode requires a nearby player attribution for spawn-time mutators",
                    entityTypeId,
                    dimensionId,
                    request.pos(),
                    request.spawnOrigin().serializedName(),
                    stageResolution.progressionMode(),
                    Optional.empty(),
                    List.of(),
                    graph.poolsById().size(),
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Optional.empty());
        }
        Set<ResourceLocation> stageSnapshot = stageResolution.stageSnapshot();
        WorldAwakenedStageRegistry stageRegistry = stageService.stageRegistry();
        Optional<ResourceLocation> biomeId = resolveBiome(request.level(), request.pos());
        Set<ResourceLocation> entityTags = collectEntityTypeTags(request.entityType());
        String mobCategory = mobCategory(request.entityType());
        boolean entityIsBoss = pinnedSnapshot.data().bossClassifier().isBoss(new EntityContext(entityTypeId, entityTags, mobCategory));
        boolean entityAlreadyMutated = false;
        int mutationDepth = 0;

        if (request.liveEntity() != null) {
            var persistentData = request.liveEntity().getPersistentData();
            if (WorldAwakenedMutationProvenance.isPipelineProcessed(persistentData)) {
                return MutatorRunResult.skipped(
                        traceId,
                        request.mode(),
                        request.dryRun(),
                        WorldAwakenedDiagnosticCodes.SPAWN_REENTRY_BLOCKED,
                        "mutation pipeline already processed for this entity",
                        entityTypeId,
                        dimensionId,
                        request.pos());
            }
            if (WorldAwakenedMutationProvenance.isRecursionBlocked(persistentData)) {
                return MutatorRunResult.skipped(
                        traceId,
                        request.mode(),
                        request.dryRun(),
                        WorldAwakenedDiagnosticCodes.SPAWN_REENTRY_BLOCKED,
                        "recursion guard blocked mutator re-entry for WA-origin spawn",
                        entityTypeId,
                        dimensionId,
                        request.pos());
            }
            WorldAwakenedMutationProvenance.MutationProvenanceView existingProvenance =
                    WorldAwakenedMutationProvenance.read(persistentData);
            entityAlreadyMutated = existingProvenance.hasProvenance();
            mutationDepth = existingProvenance.mutationDepth();
        }

        Mob previewMob = request.liveEntity();
        if (previewMob == null) {
            Entity previewEntity = request.entityType().create(request.level());
            if (previewEntity instanceof Mob mob) {
                previewMob = mob;
                previewMob.moveTo(
                        request.pos().getX() + 0.5D,
                        request.pos().getY(),
                        request.pos().getZ() + 0.5D,
                        0.0F,
                        0.0F);
            }
        }

        EvaluationContext context = new EvaluationContext(
                request.level(),
                request.pos(),
                dimensionId,
                biomeId,
                entityTypeId,
                entityTags,
                mobCategory,
                entityIsBoss,
                entityAlreadyMutated,
                request.spawnOrigin(),
                stageSnapshot,
                stageRegistry,
                OptionalLongValue.of(Math.max(0L, request.level().getDayTime() / 24000L)),
                request.level().getGameTime(),
                loadedMods(),
                configToggles(),
                stageResolution.attributedPlayer(),
                request.liveEntity(),
                previewMob,
                mutationDepth);

        LinkedHashSet<ResourceLocation> indexedCandidatePoolIds = graph.selectorIndex()
                .candidatePoolIds(context.entityTypeId(), context.entityTags());
        List<ResourceLocation> eligiblePoolIds = new ArrayList<>();
        List<RejectedObject> rejectedPools = new ArrayList<>();
        for (ResourceLocation poolId : indexedCandidatePoolIds) {
            CompiledPool pool = graph.poolsById().get(poolId);
            if (pool == null) {
                continue;
            }
            Optional<RejectedObject> rejection = evaluatePoolFilters(pool, context, traceId);
            if (rejection.isPresent()) {
                rejectedPools.add(rejection.get());
                continue;
            }
            eligiblePoolIds.add(pool.id());
        }

        Optional<ResourceLocation> selectedPoolId = selectPool(
                request.forcedPoolId(),
                indexedCandidatePoolIds,
                eligiblePoolIds,
                rejectedPools,
                graph.poolsById(),
                traceId,
                context);
        if (selectedPoolId.isEmpty()) {
            markProcessedIfLive(context.liveMob());
            return new MutatorRunResult(
                    traceId,
                    request.mode(),
                    request.dryRun(),
                    false,
                    false,
                    "",
                    "",
                    context.entityTypeId(),
                    context.dimensionId(),
                    context.pos(),
                    context.spawnOrigin().serializedName(),
                    stageResolution.progressionMode(),
                    stageResolution.attributedPlayer().map(WorldAwakenedMutatorService::toAttributedPlayerView),
                    sortedResourceLocations(context.stageSnapshot()),
                    graph.poolsById().size(),
                    indexedCandidatePoolIds.size(),
                    List.copyOf(indexedCandidatePoolIds),
                    List.copyOf(eligiblePoolIds),
                    List.copyOf(rejectedPools),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Optional.empty());
        }

        CompiledPool selectedPool = graph.poolsById().get(selectedPoolId.get());
        if (selectedPool == null) {
            markProcessedIfLive(context.liveMob());
            return MutatorRunResult.skipped(
                    traceId,
                    request.mode(),
                    request.dryRun(),
                    WorldAwakenedDiagnosticCodes.DEBUG_POOL_NOT_FOUND,
                    "selected pool was not available in compiled graph",
                    context.entityTypeId(),
                    context.dimensionId(),
                    context.pos());
        }

        MutationChanceResult chanceResult = evaluateMutationChance(
                selectedPool,
                request.mode(),
                context,
                traceId);
        recordPressureSnapshot(
                context,
                stageResolution.progressionMode(),
                selectedPool.id(),
                request.mode(),
                chanceResult,
                traceId);
        if (!chanceResult.passed()) {
            markProcessedIfLive(context.liveMob());
            return new MutatorRunResult(
                    traceId,
                    request.mode(),
                    request.dryRun(),
                    false,
                    false,
                    WorldAwakenedDiagnosticCodes.MUTATION_CHANCE_FAILED,
                    "selected pool failed mutation_chance evaluation",
                    context.entityTypeId(),
                    context.dimensionId(),
                    context.pos(),
                    context.spawnOrigin().serializedName(),
                    stageResolution.progressionMode(),
                    stageResolution.attributedPlayer().map(WorldAwakenedMutatorService::toAttributedPlayerView),
                    sortedResourceLocations(context.stageSnapshot()),
                    graph.poolsById().size(),
                    indexedCandidatePoolIds.size(),
                    List.copyOf(indexedCandidatePoolIds),
                    List.copyOf(eligiblePoolIds),
                    List.copyOf(rejectedPools),
                    Optional.of(selectedPool.id()),
                    Optional.of(chanceResult),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Optional.empty());
        }

        int configuredDefaultCap = Math.max(0, WorldAwakenedCommonConfig.MAX_MUTATORS_PER_MOB.get());
        int requestedCap = Math.max(0, selectedPool.maxMutatorsPerEntity().orElse(configuredDefaultCap));
        int enforcedCap = Math.min(requestedCap, MAX_MUTATORS_PER_SPAWN_BUDGET);

        List<EligibleMutatorCandidate> eligibleMutators = new ArrayList<>();
        List<RejectedObject> rejectedMutators = new ArrayList<>();
        for (WeightedMutatorRef mutatorRef : selectedPool.mutators()) {
            CompiledMutator mutator = graph.mutatorsById().get(mutatorRef.mutatorId());
            if (mutator == null) {
                rejectedMutators.add(new RejectedObject(
                        mutatorRef.mutatorId(),
                        WorldAwakenedDiagnosticCodes.RUNTIME_INSTANCE_MISSING_DEFINITION,
                        "pool references mutator that is missing from compiled graph"));
                continue;
            }
            Optional<RejectedObject> rejection = evaluateMutatorFilters(mutatorRef, mutator, context, traceId);
            if (rejection.isPresent()) {
                rejectedMutators.add(rejection.get());
                continue;
            }
            ComponentPreview preview = previewComponents(mutator, context.previewMob(), traceId, context);
            if (preview.supportedComponents().isEmpty()) {
                String detail = preview.failures().isEmpty()
                        ? "mutator has no supported components for this runtime context"
                        : preview.failures().get(0).detail();
                rejectedMutators.add(new RejectedObject(
                        mutator.id(),
                        WorldAwakenedDiagnosticCodes.MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        detail));
                continue;
            }
            eligibleMutators.add(new EligibleMutatorCandidate(mutator, mutatorRef.weight(), preview));
        }

        List<EligibleMutatorCandidate> selectedMutators = selectMutators(
                request.forcedMutatorId(),
                eligibleMutators,
                rejectedMutators,
                enforcedCap,
                context,
                traceId);

        List<ResourceLocation> eligibleMutatorIds = eligibleMutators.stream()
                .map(candidate -> candidate.mutator().id())
                .toList();
        List<ResourceLocation> selectedMutatorIds = selectedMutators.stream()
                .map(candidate -> candidate.mutator().id())
                .toList();

        List<AppliedMutation> appliedMutations = new ArrayList<>();
        List<WorldAwakenedMutationProvenance.ComponentFailureEntry> componentFailures = new ArrayList<>();
        for (EligibleMutatorCandidate selected : selectedMutators) {
            componentFailures.addAll(selected.preview().failures());
            List<ResourceLocation> appliedComponents = applyComponents(
                    selected.mutator(),
                    selected.preview().supportedComponents(),
                    context.liveMob(),
                    context.previewMob(),
                    request.dryRun(),
                    traceId,
                    componentFailures);
            if (appliedComponents.isEmpty()) {
                rejectedMutators.add(new RejectedObject(
                        selected.mutator().id(),
                        WorldAwakenedDiagnosticCodes.MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        "all selected components were skipped during runtime application"));
                continue;
            }
            appliedMutations.add(new AppliedMutation(selected.mutator().id(), appliedComponents));
        }

        boolean liveApplied = context.liveMob() != null && !request.dryRun() && !appliedMutations.isEmpty();
        if (liveApplied) {
            writeProvenance(
                    context.liveMob(),
                    selectedPool.id(),
                    appliedMutations,
                    context.stageSnapshot(),
                    traceId,
                    context.mutationDepth(),
                    componentFailures);
        }
        markProcessedIfLive(context.liveMob());

        return new MutatorRunResult(
                traceId,
                request.mode(),
                request.dryRun(),
                liveApplied,
                false,
                "",
                "",
                context.entityTypeId(),
                context.dimensionId(),
                context.pos(),
                context.spawnOrigin().serializedName(),
                stageResolution.progressionMode(),
                stageResolution.attributedPlayer().map(WorldAwakenedMutatorService::toAttributedPlayerView),
                sortedResourceLocations(context.stageSnapshot()),
                graph.poolsById().size(),
                indexedCandidatePoolIds.size(),
                List.copyOf(indexedCandidatePoolIds),
                List.copyOf(eligiblePoolIds),
                List.copyOf(rejectedPools),
                Optional.of(selectedPool.id()),
                Optional.of(chanceResult),
                requestedCap,
                enforcedCap,
                List.copyOf(eligibleMutatorIds),
                List.copyOf(rejectedMutators),
                List.copyOf(selectedMutatorIds),
                List.copyOf(appliedMutations),
                List.copyOf(componentFailures),
                false,
                Optional.empty());
    }

    private Optional<RejectedObject> evaluatePoolFilters(
            CompiledPool pool,
            EvaluationContext context,
            String traceId) {
        if (!pool.eligibleDimensions().isEmpty() && !pool.eligibleDimensions().contains(context.dimensionId())) {
            return Optional.of(new RejectedObject(
                    pool.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "dimension is not in eligible_dimensions"));
        }
        if (!pool.eligibleBiomes().isEmpty()) {
            if (context.biomeId().isEmpty()) {
                return Optional.of(new RejectedObject(
                        pool.id(),
                        WorldAwakenedDiagnosticCodes.INVALID_REFERENCE,
                        "biome context is unavailable"));
            }
            if (!pool.eligibleBiomes().contains(context.biomeId().get())) {
                return Optional.of(new RejectedObject(
                        pool.id(),
                        WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                        "biome is not in eligible_biomes"));
            }
        }
        if (!pool.eligibleEntities().isEmpty() && !pool.eligibleEntities().contains(context.entityTypeId())) {
            return Optional.of(new RejectedObject(
                    pool.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "entity type is not in pool eligible_entities"));
        }
        Optional<String> spawnOriginRejection = spawnOriginRejection(
                pool.allowFromSpawner(),
                pool.allowFromTrialSpawner(),
                context.spawnOrigin());
        if (spawnOriginRejection.isPresent()) {
            return Optional.of(new RejectedObject(
                    pool.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    spawnOriginRejection.get()));
        }
        if (!pool.stageFilter().matches(context.stageSnapshot(), context.stageRegistry())) {
            return Optional.of(new RejectedObject(
                    pool.id(),
                    WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING,
                    "stage_filters rejected current stage context"));
        }
        for (CompiledCondition condition : pool.conditions()) {
            if (!conditionMatches(condition, context, pool.id(), traceId)) {
                return Optional.of(new RejectedObject(
                        pool.id(),
                        WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                        "condition failed: " + condition.typeId()));
            }
        }
        if (pool.mutators().isEmpty()) {
            return Optional.of(new RejectedObject(
                    pool.id(),
                    WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                    "pool has no selectable mutators"));
        }
        return Optional.empty();
    }

    private Optional<RejectedObject> evaluateMutatorFilters(
            WeightedMutatorRef ref,
            CompiledMutator mutator,
            EvaluationContext context,
            String traceId) {
        if (!mutator.applicationContexts().contains("on_spawn")) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "application_contexts does not include on_spawn"));
        }
        if (!mutator.eligibleEntities().isEmpty() && !mutator.eligibleEntities().contains(context.entityTypeId())) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "entity type is not in mutator eligible_entities"));
        }
        if (mutator.excludedEntities().contains(context.entityTypeId())) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "entity type is in mutator excluded_entities"));
        }
        if (!mutator.eligibleEntityTags().isEmpty()) {
            boolean matchesTag = mutator.eligibleEntityTags().stream().anyMatch(tag -> tag.matches(context.entityTags()));
            if (!matchesTag) {
                return Optional.of(new RejectedObject(
                        mutator.id(),
                        WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                        "entity tags do not match mutator eligible_entity_tags"));
            }
        }
        for (TagMatcher excluded : mutator.excludedEntityTags()) {
            if (excluded.matches(context.entityTags())) {
                return Optional.of(new RejectedObject(
                        mutator.id(),
                        WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                        "entity tags match mutator excluded_entity_tags"));
            }
        }
        if (WorldAwakenedCommonConfig.RESPECT_BOSS_BLACKLIST.get() && context.entityIsBoss() && !mutator.appliesToBosses()) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                    "boss entity rejected by applies_to_bosses=false"));
        }
        if (mutator.enabledComponentCount() > MAX_COMPONENTS_PER_MUTATOR_BUDGET) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED,
                    "enabled component count exceeds max_components_per_mutator"));
        }
        for (CompiledCondition condition : mutator.requiredConditions()) {
            if (!conditionMatches(condition, context, mutator.id(), traceId)) {
                return Optional.of(new RejectedObject(
                        mutator.id(),
                        WorldAwakenedDiagnosticCodes.INVALID_CONDITION_TYPE,
                        "required condition failed: " + condition.typeId()));
            }
        }
        if (ref.weight() <= 0) {
            return Optional.of(new RejectedObject(
                    mutator.id(),
                    WorldAwakenedDiagnosticCodes.POOL_SELECTION_IMPOSSIBLE,
                    "mutator weight is <= 0"));
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> selectPool(
            Optional<ResourceLocation> forcedPoolId,
            LinkedHashSet<ResourceLocation> indexedCandidatePoolIds,
            List<ResourceLocation> eligiblePoolIds,
            List<RejectedObject> rejectedPools,
            Map<ResourceLocation, CompiledPool> poolsById,
            String traceId,
            EvaluationContext context) {
        if (forcedPoolId.isPresent()) {
            ResourceLocation forcedId = forcedPoolId.get();
            if (!poolsById.containsKey(forcedId)) {
                rejectedPools.add(new RejectedObject(
                        forcedId,
                        WorldAwakenedDiagnosticCodes.DEBUG_POOL_NOT_FOUND,
                        "forced pool id is not loaded"));
                return Optional.empty();
            }
            if (!indexedCandidatePoolIds.contains(forcedId)) {
                rejectedPools.add(new RejectedObject(
                        forcedId,
                        WorldAwakenedDiagnosticCodes.SELECTOR_INVALID,
                        "forced pool is outside selector-index candidate set"));
                return Optional.empty();
            }
            if (!eligiblePoolIds.contains(forcedId)) {
                return Optional.empty();
            }
            return Optional.of(forcedId);
        }
        if (eligiblePoolIds.isEmpty()) {
            return Optional.empty();
        }
        List<WeightedCandidate<ResourceLocation>> weighted = new ArrayList<>();
        for (ResourceLocation poolId : eligiblePoolIds) {
            CompiledPool pool = poolsById.get(poolId);
            if (pool == null) {
                continue;
            }
            weighted.add(new WeightedCandidate<>(pool.id(), pool.weight()));
        }
        long seed = deterministicSeed(context, traceId, "pool_selection", 0);
        return weightedPick(weighted, seed);
    }

    private MutationChanceResult evaluateMutationChance(
            CompiledPool selectedPool,
            MutationRunMode mode,
            EvaluationContext context,
            String traceId) {
        double baseChance = Math.min(1.0D, Math.max(0.0D, selectedPool.mutationChance()));
        boolean categoryAllowed = "monster".equals(context.mobCategory());
        boolean peacefulBlocked = categoryAllowed && context.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown scalarBreakdown =
                difficultyScalarService.resolveSpawnPressureScalar(
                        context.level(),
                        context.attributedPlayer().orElse(null),
                        context.dimensionId(),
                        baseChance,
                        Map.of(),
                        0.0D,
                        1.0D,
                        new WorldAwakenedEffectiveDifficultyScalarService.SpawnPressureContext(
                                true,
                                categoryAllowed,
                                peacefulBlocked,
                                "mutator_pool:" + selectedPool.id()));
        double chance = Math.min(1.0D, Math.max(0.0D, scalarBreakdown.clampedEffectiveValue()));
        if (mode == MutationRunMode.FORCE_POOL || mode == MutationRunMode.FORCE_MUTATOR) {
            return new MutationChanceResult(
                    chance,
                    MutationChanceRollMode.BYPASSED,
                    OptionalDouble.empty(),
                    true,
                    baseChance,
                    Optional.of(scalarBreakdown));
        }
        if (chance >= 1.0D) {
            return new MutationChanceResult(
                    chance,
                    MutationChanceRollMode.SKIPPED,
                    OptionalDouble.empty(),
                    true,
                    baseChance,
                    Optional.of(scalarBreakdown));
        }
        if (chance <= 0.0D) {
            return new MutationChanceResult(
                    chance,
                    MutationChanceRollMode.SKIPPED,
                    OptionalDouble.empty(),
                    false,
                    baseChance,
                    Optional.of(scalarBreakdown));
        }
        double roll = deterministicRoll(context, selectedPool.id(), traceId, "pool_mutation_chance", 0);
        return new MutationChanceResult(
                chance,
                MutationChanceRollMode.ROLLED,
                OptionalDouble.of(roll),
                roll <= chance,
                baseChance,
                Optional.of(scalarBreakdown));
    }

    private void recordPressureSnapshot(
            EvaluationContext context,
            WorldAwakenedProgressionMode progressionMode,
            ResourceLocation selectedPoolId,
            MutationRunMode mode,
            MutationChanceResult chanceResult,
            String traceId) {
        if (chanceResult.scalarBreakdown().isEmpty()) {
            return;
        }
        boolean categoryAllowed = "monster".equals(context.mobCategory());
        boolean peacefulBlocked = categoryAllowed
                && context.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
        WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown breakdown = chanceResult.scalarBreakdown().get();
        PressureEvaluationSnapshot snapshot = new PressureEvaluationSnapshot(
                pressureSnapshotCounter.incrementAndGet(),
                System.currentTimeMillis(),
                traceId,
                mode,
                context.dimensionId(),
                context.pos(),
                context.biomeId(),
                context.entityTypeId(),
                context.mobCategory(),
                context.spawnOrigin().serializedName(),
                progressionMode.serializedName(),
                context.attributedPlayer().map(WorldAwakenedMutatorService::toAttributedPlayerView),
                sortedResourceLocations(context.stageSnapshot()),
                selectedPoolId,
                chanceResult.baseMutationChance(),
                chanceResult.mutationChance(),
                chanceResult.rollMode(),
                chanceResult.rolledValue(),
                chanceResult.passed(),
                breakdown,
                true,
                categoryAllowed,
                peacefulBlocked,
                breakdown.provenance().getOrDefault("source_key", "mutator_pool:" + selectedPoolId));
        synchronized (pressureSnapshots) {
            pressureSnapshots.addLast(snapshot);
            while (pressureSnapshots.size() > MAX_PRESSURE_SNAPSHOTS) {
                pressureSnapshots.removeFirst();
            }
        }
    }

    private static List<EligibleMutatorCandidate> selectMutators(
            Optional<ResourceLocation> forcedMutatorId,
            List<EligibleMutatorCandidate> eligibleMutators,
            List<RejectedObject> rejectedMutators,
            int enforcedCap,
            EvaluationContext context,
            String traceId) {
        if (enforcedCap <= 0) {
            return List.of();
        }
        Map<ResourceLocation, EligibleMutatorCandidate> byId = new LinkedHashMap<>();
        for (EligibleMutatorCandidate candidate : eligibleMutators) {
            byId.put(candidate.mutator().id(), candidate);
        }

        if (forcedMutatorId.isPresent()) {
            ResourceLocation forcedId = forcedMutatorId.get();
            EligibleMutatorCandidate forced = byId.get(forcedId);
            if (forced == null) {
                rejectedMutators.add(new RejectedObject(
                        forcedId,
                        WorldAwakenedDiagnosticCodes.DEBUG_MUTATOR_NOT_FOUND,
                        "forced mutator is unavailable or failed eligibility"));
                return List.of();
            }
            return List.of(forced);
        }

        List<EligibleMutatorCandidate> remaining = new ArrayList<>(eligibleMutators);
        List<EligibleMutatorCandidate> selected = new ArrayList<>();
        Map<String, Integer> stackingCounts = new HashMap<>();
        Map<String, Integer> stackingLimits = new HashMap<>();

        int slot = 0;
        while (slot < enforcedCap && !remaining.isEmpty()) {
            List<EligibleMutatorCandidate> compatible = new ArrayList<>();
            for (EligibleMutatorCandidate candidate : remaining) {
                Optional<String> rejection = compatibilityRejection(candidate, selected, stackingCounts, stackingLimits);
                if (rejection.isPresent()) {
                    rejectedMutators.add(new RejectedObject(
                            candidate.mutator().id(),
                            WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID,
                            rejection.get()));
                    continue;
                }
                compatible.add(candidate);
            }
            remaining = compatible;
            if (remaining.isEmpty()) {
                break;
            }
            List<WeightedCandidate<ResourceLocation>> weighted = remaining.stream()
                    .map(candidate -> new WeightedCandidate<>(candidate.mutator().id(), candidate.weight()))
                    .toList();
            long seed = deterministicSeed(context, traceId, "mutator_selection", slot);
            Optional<ResourceLocation> pickedId = weightedPick(weighted, seed);
            if (pickedId.isEmpty()) {
                break;
            }
            EligibleMutatorCandidate picked = null;
            for (EligibleMutatorCandidate candidate : remaining) {
                if (candidate.mutator().id().equals(pickedId.get())) {
                    picked = candidate;
                    break;
                }
            }
            if (picked == null) {
                break;
            }
            selected.add(picked);
            if (picked.mutator().stackingGroup().isPresent()) {
                String group = picked.mutator().stackingGroup().get();
                int limit = Math.max(1, picked.mutator().maxStackCount());
                stackingLimits.merge(group, limit, Math::min);
                stackingCounts.merge(group, 1, Integer::sum);
            }
            ResourceLocation pickedMutatorId = picked.mutator().id();
            remaining.removeIf(candidate -> candidate.mutator().id().equals(pickedMutatorId));
            slot++;
        }
        return List.copyOf(selected);
    }

    private static Optional<String> compatibilityRejection(
            EligibleMutatorCandidate candidate,
            List<EligibleMutatorCandidate> selected,
            Map<String, Integer> stackingCounts,
            Map<String, Integer> stackingLimits) {
        for (EligibleMutatorCandidate existing : selected) {
            if (candidate.mutator().exclusiveWith().contains(existing.mutator().id())
                    || existing.mutator().exclusiveWith().contains(candidate.mutator().id())) {
                return Optional.of("exclusive_with conflict against selected mutator " + existing.mutator().id());
            }
        }
        if (candidate.mutator().stackingGroup().isPresent()) {
            String group = candidate.mutator().stackingGroup().get();
            int current = stackingCounts.getOrDefault(group, 0);
            int candidateLimit = Math.max(1, candidate.mutator().maxStackCount());
            int limit = Math.min(candidateLimit, stackingLimits.getOrDefault(group, candidateLimit));
            if (current >= limit) {
                return Optional.of("stacking_group limit reached for " + group + " (limit=" + limit + ")");
            }
        }
        return Optional.empty();
    }

    private static ComponentPreview previewComponents(
            CompiledMutator mutator,
            Mob target,
            String traceId,
            EvaluationContext context) {
        List<CompiledComponent> supported = new ArrayList<>();
        List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failures = new ArrayList<>();
        LinkedHashSet<ResourceLocation> activeTypes = new LinkedHashSet<>();

        for (CompiledComponent component : mutator.components()) {
            if (!component.definition().enabled()) {
                continue;
            }
            boolean conditionsPass = true;
            for (CompiledCondition condition : component.conditions()) {
                if (!conditionMatches(condition, context, mutator.id(), traceId)) {
                    conditionsPass = false;
                    break;
                }
            }
            if (!conditionsPass) {
                continue;
            }

            if (component.componentType().isPresent()) {
                WorldAwakenedMutationComponentType type = component.componentType().get();
                if (!type.allowDuplicates() && activeTypes.contains(type.id())) {
                    failures.add(new WorldAwakenedMutationProvenance.ComponentFailureEntry(
                            Optional.of(mutator.id()),
                            component.definition().type(),
                            WorldAwakenedDiagnosticCodes.COMPONENT_DUPLICATE_UNSUPPORTED,
                            "duplicate component type is not allowed"));
                    continue;
                }
                boolean incompatible = false;
                for (ResourceLocation active : activeTypes) {
                    if (type.incompatibleWith().contains(active)) {
                        incompatible = true;
                        break;
                    }
                    Optional<WorldAwakenedMutationComponentType> activeType = WorldAwakenedMutationComponentRegistry.lookup(active);
                    if (activeType.isPresent() && activeType.get().incompatibleWith().contains(type.id())) {
                        incompatible = true;
                        break;
                    }
                }
                if (incompatible) {
                    failures.add(new WorldAwakenedMutationProvenance.ComponentFailureEntry(
                            Optional.of(mutator.id()),
                            component.definition().type(),
                            WorldAwakenedDiagnosticCodes.COMPONENT_COMPOSITION_INVALID,
                            "component is incompatible with already selected component types"));
                    continue;
                }
            }

            Optional<String> supportFailure = componentSupportFailure(component, target);
            if (supportFailure.isPresent()) {
                failures.add(new WorldAwakenedMutationProvenance.ComponentFailureEntry(
                        Optional.of(mutator.id()),
                        component.definition().type(),
                        WorldAwakenedDiagnosticCodes.MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        supportFailure.get()));
                continue;
            }

            supported.add(component);
            activeTypes.add(component.definition().type());
        }
        return new ComponentPreview(List.copyOf(supported), List.copyOf(failures));
    }

    private static Optional<String> componentSupportFailure(CompiledComponent component, Mob target) {
        String path = component.definition().type().getPath().toLowerCase(Locale.ROOT);
        if (target == null) {
            return Optional.of("no runtime entity surface available for component evaluation");
        }
        return switch (path) {
            case "max_health_bonus",
                    "max_health_multiplier" -> missingAttribute(target, Attributes.MAX_HEALTH);
            case "attack_damage_bonus",
                    "attack_damage_multiplier" -> missingAttribute(target, Attributes.ATTACK_DAMAGE);
            case "armor_bonus",
                    "armor_multiplier" -> missingAttribute(target, Attributes.ARMOR);
            case "armor_toughness_bonus" -> missingAttribute(target, Attributes.ARMOR_TOUGHNESS);
            case "movement_speed_bonus",
                    "movement_speed_multiplier",
                    "pursuit_speed_boost" -> missingAttribute(target, Attributes.MOVEMENT_SPEED);
            case "follow_range_bonus",
                    "target_range_bonus" -> missingAttribute(target, Attributes.FOLLOW_RANGE);
            case "knockback_resistance_bonus" -> missingAttribute(target, Attributes.KNOCKBACK_RESISTANCE);
            case "temporary_shield",
                    "glow_style",
                    "effect_particles",
                    "ambient_particles" -> Optional.empty();
            case "equip_item" -> equipItemSupportFailure(target, component.definition().parameters());
            default -> Optional.of("component type does not have a safe WA-owned runtime surface yet");
        };
    }

    private static Optional<String> missingAttribute(Mob target, Holder<Attribute> attribute) {
        return target.getAttribute(attribute) == null
                ? Optional.of("attribute surface is unavailable: " + attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"))
                : Optional.empty();
    }

    private static List<ResourceLocation> applyComponents(
            CompiledMutator mutator,
            List<CompiledComponent> components,
            Mob liveMob,
            Mob previewMob,
            boolean dryRun,
            String traceId,
            List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failures) {
        Mob target = dryRun ? previewMob : liveMob;
        if (target == null) {
            return List.of();
        }
        List<ResourceLocation> applied = new ArrayList<>();
        for (CompiledComponent component : components) {
            Optional<String> failure = applyComponent(mutator, component, target, traceId);
            if (failure.isPresent()) {
                failures.add(new WorldAwakenedMutationProvenance.ComponentFailureEntry(
                        Optional.of(mutator.id()),
                        component.definition().type(),
                        WorldAwakenedDiagnosticCodes.MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE,
                        failure.get()));
                continue;
            }
            applied.add(component.definition().type());
        }
        if (!dryRun && target.getAttribute(Attributes.MAX_HEALTH) != null) {
            target.setHealth(target.getMaxHealth());
        }
        return List.copyOf(applied);
    }

    private static Optional<String> applyComponent(
            CompiledMutator mutator,
            CompiledComponent component,
            Mob target,
            String traceId) {
        String path = component.definition().type().getPath().toLowerCase(Locale.ROOT);
        ResourceLocation modifierId = stableModifierId(mutator.id(), component.authoredIndex());
        return switch (path) {
            case "max_health_bonus" -> applyAttributeAdd(
                    target,
                    Attributes.MAX_HEALTH,
                    modifierId,
                    component.definition().parameters(),
                    "amount");
            case "max_health_multiplier" -> applyAttributeMultiply(
                    target,
                    Attributes.MAX_HEALTH,
                    modifierId,
                    component.definition().parameters(),
                    "multiplier");
            case "attack_damage_bonus" -> applyAttributeAdd(
                    target,
                    Attributes.ATTACK_DAMAGE,
                    modifierId,
                    component.definition().parameters(),
                    "amount");
            case "attack_damage_multiplier" -> applyAttributeMultiply(
                    target,
                    Attributes.ATTACK_DAMAGE,
                    modifierId,
                    component.definition().parameters(),
                    "multiplier");
            case "armor_bonus" -> applyAttributeAdd(
                    target,
                    Attributes.ARMOR,
                    modifierId,
                    component.definition().parameters(),
                    "amount");
            case "armor_multiplier" -> applyAttributeMultiply(
                    target,
                    Attributes.ARMOR,
                    modifierId,
                    component.definition().parameters(),
                    "multiplier");
            case "armor_toughness_bonus" -> applyAttributeAdd(
                    target,
                    Attributes.ARMOR_TOUGHNESS,
                    modifierId,
                    component.definition().parameters(),
                    "amount");
            case "movement_speed_bonus",
                    "pursuit_speed_boost" -> applyAttributeAdd(
                            target,
                            Attributes.MOVEMENT_SPEED,
                            modifierId,
                            component.definition().parameters(),
                            "amount");
            case "movement_speed_multiplier" -> applyAttributeMultiply(
                    target,
                    Attributes.MOVEMENT_SPEED,
                    modifierId,
                    component.definition().parameters(),
                    "multiplier");
            case "follow_range_bonus",
                    "target_range_bonus" -> applyAttributeAdd(
                            target,
                            Attributes.FOLLOW_RANGE,
                            modifierId,
                            component.definition().parameters(),
                            "amount");
            case "knockback_resistance_bonus" -> applyAttributeAdd(
                    target,
                    Attributes.KNOCKBACK_RESISTANCE,
                    modifierId,
                    component.definition().parameters(),
                    "amount");
            case "temporary_shield" -> applyTemporaryShield(target, component.definition().parameters());
            case "equip_item" -> applyEquipItem(target, component.definition().parameters());
            case "glow_style" -> applyGlowStyle(target, component.definition().parameters());
            case "effect_particles" -> applyEffectParticles(target, component.definition().parameters());
            case "ambient_particles" -> applyAmbientParticles(target, component.definition().parameters());
            default -> Optional.of("component type is not implemented for live runtime application");
        };
    }

    private static Optional<String> applyAttributeAdd(
            Mob target,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            JsonObject parameters,
            String amountKey) {
        if (!parameters.has(amountKey)
                || !parameters.get(amountKey).isJsonPrimitive()
                || !parameters.get(amountKey).getAsJsonPrimitive().isNumber()) {
            return Optional.of("missing numeric parameters." + amountKey);
        }
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) {
            return Optional.of("attribute is unavailable: " + attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"));
        }
        double amount = parameters.get(amountKey).getAsDouble();
        instance.removeModifier(modifierId);
        instance.addPermanentModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
        return Optional.empty();
    }

    private static Optional<String> applyAttributeMultiply(
            Mob target,
            Holder<Attribute> attribute,
            ResourceLocation modifierId,
            JsonObject parameters,
            String multiplierKey) {
        if (!parameters.has(multiplierKey)
                || !parameters.get(multiplierKey).isJsonPrimitive()
                || !parameters.get(multiplierKey).getAsJsonPrimitive().isNumber()) {
            return Optional.of("missing numeric parameters." + multiplierKey);
        }
        double multiplier = parameters.get(multiplierKey).getAsDouble();
        if (multiplier <= 0.0D) {
            return Optional.of("parameters." + multiplierKey + " must be > 0");
        }
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null) {
            return Optional.of("attribute is unavailable: " + attribute.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"));
        }
        instance.removeModifier(modifierId);
        instance.addPermanentModifier(new AttributeModifier(
                modifierId,
                multiplier - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return Optional.empty();
    }

    private static Optional<String> applyTemporaryShield(Mob target, JsonObject parameters) {
        if (!parameters.has("amount")
                || !parameters.get("amount").isJsonPrimitive()
                || !parameters.get("amount").getAsJsonPrimitive().isNumber()) {
            return Optional.of("missing numeric parameters.amount");
        }
        float amount = Math.max(0.0F, parameters.get("amount").getAsFloat());
        if (amount <= 0.0F) {
            return Optional.of("parameters.amount must be > 0");
        }
        target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), amount));
        return Optional.empty();
    }

    private static Optional<String> equipItemSupportFailure(Mob target, JsonObject parameters) {
        ItemStack stack = buildEquipmentStack(parameters);
        if (stack.isEmpty()) {
            return Optional.of("item is unavailable or invalid for equipment application");
        }
        Optional<EquipmentSlot> slot = resolveEquipmentSlot(parameters, stack);
        if (slot.isEmpty()) {
            return Optional.of("equipment slot is invalid");
        }
        return verifyEquipmentSupport(target, stack, slot.get());
    }

    private static Optional<String> applyEquipItem(Mob target, JsonObject parameters) {
        ItemStack stack = buildEquipmentStack(parameters);
        if (stack.isEmpty()) {
            return Optional.of("item is unavailable or invalid for equipment application");
        }
        Optional<EquipmentSlot> slot = resolveEquipmentSlot(parameters, stack);
        if (slot.isEmpty()) {
            return Optional.of("equipment slot is invalid");
        }
        Optional<String> supportFailure = verifyEquipmentSupport(target, stack, slot.get());
        if (supportFailure.isPresent()) {
            return supportFailure;
        }
        Optional<String> enchantmentFailure = applyEquipmentEnchantments(target, stack, parameters);
        if (enchantmentFailure.isPresent()) {
            return enchantmentFailure;
        }
        target.setItemSlot(slot.get(), stack);
        if (parameters.has("drop_chance")
                && parameters.get("drop_chance").isJsonPrimitive()
                && parameters.getAsJsonPrimitive("drop_chance").isNumber()) {
            target.setDropChance(slot.get(), parameters.get("drop_chance").getAsFloat());
        }
        return Optional.empty();
    }

    private static Optional<String> applyEffectParticles(Mob target, JsonObject parameters) {
        Optional<WorldAwakenedVisualParticleEmitters.EmitterDefinition> emitter =
                WorldAwakenedVisualParticleEmitters.parseEffectParticles(parameters);
        if (emitter.isEmpty()) {
            return Optional.of("effect particle parameters are invalid");
        }
        WorldAwakenedVisualParticleEmitters.appendEmitter(target.getPersistentData(), emitter.get());
        return Optional.empty();
    }

    private static Optional<String> applyGlowStyle(Mob target, JsonObject parameters) {
        Optional<WorldAwakenedGlowStyleState.GlowStyleDefinition> style =
                WorldAwakenedGlowStyleState.fromParameters(parameters);
        if (style.isEmpty()) {
            return Optional.of("glow_style parameters are invalid");
        }
        WorldAwakenedGlowStyleState.write(target.getPersistentData(), style.get());
        return Optional.empty();
    }

    private static Optional<String> applyAmbientParticles(Mob target, JsonObject parameters) {
        Optional<WorldAwakenedVisualParticleEmitters.EmitterDefinition> emitter =
                WorldAwakenedVisualParticleEmitters.parseAmbientParticles(parameters);
        if (emitter.isEmpty()) {
            return Optional.of("ambient particle parameters are invalid");
        }
        WorldAwakenedVisualParticleEmitters.appendEmitter(target.getPersistentData(), emitter.get());
        return Optional.empty();
    }

    private static Optional<String> verifyEquipmentSupport(Mob target, ItemStack stack, EquipmentSlot slot) {
        if (!target.canUseSlot(slot)) {
            return Optional.of("entity cannot use equipment slot: " + slot.getName());
        }
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) && !target.canHoldItem(stack)) {
            return Optional.of("entity cannot hold item in slot " + slot.getName() + ": "
                    + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return Optional.empty();
    }

    private static ItemStack buildEquipmentStack(JsonObject parameters) {
        if (!parameters.has("item") || !parameters.get("item").isJsonPrimitive()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(parameters.getAsJsonPrimitive("item").getAsString());
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    private static Optional<EquipmentSlot> resolveEquipmentSlot(JsonObject parameters, ItemStack stack) {
        if (parameters.has("slot") && parameters.get("slot").isJsonPrimitive()) {
            String rawSlot = parameters.getAsJsonPrimitive("slot").getAsString().trim();
            if (!rawSlot.isBlank() && !rawSlot.equalsIgnoreCase("auto")) {
                return parseEquipmentSlot(rawSlot);
            }
        }
        Equipable equipable = Equipable.get(stack);
        if (equipable != null) {
            return Optional.of(equipable.getEquipmentSlot());
        }
        return Optional.of(EquipmentSlot.MAINHAND);
    }

    private static Optional<EquipmentSlot> parseEquipmentSlot(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "mainhand", "main_hand" -> Optional.of(EquipmentSlot.MAINHAND);
            case "offhand", "off_hand" -> Optional.of(EquipmentSlot.OFFHAND);
            case "head", "helmet" -> Optional.of(EquipmentSlot.HEAD);
            case "chest", "chestplate" -> Optional.of(EquipmentSlot.CHEST);
            case "legs", "leggings" -> Optional.of(EquipmentSlot.LEGS);
            case "feet", "boots" -> Optional.of(EquipmentSlot.FEET);
            case "body" -> Optional.of(EquipmentSlot.BODY);
            default -> Optional.empty();
        };
    }

    private static Optional<String> applyEquipmentEnchantments(Mob target, ItemStack stack, JsonObject parameters) {
        if (!parameters.has("enchantments")) {
            return Optional.empty();
        }
        JsonElement rawEnchantments = parameters.get("enchantments");
        if (!rawEnchantments.isJsonArray()) {
            return Optional.of("parameters.enchantments must be an array");
        }
        if (rawEnchantments.getAsJsonArray().size() == 0) {
            return Optional.empty();
        }
        if (!EnchantmentHelper.canStoreEnchantments(stack)) {
            return Optional.of("item cannot receive enchantments: " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        for (int index = 0; index < rawEnchantments.getAsJsonArray().size(); index++) {
            JsonElement rawEntry = rawEnchantments.getAsJsonArray().get(index);
            if (!rawEntry.isJsonObject()) {
                return Optional.of("parameters.enchantments[" + index + "] must be an object");
            }
            JsonObject entry = rawEntry.getAsJsonObject();
            if (!entry.has("id") || !entry.get("id").isJsonPrimitive()) {
                return Optional.of("parameters.enchantments[" + index + "].id is required");
            }
            ResourceLocation enchantmentId = ResourceLocation.tryParse(entry.getAsJsonPrimitive("id").getAsString());
            if (enchantmentId == null) {
                return Optional.of("parameters.enchantments[" + index + "].id must be a valid resource location");
            }
            if (!entry.has("level")
                    || !entry.get("level").isJsonPrimitive()
                    || !entry.getAsJsonPrimitive("level").isNumber()) {
                return Optional.of("parameters.enchantments[" + index + "].level must be numeric");
            }
            int level = entry.get("level").getAsInt();
            if (level < 1) {
                return Optional.of("parameters.enchantments[" + index + "].level must be >= 1");
            }
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, enchantmentId);
            Optional<? extends Holder<Enchantment>> holder = target.registryAccess()
                    .lookup(Registries.ENCHANTMENT)
                    .flatMap(lookup -> lookup.get(key));
            if (holder.isEmpty()) {
                return Optional.of("unknown enchantment: " + enchantmentId);
            }
            EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(holder.get(), level));
        }
        return Optional.empty();
    }

    private static ResourceLocation stableModifierId(ResourceLocation mutatorId, int componentIndex) {
        return ResourceLocation.fromNamespaceAndPath(
                WorldAwakenedConstants.MOD_ID,
                "mutator/" + mutatorId.getNamespace() + "/" + mutatorId.getPath() + "/" + componentIndex);
    }

    private static void markProcessedIfLive(Mob liveMob) {
        if (liveMob == null) {
            return;
        }
        WorldAwakenedMutationProvenance.markPipelineProcessed(liveMob.getPersistentData());
    }

    private SpawnStageResolution resolveSpawnStageResolution(ServerLevel level, BlockPos pos) {
        WorldAwakenedProgressionMode progressionMode =
                WorldAwakenedProgressionMode.fromConfig(WorldAwakenedCommonConfig.PROGRESSION_MODE.get());
        if (progressionMode != WorldAwakenedProgressionMode.PER_PLAYER) {
            return new SpawnStageResolution(stageService.getUnlockedStages(level), progressionMode, Optional.empty(), false);
        }

        Optional<ServerPlayer> attributedPlayer = resolveSpawnAttributedPlayer(level, pos);
        if (attributedPlayer.isEmpty()) {
            return new SpawnStageResolution(Set.of(), progressionMode, Optional.empty(), true);
        }
        return new SpawnStageResolution(
                stageService.getUnlockedStages(level, attributedPlayer.get()),
                progressionMode,
                attributedPlayer,
                false);
    }

    private static Optional<ServerPlayer> resolveSpawnAttributedPlayer(ServerLevel level, BlockPos pos) {
        double maxDistanceSqr = PLAYER_SPAWN_ATTRIBUTION_RADIUS * PLAYER_SPAWN_ATTRIBUTION_RADIUS;
        ServerPlayer closest = null;
        double closestDistanceSqr = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            double distanceSqr = player.distanceToSqr(
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D);
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }
            if (closest == null
                    || distanceSqr < closestDistanceSqr
                    || (distanceSqr == closestDistanceSqr
                            && player.getUUID().compareTo(closest.getUUID()) < 0)) {
                closest = player;
                closestDistanceSqr = distanceSqr;
            }
        }
        return Optional.ofNullable(closest);
    }

    private static AttributedPlayerView toAttributedPlayerView(ServerPlayer player) {
        return new AttributedPlayerView(player.getGameProfile().getName(), player.getStringUUID());
    }

    static Optional<String> spawnOriginRejection(
            boolean allowFromSpawner,
            boolean allowFromTrialSpawner,
            WorldAwakenedMobSpawnOrigin spawnOrigin) {
        return switch (spawnOrigin) {
            case SPAWNER -> allowFromSpawner
                    ? Optional.empty()
                    : Optional.of("spawn origin is spawner but allow_from_spawner is false");
            case TRIAL_SPAWNER -> allowFromTrialSpawner
                    ? Optional.empty()
                    : Optional.of("spawn origin is trial_spawner but allow_from_trial_spawner is false");
            case OTHER -> Optional.empty();
        };
    }

    private static void writeProvenance(
            Mob liveMob,
            ResourceLocation sourcePoolId,
            List<AppliedMutation> appliedMutations,
            Set<ResourceLocation> stageSnapshot,
            String traceId,
            int existingDepth,
            List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failures) {
        List<ResourceLocation> mutationIds = appliedMutations.stream()
                .map(AppliedMutation::mutatorId)
                .toList();
        LinkedHashSet<ResourceLocation> componentIds = new LinkedHashSet<>();
        for (AppliedMutation mutation : appliedMutations) {
            componentIds.addAll(mutation.appliedComponentTypes());
        }
        List<ResourceLocation> sortedStages = sortedResourceLocations(stageSnapshot);
        WorldAwakenedMutationProvenance.writeProvenance(
                liveMob.getPersistentData(),
                new WorldAwakenedMutationProvenance.MutationProvenancePayload(
                        Optional.of(sourcePoolId),
                        List.of(),
                        List.copyOf(mutationIds),
                        List.copyOf(componentIds),
                        sortedStages,
                        traceId,
                        Math.max(0, existingDepth),
                        WorldAwakenedMutationProvenance.ORIGIN_SPAWN_PIPELINE,
                        List.copyOf(failures)));
    }

    private CompiledGraph compiledGraph(WorldAwakenedDatapackSnapshot snapshot) {
        CachedCompiledGraph cached = cache.get();
        if (cached.generation() == snapshot.generation()) {
            return cached.graph();
        }
        CompiledGraph compiled = CompiledGraph.compile(snapshot.data().mutationPools(), snapshot.data().mobMutators());
        cache.set(new CachedCompiledGraph(snapshot.generation(), compiled));
        return compiled;
    }

    static SelectorIndex buildSelectorIndex(
            Map<ResourceLocation, MutationPoolDefinition> pools,
            Map<ResourceLocation, MobMutatorDefinition> mutators) {
        LinkedHashMap<ResourceLocation, LinkedHashSet<ResourceLocation>> byEntityId = new LinkedHashMap<>();
        LinkedHashMap<ResourceLocation, LinkedHashSet<ResourceLocation>> byEntityTag = new LinkedHashMap<>();
        LinkedHashSet<ResourceLocation> wildcardPools = new LinkedHashSet<>();

        List<ResourceLocation> orderedPoolIds = new ArrayList<>(pools.keySet());
        orderedPoolIds.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation poolId : orderedPoolIds) {
            MutationPoolDefinition pool = pools.get(poolId);
            if (pool == null || !pool.enabled()) {
                continue;
            }
            LinkedHashSet<ResourceLocation> selectorEntityIds = new LinkedHashSet<>(pool.eligibleEntities());
            LinkedHashSet<ResourceLocation> selectorTags = new LinkedHashSet<>();
            boolean hasWildcardTagSelector = false;
            for (ResourceLocation mutatorId : extractMutatorRefs(pool.mutators())) {
                MobMutatorDefinition mutator = mutators.get(mutatorId);
                if (mutator == null || !mutator.enabled()) {
                    continue;
                }
                selectorEntityIds.addAll(mutator.eligibleEntities());
                for (String rawTag : mutator.eligibleEntityTags()) {
                    Optional<TagMatcher> matcher = TagMatcher.compile(rawTag);
                    if (matcher.isEmpty()) {
                        continue;
                    }
                    if (matcher.get().exactTag().isPresent()) {
                        selectorTags.add(matcher.get().exactTag().get());
                    } else {
                        hasWildcardTagSelector = true;
                    }
                }
            }
            if (selectorEntityIds.isEmpty() && selectorTags.isEmpty()) {
                wildcardPools.add(poolId);
                continue;
            }
            for (ResourceLocation entityId : selectorEntityIds) {
                byEntityId.computeIfAbsent(entityId, ignored -> new LinkedHashSet<>()).add(poolId);
            }
            for (ResourceLocation tagId : selectorTags) {
                byEntityTag.computeIfAbsent(tagId, ignored -> new LinkedHashSet<>()).add(poolId);
            }
            if (hasWildcardTagSelector) {
                wildcardPools.add(poolId);
            }
        }
        return new SelectorIndex(
                freezeMultimap(byEntityId),
                freezeMultimap(byEntityTag),
                List.copyOf(wildcardPools));
    }

    private static Map<ResourceLocation, List<ResourceLocation>> freezeMultimap(
            Map<ResourceLocation, LinkedHashSet<ResourceLocation>> mutable) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> frozen = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, LinkedHashSet<ResourceLocation>> entry : mutable.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static List<ResourceLocation> extractMutatorRefs(List<JsonElement> entries) {
        List<ResourceLocation> refs = new ArrayList<>();
        for (JsonElement element : entries) {
            if (element.isJsonPrimitive()) {
                ResourceLocation parsed = ResourceLocation.tryParse(element.getAsString());
                if (parsed != null) {
                    refs.add(parsed);
                }
                continue;
            }
            if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) {
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(element.getAsJsonObject().get("id").getAsString());
            if (parsed != null) {
                refs.add(parsed);
            }
        }
        return refs;
    }

    private static Set<ResourceLocation> collectEntityTypeTags(EntityType<?> entityType) {
        LinkedHashSet<ResourceLocation> tags = new LinkedHashSet<>();
        entityType.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location()));
        return Set.copyOf(tags);
    }

    private static String mobCategory(EntityType<?> entityType) {
        MobCategory category = entityType.getCategory();
        return category == null ? "misc" : category.getName();
    }

    private static Optional<ResourceLocation> resolveBiome(ServerLevel level, BlockPos pos) {
        Optional<ResourceKey<Biome>> biomeKey = level.getBiome(pos).unwrapKey();
        return biomeKey.map(ResourceKey::location);
    }

    private static List<AttributeInspection> inspectManagedAttributes(LivingEntity entity) {
        List<AttributeInspection> inspected = new ArrayList<>();
        for (Holder<Attribute> managedAttribute : MANAGED_ATTRIBUTES) {
            AttributeInstance instance = entity.getAttribute(managedAttribute);
            if (instance == null) {
                continue;
            }
            ResourceLocation attributeId = managedAttribute.unwrapKey()
                    .map(key -> key.location())
                    .orElse(ResourceLocation.fromNamespaceAndPath("minecraft", "unknown"));
            List<AttributeModifierInspection> ownedModifiers = instance.getModifiers().stream()
                    .filter(modifier -> isWorldAwakenedMutatorModifier(modifier.id()))
                    .map(modifier -> new AttributeModifierInspection(
                            modifier.id(),
                            modifier.operation().name().toLowerCase(Locale.ROOT),
                            modifier.amount()))
                    .sorted(Comparator.comparing(entry -> entry.modifierId().toString()))
                    .toList();
            inspected.add(new AttributeInspection(
                    attributeId,
                    instance.getBaseValue(),
                    instance.getValue(),
                    ownedModifiers));
        }
        inspected.sort(Comparator.comparing(entry -> entry.attributeId().toString()));
        return List.copyOf(inspected);
    }

    public static boolean isWorldAwakenedMutatorModifier(ResourceLocation modifierId) {
        return modifierId.getNamespace().equals(WorldAwakenedConstants.MOD_ID)
                && modifierId.getPath().startsWith("mutator/");
    }

    private static Set<String> loadedMods() {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        ModList.get().getMods().forEach(modInfo -> mods.add(modInfo.getModId().toLowerCase(Locale.ROOT)));
        return Set.copyOf(mods);
    }

    private static Map<String, Boolean> configToggles() {
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        toggles.put("general.enable_mod", WorldAwakenedCommonConfig.ENABLE_MOD.get());
        toggles.put("general.debug_logging", WorldAwakenedCommonConfig.DEBUG_LOGGING.get());
        toggles.put("general.enable_debug_commands", WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get());
        toggles.put("mutators.enable_mutators", WorldAwakenedCommonConfig.ENABLE_MUTATORS.get());
        toggles.put("mutators.respect_boss_blacklist", WorldAwakenedCommonConfig.RESPECT_BOSS_BLACKLIST.get());
        return Map.copyOf(toggles);
    }

    private static boolean conditionMatches(
            CompiledCondition condition,
            EvaluationContext context,
            ResourceLocation ownerId,
            String traceId) {
        return switch (condition.kind()) {
            case STAGE_UNLOCKED -> condition.resourceRef()
                    .map(stageId -> stageMatches(context, stageId, true))
                    .orElse(false);
            case STAGE_LOCKED -> condition.resourceRef()
                    .map(stageId -> stageMatches(context, stageId, false))
                    .orElse(false);
            case CURRENT_DIMENSION -> condition.resourceRef()
                    .map(context.dimensionId()::equals)
                    .orElse(false);
            case CURRENT_BIOME -> condition.resourceRef()
                    .map(biome -> context.biomeId().map(biome::equals).orElse(false))
                    .orElse(false);
            case WORLD_DAY_GTE -> condition.value().isPresent()
                    && context.worldDay().isPresent()
                    && context.worldDay().getAsLong() >= (long) condition.value().getAsDouble();
            case ENTITY_TYPE -> condition.resourceRef()
                    .map(context.entityTypeId()::equals)
                    .orElse(false);
            case ENTITY_TAG -> condition.tagMatcher()
                    .map(matcher -> matcher.matches(context.entityTags()))
                    .orElse(false);
            case ENTITY_NOT_BOSS -> !context.entityIsBoss();
            case ENTITY_IS_MUTATED -> context.entityAlreadyMutated();
            case LOADED_MOD -> condition.text()
                    .map(text -> context.loadedMods().contains(text.toLowerCase(Locale.ROOT)))
                    .orElse(false);
            case CONFIG_TOGGLE_ENABLED -> condition.text()
                    .map(text -> context.configToggles().getOrDefault(text, false))
                    .orElse(false);
            case RANDOM_CHANCE -> {
                double chance = condition.value().orElse(1.0D);
                double roll = deterministicRoll(context, ownerId, traceId, "condition_random_chance", condition.conditionIndex());
                yield roll <= chance;
            }
            case MOON_PHASE -> context.worldDay().isPresent()
                    && condition.intValues().contains((int) Math.floorMod(context.worldDay().getAsLong(), 8L));
            case UNSUPPORTED -> false;
        };
    }

    private static boolean stageMatches(EvaluationContext context, ResourceLocation requestedId, boolean unlocked) {
        Optional<ResourceLocation> canonical = context.stageRegistry().resolveCanonicalId(requestedId);
        boolean has = context.stageSnapshot().contains(canonical.orElse(requestedId));
        return unlocked ? has : !has;
    }

    private static long deterministicSeed(
            EvaluationContext context,
            String traceId,
            String channel,
            int extraIndex) {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed, context.dimensionId().toString());
        seed = mix(seed, context.entityTypeId().toString());
        seed = mix(seed, context.pos().asLong());
        seed = mix(seed, context.gameTick());
        seed = mix(seed, context.worldDay().isPresent() ? context.worldDay().getAsLong() : 0L);
        seed = mix(seed, channel);
        seed = mix(seed, traceId);
        seed = mix(seed, extraIndex);
        return seed;
    }

    private static double deterministicRoll(
            EvaluationContext context,
            ResourceLocation ownerId,
            String traceId,
            String channel,
            int extraIndex) {
        long seed = deterministicSeed(context, traceId, channel, extraIndex);
        seed = mix(seed, ownerId.toString());
        return new SplittableRandom(seed).nextDouble();
    }

    private static <T> Optional<T> weightedPick(List<WeightedCandidate<T>> entries, long seed) {
        long totalWeight = 0L;
        for (WeightedCandidate<T> entry : entries) {
            if (entry.weight() > 0) {
                totalWeight += entry.weight();
            }
        }
        if (totalWeight <= 0L) {
            return Optional.empty();
        }
        long target = Math.floorMod(new SplittableRandom(seed).nextLong(), totalWeight);
        long cursor = 0L;
        for (WeightedCandidate<T> entry : entries) {
            if (entry.weight() <= 0) {
                continue;
            }
            cursor += entry.weight();
            if (target < cursor) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }

    private static long mix(long seed, String value) {
        long result = seed;
        for (int index = 0; index < value.length(); index++) {
            result ^= value.charAt(index);
            result *= 0x100000001B3L;
            result ^= (result >>> 31);
        }
        return result;
    }

    private static long mix(long seed, long value) {
        long result = seed ^ value;
        result *= 0x100000001B3L;
        result ^= (result >>> 33);
        return result;
    }

    private static List<ResourceLocation> sortedResourceLocations(Collection<ResourceLocation> ids) {
        List<ResourceLocation> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        return List.copyOf(sorted);
    }

    private String nextTraceId() {
        long value = traceCounter.incrementAndGet();
        return "WA-M" + Long.toHexString(value).toUpperCase(Locale.ROOT);
    }

    private record CachedCompiledGraph(
            long generation,
            CompiledGraph graph) {
    }

    private record EvaluationRequest(
            MutationRunMode mode,
            ServerLevel level,
            BlockPos pos,
            EntityType<?> entityType,
            Mob liveEntity,
            WorldAwakenedMobSpawnOrigin spawnOrigin,
            boolean dryRun,
            Optional<ResourceLocation> forcedPoolId,
            Optional<ResourceLocation> forcedMutatorId) {
    }

    private record EvaluationContext(
            ServerLevel level,
            BlockPos pos,
            ResourceLocation dimensionId,
            Optional<ResourceLocation> biomeId,
            ResourceLocation entityTypeId,
            Set<ResourceLocation> entityTags,
            String mobCategory,
            boolean entityIsBoss,
            boolean entityAlreadyMutated,
            WorldAwakenedMobSpawnOrigin spawnOrigin,
            Set<ResourceLocation> stageSnapshot,
            WorldAwakenedStageRegistry stageRegistry,
            OptionalLongValue worldDay,
            long gameTick,
            Set<String> loadedMods,
            Map<String, Boolean> configToggles,
            Optional<ServerPlayer> attributedPlayer,
            Mob liveMob,
            Mob previewMob,
            int mutationDepth) {
    }

    private record CompiledGraph(
            Map<ResourceLocation, CompiledPool> poolsById,
            Map<ResourceLocation, CompiledMutator> mutatorsById,
            SelectorIndex selectorIndex) {
        static CompiledGraph empty() {
            return new CompiledGraph(Map.of(), Map.of(), new SelectorIndex(Map.of(), Map.of(), List.of()));
        }

        static CompiledGraph compile(
                Map<ResourceLocation, MutationPoolDefinition> pools,
                Map<ResourceLocation, MobMutatorDefinition> mutators) {
            Map<ResourceLocation, CompiledMutator> compiledMutators = compileMutators(mutators);
            Map<ResourceLocation, CompiledPool> compiledPools = compilePools(pools, compiledMutators);
            SelectorIndex selectorIndex = buildSelectorIndex(pools, mutators);
            return new CompiledGraph(
                    Map.copyOf(compiledPools),
                    Map.copyOf(compiledMutators),
                    selectorIndex);
        }

        private static Map<ResourceLocation, CompiledMutator> compileMutators(
                Map<ResourceLocation, MobMutatorDefinition> mutators) {
            LinkedHashMap<ResourceLocation, CompiledMutator> compiled = new LinkedHashMap<>();
            List<ResourceLocation> orderedIds = new ArrayList<>(mutators.keySet());
            orderedIds.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation mutatorId : orderedIds) {
                MobMutatorDefinition definition = mutators.get(mutatorId);
                if (definition == null || !definition.enabled()) {
                    continue;
                }
                compiled.put(mutatorId, CompiledMutator.compile(definition));
            }
            return compiled;
        }

        private static Map<ResourceLocation, CompiledPool> compilePools(
                Map<ResourceLocation, MutationPoolDefinition> pools,
                Map<ResourceLocation, CompiledMutator> compiledMutators) {
            LinkedHashMap<ResourceLocation, CompiledPool> compiled = new LinkedHashMap<>();
            List<ResourceLocation> orderedIds = new ArrayList<>(pools.keySet());
            orderedIds.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation poolId : orderedIds) {
                MutationPoolDefinition definition = pools.get(poolId);
                if (definition == null || !definition.enabled()) {
                    continue;
                }
                compiled.put(poolId, CompiledPool.compile(definition, compiledMutators));
            }
            return compiled;
        }
    }

    private record CompiledPool(
            ResourceLocation id,
            int weight,
            double mutationChance,
            boolean allowFromSpawner,
            boolean allowFromTrialSpawner,
            List<CompiledCondition> conditions,
            StageFilter stageFilter,
            Set<ResourceLocation> eligibleDimensions,
            Set<ResourceLocation> eligibleBiomes,
            Set<ResourceLocation> eligibleEntities,
            List<WeightedMutatorRef> mutators,
            Optional<Integer> maxMutatorsPerEntity) {
        static CompiledPool compile(
                MutationPoolDefinition definition,
                Map<ResourceLocation, CompiledMutator> compiledMutators) {
            List<CompiledCondition> conditions = compileConditions(definition.conditions());
            StageFilter stageFilter = StageFilter.compile(definition.stageFilters());
            List<WeightedMutatorRef> mutators = compileMutatorRefs(definition.mutators(), compiledMutators);
            return new CompiledPool(
                    definition.id(),
                    Math.max(1, definition.weight()),
                    Math.min(1.0D, Math.max(0.0D, definition.mutationChance())),
                    definition.allowFromSpawner(),
                    definition.allowFromTrialSpawner(),
                    conditions,
                    stageFilter,
                    Set.copyOf(definition.eligibleDimensions()),
                    Set.copyOf(definition.eligibleBiomes()),
                    Set.copyOf(definition.eligibleEntities()),
                    List.copyOf(mutators),
                    definition.maxMutatorsPerEntity());
        }
    }

    private record SpawnStageResolution(
            Set<ResourceLocation> stageSnapshot,
            WorldAwakenedProgressionMode progressionMode,
            Optional<ServerPlayer> attributedPlayer,
            boolean attributionRequiredButMissing) {
    }

    private record WeightedMutatorRef(
            ResourceLocation mutatorId,
            int weight) {
    }

    private record CompiledMutator(
            ResourceLocation id,
            int weight,
            Set<ResourceLocation> eligibleEntities,
            Set<ResourceLocation> excludedEntities,
            List<TagMatcher> eligibleEntityTags,
            List<TagMatcher> excludedEntityTags,
            boolean appliesToBosses,
            Set<String> applicationContexts,
            List<CompiledCondition> requiredConditions,
            List<CompiledComponent> components,
            Optional<String> stackingGroup,
            int maxStackCount,
            Set<ResourceLocation> exclusiveWith,
            int enabledComponentCount) {
        static CompiledMutator compile(MobMutatorDefinition definition) {
            List<TagMatcher> eligibleTags = compileTagMatchers(definition.eligibleEntityTags());
            List<TagMatcher> excludedTags = compileTagMatchers(definition.excludedEntityTags());
            List<CompiledCondition> conditions = compileConditions(definition.requiredConditions());
            List<CompiledComponent> components = compileComponents(definition.components());
            Set<String> applicationContexts = new LinkedHashSet<>();
            for (String context : definition.applicationContexts()) {
                if (context == null || context.isBlank()) {
                    continue;
                }
                applicationContexts.add(context.trim().toLowerCase(Locale.ROOT));
            }
            if (applicationContexts.isEmpty()) {
                applicationContexts.addAll(SUPPORTED_APPLICATION_CONTEXTS);
            }
            int enabledCount = (int) definition.components().stream().filter(MutationComponentDefinition::enabled).count();
            return new CompiledMutator(
                    definition.id(),
                    Math.max(1, definition.weight()),
                    Set.copyOf(definition.eligibleEntities()),
                    Set.copyOf(definition.excludedEntities()),
                    List.copyOf(eligibleTags),
                    List.copyOf(excludedTags),
                    definition.appliesToBosses(),
                    Set.copyOf(applicationContexts),
                    conditions,
                    components,
                    definition.stackingGroup().map(value -> value.toLowerCase(Locale.ROOT)),
                    Math.max(1, definition.maxStackCount()),
                    Set.copyOf(definition.exclusiveWith()),
                    enabledCount);
        }
    }

    private static List<CompiledComponent> compileComponents(List<MutationComponentDefinition> components) {
        List<CompiledComponent> compiled = new ArrayList<>();
        for (int index = 0; index < components.size(); index++) {
            MutationComponentDefinition component = components.get(index);
            Optional<WorldAwakenedMutationComponentType> componentType = WorldAwakenedMutationComponentRegistry.lookup(component.type());
            List<CompiledCondition> conditions = compileConditions(component.conditions());
            compiled.add(new CompiledComponent(
                    index,
                    component,
                    componentType,
                    List.copyOf(conditions)));
        }
        compiled.sort(Comparator
                .comparingInt((CompiledComponent component) -> component.definition().priority())
                .reversed()
                .thenComparingInt(CompiledComponent::authoredIndex));
        return List.copyOf(compiled);
    }

    private static List<CompiledCondition> compileConditions(List<JsonElement> nodes) {
        List<CompiledCondition> compiled = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsonElement node = nodes.get(index);
            compileCondition(node, index).ifPresent(compiled::add);
        }
        return List.copyOf(compiled);
    }

    private static Optional<CompiledCondition> compileCondition(JsonElement node, int conditionIndex) {
        if (!node.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = node.getAsJsonObject();
        if (!isNodeEnabled(object)) {
            return Optional.empty();
        }
        Optional<ResourceLocation> typeOpt = readResourceLocation(object, "type");
        if (typeOpt.isEmpty()) {
            return Optional.empty();
        }
        JsonObject parameters = readParametersObject(object);
        String path = typeOpt.get().getPath().toLowerCase(Locale.ROOT);
        ConditionKind kind = switch (path) {
            case "stage_unlocked" -> ConditionKind.STAGE_UNLOCKED;
            case "stage_locked" -> ConditionKind.STAGE_LOCKED;
            case "current_dimension" -> ConditionKind.CURRENT_DIMENSION;
            case "current_biome" -> ConditionKind.CURRENT_BIOME;
            case "world_day_gte" -> ConditionKind.WORLD_DAY_GTE;
            case "entity_type" -> ConditionKind.ENTITY_TYPE;
            case "entity_tag" -> ConditionKind.ENTITY_TAG;
            case "entity_not_boss" -> ConditionKind.ENTITY_NOT_BOSS;
            case "entity_is_mutated" -> ConditionKind.ENTITY_IS_MUTATED;
            case "loaded_mod" -> ConditionKind.LOADED_MOD;
            case "config_toggle_enabled" -> ConditionKind.CONFIG_TOGGLE_ENABLED;
            case "random_chance" -> ConditionKind.RANDOM_CHANCE;
            case "moon_phase" -> ConditionKind.MOON_PHASE;
            default -> ConditionKind.UNSUPPORTED;
        };
        Optional<ResourceLocation> resourceRef = switch (kind) {
            case STAGE_UNLOCKED, STAGE_LOCKED -> readResourceLocation(parameters, "stage");
            case CURRENT_DIMENSION -> readResourceLocation(parameters, "dimension");
            case CURRENT_BIOME -> readResourceLocation(parameters, "biome");
            case ENTITY_TYPE -> readResourceLocation(parameters, "entity");
            default -> Optional.empty();
        };
        Optional<TagMatcher> tagMatcher = kind == ConditionKind.ENTITY_TAG
                ? readTagMatcher(parameters, "tag")
                : Optional.empty();
        Optional<String> text = switch (kind) {
            case LOADED_MOD -> readString(parameters, "mod").map(value -> value.toLowerCase(Locale.ROOT));
            case CONFIG_TOGGLE_ENABLED -> readString(parameters, "config_gate").map(value -> value.toLowerCase(Locale.ROOT));
            default -> Optional.empty();
        };
        OptionalDouble value = switch (kind) {
            case WORLD_DAY_GTE -> readDouble(parameters, "value");
            case RANDOM_CHANCE -> readDouble(parameters, "chance");
            default -> OptionalDouble.empty();
        };
        Set<Integer> intValues = kind == ConditionKind.MOON_PHASE ? parseMoonPhases(parameters) : Set.of();
        return Optional.of(new CompiledCondition(
                typeOpt.get(),
                kind,
                resourceRef,
                tagMatcher,
                text,
                value,
                intValues,
                conditionIndex));
    }

    private static List<WeightedMutatorRef> compileMutatorRefs(
            List<JsonElement> entries,
            Map<ResourceLocation, CompiledMutator> compiledMutators) {
        List<WeightedMutatorRef> refs = new ArrayList<>();
        for (JsonElement element : entries) {
            if (element.isJsonPrimitive()) {
                ResourceLocation mutatorId = ResourceLocation.tryParse(element.getAsString());
                if (mutatorId == null) {
                    continue;
                }
                int defaultWeight = compiledMutators.containsKey(mutatorId) ? compiledMutators.get(mutatorId).weight() : 1;
                refs.add(new WeightedMutatorRef(mutatorId, Math.max(1, defaultWeight)));
                continue;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (!object.has("id")) {
                continue;
            }
            ResourceLocation mutatorId = ResourceLocation.tryParse(object.get("id").getAsString());
            if (mutatorId == null) {
                continue;
            }
            int weight = 1;
            if (object.has("weight") && object.get("weight").isJsonPrimitive() && object.getAsJsonPrimitive("weight").isNumber()) {
                weight = object.get("weight").getAsInt();
            } else if (compiledMutators.containsKey(mutatorId)) {
                weight = compiledMutators.get(mutatorId).weight();
            }
            refs.add(new WeightedMutatorRef(mutatorId, Math.max(1, weight)));
        }
        return List.copyOf(refs);
    }

    private record CompiledComponent(
            int authoredIndex,
            MutationComponentDefinition definition,
            Optional<WorldAwakenedMutationComponentType> componentType,
            List<CompiledCondition> conditions) {
    }

    private record CompiledCondition(
            ResourceLocation typeId,
            ConditionKind kind,
            Optional<ResourceLocation> resourceRef,
            Optional<TagMatcher> tagMatcher,
            Optional<String> text,
            OptionalDouble value,
            Set<Integer> intValues,
            int conditionIndex) {
    }

    private enum ConditionKind {
        STAGE_UNLOCKED,
        STAGE_LOCKED,
        CURRENT_DIMENSION,
        CURRENT_BIOME,
        WORLD_DAY_GTE,
        ENTITY_TYPE,
        ENTITY_TAG,
        ENTITY_NOT_BOSS,
        ENTITY_IS_MUTATED,
        LOADED_MOD,
        CONFIG_TOGGLE_ENABLED,
        RANDOM_CHANCE,
        MOON_PHASE,
        UNSUPPORTED
    }

    private static final class StageFilter {
        private final Set<ResourceLocation> requiredAll;
        private final Set<ResourceLocation> requiredAny;
        private final Set<ResourceLocation> excluded;

        private StageFilter(Set<ResourceLocation> requiredAll, Set<ResourceLocation> requiredAny, Set<ResourceLocation> excluded) {
            this.requiredAll = requiredAll;
            this.requiredAny = requiredAny;
            this.excluded = excluded;
        }

        static StageFilter compile(Optional<JsonElement> raw) {
            if (raw.isEmpty()) {
                return empty();
            }
            JsonElement element = raw.get();
            LinkedHashSet<ResourceLocation> requiredAll = new LinkedHashSet<>();
            LinkedHashSet<ResourceLocation> requiredAny = new LinkedHashSet<>();
            LinkedHashSet<ResourceLocation> excluded = new LinkedHashSet<>();

            if (element.isJsonPrimitive()) {
                ResourceLocation stageId = ResourceLocation.tryParse(element.getAsString());
                if (stageId != null) {
                    requiredAll.add(stageId);
                }
            } else if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    if (!child.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation stageId = ResourceLocation.tryParse(child.getAsString());
                    if (stageId != null) {
                        requiredAll.add(stageId);
                    }
                }
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                readStageSet(object, requiredAll, "stage", "required_stage", "required_stages", "stages", "all_of");
                readStageSet(object, requiredAny, "required_any", "any_of", "one_of");
                readStageSet(object, excluded, "excluded", "excluded_stages", "none_of");
            }
            return new StageFilter(Set.copyOf(requiredAll), Set.copyOf(requiredAny), Set.copyOf(excluded));
        }

        static StageFilter empty() {
            return new StageFilter(Set.of(), Set.of(), Set.of());
        }

        boolean matches(Set<ResourceLocation> activeStages, WorldAwakenedStageRegistry stageRegistry) {
            for (ResourceLocation requiredStage : requiredAll) {
                if (!containsStage(activeStages, stageRegistry, requiredStage)) {
                    return false;
                }
            }
            if (!requiredAny.isEmpty()) {
                boolean anyMatched = false;
                for (ResourceLocation stage : requiredAny) {
                    if (containsStage(activeStages, stageRegistry, stage)) {
                        anyMatched = true;
                        break;
                    }
                }
                if (!anyMatched) {
                    return false;
                }
            }
            for (ResourceLocation stage : excluded) {
                if (containsStage(activeStages, stageRegistry, stage)) {
                    return false;
                }
            }
            return true;
        }

        private static void readStageSet(JsonObject object, Set<ResourceLocation> collector, String... keys) {
            for (String key : keys) {
                if (!object.has(key)) {
                    continue;
                }
                JsonElement raw = object.get(key);
                if (raw.isJsonPrimitive()) {
                    ResourceLocation parsed = ResourceLocation.tryParse(raw.getAsString());
                    if (parsed != null) {
                        collector.add(parsed);
                    }
                    continue;
                }
                if (!raw.isJsonArray()) {
                    continue;
                }
                for (JsonElement element : raw.getAsJsonArray()) {
                    if (!element.isJsonPrimitive()) {
                        continue;
                    }
                    ResourceLocation parsed = ResourceLocation.tryParse(element.getAsString());
                    if (parsed != null) {
                        collector.add(parsed);
                    }
                }
            }
        }

        private static boolean containsStage(
                Set<ResourceLocation> activeStages,
                WorldAwakenedStageRegistry stageRegistry,
                ResourceLocation requestedId) {
            Optional<ResourceLocation> canonical = stageRegistry.resolveCanonicalId(requestedId);
            return activeStages.contains(canonical.orElse(requestedId));
        }
    }

    private record TagMatcher(
            Optional<ResourceLocation> exactTag,
            Optional<String> namespaceWildcard) {
        static Optional<TagMatcher> compile(String raw) {
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String value = raw.startsWith("#") ? raw.substring(1) : raw;
            if (value.endsWith(":*")) {
                String namespace = value.substring(0, value.length() - 2).toLowerCase(Locale.ROOT);
                if (namespace.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new TagMatcher(Optional.empty(), Optional.of(namespace)));
            }
            ResourceLocation exact = ResourceLocation.tryParse(value);
            if (exact == null) {
                return Optional.empty();
            }
            return Optional.of(new TagMatcher(Optional.of(exact), Optional.empty()));
        }

        boolean matches(Set<ResourceLocation> entityTags) {
            if (exactTag.isPresent()) {
                return entityTags.contains(exactTag.get());
            }
            if (namespaceWildcard.isPresent()) {
                String namespace = namespaceWildcard.get();
                for (ResourceLocation tag : entityTags) {
                    if (tag.getNamespace().equals(namespace)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static List<TagMatcher> compileTagMatchers(List<String> rawSelectors) {
        List<TagMatcher> matchers = new ArrayList<>();
        for (String raw : rawSelectors) {
            TagMatcher.compile(raw).ifPresent(matchers::add);
        }
        return List.copyOf(matchers);
    }

    private record WeightedCandidate<T>(
            T value,
            int weight) {
    }

    private record EligibleMutatorCandidate(
            CompiledMutator mutator,
            int weight,
            ComponentPreview preview) {
    }

    private record ComponentPreview(
            List<CompiledComponent> supportedComponents,
            List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failures) {
    }

    private record OptionalLongValue(
            Optional<Long> value) {
        static OptionalLongValue of(long value) {
            return new OptionalLongValue(Optional.of(value));
        }

        boolean isPresent() {
            return value.isPresent();
        }

        long getAsLong() {
            return value.orElse(0L);
        }
    }

    private record EntityContext(
            ResourceLocation entityId,
            Set<ResourceLocation> entityTags,
            String mobCategory) implements WorldAwakenedEntityContextView {
    }

    public static record SelectorIndex(
            Map<ResourceLocation, List<ResourceLocation>> poolIdsByEntityId,
            Map<ResourceLocation, List<ResourceLocation>> poolIdsByEntityTag,
            List<ResourceLocation> wildcardPools) {
        public LinkedHashSet<ResourceLocation> candidatePoolIds(
                ResourceLocation entityTypeId,
                Set<ResourceLocation> entityTags) {
            LinkedHashSet<ResourceLocation> candidates = new LinkedHashSet<>();
            candidates.addAll(poolIdsByEntityId.getOrDefault(entityTypeId, List.of()));
            for (ResourceLocation tagId : entityTags) {
                candidates.addAll(poolIdsByEntityTag.getOrDefault(tagId, List.of()));
            }
            candidates.addAll(wildcardPools);
            return candidates;
        }
    }

    public enum MutationRunMode {
        LIVE_SPAWN,
        EVALUATE,
        FORCE_POOL,
        FORCE_MUTATOR,
        LIVE_TEST
    }

    public record MutatorRunResult(
            String traceId,
            MutationRunMode mode,
            boolean dryRun,
            boolean liveApplied,
            boolean skipped,
            String skipCode,
            String skipDetail,
            ResourceLocation entityTypeId,
            ResourceLocation dimensionId,
            BlockPos position,
            String spawnOrigin,
            WorldAwakenedProgressionMode progressionMode,
            Optional<AttributedPlayerView> attributedPlayer,
            List<ResourceLocation> stageContext,
            int totalPoolCount,
            int indexedCandidatePoolCount,
            List<ResourceLocation> indexedCandidatePoolIds,
            List<ResourceLocation> eligiblePoolIds,
            List<RejectedObject> rejectedPools,
            Optional<ResourceLocation> selectedPoolId,
            Optional<MutationChanceResult> chanceResult,
            int requestedMutatorCap,
            int enforcedMutatorCap,
            List<ResourceLocation> eligibleMutatorIds,
            List<RejectedObject> rejectedMutators,
            List<ResourceLocation> selectedMutatorIds,
            List<AppliedMutation> appliedMutations,
            List<WorldAwakenedMutationProvenance.ComponentFailureEntry> componentFailures,
            boolean spawnAdded,
            Optional<String> spawnedEntityUuid) {
        static MutatorRunResult skipped(
                String traceId,
                MutationRunMode mode,
                boolean dryRun,
                String skipCode,
                String skipDetail,
                ResourceLocation entityTypeId,
                ResourceLocation dimensionId,
                BlockPos position) {
            return new MutatorRunResult(
                    traceId,
                    mode,
                    dryRun,
                    false,
                    true,
                    skipCode,
                    skipDetail,
                    entityTypeId,
                    dimensionId,
                    position,
                    WorldAwakenedMobSpawnOrigin.OTHER.serializedName(),
                    WorldAwakenedProgressionMode.GLOBAL,
                    Optional.empty(),
                    List.of(),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    Optional.empty());
        }

        MutatorRunResult withSpawnOutcome(boolean added, Optional<String> entityUuid) {
            return new MutatorRunResult(
                    traceId,
                    mode,
                    dryRun,
                    liveApplied,
                    skipped,
                    skipCode,
                    skipDetail,
                    entityTypeId,
                    dimensionId,
                    position,
                    spawnOrigin,
                    progressionMode,
                    attributedPlayer,
                    stageContext,
                    totalPoolCount,
                    indexedCandidatePoolCount,
                    indexedCandidatePoolIds,
                    eligiblePoolIds,
                    rejectedPools,
                    selectedPoolId,
                    chanceResult,
                    requestedMutatorCap,
                    enforcedMutatorCap,
                    eligibleMutatorIds,
                    rejectedMutators,
                    selectedMutatorIds,
                    appliedMutations,
                    componentFailures,
                    added,
                    entityUuid);
        }
    }

    public enum MutationChanceRollMode {
        ROLLED,
        SKIPPED,
        BYPASSED
    }

    public record MutationChanceResult(
            double mutationChance,
            MutationChanceRollMode rollMode,
            OptionalDouble rolledValue,
            boolean passed,
            double baseMutationChance,
            Optional<WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown> scalarBreakdown) {
        public MutationChanceResult(
                double mutationChance,
                MutationChanceRollMode rollMode,
                OptionalDouble rolledValue,
                boolean passed) {
            this(
                    mutationChance,
                    rollMode,
                    rolledValue,
                    passed,
                    mutationChance,
                    Optional.empty());
        }
    }

    public record AttributedPlayerView(
            String name,
            String uuid) {
    }

    public record RejectedObject(
            ResourceLocation objectId,
            String code,
            String detail) {
    }

    public record AppliedMutation(
            ResourceLocation mutatorId,
            List<ResourceLocation> appliedComponentTypes) {
    }

    public record MutationInspectView(
            boolean hasProvenance,
            ResourceLocation entityTypeId,
            Optional<ResourceLocation> sourcePoolId,
            List<ResourceLocation> sourceRuleIds,
            List<ResourceLocation> mutatorIds,
            List<ResourceLocation> componentIds,
            List<ResourceLocation> stageContext,
            String traceId,
            int mutationDepth,
            String originMarker,
            boolean pipelineProcessed,
            List<ResourceLocation> resolvedMutatorIds,
            List<ResourceLocation> missingMutatorIds,
            List<WorldAwakenedMutationProvenance.ComponentFailureEntry> failedComponents,
            Optional<WorldAwakenedGlowStyleState.GlowStyleDefinition> glowStyle,
            List<WorldAwakenedVisualParticleEmitters.EmitterDefinition> particleVisualEmitters,
            List<AttributeInspection> attributes) {
    }

    public record AttributeInspection(
            ResourceLocation attributeId,
            double baseValue,
            double currentValue,
            List<AttributeModifierInspection> waOwnedModifiers) {
    }

    public record AttributeModifierInspection(
            ResourceLocation modifierId,
            String operation,
            double amount) {
    }

    public record MutatorDebugSummary(
            long generation,
            int poolCount,
            int mutatorCount,
            int selectorEntityIdBuckets,
            int selectorEntityTagBuckets,
            int wildcardPools) {
    }

    public record PressureEvaluationSnapshot(
            long snapshotId,
            long capturedAtMillis,
            String traceId,
            MutationRunMode mode,
            ResourceLocation dimensionId,
            BlockPos position,
            Optional<ResourceLocation> biomeId,
            ResourceLocation entityTypeId,
            String mobCategory,
            String spawnOrigin,
            String progressionMode,
            Optional<AttributedPlayerView> attributedPlayer,
            List<ResourceLocation> stageContext,
            ResourceLocation selectedPoolId,
            double basePressure,
            double effectivePressure,
            MutationChanceRollMode rollMode,
            OptionalDouble rolledValue,
            boolean chancePassed,
            WorldAwakenedEffectiveDifficultyScalarService.ScalarBreakdown scalarBreakdown,
            boolean categoryRestrictionDataAvailable,
            boolean categoryAllowed,
            boolean peacefulBlocked,
            String sourceKey) {
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            ResourceLocation parsed = parseResourceLocation(object.get(key).getAsString());
            if (parsed != null) {
                return Optional.of(parsed);
            }
        }
        return Optional.empty();
    }

    private static Optional<TagMatcher> readTagMatcher(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            Optional<TagMatcher> matcher = TagMatcher.compile(object.get(key).getAsString());
            if (matcher.isPresent()) {
                return matcher;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> readString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                continue;
            }
            String value = object.get(key).getAsString();
            if (!value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static OptionalDouble readDouble(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber()) {
                continue;
            }
            return OptionalDouble.of(object.get(key).getAsDouble());
        }
        return OptionalDouble.empty();
    }

    private static Set<Integer> parseMoonPhases(JsonObject object) {
        LinkedHashSet<Integer> phases = new LinkedHashSet<>();
        if (object.has("phase") && object.get("phase").isJsonPrimitive()) {
            parseMoonPhaseValue(object.get("phase"), phases);
        }
        if (object.has("phases") && object.get("phases").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("phases")) {
                parseMoonPhaseValue(element, phases);
            }
        }
        if (phases.isEmpty()) {
            phases.add(0);
        }
        return Set.copyOf(phases);
    }

    private static void parseMoonPhaseValue(JsonElement raw, Set<Integer> phases) {
        if (raw == null || raw.isJsonNull()) {
            return;
        }
        if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
            phases.add(Math.floorMod(raw.getAsInt(), 8));
            return;
        }
        if (!raw.isJsonPrimitive()) {
            return;
        }
        String value = raw.getAsString().toLowerCase(Locale.ROOT);
        Integer mapped = switch (value) {
            case "full_moon", "full" -> 0;
            case "waning_gibbous" -> 1;
            case "last_quarter" -> 2;
            case "waning_crescent" -> 3;
            case "new_moon", "new" -> 4;
            case "waxing_crescent" -> 5;
            case "first_quarter" -> 6;
            case "waxing_gibbous" -> 7;
            default -> null;
        };
        if (mapped != null) {
            phases.add(mapped);
        }
    }

    private static ResourceLocation parseResourceLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed != null) {
            return parsed;
        }
        if (!raw.contains(":")) {
            return ResourceLocation.tryParse("worldawakened:" + raw);
        }
        return null;
    }

    private static JsonObject readParametersObject(JsonObject object) {
        if (!object.has("parameters") || !object.get("parameters").isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject("parameters");
    }

    private static boolean isNodeEnabled(JsonObject node) {
        if (!node.has("enabled")) {
            return true;
        }
        JsonElement enabled = node.get("enabled");
        return !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean() || enabled.getAsBoolean();
    }
}

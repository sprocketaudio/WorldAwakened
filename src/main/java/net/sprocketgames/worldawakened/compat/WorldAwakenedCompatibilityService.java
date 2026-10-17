package net.sprocketgames.worldawakened.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.sprocketgames.worldawakened.config.WorldAwakenedCommonConfig;
import net.sprocketgames.worldawakened.config.WorldAwakenedFeatureGates;
import net.sprocketgames.worldawakened.data.definition.ApotheosisMode;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackService;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedDiagnosticCodes;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLog;
import net.sprocketgames.worldawakened.debug.WorldAwakenedLogCategory;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationResult;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageMutationStatus;
import net.sprocketgames.worldawakened.progression.WorldAwakenedStageService;

public final class WorldAwakenedCompatibilityService {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String APOTHEOSIS_PROVIDER_KEY = "apotheosis_world_tier";
    private static final String APOTHEOSIS_MOD_ID = "apotheosis";
    private static final String TIER_THRESHOLD_UNLOCK_PATH = "tier_threshold_unlock";
    private static final double APOTHEOSIS_TIER_SCALAR_STEP = 0.10D;
    private static final double APOTHEOSIS_TIER_SCALAR_MIN = 0.10D;
    private static final double APOTHEOSIS_TIER_SCALAR_MAX = 4.00D;
    private static final long APOTHEOSIS_STAGE_RECONCILE_INTERVAL_TICKS = 20L;

    private final WorldAwakenedDatapackService datapackService;
    private final AtomicReference<CachedProfiles> cachedProfiles =
            new AtomicReference<>(new CachedProfiles(0L, Map.of(), List.of()));
    private final AtomicReference<ApotheosisProviderBinding> apotheosisProviderBinding =
            new AtomicReference<>(ApotheosisProviderBinding.unbound());
    private final AtomicBoolean apotheosisProviderInvocationLogged = new AtomicBoolean(false);

    public WorldAwakenedCompatibilityService(WorldAwakenedDatapackService datapackService) {
        this.datapackService = datapackService;
    }

    public List<IntegrationActivation> listIntegrations() {
        return compiledProfiles().orderedProfiles().stream()
                .map(this::evaluateActivation)
                .toList();
    }

    public Optional<IntegrationActivation> integrationActivation(ResourceLocation integrationId) {
        CompiledIntegrationProfile profile = compiledProfiles().profilesById().get(integrationId);
        if (profile == null) {
            return Optional.empty();
        }
        return Optional.of(evaluateActivation(profile));
    }

    public Optional<IntegrationActivation> integrationActivationByModId(String modId) {
        if (modId == null || modId.isBlank()) {
            return Optional.empty();
        }
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        return compiledProfiles().orderedProfiles().stream()
                .filter(profile -> profile.modId().equals(normalized))
                .findFirst()
                .map(this::evaluateActivation);
    }

    public boolean apotheosisCompatActive() {
        return apotheosisActivation()
                .map(IntegrationActivation::active)
                .orElse(false);
    }

    public Set<ResourceLocation> apotheosisSensitiveLootTargets() {
        LinkedHashSet<ResourceLocation> targets = new LinkedHashSet<>();
        for (CompiledIntegrationProfile profile : compiledProfiles().orderedProfiles()) {
            if (APOTHEOSIS_MOD_ID.equals(profile.modId())) {
                targets.addAll(profile.lootTargets());
            }
        }
        return Set.copyOf(targets);
    }

    public List<ResourceLocation> integrationIds() {
        return compiledProfiles().orderedProfiles().stream()
                .map(CompiledIntegrationProfile::id)
                .toList();
    }

    public List<String> scalarProviderKeys() {
        boolean hasApotheosisProfile = compiledProfiles().orderedProfiles().stream()
                .anyMatch(profile -> APOTHEOSIS_MOD_ID.equals(profile.modId()));
        if (!hasApotheosisProfile) {
            return List.of();
        }
        return List.of(APOTHEOSIS_PROVIDER_KEY);
    }

    public List<DerivedStageHookView> apotheosisDerivedStageHooks() {
        return compiledProfiles().orderedProfiles().stream()
                .filter(profile -> APOTHEOSIS_MOD_ID.equals(profile.modId()))
                .flatMap(profile -> profile.stageHooks().stream()
                        .map(hook -> new DerivedStageHookView(
                                profile.id(),
                                hook.authoredIndex(),
                                hook.tierThreshold(),
                                hook.unlockStage())))
                .toList();
    }

    public ApotheosisTierResolution resolveApotheosisWorldTier(
            ServerLevel level,
            ServerPlayer player,
            ApotheosisTierUseCase useCase) {
        Optional<IntegrationActivation> activation = apotheosisActivation();
        if (activation.isEmpty()) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    "integration_profile_missing",
                    Optional.empty());
        }
        IntegrationActivation integration = activation.get();
        if (!integration.active()) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    integration.inactiveReason(),
                    Optional.of(integration));
        }
        if (!useCase.allowedByConfig()) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.CONFIG_GATE_INVALID,
                    "config_gate_disabled:" + useCase.configGateKey(),
                    Optional.of(integration));
        }
        if (player == null) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID,
                    "player_context_missing",
                    Optional.of(integration));
        }

        ApotheosisProviderBinding binding = apotheosisBinding();
        if (!binding.available()) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    binding.detail(),
                    Optional.of(integration));
        }
        OptionalDouble tier = readApotheosisTier(binding, player);
        if (tier.isEmpty()) {
            return ApotheosisTierResolution.unavailable(
                    useCase,
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    "tier_read_failed",
                    Optional.of(integration));
        }
        double scalarContribution = scalarContributionForTier(tier.getAsDouble(), useCase);
        return ApotheosisTierResolution.available(
                useCase,
                tier.getAsDouble(),
                scalarContribution,
                Optional.of(integration));
    }

    public ScalarProviderEvaluation evaluateScalarProvider(
            String providerKey,
            ServerLevel level,
            ServerPlayer player) {
        if (providerKey == null || providerKey.isBlank()) {
            return ScalarProviderEvaluation.notFound(
                    "<blank>",
                    WorldAwakenedDiagnosticCodes.DEBUG_SCALAR_PROVIDER_NOT_FOUND,
                    "provider_key_missing");
        }
        String normalized = providerKey.trim().toLowerCase(Locale.ROOT);
        if (!APOTHEOSIS_PROVIDER_KEY.equals(normalized)) {
            return ScalarProviderEvaluation.notFound(
                    normalized,
                    WorldAwakenedDiagnosticCodes.DEBUG_SCALAR_PROVIDER_NOT_FOUND,
                    "provider_unknown");
        }
        ApotheosisTierResolution tier = resolveApotheosisWorldTier(level, player, ApotheosisTierUseCase.SCALAR_PROVIDER);
        if (!tier.available()) {
            return ScalarProviderEvaluation.unavailable(
                    normalized,
                    tier.diagnosticCode(),
                    tier.detail(),
                    tier.scalarContribution(),
                    tier.integrationActivation());
        }
        return ScalarProviderEvaluation.available(
                normalized,
                tier.tierValue().orElse(0.0D),
                tier.scalarContribution(),
                tier.integrationActivation());
    }

    public void onLevelTick(ServerLevel level, WorldAwakenedStageService stageService) {
        if (level == null || stageService == null) {
            return;
        }
        if (level.getGameTime() % APOTHEOSIS_STAGE_RECONCILE_INTERVAL_TICKS != 0L) {
            return;
        }
        if (!derivedStageModeEnabled(currentApotheosisMode())) {
            return;
        }
        if (apotheosisDerivedStageHooks().isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            reconcileApotheosisDerivedStages(level, player, stageService, "level_tick");
        }
    }

    public DerivedStageReconcileSummary reconcileApotheosisDerivedStages(
            ServerLevel level,
            ServerPlayer player,
            WorldAwakenedStageService stageService,
            String reason) {
        ApotheosisMode mode = currentApotheosisMode();
        if (!derivedStageModeEnabled(mode)) {
            return DerivedStageReconcileSummary.skipped("mode_disables_derived_stage");
        }

        List<CompiledIntegrationProfile> activeProfiles = compiledProfiles().orderedProfiles().stream()
                .filter(profile -> APOTHEOSIS_MOD_ID.equals(profile.modId()))
                .filter(profile -> !profile.stageHooks().isEmpty())
                .filter(profile -> evaluateActivation(profile).active())
                .toList();
        if (activeProfiles.isEmpty()) {
            return DerivedStageReconcileSummary.skipped("no_active_stage_hooks");
        }

        ApotheosisTierResolution tier = resolveApotheosisWorldTier(level, player, ApotheosisTierUseCase.STAGE_UNLOCKS);
        if (!tier.available() || tier.tierValue().isEmpty()) {
            return DerivedStageReconcileSummary.unavailable(
                    tier.diagnosticCode(),
                    tier.detail(),
                    activeProfiles.size(),
                    activeProfiles.stream().mapToInt(profile -> profile.stageHooks().size()).sum());
        }

        return applyApotheosisDerivedStageHooks(level, player, tier.tierValue().getAsDouble(), stageService, reason, activeProfiles);
    }

    private CachedProfiles compiledProfiles() {
        WorldAwakenedDatapackSnapshot snapshot = datapackService.currentSnapshot();
        CachedProfiles cached = cachedProfiles.get();
        if (cached.generation() == snapshot.generation()) {
            return cached;
        }

        LinkedHashMap<ResourceLocation, CompiledIntegrationProfile> byId = new LinkedHashMap<>();
        List<IntegrationProfileDefinition> ordered = snapshot.data().integrationProfiles().values().stream()
                .sorted(Comparator.comparing(definition -> definition.id().toString()))
                .toList();
        for (IntegrationProfileDefinition definition : ordered) {
            if (!definition.enabled()) {
                continue;
            }
            CompiledIntegrationProfile compiled = CompiledIntegrationProfile.compile(definition, snapshot);
            byId.put(compiled.id(), compiled);
        }
        CachedProfiles refreshed = new CachedProfiles(
                snapshot.generation(),
                Map.copyOf(byId),
                List.copyOf(byId.values()));
        cachedProfiles.set(refreshed);
        return refreshed;
    }

    private Optional<IntegrationActivation> apotheosisActivation() {
        List<IntegrationActivation> activations = compiledProfiles().orderedProfiles().stream()
                .filter(profile -> APOTHEOSIS_MOD_ID.equals(profile.modId()))
                .map(this::evaluateActivation)
                .toList();
        return activations.stream()
                .filter(IntegrationActivation::active)
                .findFirst()
                .or(() -> activations.stream().findFirst());
    }

    private IntegrationActivation evaluateActivation(CompiledIntegrationProfile profile) {
        boolean autoDetect = WorldAwakenedCommonConfig.AUTO_DETECT.get();
        boolean defaultEnableDetected = WorldAwakenedCommonConfig.DEFAULT_ENABLE_DETECTED_INTEGRATIONS.get();
        boolean loaded = ModList.get().isLoaded(profile.modId());
        boolean configEnabled = configGateEnabled(profile.configKey());
        boolean profileEnabled = profile.enabledByDefault() || defaultEnableDetected;
        boolean active = autoDetect && profileEnabled && loaded && configEnabled;

        String inactiveReason = "";
        if (!active) {
            if (!autoDetect) {
                inactiveReason = "auto_detect_disabled";
            } else if (!profileEnabled) {
                inactiveReason = "profile_disabled";
            } else if (!loaded) {
                inactiveReason = "mod_not_loaded";
            } else if (!configEnabled) {
                inactiveReason = "config_gate_disabled:" + profile.configKey();
            } else {
                inactiveReason = "integration_inactive";
            }
        }

        return new IntegrationActivation(
                profile.id(),
                profile.modId(),
                profile.displayName(),
                autoDetect,
                defaultEnableDetected,
                profile.enabledByDefault(),
                profileEnabled,
                loaded,
                profile.configKey(),
                configEnabled,
                active,
                inactiveReason,
                profile.stageHookCount(),
                profile.triggerHookCount(),
                profile.specialConditionCount(),
                profile.lootTargets());
    }

    private static boolean configGateEnabled(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "general.enable_mod" -> WorldAwakenedCommonConfig.ENABLE_MOD.get();
            case "general.debug_logging" -> WorldAwakenedCommonConfig.DEBUG_LOGGING.get();
            case "general.enable_debug_commands" -> WorldAwakenedCommonConfig.ENABLE_DEBUG_COMMANDS.get();
            case "general.validation_logging" -> WorldAwakenedCommonConfig.VALIDATION_LOGGING.get();
            case "loot.enable_loot_evolution" -> WorldAwakenedCommonConfig.ENABLE_LOOT_EVOLUTION.get();
            case "mutators.enable_mutators" -> WorldAwakenedFeatureGates.mutatorsEnabled();
            case "invasions.enable_invasions" -> WorldAwakenedFeatureGates.pressureEventsEnabled();
            case "ascension.enable_ascension" -> WorldAwakenedFeatureGates.ascensionEnabled();
            case "compat.apotheosis.enabled" -> WorldAwakenedCommonConfig.APOTHEOSIS_ENABLED.get();
            case "compat.apotheosis.allow_world_tier_conditions" -> WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_CONDITIONS.get();
            case "compat.apotheosis.allow_world_tier_stage_unlocks" -> WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_STAGE_UNLOCKS.get();
            case "compat.apotheosis.allow_world_tier_loot_scaling" -> WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_LOOT_SCALING.get();
            case "compat.apotheosis.allow_world_tier_invasion_scaling" -> WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_INVASION_SCALING.get();
            case "compat.apotheosis.allow_world_tier_mutator_scaling" -> WorldAwakenedCommonConfig.ALLOW_WORLD_TIER_MUTATOR_SCALING.get();
            default -> false;
        };
    }

    private ApotheosisProviderBinding apotheosisBinding() {
        ApotheosisProviderBinding cached = apotheosisProviderBinding.get();
        if (cached.bound()) {
            return cached;
        }
        ApotheosisProviderBinding resolved = bindApotheosisProvider();
        apotheosisProviderBinding.set(resolved);
        return resolved;
    }

    private static ApotheosisProviderBinding bindApotheosisProvider() {
        if (!ModList.get().isLoaded(APOTHEOSIS_MOD_ID)) {
            return ApotheosisProviderBinding.unavailable("mod_not_loaded");
        }
        try {
            Class<?> worldTierClass = Class.forName("dev.shadowsoffire.apotheosis.tiers.WorldTier");
            Method method = worldTierClass.getMethod("getTier", net.minecraft.world.entity.player.Player.class);
            return ApotheosisProviderBinding.available(method);
        } catch (ReflectiveOperationException exception) {
            WorldAwakenedLog.warn(
                    LOGGER,
                    WorldAwakenedLogCategory.COMPAT,
                    "code={} failed to bind Apotheosis tier provider: {}",
                    WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                    exception.toString());
            return ApotheosisProviderBinding.unavailable("provider_binding_failed");
        }
    }

    private OptionalDouble readApotheosisTier(ApotheosisProviderBinding binding, ServerPlayer player) {
        try {
            Object value = binding.getTierMethod().invoke(null, player);
            OptionalDouble resolved = coerceTierValue(value);
            if (resolved.isPresent()) {
                return resolved;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (apotheosisProviderInvocationLogged.compareAndSet(false, true)) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.COMPAT,
                        "code={} failed to invoke Apotheosis tier provider: {}",
                        WorldAwakenedDiagnosticCodes.INTEGRATION_INACTIVE,
                        exception.toString());
            }
        }
        return OptionalDouble.empty();
    }

    static OptionalDouble coerceTierValue(Object value) {
        if (value == null) {
            return OptionalDouble.empty();
        }
        if (value instanceof Number number) {
            return OptionalDouble.of(number.doubleValue());
        }
        if (value instanceof Enum<?> enumValue) {
            return OptionalDouble.of(enumValue.ordinal());
        }

        String raw = value.toString();
        if (raw == null || raw.isBlank()) {
            return OptionalDouble.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        try {
            return OptionalDouble.of(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
        }

        String token = normalized;
        int namespaceSplit = token.indexOf(':');
        if (namespaceSplit >= 0 && namespaceSplit + 1 < token.length()) {
            token = token.substring(namespaceSplit + 1);
        }
        return switch (token) {
            case "haven" -> OptionalDouble.of(0.0D);
            case "frontier" -> OptionalDouble.of(1.0D);
            case "ascent" -> OptionalDouble.of(2.0D);
            case "summit" -> OptionalDouble.of(3.0D);
            case "pinnacle" -> OptionalDouble.of(4.0D);
            default -> OptionalDouble.empty();
        };
    }

    private static double scalarContributionForTier(double tier, ApotheosisTierUseCase useCase) {
        ApotheosisMode mode = currentApotheosisMode();
        if (!useCase.supportsScalarContribution()) {
            return 1.0D;
        }
        if (mode != ApotheosisMode.SCALAR && mode != ApotheosisMode.HYBRID) {
            return 1.0D;
        }
        double normalizedTier = Math.max(0.0D, tier);
        double raw = 1.0D + (normalizedTier * APOTHEOSIS_TIER_SCALAR_STEP);
        return clamp(raw, APOTHEOSIS_TIER_SCALAR_MIN, APOTHEOSIS_TIER_SCALAR_MAX);
    }

    private static ApotheosisMode currentApotheosisMode() {
        String raw = WorldAwakenedCommonConfig.APOTHEOSIS_MODE.get();
        if (raw == null || raw.isBlank()) {
            return ApotheosisMode.HYBRID;
        }
        try {
            return ApotheosisMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ApotheosisMode.HYBRID;
        }
    }

    DerivedStageReconcileSummary applyApotheosisDerivedStageHooks(
            ServerLevel level,
            ServerPlayer player,
            double tierValue,
            WorldAwakenedStageService stageService,
            String reason) {
        List<CompiledIntegrationProfile> apotheosisProfiles = compiledProfiles().orderedProfiles().stream()
                .filter(profile -> APOTHEOSIS_MOD_ID.equals(profile.modId()))
                .filter(profile -> !profile.stageHooks().isEmpty())
                .toList();
        return applyApotheosisDerivedStageHooks(level, player, tierValue, stageService, reason, apotheosisProfiles);
    }

    private DerivedStageReconcileSummary applyApotheosisDerivedStageHooks(
            ServerLevel level,
            ServerPlayer player,
            double tierValue,
            WorldAwakenedStageService stageService,
            String reason,
            List<CompiledIntegrationProfile> profiles) {
        if (level == null || player == null || stageService == null) {
            return DerivedStageReconcileSummary.unavailable(
                    WorldAwakenedDiagnosticCodes.DEBUG_CONTEXT_INVALID,
                    "player_context_missing",
                    profiles.size(),
                    profiles.stream().mapToInt(profile -> profile.stageHooks().size()).sum());
        }

        int matchedHooks = 0;
        int unlockedStages = 0;
        int alreadyUnlockedStages = 0;
        int hookCount = 0;
        String normalizedReason = reason == null || reason.isBlank() ? "unknown" : reason.trim().toLowerCase(Locale.ROOT);
        for (CompiledIntegrationProfile profile : profiles) {
            hookCount += profile.stageHooks().size();
            for (CompiledTierThresholdStageHook hook : profile.stageHooks()) {
                if (tierValue < hook.tierThreshold()) {
                    continue;
                }
                matchedHooks++;
                WorldAwakenedStageMutationResult result = stageService.unlockStage(
                        level,
                        player,
                        hook.unlockStage(),
                        "compat.apotheosis.stage_hook:"
                                + profile.id()
                                + "#"
                                + hook.authoredIndex()
                                + ":"
                                + normalizedReason);
                if (result.status() == WorldAwakenedStageMutationStatus.UNLOCKED) {
                    unlockedStages++;
                    WorldAwakenedLog.debug(
                            LOGGER,
                            WorldAwakenedLogCategory.COMPAT,
                            "Applied Apotheosis derived-stage hook profile={} threshold={} player={} stage={}",
                            profile.id(),
                            hook.tierThreshold(),
                            player.getGameProfile().getName(),
                            hook.unlockStage());
                } else if (result.status() == WorldAwakenedStageMutationStatus.ALREADY_UNLOCKED) {
                    alreadyUnlockedStages++;
                } else if (result.status() == WorldAwakenedStageMutationStatus.INVALID) {
                    WorldAwakenedLog.warn(
                            LOGGER,
                            WorldAwakenedLogCategory.COMPAT,
                            "code={} skipped Apotheosis derived-stage hook profile={} stage={} detail={}",
                            WorldAwakenedDiagnosticCodes.STAGE_REF_MISSING,
                            profile.id(),
                            hook.unlockStage(),
                            result.message());
                }
            }
        }

        return DerivedStageReconcileSummary.applied(
                tierValue,
                profiles.size(),
                hookCount,
                matchedHooks,
                unlockedStages,
                alreadyUnlockedStages);
    }

    private static boolean derivedStageModeEnabled(ApotheosisMode mode) {
        return mode == ApotheosisMode.DERIVED_STAGE || mode == ApotheosisMode.HYBRID;
    }

    private static OptionalDouble readThreshold(JsonObject parameters) {
        for (String key : List.of("tier", "threshold", "value", "min", "gte", "at_least")) {
            JsonElement raw = parameters.get(key);
            if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                double value = raw.getAsDouble();
                if (value >= 0.0D) {
                    return OptionalDouble.of(value);
                }
            }
        }
        return OptionalDouble.empty();
    }

    private static ResourceLocation readResourceLocation(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement raw = object.get(key);
            if (raw == null || !raw.isJsonPrimitive()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(raw.getAsString());
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    public enum ApotheosisTierUseCase {
        CONDITIONS("compat.apotheosis.allow_world_tier_conditions", false),
        STAGE_UNLOCKS("compat.apotheosis.allow_world_tier_stage_unlocks", false),
        LOOT_SCALING("compat.apotheosis.allow_world_tier_loot_scaling", true),
        INVASION_SCALING("compat.apotheosis.allow_world_tier_invasion_scaling", true),
        MUTATOR_SCALING("compat.apotheosis.allow_world_tier_mutator_scaling", true),
        SCALAR_PROVIDER("", true);

        private final String configGateKey;
        private final boolean supportsScalarContribution;

        ApotheosisTierUseCase(String configGateKey, boolean supportsScalarContribution) {
            this.configGateKey = configGateKey;
            this.supportsScalarContribution = supportsScalarContribution;
        }

        public String configGateKey() {
            return configGateKey;
        }

        public boolean supportsScalarContribution() {
            return supportsScalarContribution;
        }

        public boolean allowedByConfig() {
            if (configGateKey.isBlank()) {
                return true;
            }
            return configGateEnabled(configGateKey);
        }
    }

    public record IntegrationActivation(
            ResourceLocation integrationId,
            String modId,
            String displayName,
            boolean autoDetectEnabled,
            boolean defaultEnableDetectedIntegrations,
            boolean enabledByProfile,
            boolean enabledEffective,
            boolean modLoaded,
            String configGateKey,
            boolean configGateEnabled,
            boolean active,
            String inactiveReason,
            int stageHookCount,
            int triggerHookCount,
            int specialConditionCount,
            Set<ResourceLocation> lootTargets) {
    }

    public record ApotheosisTierResolution(
            ApotheosisTierUseCase useCase,
            String providerKey,
            boolean available,
            OptionalDouble tierValue,
            double scalarContribution,
            String diagnosticCode,
            String detail,
            Optional<IntegrationActivation> integrationActivation) {
        static ApotheosisTierResolution available(
                ApotheosisTierUseCase useCase,
                double tierValue,
                double scalarContribution,
                Optional<IntegrationActivation> integrationActivation) {
            return new ApotheosisTierResolution(
                    useCase,
                    APOTHEOSIS_PROVIDER_KEY,
                    true,
                    OptionalDouble.of(tierValue),
                    scalarContribution,
                    "",
                    "",
                    integrationActivation);
        }

        static ApotheosisTierResolution unavailable(
                ApotheosisTierUseCase useCase,
                String diagnosticCode,
                String detail,
                Optional<IntegrationActivation> integrationActivation) {
            return new ApotheosisTierResolution(
                    useCase,
                    APOTHEOSIS_PROVIDER_KEY,
                    false,
                    OptionalDouble.empty(),
                    1.0D,
                    diagnosticCode,
                    detail,
                    integrationActivation);
        }
    }

    public record ScalarProviderEvaluation(
            String providerKey,
            boolean found,
            boolean available,
            OptionalDouble rawValue,
            double scalarContribution,
            String diagnosticCode,
            String detail,
            Optional<IntegrationActivation> integrationActivation) {
        static ScalarProviderEvaluation notFound(String providerKey, String code, String detail) {
            return new ScalarProviderEvaluation(
                    providerKey,
                    false,
                    false,
                    OptionalDouble.empty(),
                    1.0D,
                    code,
                    detail,
                    Optional.empty());
        }

        static ScalarProviderEvaluation unavailable(
                String providerKey,
                String code,
                String detail,
                double scalarContribution,
                Optional<IntegrationActivation> integrationActivation) {
            return new ScalarProviderEvaluation(
                    providerKey,
                    true,
                    false,
                    OptionalDouble.empty(),
                    scalarContribution,
                    code,
                    detail,
                    integrationActivation);
        }

        static ScalarProviderEvaluation available(
                String providerKey,
                double rawValue,
                double scalarContribution,
                Optional<IntegrationActivation> integrationActivation) {
            return new ScalarProviderEvaluation(
                    providerKey,
                    true,
                    true,
                    OptionalDouble.of(rawValue),
                    scalarContribution,
                    "",
                    "",
                    integrationActivation);
        }
    }

    private record CachedProfiles(
            long generation,
            Map<ResourceLocation, CompiledIntegrationProfile> profilesById,
            List<CompiledIntegrationProfile> orderedProfiles) {
    }

    private record CompiledIntegrationProfile(
            ResourceLocation id,
            String modId,
            String displayName,
            boolean enabledByDefault,
            String configKey,
            Set<ResourceLocation> lootTargets,
            List<CompiledTierThresholdStageHook> stageHooks,
            int stageHookCount,
            int triggerHookCount,
            int specialConditionCount) {
        static CompiledIntegrationProfile compile(IntegrationProfileDefinition definition, WorldAwakenedDatapackSnapshot snapshot) {
            String modId = definition.modId().trim().toLowerCase(Locale.ROOT);
            String displayName = definition.displayName()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .orElse(definition.modId());
            String configKey = definition.configKey().trim().toLowerCase(Locale.ROOT);
            LinkedHashSet<ResourceLocation> lootTargets = new LinkedHashSet<>(definition.lootTargets());
            List<CompiledTierThresholdStageHook> stageHooks = new ArrayList<>();
            for (int index = 0; index < definition.stageHooks().size(); index++) {
                compileStageHook(definition.id(), definition.stageHooks().get(index), index, snapshot)
                        .ifPresent(stageHooks::add);
            }
            return new CompiledIntegrationProfile(
                    definition.id(),
                    modId,
                    displayName,
                    definition.enabledByDefault(),
                    configKey,
                    Set.copyOf(lootTargets),
                    List.copyOf(stageHooks),
                    stageHooks.size(),
                    definition.triggerHooks().size(),
                    definition.specialConditions().size());
        }

        private static Optional<CompiledTierThresholdStageHook> compileStageHook(
                ResourceLocation integrationId,
                JsonElement raw,
                int authoredIndex,
                WorldAwakenedDatapackSnapshot snapshot) {
            if (raw == null || !raw.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject node = raw.getAsJsonObject();
            JsonElement enabled = node.get("enabled");
            if (enabled != null
                    && enabled.isJsonPrimitive()
                    && enabled.getAsJsonPrimitive().isBoolean()
                    && !enabled.getAsBoolean()) {
                return Optional.empty();
            }

            JsonElement rawType = node.get("type");
            if (rawType == null || !rawType.isJsonPrimitive()) {
                return Optional.empty();
            }
            ResourceLocation typeId = ResourceLocation.tryParse(rawType.getAsString());
            if (typeId == null || !TIER_THRESHOLD_UNLOCK_PATH.equals(typeId.getPath().toLowerCase(Locale.ROOT))) {
                return Optional.empty();
            }

            JsonElement rawParameters = node.get("parameters");
            if (rawParameters == null || !rawParameters.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject parameters = rawParameters.getAsJsonObject();
            OptionalDouble threshold = readThreshold(parameters);
            ResourceLocation unlockStage = readResourceLocation(parameters, "unlock_stage", "stage", "stage_id");
            if (threshold.isEmpty() || unlockStage == null || !snapshot.data().stages().containsKey(unlockStage)) {
                WorldAwakenedLog.warn(
                        LOGGER,
                        WorldAwakenedLogCategory.COMPAT,
                        "code={} skipped invalid Apotheosis stage hook integration={} index={} stage={} threshold={}",
                        WorldAwakenedDiagnosticCodes.INTEGRATION_STAGE_HOOK_INVALID,
                        integrationId,
                        authoredIndex,
                        unlockStage == null ? "<none>" : unlockStage,
                        threshold.isPresent() ? threshold.getAsDouble() : "<none>");
                return Optional.empty();
            }
            return Optional.of(new CompiledTierThresholdStageHook(authoredIndex, threshold.getAsDouble(), unlockStage));
        }
    }

    private record CompiledTierThresholdStageHook(
            int authoredIndex,
            double tierThreshold,
            ResourceLocation unlockStage) {
    }

    public record DerivedStageHookView(
            ResourceLocation integrationId,
            int authoredIndex,
            double tierThreshold,
            ResourceLocation unlockStage) {
    }

    public record DerivedStageReconcileSummary(
            boolean evaluated,
            boolean tierAvailable,
            OptionalDouble tierValue,
            int profileCount,
            int hookCount,
            int matchedHookCount,
            int unlockedStageCount,
            int alreadyUnlockedStageCount,
            String diagnosticCode,
            String detail) {
        static DerivedStageReconcileSummary skipped(String detail) {
            return new DerivedStageReconcileSummary(
                    false,
                    false,
                    OptionalDouble.empty(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    "",
                    detail);
        }

        static DerivedStageReconcileSummary unavailable(
                String diagnosticCode,
                String detail,
                int profileCount,
                int hookCount) {
            return new DerivedStageReconcileSummary(
                    true,
                    false,
                    OptionalDouble.empty(),
                    profileCount,
                    hookCount,
                    0,
                    0,
                    0,
                    diagnosticCode == null ? "" : diagnosticCode,
                    detail == null ? "" : detail);
        }

        static DerivedStageReconcileSummary applied(
                double tierValue,
                int profileCount,
                int hookCount,
                int matchedHookCount,
                int unlockedStageCount,
                int alreadyUnlockedStageCount) {
            return new DerivedStageReconcileSummary(
                    true,
                    true,
                    OptionalDouble.of(tierValue),
                    profileCount,
                    hookCount,
                    matchedHookCount,
                    unlockedStageCount,
                    alreadyUnlockedStageCount,
                    "",
                    "");
        }
    }

    private record ApotheosisProviderBinding(
            boolean bound,
            boolean available,
            Method getTierMethod,
            String detail) {
        static ApotheosisProviderBinding unbound() {
            return new ApotheosisProviderBinding(false, false, null, "");
        }

        static ApotheosisProviderBinding available(Method method) {
            return new ApotheosisProviderBinding(true, true, method, "");
        }

        static ApotheosisProviderBinding unavailable(String detail) {
            return new ApotheosisProviderBinding(true, false, null, detail);
        }
    }
}

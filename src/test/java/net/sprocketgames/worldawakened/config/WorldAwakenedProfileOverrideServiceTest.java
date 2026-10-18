package net.sprocketgames.worldawakened.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.StageUnlockPolicy;
import net.sprocketgames.worldawakened.data.definition.SourceScope;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedCompiledData;
import net.sprocketgames.worldawakened.data.load.WorldAwakenedDatapackSnapshot;
import net.sprocketgames.worldawakened.debug.WorldAwakenedValidationSummary;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

class WorldAwakenedProfileOverrideServiceTest {
    private static final ResourceLocation POOL_ID = ResourceLocation.parse("test:pool");
    private static final ResourceLocation MUTATOR_ID = ResourceLocation.parse("test:mutator");

    @AfterEach
    void reset() {
        WorldAwakenedProfileOverrideService.unbind();
    }

    @Test
    void savedOverridesRoundTripThroughNbt() {
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.put(pool(0.25D));
        CompoundTag tag = data.save(new CompoundTag(), null);

        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(tag);

        assertEquals(1L, restored.revision());
        assertEquals(0.25D, restored.mutationPools().get(POOL_ID).mutationChance());
    }

    @Test
    void ascensionChoiceAndOfferAuthoringRoundTripTogether() {
        ResourceLocation stageId = ResourceLocation.parse("test:stirring");
        AscensionRewardDefinition reward = ascensionReward("test:steady_heart");
        AscensionOfferDefinition offer = ascensionOffer("test:first_choice", stageId, reward.id());
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();

        data.replaceAuthoring(Map.of(stageId, stage(stageId, 10)), Map.of(), Map.of(), Map.of(),
                Map.of(reward.id(), reward), Map.of(offer.id(), offer), Map.of());
        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(
                data.save(new CompoundTag(), null));

        assertEquals(reward.id(), restored.ascensionRewards().get(reward.id()).id());
        assertEquals(List.of(reward.id()), restored.ascensionOffers().get(offer.id()).candidateRewards());
        assertEquals(1L, restored.revision());
    }

    @Test
    void authoredMutatorRoundTripsWithOtherEditorDefinitions() {
        MobMutatorDefinition mutator = mutator("worldawakened:world/mutators/test");
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();

        data.replaceAuthoring(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(mutator.id(), mutator));
        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(
                data.save(new CompoundTag(), null));

        assertEquals(mutator.id(), restored.mobMutators().get(mutator.id()).id());
        assertEquals(1L, restored.revision());
    }

    @Test
    void authoredOfferMayReferenceBundledPhaseAndReward() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        ResourceLocation stageId = ResourceLocation.parse("worldawakened:defaults/stirring");
        AscensionRewardDefinition reward = ascensionReward("worldawakened:defaults/vital_spark");
        AscensionOfferDefinition offer = ascensionOffer("worldawakened:world/first_choice", stageId, reward.id());
        WorldAwakenedDatapackSnapshot source = ascensionSnapshot(stage(stageId, 10), reward);

        var result = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(offer.id(), offer), source);

        assertTrue(result.accepted(), result.message());
        assertTrue(WorldAwakenedProfileOverrideService.state().ascensionOffers().containsKey(offer.id()));
    }

    @Test
    void nativeAscensionChoiceRejectsUnsafeEffectAmount() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        AscensionRewardDefinition reward = ascensionReward("worldawakened:world/ascension_reward_unsafe", 100);

        var result = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(reward.id(), reward), Map.of(), snapshotWithoutPool());

        assertFalse(result.accepted());
        assertTrue(result.message().contains("safe editor range"));
    }

    @Test
    void nativeAscensionChoiceAcceptsThreeDistinctLiveEffects() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        AscensionRewardDefinition reward = multiEffectAscensionReward(
                "worldawakened:world/ascension_reward_three", 3);

        var result = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(reward.id(), reward), Map.of(), snapshotWithoutPool());

        assertTrue(result.accepted(), result.message());
        assertEquals(3, WorldAwakenedProfileOverrideService.state().ascensionRewards().get(reward.id()).components().size());
    }

    @Test
    void nativeAscensionChoiceRejectsMoreThanThreeEffects() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        AscensionRewardDefinition reward = multiEffectAscensionReward(
                "worldawakened:world/ascension_reward_four", 4);

        var result = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(reward.id(), reward), Map.of(), snapshotWithoutPool());

        assertFalse(result.accepted());
        assertTrue(result.message().contains("between one and three"));
    }

    @Test
    void obsoleteSavedPoolShapeIsDiscardedWithoutReplacingDatapackContent() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("revision", 7L);
        tag.putString("mutation_pools_json", """
                {"test:pool":{"schema_version":1,"id":"test:pool","enabled":true,"weight":1,
                "mutation_chance":0.25,"conditions":[],"eligible_dimensions":[],"eligible_biomes":[],
                "eligible_entities":[],"mutators":[{"id":"test:mutator","weight":1}]}}
                """);

        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(tag);

        assertTrue(restored.mutationPools().isEmpty());
        assertEquals(1, restored.discardedOverrideCount());
        assertEquals(7L, restored.revision());
    }

    @Test
    void unifiedSelectionAndCuratedCombinationRoundTripThroughNbt() {
        ResourceLocation secondId = ResourceLocation.parse("test:second");
        JsonObject first = new JsonObject(); first.addProperty("id", MUTATOR_ID.toString()); first.addProperty("weight", 3);
        JsonObject second = new JsonObject(); second.addProperty("id", secondId.toString()); second.addProperty("weight", 1);
        var combination = new MutationPoolDefinition.CuratedCombination(
                ResourceLocation.parse("test:rare_pair"), 7, List.of(MUTATOR_ID, secondId),
                List.of(ResourceLocation.parse("minecraft:zombie")));
        MutationPoolDefinition unified = new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(10, 0.11D, 0.02D, 2,
                        MutationPoolDefinition.CombinationMode.CURATED, List.of(combination)),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(ResourceLocation.parse("minecraft:zombie")), List.of(first, second));
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.put(unified);

        MutationPoolDefinition restored = WorldAwakenedProfileOverrideSavedData.fromTag(
                data.save(new CompoundTag(), null)).mutationPools().get(POOL_ID);

        assertEquals(0.02D, restored.additionalMutationChance());
        assertEquals(MutationPoolDefinition.CombinationMode.CURATED, restored.combinationMode());
        assertEquals(7, restored.rareCombinations().getFirst().weight());
        assertEquals(List.of(MUTATOR_ID, secondId), restored.rareCombinations().getFirst().mutators());
    }

    @Test
    void applyPublishesOnlyMatchingRevisionAndRevertRestoresSource() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        WorldAwakenedDatapackSnapshot source = snapshot(pool(0.10D));

        var applied = WorldAwakenedProfileOverrideService.apply(level, 0L, pool(0.40D), source);
        var stale = WorldAwakenedProfileOverrideService.apply(level, 0L, pool(0.60D), source);
        WorldAwakenedDatapackSnapshot overlaid = WorldAwakenedProfileOverrideService.overlay(source);
        var reverted = WorldAwakenedProfileOverrideService.revert(level, 1L, POOL_ID, source);

        assertTrue(applied.accepted());
        assertFalse(stale.accepted());
        assertEquals(0.40D, overlaid.data().mutationPools().get(POOL_ID).mutationChance());
        assertTrue(reverted.accepted());
        assertEquals(0.10D, WorldAwakenedProfileOverrideService.overlay(source).data().mutationPools().get(POOL_ID).mutationChance());
        assertTrue(stale.message().contains("changed on the server"));
    }

    @Test
    void disablingAndReenablingPoolPreservesItsSettingsAndSavedDefinition() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        MutationPoolDefinition configured = pool(.37D);
        MutationPoolDefinition disabled = withEnabled(configured, false);
        WorldAwakenedDatapackSnapshot source = snapshot(pool(.10D));

        var disabledResult = WorldAwakenedProfileOverrideService.apply(level, 0L, disabled, source);
        MutationPoolDefinition disabledCompiled = WorldAwakenedProfileOverrideService.overlay(source)
                .data().mutationPools().get(POOL_ID);
        CompoundTag persisted = WorldAwakenedProfileOverrideSavedData.get(level).save(new CompoundTag(), null);
        MutationPoolDefinition persistedPool = WorldAwakenedProfileOverrideSavedData.fromTag(persisted)
                .mutationPools().get(POOL_ID);
        var enabledResult = WorldAwakenedProfileOverrideService.apply(level, 1L, withEnabled(persistedPool, true), source);
        MutationPoolDefinition enabledCompiled = WorldAwakenedProfileOverrideService.overlay(source)
                .data().mutationPools().get(POOL_ID);

        assertTrue(disabledResult.accepted());
        assertFalse(disabledCompiled.enabled());
        assertEquals(.37D, disabledCompiled.mutationChance());
        assertFalse(persistedPool.enabled());
        assertEquals(.37D, persistedPool.mutationChance());
        assertTrue(enabledResult.accepted());
        assertTrue(enabledCompiled.enabled());
        assertEquals(.37D, enabledCompiled.mutationChance());
    }

    @Test
    void validationRejectsMissingMutatorAndOutOfRangeChance() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.overworld()).thenReturn(level);
        MutationPoolDefinition invalid = new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(1, 1.5D, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()), false, false,
                List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(),
                List.of(JsonParser.parseString("{\"id\":\"test:missing\",\"weight\":0}")));

        List<String> errors = WorldAwakenedProfileOverrideService.validate(server, invalid, snapshot(pool(0.1D)));

        assertTrue(errors.stream().anyMatch(error -> error.contains("100%")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("Unknown mutator")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("weights")));
    }

    @Test
    void disabledBlankDraftCanBeSavedButMustBeCompletedBeforeEnabling() {
        MinecraftServer server = mock(MinecraftServer.class);
        MutationPoolDefinition blankDisabled = new MutationPoolDefinition(1, POOL_ID, false,
                new MutationPoolDefinition.MutationSelection(1, .05D, 0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of());
        MutationPoolDefinition blankEnabled = withEnabled(blankDisabled, true);
        WorldAwakenedDatapackSnapshot source = snapshot(Map.of(), Map.of());

        assertTrue(WorldAwakenedProfileOverrideService.validate(server, blankDisabled, source).isEmpty());
        assertTrue(WorldAwakenedProfileOverrideService.validate(server, blankEnabled, source).stream()
                .anyMatch(error -> error.contains("At least one mutator")));
    }

    @Test
    void worldOwnedPoolCanBeCreatedWithoutDatapackSource() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null);
        WorldAwakenedDatapackSnapshot source = snapshotWithoutPool();

        var result = WorldAwakenedProfileOverrideService.apply(level, 0L, pool(0.2D), source);

        assertTrue(result.accepted());
        assertEquals(0.2D, WorldAwakenedProfileOverrideService.overlay(source).data().mutationPools()
                .get(POOL_ID).mutationChance());
    }

    @Test
    void trackCompilationActivatesOnlyHighestUnlockedTierAndLeavesPressurePoolsIndependent() {
        ResourceLocation firstStage = ResourceLocation.parse("test:first");
        ResourceLocation secondStage = ResourceLocation.parse("test:second");
        ResourceLocation thirdStage = ResourceLocation.parse("test:third");
        ResourceLocation firstPool = ResourceLocation.parse("test:first_pool");
        ResourceLocation secondPool = ResourceLocation.parse("test:second_pool");
        ResourceLocation thirdPool = ResourceLocation.parse("test:third_pool");
        ResourceLocation pressurePool = ResourceLocation.parse("test:pressure");
        Map<ResourceLocation, MutationPoolDefinition> pools = new java.util.LinkedHashMap<>();
        pools.put(firstPool, pool(firstPool)); pools.put(secondPool, pool(secondPool));
        pools.put(thirdPool, pool(thirdPool)); pools.put(pressurePool, pool(pressurePool));
        var track = new WorldAwakenedDifficultyTrackDefinition(ResourceLocation.parse("test:track"), "Test", "", true,
                List.of(), List.of(new WorldAwakenedDifficultyTrackDefinition.Tier(thirdStage, thirdPool),
                        new WorldAwakenedDifficultyTrackDefinition.Tier(firstStage, firstPool),
                        new WorldAwakenedDifficultyTrackDefinition.Tier(secondStage, secondPool)));

        WorldAwakenedProfileOverrideService.compileDifficultyTracks(pools, Map.of(track.id(), track), Map.of(
                firstStage, stage(firstStage, 10), secondStage, stage(secondStage, 20), thirdStage, stage(thirdStage, 30)));

        assertEquals(List.of("stage_unlocked:test:first", "stage_locked:test:second", "stage_locked:test:third"),
                gates(pools.get(firstPool)));
        assertEquals(List.of("stage_unlocked:test:second", "stage_locked:test:third"), gates(pools.get(secondPool)));
        assertEquals(List.of("stage_unlocked:test:third"), gates(pools.get(thirdPool)));
        assertTrue(pools.get(pressurePool).conditions().isEmpty());
    }

    @Test
    void phaseConfigurationsPreferSpecialisedMobsAndStillCompileOneSharedPhaseGate() {
        ResourceLocation stageId = ResourceLocation.parse("test:gathering_storm");
        ResourceLocation laterStageId = ResourceLocation.parse("test:fracture");
        ResourceLocation defaultPoolId = ResourceLocation.parse("test:default_mobs");
        ResourceLocation skeletonPoolId = ResourceLocation.parse("test:dangerous_skeletons");
        ResourceLocation laterPoolId = ResourceLocation.parse("test:later_default");
        ResourceLocation zombie = ResourceLocation.parse("minecraft:zombie");
        ResourceLocation skeleton = ResourceLocation.parse("minecraft:skeleton");
        ResourceLocation stray = ResourceLocation.parse("minecraft:stray");
        Map<ResourceLocation, MutationPoolDefinition> pools = new java.util.LinkedHashMap<>();
        pools.put(defaultPoolId, pool(defaultPoolId, List.of(zombie, skeleton, stray)));
        pools.put(skeletonPoolId, pool(skeletonPoolId, List.of(skeleton, stray)));
        pools.put(laterPoolId, pool(laterPoolId, List.of(zombie, skeleton, stray)));
        var track = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(
                        phaseTier(stageId, defaultPoolId, "minecraft:overworld", "Default Mobs", true),
                        phaseTier(stageId, skeletonPoolId, "minecraft:overworld", "Dangerous Skeletons", false),
                        phaseTier(laterStageId, laterPoolId, "minecraft:overworld", "Default Mobs", true)));

        WorldAwakenedProfileOverrideService.compileDifficultyTracks(pools, Map.of(track.id(), track), Map.of(
                stageId, stage(stageId, 30), laterStageId, stage(laterStageId, 40)));

        assertEquals(List.of(zombie), pools.get(defaultPoolId).eligibleEntities());
        assertEquals(List.of(skeleton, stray), pools.get(skeletonPoolId).eligibleEntities());
        assertEquals(List.of("stage_unlocked:test:gathering_storm", "stage_locked:test:fracture"),
                gates(pools.get(defaultPoolId)));
        assertEquals(List.of("stage_unlocked:test:gathering_storm", "stage_locked:test:fracture"),
                gates(pools.get(skeletonPoolId)));
        assertEquals(List.of(ResourceLocation.parse("minecraft:overworld")), pools.get(skeletonPoolId).eligibleDimensions());
    }

    @Test
    void phaseDefaultIsDisabledWhenSpecialisedConfigurationConsumesEveryMob() {
        ResourceLocation stageId = ResourceLocation.parse("test:fracture");
        ResourceLocation defaultPoolId = ResourceLocation.parse("test:default_mobs");
        ResourceLocation specialisedPoolId = ResourceLocation.parse("test:all_skeletons");
        ResourceLocation skeleton = ResourceLocation.parse("minecraft:skeleton");
        Map<ResourceLocation, MutationPoolDefinition> pools = new java.util.LinkedHashMap<>();
        pools.put(defaultPoolId, pool(defaultPoolId, List.of(skeleton)));
        pools.put(specialisedPoolId, pool(specialisedPoolId, List.of(skeleton)));
        var track = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(
                        phaseTier(stageId, defaultPoolId, "minecraft:overworld", "Default Mobs", true),
                        phaseTier(stageId, specialisedPoolId, "minecraft:overworld", "Skeletons", false)));

        WorldAwakenedProfileOverrideService.compileDifficultyTracks(pools, Map.of(track.id(), track),
                Map.of(stageId, stage(stageId, 30)));

        assertTrue(pools.get(defaultPoolId).eligibleEntities().isEmpty());
        assertFalse(pools.get(defaultPoolId).enabled());
        assertTrue(pools.get(specialisedPoolId).enabled());
    }

    @Test
    void disabledSpecialisedConfigurationDoesNotRemoveMobsFromDefaultConfiguration() {
        ResourceLocation stageId = ResourceLocation.parse("test:unease");
        ResourceLocation defaultPoolId = ResourceLocation.parse("test:default_mobs");
        ResourceLocation specialisedPoolId = ResourceLocation.parse("test:disabled_skeletons");
        ResourceLocation zombie = ResourceLocation.parse("minecraft:zombie");
        ResourceLocation skeleton = ResourceLocation.parse("minecraft:skeleton");
        Map<ResourceLocation, MutationPoolDefinition> pools = new java.util.LinkedHashMap<>();
        pools.put(defaultPoolId, pool(defaultPoolId, List.of(zombie, skeleton)));
        MutationPoolDefinition specialised = pool(specialisedPoolId, List.of(skeleton));
        pools.put(specialisedPoolId, withEnabled(specialised, false));
        var track = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(
                        phaseTier(stageId, defaultPoolId, "minecraft:overworld", "Default Mobs", true),
                        phaseTier(stageId, specialisedPoolId, "minecraft:overworld", "Skeletons", false)));

        WorldAwakenedProfileOverrideService.compileDifficultyTracks(pools, Map.of(track.id(), track),
                Map.of(stageId, stage(stageId, 20)));

        assertEquals(List.of(zombie, skeleton), pools.get(defaultPoolId).eligibleEntities());
        assertTrue(pools.get(defaultPoolId).enabled());
        assertFalse(pools.get(specialisedPoolId).enabled());
    }

    @Test
    void unrestrictedDefaultMaterializesTheRemainingEligibleMobsAtRuntime() {
        ResourceLocation zombie = ResourceLocation.parse("minecraft:zombie");
        ResourceLocation skeleton = ResourceLocation.parse("minecraft:skeleton");
        ResourceLocation creeper = ResourceLocation.parse("minecraft:creeper");

        List<ResourceLocation> remaining = WorldAwakenedProfileOverrideService.defaultEntitiesForRuntime(
                List.of(), java.util.Set.of(skeleton), java.util.Set.of(skeleton, zombie, creeper));

        assertEquals(List.of(creeper, zombie), remaining);
        assertTrue(WorldAwakenedProfileOverrideService.defaultEntitiesForRuntime(
                List.of(), java.util.Set.of(), java.util.Set.of(skeleton, zombie)).isEmpty(),
                "Without a specialised config the authored empty list must retain wildcard semantics");
    }

    @Test
    void removedSourceBackedConfigurationRestoresOriginalDefinitionAndKeepsCustomConfig() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null); when(server.levelKeys()).thenReturn(java.util.Set.of());
        ResourceLocation stageId = ResourceLocation.parse("test:unease");
        ResourceLocation customId = ResourceLocation.parse("test:custom_zombies");
        ResourceLocation zombie = ResourceLocation.parse("minecraft:zombie");
        MutationPoolDefinition original = new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(1, .04D, .01D, 2,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()), false, false,
                List.of(), Optional.empty(), Optional.empty(), List.of(ResourceLocation.parse("minecraft:overworld")),
                List.of(), List.of(zombie), pool(POOL_ID).mutators());
        MutationPoolDefinition removedOverride = withEnabled(new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(1, .40D, 0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()), false, false,
                original.conditions(), Optional.empty(), Optional.empty(), List.of(), List.of(),
                List.of(), original.mutators()), false);
        MutationPoolDefinition custom = withEnabled(pool(customId), false);
        StageDefinition stage = stage(stageId, 20);
        WorldAwakenedDatapackSnapshot source = snapshot(Map.of(stageId, stage), Map.of(POOL_ID, original));
        var removedTier = new WorldAwakenedDifficultyTrackDefinition.Tier(stageId, POOL_ID,
                Optional.of(ResourceLocation.parse("minecraft:overworld")), "Default Mobs", true, false);
        var customTier = phaseTier(stageId, customId, "minecraft:overworld", "Custom Zombies", false);
        var removedTrack = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(removedTier, customTier));

        var removed = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(),
                Map.of(POOL_ID, removedOverride, customId, custom), Map.of(removedTrack.id(), removedTrack), source);
        CompoundTag persisted = WorldAwakenedProfileOverrideSavedData.get(level).save(new CompoundTag(), null);
        var restoredSavedData = WorldAwakenedProfileOverrideSavedData.fromTag(persisted);
        var restoredTrack = new WorldAwakenedDifficultyTrackDefinition(removedTrack.id(), removedTrack.displayName(),
                removedTrack.description(), true, List.of(), List.of(
                        phaseTier(stageId, POOL_ID, "minecraft:overworld", "Default Mobs", true), customTier));
        var restored = WorldAwakenedProfileOverrideService.applyAuthoring(level, 1L, Map.of(), Map.of(),
                Map.of(customId, custom), Map.of(restoredTrack.id(), restoredTrack), source);
        WorldAwakenedDatapackSnapshot compiled = WorldAwakenedProfileOverrideService.overlay(source);

        assertTrue(removed.accepted(), removed.message());
        assertFalse(WorldAwakenedProfileOverrideService.overlay(source).data().mutationPools().get(customId).enabled());
        assertEquals(1, restoredSavedData.difficultyTracks().size(), "Removal tombstone survives restart persistence");
        assertTrue(restored.accepted(), restored.message());
        assertEquals(.04D, compiled.data().mutationPools().get(POOL_ID).mutationChance());
        assertEquals(.01D, compiled.data().mutationPools().get(POOL_ID).additionalMutationChance());
        assertEquals(List.of(zombie), compiled.data().mutationPools().get(POOL_ID).eligibleEntities());
        assertTrue(compiled.data().mutationPools().get(POOL_ID).enabled());
        assertTrue(WorldAwakenedProfileOverrideService.state().mutationPools().containsKey(customId));
        assertFalse(WorldAwakenedProfileOverrideService.state().mutationPools().containsKey(POOL_ID));
    }

    @Test
    void preExistingValidationIssueDoesNotBlockUnrelatedRecoveryEdit() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(null); when(server.levelKeys()).thenReturn(java.util.Set.of());
        ResourceLocation stageId = ResourceLocation.parse("test:unease");
        ResourceLocation specialisedId = ResourceLocation.parse("test:skeletons");
        ResourceLocation newDisabledId = ResourceLocation.parse("test:new_disabled");
        StageDefinition stage = stage(stageId, 20);
        MutationPoolDefinition unrestrictedDefault = pool(POOL_ID);
        MutationPoolDefinition specialised = pool(specialisedId, List.of(ResourceLocation.parse("minecraft:skeleton")));
        var existingTrack = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(
                        phaseTier(stageId, POOL_ID, "minecraft:overworld", "Default Mobs", true),
                        phaseTier(stageId, specialisedId, "minecraft:overworld", "Skeletons", false)));
        WorldAwakenedProfileOverrideSavedData.get(level).replaceAll(Map.of(), Map.of(),
                Map.of(specialisedId, specialised), Map.of(existingTrack.id(), existingTrack));
        WorldAwakenedDatapackSnapshot source = snapshot(Map.of(stageId, stage), Map.of(POOL_ID, unrestrictedDefault));

        MutationPoolDefinition newDisabled = withEnabled(pool(newDisabledId), false);
        var candidateTrack = new WorldAwakenedDifficultyTrackDefinition(existingTrack.id(), existingTrack.displayName(),
                existingTrack.description(), true, List.of(), List.of(
                        phaseTier(stageId, POOL_ID, "minecraft:overworld", "Default Mobs", true),
                        phaseTier(stageId, specialisedId, "minecraft:overworld", "Skeletons", false),
                        phaseTier(stageId, newDisabledId, "minecraft:overworld", "New Disabled", false)));
        var result = WorldAwakenedProfileOverrideService.applyAuthoring(level, 1L, Map.of(), Map.of(),
                Map.of(specialisedId, specialised, newDisabledId, newDisabled),
                Map.of(candidateTrack.id(), candidateTrack), source);

        assertTrue(result.accepted(), result.message());
        assertTrue(result.message().contains("older configuration"));
        assertTrue(WorldAwakenedProfileOverrideService.state().mutationPools().containsKey(newDisabledId));
    }

    @Test
    void isolatedEditedPhaseStillLocksAgainstLaterSharedTimelineStages() {
        ResourceLocation stageId = ResourceLocation.parse("test:stirring");
        ResourceLocation laterStageId = ResourceLocation.parse("test:unease");
        ResourceLocation poolId = ResourceLocation.parse("test:stirring_overworld");
        Map<ResourceLocation, MutationPoolDefinition> pools = new java.util.LinkedHashMap<>();
        pools.put(poolId, pool(poolId, List.of(ResourceLocation.parse("minecraft:zombie"))));
        var track = new WorldAwakenedDifficultyTrackDefinition(
                ResourceLocation.parse("worldawakened:world/phase_mutations"), "Phase mob configurations", "", true,
                List.of(), List.of(phaseTier(stageId, poolId, "minecraft:overworld", "Default Mobs", true)));

        WorldAwakenedProfileOverrideService.compileDifficultyTracks(pools, Map.of(track.id(), track), Map.of(
                stageId, stage(stageId, 10), laterStageId, stage(laterStageId, 20)));

        assertEquals(List.of("stage_unlocked:test:stirring", "stage_locked:test:unease"), gates(pools.get(poolId)));
    }

    @Test
    void authoredStageTrackAndPoolRoundTripTogether() {
        ResourceLocation stageId = ResourceLocation.parse("worldawakened:world/awakening");
        StageDefinition stage = new StageDefinition(1, stageId, List.of(), JsonParser.parseString("\"The Awakening\""),
                Optional.empty(), Optional.empty(), Optional.empty(), 10, true, true, List.of(), Optional.empty(),
                Optional.of("custom"), StageUnlockPolicy.CUMULATIVE, false);
        var track = new WorldAwakenedDifficultyTrackDefinition(ResourceLocation.parse("worldawakened:world/tracks/nether"),
                "Nether Difficulty", "", true, List.of(ResourceLocation.parse("minecraft:the_nether")),
                List.of(new WorldAwakenedDifficultyTrackDefinition.Tier(stageId, POOL_ID)));
        WorldAwakenedProfileOverrideSavedData data = new WorldAwakenedProfileOverrideSavedData();
        data.replaceAll(Map.of(stageId, stage), Map.of(), Map.of(POOL_ID, pool(0.1D)), Map.of(track.id(), track));

        WorldAwakenedProfileOverrideSavedData restored = WorldAwakenedProfileOverrideSavedData.fromTag(
                data.save(new CompoundTag(), null));

        assertEquals("The Awakening", restored.stages().get(stageId).displayName().getAsString());
        assertEquals(stageId, restored.difficultyTracks().get(track.id()).tiers().getFirst().stageId());
        assertEquals(ResourceLocation.parse("minecraft:the_nether"),
                restored.difficultyTracks().get(track.id()).eligibleDimensions().getFirst());
    }

    @Test
    void supportedAuthoredStageTriggersValidateAndInvalidScopeFails() {
        MinecraftServer server = mock(MinecraftServer.class);
        ResourceLocation stageId = ResourceLocation.parse("test:awakening");
        StageDefinition stage = stage(stageId, 10);
        List<TriggerRuleDefinition> valid = List.of(
                trigger("day", "world_day_milestone", SourceScope.WORLD, condition("world_day_gte", "value", 3), stageId),
                trigger("dimension", "player_enter_dimension", SourceScope.PLAYER,
                        condition("current_dimension", "dimension", "minecraft:the_nether"), stageId),
                trigger("advancement", "advancement_completed", SourceScope.PLAYER,
                        condition("advancement_completed", "advancement", "minecraft:story/mine_diamond"), stageId),
                trigger("mob", "entity_killed", SourceScope.PLAYER,
                        condition("entity_type", "entity", "minecraft:zombie"), stageId));
        Map<ResourceLocation, TriggerRuleDefinition> triggers = valid.stream().collect(java.util.stream.Collectors.toMap(
                TriggerRuleDefinition::id, value -> value));

        List<String> validErrors = WorldAwakenedProfileOverrideService.validateAuthoring(server, Map.of(stageId, stage),
                triggers, Map.of(), Map.of(), snapshotWithoutPool());
        TriggerRuleDefinition invalid = trigger("bad", "player_enter_dimension", SourceScope.WORLD,
                condition("current_dimension", "dimension", "minecraft:the_nether"), stageId);
        List<String> invalidErrors = WorldAwakenedProfileOverrideService.validateAuthoring(server, Map.of(stageId, stage),
                Map.of(invalid.id(), invalid), Map.of(), Map.of(), snapshotWithoutPool());

        assertTrue(validErrors.isEmpty(), () -> String.join(" | ", validErrors));
        assertTrue(invalidErrors.stream().anyMatch(error -> error.contains("player progression context")));
    }

    @Test
    void linkedStageTriggerTrackAndPoolApplyAtomicallyWithOneRevision() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level); when(level.getDataStorage()).thenReturn(null);
        ResourceLocation stageId = ResourceLocation.parse("test:awakening"); StageDefinition stage = stage(stageId, 10);
        TriggerRuleDefinition trigger = trigger("enter_nether", "player_enter_dimension", SourceScope.PLAYER,
                condition("current_dimension", "dimension", "minecraft:the_nether"), stageId);
        var track = new WorldAwakenedDifficultyTrackDefinition(ResourceLocation.parse("test:nether_track"), "Nether", "", true,
                List.of(), List.of(new WorldAwakenedDifficultyTrackDefinition.Tier(stageId, POOL_ID)));
        WorldAwakenedDatapackSnapshot source = snapshotWithoutPool();

        var accepted = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(stageId, stage),
                Map.of(trigger.id(), trigger), Map.of(POOL_ID, pool(0.1D)), Map.of(track.id(), track), source);
        var stale = WorldAwakenedProfileOverrideService.applyAuthoring(level, 0L, Map.of(), Map.of(), Map.of(), Map.of(), source);
        WorldAwakenedDatapackSnapshot overlaid = WorldAwakenedProfileOverrideService.overlay(source);

        assertTrue(accepted.accepted()); assertFalse(stale.accepted());
        assertEquals(1L, WorldAwakenedProfileOverrideService.state().revision());
        assertTrue(overlaid.data().stages().containsKey(stageId));
        assertTrue(overlaid.data().triggerRules().containsKey(trigger.id()));
        assertTrue(overlaid.data().mutationPools().containsKey(POOL_ID));
    }

    @Test
    void copiedStandardBecomesIndependentAuthoredContentAndCanBeReplacedByEmptyProfile() {
        ServerLevel level = mock(ServerLevel.class); MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server); when(server.overworld()).thenReturn(level); when(level.getDataStorage()).thenReturn(null);
        ResourceLocation stageId = ResourceLocation.parse("worldawakened:defaults/stages/stirring");
        MobMutatorDefinition mutator = mock(MobMutatorDefinition.class);
        WorldAwakenedCompiledData standard = new WorldAwakenedCompiledData(Map.of(stageId, stage(stageId, 10)),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(MUTATOR_ID, mutator), Map.of(POOL_ID, pool(0.1D)),
                Map.of(), Map.of(), Map.of(), Map.of(), WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));

        var copied = WorldAwakenedProfileOverrideService.copyProfile(level, 0L, standard);
        var copiedState = WorldAwakenedProfileOverrideService.state();
        var emptied = WorldAwakenedProfileOverrideService.startEmptyProfile(level, 1L, Map.of(MUTATOR_ID, mutator));
        var state = WorldAwakenedProfileOverrideService.state();

        assertTrue(copied.accepted());
        assertEquals("standard_copy", copiedState.profileKind());
        assertTrue(copiedState.stages().containsKey(stageId));
        assertTrue(copiedState.mutationPools().containsKey(POOL_ID));
        assertTrue(emptied.accepted());
        assertEquals("empty", state.profileKind());
        assertTrue(state.stages().isEmpty());
        assertTrue(state.mutationPools().isEmpty());
        assertTrue(state.mobMutators().containsKey(MUTATOR_ID), "Blank profiles retain the selectable mutator library");
        assertEquals(2L, state.revision());
    }

    private static MutationPoolDefinition pool(double chance) {
        JsonObject mutator = new JsonObject();
        mutator.addProperty("id", MUTATOR_ID.toString());
        mutator.addProperty("weight", 10);
        return new MutationPoolDefinition(1, POOL_ID, true,
                new MutationPoolDefinition.MutationSelection(10, chance, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()),
                false, false, List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(mutator));
    }

    private static MutationPoolDefinition pool(ResourceLocation id) {
        JsonObject mutator = new JsonObject(); mutator.addProperty("id", MUTATOR_ID.toString()); mutator.addProperty("weight", 1);
        return new MutationPoolDefinition(1, id, true,
                new MutationPoolDefinition.MutationSelection(1, 0.1D, 0.0D, 1,
                        MutationPoolDefinition.CombinationMode.ANY_COMPATIBLE, List.of()), false, false,
                List.of(), Optional.empty(), Optional.empty(), List.of(), List.of(), List.of(), List.of(mutator));
    }

    private static MutationPoolDefinition pool(ResourceLocation id, List<ResourceLocation> entities) {
        MutationPoolDefinition source = pool(id);
        return new MutationPoolDefinition(source.schemaVersion(), source.id(), source.enabled(), source.selection(),
                source.allowFromSpawner(), source.allowFromTrialSpawner(), source.conditions(), source.stageFilters(),
                source.apotheosisTierFilters(), List.of(ResourceLocation.parse("minecraft:overworld")),
                source.eligibleBiomes(), entities, source.mutators());
    }

    private static MutationPoolDefinition withEnabled(MutationPoolDefinition source, boolean enabled) {
        return new MutationPoolDefinition(source.schemaVersion(), source.id(), enabled, source.selection(),
                source.allowFromSpawner(), source.allowFromTrialSpawner(), source.conditions(), source.stageFilters(),
                source.apotheosisTierFilters(), source.eligibleDimensions(), source.eligibleBiomes(),
                source.eligibleEntities(), source.mutators());
    }

    private static WorldAwakenedDifficultyTrackDefinition.Tier phaseTier(ResourceLocation stageId,
            ResourceLocation poolId, String dimension, String name, boolean isDefault) {
        return new WorldAwakenedDifficultyTrackDefinition.Tier(stageId, poolId,
                Optional.of(ResourceLocation.parse(dimension)), name, isDefault, true);
    }

    private static List<String> gates(MutationPoolDefinition pool) {
        return pool.conditions().stream().map(JsonElement -> {
            JsonObject node = JsonElement.getAsJsonObject();
            String type = node.get("type").getAsString().substring("worldawakened:".length());
            return type + ":" + node.getAsJsonObject("parameters").get("stage").getAsString();
        }).toList();
    }

    private static StageDefinition stage(ResourceLocation id, int order) {
        return new StageDefinition(1, id, List.of(), JsonParser.parseString("\"" + id.getPath() + "\""), Optional.empty(),
                Optional.empty(), Optional.empty(), order, true, true, List.of(), Optional.empty(), Optional.of("test"),
                StageUnlockPolicy.CUMULATIVE, false);
    }

    private static TriggerRuleDefinition trigger(String id, String type, SourceScope scope, JsonElement condition,
            ResourceLocation stageId) {
        return new TriggerRuleDefinition(1, ResourceLocation.parse("test:" + id), true, 100,
                ResourceLocation.parse("worldawakened:" + type), scope,
                List.of(condition, condition("stage_locked", "stage", stageId.toString())),
                List.of(condition("unlock_stage", "stage", stageId.toString())), Optional.empty(), true);
    }

    private static JsonElement condition(String type, String parameter, Object value) {
        JsonObject node = new JsonObject(); node.addProperty("type", "worldawakened:" + type); JsonObject parameters = new JsonObject();
        if (value instanceof Number number) parameters.addProperty(parameter, number); else parameters.addProperty(parameter, value.toString());
        node.add("parameters", parameters); return node;
    }

    private static WorldAwakenedDatapackSnapshot snapshot(MutationPoolDefinition pool) {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(MUTATOR_ID, mock(MobMutatorDefinition.class)), Map.of(POOL_ID, pool), Map.of(), Map.of(), Map.of(),
                Map.of(), WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        return new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
    }

    private static WorldAwakenedDatapackSnapshot snapshot(Map<ResourceLocation, StageDefinition> stages,
            Map<ResourceLocation, MutationPoolDefinition> pools) {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(stages, Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(MUTATOR_ID, mock(MobMutatorDefinition.class)), pools, Map.of(), Map.of(), Map.of(),
                Map.of(), WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        return new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
    }

    private static WorldAwakenedDatapackSnapshot snapshotWithoutPool() {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(MUTATOR_ID, mock(MobMutatorDefinition.class)), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        return new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
    }

    private static AscensionRewardDefinition ascensionReward(String id) {
        return ascensionReward(id, 2);
    }

    private static MobMutatorDefinition mutator(String id) {
        return MobMutatorDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"schema_version":1,"id":"%s","display_name":"Test Mutator","enabled":true,"weight":1,
                "components":[{"type":"worldawakened:max_health_bonus","parameters":{"amount":4}}],
                "max_stack_count":1,"eligible_entities":[],"eligible_entity_tags":[],"excluded_entities":[],
                "excluded_entity_tags":[],"required_conditions":[],"applies_to_bosses":false,
                "applies_to_invaders":false,"application_contexts":["on_spawn"]}
                """.formatted(id))).getOrThrow();
    }

    private static AscensionRewardDefinition ascensionReward(String id, double amount) {
        JsonElement json = JsonParser.parseString("""
                {"schema_version":1,"id":"%s","display_name":"Steady Heart","description":"A permanent benefit.",
                "enabled":true,"components":[{"type":"worldawakened:max_health_bonus","parameters":{"amount":%s}}],
                "tags":[],"offer_weight":1,"requires_conditions":[],"forbidden_conditions":[],"max_rank":1}
                """.formatted(id, amount));
        return AscensionRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static AscensionRewardDefinition multiEffectAscensionReward(String id, int count) {
        String[] components = {
                "{\"type\":\"worldawakened:max_health_bonus\",\"parameters\":{\"amount\":2}}",
                "{\"type\":\"worldawakened:armor_bonus\",\"parameters\":{\"amount\":1}}",
                "{\"type\":\"worldawakened:attack_damage_bonus\",\"parameters\":{\"amount\":1}}",
                "{\"type\":\"worldawakened:luck_bonus\",\"parameters\":{\"amount\":1}}"
        };
        JsonElement json = JsonParser.parseString("""
                {"schema_version":1,"id":"%s","display_name":"Combined Choice","description":"Several permanent benefits.",
                "enabled":true,"components":[%s],"tags":[],"offer_weight":1,
                "requires_conditions":[],"forbidden_conditions":[],"max_rank":1}
                """.formatted(id, String.join(",", java.util.Arrays.copyOf(components, count))));
        return AscensionRewardDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static AscensionOfferDefinition ascensionOffer(String id, ResourceLocation stageId, ResourceLocation rewardId) {
        JsonElement json = JsonParser.parseString("""
                {"schema_version":1,"id":"%s","display_name":"First Choice","description":"Choose one.","enabled":true,
                "trigger_conditions":[],"stage_filters":{"all_of":["%s"]},"candidate_rewards":["%s"],
                "candidate_reward_tags":[],"offer_mode":"explicit_list","weighting_rules":{},"choice_count":1,
                "selection_count":1,"ui_priority":0,"allow_duplicates_across_players":true,
                "offer_repeat_policy":"once_per_player","reward_repeat_policy":"block_all"}
                """.formatted(id, stageId, rewardId));
        return AscensionOfferDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static WorldAwakenedDatapackSnapshot ascensionSnapshot(StageDefinition stage,
            AscensionRewardDefinition reward) {
        WorldAwakenedCompiledData data = new WorldAwakenedCompiledData(Map.of(stage.id(), stage), Map.of(), Map.of(),
                Map.of(reward.id(), reward), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                WorldAwakenedDataDrivenBossClassifier.fromMaps(List.of()));
        return new WorldAwakenedDatapackSnapshot(1L, Instant.EPOCH, data, WorldAwakenedValidationSummary.empty());
    }
}

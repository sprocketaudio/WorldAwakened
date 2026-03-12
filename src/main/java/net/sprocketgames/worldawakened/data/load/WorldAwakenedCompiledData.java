package net.sprocketgames.worldawakened.data.load;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.worldawakened.data.definition.AscensionOfferDefinition;
import net.sprocketgames.worldawakened.data.definition.AscensionRewardDefinition;
import net.sprocketgames.worldawakened.data.definition.EntityBossFlagMapDefinition;
import net.sprocketgames.worldawakened.data.definition.IntegrationProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.InvasionProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.LootProfileDefinition;
import net.sprocketgames.worldawakened.data.definition.MobMutatorDefinition;
import net.sprocketgames.worldawakened.data.definition.MutationPoolDefinition;
import net.sprocketgames.worldawakened.data.definition.RuleDefinition;
import net.sprocketgames.worldawakened.data.definition.StageDefinition;
import net.sprocketgames.worldawakened.data.definition.TriggerRuleDefinition;
import net.sprocketgames.worldawakened.spawning.selector.WorldAwakenedDataDrivenBossClassifier;

public record WorldAwakenedCompiledData(
        Map<ResourceLocation, StageDefinition> stages,
        Map<ResourceLocation, TriggerRuleDefinition> triggerRules,
        Map<ResourceLocation, RuleDefinition> rules,
        Map<ResourceLocation, AscensionRewardDefinition> ascensionRewards,
        Map<ResourceLocation, AscensionOfferDefinition> ascensionOffers,
        Map<ResourceLocation, MobMutatorDefinition> mobMutators,
        Map<ResourceLocation, MutationPoolDefinition> mutationPools,
        Map<ResourceLocation, LootProfileDefinition> lootProfiles,
        Map<ResourceLocation, InvasionProfileDefinition> invasionProfiles,
        Map<ResourceLocation, IntegrationProfileDefinition> integrationProfiles,
        Map<ResourceLocation, EntityBossFlagMapDefinition> entityBossFlags,
        WorldAwakenedDataDrivenBossClassifier bossClassifier) {
    public static WorldAwakenedCompiledData empty() {
        return new WorldAwakenedCompiledData(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                WorldAwakenedDataDrivenBossClassifier.fromMaps(java.util.List.of()));
    }
}


# WorldAwakened Automated Test Suite Audit

Quality audit of the current automated suite. This is a maintenance record, not a replacement for the runtime contracts in the specification.

- Document status: Active audit record
- Last updated: 2026-10-19
- Scope: Current JUnit/Mockito coverage, isolation expectations, and manual-test boundaries

---

## Audit result

The baseline suite contained 76 test classes and 278 test cases. After this audit it contains 77 classes and 280 test cases. It is substantially contract-focused: codecs and validation, progression-state behavior, spawn-time mutation decisions, resolved-state persistence, loot eligibility, and server-authoritative editor services have meaningful coverage.

No test class was removed. The retired Difficulty Track tests were already removed during the schema cleanup; no remaining class exists solely to protect that retired schema. Legacy-looking names remain where they protect a live persistence or parsing boundary.

This audit adds two regression tests:

- nested preview-setting scopes restore their outer setting rather than leaking test-local configuration;
- Per Player mutation spawning fails closed before pool selection when no eligible nearby player can be attributed.

## Classification inventory

All current classes are **Retain** unless marked **Improve**. “Improve” means meaningful coverage with a known next expansion; it is not a removal recommendation.

| Area | Classes | Classification | Protected behavior / note |
|---|---|---:|---|
| Ascension | `AscensionComponentRegistryTest`, `AscensionComponentValidationTest`, `AscensionOfferRuntimeTest`, `AscensionServiceTest`, `AscensionStateEditorTest` | Retain | Component registration and validation; personal pending/claimed offers; generated choices and frozen resolved components; selection service; editor state. |
| Ascension | `AscensionComponentKeysTest` | Improve | Suppression-key parsing for persisted player data; rename legacy wording only if that persisted shape changes. |
| Carrier | `OwnedCarrierStateTest` | Retain | Safe WorldAwakened-owned carrier state. |
| Carrier | `OwnedClientVisualCarrierServiceTest` | Improve | Client visual state transfer; live renderer remains manual. |
| Commands | `CommandsAscensionSuppressionTest`, `CommandsMobInspectCommandTest`, `CommandsMutatorDebugFormattingTest` | Retain | Suppression, inspection, and human-readable diagnostic output. |
| Commands | `CommandsDebugTreeStructureTest` | Improve | Registration tree; expand execution coverage only with an inexpensive Brigadier harness. |
| Compatibility | `CompatibilityServiceTest` | Retain | Optional-integration fail-closed/additive decisions. |
| Config/editor | `ConfigServiceTest`, `ConfigSavedDataTest`, `EditorSettingsTest`, `EditorListLayoutTest`, `MutatorWeightListTest`, `ProfileOverrideServiceTest` | Retain | Authorization, revision/validation, settings persistence, layout, stable weights, direct phase configuration, specialised precedence, apply/reload. |
| Codecs | `AscensionOfferDefinitionCodecTest`, `AscensionRewardDefinitionCodecTest`, `InvasionProfileDefinitionCodecTest`, `LootProfileDefinitionCodecTest`, `MobMutatorDefinitionCodecTest`, `StageDefinitionCodecTest` | Retain | Live definition-schema contracts. |
| Datapack | `ExampleDatapackShapeTest`, `DatapackLoaderBuiltInProfileTest`, `DatapackLoaderIntegrationProfileValidationTest`, `DatapackLoaderPerformanceBudgetTest`, `DatapackServiceTest`, `MutationPoolValidationTest` | Retain | Bundled shape, reload snapshots, validation isolation, performance budgets, and pool contracts. |
| Debug | `ComponentDebugFormatterTest`, `ValidationSummaryTest` | Retain | Inspectable component and validation presentation. |
| Generation/rarity | `EffectGenerationServiceTest`, `RarityResolverTest` | Retain | Deterministic generation, power versus selection weight, repeat behavior, thresholds and custom tiers. |
| Invasion | `InvasionFrequencyOverrideTest` | Improve | Frequency scalar precedence; lifecycle/restart coverage remains missing. |
| Loot | `LootServiceTest` | Retain | Additive/replace behavior, exact/min rarity, explicit profile type, active-phase eligibility. |
| Mutation components | `MutationComponentRegistryTest`, `MutationComponentValidationTest` | Retain | Runtime component catalog, schema, conflicts and budgets. |
| Mutation runtime | `MutationCombatStateTest`, `MutationProbabilityTest`, `MutationProvenanceTest`, `MutatorCombatExecutionTest`, `MutatorCombatPackRegressionTest`, `MutatorDebugSpawnTest`, `MutatorEquipmentComponentTest`, `MutatorMutationChanceTest`, `MutatorPhase5PackRegressionTest`, `MutatorServiceSelectorIndexTest`, `MutatorSpawnOriginGateTest`, `MutatorVisualComponentTest`, `MutatorPerPlayerSpawnTest` | Retain | Frozen combat values, probability, provenance, combat/equipment/visual behavior, debug spawning, selector indexes, spawner gates, explicit Per Server fixtures, and Per Player unattributed safety. |
| Network/client | `ClientConfigPayloadHandlerTest`, `ClientStatusMessageTest`, `EditorPoolJsonTest`, `EditorStageTriggerTest`, `LootProfileEditorConversionTest`, `PhaseMutationIndexTest`, `PhaseOrderingTest`, `PoolBrowserGroupingTest`, `StandardProgressionPresentationTest`, `NetworkTest` | Retain | Payloads and authoring serialization, local conversion/apply/discard, ordering and presentation. `NetworkTest` is Improve for real transport coverage. |
| Progression triggers | `TriggerMatcherTest`, `UnlockConditionProgressTest` | Retain | Trigger matching, partial progress, AND/OR, cooldown, and consumption. |
| Progression state | `PlayerProgressionSavedDataTest`, `ProgressionHistoryTest`, `ProgressionOwnerTest`, `ProgressionStateEditorTest`, `StageRegistryTest`, `StageStateEngineTest`, `TeamProgressionSavedDataTest`, `WorldProgressionSavedDataTest` | Retain | Per Server/Player/team ownership, active versus history, editor data, registry resolution, cumulative/exclusive/replacement behavior, and persistence. |
| Rules | `CanonicalPipelineTest`, `ConflictResolutionTest`, `RecursionGuardTest`, `RuleEngineTest` | Retain | Single-pass propagation, deterministic priority, recursion guard, and scope-indexed evaluation. |
| Spawn selectors | `CompiledEntitySelectorTest` | Retain | Entity ID/tag compilation and matching. |

## Coverage matrix

| Implemented subsystem | Meaningful behavioral coverage | Important remaining gap |
|---|---|---|
| Progression | Ownership modes, active/history distinction, triggers, persistence, sequence semantics | Real `ServerLevel` restart and broader branching/group end-to-end flows. |
| Mutation selection | Selector index, spawn origins, chance, pool precedence, specialised configs, Per Server and unattributed Per Player boundary | Live attributed-player selection using real server entity placement. |
| Components/combat | Composition validation, equipment, visual state, on-hit/life-steal/explosion execution and frozen state | Live multiplayer damage and client visual smoke tests. |
| Rarity/generation | Custom tiers, boundaries, selection/power independence, deterministic generation, provenance | Full spawn-to-loot runtime fixture. |
| Ascension | Personal pending/claimed state, generation, frozen choices, reconciliation | Two online team members plus offline login in one integration fixture. |
| Loot | Profile type, active phase, rarity filters, additive safety, conversion/editor draft semantics | Vanilla loot-table hook and chest-container integration. |
| Pressure events | Codec and frequency override | Scheduler activation/expiry, warning/cooldown/restart recovery, overlap restrictions. |
| Native editor | Revision, validation, draft/apply/discard, profile conversion | Widget interaction and dedicated-server packet round trip. |
| Networking/client | Codec/payload handler contracts | Real transport and rendering lifecycle. |

## Isolation rules

- Spawn tests that cover Per Server explicitly use a scoped Per Server settings view.
- Per Player tests must state their attribution context. Unattributed spawns are an explicit fail-closed regression case.
- Temporary preview settings are nest-safe and restored after use.
- Deterministic definitions and roll inputs are preferred to statistical success thresholds.

## Effectiveness checks

The existing assertions would fail if active-phase loot read unlocked history, rarity thresholds were ignored, specialised and default mob configurations both applied, or persisted resolved components were recomputed from updated definitions. The new Per Player regression fails if attribution is removed or moved after pool selection. The nested-preview test fails if an inner scope clears an outer override.

No production fault injection was committed. Tests target observable contract outcomes rather than helper call counts.

## Manual smoke-test requirements

1. Verify nearby players receive only their Per Player mutation context and unattributed spawns remain unchanged.
2. Verify team progression grants separate personal Ascension offers to online and reconnecting members without duplication.
3. Verify pressure-event warnings, activation, expiry, cooldown, and restart recovery under normal world time.
4. Verify real mob-death and chest loot integration, including optional-mod compatibility.
5. Verify mutation combat visuals, damage attribution, death explosions, and natural despawning with a real client.
6. Verify editor Apply/Discard/revision rejection over a dedicated-server connection with operator and non-operator accounts.

## Maintenance rule

New tests should state the progression mode and context they exercise. Prefer extending a focused class over adding a duplicate fixture. Do not reintroduce tests for the retired Difficulty Track development schema; retain or rewrite tests whenever they protect a supported backend capability.

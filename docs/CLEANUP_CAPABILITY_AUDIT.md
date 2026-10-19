# Greenfield Cleanup Capability Audit

- Document status: Active verification record
- Last updated: 2026-10-19
- Scope: Removal of the retired Difficulty Track schema and related tests

## Result

The cleanup removes a schema and normal-editor terminology, not the underlying capability.
`WorldAwakenedPhaseMutationConfigurationDefinition` replaces the removed Difficulty Track wrapper. It retains
ordered phase-to-pool assignments, phase and dimension defaults, specialised-mob precedence, enabled-state
handling, validation, persistence, and publication into ordinary compiled mutation pools.

## Removed Diff Items

| Removed item | Classification | Replacement / retained behaviour |
|---|---|---|
| `WorldAwakenedDifficultyTrackDefinition` | Obsolete implementation/schema name | `WorldAwakenedPhaseMutationConfigurationDefinition`, persisted under `phase_mutation_configurations_json` and transmitted as `phase_mutation_configurations`. |
| Difficulty Track codec test | Deliberately retired schema test | Direct configuration persistence and publish behaviour is covered by `WorldAwakenedProfileOverrideServiceTest`. |
| Track JSON client test | Deliberately retired payload test | The client now reads and writes the direct configuration payload key; no `difficulty_tracks` payload is accepted. |
| Tier draft client test | Deliberately retired editor naming/API test | The current phase/mob-configuration draft path remains and submits the direct configuration payload. |
| Track compilation tests removed during the initial cleanup | Functionality requiring replacement | Restored as direct-configuration regression coverage for ordered gates, specialised/default mob precedence, persistence, validation, and runtime overlay publication. |

## Capability Checks

- Ordered non-phase assignments still compile to `stage_unlocked` plus later `stage_locked` gates.
- A phase/dimension's specialised enabled mob configuration still wins over Default Mobs without a second spawn roll.
- Disabled specialised configurations do not participate in specialised-mob subtraction.
- Configuration references to a missing phase or pool are rejected server-side.
- Submitted configurations are revisioned, stored in `SavedData`, restored from it, and compiled by `overlay()` before live mutation evaluation.
- External datapack pools outside a submitted configuration remain ordinary independent pools; the direct configuration metadata does not narrow the underlying datapack model.

## Intentional Removals

Only the `difficulty_tracks` wire/save schema and tier-named editor/test API are intentionally removed. No supported
progression mode, mutation-pool composition rule, advanced condition, datapack pool, specialised-mob precedence
rule, or hot-path compilation behaviour is intentionally removed by this cleanup.

## Verification

The targeted profile-override regression suite passes. The full suite currently has pre-existing broad failures in
mutator visual/equipment/combat and loot tests; these are not caused by the direct configuration tests and must be
resolved before declaring the overall branch green.

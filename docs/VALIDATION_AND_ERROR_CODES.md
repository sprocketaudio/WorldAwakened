# World Awakened Validation and Error Codes

Canonical reference for structured diagnostic code families, severity semantics, and publication rules.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Validation and diagnostics contracts across runtime, commands, and tooling

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever new validation branches, diagnostic families, or code-name semantics are introduced.
- Keep diagnostic taxonomy and domain mapping aligned with condition/action/component/scope references.

---

## 1. Purpose

This file is the canonical reference for World Awakened structured diagnostic codes used by validators, loaders, and runtime safety guards.

## 2. Overview

Structured diagnostics exist so pack authors, contributors, and tooling can reason about failures using stable machine-readable identifiers instead of brittle log text.

Core rules:
- Stable codes are the primary contract; free-form text is explanatory only.
- The same code may appear in multiple surfaces (server logs, `/wa reload validate` summaries, debug output, future web editor validation UI).
- Human-readable messages may change for clarity; code names should remain stable once published.

## 3. Naming Convention

Canonical format:

`WA_<DOMAIN>_<DETAIL>`

Naming semantics:
- `WA`: World Awakened prefix.
- `DOMAIN`: owning subsystem/domain (for example `SCHEMA`, `STAGE`, `CONDITION`, `ACTION`, `COMPONENT`, `INTEGRATION`).
- `DETAIL`: precise failure condition.

Examples:
- `WA_STAGE_REF_MISSING`
- `WA_COMPONENT_TYPE_UNKNOWN`
- `WA_CONDITION_SCOPE_INVALID`
- `WA_ACTION_TYPE_UNKNOWN`

## 4. Severity Taxonomy

| Severity | Meaning | Blocking expectation |
| --- | --- | --- |
| `info` | Trace/notice only; no correctness violation. | Non-blocking. |
| `warning` | Notable issue, degraded behavior, or policy warning. | Usually non-blocking; may disable only an unsafe branch/policy path. |
| `error` | Validation/runtime guard failure for an object or operation. | Blocks owning object or operation branch. |
| `fatal` | Framework-integrity/startup-level failure only. | Can stop startup/subsystem initialization. |

Notes:
- Current runtime `WorldAwakenedDiagnosticSeverity` exposes `INFO`, `WARNING`, and `ERROR`.
- `fatal` is a policy-level reserved severity for integrity failures and should be used sparingly.

## 5. Diagnostic Lifecycle Categories

Diagnostics may be emitted during:
- Datapack load and codec validation.
- Cross-object reference validation.
- Config validation.
- Runtime guarded execution (recursion/re-entry/bounds protections).
- Migration/compatibility reconciliation.
- Debug inspection and validation summaries.

## 6. Domain Groups

### A. Schema / Object Shape

Primary families:
- `WA_SCHEMA_*`
- `WA_CODEC_*`

Common cases:
- Unsupported schema version.
- Missing required field.
- Wrong field type.
- Malformed typed-node wrapper.
- Invalid enum value.

### B. Stage / Progression

Primary families:
- `WA_STAGE_*`
- `WA_PROGRESSION_*`
- `WA_CHALLENGE_*`
- `WA_DIFFICULTY_*`

Common cases:
- Missing stage reference.
- Invalid stage alias/group mapping.
- Invalid progression mode/scope/bounds.

### C. Condition System

Primary families:
- `WA_CONDITION_*`

Compatibility family in current runtime:
- `WA_INVALID_CONDITION_TYPE` (legacy consolidated code).

Common cases:
- Unknown condition type.
- Invalid condition parameters.
- Invalid logical wrapper shape.
- Scope misuse.
- Missing required runtime context.

### D. Action System

Primary families:
- `WA_ACTION_*`

Compatibility family in current runtime:
- `WA_INVALID_ACTION_TYPE` (legacy consolidated code).

Common cases:
- Unknown action type.
- Invalid parameters.
- Invalid scope usage.
- Unsafe non-idempotent repetition.
- Recursion/re-entry safety block.

### E. Component System

Primary families:
- `WA_COMPONENT_*`
- `WA_MUTATION_COMPONENT_*`
- `WA_ASCENSION_COMPONENT_*`
- `WA_COMPONENT_BUDGET_*`

Common cases:
- Unknown component type.
- Invalid parameters.
- Empty component array.
- Conflict/duplicate/companion violations.
- Budget exceeded.

### F. Reference Resolution

Primary families:
- `WA_REF_*`

Compatibility family in current runtime:
- `WA_INVALID_REFERENCE` (generic reference failure code).

Common cases:
- Missing referenced mutator/reward/pool/profile target.
- Unresolved component or stage references.

### G. Scope Misuse

Primary families:
- `WA_SCOPE_*`
- `WA_CONDITION_SCOPE_*`
- `WA_ACTION_SCOPE_*`

Common cases:
- Player-only condition used in world scope.
- Loot action used in spawn-only surface.
- Entity-only checks without entity context.

### H. Integration / Compat

Primary families:
- `WA_INTEGRATION_*`
- `WA_APOTHEOSIS_*`

Common cases:
- Integration inactive while integration-specific data is authored.
- Unsafe Apotheosis-sensitive loot behavior blocked/downgraded.

### I. Migration / Compatibility / Deprecation

Primary families:
- `WA_MIGRATION_*`
- `WA_DEPRECATED_*`
- `WA_STATUS_*`

Common cases:
- Deprecated field usage.
- Removed objects preserved inactive.
- Persisted values clamped to safe bounds.
- Missing chosen reward definition during reconciliation.

### J. Runtime Protection / Recursion / Bounded Execution

Primary families:
- `WA_RUNTIME_*`
- `WA_RULE_RECURSION_*`

Common cases:
- Recursion blocked.
- Queue depth exceeded.
- Re-entry blocked.
- Spawn path safely aborted for World Awakened branch only.

### K. Performance Budgets and Hot-Path Guardrails

Primary families:
- `WA_PERF_*`

Common cases:
- scope rule bucket exceeds recommended budget
- event pass evaluates more rules than configured budget
- single rule action count exceeds configured safe bound
- spawn mutator/component counts exceed configured safe bounds
- action-chain depth violates single-pass guardrails

## 7. Per-Code Entry Template

Each published code entry should define:
- `code`
- `domain`
- `severity`
- `lifecycle` (`validation`, `cross-object`, `config`, `runtime`, `migration`, `debug`)
- `meaning`
- `typical causes`
- `fallback behavior`
- `fix guidance`
- `notes/examples` (optional)

## 8. Minimum Canonical Published Codes

Publication labels used below:
- `implemented`: currently emitted by runtime validators/loaders.
- `published`: documented canonical code (spec/reference) and expected across relevant surfaces.
- `canonical-target`: preferred stable code for new emitters; legacy generic code may still appear in some current paths.

### 7A. Schema, Load, and Object Shape

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_CODEC_PARSE_FAILED` | implemented | schema/load | error | validation | yes | JSON/codec parse failure. | Disable owning object; continue reload. | Correct object shape/types to match codec. | Emitted in datapack loader parse path. |
| `WA_SCHEMA_UNSUPPORTED` | implemented | schema | error | validation | yes | `schema_version` newer than supported handler. | Disable offending object only. | Use supported schema version or update handler. | Canonical schema-version failure code. |
| `WA_MISSING_REQUIRED_FIELD` | implemented | schema | error | validation | yes | Required field absent. | Disable owning object. | Add required field with valid type. | Use object-level context in message. |
| `WA_DUPLICATE_ID` | implemented | schema/object identity | error | cross-object | yes | Duplicate object ID within same object type set. | Keep deterministic winner per load order; mark duplicate invalid. | Remove duplicate IDs across packs. | Loader currently logs `replaced_existing` resolution path. |
| `WA_RELOAD_EXCEPTION` | implemented | load/runtime bridge | error | validation | yes | Unexpected exception during load/validation. | Disable offending object; continue with isolation. | Fix malformed data or validator edge case. | Should include source path and exception summary. |
| `WA_CONDITION_SHAPE_INVALID` | published | condition | error | validation | yes | Malformed condition wrapper (`type`, `parameters`, optional fields). | Disable owning object branch. | Fix wrapper/node structure. | Prefer over generic legacy code for new validators. |
| `WA_ACTION_SHAPE_INVALID` | published | action | error | validation | yes | Malformed action wrapper (`type`, `parameters`, optional fields). | Disable owning object branch. | Fix wrapper/node structure. | Prefer over generic legacy code for new validators. |
| `WA_STATUS_MODE_VIOLATION` | published | status/mode policy | warning or error | validation | mode-dependent | `planned/reserved/deprecated` usage violates active validation policy. | Warn or block based on mode policy. | Align authored content with active mode and status taxonomy. | Severity can shift by strict/permissive mode. |

### 7B. Stage, Progression, and Config Bounds

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_STAGE_REF_MISSING` | implemented | stage/progression | error | cross-object | yes | Object references unknown stage ID. | Disable dependent object only. | Add missing stage or fix reference ID. | Canonical missing-stage reference code. |
| `WA_ASCENSION_REWARD_INVALID` | implemented | ascension | error | validation | yes | Invalid reward definition fields, icon, or constraints. | Disable reward object (and dependents if unresolved). | Fix reward schema/content. | Used by loader validation paths. |
| `WA_PRESSURE_TIER_PROVIDER_INVALID` | published | pressure tier compat | error | config/validation | branch-level | Invalid/unsupported tier provider configuration. | Disable provider branch; preserve unrelated systems. | Correct provider ID/config and compat toggles. | Listed as canonical in spec naming guidance. |
| `WA_DIFFICULTY_GLOBAL_INVALID` | published | difficulty config | error | config | subsystem-level | Invalid global modifier defaults/bounds. | Disable or clamp affected difficulty branch per policy. | Fix bounds/defaults in config/datapack policy. | Recommended in spec Section 5B. |
| `WA_CHALLENGE_SCOPE_INVALID` | published | challenge config | error | config | subsystem-level | Invalid challenge scope mode or unsupported combination. | Disable challenge branch where possible. | Use supported scope mode mapping. | Required canonical challenge code. |
| `WA_CHALLENGE_BOUNDS_INVALID` | published | challenge config | error | config | subsystem-level | Min/max/default out of bounds or inconsistent. | Disable or clamp challenge branch by policy. | Correct bounds and defaults. | Required canonical challenge code. |
| `WA_CHALLENGE_STEP_INVALID` | published | challenge config | error | config | subsystem-level | Invalid step/cooldown configuration. | Disable affected challenge controls. | Fix step/cooldown values. | Recommended in spec Section 5B. |
| `WA_CHALLENGE_VOTE_CONFIG_INVALID` | published | challenge config | error | config | subsystem-level | Vote required but vote settings invalid/missing. | Disable vote path or challenge branch by policy. | Provide valid vote thresholds/cooldowns. | Recommended in spec Section 5B. |
| `WA_CHALLENGE_MODE_UNSUPPORTED` | published | challenge config | error | config | subsystem-level | Unsupported challenge mode selected. | Disable unsupported mode branch. | Choose a supported mode. | Recommended in spec Section 5B. |

### 7C. Condition, Action, and Scope Legality

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_CONDITION_SCOPE_INVALID` | canonical-target | condition/scope | error | validation | yes | Condition used outside allowed scopes. | Disable owning object branch. | Move condition to legal scope or change scope. | Prefer this over generic scope/type codes for new emitters. |
| `WA_ACTION_SCOPE_INVALID` | canonical-target | action/scope | error | validation | yes | Action used outside allowed scopes. | Disable owning object branch. | Move action to legal scope or change scope. | Prefer this over generic scope/type codes for new emitters. |
| `WA_SCOPE_VIOLATION` | published | generic scope | error | validation/runtime | branch-level | Generic scope contract violation where finer code unavailable. | Fail closed for branch/object. | Fix scope declaration and usage alignment. | Keep only as fallback; prefer specific scope codes. |
| `WA_ACTION_TYPE_UNKNOWN` | canonical-target | action | error | validation | yes | Action `type` is unknown/unregistered. | Disable owning object branch. | Use supported action ID or register extension. | Required canonical action unknown-type code. |
| `WA_ACTION_PARAMETERS_INVALID` | canonical-target | action | error | validation | yes | Missing/wrong/contradictory action parameters. | Disable owning object branch. | Fix action `parameters` schema/value bounds. | Required canonical action-parameters code. |
| `WA_INVALID_CONDITION_TYPE` | implemented (legacy) | condition | error | validation | yes | Consolidated condition-node failure: shape/type/scope/parameter issues. | Disable owning object. | Resolve message-specific node issue. | Legacy broad code; migrate toward granular `WA_CONDITION_*` codes. |
| `WA_INVALID_ACTION_TYPE` | implemented (legacy) | action | error | validation | yes | Consolidated action-node failure: shape/type/scope/parameter issues. | Disable owning object. | Resolve message-specific node issue. | Legacy broad code; migrate toward granular `WA_ACTION_*` codes. |
| `WA_CONFIG_GATE_INVALID` | implemented | config gate | error | validation | yes | Invalid `config_gate` token/shape. | Disable owning object branch. | Use valid gate key format and existing gate. | Emitted by loader gate validation. |
| `WA_SELECTOR_INVALID` | implemented | selector | error | validation | yes | Invalid selector/tag syntax for hot-path filters. | Disable owning object. | Correct selector syntax and target type. | Emitted during reload precompilation checks. |

### 7D. Component Composition and Budgeting

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_COMPONENT_ARRAY_EMPTY` | implemented | component | error | validation | yes | Empty `components[]`. | Disable owning mutator/reward object. | Provide at least one valid component. | Required canonical component code. |
| `WA_COMPONENT_TYPE_UNKNOWN` | implemented | component | error | validation | yes | Unknown/unregistered component `type`. | Disable owning object. | Use supported component type or install provider mod. | Required canonical component code. |
| `WA_COMPONENT_PARAMETERS_INVALID` | implemented | component | error | validation | yes | Invalid/missing component parameters. | Disable owning object. | Correct parameter shape/ranges. | Required canonical component code. |
| `WA_COMPONENT_COMPOSITION_INVALID` | implemented | component composition | error | validation | yes | Incompatible composition, conflict, impossible combination. | Disable owning object. | Remove conflicting components or satisfy dependencies. | Required canonical component code. |
| `WA_COMPONENT_BUDGET_EXCEEDED` | implemented | component budget | error | validation | yes | Component budget/cost exceeded. | Disable owning object. | Reduce cost or raise configured budget if intended. | Required canonical component budget code. |
| `WA_COMPONENT_DUPLICATE_UNSUPPORTED` | implemented | component duplicate policy | error | validation | yes | Duplicate component type not allowed by policy. | Disable owning object. | Remove duplicate or change duplicate policy support. | Shared duplicate-policy failure code. |
| `WA_COMPONENT_NO_RUNTIME_RESULT` | implemented | component runtime applicability | error | validation | yes | Component composition validates structurally but cannot produce runtime effect. | Disable owning object. | Adjust composition to produce executable runtime result. | Used by both ascension and mutation validations. |
| `WA_MUTATION_COMPONENT_INVALID` | published | mutation component | error | validation | yes | Mutation component invalid (type/shape-level). | Disable owning mutator object. | Correct mutation component definition. | Component reference recommended family. |
| `WA_MUTATION_COMPONENT_PARAM_INVALID` | published | mutation component | error | validation | yes | Mutation component parameter invalid. | Disable owning mutator object. | Fix parameter values/schema. | Component reference recommended family. |
| `WA_MUTATION_COMPONENT_CONFLICT` | published | mutation component composition | error | validation | yes | Mutation component conflict/companion violation. | Disable owning mutator object. | Resolve conflict metadata or composition order. | Component reference recommended family. |
| `WA_MUTATION_COMPOSITION_EMPTY` | published | mutation component composition | error | validation | yes | Mutation component composition empty. | Disable owning mutator object. | Add at least one valid component entry. | Component reference recommended family. |
| `WA_ASCENSION_COMPONENT_INVALID` | published | ascension component | error | validation | yes | Ascension component invalid (type/shape-level). | Disable owning reward object. | Correct ascension component definition. | Component reference recommended family. |
| `WA_ASCENSION_COMPONENT_PARAM_INVALID` | published | ascension component | error | validation | yes | Ascension component parameter invalid. | Disable owning reward object. | Fix parameter values/schema. | Component reference recommended family. |
| `WA_ASCENSION_COMPONENT_CONFLICT` | published | ascension component composition | error | validation | yes | Ascension component conflict/companion violation. | Disable owning reward object. | Resolve conflict metadata or composition order. | Component reference recommended family. |
| `WA_ASCENSION_COMPOSITION_EMPTY` | published | ascension component composition | error | validation | yes | Ascension `components[]` empty. | Disable owning reward object. | Add at least one valid component entry. | Component reference recommended family. |

### 7E. References, Pools, Integration, and Compat Safety

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_INVALID_REFERENCE` | implemented | reference resolution | warning or error | cross-object | context-dependent | Missing referenced mutator/reward/profile/stage or ID/path mismatch. | Disable dependent object on hard missing refs; keep warning-only mismatches non-blocking. | Fix target IDs and ensure referenced object exists/enabled. | Current generic reference code in loader. |
| `WA_POOL_SELECTION_IMPOSSIBLE` | implemented | mutation pool | error | validation/runtime guard | yes | Pool has no selectable/valid entries after filtering. | Disable invalid pool/object branch. | Add valid entries/weights and fix filters. | Emitted in pool validation paths. |
| `WA_INVASION_COMPOSITION_INVALID` | implemented | invasion profile | error | validation | yes | Invalid `spawn_composition` entries or zero valid entries. | Disable offending invasion profile. | Provide valid selectors and composition entries. | Emitted during invasion profile checks. |
| `WA_INTEGRATION_INACTIVE` | implemented | integration | error | validation | branch-level | Integration-specific data authored while integration disabled/missing. | Skip/disable integration-specific branch only. | Enable integration or remove integration-specific nodes. | Must not become hard dependency failure. |
| `WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED` | published | Apotheosis compat | warning or error | validation/runtime guard | branch-level | Unsafe destructive operation (`replace_entries`, `remove_entries`) blocked on sensitive target. | Block operation branch; preserve Apotheosis behavior. | Use additive/safe mode or retarget profile. | Required canonical Apotheosis safety code. |
| `WA_APOTHEOSIS_LOOT_MODE_UNSAFE` | published | Apotheosis compat | warning or error | validation/runtime guard | branch-level | Selected loot mode conflicts with safety policy for sensitive targets. | Downgrade to safe additive mode or disable branch per policy. | Choose policy-compliant mode. | Required canonical Apotheosis safety code. |
| `WA_APOTHEOSIS_LOOT_TARGET_SENSITIVE` | published | Apotheosis compat | info or warning | validation/debug | no (by itself) | Target identified as Apotheosis-sensitive context. | Inform policy evaluation; may combine with blocking codes. | Confirm target metadata and intended behavior. | Required canonical Apotheosis sensitivity code. |

### 7F. Runtime Safety Guards and Migration/Deprecation

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_RULE_RECURSION_BLOCKED` | published | runtime recursion guard | warning | runtime | branch-level | Recursive rule execution or max recursion depth hit. | Abort recursive branch only; continue remaining pass. | Remove cyclic action chains and unsafe self-trigger loops. | Listed in spec canonical examples. |
| `WA_DEPRECATED_FIELD_USED` | canonical-target | migration/deprecation | warning | migration/validation | no | Deprecated field appears in authored content. | Continue with compatibility behavior where supported. | Migrate to replacement field/schema. | Use with migration target note when known. |
| `WA_MIGRATION_STAGE_PRESERVED_INACTIVE` | canonical-target | migration | info or warning | migration | no | Removed stage ID remains in persisted data. | Preserve saved ID inactive; do not auto-map unless alias exists. | Add `aliases` or migration guidance in pack updates. | Mirrors save-compat expectations in authoring docs/spec. |
| `WA_MIGRATION_VALUE_CLAMPED` | canonical-target | migration/config safety | warning | migration/runtime guard | branch-level | Persisted/config value out of bounds and clamped to safe range. | Clamp value and continue safely. | Correct persisted/config bounds to eliminate clamp. | Prefer explicit clamp details in message. |
| `WA_MIGRATION_REWARD_MISSING` | canonical-target | migration/ascension | warning | migration/runtime | branch-level | Saved chosen ascension reward definition removed. | Keep saved ID, stop applying missing effect, no auto-substitution. | Restore reward definition or accept inert historical record. | Mirrors ascension reconciliation rules in spec/docs. |

### 7G. Performance Budgets and Hot-Path Guardrails

| Code | Publication | Domain | Severity | Lifecycle | Blocking? | Typical causes | Fallback behavior | Fix guidance | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `WA_PERF_RULE_BUCKET_OVERSIZE` | published | performance/rule bucket | warning | validation | no (by default) | Scope bucket exceeds `maximum_rules_per_bucket` recommendation/policy. | Emit warning; optionally disable or split offending scope branch by policy. | Split rules across scopes, reduce broad conditions, or tighten policy thresholds. | Performance budget warning family from `PERFORMANCE_BUDGETS.md`. |
| `WA_PERF_RULE_EVENT_LIMIT_EXCEEDED` | published | performance/rule evaluation | warning | runtime/debug | branch-level | Event pass attempted to evaluate over `maximum_rules_evaluated_per_event`. | Truncate or short-circuit per policy; emit trace summary. | Reduce candidate fan-out and improve indexing/selectors. | Must include scope bucket and event type in payload. |
| `WA_PERF_RULE_ACTION_COUNT_EXCEEDED` | published | performance/action budget | warning or error | validation | object-level | Rule defines more than `maximum_actions_per_rule`. | Warn by default; optionally disable offending rule by policy. | Split rule into smaller deterministic rules. | Severity may be strict-mode elevated. |
| `WA_PERF_MUTATOR_COUNT_EXCEEDED` | published | performance/spawn mutators | warning | validation/runtime | branch-level | Spawn branch exceeds `max_mutators_per_spawn`. | Deterministic truncation or candidate rejection per policy. | Reduce pool fan-out or lower mutator density. | Must remain deterministic for identical inputs. |
| `WA_PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED` | published | performance/spawn components | warning | validation | object-level | Mutator defines over `max_components_per_mutator`. | Warn by default; optionally disable offending mutator by policy. | Reduce component count or split mutator archetypes. | Complements composition budget codes. |
| `WA_PERF_ACTION_CHAIN_DEPTH_EXCEEDED` | published | performance/action chain | warning or error | runtime guard | branch-level | Action chain depth exceeds single-pass `max_action_chain_depth`. | Abort recursive/re-entry branch; continue stable pass. | Remove same-pass re-entry and cyclic action design. | Should correlate with `WA_RULE_RECURSION_BLOCKED` when both apply. |

### 7H. Legacy-to-Canonical Granularity Mapping

Use this mapping when migrating emitters and tooling:

| Legacy code | Canonical granular targets |
| --- | --- |
| `WA_INVALID_CONDITION_TYPE` | `WA_CONDITION_SHAPE_INVALID`, `WA_CONDITION_TYPE_UNKNOWN`, `WA_CONDITION_SCOPE_INVALID`, `WA_CONDITION_PARAMETERS_INVALID` |
| `WA_INVALID_ACTION_TYPE` | `WA_ACTION_SHAPE_INVALID`, `WA_ACTION_TYPE_UNKNOWN`, `WA_ACTION_SCOPE_INVALID`, `WA_ACTION_PARAMETERS_INVALID` |
| `WA_SCOPE_VIOLATION` | `WA_CONDITION_SCOPE_INVALID` or `WA_ACTION_SCOPE_INVALID` when context is known |
| `WA_INVALID_REFERENCE` | `WA_REF_*` family members when a more specific missing-reference category is introduced |

Canonical granular codes referenced above and reserved for future emitters/tooling:
- `WA_CONDITION_TYPE_UNKNOWN`
- `WA_CONDITION_PARAMETERS_INVALID`
- `WA_ACTION_TYPE_UNKNOWN`
- `WA_ACTION_PARAMETERS_INVALID`
- `WA_CONDITION_SCOPE_INVALID`
- `WA_ACTION_SCOPE_INVALID`

## 9. Fallback Behavior Rules

Expected fallback patterns:
- Invalid authored object disables the owning object only.
- Unresolved reference disables only the dependent object/branch.
- Warning diagnostics do not block reload/export unless active validation policy is strict for that case.
- Runtime recursion/re-entry protection aborts only the recursive branch.
- Runtime failures in World Awakened hot paths should fail closed for the affected World Awakened branch and should not crash unrelated systems.
- Integration-specific failures (for example compat disabled) skip/disable only integration-specific behavior.
- `fatal` is reserved for framework-integrity/startup failures only.

## 10. Validation Mode Notes

Validation policy may change effective severity for some codes:
- `planned` and `deprecated` status-related cases may be `warning` in permissive mode and `error` in strict mode.
- The canonical code name must remain unchanged even when policy changes severity.
- Tooling should key behavior on code identity first, then apply policy-specific severity.

## 11. Contributor Update Rules

When introducing or changing published diagnostics:
- Add/update this file in the same change.
- Keep code names stable once published.
- Do not casually rename existing codes; prefer alias/deprecation mapping when migration is required.
- Keep `docs/SPECIFICATION.md`, runtime emitters, tests, and tooling contracts aligned on the same code names.
- When deprecating codes, document canonical replacement targets and expected migration window.

## Appendix A: Quick Lookup (High-Use Codes)

| Code | Severity | Subsystem | Blocking? |
| --- | --- | --- | --- |
| `WA_SCHEMA_UNSUPPORTED` | error | schema/load | yes |
| `WA_CODEC_PARSE_FAILED` | error | schema/load | yes |
| `WA_MISSING_REQUIRED_FIELD` | error | schema/load | yes |
| `WA_STAGE_REF_MISSING` | error | stage/reference | yes |
| `WA_INVALID_REFERENCE` | warning or error | references | depends |
| `WA_COMPONENT_TYPE_UNKNOWN` | error | components | yes |
| `WA_COMPONENT_PARAMETERS_INVALID` | error | components | yes |
| `WA_COMPONENT_COMPOSITION_INVALID` | error | components | yes |
| `WA_COMPONENT_ARRAY_EMPTY` | error | components | yes |
| `WA_COMPONENT_BUDGET_EXCEEDED` | error | components | yes |
| `WA_CONDITION_SCOPE_INVALID` | error | condition/scope | yes |
| `WA_ACTION_SCOPE_INVALID` | error | action/scope | yes |
| `WA_ACTION_TYPE_UNKNOWN` | error | action | yes |
| `WA_ACTION_PARAMETERS_INVALID` | error | action | yes |
| `WA_CHALLENGE_SCOPE_INVALID` | error | challenge config | yes |
| `WA_CHALLENGE_BOUNDS_INVALID` | error | challenge config | yes |
| `WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED` | warning or error | compat/apotheosis | branch-level |
| `WA_APOTHEOSIS_LOOT_MODE_UNSAFE` | warning or error | compat/apotheosis | branch-level |
| `WA_APOTHEOSIS_LOOT_TARGET_SENSITIVE` | info or warning | compat/apotheosis | no |
| `WA_RULE_RECURSION_BLOCKED` | warning | runtime safety | branch-level |
| `WA_PERF_RULE_BUCKET_OVERSIZE` | warning | performance/rule bucket | no (by default) |
| `WA_PERF_RULE_EVENT_LIMIT_EXCEEDED` | warning | performance/rule evaluation | branch-level |
| `WA_PERF_RULE_ACTION_COUNT_EXCEEDED` | warning or error | performance/action budget | object-level |
| `WA_PERF_MUTATOR_COUNT_EXCEEDED` | warning | performance/spawn mutators | branch-level |
| `WA_PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED` | warning | performance/spawn components | object-level |
| `WA_PERF_ACTION_CHAIN_DEPTH_EXCEEDED` | warning or error | performance/action chain | branch-level |

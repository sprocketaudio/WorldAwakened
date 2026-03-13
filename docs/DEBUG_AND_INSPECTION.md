# World Awakened Debug and Inspection Contract

Canonical contract for runtime inspection surfaces, debug command minimums, trace payloads, and provenance visibility.

- Document status: Active shared-contract reference
- Last updated: 2026-03-13
- Scope: Runtime debug, inspect output, and trace observability contracts

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever debug commands, trace payload fields, provenance exposure, or inspection surfaces change.
- Keep this file aligned with runtime command implementation and validation/error-code taxonomy.
- Treat command output layering as part of the inspection contract. If a command's default, operator, or debug presentation changes, update this file in the same change.

---

## 1. Purpose

This document locks minimum runtime observability requirements so subsystem behavior remains inspectable and deterministic.

Hard goals:
- operators can answer why something matched, failed, or executed
- pack makers can map behavior to authored IDs
- debug output can be correlated across subsystems using stable trace IDs

Hard rule:
- no major mechanic may ship without at least one command or debug surface that exposes match/rejection state.

---

## 2. Required Inspection Surfaces by Subsystem

| Subsystem | Required surface | Minimum identity fields | Minimum outcome fields | Status |
| --- | --- | --- | --- | --- |
| Stages/progression | `/wa stage list`, `/wa stage inspect` | stage ID, scope key | unlocked/locked, source, aliases resolution | `implemented`/`planned` |
| Trigger engine | `/wa trigger fire`, trigger inspect output | trigger ID, trigger type, scope | matched/rejected, cooldown, one-shot, rejection reason | `implemented` |
| Rule engine | `/wa dump active_rules` | rule ID, scope, priority | eligible/rejected, cooldown, consumed, reason category | `implemented` |
| Ascension | `/wa ascension inspect` | player UUID, offer ID, reward IDs, source key, active owned carriers | pending/resolved state, forfeits, suppression state, grant/reconcile outcome | `implemented` |
| Mutators | `/wa mob inspect` | entity UUID, mutator ID, pool ID | applied/rejected components, budget/conflict outcomes | `planned` |
| Mutation pools | pool inspect output | pool ID, candidate IDs | candidate eligibility, selection path, reroll count | `planned` |
| Loot profiles | loot debug output | profile ID, loot target | matched/rejected, compat safety outcome, applied operations | `planned` |
| Invasions | invasion inspect output | profile ID, invasion instance ID | scheduler state, wave state, caps/cooldown outcomes | `planned` |
| Compat/integrations | `/wa compat list`, integration inspect | integration ID/mod ID | active/inactive reason, gate path, fallback path | `implemented`/`planned` |

Note:
- command naming may evolve, but equivalent functionality and payload fields are required.

---

## 3. Minimum Debug Command Contract

The command tree must provide at least:
- reload and validation summary
- stage state inspection
- trigger test path
- rule eligibility/rejection inspection
- ascension offer/reward inspection
- mutator provenance inspection (when mutators are active)
- invasion profile/runtime inspection (when invasions are active)
- integration activation inspection

Current baseline command surface:
- `/wa reload validate`
- `/wa stage list`
- `/wa stage list player <player>`
- `/wa stage list global`
- `/wa trigger fire <id>`
- `/wa trigger fire <id> player <player> [dimension <dimension_id>]`
- `/wa trigger fire <id> global [dimension <dimension_id>]`
- `/wa dump active_rules`
- `/wa dump active_rules player <player> [dimension <dimension_id>]`
- `/wa dump active_rules global [dimension <dimension_id>]`
- `/wa ascension inspect <player>`
- `/wa ascension choose <player> <instance_id> <reward_id>`
- `/wa ascension active <player> <reward_id>`
- `/wa ascension suppress reward <player> <reward_id>`
- `/wa ascension unsuppress reward <player> <reward_id>`
- `/wa ascension suppress component <player> <reward_id> <component_key>`
- `/wa ascension unsuppress component <player> <reward_id> <component_key>`
- `/wa ascension reconcile <player>`
- `/wa ascension reopen <player> <instance_id>`
- `/wa ascension clear <player> <instance_id>`
- `/wa debug reset global <stages|triggers|rules|all>`
- `/wa debug reset player <player> <stages|triggers|rules|ascension|all>`
- `/wa debug clear global <stage|trigger|rule> <id>`
- `/wa debug clear player <player> <stage|trigger|rule> <id>`
- `/wa debug clear player <player> ascension_instance <instance_id>`
- `/wa compat list`

Notes:
- `global` means the shared save-wide progression bucket, not a second Minecraft world.
- `dimension <dimension_id>` overrides only the world-context evaluation dimension for the command pass; it does not change the targeted player/global persistence bucket.
- ascension runtime `instance_id` values are generated as short opaque command-safe IDs such as `wao_ab12cd34`; `offer_id` and `source_key` remain the inspect/debug provenance fields
- ascension suppression `component_key` values use canonical `index|namespace:component_type` form (for example `0|worldawakened:movement_speed_bonus`); index-only shorthand (for example `0`) is accepted
- inspect output must surface active owned carriers separately from chosen rewards so operators can see both the stable owned key and the carrier type ID
- client-visual carriers such as `worldawakened:night_vision_passive` belong in inspect/debug output the same way as server-owned carriers; only the execution layer differs, including whether the carrier is feeding a lightmap-backed client render path
- operator-facing ascension command output should expose clickable copy and suggest-command actions for runtime IDs and common next actions where the client supports chat click events
- operator command arguments that target loaded authored objects or pending runtime instances should provide Brigadier suggestions from the current loaded runtime state
- the `/wa debug` tree is registered only when `general.enable_debug_commands = true`

Planned minimum additions as systems complete:
- `/wa debug perf`
- `/wa debug rules`
- `/wa debug mutators`
- `/wa mob inspect`
- `/wa invasion inspect <profile|active>`
- `/wa loot inspect <target>`
- `/wa debug trace <trace_id>`
- `/wa debug mutators evaluate <entity_id> [dimension] [x] [y] [z]`
- `/wa debug mutators force_pool <entity_id> <pool_id> [dimension] [x] [y] [z]`
- `/wa debug mutators force_mutator <entity_id> <mutator_id> [dimension] [x] [y] [z]`
- `/wa debug spawn test <entity_id> [dimension] [x] [y] [z]`
- `/wa debug pressure evaluate [dimension] [x] [y] [z] [player]`
- `/wa debug difficulty scalar [player]`
- `/wa debug loot evaluate <target_type> <target_id> [player] [dimension]`
- `/wa debug loot force_profile <profile_id> <target_type> <target_id> [player] [dimension]`
- `/wa debug invasion evaluate <profile_id> [dimension] [x] [y] [z]`
- `/wa debug invasion force_wave <profile_id> [wave_index] [dimension] [x] [y] [z]`
- `/wa debug compat evaluate <integration_id> [player] [entity] [dimension]`
- `/wa debug scalar provider <provider_key> [player] [entity] [dimension]`
- `/wa difficulty global get|set|reset`
- `/wa difficulty personal get|set`
- `/wa difficulty world get|set`
- `/wa difficulty vote yes|no`

Performance-debug payload minimums:
- scope bucket sizes
- per-event rule evaluation counts
- spawn mutator/component counts
- rule timing summaries

Operator/debug split:
- operator-level commands are gameplay-facing control and recovery paths (`stage unlock|lock`, `trigger fire`, `ascension grant_offer|choose|revoke|reopen|clear`)
- debug-level commands are explicit persistence-bucket tools and must not imply hidden rollback of unrelated state
- debug reset/clear commands must report exactly what bucket or identity was removed
- operator inspection/mutation commands must allow explicit `player` and `global` targeting rather than relying on ambiguous fallback
- manual trigger fire and rule-dump inspection must expose optional dimension override control when world-context conditions are relevant
- all future `/wa` commands must fit one of these presentation layers: player-facing notification, operator command feedback, or inspect/debug output
- player-facing notifications should be short, readable, and display-name-first; they should not expose raw authored/runtime IDs, copy buttons, or provenance fields unless the surface is explicitly inspect/debug oriented
- non-debug operator outputs should stay concise and human-readable by default
- inspect/debug outputs are the dense layer and should carry raw IDs, source keys, reason codes, provenance, and other diagnostics needed for recovery or diagnosis
- concise operator commands may append extra raw detail only when `general.debug_logging = true`
- when `general.debug_logging = true`, keep the concise operator line and add the raw-detail line or suffix after it; do not replace the operator layer with dense-only output
- new command surfaces must not duplicate player-facing action prompts inside normal gameplay notifications when the same action is already present in the intended player-facing UX
- when `general.enable_debug_commands = false`, the debug tree must not be available

---

## 3A. Phase 5+ Evaluate/Force Output Contract

All Phase 5+ debug verification commands must emit a shared minimum payload contract.

Applies to command modes:
- `inspect`
- `evaluate`
- `force`
- `live_test`

Required common output fields:
- trace ID
- command mode (`inspect`, `evaluate`, `force`, `live_test`)
- target context summary
- active stage context
- relevant external scalars
- config gates
- integration gates
- candidate objects
- rejected objects and rejection reasons
- selected/final objects
- whether randomness or scheduling was bypassed
- whether the run was dry-run or live
- final outcome summary

Required subsystem-specific output:

Mutators/spawn:
- selector narrowing summary
- candidate pools
- rejected pools
- candidate mutators
- rejected mutators
- selected components
- composition results
- budget results

Pressure/difficulty:
- base values
- dimension baseline
- global difficulty modifier
- challenge modifier
- integration scalar inputs
- final effective scalar
- policy rejection reasons

Loot:
- candidate profiles
- `replace`/`inject`/`remove`/`add_bonus_pool` decisions
- compat safety restrictions
- fallback action taken
- final assembled loot outcome summary

Invasions:
- scheduler eligibility
- cooldown/cap checks
- selected profile
- wave composition
- mutator application to invasion units
- entity cap and safe-zone outcomes

Compat/integrations:
- mod loaded state
- config enabled state
- profile enabled state
- provider available/unavailable
- compat branch active/inactive reason
- fail-closed reason when applicable

Command-path behavior rules:
- `evaluate` is dry-run by default and does not mutate gameplay state
- `force` may bypass randomness or scheduling uncertainty but must still enforce composition, ownership, compatibility safety, and policy gates
- `live_test` must stay bounded and explicit; it must not become an uncontrolled world-modification path
- when command-path and live-path outcomes diverge, both outputs should include enough candidate/rejection detail to isolate pipeline mismatch regressions

---

## 4. Canonical Evaluation-Trace Payload Shape

Every evaluation pass must produce a structured trace payload (log or command surface).

Canonical top-level shape:

```json
{
  "trace_id": "WA-20260312-7f8a",
  "event_type": "entity_spawn",
  "scope": "spawn_event",
  "subsystem": "rules",
  "phase": "rule_evaluation",
  "object_type": "rules",
  "object_id": "my_pack:night_pressure_stage2",
  "outcome": "rejected",
  "reason_code": "WA_RULE_COOLDOWN_ACTIVE",
  "reason_category": "cooldown_active",
  "snapshot": {
    "stage_ids": ["my_pack:baseline", "my_pack:nether_opened"],
    "dimension": "minecraft:overworld"
  }
}
```

Required fields:
- `trace_id`
- `event_type`
- `scope`
- `subsystem`
- `phase`
- `object_type`
- `object_id`
- `outcome`
- `reason_code` (or explicit `null` when not applicable)
- `reason_category` (or explicit `none`)

Optional but recommended:
- `conditions_evaluated[]`
- `actions_evaluated[]`
- `resolved_components[]`
- `dropped_components[]`
- `state_changes[]`

---

## 5. Match/Fail Explanation Contract

For each evaluated object, debug output must expose:
- whether the object was considered
- whether it matched or failed
- the first terminal rejection reason
- any non-terminal warnings
- final action/result summary

Required rejection categories:
- feature disabled
- config gate failed
- integration inactive
- scope mismatch
- selector mismatch
- condition failed
- cooldown active
- one-shot consumed
- chance failed
- conflict-set rejected
- duplicate policy rejected
- budget exceeded
- no-op result
- invalid reference
- safe-zone/cap restriction
- upstream spawn cancelled
- upstream spawn transformed
- spawn context invalidated
- spawn re-entry blocked by coexistence policy

Reason categories must map to canonical code families in [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md).

## 5A. Ownership-Safe Reconcile and Degraded-State Reasons

Player / reward reconciliation surfaces should be able to explain these outcomes where relevant:
- foreign modifier preserved
- WA-owned modifier refreshed
- WA-owned modifier missing and reapplied
- third-party modifier untouched
- WA-owned carrier refreshed
- WA-owned carrier missing and rebuilt
- reward component failed closed due to missing carrier type
- gameplay carrier unavailable
- visual carrier unavailable
- external attribute missing
- external effect channel missing
- reward component skipped due to unavailable runtime surface
- foreign visual/effect surface preserved

Mutator / entity application surfaces should be able to explain these outcomes where relevant:
- required entity capability missing
- required hook unavailable
- component skipped due to incompatible entity runtime surface
- branch disabled due to removed definition
- runtime instance references missing definition

Mutator / reward compatibility surfaces should be able to explain these outcomes where relevant:
- optional runtime surface unavailable
- extra equipment slot surface unavailable
- custom combat hook unavailable
- custom attribute surface unavailable
- boss-runtime surface unavailable
- client visual channel unavailable
- compat-sensitive branch skipped
- foreign state preserved by ownership policy

--- 

## 6. Trace ID Propagation Rules

Trace ID lifecycle:
1. generated at pass entry
2. attached to trigger evaluation
3. attached to rule evaluation
4. attached to action execution records
5. attached to downstream subsystem hooks (mutator/loot/invasion/ascension)
6. emitted in command/log output until pass completion

Hard rules:
- one pass has one stable root trace ID
- sub-steps may append deterministic suffixes (`.t1`, `.r3`, `.a2`) but must keep the root ID
- trace IDs must not be reused across independent passes

---

## 7. Provenance Visibility Requirements

### 7.1 Mutator Provenance

When an entity is mutated, inspect output must show:
- entity ID and UUID
- selected pool ID
- selected mutator ID
- resolved component list in final order
- rejected candidates with reason
- budget/conflict/duplicate outcomes

When an eligible spawn is not mutated due to coexistence policy, inspect/debug output should still show:
- upstream-cancel skip reason when present
- external-transform detection marker when present
- spawn-context invalidation reason when present
- re-entry-blocked reason when present

### 7.2 Rule Provenance

For rule execution:
- rule ID
- scope key
- condition outcomes per node
- action outcomes per node
- state writes (cooldown, consumed, stage, scalar)

### 7.3 Ascension Provenance

For ascension events:
- offer template ID
- runtime offer instance key
- candidate rewards
- selected reward
- forfeited rewards
- reconciliation status on login/respawn
- active WA-owned runtime carrier IDs and stable keys when those carriers are part of the reconciled reward set
- saved reward ownership state
- live-applied WA-owned modifier/effect state
- suppression state (`active`, `suppressed`, `partially_suppressed`, `suppressed_group`)
- suppression rejection state (`suppression_rejected_invalid_group_state`, `suppression_rejected_not_independently_supported`)
- failed-closed reward component state
- foreign state intentionally preserved
- debug/runtime diagnostics should include component-level reconcile skip codes and details when a reward effect fails closed for safety
- operator/debug reset outcome when an offer is reopened or cleared

### 7.4 Loot/Invasion Provenance

For loot and invasions:
- profile IDs
- compatibility safety decisions
- applied operations and blocked operations
- active caps/safety guards

---

## 8. Raw Node and Resolved View Requirements

Default mode:
- show concise summaries and rejection categories

Operator/debug mode:
- may show raw condition/action nodes
- must show resolved component lists for component-based definitions
- must show explicit resolution path for dropped/rejected branches

Hard rule:
- summaries must preserve authored IDs; never replace with opaque internal-only identifiers.

---

## 9. Performance and Safety Rules

- debug mode must not mutate gameplay authority decisions
- debug payload generation must remain bounded
- trace emission failures must not crash gameplay systems
- sensitive or huge payload branches may be truncated with explicit marker fields

---

## 10. Example End-to-End Trace

```json
{
  "trace_id": "WA-20260312-1ab2",
  "event_type": "entity_spawn",
  "scope": "spawn_event",
  "subsystem": "mutator",
  "phase": "apply_mutator_pool",
  "object_type": "mutation_pools",
  "object_id": "my_pack:overworld_night_t2",
  "outcome": "executed",
  "reason_code": null,
  "reason_category": "none",
  "resolved_components": [
    "worldawakened:max_health_multiplier",
    "worldawakened:reinforcement_summon"
  ],
  "dropped_components": [
    {
      "type": "worldawakened:projectile_split",
      "reason_code": "WA_COMPONENT_BUDGET_EXCEEDED"
    }
  ],
  "state_changes": [
    "entity_tag:worldawakened:mutated",
    "metadata.pool=my_pack:overworld_night_t2"
  ]
}
```

---

## 11. Phase Alignment (MVP Roadmap)

This contract aligns to future implementation phases as follows:
- Phase 5: `mob inspect` provenance, mutation-budget visibility, and ownership-safe mutator fail-closed diagnostics
- Phase 6: rule-event guardrail diagnostics and deeper runtime observability
- Phase 7-9: loot/invasion/compat inspection surfaces and rejection-path visibility
- Phase 10: tooling-facing validation/debug payload alignment for authoring workflows
- Phase 11: required `/wa debug perf`, `/wa debug rules`, `/wa debug mutators` surfaces and telemetry hardening

Roadmap sync rule:
- if a phase adds or changes inspect/debug surfaces, update this file and `SPECIFICATION.md` together.

Inspect/debug expectation notes:
- `/wa ascension inspect` should distinguish saved reward ownership, WA-owned carrier state, live WA-owned modifier/effect state, failed-closed component state, and foreign state intentionally preserved
- `/wa ascension inspect` should also surface suppression diagnostics such as:
  - `WA_ASC_SUPPRESSION_APPLIED`
  - `WA_ASC_SUPPRESSION_REMOVED`
  - `WA_ASC_SUPPRESSION_INVALID_PARTIAL`
  - `WA_ASC_SUPPRESSION_GROUP_REQUIRED`
  - `WA_ASC_COMPONENT_NOT_SUPPRESSIBLE`
  - `WA_ASC_SUPPRESSED_DEFINITION_MISSING`
- `/wa mob inspect` should distinguish persisted mutation provenance, currently resolvable definition state, and failed-closed mutator component state where relevant
- `/wa mob inspect` should also surface mutator ownership-safe branch diagnostics where relevant:
  - `WA_ENTITY_RUNTIME_SURFACE_MISSING`
  - `WA_MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE`
  - `WA_RUNTIME_SURFACE_OPTIONAL_UNAVAILABLE`
  - `WA_COMPAT_BRANCH_SKIPPED_SURFACE_UNAVAILABLE`
  - `WA_EXTRA_SLOT_SURFACE_UNAVAILABLE`
  - `WA_COMBAT_HOOK_UNAVAILABLE`
  - `WA_CUSTOM_ATTRIBUTE_SURFACE_UNAVAILABLE`
  - `WA_BOSS_RUNTIME_SURFACE_UNAVAILABLE`
  - `WA_CLIENT_VISUAL_CHANNEL_UNAVAILABLE`
  - `WA_RUNTIME_INSTANCE_MISSING_DEFINITION`
  - `WA_FOREIGN_STATE_PRESERVATION_REQUIRED`
- `/wa mob inspect` should clearly indicate when World Awakened intentionally preserved foreign entity state instead of mutating it
- `/wa mob inspect` should be able to show when a mutator branch was skipped because a required optional runtime surface was unavailable
- `/wa ascension inspect` should be able to show when a reward component failed closed because a required runtime surface or carrier class was unavailable
- trace output should distinguish selector/config/condition rejection, composition rejection, optional runtime-surface unavailability, and ownership-policy preservation of foreign state
- debug traces should clearly indicate when World Awakened intentionally preserved foreign state instead of mutating it

---

## 12. Contributor Update Rules

- If a new subsystem is added, define its required inspection surface before declaring the feature complete.
- If a new rejection reason category appears, update this file and [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md) together.
- Keep command examples aligned with actual command tree behavior.
- Keep provenance requirements synchronized with `DATAPACK_AUTHORING.md` debug workflow guidance.

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
| Ascension | `/wa ascension inspect` | player UUID, offer ID, reward IDs, source key | pending/resolved state, forfeits, grant/reconcile outcome | `implemented` |
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

Reason categories must map to canonical code families in [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md).

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
- Phase 5: `mob inspect` provenance and mutation-budget visibility
- Phase 6: rule-event guardrail diagnostics and deeper runtime observability
- Phase 7-9: loot/invasion/compat inspection surfaces and rejection-path visibility
- Phase 10: tooling-facing validation/debug payload alignment for authoring workflows
- Phase 11: required `/wa debug perf`, `/wa debug rules`, `/wa debug mutators` surfaces and telemetry hardening

Roadmap sync rule:
- if a phase adds or changes inspect/debug surfaces, update this file and `SPECIFICATION.md` together.

---

## 12. Contributor Update Rules

- If a new subsystem is added, define its required inspection surface before declaring the feature complete.
- If a new rejection reason category appears, update this file and [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md) together.
- Keep command examples aligned with actual command tree behavior.
- Keep provenance requirements synchronized with `DATAPACK_AUTHORING.md` debug workflow guidance.

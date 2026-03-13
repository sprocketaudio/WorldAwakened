# World Awakened Scope Matrix

Canonical reference for shared execution scopes, guaranteed context, and condition/action legality.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Shared scope model used by runtime, validators, and tooling

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever a scope is added/renamed, guaranteed context changes, or condition/action legality changes.
- Keep this file aligned with condition/action catalogs, performance guardrails, and shared validation rules.

---

## 1. Purpose

This document is the canonical scope reference for World Awakened shared framework execution.

Conditions and actions must declare legal scopes, and scope legality must be enforced consistently across runtime, validators, and tooling.
All scopes are shared framework concepts; no subsystem may silently redefine scope behavior.
If a condition/action is legal only in certain scopes, this matrix is the canonical reference point.

## 2. Overview

A scope is the execution domain for shared framework conditions/actions.

Scopes exist to define, for each evaluation:
- what runtime context is guaranteed
- which condition categories are legal
- which action categories are legal
- how missing context must behave
- where persistent mutations are allowed to land

Using a condition or action outside its legal scope is a validation error.

## 3. Canonical Scope List

Only these canonical shared scopes are valid:
- `world`
- `player`
- `entity`
- `spawn_event`
- `loot`
- `invasion`
- `event_context`

No subsystem may add pseudo-scopes without promoting them into shared contracts first.

## 4. Scope Entry Template

Each scope entry in this file defines:
- scope id
- purpose
- guaranteed context
- optional context
- typical producers/consumers
- allowed condition categories
- allowed action categories
- persistence domain
- invalid/missing-context behavior
- notes/example

## 5. Scope Definitions

### A. `world`

- Scope id: `world`
- Purpose: Global/world-state evaluation and world-owned orchestration.
- Guaranteed context:
  - world reference
  - world progression state snapshot
  - world config gates/toggles
  - world scalar context when relevant
- Optional context:
  - none unless explicitly attached by event wrappers
- Typical producers/consumers:
  - `trigger_rules.source_scope = world`
  - `rules.execution_scope = world`
  - world-level scheduler/integration hooks
- Allowed condition categories:
  - Progression/state
  - World/time/environment
  - External/integration/config
  - Random/event context where event metadata exists
  - Player category only for world-safe aggregate conditions explicitly legal in `world` (for example `player_count_online`)
- Disallowed examples:
  - pure player-only checks that require a bound player object (for example `player_health_range`) when no player context exists
  - pure entity-only checks that require a bound entity object (for example `entity_type`) when no entity context exists
- Allowed action categories:
  - Progression/state world actions
  - Invasion/event world actions
  - Explicitly documented world-legal scheduling/integration actions
- Persistence domain:
  - world `SavedData` and world runtime state
- Invalid/missing-context behavior:
  - out-of-scope conditions/actions are validation errors
  - legal-but-context-dependent checks fail closed (`false`) when optional context is unavailable
- Notes/example:
  - Example: world rule checks `stage_unlocked` + `world_day_gte` and applies `set_world_scalar`.

### B. `player`

- Scope id: `player`
- Purpose: Player-targeted evaluation and player-owned progression/ascension behavior.
- Guaranteed context:
  - player reference
  - world reference
  - player progression/ascension state where relevant
- Optional context:
  - entity or event attachments when present in the snapshot
- Typical producers/consumers:
  - `trigger_rules.source_scope = player`
  - `rules.execution_scope = player`
  - ascension offer/reward flows
- Allowed condition categories:
  - Progression/state
  - World/time/environment
  - Player
  - External/integration/config
  - Entity or random/event context categories when snapshot fields exist and condition scope metadata allows them
- Allowed action categories:
  - Player progression/ascension/scalar actions
  - Messaging/reward actions that explicitly allow `player`
  - World-mutating actions only when an action entry explicitly allows `player` and defines world mutation semantics
- Persistence domain:
  - player persistent state, plus world state only when the action explicitly mutates world-owned data
- Invalid/missing-context behavior:
  - player-dependent conditions are legal here
  - missing player reference invalidates `player` scope evaluation and fails closed
- Notes/example:
  - Example: player rule checks `ascension_reward_owned` and grants `grant_ascension_offer`.

### C. `entity`

- Scope id: `entity`
- Purpose: Entity-targeted evaluation for entity-specific checks and effects.
- Guaranteed context:
  - entity reference
  - world reference
- Optional context:
  - player reference when event source binds one
  - progression snapshots if supplied by caller/runtime pass
- Typical producers/consumers:
  - `rules.execution_scope = entity`
  - spawn-adjacent evaluation passes with concrete entity targets
- Allowed condition categories:
  - Entity
  - Progression/state where bound snapshots exist
  - World/time/environment
  - External/integration/config
  - Player/random/event context categories only when required snapshot fields exist
- Allowed action categories:
  - Entity and mutation/spawn-adjacent actions
  - Entity-legal progression/state actions
  - Any cross-domain action only when the action explicitly declares `entity`
- Persistence domain:
  - runtime entity application, plus any persistent world/player state explicitly mutated by action semantics
- Invalid/missing-context behavior:
  - if the entity is invalid/removed before completion, entity-targeted behavior fails closed
  - missing optional player/event data makes dependent checks false
- Notes/example:
  - Example: entity-scoped rule checks `entity_not_boss` and `current_dimension` before applying entity-legal actions.

### D. `spawn_event`

- Scope id: `spawn_event`
- Purpose: Ephemeral spawn-path evaluation before/around entity finalization.
- Guaranteed context:
  - spawn event snapshot
  - world reference
  - target entity or pending spawn context
- Optional context:
  - player source attribution when available
  - biome/dimension/structure/light context snapshots when available
- Typical producers/consumers:
  - `rules.execution_scope = spawn_event`
  - future mutation pool/stat profile handlers
- Allowed condition categories:
  - Entity
  - World/time/environment
  - Progression/state
  - External/integration/config
  - Random/event context
  - Player category when player attribution exists in snapshot and the condition allows `spawn_event`
- Allowed action categories:
  - Mutation/spawn actions
  - Spawn-safe event outputs and other actions only when explicitly documented as `spawn_event`-legal
- Persistence domain:
  - primarily runtime/event-bound state; persistent writes only when an action explicitly documents them
- Invalid/missing-context behavior:
  - missing optional player/entity-derived fields fail closed for dependent conditions
  - no assumption of pre-existing persisted entity metadata before spawn finalize
- Notes/example:
  - Example: spawn-event rule checks `entity_type` + `world_day_gte` then attempts `apply_mutator_pool`.

### E. `loot`

- Scope id: `loot`
- Purpose: Loot generation and loot-table/profile evaluation.
- Guaranteed context:
  - loot context snapshot
  - target loot table/drop source
  - world reference
- Optional context:
  - player, entity, structure, and stage snapshots when supplied by loot source/event
- Typical producers/consumers:
  - loot profile evaluators and reward integration passes
- Allowed condition categories:
  - Progression/state
  - World/time/environment
  - Entity/player conditions only when loot snapshot provides needed context and condition scope metadata allows `loot`
  - External/integration/config
  - Random/event context where loot event metadata exists
- Allowed action categories:
  - Loot/reward actions
  - No unrelated world mutation unless an action explicitly documents `loot` legality and world mutation semantics
- Persistence domain:
  - runtime loot assembly by default; persistent mutations only for actions that explicitly define them
- Invalid/missing-context behavior:
  - if player/entity context is absent, dependent conditions evaluate `false`
  - out-of-scope action usage is a validation error
- Notes/example:
  - Example: loot evaluation checks `stage_unlocked` + `entity_is_mutated` then uses `inject_loot_profile` when legal.

### F. `invasion`

- Scope id: `invasion`
- Purpose: Invasion scheduling, activation, and wave-processing logic.
- Guaranteed context:
  - world reference
  - invasion runtime/scheduler state
- Optional context:
  - targeted player set
  - wave entity context
  - stage/scalar snapshots
- Typical producers/consumers:
  - invasion scheduler and active invasion passes
  - invasion-related rule/event processing
- Allowed condition categories:
  - Progression/state
  - World/time/environment
  - Player/entity categories where invasion snapshot includes required targets
  - External/integration/config
  - Random/event context where invasion event metadata exists
- Allowed action categories:
  - Invasion/event actions
  - Progression/state and messaging actions when explicitly invasion-legal
  - Reward actions when explicitly invasion-legal
- Persistence domain:
  - invasion runtime state and world-owned state
- Invalid/missing-context behavior:
  - player/entity-dependent checks fail closed when no target player/entity is attached
  - invalid-scope usage remains a validation error even during live invasion processing
- Notes/example:
  - Example: invasion scheduler checks `invasion_active` + `player_count_online` then runs `schedule_invasion_check`.

### G. `event_context`

- Scope id: `event_context`
- Purpose: Generic wrapper scope for trigger/rule evaluation when concrete context varies by event subtype.
- Guaranteed context:
  - event snapshot object
  - world reference when the wrapped event is world-bound
- Optional context:
  - player
  - entity
  - loot snapshot
  - invasion snapshot
  - trigger source metadata (including source scope where provided)
- Typical producers/consumers:
  - generic trigger/rule wrappers
  - shared pipelines bridging heterogeneous event sources
- Allowed condition categories:
  - any condition category only when both are true:
    - condition metadata includes `event_context`
    - snapshot includes required fields
- Allowed action categories:
  - any action category only when both are true:
    - action metadata includes `event_context`
    - runtime pass/event subtype allows execution at that point
- Persistence domain:
  - determined by executed actions, not by `event_context` alone
- Invalid/missing-context behavior:
  - required context missing in an otherwise legal condition -> evaluate `false`
  - condition/action missing `event_context` legality -> validation error
- Notes/example:
  - Example: wrapper event checks `source_scope_match` then executes `emit_named_event` if legal in that pass.

## 6. Legal Condition/Action Matrix

Condition categories by scope (category-level allowance; per-condition legality is authoritative in [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)):

| Scope | Allowed condition categories |
| --- | --- |
| `world` | Progression/state; World/time/environment; External/integration/config; Random/event context (when present); limited world-safe aggregate player conditions only when explicitly world-legal |
| `player` | Progression/state; World/time/environment; Player; External/integration/config; Entity and Random/event context when snapshot supports them |
| `entity` | Entity; Progression/state; World/time/environment; External/integration/config; Player and Random/event context when snapshot supports them |
| `spawn_event` | Entity; Progression/state; World/time/environment; External/integration/config; Random/event context; Player when spawn attribution exists |
| `loot` | Progression/state; World/time/environment; Entity/Player when loot snapshot supports them; External/integration/config; Random/event context |
| `invasion` | Progression/state; World/time/environment; Player/Entity when invasion target context exists; External/integration/config; Random/event context |
| `event_context` | Any category only if required snapshot data exists and condition entry includes `event_context` |

Action categories by scope (category-level allowance; per-action legality is authoritative in [ACTION_REFERENCE.md](ACTION_REFERENCE.md)):

| Scope | Allowed action categories |
| --- | --- |
| `world` | Progression/state; Invasion/event; explicitly documented world-legal scheduling/integration actions |
| `player` | Progression/state; Ascension; Loot/reward and Invasion/event messaging where action metadata allows `player` |
| `entity` | Mutation/spawn; entity-legal Progression/state; Loot/reward where action metadata allows `entity` |
| `spawn_event` | Mutation/spawn; spawn-event-safe event outputs/actions only when explicitly legal |
| `loot` | Loot/reward (plus only explicitly documented cross-domain actions) |
| `invasion` | Invasion/event; invasion-legal Progression/state; Reward/messaging actions where explicitly legal |
| `event_context` | Any category only if action entry includes `event_context` and runtime pass/subtype permits execution |

## 7. Invalid-Scope Rule

- Using a condition or action outside its declared allowed scopes is a validation error.
- Missing context inside an otherwise legal scope evaluates `false` unless a condition/action entry explicitly defines different behavior.
- Scope legality should be enforced pre-runtime (reload/validation/tooling) wherever possible; runtime must still fail closed for unavailable optional context.

## 8. Persistence Notes

- Scope does not automatically decide persistence.
- Action semantics decide whether world/player/runtime state is mutated.
- Scope defines legal context and evaluation domain, not storage policy by itself.
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md) is authoritative for per-action persistence behavior.

## 9. Contributor Update Rules

- Update this file whenever a canonical scope is added, removed, renamed, or has semantic changes.
- Update this file whenever any condition or action gains/loses legal scopes.
- Keep this matrix aligned with [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md), [ACTION_REFERENCE.md](ACTION_REFERENCE.md), and [SPECIFICATION.md](SPECIFICATION.md).
- Do not add subsystem-specific pseudo-scopes; promote scope changes through shared contracts and spec updates first.

## 10. Common Mistakes

- Using player-only conditions (for example `player_health_range`) in `world` scope without bound player context.
- Using entity-only conditions in `loot` scope when the loot snapshot does not carry an entity.
- Assuming `spawn_event` runtime context implies persistent entity metadata already exists.

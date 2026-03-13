# World Awakened Action Reference

Canonical reference for shared action node IDs, scope legality, execution semantics, and status markers.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Shared action contracts across runtime, validation, and tooling

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever action IDs, parameter schemas, scope legality, status labels, or runtime execution support changes.
- Keep this file aligned with `CONDITION_REFERENCE.md`, `SCOPE_MATRIX.md`, performance guardrails, and validation diagnostics.

---

## 1. Purpose

This document is the canonical reference for supported World Awakened action types.
- Action behavior is defined by shared framework execution logic and shared-contract validators.
- This file is the practical reference for authors/maintainers and does not replace full runtime design prose.

## 2. Overview

An action is a server-authoritative execution node that can:
- mutate progression or runtime state
- emit events/messages
- schedule downstream framework work

Actions are shared framework primitives. They are not subsystem-owned variants.

Systems that currently produce or consume shared actions:
- `trigger_rules` outputs
- generic runtime `rules`
- ascension grant/reconcile flows
- mutation/spawn flow integration points
- loot/reward flow integration points
- invasion/event flow integration points
- future web authoring/validation tooling

Actions execute under shared execution safety rules (deterministic ordering, bounded processing, scope legality checks, and fail-closed validation behavior).

## 3. Shared Authoring Rules

- Every action must include `type`.
- Every action must include `parameters` (object).
- Actions may include optional `enabled`.
- Actions may include optional `priority`.
- Actions may include optional `debug_label`.
- Unknown action types fail validation in strict typed-node validation paths.
- Invalid parameter shape fails validation.
- Using an action outside valid scopes fails validation.
- Each action must explicitly document idempotency.
- Each action must explicitly document persistence behavior.
- Status must be explicit: `implemented`, `planned`, `reserved`, or `deprecated`.
- Planned/reserved/deprecated actions must never be treated as active runtime behavior.

Current v1 validator/runtime baseline (repository state as of March 12, 2026):
- `trigger_rules` validator accepts: `unlock_stage`, `lock_stage`, `emit_named_event`, `increment_counter`, `send_warning_message`, `grant_ascension_offer`.
- `rules` validator accepts: `unlock_stage`, `lock_stage`, `grant_ascension_offer`, `apply_mutator_pool`, `apply_stat_profile`, `inject_loot_profile`, `trigger_invasion_profile`, `send_warning_message`, `drop_reward_table`, `mark_rule_consumed`, `set_world_scalar`, `set_temp_invasion_modifier`.
- `rules` runtime executes now: `unlock_stage`, `lock_stage`, `grant_ascension_offer`, `send_warning_message`, `mark_rule_consumed`, `set_world_scalar`.
- `rules` runtime currently defers/no-ops: `apply_mutator_pool`, `apply_stat_profile`, `inject_loot_profile`, `trigger_invasion_profile`, `drop_reward_table`, `set_temp_invasion_modifier`.

## 4. Canonical Action Entry Shape

Canonical shared action node:

```json
{
  "type": "worldawakened:unlock_stage",
  "parameters": {
    "stage": "my_pack:nether_opened"
  },
  "enabled": true,
  "priority": 100,
  "debug_label": "unlock after threshold"
}
```

Execution model requirements:
- Authored order is deterministic.
- Runtime ordering is deterministic (`priority` first, then authored order).
- `priority` does not bypass shared safety guarantees.
- Action processing is bounded; uncontrolled self-chaining is not allowed.

## 5. Action Categories

### A. Progression / State Actions
- `unlock_stage`
- `lock_stage`
- `emit_named_event`
- `increment_counter`
- `set_counter`
- `start_cooldown`
- `mark_rule_consumed`
- `set_world_scalar`
- `set_player_scalar`
- `set_temp_invasion_modifier`

### B. Ascension Actions
- `grant_ascension_offer`
- `revoke_ascension_reward`
- `queue_ascension_offer`

### C. Mutation / Spawn Actions
- `apply_mutator_pool`
- `apply_stat_profile`
- `attach_spawn_metadata`

### D. Loot / Reward Actions
- `inject_loot_profile`
- `drop_reward_table`
- `grant_reward`

### E. Invasion / Event Actions
- `trigger_invasion_profile`
- `schedule_invasion_check`
- `send_warning_message`

## 6. Per-Action Entry Template

Each action entry uses the same structure:
- Action ID
- Category
- Purpose
- Valid scopes
- Parameters
- Idempotency
- Persistence
- Ordering / Execution Notes
- Defaults / Notes
- Compatibility Notes
- Status
- Example Snippet

## 7. Scope Requirements

Canonical scope IDs used by this file:
- `world`
- `player`
- `entity`
- `spawn_event`
- `loot`
- `invasion`
- `event_context`

Scope rules:
- Every action entry lists canonical valid scopes.
- Scope legality must stay aligned with shared scope contracts.
- Current v1 scope-bearing object fields are limited to:
  - `trigger_rules.source_scope`: `world | player`
  - `rules.execution_scope`: `world | player | entity | spawn_event`
- Canonical scopes `loot`, `invasion`, and `event_context` are framework-level contracts and are promoted into object schemas as phases advance.

## 8. Idempotency Rules

Idempotency labels used in this file:
- `idempotent`: repeated application is safe and does not create additional effect.
- `non-idempotent`: repeated application changes outcomes unless explicitly guarded.
- `conditionally idempotent`: idempotent only under documented state/policy constraints.

Examples:
- `unlock_stage`: idempotent.
- `lock_stage`: conditionally idempotent (state + regression policy gated).
- `grant_ascension_offer`: idempotent by runtime offer identity key.
- `drop_reward_table`: non-idempotent unless downstream runtime adds explicit dedupe/guards.

## 9. Persistence Rules

Persistence labels used in this file:
- Mutates persistent world state.
- Mutates persistent player state.
- Ephemeral runtime only.
- Schedules later work without immediate persistence.

Examples:
- `set_world_scalar`: persistent world state.
- `grant_ascension_offer`: persistent player state.
- `apply_stat_profile`: runtime/entity application.
- `send_warning_message`: ephemeral output only.

## 10. Ordering / Execution Notes

Shared ordering guarantees used across entries:
- Action sorting is deterministic (`priority` descending, then authored index).
- Trigger and rule evaluations are snapshot-based for condition matching.
- Stage unlock/lock effects apply during action application and are visible to later event passes, not retroactively to already-evaluated same-pass rule conditions.
- Rule execution includes recursion protection (`WorldAwakenedRecursionGuard`) and bounded processing semantics.
- Planned action handlers that are accepted-but-deferred must not silently pretend to have executed effects.

## 11. Status Taxonomy Usage

Action status labels:
- `implemented`: supported for active runtime use now.
- `planned`: designed contract exists; runtime/validator support may be partial or deferred.
- `reserved`: identifier/shape reserved only; do not use in live content.
- `deprecated`: compatibility only; should warn and migrate away.

Current action catalog state:
- This catalog currently uses `implemented` and `planned`.
- No canonical action in this file is currently marked `reserved` or `deprecated`.

## 12. Canonical Action Entries

### A. Progression / State Actions

#### `worldawakened:unlock_stage`
- **Category:** Progression / state
- **Purpose:** Unlock a stage in resolved scope context.
- **Valid scopes:** `world`, `player`
- **Parameters:** `stage` (`resource_location`, required)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent world/player stage state.
- **Ordering / Execution Notes:** Applied in sorted action order; stage changes affect subsequent passes.
- **Defaults / Notes:** None.
- **Compatibility Notes:** Implemented in both `trigger_rules` and `rules`.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:unlock_stage",
  "parameters": { "stage": "my_pack:nether_opened" }
}
```

#### `worldawakened:lock_stage`
- **Category:** Progression / state
- **Purpose:** Lock a stage in resolved scope context.
- **Valid scopes:** `world`, `player`
- **Parameters:** `stage` (`resource_location`, required)
- **Idempotency:** `conditionally idempotent`
- **Persistence:** Mutates persistent world/player stage state.
- **Ordering / Execution Notes:** Applied in sorted action order; stage locks affect subsequent passes.
- **Defaults / Notes:** None.
- **Compatibility Notes:** Implemented in both `trigger_rules` and `rules`.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:lock_stage",
  "parameters": { "stage": "my_pack:nether_opened" }
}
```

#### `worldawakened:emit_named_event`
- **Category:** Progression / state
- **Purpose:** Emit a named internal framework event.
- **Valid scopes:** `world`, `player`, `entity`, `event_context`
- **Parameters:** `event` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral runtime only.
- **Ordering / Execution Notes:** Emitted in action order; current trigger runtime posts immediately to the NeoForge event bus.
- **Defaults / Notes:** Use deterministic event IDs.
- **Compatibility Notes:** Currently implemented in `trigger_rules` only (`source_scope` `world|player`); not a `rules` action today.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:emit_named_event",
  "parameters": { "event": "my_pack:boss_kill_chain_step" }
}
```

#### `worldawakened:increment_counter`
- **Category:** Progression / state
- **Purpose:** Increment a named counter by delta.
- **Valid scopes:** `world`, `player`, `event_context`
- **Parameters:** `counter` (`string`, required), `amount` (`integer`, optional)
- **Idempotency:** `non-idempotent`
- **Persistence:** Mutates persistent world/player trigger counter state.
- **Ordering / Execution Notes:** Deterministic priority/authored ordering.
- **Defaults / Notes:** `amount` defaults to `1` in current trigger runtime.
- **Compatibility Notes:** Implemented in `trigger_rules`; currently rejected in `rules`.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:increment_counter",
  "parameters": { "counter": "my_pack:boss_kills", "amount": 1 }
}
```

#### `worldawakened:set_counter`
- **Category:** Progression / state
- **Purpose:** Set a named counter to an explicit value.
- **Valid scopes:** `world`, `player`, `event_context`
- **Parameters:** `counter` (`string`, required), `value` (`integer`, required)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent world/player counter state.
- **Ordering / Execution Notes:** Intended for deterministic explicit state writes.
- **Defaults / Notes:** No default value.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:set_counter",
  "parameters": { "counter": "my_pack:boss_kills", "value": 10 }
}
```

#### `worldawakened:start_cooldown`
- **Category:** Progression / state
- **Purpose:** Start or refresh a cooldown key.
- **Valid scopes:** `world`, `player`, `entity`, `event_context`
- **Parameters:** `key` (`string`, required), `duration_seconds` (`number`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Mutates persistent cooldown state.
- **Ordering / Execution Notes:** Repeated applications refresh/extend cooldown by policy.
- **Defaults / Notes:** Duration unit is seconds in canonical shape.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:start_cooldown",
  "parameters": { "key": "my_pack:rare_spawn_gate", "duration_seconds": 30 }
}
```

#### `worldawakened:mark_rule_consumed`
- **Category:** Progression / state
- **Purpose:** Mark a rule-consumed flag for the resolved scope key.
- **Valid scopes:** `world`, `player`, `entity`, `event_context`
- **Parameters:** `rule` (`resource_location`, optional)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent rule-state consumed set.
- **Ordering / Execution Notes:** Should run late when combined with other gating side effects.
- **Defaults / Notes:** Current `rules` runtime marks the current matched rule key; `parameters.rule` is not consumed yet.
- **Compatibility Notes:** Implemented in `rules` (`world|player|entity` validator scopes today); not available in `trigger_rules`.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:mark_rule_consumed",
  "parameters": {}
}
```

#### `worldawakened:set_world_scalar`
- **Category:** Progression / state
- **Purpose:** Write or transform a world scalar value.
- **Valid scopes:** `world`, `event_context`
- **Parameters:** `key` (`string`, required), `op` (`set|add|multiply`, optional), `value` (`number`, required)
- **Idempotency:** `conditionally idempotent`
- **Persistence:** Mutates persistent world scalar state.
- **Ordering / Execution Notes:** Deterministic last-writer behavior under sorted action ordering.
- **Defaults / Notes:** `op` defaults to `set` in current runtime; aliases `mul/*` and `add/+` are accepted by runtime operator parsing.
- **Compatibility Notes:** Implemented in `rules` for `world` scope; `event_context` promotion is planned.
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:set_world_scalar",
  "parameters": { "key": "spawn_pressure", "op": "multiply", "value": 1.15 }
}
```

#### `worldawakened:set_player_scalar`
- **Category:** Progression / state
- **Purpose:** Write or transform a player scalar value.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `key` (`string`, required), `op` (`set|add|multiply`, optional), `value` (`number`, required)
- **Idempotency:** `conditionally idempotent`
- **Persistence:** Mutates persistent player scalar state.
- **Ordering / Execution Notes:** Deterministic last-writer behavior under sorted action ordering.
- **Defaults / Notes:** `op` defaults to `set` in canonical contract.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:set_player_scalar",
  "parameters": { "key": "survival_pressure", "op": "set", "value": 0.8 }
}
```

#### `worldawakened:set_temp_invasion_modifier`
- **Category:** Progression / state
- **Purpose:** Apply a temporary invasion scalar modifier.
- **Valid scopes:** `invasion`, `world`, `event_context`
- **Parameters:** `key` (`string`, required), `value` (`number`, required), `duration_seconds` (`number`, optional)
- **Idempotency:** `non-idempotent`
- **Persistence:** Schedules/updates temporary runtime modifier state (expiry-based).
- **Ordering / Execution Notes:** Must obey bounded deterministic expiry handling.
- **Defaults / Notes:** Duration is optional; policy decides fallback duration.
- **Compatibility Notes:** Accepted in current `rules` validator only for `world` scope, but runtime handler is currently deferred/no-op.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:set_temp_invasion_modifier",
  "parameters": { "key": "wave_health", "value": 1.25, "duration_seconds": 60 }
}
```

### B. Ascension Actions

#### `worldawakened:grant_ascension_offer`
- **Category:** Ascension
- **Purpose:** Grant an ascension offer runtime instance.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `offer` (`resource_location`, required), `source_key` (`string`, optional)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent player ascension offer state.
- **Ordering / Execution Notes:** Obeys pending-offer queue semantics and deterministic instance identity.
- **Defaults / Notes:** Current trigger/rule handlers derive source identity from invoking rule/trigger; authored `source_key` is not consumed yet.
- **Compatibility Notes:** Implemented in `trigger_rules` (`player` scope) and `rules` (`player` scope).
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:grant_ascension_offer",
  "parameters": { "offer": "my_pack:survival_path" }
}
```

#### `worldawakened:revoke_ascension_reward`
- **Category:** Ascension
- **Purpose:** Revoke a previously chosen ascension reward.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `reward` (`resource_location`, required)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent player ascension reward state.
- **Ordering / Execution Notes:** Intended to execute before reconcile/apply flows.
- **Defaults / Notes:** Policy/admin-gated behavior by design.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:revoke_ascension_reward",
  "parameters": { "reward": "my_pack:ember_blood" }
}
```

#### `worldawakened:queue_ascension_offer`
- **Category:** Ascension
- **Purpose:** Queue an ascension offer when immediate display/grant policy is blocked.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `offer` (`resource_location`, required), `source_key` (`string`, optional)
- **Idempotency:** `idempotent`
- **Persistence:** Mutates persistent player offer queue state.
- **Ordering / Execution Notes:** Queue dedupe is identity-based.
- **Defaults / Notes:** Shared ID remains distinct from direct grant semantics.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:queue_ascension_offer",
  "parameters": { "offer": "my_pack:predator_path", "source_key": "my_pack:night_chain" }
}
```

### C. Mutation / Spawn Actions

#### `worldawakened:apply_mutator_pool`
- **Category:** Mutation / spawn
- **Purpose:** Apply a mutator pool selection to target spawn/entity context.
- **Valid scopes:** `spawn_event`, `entity`, `event_context`
- **Parameters:** `pool` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral spawn/entity application (unless downstream systems persist explicit metadata).
- **Ordering / Execution Notes:** Intended before spawn finalization after eligibility filters.
- **Defaults / Notes:** Pool selection semantics are data-driven.
- **Compatibility Notes:** Accepted by `rules` validator for `entity|spawn_event`; current `rules` runtime defers/no-ops this action.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:apply_mutator_pool",
  "parameters": { "pool": "my_pack:overworld_night_t2" }
}
```

#### `worldawakened:apply_stat_profile`
- **Category:** Mutation / spawn
- **Purpose:** Apply a named stat profile package to target context.
- **Valid scopes:** `spawn_event`, `entity`, `event_context`
- **Parameters:** `profile` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral spawn/entity application.
- **Ordering / Execution Notes:** Must follow deterministic profile conflict/composition ordering.
- **Defaults / Notes:** Profile ID resolves against authored stat profile definitions.
- **Compatibility Notes:** Accepted by `rules` validator for `entity|spawn_event`; current `rules` runtime defers/no-ops this action.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:apply_stat_profile",
  "parameters": { "profile": "my_pack:elite_tier_2" }
}
```

#### `worldawakened:attach_spawn_metadata`
- **Category:** Mutation / spawn
- **Purpose:** Attach deterministic metadata to spawn context.
- **Valid scopes:** `spawn_event`, `event_context`
- **Parameters:** `key` (`string`, required), `value` (`json`, required)
- **Idempotency:** `idempotent`
- **Persistence:** Ephemeral runtime metadata unless explicitly promoted by downstream persistence logic.
- **Ordering / Execution Notes:** Deterministic overwrite/merge order must remain explicit.
- **Defaults / Notes:** Metadata key space should be namespaced by pack or subsystem.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:attach_spawn_metadata",
  "parameters": { "key": "my_pack:spawn_tag", "value": { "elite": true } }
}
```

### D. Loot / Reward Actions

#### `worldawakened:inject_loot_profile`
- **Category:** Loot / reward
- **Purpose:** Inject a loot profile into current loot evaluation.
- **Valid scopes:** `loot`, `event_context`
- **Parameters:** `profile` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral loot assembly.
- **Ordering / Execution Notes:** Must preserve deterministic loot profile merge order.
- **Defaults / Notes:** Additive/replacement behavior is controlled by profile/runtime policy.
- **Compatibility Notes:** Current `rules` validator temporarily accepts this action only in `spawn_event` scope; runtime currently defers/no-ops.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:inject_loot_profile",
  "parameters": { "profile": "my_pack:nether_tier_loot" }
}
```

#### `worldawakened:drop_reward_table`
- **Category:** Loot / reward
- **Purpose:** Trigger explicit reward table drop.
- **Valid scopes:** `loot`, `entity`, `event_context`
- **Parameters:** `table` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral reward/drop output generation.
- **Ordering / Execution Notes:** Must remain bounded by anti-recursion safeguards.
- **Defaults / Notes:** Table IDs should be explicit and stable.
- **Compatibility Notes:** Current `rules` validator accepts this for `entity|spawn_event`; runtime currently defers/no-ops.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:drop_reward_table",
  "parameters": { "table": "my_pack:bonus/boss_cache" }
}
```

#### `worldawakened:grant_reward`
- **Category:** Loot / reward
- **Purpose:** Grant non-loot-table reward payloads.
- **Valid scopes:** `player`, `loot`, `invasion`, `event_context`
- **Parameters:** `reward_type` (`string`, required), `payload` (`object`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Typically mutates persistent player/world state (reward-handler specific).
- **Ordering / Execution Notes:** Handler-level dedupe policy must be explicit.
- **Defaults / Notes:** Payload contract is handler-specific and must be schema-validated by handler type.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:grant_reward",
  "parameters": {
    "reward_type": "my_pack:token_bundle",
    "payload": { "token_id": "my_pack:rift_token", "count": 2 }
  }
}
```

### E. Invasion / Event Actions

#### `worldawakened:trigger_invasion_profile`
- **Category:** Invasion / event
- **Purpose:** Trigger an invasion profile immediately.
- **Valid scopes:** `world`, `invasion`, `event_context`
- **Parameters:** `profile` (`resource_location`, required)
- **Idempotency:** `non-idempotent`
- **Persistence:** Mutates persistent world invasion runtime state.
- **Ordering / Execution Notes:** Must obey invasion concurrency/priority caps.
- **Defaults / Notes:** Profile should reference an authored invasion definition ID.
- **Compatibility Notes:** Current `rules` validator accepts only `world` scope; runtime currently defers/no-ops.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:trigger_invasion_profile",
  "parameters": { "profile": "my_pack:crimson_surge" }
}
```

#### `worldawakened:schedule_invasion_check`
- **Category:** Invasion / event
- **Purpose:** Schedule a later invasion chance check.
- **Valid scopes:** `world`, `invasion`, `event_context`
- **Parameters:** `profile` (`resource_location`, optional), `delay_seconds` (`number`, optional)
- **Idempotency:** `idempotent`
- **Persistence:** Schedules later work in persistent world scheduler state.
- **Ordering / Execution Notes:** Scheduler dedupe key/time policy must remain deterministic.
- **Defaults / Notes:** Missing `profile` means scheduler uses profile resolution policy from current context.
- **Compatibility Notes:** Planned shared action ID; currently rejected by `trigger_rules` and `rules` validators.
- **Status:** `planned`
- **Example Snippet:**
```json
{
  "type": "worldawakened:schedule_invasion_check",
  "parameters": { "profile": "my_pack:crimson_surge", "delay_seconds": 120 }
}
```

#### `worldawakened:send_warning_message`
- **Category:** Invasion / event
- **Purpose:** Emit warning/system messaging to player-facing audiences.
- **Valid scopes:** `world`, `player`, `invasion`, `event_context`
- **Parameters:** `message` (`string` or text-component JSON, required), `audience` (`player|nearby|world`, optional)
- **Idempotency:** `non-idempotent`
- **Persistence:** Ephemeral output only.
- **Ordering / Execution Notes:** Ordered with other actions by priority/authored index.
- **Defaults / Notes:** Current handlers send direct system message to a concrete scoped player.
- **Compatibility Notes:** `trigger_rules` supports this for `player` scope; `rules` validator allows `world|player` but current runtime sends only when player context exists (world-scope call is a no-op).
- **Status:** `implemented`
- **Example Snippet:**
```json
{
  "type": "worldawakened:send_warning_message",
  "parameters": { "message": "The sky darkens..." }
}
```

## 13. Compatibility / Constraints Notes

Shared constraints:
- Scope restrictions are hard validation/runtime contracts.
- Required context must exist for scope-sensitive actions (especially `player` and `entity`).
- Idempotency caveats are part of action semantics and must not be hidden.
- Runtime safety protections (bounded processing, deterministic ordering, recursion guard) apply to all action execution paths.
- Interactions with counters, cooldowns, ascension offers, loot, and invasions must remain explicit and inspectable.

Current implementation constraints:
- Planned shared IDs currently split into three states: rejected-by-validator, accepted-but-deferred, and fully implemented.
- Deferred handlers must remain clearly reported in runtime/debug output; they are not successful side-effect execution.
- Future web authoring validation must consume the same shared action catalog/status metadata used by runtime docs/validators.

## 14. Contributor Update Rules

Update this file whenever:
- an action is added, removed, renamed, or deprecated
- an action parameter shape changes
- valid scopes change
- idempotency or persistence semantics change
- runtime implementation status changes

Maintenance expectations:
- Keep entries aligned with real validators, codecs, and runtime semantics.
- Keep examples copyable and schema-valid.
- Keep scope notes aligned with `docs/SCOPE_MATRIX.md` and shared scope contracts.
- Do not mark planned actions as implemented without real runtime support.
- Keep compatibility caveats explicit for validator implementers and web-tool implementers.

## Appendix A. Compact Action Matrix

| Action ID | Category | Valid scopes | Idempotent? | Persistent? | Status |
| --- | --- | --- | --- | --- | --- |
| `unlock_stage` | Progression/state | world, player | yes | yes | implemented |
| `lock_stage` | Progression/state | world, player | conditional | yes | implemented |
| `emit_named_event` | Progression/state | world, player, entity, event_context | no | no | implemented |
| `increment_counter` | Progression/state | world, player, event_context | no | yes | implemented |
| `set_counter` | Progression/state | world, player, event_context | yes | yes | planned |
| `start_cooldown` | Progression/state | world, player, entity, event_context | no | yes | planned |
| `mark_rule_consumed` | Progression/state | world, player, entity, event_context | yes | yes | implemented |
| `set_world_scalar` | Progression/state | world, event_context | conditional | yes | implemented |
| `set_player_scalar` | Progression/state | player, event_context | conditional | yes | planned |
| `set_temp_invasion_modifier` | Progression/state | invasion, world, event_context | no | schedule/runtime-temp | planned |
| `grant_ascension_offer` | Ascension | player, event_context | yes | yes | implemented |
| `revoke_ascension_reward` | Ascension | player, event_context | yes | yes | planned |
| `queue_ascension_offer` | Ascension | player, event_context | yes | yes | planned |
| `apply_mutator_pool` | Mutation/spawn | spawn_event, entity, event_context | no | no | planned |
| `apply_stat_profile` | Mutation/spawn | spawn_event, entity, event_context | no | no | planned |
| `attach_spawn_metadata` | Mutation/spawn | spawn_event, event_context | yes | no | planned |
| `inject_loot_profile` | Loot/reward | loot, event_context | no | no | planned |
| `drop_reward_table` | Loot/reward | loot, entity, event_context | no | no | planned |
| `grant_reward` | Loot/reward | player, loot, invasion, event_context | no | handler-defined (usually yes) | planned |
| `trigger_invasion_profile` | Invasion/event | world, invasion, event_context | no | yes | planned |
| `schedule_invasion_check` | Invasion/event | world, invasion, event_context | yes | yes | planned |
| `send_warning_message` | Invasion/event | world, player, invasion, event_context | no | no | implemented |

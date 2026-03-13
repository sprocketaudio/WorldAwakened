# World Awakened Condition Reference

Canonical reference for shared condition node IDs, parameter schemas, scope legality, and status markers.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Shared condition contracts across runtime, validation, and tooling

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever condition IDs, wrapper semantics, parameter schemas, scope legality, or status labels change.
- Keep this file aligned with `ACTION_REFERENCE.md`, `SCOPE_MATRIX.md`, performance guardrails, and validation diagnostics.

---

## 1. Purpose

This document is the canonical reference for supported World Awakened condition types.
- Condition behavior is defined by the shared condition framework, runtime evaluators, and validator expectations.
- This file is a lookup/reference companion and does not replace the main design specification.

## 2. Overview

A condition is a server-authoritative boolean gate evaluated against a runtime context snapshot.

Conditions are shared framework primitives and must not diverge by subsystem. The same condition definitions are consumed by:
- `trigger_rules`
- `rules`
- `mob_mutators` / mutation definitions
- `ascension_rewards`
- `ascension_offers`
- `mutation_pools`
- `loot_profiles`
- `invasion_profiles`
- future web authoring/validation workflows

Conditions are composable and reusable. Missing required context fails closed (`false`) unless a condition explicitly documents an exception.

## 3. Shared Authoring Rules

- Every condition has a `type`.
- Every leaf condition must include `parameters` as a JSON object (canonical shared shape).
- Unknown condition types fail validation.
- Invalid condition parameter shape fails validation.
- Conditions must declare/document valid scopes.
- Conditions used outside valid scopes fail validation in shared-contract validators.
- Missing required context evaluates `false` unless the condition entry explicitly documents otherwise.
- Logical wrappers must use canonical wrapper forms only (`all_of`, `any_of`, `not`).
- Conditions are server-authoritative.
- Every condition entry must carry an explicit status: `implemented`, `planned`, `reserved`, or `deprecated`.

Current v1 runtime/validator caveats:
- Canonical `type` + `parameters` parsing is now used by runtime rule/trigger evaluators for implemented paths.
- Loader validation now enforces typed-node shape (`type`, `parameters` object, optional boolean `enabled`, optional string `debug_label`) for typed condition arrays.
- Strict condition type-path validation is currently enforced for `rules`, `trigger_rules`, `mob_mutators`, and `ascension_rewards`; other object types still allow broader condition IDs while retaining node-shape validation.
- Logical wrappers are canonical but currently `planned` for strict runtime execution.
- Flat condition arrays are currently interpreted as implicit AND behavior in implemented evaluators.

## 4. Canonical Condition Entry Shape

Canonical conceptual leaf node:

```json
{
  "type": "worldawakened:stage_unlocked",
  "parameters": {
    "stage": "my_pack:nether_opened"
  },
  "enabled": true,
  "debug_label": "nether unlocked gate"
}
```

Canonical logical wrappers:

```json
{
  "type": "worldawakened:all_of",
  "parameters": {
    "conditions": [
      { "type": "worldawakened:stage_unlocked", "parameters": { "stage": "my_pack:nether_opened" } },
      { "type": "worldawakened:world_day_gte", "parameters": { "value": 20 } }
    ]
  }
}
```

```json
{
  "type": "worldawakened:any_of",
  "parameters": {
    "conditions": [
      { "type": "worldawakened:entity_type", "parameters": { "entity": "minecraft:zombie" } },
      { "type": "worldawakened:entity_tag", "parameters": { "tag": "minecraft:raiders" } }
    ]
  }
}
```

```json
{
  "type": "worldawakened:not",
  "parameters": {
    "condition": {
      "type": "worldawakened:entity_is_boss",
      "parameters": {}
    }
  }
}
```

Wrapper semantics:
- `all_of.parameters.conditions[]`: all child conditions must pass.
- `any_of.parameters.conditions[]`: at least one child condition must pass.
- `not.parameters.condition`: negates one child condition.
- Wrapper nodes contain child condition nodes.
- Leaf nodes contain concrete condition types.
- Nested composition is legal.
- Validators must reject malformed trees, invalid wrapper payloads, and invalid recursive structure.

Wrapper status:
- `worldawakened:all_of`: `planned`
- `worldawakened:any_of`: `planned`
- `worldawakened:not`: `planned`

## 5. Condition Categories

### A. Progression / State

#### `stage_unlocked`
- **Category:** `Progression / state`
- **Purpose:** True when the target stage is active in the resolved scope snapshot.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `stage` (`resource_location`, required)
- **Defaults / Notes:** Use canonical `parameters.stage`.
- **Missing-context behavior:** False when resolved scope has no stage snapshot.
- **Compatibility Notes:** Scope misuse is invalid in shared-contract validation paths.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:stage_unlocked", "parameters": { "stage": "my_pack:nether_opened" } }
```

#### `stage_locked`
- **Category:** `Progression / state`
- **Purpose:** True when the target stage is not active in the resolved scope snapshot.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `stage` (`resource_location`, required)
- **Defaults / Notes:** Use canonical `parameters.stage`.
- **Missing-context behavior:** False when resolved scope has no stage snapshot.
- **Compatibility Notes:** Complements `stage_unlocked`; use one explicit polarity per gate.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:stage_locked", "parameters": { "stage": "my_pack:end_reached" } }
```

#### `ascension_reward_owned`
- **Category:** `Progression / state`
- **Purpose:** True when the resolved player context owns the reward ID.
- **Valid scopes:** `player`, `entity`, `spawn_event`, `event_context`, `invasion`
- **Parameters:** `reward` (`resource_location`, required)
- **Defaults / Notes:** Use canonical `parameters.reward`.
- **Missing-context behavior:** False when player context is unavailable.
- **Compatibility Notes:** Player ownership is authoritative server state.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:ascension_reward_owned", "parameters": { "reward": "my_pack:ember_blood" } }
```

#### `ascension_offer_pending`
- **Category:** `Progression / state`
- **Purpose:** True when player has a pending offer matching the definition ID.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `offer` (`resource_location`, required)
- **Defaults / Notes:** Use canonical `parameters.offer`.
- **Missing-context behavior:** False when player context is unavailable.
- **Compatibility Notes:** Evaluates against pending runtime offer instance state.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:ascension_offer_pending", "parameters": { "offer": "my_pack:tier2_offer" } }
```

#### `invasion_active`
- **Category:** `Progression / state`
- **Purpose:** True when an invasion runtime is currently active.
- **Valid scopes:** `world`, `invasion`, `event_context`
- **Parameters:** `profile` (`resource_location`, optional)
- **Defaults / Notes:** Current implemented runtime checks active-state boolean; profile filtering is a future refinement.
- **Missing-context behavior:** False when invasion runtime context is unavailable.
- **Compatibility Notes:** Use with invasion scheduler/wave contexts where possible.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:invasion_active", "parameters": { "profile": "my_pack:nightfall" } }
```

#### `mutation_present`
- **Category:** `Progression / state`
- **Purpose:** True when target entity carries a matching mutation ID or tag.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** `mutation_id` (`resource_location`, optional), `mutation_tag` (`string`, optional)
- **Defaults / Notes:** At least one selector should be provided.
- **Missing-context behavior:** False when entity mutation metadata is unavailable.
- **Compatibility Notes:** Intended for Phase 5+ mutation-state-aware gates.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:mutation_present", "parameters": { "mutation_tag": "worldawakened:elite" } }
```

#### `rule_consumed`
- **Category:** `Progression / state`
- **Purpose:** True when a consumed flag exists for the resolved rule scope key.
- **Valid scopes:** `world`, `player`, `entity`, `event_context`
- **Parameters:** `rule` (`resource_location`, required)
- **Defaults / Notes:** Intended for one-shot and stateful rule orchestration.
- **Missing-context behavior:** False when rule-state snapshot is unavailable.
- **Compatibility Notes:** Pair with `mark_rule_consumed` action semantics.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:rule_consumed", "parameters": { "rule": "my_pack:lock_after_first_run" } }
```

#### `trigger_consumed`
- **Category:** `Progression / state`
- **Purpose:** True when one-shot trigger state exists for the resolved scope key.
- **Valid scopes:** `world`, `player`, `event_context`
- **Parameters:** `trigger` (`resource_location`, required)
- **Defaults / Notes:** Intended for trigger-level one-shot orchestration.
- **Missing-context behavior:** False when trigger-state snapshot is unavailable.
- **Compatibility Notes:** Complements trigger cooldown/one-shot persistence.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:trigger_consumed", "parameters": { "trigger": "my_pack:first_nether_entry" } }
```

### B. World / Time / Environment

#### `current_dimension`
- **Category:** `World / time / environment`
- **Purpose:** Matches current dimension ID.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `dimension` (`resource_location`, required)
- **Defaults / Notes:** Dimension IDs are canonical registry IDs.
- **Missing-context behavior:** False when dimension cannot be resolved.
- **Compatibility Notes:** In implemented evaluators, dimension is read from active context snapshot.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:current_dimension", "parameters": { "dimension": "minecraft:the_nether" } }
```

#### `current_biome`
- **Category:** `World / time / environment`
- **Purpose:** Matches current biome ID.
- **Valid scopes:** `player`, `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** `biome` (`resource_location`, required)
- **Defaults / Notes:** Biome resolution may depend on event snapshot richness.
- **Missing-context behavior:** False when biome cannot be resolved.
- **Compatibility Notes:** Use with contexts that include position-derived biome metadata.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:current_biome", "parameters": { "biome": "minecraft:deep_dark" } }
```

#### `world_day_gte`
- **Category:** `World / time / environment`
- **Purpose:** True when world day is greater than or equal to threshold.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `value` (`integer >= 0`, required)
- **Defaults / Notes:** Validator enforces non-negative threshold for enforced object types.
- **Missing-context behavior:** False when world day context is unavailable.
- **Compatibility Notes:** Use canonical `parameters.value` in authored content.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:world_day_gte", "parameters": { "value": 20 } }
```

#### `moon_phase`
- **Category:** `World / time / environment`
- **Purpose:** Matches moon phase by index or named phase.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `phase` (`int or phase name`, optional), `phases` (`array<int or phase name>`, optional)
- **Defaults / Notes:** Implemented evaluator maps names (`full_moon`, `new_moon`, etc.). If unset in current runtime path, default set resolves to phase `0`.
- **Missing-context behavior:** False when world day/phase context is unavailable.
- **Compatibility Notes:** Determined from day-cycle modulo 8 in current runtime evaluator.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:moon_phase", "parameters": { "phases": ["full_moon", "new_moon"] } }
```

#### `time_of_day_range`
- **Category:** `World / time / environment`
- **Purpose:** Matches world time-of-day within an inclusive range.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `min` (`0-23999`, optional), `max` (`0-23999`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when time-of-day context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:time_of_day_range", "parameters": { "min": 13000, "max": 23000 } }
```

#### `weather_state`
- **Category:** `World / time / environment`
- **Purpose:** Matches weather state.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `state` (`clear | rain | thunder`, required)
- **Defaults / Notes:** Explicit state string avoids ambiguity.
- **Missing-context behavior:** False when weather state is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:weather_state", "parameters": { "state": "thunder" } }
```

#### `structure_context`
- **Category:** `World / time / environment`
- **Purpose:** Matches structure context label/ID when present.
- **Valid scopes:** `spawn_event`, `loot`, `event_context`
- **Parameters:** `structure` (`string or resource_location`, required by canonical contract)
- **Defaults / Notes:** Current implemented evaluator treats missing parameter as pass when structure context exists; prefer explicit `structure` for deterministic authoring.
- **Missing-context behavior:** False when structure context is unavailable.
- **Compatibility Notes:** Use explicit structure IDs to avoid ambiguous matches.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:structure_context", "parameters": { "structure": "minecraft:stronghold" } }
```

#### `light_level_range`
- **Category:** `World / time / environment`
- **Purpose:** Matches block/entity light level range.
- **Valid scopes:** `entity`, `spawn_event`, `event_context`
- **Parameters:** `min` (`0-15`, optional), `max` (`0-15`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when light level context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:light_level_range", "parameters": { "max": 7 } }
```

### C. Player

#### `player_distance_from_spawn`
- **Category:** `Player`
- **Purpose:** Matches player distance from spawn in blocks.
- **Valid scopes:** `player`, `entity`, `spawn_event`, `event_context`, `invasion`
- **Parameters:** `min` (`number`, optional), `max` (`number`, optional)
- **Defaults / Notes:** At least one bound should be provided; if both are present then `min <= max`.
- **Missing-context behavior:** False when no player distance context is available.
- **Compatibility Notes:** Implemented validator checks range shape on enforced object types.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:player_distance_from_spawn", "parameters": { "min": 128, "max": 1500 } }
```

#### `player_count_online`
- **Category:** `Player`
- **Purpose:** Matches online player-count range.
- **Valid scopes:** `world`, `invasion`, `event_context`
- **Parameters:** `min` (`integer`, optional), `max` (`integer`, optional)
- **Defaults / Notes:** Range bounds are inclusive.
- **Missing-context behavior:** False when player-count provider is unavailable.
- **Compatibility Notes:** Current implemented evaluator uses integer online count from server snapshot.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:player_count_online", "parameters": { "min": 2 } }
```

#### `player_health_range`
- **Category:** `Player`
- **Purpose:** Matches player health range.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `min` (`number`, optional), `max` (`number`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when player health context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:player_health_range", "parameters": { "max": 8.0 } }
```

#### `player_armor_range`
- **Category:** `Player`
- **Purpose:** Matches total player armor range.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `min` (`number`, optional), `max` (`number`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when player armor context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:player_armor_range", "parameters": { "min": 10 } }
```

#### `player_tag`
- **Category:** `Player`
- **Purpose:** Matches player tag membership.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `tag` (`string`, required)
- **Defaults / Notes:** Tag source can be scoreboard/team/datapack tag model per runtime implementation.
- **Missing-context behavior:** False when player tag context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:player_tag", "parameters": { "tag": "wa_hardcore" } }
```

#### `held_item`
- **Category:** `Player`
- **Purpose:** Matches held/offhand item filters.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `item` (`resource_location`, optional), `tag` (`item_tag`, optional), `slot` (`mainhand | offhand | either`, optional)
- **Defaults / Notes:** At least one item selector should be provided.
- **Missing-context behavior:** False when player inventory context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:held_item", "parameters": { "item": "minecraft:totem_of_undying" } }
```

#### `equipped_item`
- **Category:** `Player`
- **Purpose:** Matches equipped slot item filters.
- **Valid scopes:** `player`, `event_context`
- **Parameters:** `slot` (`head | chest | legs | feet | any`, required), `item` (`resource_location`, optional), `tag` (`item_tag`, optional)
- **Defaults / Notes:** `slot` is required to avoid ambiguous slot resolution.
- **Missing-context behavior:** False when equipment context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:equipped_item", "parameters": { "slot": "chest", "tag": "minecraft:chest_armor" } }
```

#### `effect_active`
- **Category:** `Player`
- **Purpose:** Matches active effect status and optional amplifier bounds.
- **Valid scopes:** `player`, `entity`, `event_context`
- **Parameters:** `effect` (`resource_location`, required), `min_amplifier` (`int`, optional), `max_amplifier` (`int`, optional)
- **Defaults / Notes:** Amplifier bounds are inclusive when present.
- **Missing-context behavior:** False when effect context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:effect_active", "parameters": { "effect": "minecraft:strength", "min_amplifier": 1 } }
```

### D. Entity

#### `entity_type`
- **Category:** `Entity`
- **Purpose:** Matches exact entity type ID.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** `entity` (`resource_location`, required)
- **Defaults / Notes:** Use canonical `parameters.entity`.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Trigger matcher uses the same canonical condition ID for entity selector checks.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_type", "parameters": { "entity": "minecraft:zombie" } }
```

#### `entity_tag`
- **Category:** `Entity`
- **Purpose:** Matches entity type tag membership.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** `tag` (`resource_location`, required)
- **Defaults / Notes:** `#`-prefixed and non-prefixed forms are commonly normalized by evaluators.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Use canonical tag IDs for deterministic matching.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_tag", "parameters": { "tag": "minecraft:raiders" } }
```

#### `entity_is_boss`
- **Category:** `Entity`
- **Purpose:** True when entity is classified as boss.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Canonical shared condition is planned.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Boss trigger matching currently uses trigger-only `boss_killed`; `entity_is_boss` remains a planned shared condition ID.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_is_boss", "parameters": {} }
```

#### `entity_not_boss`
- **Category:** `Entity`
- **Purpose:** True when entity exists and is not classified as boss.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Uses runtime boss classification providers.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Prefer explicit complementary use with `entity_is_boss` once both branches are fully runtime-supported.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_not_boss", "parameters": {} }
```

#### `entity_is_mutated`
- **Category:** `Entity`
- **Purpose:** True when entity carries World Awakened mutation state.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Requires mutation-state metadata on resolved entity snapshot.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Useful for downstream gates to avoid repeated mutation application.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_is_mutated", "parameters": {} }
```

#### `entity_health_range`
- **Category:** `Entity`
- **Purpose:** Matches entity health range.
- **Valid scopes:** `entity`, `event_context`
- **Parameters:** `min` (`number`, optional), `max` (`number`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when entity health context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_health_range", "parameters": { "max": 20.0 } }
```

#### `entity_on_fire`
- **Category:** `Entity`
- **Purpose:** True when entity burn state is active.
- **Valid scopes:** `entity`, `spawn_event`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Fire state should be evaluated from event snapshot when available.
- **Missing-context behavior:** False when entity context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_on_fire", "parameters": {} }
```

#### `entity_hostile`
- **Category:** `Entity`
- **Purpose:** True when entity resolves to hostile classification.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Classification source must be deterministic for the resolved context.
- **Missing-context behavior:** False when hostility context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_hostile", "parameters": {} }
```

#### `entity_passive`
- **Category:** `Entity`
- **Purpose:** True when entity resolves to passive classification.
- **Valid scopes:** `entity`, `spawn_event`, `loot`, `event_context`
- **Parameters:** none
- **Defaults / Notes:** Classification source must be deterministic for the resolved context.
- **Missing-context behavior:** False when passive classification context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:entity_passive", "parameters": {} }
```

### E. External / Integration / Config

#### `loaded_mod`
- **Category:** `External / integration / config`
- **Purpose:** True when a mod ID is present in loaded-mod set.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `mod` (`string`, required)
- **Defaults / Notes:** Use canonical `parameters.mod`.
- **Missing-context behavior:** False when loaded-mod provider is unavailable.
- **Compatibility Notes:** Mod ID comparisons should be normalized to lowercase.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:loaded_mod", "parameters": { "mod": "apotheosis" } }
```

#### `config_toggle_enabled`
- **Category:** `External / integration / config`
- **Purpose:** True when a config toggle path resolves to true.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `config_gate` (`dot.path`, required)
- **Defaults / Notes:** Use canonical `parameters.config_gate`.
- **Missing-context behavior:** False when toggle map is unavailable or key is missing.
- **Compatibility Notes:** Keep keys aligned with config contracts; unknown keys fail closed.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:config_toggle_enabled", "parameters": { "config_gate": "compat.apotheosis.enabled" } }
```

#### `apotheosis_world_tier_compare`
- **Category:** `External / integration / config`
- **Purpose:** Compares Apotheosis world tier against an operator/value pair.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `op` (`comparison operator`, required), `value` (`number`, required)
- **Defaults / Notes:** Canonical shared condition is defined but provider/runtime integration is not fully active.
- **Missing-context behavior:** False when integration/provider context is unavailable.
- **Compatibility Notes:** May emit integration-inactive diagnostics when Apotheosis compat is disabled for enforced validation paths.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:apotheosis_world_tier_compare", "parameters": { "op": ">=", "value": 3 } }
```

#### `external_scalar_range`
- **Category:** `External / integration / config`
- **Purpose:** Matches named external scalar against min/max bounds.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `scalar` (`string`, required), `min` (`number`, optional), `max` (`number`, optional)
- **Defaults / Notes:** At least one bound should be supplied.
- **Missing-context behavior:** False when scalar provider/key is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:external_scalar_range", "parameters": { "scalar": "pressure_tier", "min": 2 } }
```

#### `integration_active`
- **Category:** `External / integration / config`
- **Purpose:** True when named integration profile is active.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `integration` (`string or resource_location`, required)
- **Defaults / Notes:** Integration profile activation is runtime-owned and config-gated.
- **Missing-context behavior:** False when integration activation context is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:integration_active", "parameters": { "integration": "apotheosis" } }
```

### F. Random / Event Context

#### `random_chance`
- **Category:** `Random / event context`
- **Purpose:** Deterministic probabilistic gate for current evaluation snapshot.
- **Valid scopes:** `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `chance` (`0.0-1.0`, required by canonical contract)
- **Defaults / Notes:** Current runtime evaluator defaults missing chance to `1.0`.
- **Missing-context behavior:** False when deterministic roll context is unavailable.
- **Compatibility Notes:** Deterministic seed input comes from server-side event snapshot fields.
- **Status:** `implemented`
- **Example Snippet:**
```json
{ "type": "worldawakened:random_chance", "parameters": { "chance": 0.35 } }
```

#### `event_type`
- **Category:** `Random / event context`
- **Purpose:** Matches event type identifier in current event snapshot.
- **Valid scopes:** `spawn_event`, `loot`, `invasion`, `event_context`
- **Parameters:** `event` (`resource_location or string`, required)
- **Defaults / Notes:** Event IDs should be normalized and stable.
- **Missing-context behavior:** False when event-type field is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:event_type", "parameters": { "event": "worldawakened:entity_killed" } }
```

#### `recent_trigger`
- **Category:** `Random / event context`
- **Purpose:** Matches whether a trigger fired within a bounded recent window.
- **Valid scopes:** `world`, `player`, `event_context`
- **Parameters:** `trigger` (`resource_location`, required), `within_seconds` (`integer`, optional)
- **Defaults / Notes:** Window default should be explicitly defined by runtime when this condition is promoted.
- **Missing-context behavior:** False when recent-trigger cache is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:recent_trigger", "parameters": { "trigger": "my_pack:nether_entry", "within_seconds": 60 } }
```

#### `source_scope_match`
- **Category:** `Random / event context`
- **Purpose:** Matches source scope metadata of wrapped event context.
- **Valid scopes:** `event_context`
- **Parameters:** `scope` (`world | player | entity | spawn_event | loot | invasion`, required)
- **Defaults / Notes:** Scope strings must use canonical scope IDs.
- **Missing-context behavior:** False when source-scope metadata is unavailable.
- **Compatibility Notes:** Planned shared catalog entry; not fully runtime-active.
- **Status:** `planned`
- **Example Snippet:**
```json
{ "type": "worldawakened:source_scope_match", "parameters": { "scope": "player" } }
```

## 6. Per-Condition Entry Template

All condition entries in this file follow this format:
- `Condition ID`
- `Category`
- `Purpose`
- `Valid scopes`
- `Parameters`
- `Defaults / Notes`
- `Missing-context behavior`
- `Compatibility Notes`
- `Status`
- `Example Snippet`

## 7. Scope Requirements

Canonical scope IDs:
- `world`
- `player`
- `entity`
- `spawn_event`
- `loot`
- `invasion`
- `event_context`

Hard rules:
- Every condition must define legal scopes.
- Using a condition outside those scopes is a validation error in shared-contract validators.
- Scope legality is independent of owning object type.

Scope examples:
- `stage_unlocked` can be valid in `world`, `player`, `entity`, `spawn_event`, `loot`, `invasion`, and `event_context` when stage snapshot context exists.
- `player_distance_from_spawn` is invalid for pure `world` context with no player snapshot.
- `entity_type` is invalid when no entity context exists.
- `moon_phase` is valid where world-time context is available.

Current implementation coverage note:
- `rule.execution_scope` currently implements `world | player | entity | spawn_event`.
- `trigger_rule.source_scope` currently implements `world | player`.
- Other canonical scopes (`loot`, `invasion`, `event_context`) remain part of shared framework contracts for promoted/future systems.

## 8. Missing-Context Rules

Default rule:
- If a condition cannot evaluate because required context is unavailable, it returns `false`.

Examples:
- No player present for `player_distance_from_spawn` -> `false`.
- No entity present for `entity_type` -> `false`.
- No world-time context for `moon_phase` -> `false`.
- Integration inactive for `apotheosis_world_tier_compare` -> fail closed at evaluation and may also emit integration diagnostics in enforced validation paths.

## 9. Status Taxonomy Usage

Allowed status labels:
- `implemented`
- `planned`
- `reserved`
- `deprecated`

Meaning:
- `implemented`: valid for live runtime-authored content now.
- `planned`: designed and documented; not fully runtime-active yet.
- `reserved`: identifier/slot held; not valid for runtime-authored content.
- `deprecated`: compatibility-only path; should emit warnings and migration guidance where available.

This reference keeps statuses explicit and never treats `planned`/`reserved` conditions as active runtime behavior.

## 10. Example Composition Snippets

Simple leaf:

```json
{
  "type": "worldawakened:stage_unlocked",
  "parameters": { "stage": "my_pack:nether_opened" }
}
```

Wrapped `all_of`:

```json
{
  "type": "worldawakened:all_of",
  "parameters": {
    "conditions": [
      { "type": "worldawakened:stage_unlocked", "parameters": { "stage": "my_pack:nether_opened" } },
      { "type": "worldawakened:world_day_gte", "parameters": { "value": 20 } }
    ]
  }
}
```

Wrapped `any_of`:

```json
{
  "type": "worldawakened:any_of",
  "parameters": {
    "conditions": [
      { "type": "worldawakened:entity_type", "parameters": { "entity": "minecraft:zombie" } },
      { "type": "worldawakened:entity_tag", "parameters": { "tag": "minecraft:raiders" } }
    ]
  }
}
```

Wrapped `not`:

```json
{
  "type": "worldawakened:not",
  "parameters": {
    "condition": {
      "type": "worldawakened:entity_is_boss",
      "parameters": {}
    }
  }
}
```

## 11. Compatibility / Constraints Notes

Shared constraints:
- Invalid scope usage is an authoring/validation error.
- Integration-dependent conditions require active integration context and feature gates.
- Ambiguous selector payloads should be avoided; prefer explicit required keys.
- Deterministic random behavior must remain server-authoritative and context-seeded.
- Missing context is fail-closed by default.

Condition-specific caveats:
- `moon_phase` depends on world day/time context.
- `apotheosis_world_tier_compare` depends on compat availability and provider integration.
- `recent_trigger` depends on trigger-history cache/state.
- `random_chance` must remain deterministic per evaluation context.
- `structure_context` should include explicit `structure` to avoid ambiguous pass behavior.
- `invasion_active` currently evaluates active invasion state; profile filtering is planned refinement.

Runtime compatibility paths not yet promoted into canonical shared catalog:

| Type ID | Runtime acceptance path | Compatibility note | Runtime status |
| --- | --- | --- | --- |
| `advancement_completed` | Trigger matcher (`trigger_rules`) | Trigger-event-specific condition; not promoted in canonical shared catalog tables. | `implemented` (trigger-only) |
| `manual_trigger` | Trigger matcher (`trigger_rules`) | Manual trigger ID gate used by debug/manual trigger execution flow. | `implemented` (trigger-only) |
| `boss_killed` | Trigger matcher (`trigger_rules`) | Boss-flag match gate for boss-kill trigger contexts. | `implemented` (trigger-only) |

Authoring recommendation:
- Prefer canonical shared condition IDs in new datapack content.
- Use compatibility-only paths only when intentionally targeting current trigger/runtime compatibility behavior.

## Appendix A. Compact Matrix (Canonical Shared Catalog)

| Condition ID | Category | Valid scopes | Status |
| --- | --- | --- | --- |
| `stage_unlocked` | Progression/state | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `stage_locked` | Progression/state | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `ascension_reward_owned` | Progression/state | player, entity, spawn_event, event_context, invasion | implemented |
| `ascension_offer_pending` | Progression/state | player, event_context | implemented |
| `invasion_active` | Progression/state | world, invasion, event_context | implemented |
| `mutation_present` | Progression/state | entity, spawn_event, loot, event_context | planned |
| `rule_consumed` | Progression/state | world, player, entity, event_context | planned |
| `trigger_consumed` | Progression/state | world, player, event_context | planned |
| `current_dimension` | World/time/environment | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `current_biome` | World/time/environment | player, entity, spawn_event, loot, event_context | implemented |
| `world_day_gte` | World/time/environment | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `moon_phase` | World/time/environment | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `time_of_day_range` | World/time/environment | world, player, entity, spawn_event, loot, invasion, event_context | planned |
| `weather_state` | World/time/environment | world, player, entity, spawn_event, loot, invasion, event_context | planned |
| `structure_context` | World/time/environment | spawn_event, loot, event_context | implemented |
| `light_level_range` | World/time/environment | entity, spawn_event, event_context | planned |
| `player_distance_from_spawn` | Player | player, entity, spawn_event, event_context, invasion | implemented |
| `player_count_online` | Player | world, invasion, event_context | implemented |
| `player_health_range` | Player | player, event_context | planned |
| `player_armor_range` | Player | player, event_context | planned |
| `player_tag` | Player | player, event_context | planned |
| `held_item` | Player | player, event_context | planned |
| `equipped_item` | Player | player, event_context | planned |
| `effect_active` | Player | player, entity, event_context | planned |
| `entity_type` | Entity | entity, spawn_event, loot, event_context | implemented |
| `entity_tag` | Entity | entity, spawn_event, loot, event_context | implemented |
| `entity_is_boss` | Entity | entity, spawn_event, loot, event_context | planned |
| `entity_not_boss` | Entity | entity, spawn_event, loot, event_context | implemented |
| `entity_is_mutated` | Entity | entity, spawn_event, loot, event_context | implemented |
| `entity_health_range` | Entity | entity, event_context | planned |
| `entity_on_fire` | Entity | entity, spawn_event, event_context | planned |
| `entity_hostile` | Entity | entity, spawn_event, loot, event_context | planned |
| `entity_passive` | Entity | entity, spawn_event, loot, event_context | planned |
| `loaded_mod` | External/integration/config | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `config_toggle_enabled` | External/integration/config | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `apotheosis_world_tier_compare` | External/integration/config | world, player, entity, spawn_event, loot, invasion, event_context | planned |
| `external_scalar_range` | External/integration/config | world, player, entity, spawn_event, loot, invasion, event_context | planned |
| `integration_active` | External/integration/config | world, player, entity, spawn_event, loot, invasion, event_context | planned |
| `random_chance` | Random/event context | world, player, entity, spawn_event, loot, invasion, event_context | implemented |
| `event_type` | Random/event context | spawn_event, loot, invasion, event_context | planned |
| `recent_trigger` | Random/event context | world, player, event_context | planned |
| `source_scope_match` | Random/event context | event_context | planned |
| `all_of` | Logical wrapper | compositional wrapper node | planned |
| `any_of` | Logical wrapper | compositional wrapper node | planned |
| `not` | Logical wrapper | compositional wrapper node | planned |

## 12. Contributor Update Rules

Update this file whenever:
- a condition is added, removed, renamed, deprecated, or has parameter-shape changes
- scope legality changes
- missing-context behavior changes
- status changes (`implemented/planned/reserved/deprecated`)

Maintenance expectations:
- Keep examples aligned with real validators/codecs and runtime behavior.
- Do not mark planned conditions as implemented without runtime support.
- Keep scope legality aligned with `docs/SCOPE_MATRIX.md` and `docs/SPECIFICATION.md`.
- Keep shared condition contracts aligned with `docs/SPECIFICATION.md` and datapack-facing notes in `docs/DATAPACK_AUTHORING.md`.

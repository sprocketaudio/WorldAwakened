# World Awakened Datapack Authoring Guide

Datapack format and content contract for user-created progression/content packs.

- Document status: Draft for implementation
- Last updated: 2026-03-13
- Applies to: Minecraft 1.21.1 + NeoForge + World Awakened (`worldawakened`)

---

## 0. Governance and Maintenance

This file is the canonical datapack shape and field contract for World Awakened content.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever datapack schemas, object fields, reference rules, validation outcomes, or authoring examples change.
- Keep this file aligned with shared condition/action/scope/component contracts.

---

## 1. Scope

This document defines:
- how to structure a World Awakened-compatible datapack
- required and optional JSON fields per object type
- reference rules between objects
- validation/error behavior expectations
- practical examples for pack authors

This is a format/contract guide. It does not define Java internals.
For the canonical mutation/ascension component catalog and per-component parameter/status details, see [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md).
For the web authoring companion behavior and UX contract, see [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md).
Shared framework contracts for scopes, conditions, actions, component composition, and status taxonomy are defined in [SPECIFICATION.md](SPECIFICATION.md) Sections `3B`-`3F`.

---

## 2. Compatibility and Assumptions

- Target game version: Minecraft `1.21.1`
- Target mod loader stack: NeoForge + World Awakened
- Datapacks must be valid JSON (no comments, no trailing commas)
- IDs are `ResourceLocation` values (`namespace:path`)
- Invalid required fields should fail validation for that object

Unknown field handling:
- unknown top-level object fields should be ignored with a warning by default
- deeply typed or codec-validated branches may reject unknown fields during validation
- unknown fields must never silently change runtime behavior

Localization fallback behavior:
- translation key components may be used anywhere the schema accepts text components
- if a translation key is missing, the raw key should be displayed
- raw strings remain valid without localization
- datapacks should prefer translation keys for portability across languages

Note on `pack.mcmeta`:
- Use the data pack format value required by Minecraft `1.21.1`.
- If the value is wrong, Minecraft will reject the pack before World Awakened data loads.

---

## 2A. Web Authoring Tool Interop Contract

World Awakened v1 includes a browser-based authoring companion.

Interop rules:
- there is one shared datapack format across manual JSON authoring and web-tool authoring
- tool exports must remain canonical World Awakened datapack JSON, not a proprietary intermediate format
- deterministic export ordering is recommended to keep diffs stable
- import workflows should preserve IDs and references unless intentionally edited
- schema-version mismatches should surface as migration warnings or errors with actionable diagnostics

Canonical export layout must remain:

```text
data/<namespace>/
  stages/
  trigger_rules/
  rules/
  mob_mutators/
  mutation_pools/
  ascension_rewards/
  ascension_offers/
  loot_profiles/
  invasion_profiles/
  integration_profiles/
```

---

## 3. Pack Layout

Recommended folder structure:

```text
<your_pack>/
  pack.mcmeta
  data/
    <namespace>/
      stages/
        *.json
      trigger_rules/
        *.json
      rules/
        *.json
      ascension_rewards/
        *.json
      ascension_offers/
        *.json
      mob_mutators/
        *.json
      mutation_pools/
        *.json
      loot_profiles/
        *.json
      invasion_profiles/
        *.json
      integration_profiles/
        *.json
      maps/                         # optional data maps
        entity_boss_flags/
          *.json
        entity_mutation_defaults/
          *.json
        item_metadata/
          *.json
        structure_loot_hints/
          *.json
```

Path rule:
- Use `data/<namespace>/<object_type>/...` directly.
- Do not add a duplicate namespace folder layer under `data/<namespace>/`.

---

## 4. ID and Reference Rules

### 4.1 Object Identity

- Every World Awakened object must include `id`.
- `id` should match the file path identity for clarity.
  - Example: `data/my_pack/stages/baseline.json` -> `my_pack:baseline`
- IDs must be unique per object type set.

### 4.2 Cross-Object References

Common reference fields:
- stage refs: `stage`, `stage_filters`, `required_stages`
- ascension reward refs: `reward`, `candidate_rewards`
- ascension offer refs: `offer`
- mutator refs: `mutators[]`
- pool refs: `mutator_pool_refs[]`
- loot refs: `reward_profile` or loot profile IDs
- integration refs: `mod_id`, integration condition flags

Reference rules:
- unresolved references are validation errors
- broken referencing object should be disabled
- unrelated valid objects should continue to load

### 4.3 Namespace Guidance

- Use your own namespace for pack content (`my_pack:*`)
- Refer to other packs/mod IDs only when intentionally integrating
- Avoid using `worldawakened:*` for user content unless instructed by upstream docs

---

## 4A. Generic Modded Boss and Mob Support

World Awakened is designed so many future boss and mob mods can be supported without dedicated compat code.

Pack authors can target modded entities through:
- direct entity IDs
- entity tags
- boss flag maps
- generic trigger, rule, mutator, loot, and invasion references

Use dedicated compat only when a mod exposes important systems that generic entity-based targeting cannot reach.

Examples:
- unlock a stage when `somebossmod:ancient_tyrant` dies
- exclude `othermod:void_beast` from mutation
- mark a modded entity as a boss through `maps/entity_boss_flags`
- add modded mobs to invasion compositions by entity ID

---

## 5. Shared Patterns

### 5.1 `enabled` Convention

Most objects support:

```json
{
  "enabled": true
}
```

If `enabled` is false, object should load but remain inactive.

### 5.2 Condition Objects

All condition arrays use the shared canonical condition node:

```json
{
  "type": "worldawakened:stage_unlocked",
  "parameters": {
    "stage": "my_pack:nether_opened"
  },
  "enabled": true,
  "debug_label": "nether gate opened"
}
```

Canonical rules:
- use `type` + `parameters` for all leaf conditions
- unknown `type` is a validation error
- missing context evaluates `false` unless the condition entry explicitly documents another behavior
- condition semantics come from the shared catalog in `docs/SPECIFICATION.md` Section `3C`
- conditions must validate independently of owning object type (`rules`, `trigger_rules`, `mob_mutators`, `ascension_rewards`, etc.)
- condition scope legality is enforced by the shared scope model (`docs/SPECIFICATION.md` Section `3B`)

Logical wrappers are also condition nodes:

```json
{
  "type": "worldawakened:all_of",
  "parameters": {
    "conditions": [
      {
        "type": "worldawakened:stage_unlocked",
        "parameters": { "stage": "my_pack:nether_opened" }
      },
      {
        "type": "worldawakened:world_day_gte",
        "parameters": { "value": 20 }
      }
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

Validation notes:
- unsupported condition `type`
- invalid/missing `parameters` object
- illegal use outside allowed scopes
- invalid wrapper payload shape (`all_of`/`any_of`/`not`)

### 5.2A Optional World-Context Conditions

World-context conditions are optional shared conditions, not primary progression systems.

Design rules:
- optional and datapack-driven
- not primary progression systems
- intended for custom scaling and gating logic authored by pack makers
- if the runtime context cannot evaluate a world-context condition, it evaluates false

Supported optional world-context conditions:

`worldawakened:world_day_gte`

```json
{
  "type": "worldawakened:world_day_gte",
  "parameters": {
    "value": 20
  }
}
```

- true when world day is greater than or equal to `value`
- `value` should be a non-negative integer

`worldawakened:player_distance_from_spawn`

```json
{
  "type": "worldawakened:player_distance_from_spawn",
  "parameters": {
    "min": 128,
    "max": 1500
  }
}
```

- true when player distance from world spawn is within the configured inclusive range
- specify at least one of `min` or `max`
- if both are present, `min` should be less than or equal to `max`
- contexts without a resolvable player should evaluate this condition as false

### 5.3 Action Objects

All action arrays use the shared canonical action node:

```json
{
  "type": "worldawakened:unlock_stage",
  "parameters": {
    "stage": "my_pack:end_reached"
  },
  "enabled": true,
  "priority": 100,
  "debug_label": "unlock final stage"
}
```

```json
{
  "type": "worldawakened:apply_mutator_pool",
  "parameters": {
    "pool": "my_pack:overworld_night_t2"
  }
}
```

```json
{
  "type": "worldawakened:grant_ascension_offer",
  "parameters": {
    "offer": "my_pack:nether_ascension_1"
  }
}
```

Validation note:
- unknown action `type` is a validation error
- missing/invalid `parameters` object is a validation error
- action scope legality must match the shared scope model (`docs/SPECIFICATION.md` Section `3B`)
- action status semantics use the shared taxonomy (`implemented`, `planned`, `reserved`, `deprecated`)

### 5.3A Shared Scope and Status Contracts

Scope fields in object schemas must use shared canonical scope IDs:
- `world`
- `player`
- `entity`
- `spawn_event`
- `loot`
- `invasion`
- `event_context`

Status semantics in documentation/tooling/validation must use shared labels:
- `implemented` (allowed)
- `planned` (warning or error by validation mode)
- `reserved` (error)
- `deprecated` (warning with migration guidance when available)

### 5.4 Config and Mod Gates

Use explicit gates to keep packs portable:

```json
{
  "config_gate": "compat.apotheosis.enabled",
  "mod_conditions": [
    { "mod_id": "apotheosis", "required": true }
  ]
}
```

### 5.5 `schema_version`

All World Awakened datapack objects may optionally include:

```json
{
  "schema_version": 1
}
```

Rules:
- missing `schema_version` defaults to `1`
- unsupported or incompatible schema versions should fail validation for that object
- schema version errors should disable the object, not crash the mod

### 5.6 Selector Semantics

Selectors are used by rules, mutators, triggers, and other filters that target entities.

Supported positive selector types:
- explicit entity ID
- entity tag
- namespace wildcard
- mob category
- boss flag map
- default eligibility maps

Supported exclusion selector types:
- explicit entity ID blacklist
- entity tag blacklist

Matching precedence:
1. explicit entity ID
2. explicit entity tag
3. namespace wildcard
4. mob category
5. boss flag map
6. default eligibility maps

Combination rules:
- positive matches are ORed together
- exclusions are applied as AND-style rejections
- blacklist matches always win over positive matches

Interpretation pattern:

```text
(positive match by ID or tag or wildcard or category) AND NOT excluded
```

Example:

```text
eligible_entities:
- minecraft:zombie
- minecraft:skeleton

eligible_entity_tags:
- #minecraft:raiders

excluded_entities:
- minecraft:warden

excluded_entity_tags:
- #worldawakened:bosses
```

This means:

```text
(entity match OR tag match) AND NOT excluded
```

Selectors should compile into cached matchers during datapack reload.

---

### 5.7 Runtime Evaluation Expectations

Pack authors should assume World Awakened event handling follows a snapshot-based, single-pass model in v1.

Canonical candidate ordering:
1. enabled
2. config gate
3. integration gate
4. scope/context availability check
5. selector/entity/context match
6. shared condition evaluation
7. priority
8. cooldown
9. one-shot
10. chance
11. enqueue actions
12. bounded action queue execution
13. post-actions consumed/cooldown writes

Shared action execution guarantees:
- action queue execution is bounded
- recursion/re-entry protections prevent unbounded self-triggering
- idempotent actions must be safe when reevaluated
- non-idempotent actions require explicit repeat guards by design
- invalid actions disable owning objects (or branches) without taking down unrelated systems

Condition guarantees:
- missing required context evaluates `false` (fail-closed) unless the condition explicitly documents another behavior
- condition semantics are shared across object types; `rules` and `trigger_rules` do not redefine meaning per type

Component composition guarantees:
- component ordering is deterministic (authored order + priority semantics)
- duplicate/conflict behavior is explicit via shared composition metadata
- impossible component compositions fail validation before runtime
- canonical duplicate/conflict/order/budget/no-op resolution follows [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)

Performance guarantees:
- rule evaluation must inspect only the active scope bucket (`O(bucket_size)`)
- spawn events must not trigger full rule-set scans (`O(total_rules)`)
- hot-path rules/selectors/actions must run from reload-compiled structures
- canonical limits and guardrails follow [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)

Status-mode guarantees:
- `implemented`: allowed
- `planned`: warning or error based on active validation mode
- `reserved`: rejected
- `deprecated`: warning with migration note when available

Implications:
- chance rolls happen only after a candidate is fully eligible
- cooldown failures do not consume chance rolls
- one-shot consumed state blocks chance rolls
- in v1, stage unlocks from the current event affect later events, not the current pass
- in v1, ascension offers granted by the current event become pending after the pass resolves
- broken objects should disable only themselves or their dependent objects instead of breaking unrelated World Awakened content

---

### 5.8 Default Value Notation

In the object sections below:
- `required` means the field should be treated as a validation error if absent
- `absent` means the field may be omitted and no value is assumed beyond "not set"
- empty arrays or objects mean the field defaults to an empty value of that type

---

### 5.9 Difficulty Modifier Note (Config/Command Controlled)

Global difficulty modifier and optional challenge modifier settings are not authored as datapack objects in v1.

Authoring implications:
- datapacks should continue to define progression content, rules, mutators, loot profiles, and invasions as normal
- effective numeric intensity may be scaled by server config and commands
- these modifiers do not change datapack stage IDs, stage unlock state, or trigger eligibility
- pack authors should avoid encoding assumptions that depend on a single fixed scalar value

Operational model:
- operator/admin config controls baseline global difficulty modifier
- optional challenge modifier policies and scope are server-controlled
- World Awakened applies modifiers only to World Awakened-owned numeric difficulty outputs

---

### 5.10 Performance Budget Guardrails

World Awakened enforces hot-path performance guardrails for rules and spawn mutation evaluation.

Recommended limits:
- `maximum_rules_per_bucket`: `500`
- `maximum_rules_evaluated_per_event`: `50`
- `maximum_actions_per_rule`: `10`
- `max_mutators_per_spawn`: `8`
- `max_components_per_mutator`: `10`
- `max_action_chain_depth`: `1` (single-pass)

Authoring implications:
- keep rule sets distributed by scope to avoid oversized hot buckets
- avoid giant action lists on one rule
- keep mutator definitions and component stacks bounded
- treat performance-limit diagnostics as balancing/architecture failures, not cosmetic warnings

Canonical contract:
- full semantics and diagnostics expectations are defined in [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)

---

## 6. Object Type: `stages`

Path:
- `data/<namespace>/stages/*.json`

Minimum required fields:
- `id`
- `display_name`

Recommended full shape:

```json
{
  "schema_version": 1,
  "id": "my_pack:baseline",
  "aliases": ["my_pack:starter"],
  "display_name": { "translate": "stage.my_pack.baseline" },
  "short_name": "Base",
  "description": { "translate": "stage.my_pack.baseline.desc" },
  "icon": { "item": "minecraft:iron_sword" },
  "sort_index": 0,
  "visible_to_players": true,
  "enabled": true,
  "tags": ["progression:core", "tier:0"],
  "style": { "color": "green", "bold": false },
  "progression_group": "mainline",
  "unlock_policy": "cumulative",
  "default_unlocked": true
}
```

Field notes:
- `display_name`: string or text component object
- `unlock_policy`: `cumulative | exclusive_group | replace_group`
- `default_unlocked`: useful for baseline stage
- `aliases`: optional legacy stage IDs used for save migration after renames

Field defaults:
- `schema_version`: `1`
- `id`: required
- `aliases`: `[]`
- `display_name`: required
- `short_name`: absent
- `description`: absent
- `icon`: absent
- `sort_index`: `0`
- `visible_to_players`: `true`
- `enabled`: `true`
- `tags`: `[]`
- `style`: absent
- `progression_group`: absent
- `unlock_policy`: `cumulative`
- `default_unlocked`: `false`

---

## 7. Object Type: `trigger_rules`

Path:
- `data/<namespace>/trigger_rules/*.json`

Minimum required fields:
- `id`
- `trigger_type`
- `actions`

Example:

```json
{
  "id": "my_pack:unlock_nether_on_entry",
  "enabled": true,
  "priority": 100,
  "trigger_type": "worldawakened:player_enter_dimension",
  "source_scope": "world",
  "conditions": [
    {
      "type": "worldawakened:current_dimension",
      "parameters": {
        "dimension": "minecraft:the_nether"
      }
    },
    {
      "type": "worldawakened:stage_locked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "actions": [
    {
      "type": "worldawakened:unlock_stage",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    },
    {
      "type": "worldawakened:send_warning_message",
      "parameters": {
        "message": { "translate": "worldawakened.stage.nether_opened" }
      }
    }
  ],
  "cooldown": { "seconds": 5 },
  "one_shot": true
}
```

Common `trigger_type` values:
- `worldawakened:player_enter_dimension`
- `worldawakened:advancement_completed`
- `worldawakened:entity_killed`
- `worldawakened:boss_killed`
- `worldawakened:item_crafted`
- `worldawakened:block_placed`
- `worldawakened:block_broken`
- `worldawakened:manual_debug`
- `worldawakened:apotheosis_tier_threshold`

Common trigger `actions[].type` values currently implemented:
- `worldawakened:unlock_stage`
- `worldawakened:lock_stage`
- `worldawakened:emit_named_event`
- `worldawakened:increment_counter`
- `worldawakened:send_warning_message`
- `worldawakened:grant_ascension_offer`

Shared contract notes:
- `conditions[]` must use shared canonical condition nodes (`type`, `parameters`, optional `enabled`, optional `debug_label`)
- `actions[]` must use shared canonical action nodes (`type`, `parameters`, optional `enabled`, optional `priority`, optional `debug_label`)
- `source_scope` uses shared scope semantics; `trigger_rules` currently support the v1 subset `world | player`

Manual debug flow:
- use `trigger_type = worldawakened:manual_debug`
- execute with `/wa trigger fire <trigger_rule_id>`

Note:
- world-context checks like world day and player distance from spawn should be expressed in `rules.conditions`, not as core trigger types

Field defaults:
- `schema_version`: `1`
- `id`: required
- `enabled`: `true`
- `priority`: `0`
- `trigger_type`: required
- `source_scope`: `world`
- `conditions`: `[]`
- `actions`: required
- `cooldown`: absent
- `one_shot`: `false`

---

## 8. Object Type: `rules`

Path:
- `data/<namespace>/rules/*.json`

Minimum required fields:
- `id`
- `conditions`
- `actions`

Example:

```json
{
  "id": "my_pack:night_pressure_stage2",
  "enabled": true,
  "priority": 50,
  "execution_scope": "spawn_event",
  "conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    },
    {
      "type": "worldawakened:moon_phase",
      "parameters": {
        "phases": ["full_moon", "waning_gibbous"]
      }
    },
    {
      "type": "worldawakened:random_chance",
      "parameters": {
        "chance": 0.35
      }
    }
  ],
  "actions": [
    {
      "type": "worldawakened:apply_mutator_pool",
      "parameters": {
        "pool": "my_pack:overworld_night_t2"
      }
    },
    {
      "type": "worldawakened:set_world_scalar",
      "parameters": {
        "key": "spawn_pressure",
        "op": "multiply",
        "value": 1.15
      }
    }
  ],
  "weight": 1.0,
  "chance": 1.0,
  "cooldown": { "seconds": 30 },
  "tags": ["spawning", "night"]
}
```

Field defaults:
- `schema_version`: `1`
- `id`: required
- `enabled`: `true`
- `priority`: `0`
- `conditions`: required
- `actions`: required
- `weight`: `1.0`
- `chance`: `1.0`
- `cooldown`: absent
- `execution_scope`: `world`
- `tags`: `[]`

Shared contract notes:
- rule condition/action meaning comes from shared catalogs; do not redefine semantics per file
- each condition/action must be legal for the selected `execution_scope`
- status taxonomy is shared across runtime, docs, validation output, and web tooling

---

## 9. Object Type: `mob_mutators`

Path:
- `data/<namespace>/mob_mutators/*.json`

Minimum required fields:
- `id`
- `display_name`
- `components`
- `weight`

Example:

```json
{
  "id": "my_pack:berserker_t1",
  "display_name": { "translate": "mutator.my_pack.berserker_t1" },
  "enabled": true,
  "rarity": "uncommon",
  "weight": 10,
  "stacking_group": "offense",
  "exclusive_with": ["my_pack:juggernaut_t1"],
  "max_stack_count": 1,
  "eligible_entities": [
    "minecraft:zombie",
    "minecraft:husk",
    "minecraft:drowned"
  ],
  "eligible_entity_tags": ["minecraft:skeletons"],
  "excluded_entities": ["minecraft:wither"],
  "excluded_entity_tags": ["c:bosses"],
  "required_conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:baseline"
      }
    }
  ],
  "components": [
    {
      "type": "worldawakened:max_health_multiplier",
      "priority": 100,
      "parameters": { "multiplier": 1.3 },
      "conditions": [],
      "conflicts_with": []
    },
    {
      "type": "worldawakened:attack_damage_multiplier",
      "parameters": { "multiplier": 1.25 }
    },
    {
      "type": "worldawakened:movement_speed_bonus",
      "parameters": { "amount": 0.1 }
    }
  ],
  "component_budget": 5,
  "reward_modifier": {
    "loot_bonus_chance": 0.08,
    "xp_multiplier": 1.2
  },
  "visuals": {
    "nameplate_style": "aggressive",
    "glow": false
  },
  "sounds": {
    "spawn": "minecraft:entity.zombie.ambient"
  },
  "applies_to_bosses": false,
  "applies_to_invaders": true
}
```

Authoring model:
- the mutator object ID (for example `my_pack:berserker_t1`) is the authored mutation definition identity
- `components[]` defines the behavior composition
- Java defines what each component type does
- optional example/default packs distributed with World Awakened use the same component model as user packs
- addon mods may register additional mutation component types in future extension APIs; unknown types fail validation when unavailable
- shared composition semantics apply (`conflicts_with`, `stacking_group`, `duplicate_policy`, `max_instances`, `composition_priority`, companion requirements)
- incompatible component compositions fail validation; runtime does not silently improvise stacking behavior
- mutator components that require entity capabilities/hooks/runtime surfaces unavailable in the current modpack fail closed branch-only with diagnostics; they must not rewrite foreign entity state as fallback
- compat-sensitive components should document required optional runtime surfaces (for example extra slot systems, custom combat hooks, boss-runtime metadata, custom attributes, or visual channels) so operators can gate content per modpack
- missing mutator definitions referenced by persisted provenance remain inspectable and non-fatal; World Awakened does not auto-substitute another definition
- use `/wa mob inspect` during testing to verify resolved mutator branches, failed-closed component branches, and missing-definition provenance state

Example mutation component type IDs currently registered by core:
- `worldawakened:max_health_bonus`
- `worldawakened:max_health_multiplier`
- `worldawakened:attack_damage_bonus`
- `worldawakened:attack_damage_multiplier`
- `worldawakened:armor_bonus`
- `worldawakened:armor_multiplier`
- `worldawakened:reinforcement_summon`
- `worldawakened:summon_cooldown`
- `worldawakened:summon_cap`
- `worldawakened:fire_package`
- `worldawakened:frost_package`
- `worldawakened:lightning_package`
- `worldawakened:poison_package`

Field defaults:
- `schema_version`: `1`
- `id`: required
- `display_name`: required
- `enabled`: `true`
- `components`: required (must resolve to at least one valid enabled component)
- `rarity`: `common`
- `weight`: required
- `stacking_group`: absent
- `exclusive_with`: `[]`
- `max_stack_count`: `1`
- `eligible_entities`: `[]`
- `eligible_entity_tags`: `[]`
- `excluded_entities`: `[]`
- `excluded_entity_tags`: `[]`
- `required_conditions`: `[]`
- `component_budget`: absent
- `reward_modifier`: `{}`
- `visuals`: `{}`
- `sounds`: `{}`
- `applies_to_bosses`: `false`
- `applies_to_invaders`: `false`

---

## 10. Object Type: `mutation_pools`

Path:
- `data/<namespace>/mutation_pools/*.json`

Minimum required fields:
- `id`
- `mutators`

Example:

```json
{
  "id": "my_pack:overworld_night_t2",
  "enabled": true,
  "weight": 20,
  "conditions": [
    {
      "type": "worldawakened:current_dimension",
      "parameters": {
        "dimension": "minecraft:overworld"
      }
    },
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "stage_filters": {
    "all_of": ["my_pack:baseline", "my_pack:nether_opened"]
  },
  "apotheosis_tier_filters": {
    "min": 2
  },
  "eligible_dimensions": ["minecraft:overworld"],
  "eligible_biomes": ["minecraft:plains", "minecraft:forest"],
  "eligible_entities": ["minecraft:zombie", "minecraft:skeleton"],
  "mutators": [
    { "id": "my_pack:berserker_t1", "weight": 10 },
    { "id": "my_pack:shielded_t1", "weight": 6 }
  ],
  "max_mutators_per_entity": 2,
  "reroll_policy": "single_retry"
}
```

Field defaults:
- `schema_version`: `1`
- `id`: required
- `enabled`: `true`
- `weight`: `1`
- `conditions`: `[]`
- `stage_filters`: absent
- `apotheosis_tier_filters`: absent
- `eligible_dimensions`: `[]`
- `eligible_biomes`: `[]`
- `eligible_entities`: `[]`
- `mutators`: required
- `max_mutators_per_entity`: inherit global `mutators.max_mutators_per_mob`
- `reroll_policy`: `none`

---

## 11. Object Type: `loot_profiles`

Path:
- `data/<namespace>/loot_profiles/*.json`

Minimum required fields:
- `id`
- `target_loot_tables`
- `replace_mode`
- `entries`

Example:

```json
{
  "id": "my_pack:nether_chest_upgrade",
  "enabled": true,
  "target_loot_tables": [
    "minecraft:chests/nether_bridge",
    "minecraft:chests/bastion_treasure"
  ],
  "conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "stage_filters": {
    "any_of": ["my_pack:nether_opened", "my_pack:end_reached"]
  },
  "apotheosis_tier_filters": {
    "min": 1
  },
  "replace_mode": "add_bonus_pool",
  "entries": [
    {
      "type": "item",
      "item": "minecraft:golden_apple",
      "weight": 6,
      "min": 1,
      "max": 2
    },
    {
      "type": "item",
      "item": "minecraft:blaze_rod",
      "weight": 10,
      "min": 2,
      "max": 5
    }
  ],
  "weight_multiplier": 1.1,
  "quality_scalar": 1.0,
  "config_gate": "loot.enable_loot_evolution",
  "mod_conditions": []
}
```

Allowed `replace_mode` values:
- `inject`
- `replace_entries`
- `remove_entries`
- `add_bonus_pool`

Compatibility note:
- when Apotheosis compat is active and a target loot table is Apotheosis-sensitive, use additive modes (`inject`, `add_bonus_pool`)
- destructive modes (`replace_entries`, `remove_entries`) are restricted on Apotheosis-sensitive targets and may be blocked, downgraded, or disabled by validation policy
- optional example/default loot profiles may be distributed by World Awakened as datapack-authored presets; user-authored profiles are first-class and not secondary to shipped example-pack names

Field defaults:
- `schema_version`: `1`
- `id`: required
- `enabled`: `true`
- `target_loot_tables`: required
- `conditions`: `[]`
- `apotheosis_tier_filters`: absent
- `stage_filters`: absent
- `replace_mode`: `inject`
- `entries`: required
- `weight_multiplier`: `1.0`
- `quality_scalar`: `1.0`
- `config_gate`: absent
- `mod_conditions`: `[]`

---

## 11A. Apotheosis Loot Compatibility for `loot_profiles`

When Apotheosis integration is active, treat Apotheosis world-tier loot behavior as authoritative on Apotheosis-sensitive targets.

Pack-author guidance:
- compose with Apotheosis, do not override it
- prefer `inject` and `add_bonus_pool`
- avoid `replace_entries` and `remove_entries` on Apotheosis-sensitive targets
- if you need tier-aware rewards, scale only World Awakened-owned injected entries with tier filters or compatible scalar inputs

Apotheosis-sensitive targets include:
- targets known to be modified by Apotheosis world-tier loot logic
- targets containing Apotheosis tier-gated outcomes
- targets identified through compat metadata, including integration profile `loot_targets` for Apotheosis when used for sensitive-target marking

If an unsafe mode is used on an Apotheosis-sensitive target:
- validation should emit structured warnings or errors
- the profile operation should be blocked, downgraded to additive behavior, or disabled according to validation policy
- World Awakened should never silently erase Apotheosis tier-gated loot behavior
- server policy may be configured through `compat.apotheosis.loot_unsafe_mode_policy`

Recommended diagnostics:
- `WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED`
- `WA_APOTHEOSIS_LOOT_MODE_UNSAFE`
- `WA_APOTHEOSIS_LOOT_TARGET_SENSITIVE`

Preferred fallback order:
1. block unsafe operation and log it
2. preserve Apotheosis behavior unchanged
3. apply only safe additive World Awakened rewards when possible
4. otherwise disable the offending profile branch

---

## 12. Object Type: `invasion_profiles`

Path:
- `data/<namespace>/invasion_profiles/*.json`

Minimum required fields:
- `id`
- `display_name`
- `trigger_mode`
- `wave_count`
- `spawn_composition`

Example:

```json
{
  "id": "my_pack:overworld_siege_t1",
  "display_name": { "translate": "invasion.my_pack.overworld_siege_t1" },
  "enabled": true,
  "trigger_mode": "random_periodic",
  "conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "stage_filters": {
    "all_of": ["my_pack:baseline", "my_pack:nether_opened"]
  },
  "apotheosis_tier_filters": {
    "min": 1
  },
  "dimensions": ["minecraft:overworld"],
  "biome_filters": ["minecraft:plains", "minecraft:savanna"],
  "min_players": 1,
  "cooldown": { "minutes": 90 },
  "warning_time": { "seconds": 20 },
  "wave_count": 4,
  "wave_interval": { "seconds": 45 },
  "spawn_budget": 30,
  "spawn_composition": [
    { "entity": "minecraft:zombie", "weight": 10, "cost": 1 },
    { "entity": "minecraft:skeleton", "weight": 8, "cost": 1 },
    { "entity": "minecraft:spider", "weight": 4, "cost": 1 }
  ],
  "elite_chance": 0.15,
  "mutator_pool_refs": ["my_pack:overworld_night_t2"],
  "reward_profile": "my_pack:invasion_t1_rewards",
  "boss_wave": null,
  "max_active_entities": 60,
  "safe_zone_rules": {
    "respect_spawn_protection": true
  }
}
```

Allowed `trigger_mode` values:
- `random_periodic`
- `stage_unlock_reaction`
- `boss_retaliation`
- `structure_proximity`
- `command_forced`
- `time_based`
- `apotheosis_tier_threshold`

Authoring note:
- optional example/default invasion profiles may be distributed by World Awakened as datapack-authored presets
- user-authored invasion profiles are first-class definitions and should not rely on code-level preset names

Field defaults:
- `schema_version`: `1`
- `id`: required
- `display_name`: required
- `enabled`: `true`
- `trigger_mode`: required
- `conditions`: `[]`
- `stage_filters`: absent
- `apotheosis_tier_filters`: absent
- `dimensions`: `[]`
- `biome_filters`: `[]`
- `min_players`: `1`
- `cooldown`: inherit global invasion cooldown if omitted
- `warning_time`: inherit global warning setting if omitted
- `wave_count`: required
- `wave_interval`: required
- `spawn_budget`: required
- `spawn_composition`: required
- `elite_chance`: `0.0`
- `mutator_pool_refs`: `[]`
- `reward_profile`: absent
- `boss_wave`: absent
- `max_active_entities`: defaults to `spawn_budget` if omitted
- `safe_zone_rules`: `{}`

---

## 13. Object Type: `integration_profiles`

Path:
- `data/<namespace>/integration_profiles/*.json`

Minimum required fields:
- `mod_id`
- `config_key`

Example (Apotheosis):

```json
{
  "mod_id": "apotheosis",
  "display_name": "Apotheosis",
  "enabled_by_default": true,
  "config_key": "compat.apotheosis.enabled",
  "stage_hooks": [
    {
      "type": "worldawakened:tier_threshold_unlock",
      "parameters": {
        "tier": 2,
        "unlock_stage": "my_pack:apoth_tier_2"
      }
    }
  ],
  "trigger_hooks": [
    "worldawakened:apotheosis_tier_threshold"
  ],
  "entity_tags": ["c:bosses"],
  "loot_targets": [
    "minecraft:chests/end_city_treasure"
  ],
  "special_conditions": [
    {
      "type": "worldawakened:apotheosis_world_tier_compare",
      "parameters": {
        "op": ">=",
        "value": 2
      }
    }
  ],
  "notes": "Enable tier-based scaling and stage unlocks."
}
```

Activation still requires runtime conditions:
- mod loaded
- config enabled
- profile enabled

Design guidance:
- do not create an integration profile just to reference a modded mob or boss by entity ID
- use integration profiles when you need mod-level toggles, custom hooks, special APIs, or non-standard progression data

Field defaults:
- `schema_version`: `1`
- `mod_id`: required
- `display_name`: defaults to `mod_id` when omitted
- `enabled_by_default`: `true`
- `config_key`: required
- `stage_hooks`: `[]`
- `trigger_hooks`: `[]`
- `entity_tags`: `[]`
- `boss_tags`: `[]`
- `loot_targets`: `[]`
- `special_conditions`: `[]`
- `notes`: absent

Apotheosis note:
- for `mod_id = apotheosis`, `loot_targets` may be used as compat metadata to identify Apotheosis-sensitive loot tables for safe loot composition enforcement

---

## 13A. Object Type: `ascension_rewards`

Path:
- `data/<namespace>/ascension_rewards/*.json`

Minimum required fields:
- `id`
- `display_name`
- `components`

Example:

```json
{
  "schema_version": 1,
  "id": "my_pack:ember_blood",
  "display_name": { "translate": "ascension.my_pack.ember_blood" },
  "description": { "translate": "ascension.my_pack.ember_blood.desc" },
  "icon": { "item": "minecraft:blaze_powder" },
  "enabled": true,
  "components": [
    {
      "type": "worldawakened:max_health_bonus",
      "parameters": {
        "amount": 4.0
      }
    },
    {
      "type": "worldawakened:movement_speed_bonus",
      "parameters": {
        "amount": 0.08
      },
      "suppressible_individually": true,
      "suppression_policy": "grouped",
      "suppression_group": "mobility_visual_package"
    },
    {
      "type": "worldawakened:night_vision_passive",
      "suppressible_individually": true,
      "suppression_policy": "grouped",
      "suppression_group": "mobility_visual_package"
    }
  ],
  "rarity": "rare",
  "tags": ["ascension:tier1", "theme:fire"],
  "offer_weight": 8,
  "unique_group": "survivability_major",
  "exclusion_tags": ["theme:frost"],
  "requires_conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "forbidden_conditions": [],
  "ui_style": {
    "accent": "amber"
  },
  "max_rank": 1
}
```

Reward rules:
- reward object ID is the authored reward identity used by offers and persistence
- reward behavior is defined by Java component semantics
- `components[]` is the authored composition evaluated by the engine
- rewards are permanent once chosen
- rewards should be safe to reapply on login and respawn
- live reward reconciliation is additive-first and only manages World Awakened-owned modifiers/effects
- World Awakened will not clear or normalize third-party vanilla/modded player effects just to make a reward fit
- refreshable or revocable non-attribute effects must be backed by a WA-owned runtime carrier, not by direct ownership of shared vanilla/modded effect slots
- visual-only passive rewards must use WA-owned client visual carriers driven from synced WA-owned state; they must not mutate player options or inject/remove shared vanilla effect instances to simulate ownership, and they should use the closest owned render/lightmap path available for vanilla parity
- `fire_resistance_passive` and `night_vision_passive` are implemented examples of carrier-backed passive reward components
- stateless one-shot action payloads may still apply shared vanilla/modded effects when no later ownership or revoke path is required, but those paths must remain additive and must never clear external state
- if a reward component depends on an external attribute/effect carrier that cannot yet be safely owned during reconciliation, that component fails closed with diagnostics instead of mutating unrelated player state
- `unique_group` can be used to prevent similar future picks
- `offer_weight` is used when selecting rewards within a concrete offer candidate set
- `tier_weight` is used only for higher-level tier or pool-driven reward generation when that generation mode exists
- optional shipped example-pack presets and user-authored presets coexist; neither is privileged at runtime
- shared component composition semantics are mandatory; duplicate/stack/conflict behavior must be explicit
- optional suppression metadata may be authored per component entry:
  - `suppressible_individually` (default `false`)
  - `suppression_policy` (`reward_only | independent | grouped`, default `reward_only`)
  - `suppression_group` (required when `suppression_policy = grouped`)
- component-level suppression is allowed only when component type metadata and authored component metadata both allow it
- component-level `suppression_policy` values (`independent` or `grouped`) require `suppressible_individually=true`
- grouped suppression must resolve to all linked components in the same `suppression_group`

Field defaults:
- `schema_version`: `1`
- `id`: required
- `display_name`: required
- `description`: absent
- `icon`: absent
- `enabled`: `true`
- `components`: required (must resolve to at least one valid enabled component)
- `rarity`: absent
- `tags`: `[]`
- `tier_weight`: `1.0` when used
- `offer_weight`: `1.0` when used
- `unique_group`: absent
- `exclusion_tags`: `[]`
- `requires_conditions`: `[]`
- `forbidden_conditions`: `[]`
- `ui_style`: `{}`
- `max_rank`: `1`

Component entry defaults for `ascension_rewards.components[]`:
- `suppressible_individually`: `false`
- `suppression_policy`: `reward_only`
- `suppression_group`: absent

Recommended v1 ascension component types:
- `worldawakened:max_health_bonus`
- `worldawakened:movement_speed_bonus`
- `worldawakened:armor_bonus`
- `worldawakened:attack_damage_bonus`
- `worldawakened:knockback_resistance_bonus`
- `worldawakened:luck_bonus`
- `worldawakened:xp_gain_bonus`
- `worldawakened:loot_quality_bonus`
- `worldawakened:potion_resistance`
- `worldawakened:fire_resistance_like_passive`
- `worldawakened:extra_revival_buffer`
- `worldawakened:night_vision_like_passive`
- `worldawakened:fall_damage_reduction`
- `worldawakened:healing_efficiency_bonus`
- `worldawakened:mob_detection_bonus`
- `worldawakened:invasion_reward_bonus`
- `worldawakened:mutation_resistance_bonus`

Extension note:
- behavior types are Java-registered
- datapacks combine available behavior types and parameters
- addon mods may register additional component types in future extension APIs; unknown types fail validation when the registering mod is absent

---

## 13B. Object Type: `ascension_offers`

Path:
- `data/<namespace>/ascension_offers/*.json`

Minimum required fields:
- `id`
- `display_name`
- `choice_count`
- `selection_count`
- candidate reward refs or tags

Example:

```json
{
  "schema_version": 1,
  "id": "my_pack:nether_ascension_1",
  "display_name": { "translate": "ascension_offer.my_pack.nether_ascension_1" },
  "description": { "translate": "ascension_offer.my_pack.nether_ascension_1.desc" },
  "enabled": true,
  "trigger_conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "stage_filters": {
    "all_of": ["my_pack:nether_opened"]
  },
  "pressure_tier_filters": {},
  "apotheosis_tier_filters": {},
  "choice_count": 3,
  "selection_count": 1,
  "candidate_rewards": [
    "my_pack:ember_blood",
    "my_pack:grave_stride",
    "my_pack:predator_sense"
  ],
  "candidate_reward_tags": [],
  "offer_mode": "explicit_list",
  "weighting_rules": {},
  "ui_priority": 100,
  "allow_duplicates_across_players": true,
  "reward_repeat_policy": "block_all"
}
```

Offer rules:
- players may choose exactly one reward from a v1 offer
- unchosen displayed rewards become permanently forfeited for that offer
- resolved offers do not reopen for a different choice
- `reward_repeat_policy = "block_all"` excludes both previously chosen and previously forfeited rewards from later offers for that player
- `reward_repeat_policy = "allow_forfeited_only"` keeps previously chosen rewards blocked but allows previously forfeited rewards to reappear later if the rest of the offer logic permits it
- `reward_repeat_policy = "allow_all"` allows both previously chosen and previously forfeited rewards to reappear later if the rest of the offer logic permits it
- `reward_repeat_policy` should default to `"block_all"` in most packs
- `pressure_tier_filters` should be treated as optional external or pack-defined context unless the core spec later formalizes a built-in pressure-tier subsystem

Runtime identity note:
- the offer `id` identifies the reusable template
- the per-player runtime instance identity is resolved by the engine, not authored directly in the datapack
- v1 duplicate-prevention identity is canonicalized as `(player UUID, offer ID, source progression key)`; player scope is implied by save ownership
- runtime `instance_id` values are separate short opaque command-safe IDs generated by the engine
- datapack authors must not try to manually encode runtime offer instance identities in object IDs, fields, or generated references

Field defaults:
- `schema_version`: `1`
- `id`: required
- `display_name`: required
- `description`: absent
- `enabled`: `true`
- `trigger_conditions`: `[]`
- `stage_filters`: absent
- `pressure_tier_filters`: absent
- `apotheosis_tier_filters`: absent
- `choice_count`: `2`
- `selection_count`: `1`
- `candidate_rewards`: `[]`
- `candidate_reward_tags`: `[]`
- `offer_mode`: `explicit_list`
- `weighting_rules`: `{}`
- `ui_priority`: `0`
- `allow_duplicates_across_players`: `true`
- `reward_repeat_policy`: `block_all`

Recommended v1:
- `choice_count` of `2` or `3`
- `selection_count` of `1`
- `offer_mode` of `explicit_list` or `weighted_from_pool`
- one pending offer at a time per player

---

## 14. Optional Maps

These are optional helper datasets that may be consumed by World Awakened:

### 14.1 `maps/entity_boss_flags/*.json`

```json
{
  "id": "my_pack:boss_flags",
  "entries": [
    { "entity": "minecraft:wither", "is_boss": true },
    { "entity": "minecraft:ender_dragon", "is_boss": true }
  ]
}
```

This is the primary generic way to declare that a future modded entity should count as a boss without waiting for dedicated Java compat.

Example with modded bosses:

```json
{
  "id": "my_pack:boss_flags",
  "entries": [
    { "entity": "somebossmod:ancient_tyrant", "is_boss": true },
    { "entity": "somebossmod:grave_colossus", "is_boss": true }
  ]
}
```

### 14.2 `maps/entity_mutation_defaults/*.json`

```json
{
  "id": "my_pack:mutation_defaults",
  "entries": [
    { "entity": "minecraft:zombie", "eligible": true },
    { "entity": "minecraft:villager", "eligible": false }
  ]
}
```

### 14.3 `maps/item_metadata/*.json`

```json
{
  "id": "my_pack:item_metadata",
  "entries": [
    { "item": "minecraft:nether_star", "icon_tier": "legendary" }
  ]
}
```

### 14.4 `maps/structure_loot_hints/*.json`

```json
{
  "id": "my_pack:structure_hints",
  "entries": [
    {
      "structure_or_table": "minecraft:chests/stronghold_corridor",
      "category": "dungeon"
    }
  ]
}
```

---

## 15. Validation Behavior

On reload, World Awakened should validate and report:
- duplicate IDs
- duplicate ascension reward IDs
- duplicate ascension offer IDs
- missing references
- missing reward references inside ascension offers
- invalid condition/action types
- invalid condition/action shapes (missing `type`, missing/non-object `parameters`)
- scope violations (condition/action used outside allowed shared scopes)
- invalid logical wrapper payloads (`all_of`, `any_of`, `not`)
- invalid optional world-context condition payloads (for example: negative day thresholds, missing both `min` and `max`, or `min > max`)
- invalid config gates
- unsafe replacement/removal behavior against Apotheosis-sensitive loot targets when Apotheosis compat is active
- incompatible loot profile modes for Apotheosis-sensitive targets
- attempted destructive overrides of Apotheosis-owned tier-gated loot paths
- impossible mutator pool selections
- empty mutation `components[]`
- empty ascension reward `components[]`
- unknown mutation component types
- unknown ascension component types
- invalid mutation component parameters
- invalid ascension component parameters
- incompatible component combinations or explicit component conflicts
- invalid suppression metadata on ascension components
- grouped suppression policy conflicts within one suppression package
- component-level suppression paths that violate grouped suppression requirements
- component compositions with no valid runtime result (for example all components disabled)
- mutation component compositions over `component_budget` when budget is set
- performance threshold exceedance:
  - scope rule bucket size over `maximum_rules_per_bucket`
  - per-event evaluated rules over `maximum_rules_evaluated_per_event`
  - actions per rule over `maximum_actions_per_rule`
  - mutators per spawn over `max_mutators_per_spawn`
  - components per mutator over `max_components_per_mutator`
  - action-chain complexity over `max_action_chain_depth`
- duplicate component types where duplicates are not supported
- missing required companion component types
- forbidden companion component types
- stacking group overflow and `max_instances` violations
- `choice_count < 1`
- `selection_count != 1` in v1
- no valid candidate rewards after filtering
- invalid icon references for ascension rewards or offers
- unsupported entity IDs/tags
- invasion profiles with no valid composition
- Apotheosis-only conditions while integration disabled
- unsupported or incompatible schema versions
- invalid status taxonomy usage:
  - `planned` usage severity depends on validation mode
  - `reserved` usage is rejected
  - `deprecated` usage emits warnings with migration notes where known

Validation outcomes:
- valid object -> active
- invalid object -> disabled with error log
- fatal startup failure only for unrecoverable core integrity issues
- performance threshold violations should emit warnings by default and may optionally disable offending objects/branches based on policy
- World Awakened should never silently substitute a different ascension reward or offer without logging
- world-context conditions that are valid but unevaluable in a runtime context should behave as false matches, not validation errors
- unsafe Apotheosis-sensitive loot operations should never silently destroy Apotheosis tier-gated behavior; fallback action should be explicit and logged

Severity note:
- invalid stages are more severe than most other objects because dependent objects may also be disabled
- most other broken objects should fail in isolation

Recommended log format:
- object type
- object ID
- file path
- canonical error code in `WA_<DOMAIN>_<DETAIL>` format plus message
- fallback action taken

---

## 16. Reload and Override Semantics

- Datapack reload should refresh World Awakened objects.
- Object identity conflicts (`id`) are invalid and must be reported.
- Conflict winner (if one is retained) follows pack priority order.
- Pack authors should avoid duplicate IDs across packs.
- Selectors, conditions, and other hot-path filters should compile during reload, not during live event evaluation

---

## 16A. Save Compatibility and Migration Expectations

Pack authors should expect World Awakened to degrade gracefully when packs change.

Expected behavior:
- removed stages remain in save data but become inactive
- renamed stages can continue resolving through `aliases`
- removed mutators on already spawned mobs remain until those entities despawn
- removed invasion profiles should not break invasions that are already active
- removed chosen ascension reward definitions should preserve saved reward IDs, stop applying live effects after reconciliation, emit debug or validation warnings, and never be auto-substituted

Guidance:
- when renaming a stage, keep the new canonical `id` and add the old ID to `aliases`
- avoid deleting or renaming heavily used progression IDs without a migration plan

---

## 16B. Randomness and Determinism Expectations

Pack authors should assume World Awakened random outcomes are controlled by server-side deterministic evaluation rules.
Exact rule-execution sequence and bucket/index guarantees are defined in [SPECIFICATION.md](SPECIFICATION.md) Section `8.7`.

Implications:
- mutator rolls, invasion composition, and bonus loot rolls should not reroll repeatedly inside the same evaluation context
- the same spawn event should not produce different results just because multiple systems inspect it in the same tick
- client-side display should never become the source of truth for rule or spawn outcomes

---

## 17. Debug Workflow for Pack Authors

Suggested flow:
1. Load pack and run `/wa reload validate`
2. Use `/wa stage list`, `/wa stage list player <player>`, or `/wa stage list global` to verify stage load and state; in `PER_PLAYER` mode the explicit `player` and `global` targets are the authoritative operator paths
3. Use `/wa trigger fire <id> player <player> [dimension <dimension_id>]` or `/wa trigger fire <id> global [dimension <dimension_id>]` for trigger testing; use the optional dimension override when validating dimension-dependent conditions from a controlled operator path
4. Use `/wa stage unlock <id> player <player>`, `/wa stage lock <id> player <player>`, or the corresponding `global` forms for rule-gating checks and operator rollback
5. Use `/wa ascension grant_offer <player> <offer_id>`, `/wa ascension open <player>`, `/wa ascension inspect <player>`, `/wa ascension choose <player> <instance_id> <reward_id>`, or `/wa ascension active <player> <reward_id>` for ascension testing
6. Use `/wa ascension suppress reward <player> <reward_id>`, `/wa ascension unsuppress reward <player> <reward_id>`, `/wa ascension suppress component <player> <reward_id> <component_key>`, `/wa ascension unsuppress component <player> <reward_id> <component_key>`, and `/wa ascension reconcile <player>` when testing suppression and re-enable flows
   - `component_key` uses canonical `index|namespace:component_type` keys (example: `0|worldawakened:movement_speed_bonus`) and also accepts index-only shorthand like `0`
7. Use `/wa ascension revoke <player> <reward_id>`, `/wa ascension reopen <player> <instance_id>`, `/wa ascension clear <player> <instance_id>`, and `/wa debug reset player <player> ascension` when testing recovery or rollback behavior
8. Runtime ascension `instance_id` values are opaque command-safe IDs; inspect/debug output exposes `offer_id` and `source_key` separately when you need provenance
9. Operator-facing ascension outputs should provide copy/suggest actions where the client supports clickable chat; authored IDs remain the canonical support/debug identity even when operators use the friendly shortcuts
10. Use `/wa invasion start <profile>` for invasion profiles
11. Use `/wa mob inspect` on mutated entities for provenance
12. Use `/wa dump active_rules player <player> [dimension <dimension_id>]` or `/wa dump active_rules global [dimension <dimension_id>]` to confirm rule activation set; use the dimension override when validating dimension-sensitive rule context
13. Use `/wa apotheosis tier inspect` if Apotheosis rules are used
14. If enabled by server policy, use `/wa difficulty global get` and `/wa difficulty personal get` to confirm active scalar context while tuning pack behavior

Debug payload contract note:
- canonical trace/rejection/provenance output shape is defined in [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)

---

## 17A. Soft Compat Workflow for New Mob/Boss Mods

When a new content mod adds mobs or bosses, try this before asking for dedicated compat:
1. Reference the entity by ID in `trigger_rules`, `rules`, `mutation_pools`, or `invasion_profiles`
2. Add boss classification in `maps/entity_boss_flags` if it should count as a boss
3. Add or reuse entity tags if multiple entities should be grouped together
4. Validate and test with `/wa reload validate`, `/wa trigger fire`, and `/wa mob inspect`

Move to dedicated compat only if the mod depends on custom APIs, hidden boss definitions, or non-standard progression hooks.

---

## 18. Minimal Starter Pack Example

This is a minimal usable progression skeleton:

```text
data/my_pack/stages/baseline.json
data/my_pack/stages/nether_opened.json
data/my_pack/trigger_rules/unlock_nether_on_entry.json
data/my_pack/rules/night_pressure_stage2.json
data/my_pack/mob_mutators/berserker_t1.json
data/my_pack/mutation_pools/overworld_night_t2.json
```

Core principle for authors:
- Define the content in data.
- Let World Awakened Java systems execute behavior safely and consistently.

---

## 19. Authoring Checklist

Before shipping a pack:
- all IDs are unique
- all references resolve
- condition/action entries use canonical shared shape (`type` + `parameters`)
- condition/action entries are only used in allowed scopes
- stage names and display text are localized where needed
- ascension offers only reference valid rewards
- component compositions declare explicit duplicate/conflict/stacking behavior
- no `reserved` status entries are used in runtime content
- `selection_count` remains `1` unless the engine explicitly documents broader support
- integration-specific objects are gated with mod/config conditions
- no invalid JSON syntax
- no impossible filter combinations
- if your pack balance depends on scalar-sensitive encounters, test with non-default server difficulty/challenge modifier settings
- reload validation passes without errors
- debug commands confirm expected behavior in-game



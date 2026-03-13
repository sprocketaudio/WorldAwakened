# World Awakened Component Reference

Canonical reference for mutation and ascension component IDs, schemas, statuses, and composition rules.

- Document status: Active shared-contract reference
- Last updated: 2026-03-13
- Scope: Runtime component contracts and authoring/tooling alignment

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
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
- Update this file in the same change whenever component IDs, schemas, status labels, composition rules, or runtime handler support change.
- Keep this file aligned with the other shared reference docs and with validator/runtime behavior.

## 1. Overview
- World Awakened has two component libraries: mutation components and ascension components.
- Mutation components are authored inside named mutation definitions (`mob_mutators`).
- Ascension components are authored inside named reward definitions (`ascension_rewards`).
- Engine behavior is component-driven; named definitions are datapack-authored compositions.
- Component status labels in this document use the shared taxonomy (`implemented`, `planned`, `reserved`, `deprecated`) and are canonical inputs for validation/tooling feature markers.

Terminology:
- `component type`: reusable behavior building block.
- `authored definition`: named gameplay object that combines one or more components.

Phase 4 runtime application note:
- Ascension reward reconciliation now reapplies selected reward effects on login and respawn.
- Current live player-effect handlers are wired for: `max_health_bonus`, `armor_bonus`, `armor_toughness_bonus`, `attack_damage_bonus`, `movement_speed_bonus`, `knockback_resistance_bonus`, `luck_bonus`, `fire_resistance_passive`, and `night_vision_passive`.
- Reconciliation is additive-first and only clears World Awakened-owned attribute modifiers keyed under the `worldawakened:ascension/...` namespace/path contract.
- Refreshable/revocable non-attribute effects must route through WA-owned runtime carriers keyed by stable WA-owned source keys; shared vanilla/modded effect carriers are not normalized or cleared during reconciliation.
- `fire_resistance_passive` is backed by a WA-owned server runtime carrier; `night_vision_passive` is backed by a WA-owned client visual carrier driven from synced owned-carrier state and the client lightmap path.
- Other ascension component IDs listed as implemented remain valid/validated authored types, but may still be inert until their dedicated runtime handlers are added in later phases.

## 2. Shared Authoring Rules
- Every authored definition must contain at least one component entry.
- Component types are referenced by `ResourceLocation` (`namespace:path`).
- Unknown component types fail validation for the authored object.
- Parameters must match the component schema/validator expectations.
- Duplicate behavior must be explicit per component type (`duplicate_policy` semantics).
- Component execution order is deterministic (authored order + priority semantics).
- Component behavior is server-authoritative.
- Datapacks may combine supported components freely within validation/runtime safety rules.
- New behavior categories require Java implementation and registry registration.
- Built-in presets are examples, not privileged code-only gameplay identities.

### 2A. Shared Status Taxonomy

This reference follows the framework-wide status taxonomy from `docs/SPECIFICATION.md`:
- `implemented`: runtime-supported now
- `planned`: designed/documented but not fully runtime-supported
- `reserved`: name/shape held, not valid for runtime authored content
- `deprecated`: still recognized for compatibility, warning for new content

Validation expectation:
- `implemented` entries are allowed
- `planned` entries are warning/error depending on validation mode
- `reserved` entries are rejected
- `deprecated` entries warn with migration guidance when available

### 2B. Shared Composition Contracts

Mutation and ascension components both use shared composition semantics:
- explicit conflict metadata (`conflicts_with`, `exclusive_with`)
- explicit duplicate metadata (`duplicate_policy`, `max_instances`)
- explicit ordering metadata (`composition_priority`, authored order)
- explicit dependency metadata (`requires_component_types`, `forbidden_component_types`)

Authoritative semantics:
- canonical duplicate/conflict/order/budget/no-op resolution algorithm is defined in [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- component entries in this file should reference that contract instead of redefining local variants

Hard composition expectations:
- additive/multiplicative ordering must be deterministic
- incompatible compositions fail validation pre-runtime
- helper components requiring a core component are invalid without that core
- presentation-layer stacking is opt-in by component contract
- diagnostics must call out duplicate/conflict/stacking/budget failures explicitly

## Component Entry Shape
Common conceptual entry fields:
- `type`
- `parameters`
- `enabled` (optional)
- `priority` (optional)
- `required_conditions` (optional conceptual field)
- `forbidden_conditions` (optional conceptual field)
- `exclusive_with_component_types` (optional conceptual field)
- `duplicate_policy` (optional conceptual field)
- `max_instances` (optional conceptual field)
- `composition_priority` (optional conceptual field)
- `requires_component_types` (optional conceptual field)
- `forbidden_component_types` (optional conceptual field)
- `suppressible_individually` (optional ascension metadata; default `false`)
- `suppression_policy` (optional ascension metadata; `reward_only | independent | grouped`, default `reward_only`)
- `suppression_group` (optional ascension metadata; required when `suppression_policy=grouped`)

Current v1 codec note:
- Mutation/ascension component entries currently encode gating/conflicts with `conditions`, `exclusions`, and `conflicts_with`.
- Not every component type uses every field.

## 3. Mutation Components
Mutation components are reusable mob-affecting behavior/stat packages used inside named mutation definitions in `mob_mutators`.
A mutation definition may combine multiple compatible components.

### Stat / durability

## `max_health_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `max_health_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:max_health_bonus", "parameters": { "amount": 1.0 } }
```

## `max_health_multiplier`

**Category:** `Stat / durability`

**Purpose:**
Applies `max_health_multiplier` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `multiplier` (number, expected `> 0`).

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:max_health_multiplier", "parameters": { "multiplier": 1.1 } }
```

## `attack_damage_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `attack_damage_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:attack_damage_bonus", "parameters": { "amount": 1.0 } }
```

## `attack_damage_multiplier`

**Category:** `Stat / durability`

**Purpose:**
Applies `attack_damage_multiplier` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `multiplier` (number, expected `> 0`).

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:attack_damage_multiplier", "parameters": { "multiplier": 1.1 } }
```

## `armor_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `armor_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:armor_bonus", "parameters": { "amount": 1.0 } }
```

## `armor_multiplier`

**Category:** `Stat / durability`

**Purpose:**
Applies `armor_multiplier` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `multiplier` (number, expected `> 0`).

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:armor_multiplier", "parameters": { "multiplier": 1.1 } }
```

## `armor_toughness_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `armor_toughness_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:armor_toughness_bonus", "parameters": { "amount": 1.0 } }
```

## `movement_speed_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `movement_speed_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:movement_speed_bonus", "parameters": { "amount": 1.0 } }
```

## `movement_speed_multiplier`

**Category:** `Stat / durability`

**Purpose:**
Applies `movement_speed_multiplier` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `multiplier` (number, expected `> 0`).
- Initial design target: final lower/upper bounds may be tightened when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:movement_speed_multiplier", "parameters": { "multiplier": 1.1 } }
```

## `follow_range_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `follow_range_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:follow_range_bonus", "parameters": { "amount": 1.0 } }
```

## `knockback_resistance_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `knockback_resistance_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:knockback_resistance_bonus", "parameters": { "amount": 1.0 } }
```

## `xp_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `xp_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:xp_bonus", "parameters": { "amount": 1.0 } }
```

## `loot_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `loot_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:loot_bonus", "parameters": { "amount": 1.0 } }
```

## `reinforcement_chance_bonus`

**Category:** `Stat / durability`

**Purpose:**
Applies `reinforcement_chance_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Stat / durability`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:reinforcement_chance_bonus", "parameters": { "amount": 1.0 } }
```

### Targeting / pursuit

## `wall_sense`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `wall_sense` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:wall_sense" }
```

## `target_range_bonus`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `target_range_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:target_range_bonus", "parameters": { "amount": 1.0 } }
```

## `target_switching`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `target_switching` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- `seconds` (number, expected `> 0`).
- Initial design target: may accept min/max or per-phase cooldown keys later.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:target_switching", "parameters": { "seconds": 12.0 } }
```

## `pursuit_speed_boost`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `pursuit_speed_boost` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:pursuit_speed_boost", "parameters": { "amount": 1.0 } }
```

## `anti_kite_behavior`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `anti_kite_behavior` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:anti_kite_behavior" }
```

## `aggro_lock_window`

**Category:** `Targeting / pursuit`

**Purpose:**
Applies `aggro_lock_window` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Targeting / pursuit`-focused preset compositions.

**Parameters:**
- `seconds` (number, expected `> 0`).
- Initial design target: may accept min/max or per-phase cooldown keys later.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:aggro_lock_window", "parameters": { "seconds": 12.0 } }
```

### Defensive

## `debuff_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `debuff_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:debuff_resistance", "parameters": { "amount": 1.0 } }
```

## `damage_type_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `damage_type_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `damage_type` (`namespace:path` string, non-blank).
- Optional amount/ratio keys are component-specific and must be validator-backed.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:damage_type_resistance", "parameters": { "damage_type": "minecraft:fire" } }
```

## `temporary_shield`

**Category:** `Defensive`

**Purpose:**
Applies `temporary_shield` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:temporary_shield", "parameters": { "amount": 1.0 } }
```

## `shield_regen`

**Category:** `Defensive`

**Purpose:**
Applies `shield_regen` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- Intended shape: `amount_per_second` with optional delay/cooldown keys.
- **Schema status:** Initial design target; final key names are not locked yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:shield_regen", "parameters": { "amount_per_second": 1.0 } }
```

## `partial_cc_immunity`

**Category:** `Defensive`

**Purpose:**
Applies `partial_cc_immunity` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:partial_cc_immunity", "parameters": { "amount": 1.0 } }
```

## `projectile_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `projectile_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_resistance", "parameters": { "amount": 1.0 } }
```

## `fall_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `fall_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:fall_resistance", "parameters": { "amount": 1.0 } }
```

## `fire_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `fire_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:fire_resistance", "parameters": { "amount": 1.0 } }
```

## `explosion_resistance`

**Category:** `Defensive`

**Purpose:**
Applies `explosion_resistance` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Defensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:explosion_resistance", "parameters": { "amount": 1.0 } }
```

### Offensive

## `on_hit_effect`

**Category:** `Offensive`

**Purpose:**
Applies `on_hit_effect` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `effect` (`namespace:path` string, non-blank).
- Optional duration/amplifier keys are component-specific.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:on_hit_effect", "parameters": { "effect": "minecraft:slowness" } }
```

## `life_steal`

**Category:** `Offensive`

**Purpose:**
Applies `life_steal` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:life_steal", "parameters": { "amount": 1.0 } }
```

## `bleed_on_hit`

**Category:** `Offensive`

**Purpose:**
Applies `bleed_on_hit` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:bleed_on_hit", "parameters": { "amount": 1.0 } }
```

## `poison_on_hit`

**Category:** `Offensive`

**Purpose:**
Applies `poison_on_hit` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:poison_on_hit", "parameters": { "amount": 1.0 } }
```

## `slow_on_hit`

**Category:** `Offensive`

**Purpose:**
Applies `slow_on_hit` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:slow_on_hit", "parameters": { "amount": 1.0 } }
```

## `knockback_on_hit`

**Category:** `Offensive`

**Purpose:**
Applies `knockback_on_hit` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:knockback_on_hit", "parameters": { "amount": 1.0 } }
```

## `armor_break_on_hit`

**Category:** `Offensive`

**Purpose:**
Applies `armor_break_on_hit` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:armor_break_on_hit", "parameters": { "amount": 1.0 } }
```

## `cooldown_burst_attack`

**Category:** `Offensive`

**Purpose:**
Applies `cooldown_burst_attack` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Offensive`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:cooldown_burst_attack", "parameters": { "amount": 1.0 } }
```

### Summon / spawn

## `reinforcement_summon`

**Category:** `Summon / spawn`

**Purpose:**
Applies `reinforcement_summon` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- At least one of `entity` or `entity_tag`.
- Additional spawn tuning keys are optional and component-specific.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:reinforcement_summon", "parameters": { "entity_tag": "minecraft:skeletons" } }
```

## `summon_cooldown`

**Category:** `Summon / spawn`

**Purpose:**
Applies `summon_cooldown` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- `seconds` (number, expected `> 0`).

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Requires `reinforcement_summon`; otherwise composition is invalid.

**Example Snippet:**
```json
{ "type": "worldawakened:summon_cooldown", "parameters": { "seconds": 12.0 } }
```

## `summon_cap`

**Category:** `Summon / spawn`

**Purpose:**
Applies `summon_cap` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- `max` (integer, expected `>= 1`).

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Requires `reinforcement_summon`; otherwise composition is invalid.

**Example Snippet:**
```json
{ "type": "worldawakened:summon_cap", "parameters": { "max": 3 } }
```

## `summon_entity_table`

**Category:** `Summon / spawn`

**Purpose:**
Applies `summon_entity_table` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- Intended shape: `table` (`ResourceLocation`) and/or weighted summon entries.
- **Schema status:** Initial design target; final table schema is not implemented yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:summon_entity_table", "parameters": { "table": "worldawakened:default_reinforcements" } }
```

## `death_spawn`

**Category:** `Summon / spawn`

**Purpose:**
Applies `death_spawn` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- At least one of `entity` or `entity_tag`.
- Additional spawn tuning keys are optional and component-specific.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:death_spawn", "parameters": { "entity_tag": "minecraft:skeletons" } }
```

## `phase_spawn`

**Category:** `Summon / spawn`

**Purpose:**
Applies `phase_spawn` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Summon / spawn`-focused preset compositions.

**Parameters:**
- Intended shape: summon target (`entity` or `entity_tag`) plus phase/trigger fields.
- **Schema status:** Initial design target; not finalized in current implementation.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:phase_spawn", "parameters": { "entity_tag": "minecraft:skeletons", "phase": "enraged" } }
```

### Mobility

## `burst_movement`

**Category:** `Mobility`

**Purpose:**
Applies `burst_movement` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Mobility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:burst_movement", "parameters": { "amount": 1.0 } }
```

## `short_dash`

**Category:** `Mobility`

**Purpose:**
Applies `short_dash` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Mobility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:short_dash", "parameters": { "amount": 1.0 } }
```

## `teleport_step`

**Category:** `Mobility`

**Purpose:**
Applies `teleport_step` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Mobility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:teleport_step", "parameters": { "amount": 1.0 } }
```

## `jump_boost_behavior`

**Category:** `Mobility`

**Purpose:**
Applies `jump_boost_behavior` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Mobility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:jump_boost_behavior", "parameters": { "amount": 1.0 } }
```

## `water_chase_behavior`

**Category:** `Mobility`

**Purpose:**
Applies `water_chase_behavior` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Mobility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:water_chase_behavior", "parameters": { "amount": 1.0 } }
```

### Projectile / ranged

## `projectile_modifier`

**Category:** `Projectile / ranged`

**Purpose:**
Applies `projectile_modifier` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Projectile / ranged`-focused preset compositions.

**Parameters:**
- No currently required keys in validator; component uses open parameter object.
- Use explicit named keys in authored content and validate in future schema revisions.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_modifier", "parameters": { "mode": "default" } }
```

## `projectile_split`

**Category:** `Projectile / ranged`

**Purpose:**
Applies `projectile_split` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Projectile / ranged`-focused preset compositions.

**Parameters:**
- `count` (integer, expected `>= 2` for split behavior).
- Optional spread/angle keys are component-specific.
- Initial design target: exact spread schema is not finalized yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_split", "parameters": { "count": 2 } }
```

## `projectile_status_payload`

**Category:** `Projectile / ranged`

**Purpose:**
Applies `projectile_status_payload` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Projectile / ranged`-focused preset compositions.

**Parameters:**
- `effect` (`namespace:path` string, non-blank).
- Optional duration/amplifier keys are component-specific.
- Initial design target: duration/amplifier fields are not final yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_status_payload", "parameters": { "effect": "minecraft:slowness" } }
```

## `projectile_velocity_bonus`

**Category:** `Projectile / ranged`

**Purpose:**
Applies `projectile_velocity_bonus` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Projectile / ranged`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_velocity_bonus", "parameters": { "amount": 1.0 } }
```

## `projectile_tracking_partial`

**Category:** `Projectile / ranged`

**Purpose:**
Applies `projectile_tracking_partial` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Projectile / ranged`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_tracking_partial", "parameters": { "amount": 1.0 } }
```

### Elemental

## `fire_package`

**Category:** `Elemental`

**Purpose:**
Applies `fire_package` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Elemental`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Incompatible with `frost_package`, `lightning_package`, and `poison_package` in current registry rules.

**Example Snippet:**
```json
{ "type": "worldawakened:fire_package" }
```

## `frost_package`

**Category:** `Elemental`

**Purpose:**
Applies `frost_package` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Elemental`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Incompatible with `fire_package`, `lightning_package`, and `poison_package` in current registry rules.

**Example Snippet:**
```json
{ "type": "worldawakened:frost_package" }
```

## `lightning_package`

**Category:** `Elemental`

**Purpose:**
Applies `lightning_package` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Elemental`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Incompatible with `fire_package`, `frost_package`, and `poison_package` in current registry rules.

**Example Snippet:**
```json
{ "type": "worldawakened:lightning_package" }
```

## `poison_package`

**Category:** `Elemental`

**Purpose:**
Applies `poison_package` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Elemental`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Incompatible with `fire_package`, `frost_package`, and `lightning_package` in current registry rules.

**Example Snippet:**
```json
{ "type": "worldawakened:poison_package" }
```

## `void_package`

**Category:** `Elemental`

**Purpose:**
Applies `void_package` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Elemental`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.
- Planned schema may add optional tuning keys; treat this as non-final.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:void_package" }
```

### Aura / retaliation / death

## `damage_aura`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `damage_aura` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:damage_aura", "parameters": { "amount": 1.0 } }
```

## `slow_aura`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `slow_aura` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:slow_aura", "parameters": { "amount": 1.0 } }
```

## `fear_aura`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `fear_aura` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:fear_aura", "parameters": { "amount": 1.0 } }
```

## `healing_suppression_aura`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `healing_suppression_aura` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:healing_suppression_aura", "parameters": { "amount": 1.0 } }
```

## `ally_buff_aura`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `ally_buff_aura` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:ally_buff_aura", "parameters": { "amount": 1.0 } }
```

## `death_explosion`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `death_explosion` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:death_explosion", "parameters": { "amount": 1.0 } }
```

## `death_cloud`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `death_cloud` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:death_cloud", "parameters": { "amount": 1.0 } }
```

## `retaliation_thorns`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `retaliation_thorns` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:retaliation_thorns", "parameters": { "amount": 1.0 } }
```

## `retaliation_burst`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `retaliation_burst` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:retaliation_burst", "parameters": { "amount": 1.0 } }
```

## `revenge_enrage`

**Category:** `Aura / retaliation / death`

**Purpose:**
Applies `revenge_enrage` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Aura / retaliation / death`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:revenge_enrage", "parameters": { "amount": 1.0 } }
```

### Presentation

## `nameplate_style`

**Category:** `Presentation`

**Purpose:**
Applies `nameplate_style` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Presentation`-focused preset compositions.

**Parameters:**
- Intended style keys such as `style`, `color`, and presentation toggles.
- **Schema status:** Initial design target; do not assume final key set yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:nameplate_style", "parameters": { "style": "default" } }
```

## `glow_style`

**Category:** `Presentation`

**Purpose:**
Applies `glow_style` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Presentation`-focused preset compositions.

**Parameters:**
- Intended style keys such as `style`, `color`, and presentation toggles.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:glow_style", "parameters": { "style": "default" } }
```

## `ambient_particles`

**Category:** `Presentation`

**Purpose:**
Applies `ambient_particles` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Presentation`-focused preset compositions.

**Parameters:**
- Intended style keys such as `style`, `color`, and presentation toggles.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicate entries are currently allowed; keep particle stacks intentional.

**Example Snippet:**
```json
{ "type": "worldawakened:ambient_particles", "parameters": { "style": "default" } }
```

## `ambient_sound_loop`

**Category:** `Presentation`

**Purpose:**
Applies `ambient_sound_loop` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Presentation`-focused preset compositions.

**Parameters:**
- Intended keys: `sound` (`ResourceLocation`) with optional `volume`/`pitch`.
- **Schema status:** Initial design target.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:ambient_sound_loop", "parameters": { "sound": "minecraft:entity.zombie.ambient" } }
```

## `spawn_announcement_style`

**Category:** `Presentation`

**Purpose:**
Applies `spawn_announcement_style` behavior to a named mutation definition in `mob_mutators`.

**Typical Uses:**
- Combine into named elite archetypes, invasion variants, or boss-adjacent mutation presets.
- Fits `Presentation`-focused preset compositions.

**Parameters:**
- Intended style keys such as `style`, `color`, and presentation toggles.
- **Schema status:** Initial design target; do not assume final key set yet.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:spawn_announcement_style", "parameters": { "style": "default" } }
```

## 4. Ascension Components
Ascension components are reusable permanent player-benefit packages used inside named ascension reward definitions.
A reward may combine multiple compatible components.
Live effects are reconciled from saved chosen reward definitions.

Ascension suppression metadata (v1):
- reward-level suppression is always allowed
- component-level suppression requires both:
  - component type metadata support in the registry
  - authored component metadata support (`suppressible_individually` / `suppression_policy`)
- component-level suppression policies (`independent` / `grouped`) require `suppressible_individually=true`
- grouped suppression requires `suppression_policy=grouped` plus a shared `suppression_group`
- grouped suppression resolves and applies as all-linked-components together
- invalid partial suppression states are rejected

### Core stats

## `max_health_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `max_health_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:max_health_bonus", "parameters": { "amount": 1.0 } }
```

## `armor_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `armor_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:armor_bonus", "parameters": { "amount": 1.0 } }
```

## `armor_toughness_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `armor_toughness_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:armor_toughness_bonus", "parameters": { "amount": 1.0 } }
```

## `attack_damage_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `attack_damage_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:attack_damage_bonus", "parameters": { "amount": 1.0 } }
```

## `movement_speed_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `movement_speed_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:movement_speed_bonus", "parameters": { "amount": 1.0 } }
```

## `knockback_resistance_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `knockback_resistance_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:knockback_resistance_bonus", "parameters": { "amount": 1.0 } }
```

## `luck_bonus`

**Category:** `Core stats`

**Purpose:**
Applies `luck_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Core stats`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:luck_bonus", "parameters": { "amount": 1.0 } }
```

### Survival / mitigation

## `fire_resistance_passive`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `fire_resistance_passive` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Runtime Notes:**
- Implemented through a WA-owned server runtime carrier keyed by a stable WA-owned source key.
- Reconcile may refresh or remove this carrier without touching shared vanilla or other-mod effect slots.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.
- Current live reconciliation routes this component through a WA-owned runtime carrier that blocks fire-tagged incoming damage without touching shared vanilla/modded effect state.

**Example Snippet:**
```json
{ "type": "worldawakened:fire_resistance_passive" }
```

## `fall_damage_reduction`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `fall_damage_reduction` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:fall_damage_reduction", "parameters": { "amount": 1.0 } }
```

## `debuff_resistance`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `debuff_resistance` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:debuff_resistance", "parameters": { "amount": 1.0 } }
```

## `damage_type_resistance`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `damage_type_resistance` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `damage_type` (`namespace:path` string, non-blank).
- Optional amount/ratio keys are component-specific and must be validator-backed.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:damage_type_resistance", "parameters": { "damage_type": "minecraft:fire" } }
```

## `healing_efficiency_bonus`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `healing_efficiency_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:healing_efficiency_bonus", "parameters": { "amount": 1.0 } }
```

## `extra_revival_buffer`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `extra_revival_buffer` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:extra_revival_buffer", "parameters": { "amount": 1.0 } }
```

## `environmental_resistance`

**Category:** `Survival / mitigation`

**Purpose:**
Applies `environmental_resistance` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Survival / mitigation`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:environmental_resistance", "parameters": { "amount": 1.0 } }
```

### Mobility / utility

## `step_height_bonus`

**Category:** `Mobility / utility`

**Purpose:**
Applies `step_height_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:step_height_bonus", "parameters": { "amount": 1.0 } }
```

## `sprint_efficiency`

**Category:** `Mobility / utility`

**Purpose:**
Applies `sprint_efficiency` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:sprint_efficiency", "parameters": { "amount": 1.0 } }
```

## `jump_bonus`

**Category:** `Mobility / utility`

**Purpose:**
Applies `jump_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:jump_bonus", "parameters": { "amount": 1.0 } }
```

## `water_mobility_bonus`

**Category:** `Mobility / utility`

**Purpose:**
Applies `water_mobility_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:water_mobility_bonus", "parameters": { "amount": 1.0 } }
```

## `night_vision_passive`

**Category:** `Mobility / utility`

**Purpose:**
Applies `night_vision_passive` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Runtime Notes:**
- Implemented through a WA-owned client visual carrier keyed by a stable WA-owned source key and synced to the owning client.
- Uses a WA-owned lightmap-backed night-vision scale path plus the matching fog-color lift instead of injecting the vanilla `NIGHT_VISION` mob effect.
- Yields when the client already has real vanilla/modded `NIGHT_VISION` so the WA-owned carrier does not double-stack the viewport path and instead reuses the vanilla lightmap scale.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.
- Current live reconciliation applies this component through a WA-owned client visual carrier and lightmap hook rather than the shared vanilla `NIGHT_VISION` effect slot.

**Example Snippet:**
```json
{ "type": "worldawakened:night_vision_passive" }
```

## `hostile_wall_sense`

**Category:** `Mobility / utility`

**Purpose:**
Applies `hostile_wall_sense` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:hostile_wall_sense" }
```

## `loot_detection`

**Category:** `Mobility / utility`

**Purpose:**
Applies `loot_detection` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:loot_detection" }
```

## `structure_detection_hint`

**Category:** `Mobility / utility`

**Purpose:**
Applies `structure_detection_hint` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Mobility / utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:structure_detection_hint", "parameters": { "amount": 1.0 } }
```

### Combat

## `crit_bonus`

**Category:** `Combat`

**Purpose:**
Applies `crit_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:crit_bonus", "parameters": { "amount": 1.0 } }
```

## `attack_reach_bonus`

**Category:** `Combat`

**Purpose:**
Applies `attack_reach_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:attack_reach_bonus", "parameters": { "amount": 1.0 } }
```

## `on_hit_effect_passive`

**Category:** `Combat`

**Purpose:**
Applies `on_hit_effect_passive` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `effect` (`namespace:path` string, non-blank).
- Optional duration/amplifier keys are component-specific.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:on_hit_effect_passive", "parameters": { "effect": "minecraft:slowness" } }
```

## `life_steal_minor`

**Category:** `Combat`

**Purpose:**
Applies `life_steal_minor` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:life_steal_minor", "parameters": { "amount": 1.0 } }
```

## `execution_bonus`

**Category:** `Combat`

**Purpose:**
Applies `execution_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:execution_bonus", "parameters": { "amount": 1.0 } }
```

## `cooldown_reduction_minor`

**Category:** `Combat`

**Purpose:**
Applies `cooldown_reduction_minor` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:cooldown_reduction_minor", "parameters": { "amount": 1.0 } }
```

## `projectile_bonus`

**Category:** `Combat`

**Purpose:**
Applies `projectile_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:projectile_bonus", "parameters": { "amount": 1.0 } }
```

## `elemental_affinity`

**Category:** `Combat`

**Purpose:**
Applies `elemental_affinity` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Combat`-focused preset compositions.

**Parameters:**
- Intended keys: `element` plus optional scalar tuning.
- **Schema status:** Initial design target.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:elemental_affinity", "parameters": { "element": "fire" } }
```

### Progression / rewards

## `xp_gain_bonus`

**Category:** `Progression / rewards`

**Purpose:**
Applies `xp_gain_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Progression / rewards`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:xp_gain_bonus", "parameters": { "amount": 1.0 } }
```

## `loot_quality_bonus`

**Category:** `Progression / rewards`

**Purpose:**
Applies `loot_quality_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Progression / rewards`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:loot_quality_bonus", "parameters": { "amount": 1.0 } }
```

## `invasion_reward_bonus`

**Category:** `Progression / rewards`

**Purpose:**
Applies `invasion_reward_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Progression / rewards`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:invasion_reward_bonus", "parameters": { "amount": 1.0 } }
```

## `mutation_resistance_bonus`

**Category:** `Progression / rewards`

**Purpose:**
Applies `mutation_resistance_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Progression / rewards`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:mutation_resistance_bonus", "parameters": { "amount": 1.0 } }
```

## `boss_reward_bonus`

**Category:** `Progression / rewards`

**Purpose:**
Applies `boss_reward_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Progression / rewards`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:boss_reward_bonus", "parameters": { "amount": 1.0 } }
```

### World/event utility

## `invasion_warning_bonus`

**Category:** `World/event utility`

**Purpose:**
Applies `invasion_warning_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `World/event utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:invasion_warning_bonus", "parameters": { "amount": 1.0 } }
```

## `elite_detection`

**Category:** `World/event utility`

**Purpose:**
Applies `elite_detection` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `World/event utility`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.

**Defaults / Notes:**
- **Status:** `implemented`.
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**
- Duplicates are rejected unless the component type explicitly allows duplicates.

**Example Snippet:**
```json
{ "type": "worldawakened:elite_detection" }
```

## `rare_drop_ping`

**Category:** `World/event utility`

**Purpose:**
Applies `rare_drop_ping` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `World/event utility`-focused preset compositions.

**Parameters:**
- No required parameters in current validators.
- Planned schema may add optional tuning keys; treat this as non-final.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:rare_drop_ping" }
```

## `exploration_reward_bonus`

**Category:** `World/event utility`

**Purpose:**
Applies `exploration_reward_bonus` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `World/event utility`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:exploration_reward_bonus", "parameters": { "amount": 1.0 } }
```

### Optional later support

## `ally_minor_aura`

**Category:** `Optional later support`

**Purpose:**
Applies `ally_minor_aura` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Optional later support`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:ally_minor_aura", "parameters": { "amount": 1.0 } }
```

## `shared_detection_aura`

**Category:** `Optional later support`

**Purpose:**
Applies `shared_detection_aura` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Optional later support`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:shared_detection_aura", "parameters": { "amount": 1.0 } }
```

## `support_healing_aura`

**Category:** `Optional later support`

**Purpose:**
Applies `support_healing_aura` behavior to a named ascension reward definition in `ascension_rewards`.

**Typical Uses:**
- Combine into permanent player reward identities selected through ascension offers.
- Fits `Optional later support`-focused preset compositions.

**Parameters:**
- `amount` (number).
- Flat additive scalar unless component-specific behavior states otherwise.
- Initial design target: may gain explicit bounds or extra keys when implemented.

**Defaults / Notes:**
- **Status:** `planned` (initial design target).
- Component entry defaults: `enabled=true`, `priority=0`, `parameters={}`.
- Component-level conditions/conflicts use `conditions`, `exclusions`, and `conflicts_with` in current v1 codecs.

**Compatibility Notes:**

**Example Snippet:**
```json
{ "type": "worldawakened:support_healing_aura", "parameters": { "amount": 1.0 } }
```

## 5. Component Parameter Conventions
- Use flat values for additive fields (`amount`) and multiplicative fields for multipliers (`multiplier`).
- Keep unit names explicit (`seconds`, `count`, `max`, `range_blocks`).
- Prefer `seconds` for durations/cooldowns unless a component explicitly documents ticks.
- Treat range values as block units unless a component says otherwise.
- Use canonical IDs for entity/effect/damage-type references (`namespace:path`).
- Keep booleans explicit and single-purpose.
- Define min/max bounds in validators when adding new numeric parameters.
- Prefer descriptive parameter names over shorthand.

## 6. Validation and Error Behavior
- Unknown component type -> validation error.
- Missing required parameter -> validation error.
- Invalid parameter type/value -> validation error.
- Impossible parameter combination -> validation error.
- Incompatible component combination -> validation error.
- Missing required companion component -> validation error.
- Forbidden companion component present -> validation error.
- Forbidden duplicate policy violation -> validation error.
- `max_instances` exceeded -> validation error.
- Explicit conflict or stacking-group overflow -> validation error.
- Empty components array -> validation error.
- Invalid authored definition disables that object, not unrelated systems.

Recommended error code families:
- `WA_MUTATION_COMPONENT_INVALID`
- `WA_MUTATION_COMPONENT_PARAM_INVALID`
- `WA_MUTATION_COMPONENT_CONFLICT`
- `WA_MUTATION_COMPOSITION_EMPTY`
- `WA_ASCENSION_COMPONENT_INVALID`
- `WA_ASCENSION_COMPONENT_PARAM_INVALID`
- `WA_ASCENSION_COMPONENT_CONFLICT`
- `WA_ASCENSION_COMPOSITION_EMPTY`
- `WA_ASC_COMPONENT_NOT_SUPPRESSIBLE`
- `WA_ASC_SUPPRESSION_GROUP_REQUIRED`
- `WA_ASC_SUPPRESSION_INVALID_PARTIAL`

Current implementation note:
- Runtime diagnostics currently use shared component codes such as `WA_COMPONENT_TYPE_UNKNOWN`, `WA_COMPONENT_PARAMETERS_INVALID`, `WA_COMPONENT_COMPOSITION_INVALID`, `WA_COMPONENT_ARRAY_EMPTY`, and `WA_COMPONENT_BUDGET_EXCEEDED`.
- Keep this reference aligned with real codec/validator behavior and shipped external example-pack content.

## 7. Built-In Preset Examples
Built-in presets are authored datapack compositions, not privileged code-only identities.

Mutation preset examples:
- `juggernaut_preset` -> `max_health_multiplier` + `armor_bonus` + `knockback_resistance_bonus` + `movement_speed_bonus` (shipped external example-pack content).
- `summoner_preset` -> `reinforcement_summon` + `summon_cooldown` + `summon_cap` + `max_health_bonus` (shipped external example-pack content).
- `hunter_preset` -> `wall_sense` + `target_range_bonus` + `pursuit_speed_boost` (conceptual example).
- `unyielding_hunter` -> `max_health_bonus` + `debuff_resistance` + `wall_sense` (conceptual mutation example).
- `summoner_juggernaut` -> `reinforcement_summon` + `summon_cooldown` + `max_health_multiplier` + `armor_bonus` (conceptual hybrid example).

Ascension preset examples:
- `ember_blood` -> `armor_toughness_bonus` + `attack_damage_bonus` + `fire_resistance_passive` + `healing_efficiency_bonus` (shipped external example-pack content).
- `grave_stride` -> `movement_speed_bonus` + `step_height_bonus` + `night_vision_passive` + `fall_damage_reduction` + `knockback_resistance_bonus` (shipped external example-pack content).
- `predator_sense` -> `hostile_wall_sense` + `loot_detection` + `crit_bonus` (conceptual example).
- `unyielding_will` -> `extra_revival_buffer` + `debuff_resistance` + `mutation_resistance_bonus` (conceptual example).

## 8. Contributor Update Rules
- Update this document in the same change whenever component IDs or schemas change.
- Keep status explicit for every component entry using shared taxonomy labels (`implemented`, `planned`, `reserved`, `deprecated`).
- Keep status labels synchronized with `docs/WEB_AUTHORING_TOOL_SPEC.md` feature-marker expectations.
- Keep composition semantics aligned with shared framework contracts in `docs/SPECIFICATION.md`.
- Keep examples aligned with real codecs, validators, and external example-pack content.
- Do not reintroduce hardcoded preset assumptions in this reference.
- Keep this file as practical authoring documentation, not speculative wishlist text.


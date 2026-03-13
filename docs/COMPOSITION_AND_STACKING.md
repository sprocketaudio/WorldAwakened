# World Awakened Composition and Stacking Contract

Canonical contract for duplicate handling, conflict resolution, ordering, budget rules, and no-op detection in component-based authored definitions.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Mutation and ascension component composition semantics

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever composition fields, duplicate/conflict policies, ordering semantics, or budget/no-op behavior changes.
- Keep this file aligned with runtime resolver implementation, validation diagnostics, and debug inspection output.

---

## 1. Purpose

This document defines one canonical composition algorithm for component-based authored definitions.

Covered authored objects:
- `mob_mutators`
- `ascension_rewards`
- any future object type that declares `components[]` and opts into shared composition semantics

Hard rule:
- no subsystem may define a separate duplicate/conflict/ordering/budget algorithm without promoting that change into this file and `SPECIFICATION.md`.

---

## 2. Composition Applicability and Domain Boundaries

Primary domains:
- mutation domain: components used in `mob_mutators`
- ascension domain: components used in `ascension_rewards`

Boundary rules:
- mutation components are invalid in ascension rewards unless explicitly dual-domain in component metadata
- ascension components are invalid in mutators unless explicitly dual-domain in component metadata
- cross-domain fallback conversion is not allowed
- unknown component type is a validation error

Optional future domains must declare:
- domain ID
- allowed object types
- legal side effects
- persistence ownership

---

## 3. Canonical Resolution Pipeline

All component compositions must be resolved through this exact sequence:

1. Parse and normalize entries
2. Drop entries with `enabled=false`
3. Resolve component type metadata from registry
4. Validate domain legality
5. Validate parameter schema
6. Validate status policy (`implemented`, `planned`, `reserved`, `deprecated`)
7. Evaluate component-local `conditions` / `exclusions`
8. Build candidate set for this authored definition
9. Apply duplicate policy per type/stacking group
10. Apply conflict-set policy
11. Apply companion/dependency constraints
12. Apply deterministic ordering
13. Compute effective component budget cost
14. Apply budget rules
15. Detect no-op effective results
16. Emit resolved component list + diagnostics metadata

Hard rule:
- each phase may only consume output from previous phases; later phases must not retroactively skip required earlier checks.

---

## 4. Duplicate Policy

Duplicate policy is evaluated after condition filtering and before conflict filtering.

Canonical policy values:
- `reject`: more than one matching entry is a validation error
- `allow_identical`: duplicates allowed only when normalized parameters are identical; otherwise validation error
- `stack`: duplicates allowed up to `max_instances`; overflow is rejected
- `last_wins`: highest-precedence entry survives, all lower-precedence duplicates are dropped with diagnostics
- `merge`: entries are merged with component-type merge semantics; unsupported merge is a validation error

Default:
- unless explicitly declared by component metadata, policy is `reject`

Precedence for duplicate resolution:
1. higher `priority`
2. higher `composition_priority` (if present)
3. later authored order
4. stable tiebreaker by normalized entry hash

---

## 5. Conflict-Set Policy

Conflicts are evaluated after duplicate resolution.

Conflict sources:
- explicit `conflicts_with` declarations
- registry-declared conflict sets
- shared stacking-group exclusivity

Canonical outcomes:
- `hard_error`: composition is invalid and owning object/branch is disabled
- `last_wins`: lower-precedence conflicting entries are dropped
- `first_wins`: highest-precedence accepted entry remains, later conflicts dropped
- `merge_if_compatible`: both entries remain only if component-type compatibility predicate passes

Default:
- explicit conflict declarations use `hard_error` unless component metadata declares another policy

Hard rules:
- silent conflict auto-resolution is forbidden
- every dropped/rejected component must emit a reason code

---

## 6. Deterministic Ordering

Resolved component execution order uses this canonical precedence:
1. component domain phase bucket (core stats -> defensive -> offensive -> mobility -> utility -> presentation; domain-specific)
2. explicit `composition_priority` descending
3. action `priority` descending when component supports action-like side effects
4. authored order
5. stable type ID + entry hash tiebreak

Order guarantees:
- same input yields same output order across reloads
- order must not depend on map iteration order
- sorted order must be visible in debug inspect output

---

## 7. Budget Interaction Rules

Budget evaluation uses resolved components after duplicates/conflicts are applied.

Budget fields:
- candidate-level limit (for example mutator `component_budget`)
- component cost from registry metadata (default `1` unless declared otherwise)

Rules:
- total cost = sum of resolved component costs
- if total cost exceeds budget, candidate is rejected
- budget rejection disables only the affected candidate/object branch
- budget failure must not crash unrelated systems

Runtime behavior:
- spawn-time mutation pool rolls may skip over-budget candidates and continue bounded reroll flow
- ascension reward definitions that are permanently over budget are validation errors when a budget contract is active

---

## 8. No-Op Detection

No-op detection runs after final resolution.

No-op cases:
- all components removed by conditions/conflicts
- remaining components produce zero effective side effects
- only presentation components remain where a behaviorful result is required by object policy

Outcome policy:
- `error` when object requires at least one behaviorful component
- `warning` when no-op is allowed but likely unintended

No-op detection must emit:
- owning object ID
- resolved component list
- no-op reason code
- resolution path

---

## 9. Resolved Composition Examples

### 9.1 Duplicate Rejection

Input:

```json
{
  "id": "my_pack:elite_tank",
  "components": [
    { "type": "worldawakened:max_health_bonus", "parameters": { "amount": 8.0 } },
    { "type": "worldawakened:max_health_bonus", "parameters": { "amount": 6.0 } }
  ]
}
```

Result:
- fails duplicate policy `reject`
- object disabled
- emit `WA_COMPONENT_DUPLICATE_DISALLOWED`

### 9.2 Conflict Last-Wins

Input:

```json
{
  "id": "my_pack:predator_variant",
  "components": [
    { "type": "worldawakened:fire_package", "priority": 10 },
    { "type": "worldawakened:frost_package", "priority": 20 }
  ]
}
```

Assumption:
- registry conflict policy for these types is `last_wins`

Result:
- keep `frost_package`
- drop `fire_package`
- emit drop diagnostic with precedence reason

### 9.3 Budget Rejection

Input:

```json
{
  "id": "my_pack:glass_cannon",
  "component_budget": 4,
  "components": [
    { "type": "worldawakened:max_health_multiplier" },
    { "type": "worldawakened:attack_damage_multiplier" },
    { "type": "worldawakened:projectile_split" }
  ]
}
```

Assumption:
- resolved cost is `6`

Result:
- candidate rejected as over budget
- emit `WA_COMPONENT_BUDGET_EXCEEDED`

---

## 10. Inspection and Trace Requirements

Composition resolution must be inspectable through debug surfaces defined in [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md).

Minimum exposed fields:
- owning object ID
- input component list
- resolved component list in execution order
- dropped/rejected components
- reason codes and categories
- effective cost and budget outcome

---

## 11. Phase Alignment (MVP Roadmap)

This contract aligns to future implementation phases as follows:
- Phase 5: mutation composition enforcement (duplicates/conflicts/order/budget/no-op) for spawn-time mutators
- Phase 6: integration of composition outcomes with bounded rule/action and spawn guardrails
- Phase 10: web authoring tool composition previews and warnings aligned with this resolver contract
- Phase 11: hardening and validation quality for composition diagnostics and deterministic resolution

Roadmap sync rule:
- if phase scope changes alter composition requirements, update this file and `SPECIFICATION.md` in the same change.

---

## 12. Contributor Update Rules

- Keep this file synchronized with component metadata behavior in Java registries.
- Keep example outcomes aligned with validator and runtime behavior.
- If a new policy keyword is introduced, update:
  - [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
  - [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
  - [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
  - [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
  - [SPECIFICATION.md](SPECIFICATION.md)
- Do not merge composition behavior changes without explicit diagnostics mapping.

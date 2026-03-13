# World Awakened Web Authoring Tool Specification

Browser-based datapack authoring and validation companion for World Awakened.

- Document status: Active v1 companion spec
- Last updated: 2026-03-13
- Scope: v1 required deliverable (late-phase implementation)

---

## 0. Governance and Authority Boundary

This document defines the contract for the World Awakened web authoring tool.

Hierarchy and sync rules:
- [SPECIFICATION.md](SPECIFICATION.md) remains the top-level product contract.
- This file is the detailed contract for web authoring behavior and workflows.
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md) remains the canonical datapack shape and field reference.
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md), [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md), [ACTION_REFERENCE.md](ACTION_REFERENCE.md), and [SCOPE_MATRIX.md](SCOPE_MATRIX.md) remain canonical shared-contract references consumed by the tool.
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md) remains the canonical resolver contract for duplicate/conflict/order/budget/no-op composition behavior.
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md) remains the canonical runtime trace/provenance contract for tooling-facing diagnostics alignment.
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md) remains the canonical contract for rule indexing limits and hot-path budget warnings.
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md) remains the canonical diagnostics taxonomy for tool validation output mapping.
- [docs/README.md](README.md) remains the docs set map and cross-update reference.
- [README.md](../README.md) and [AGENTS.md](../AGENTS.md) must reflect scope and roadmap changes from this file.

Update rule:
- Update this file in the same change whenever authoring workflows, validation behavior, schema/version handling, or import/export guarantees change.
- Keep this file aligned with runtime contracts so the browser tool never becomes a divergent authority surface.

Hard authority boundary:
- website: authoring and validation only
- mod: loading and execution authority
- there must be one shared datapack format across both

---

## 1. Purpose

Provide a browser-based tool to visually create, edit, validate, import, and export World Awakened datapacks without requiring large manual JSON authoring for common workflows.

The tool must:
- reduce authoring friction
- preserve full JSON-level control for advanced users
- round-trip existing datapacks without inventing a second data model

---

## 2. Release Position

This system is required for v1 release quality but should be implemented after core gameplay systems and schema contracts stabilize.

Recommended placement:
- late-phase implementation after core runtime systems are stable
- prior to final release hardening sign-off

---

## 3. Supported Object Types

The tool must support authoring and editing of all v1 datapack object sets:
- `stages`
- `trigger_rules`
- `rules`
- `mob_mutators`
- `mutation_pools`
- `ascension_rewards`
- `ascension_offers`
- `loot_profiles`
- `invasion_profiles`
- `integration_profiles`

The tool must also support:
- conditions (including logical composition)
- components (mutation and ascension)
- references between objects
- implemented/planned/deprecated feature markers
- validation results and diagnostics

---

## 4. Primary User Workflows

Required workflows:
1. Create new datapack project.
2. Start from template or preset content.
3. Import existing datapack content.
4. Validate configuration and resolve problems.
5. Export valid datapack output ready for Minecraft.

---

## 5. Authoring Layers

The tool must expose three synchronized editing layers over one shared project model.

Layer 1: Visual Builder
- guided forms
- picker controls
- relationship selectors

Layer 2: Structured Editor
- direct editing of object/list structure with less abstraction than forms

Layer 3: Raw JSON Editor
- advanced direct JSON editing through a full code editor

Required behavior:
- all layers edit the same underlying data model
- switching layers must preserve semantic content
- round-trip behavior should remain stable unless the user intentionally changes data

---

## 6. Application Modules

### 6.1 Dashboard

Must display:
- project or datapack name
- namespace
- object counts by type
- validation status
- unresolved reference count
- warnings summary

### 6.2 Object Libraries

Provide per-type browsing for all supported object types with:
- search
- filtering
- implemented/planned/deprecated markers
- reference navigation (`references` and `used by`)

### 6.3 Visual Editors

Provide type-aware editing experiences for each object type, including references and relationships.

Ascension offer editors must expose the authored repeat-policy field for later-offer reward resurfacing:
- `reward_repeat_policy = block_all`
- `reward_repeat_policy = allow_forfeited_only`
- `reward_repeat_policy = allow_all`

Authoring note:
- `block_all` blocks both previously chosen and previously forfeited rewards
- `allow_forfeited_only` still blocks chosen rewards, but allows previously forfeited rewards to reappear
- `allow_all` allows both previously chosen and previously forfeited rewards to reappear

### 6.4 Condition Builder

Must support:
- `AND`
- `OR`
- `NOT`
- condition parameter editing
- scope-aware validation feedback

### 6.5 Component Builder

Must support:
- component registry selection
- parameter editing
- ascension suppression metadata editing (`suppressible_individually`, `suppression_policy`, `suppression_group`)
- deterministic ordering controls
- conflict detection
- duplicate/conflict/order resolution preview against canonical rules
- budget and no-op outcome preview
- implemented/planned/deprecated markers

Semantics source:
- component composition behavior must follow [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)

### 6.6 Validation Panel

Must display:
- invalid parameters
- missing references
- incompatible components
- invalid schema shape
- unsupported feature usage
- suppression validation diagnostics (`WA_ASC_COMPONENT_NOT_SUPPRESSIBLE`, `WA_ASC_SUPPRESSION_GROUP_REQUIRED`, `WA_ASC_SUPPRESSION_INVALID_PARTIAL`)
- optional runtime-surface compatibility diagnostics for compat-sensitive component branches (`WA_RUNTIME_SURFACE_OPTIONAL_UNAVAILABLE` and specialized surface-unavailable codes)
- reason-code category mapping aligned with runtime diagnostics
- performance-budget warnings (rule bucket sizes, actions-per-rule, mutator/component hot-path thresholds)

Severity behavior:
- warnings are non-blocking
- errors block export

### 6.7 Import and Export Center

Central workspace for content import and export actions.

---

## 7. Import and Export Requirements

### 7.1 Import

Support:
- folder upload
- zip upload
- individual JSON file import

Import behavior:
- detect schema version metadata
- isolate invalid objects where possible instead of failing the entire project
- preserve original IDs/references unless user edits them

### 7.2 Export

Support:
- folder export
- zip export
- deterministic JSON output
- canonical datapack folder structure

Export structure must align with the mod datapack layout:

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

## 8. Templates and Presets

The tool should ship with clonable/editable starter templates.

Mutation templates:
- juggernaut
- summoner
- hunter
- elemental elite
- thorned defender

Ascension templates:
- tank
- hunter
- explorer
- loot focused
- offensive sustain

Rule templates:
- unlock stage on dimension entry
- unlock stage on boss kill
- trigger invasion on milestone
- apply mutation pool by stage

---

## 9. Validation System

Three required validation layers:

1. Schema validation
- required fields
- field types
- structural shape

2. Semantic validation
- incompatible components
- invalid condition payloads
- missing required companion components
- invalid suppression metadata (`suppression_policy`, `suppression_group`, component-level suppressibility)
- reject `suppression_policy=independent|grouped` unless `suppressible_individually=true`

3. Cross-object validation
- missing references
- unused objects
- invalid pool references
- invalid rule targets

Export policy:
- warnings do not block export
- errors block export

---

## 10. JSON Schema Strategy

Canonical structure contracts should be represented as JSON Schema and versioned with the mod.

Schema coverage should include:
- stages
- trigger rules
- runtime rules
- mutators
- pools
- rewards
- offers
- conditions
- components
- loot profiles
- invasion profiles
- integration profiles

---

## 11. Technology Stack Baseline

Preferred v1 stack:
- frontend framework: Next.js with TypeScript
- schema-driven form layer: `react-jsonschema-form`
- validation engine: AJV
- raw code editor: Monaco Editor

Implementation note:
- equivalent alternatives are acceptable only if they preserve the same schema-driven, validation-first behavior and round-trip guarantees

---

## 12. Backend Requirements

Initial version should be primarily client-side.

Optional backend helpers:
- zip generation
- very large import parsing
- schema distribution

Initial constraints:
- avoid account requirement
- avoid mandatory persistent server dependency

---

## 13. Project Model

The tool should treat each datapack workspace as a project.

Project metadata includes:
- namespace
- datapack version
- schema version
- object collections
- validation results

Local persistence:
- projects may be saved locally in-browser for iterative authoring

---

## 14. Search and Discoverability

The tool must support:
- global search
- object filtering
- unused-object discovery
- broken-reference discovery
- component usage lookup

---

## 15. Documentation Integration

Inline docs should exist for:
- components
- parameters
- conditions
- examples
- compatibility rules

UI behavior:
- hover tooltips should link to relevant docs when available

---

## 16. Versioning and Compatibility

The tool should support multiple World Awakened versions where schemas are available.

Feature labels:
- implemented
- planned
- deprecated

Carrier-backed component note:
- implemented component labels must reflect owned-carrier support, not just schema validity
- for example `fire_resistance_passive` and `night_vision_passive` are `implemented` because the runtime owns their refresh/revoke path through WA-owned server/client carriers rather than shared vanilla effect slots; the night-vision carrier is lightmap-backed on the owning client instead of borrowing the vanilla effect slot

Importing older datapacks:
- allowed when schema compatibility exists
- should emit migration warnings when shape or semantics are outdated

---

## 17. Out-of-Scope for Initial Version

Not included initially:
- multiplayer real-time collaboration
- cloud save accounts
- live runtime control connection to Minecraft servers
- marketplace-style sharing
- plugin scripting

---

## 18. Acceptance Criteria

v1 completion criteria:
- create a datapack from scratch
- import an existing datapack
- edit all supported object types
- compose components visually
- compose conditions visually
- validate configuration live
- resolve references
- export a valid datapack
- support raw JSON editing
- significantly reduce manual JSON authoring complexity

---

## 19. Delivery Roadmap

Recommended internal delivery slices:

Phase A
- schemas
- validation core
- import/export core
- project model

Phase B
- visual editors
- component builder
- condition builder
- templates

Phase C
- advanced JSON editor integration
- documentation integration
- reference navigation

Phase D
- validation polish
- search improvements
- export stability hardening
- performance-budget warning polish and diagnostics clarity

Main roadmap alignment:
- this document maps primarily to `SPECIFICATION.md` Phase 10 (implementation) and Phase 11 (hardening).

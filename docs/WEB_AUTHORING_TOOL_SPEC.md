# World Awakened Web Authoring Tool Specification

Hosted React-based authoring and validation platform for World Awakened datapacks and live-linked runtime editing sessions.

- Document status: Active v1 companion spec
- Last updated: 2026-03-18
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
- website: authoring, validation, and editor workflow authority
- mod/runtime: execution and apply-commit authority
- hosted web tooling must never become a second gameplay authority
- live-linked apply operations must always be runtime-validated before commit
- there must be one shared datapack/data contract across raw datapacks, runtime-authored state, and hosted-editor session payloads

---

## 1. Purpose

Provide a hosted browser-based tool to visually create, edit, validate, import, and export World Awakened datapacks without requiring large manual JSON authoring for common workflows, while also supporting optional live linked editing against a running game/server runtime.

The tool must:
- reduce authoring friction
- preserve full JSON-level control for advanced users
- round-trip existing datapacks without inventing a second data model
- support two editing modes:
  - offline project mode (import/export-first)
  - live linked session mode (runtime-connected, revision-safe apply)

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

Mutation pool editor minimum field support:
- `mutation_chance`
- `allow_from_spawner`
- `allow_from_trial_spawner`
- `max_mutators_per_entity`
- validation results and diagnostics

---

## 4. Primary User Workflows

Required workflows:
1. Create new datapack project.
2. Start from template or preset content.
3. Import existing datapack content.
4. Validate configuration and resolve problems.
5. Export valid datapack output ready for Minecraft.
6. Start a live linked editing session from a running game/server (`/wa web edit` baseline command surface).
7. Load current runtime-authored World Awakened state from the linked session in the hosted editor.
8. Save/apply validated changes back to runtime through the linked session relay path.

Minimum live linked flow:
1. user runs `/wa web edit`
2. mod gathers current editable World Awakened state
3. mod opens/registers a linked session with the hosted backend
4. backend returns a hosted editor URL
5. user opens the hosted URL
6. editor loads linked session data
7. editor loads shared schema + registry-backed metadata
8. user edits through visual/structured/raw layers
9. editor validates continuously
10. user presses save/apply
11. backend relays apply payload to mod/runtime
12. runtime validates and commits or rejects
13. editor receives result and diagnostics

Runtime command surface guidance:
- required baseline: `/wa web edit`
- optional future variants:
  - `/wa web edit live`
  - `/wa web edit export`
  - `/wa web session status`
  - `/wa web session revoke`

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
- in live linked mode, all three layers still edit one linked project model and must preserve stable round-trip semantics

---

## 6. Application Modules

### 6.1 Dashboard

Must display:
- project or datapack name
- namespace
- editing mode (`offline`, `imported`, `live_linked`)
- object counts by type
- validation status
- unresolved reference count
- warnings summary
- linked-session status (when live mode is active)
- connected runtime version (when live mode is active)
- connected schema version (when live mode is active)
- connected modpack metadata summary (when live mode is active)
- registry metadata freshness/last refresh timestamp (when live mode is active)
- apply history summary, last sync timestamp, and revision-mismatch state (when live mode is active)

### 6.2 Object Libraries

Provide per-type browsing for all supported object types with:
- search
- filtering
- implemented/planned/deprecated markers
- reference navigation (`references` and `used by`)

### 6.3 Visual Editors

Provide type-aware editing experiences for each object type, including references and relationships.

Registry-aware selector behavior:
- in live linked mode, selectors must prefer runtime-provided registry metadata over static baked catalogs
- selector options should reflect the connected modpack runtime snapshot (entity types, entity tags, items, blocks, dimensions, biomes, loot tables, effects, and other WA-relevant registries)
- in offline mode, selectors may use shipped schema metadata and optional cached catalogs

Ascension offer editors must expose the authored repeat-policy field for later-offer reward resurfacing:
- `reward_repeat_policy = block_all`
- `reward_repeat_policy = allow_forfeited_only`
- `reward_repeat_policy = allow_all`

Authoring note:
- `block_all` blocks both previously chosen and previously forfeited rewards
- `allow_forfeited_only` still blocks chosen rewards, but allows previously forfeited rewards to reappear
- `allow_all` allows both previously chosen and previously forfeited rewards to reappear

Loot/reward editor requirements for Phase 7:
- surface default additive/inject-first behavior as the safe baseline
- mark destructive modes (`replace_entries`, `remove_entries`) as policy-gated and non-default
- show that Phase 7 reward evaluation is downstream-event driven (`entity_killed`, `invasion_completed`, and other explicitly documented WA-owned reward events)
- explain that reward-capable subsystems contribute reward intent/eligibility only; final reward application is canonical-resolver owned
- show canonical loot-context fields used for matching (`loot_context_type`, `loot_table_id`, `entity_type`, `entity_is_mutated`, `mutation_tags`, `player`, `dimension`, `stage context`, `invasion context`) plus supporting identity/policy fields (`target_type`, `target_id`, compat/scalar policy context)
- provide guided condition authoring patterns for `loot_table`, `entity_is_mutated`, `invasion_active`, and `invasion_tag`
- warn that reward definitions must not introduce progression mutations (stage unlocks, trigger-eligibility rewrites, rule-identity mutation)
- label reward-scaling controls as unavailable in Phase 7 (difficulty/challenge scalar influence deferred)
- surface repeatability controls/metadata with safe defaults (`non-repeatable` per event unless explicitly marked repeatable)
- enforce and explain activation-only-by-conditions: loot profiles must never be directly attached to entities, mutation pools, mutators, structures, invasion profiles, or invasions
- enforce fail-closed behavior for missing required loot-context fields and reject partial-evaluation fallback behavior

Invasion editor requirements for Phase 8:
- model invasions as pressure events (scheduler + active state + warning + duration + cooldown + temporary pressure modifier)
- support v1 profile fields (`id`, `display_name`, `enabled`, `trigger_mode`, `conditions`, `stage_filters`, `dimensions`, `biome_filters`, `min_players`, `cooldown_seconds`, `warning_seconds`, `duration_seconds`, `pressure_modifier`, `reward_profile`, `tags`)
- expose runtime-context expectations (`invasion_active`, `invasion_profile_id`, `invasion_tags`, `warning_active`, `invasion_remaining_duration`, `pressure_modifier`)
- clearly mark WA-owned wave-orchestration fields as deferred/not available in Phase 8 v1 (`wave_count`, `wave_interval`, `spawn_budget`, `spawn_composition`, `elite_chance`, `boss_wave`, `max_active_entities`)
- provide guided condition authoring for `invasion_active` (with optional `profile_id`) and `invasion_tag` across mutation-pool and loot/reward gating flows

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
- typed equipment editors for implemented mutation components such as `worldawakened:equip_item` (`item`, `slot`, `drop_chance`, `enchantments[]`)
- typed presentation editors for implemented mutation components such as `worldawakened:glow_style` (`color`, `brightness`, `see_through_walls`, `pulse`), `worldawakened:effect_particles` (`effect_type`, `color`, `count`, `interval_ticks`), and `worldawakened:ambient_particles` (`particle`, `color`, `size`, `count`, `offset_x`, `offset_y`, `offset_z`, `speed`, `interval_ticks`)
- ascension suppression metadata editing (`suppressible_individually`, `suppression_policy`, `suppression_group`)
- deterministic ordering controls
- conflict detection
- duplicate/conflict/order resolution preview against canonical rules
- component-count guardrail and no-op outcome preview
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

### 6.8 Live Session Panel

When operating in live linked mode, the application must provide a dedicated session/status panel showing:
- linked session ID
- token/session expiry timer
- runtime connection state
- runtime and schema version alignment state
- modpack metadata summary
- last runtime sync timestamp
- pending local changes indicator
- apply history and latest apply result
- revision mismatch and stale-session state

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
- reward-policy boundary violations (unsupported reward event source, destructive mode without explicit policy enablement, reward-to-progression mutation attempts, scalar-dependent reward authoring in Phase 7)
- reward pipeline boundary violations (direct-apply bypass attempts, invalid repeatable metadata, duplicate contributor usage without explicit repeatable permission)
- reward activation boundary violations (direct attachment attempts on entities/mutation pools/mutators/structures/invasion profiles/invasions instead of condition-driven matching)

Export policy:
- warnings do not block export
- errors block export

---

## 10. JSON Schema Strategy

Canonical structure contracts should be represented as JSON Schema and versioned with the mod.

Schema/UI authority rule:
- runtime/exported schema contracts remain canonical
- the website consumes shared schema + runtime metadata and must not become an independent schema authority
- editor UX must avoid duplicating runtime logic when a shared schema/metadata contract already exists

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
- frontend architecture: React + TypeScript
- recommended app framework: Next.js
- schema-driven form layer: `react-jsonschema-form`
- validation engine: AJV
- raw code editor: Monaco Editor

Implementation note:
- the React/TypeScript editor architecture is expected to be componentized and schema-driven for nested condition trees, weighted loot entries, mutation/component parameter forms, invasion profile editing, and live validation panels
- equivalent alternatives are acceptable only if they preserve the same schema-driven, validation-first behavior, round-trip guarantees, and shared-contract alignment with runtime schemas/metadata

---

## 12. Backend Requirements

Live linked mode requires a hosted backend/session relay service.

Primary hosted model:
- centrally hosted World Awakened editor frontend
- centrally hosted backend session service
- centrally hosted relay transport between browser sessions and mod/runtime sessions
- mod/runtime connects outbound to hosted backend (HTTPS and/or WebSocket)
- browser connects only to hosted website/backend
- normal supported flow must not require opening a dedicated public HTTP API port on the game/server

Required backend responsibilities:
- session creation and lifecycle management
- browser-session linking and mod-session linking
- relay transport for session payload and apply operations
- signed token verification and session expiry enforcement
- revision/version conflict checks
- apply result relay and diagnostics reporting
- optional temporary draft buffering and/or patch buffering

Session safety requirements:
- unique session ID per live linked editing session
- short-lived signed token
- explicit session expiry
- revision/version tracking on editable state
- support one or more active browser tabs
- stale sessions must fail safely and must not silently overwrite newer runtime changes
- apply operations must fail cleanly on revision mismatch
- runtime validation is mandatory before commit
- invalid objects fail closed with diagnostics, with branch/object isolation where possible

Optional backend helpers:
- zip generation
- very large import parsing
- schema distribution and cache warming

Optional deployment note:
- self-hosting may be supported later, but centrally hosted mode is the primary v1 target

---

## 13. Project Model

The tool should treat each authoring workspace as a project.

Project kinds:
- local offline project
- imported datapack project
- live linked runtime project

Common project metadata includes:
- namespace
- datapack version
- schema version
- object collections
- validation results

Local persistence:
- projects may be saved locally in-browser for iterative authoring

Live linked project metadata (minimum):
- linked session status
- linked session ID
- token/session expiry state
- connected runtime version
- connected schema version
- connected modpack metadata
- registry metadata freshness
- last sync timestamp
- pending local changes indicator
- apply result history
- revision mismatch/stale-session state

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
- arbitrary remote server administration
- marketplace-style sharing
- plugin scripting

In-scope clarification:
- linked editing of World Awakened-authored runtime state is in scope
- gameplay execution control remains runtime-owned
- non-World Awakened server management is out of scope

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
- start a linked web editing session from the running mod/runtime
- load current World Awakened-authored state from a linked runtime session
- show registry-aware selector values sourced from the connected runtime/modpack
- validate live-linked content before apply
- reject invalid apply operations safely at runtime with diagnostics
- commit successful apply operations into running runtime state cleanly
- fail stale or conflicting browser sessions safely with clear revision diagnostics

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

Phase E
- hosted backend/session relay hardening
- linked-session conflict handling polish
- apply safety and diagnostics polish
- runtime-registry metadata freshness and caching polish
- auth tightening (token expiry, signature checks, revoke flows)
- regression checks that authoring/validation surfaces preserve earlier-phase guardrails (Phase 7 reward boundaries, Phase 8 pressure-event invasion model, Phase 9 compat influence limits)

Main roadmap alignment:
- this document maps primarily to `SPECIFICATION.md` Phase 10 (implementation) and Phase 11 (hardening).

---

## 20. Regression Anti-Patterns (Do Not Reintroduce)

Do not reintroduce:
- direct attachment systems for rewards
- wave-based invasion spawning or WA-owned spawn orchestration
- hidden scalar stacking across unrelated systems
- duplicated/parallel reward pipelines
- schema duplication between runtime and web tool payloads
- hardcoded entity/loot catalogs in live linked mode
- web tooling behavior that bypasses runtime validation or becomes gameplay authority

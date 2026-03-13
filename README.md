# World Awakened

World Awakened is a NeoForge mod framework for Minecraft 1.21.1 focused on **server-authoritative progression-driven difficulty**.

- Last updated: 2026-03-13
- Documentation index/read order: [docs/README.md](docs/README.md)

The core model is:
- Java code defines execution systems and behavior types.
- Datapacks define progression stages, rules, authored mutation/reward compositions, loot evolution, invasions, and integrations.
- All subsystems share one canonical condition model, action model, scope model, component composition/conflict model, and status taxonomy.
- Datapack object files use the standard layout `data/<namespace>/<object_type>/*.json` (no duplicate namespace folder layer).
- TOML config provides operator-level overrides, safety gates, and the `/wa debug` command-tree kill switch.
- The mod jar intentionally ships no gameplay-active datapack content.
- Example/default content is distributed as optional external datapacks using the same schemas available to pack authors.

Repository/distribution note:
- Optional example content currently lives under `example_datapacks/worldawakened_example_pack/` and must be installed like a normal datapack.

## Status
Phase 4 core complete.  
Phase 0 foundation systems, Phase 1 stage progression, and Phase 2 trigger flow are implemented end-to-end. Phase 3 is implemented: compiled generic rule evaluation (`world | player | entity | spawn_event`), deterministic priority/cooldown/chance ordering, optional world-context condition evaluation (`world_day_gte`, `player_distance_from_spawn`) with fail-closed behavior, single-pass stage propagation (pre-action snapshots), runtime rule cooldown/consumed persistence, per-pass debug trace IDs, explicit `global`/`player` operator targeting for stage/trigger/rule inspection in `PER_PLAYER` mode, optional command-side dimension overrides for manual trigger/rule inspection, and `/wa dump active_rules`. Phase 4 is now implemented: player-scoped ascension runtime offer instances with idempotent grant keys and queued one-pending semantics, clickable chat notifications, minimal client GUI + packet selection flow, server-authoritative selection validation, login/respawn reward reconciliation, operator-friendly ascension selection/reversal controls (`choose`, `active`, `reopen`, `clear`), copy/suggest chat actions for common operator paths, and explicit `/wa debug reset|clear` persistence-bucket commands for global/player testing and recovery.
Architecture baseline correction is applied: mutation definitions and ascension reward definitions are component-based authored objects, and the framework jar remains content-empty until a datapack is installed.
Current active milestone is Phase 5 (Mutators and Pools runtime).
The browser-based web authoring tool is part of v1 scope and is planned as a late-phase companion deliverable after core runtime systems stabilize.

## Primary Documentation
- Documentation index and required read order: [docs/README.md](docs/README.md)
- Human-friendly wiki index: [docs/wiki/README.md](docs/wiki/README.md)
- Full design spec: [docs/SPECIFICATION.md](docs/SPECIFICATION.md)
- Web authoring tool spec: [docs/WEB_AUTHORING_TOOL_SPEC.md](docs/WEB_AUTHORING_TOOL_SPEC.md)
- Datapack format/authoring guide: [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md)
- Component authoring reference: [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md)
- Composition and stacking contract: [docs/COMPOSITION_AND_STACKING.md](docs/COMPOSITION_AND_STACKING.md)
- Condition reference: [docs/CONDITION_REFERENCE.md](docs/CONDITION_REFERENCE.md)
- Action reference: [docs/ACTION_REFERENCE.md](docs/ACTION_REFERENCE.md)
- Scope legality matrix: [docs/SCOPE_MATRIX.md](docs/SCOPE_MATRIX.md)
- Debug and inspection contract: [docs/DEBUG_AND_INSPECTION.md](docs/DEBUG_AND_INSPECTION.md)
- Performance budgets and runtime guardrails: [docs/PERFORMANCE_BUDGETS.md](docs/PERFORMANCE_BUDGETS.md)
- Validation/error code taxonomy: [docs/VALIDATION_AND_ERROR_CODES.md](docs/VALIDATION_AND_ERROR_CODES.md)
- Preset/template catalog: [docs/PRESET_CATALOG.md](docs/PRESET_CATALOG.md)
- Future backlog and long-term ideas: [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md)
- Future in-game/admin runtime UI concept (post-v1): [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md)
- Agent operating guidance: [AGENTS.md](AGENTS.md)

## Documentation Sync Policy
- `docs/README.md` defines required read order, cross-update matrix, and shared reference-doc format expectations.
- `docs/wiki/README.md` defines the human-friendly wiki layer and when it must be updated.
- `docs/SPECIFICATION.md` is the source-of-truth design contract.
- `docs/WEB_AUTHORING_TOOL_SPEC.md` is the detailed contract for the v1 browser authoring/validation companion.
- `docs/DATAPACK_AUTHORING.md` is the source-of-truth authoring format contract for user datapacks.
- `docs/COMPONENT_REFERENCE.md` is the canonical component catalog and must be updated whenever a component type/schema changes.
- `docs/COMPOSITION_AND_STACKING.md` is the canonical component composition resolver contract (duplicate/conflict/order/budget/no-op).
- `docs/CONDITION_REFERENCE.md`, `docs/ACTION_REFERENCE.md`, and `docs/SCOPE_MATRIX.md` are canonical shared-contract references for condition/action/scope legality.
- `docs/DEBUG_AND_INSPECTION.md` is the canonical command/trace/provenance contract for runtime inspection.
- `docs/PERFORMANCE_BUDGETS.md` is the canonical hot-path performance and runtime budget guardrail contract.
- `docs/VALIDATION_AND_ERROR_CODES.md` is the canonical diagnostics taxonomy and should be updated with validator/runtime diagnostic changes.
- `docs/PRESET_CATALOG.md` must stay aligned with shipped external example-pack content or canonical authored preset/template compositions.
- `docs/FUTURE_IDEAS.md` is a non-normative backlog and does not expand MVP scope by itself.
- `docs/FUTURE_ADMIN_UI.md` is a non-normative post-v1 in-game/admin runtime UI concept and does not expand MVP scope by itself.
- The `docs/wiki/` layer is the human-readable companion set for operators, authors, testers, and server owners; keep it aligned with real workflows, commands, and recovery guidance.
- Command output is intentionally layered: default gameplay/operator output should stay concise and human-readable, while dense raw IDs/reason paths belong in inspect/debug surfaces or behind `general.debug_logging`.
- Every implementation task should reference the governing docs from `docs/README.md` + `AGENTS.md` before making changes.
- If the spec changes, update `README.md` summary sections in the same change.
- If datapack format/shape rules change, update `docs/SPECIFICATION.md` and `docs/DATAPACK_AUTHORING.md` together.
- If a change affects user mental models, command flows, testing patterns, troubleshooting, or authoring recipes, update the relevant files under `docs/wiki/` in the same change.
- If workflow expectations change, update `AGENTS.md` in the same change.
- Do not merge behavior/design changes with stale documentation.

## Target Platform
Based on current `gradle.properties`:
- Minecraft: `1.21.1`
- NeoForge: `21.1.219`
- Mappings: Parchment for `1.21.1` (`2024.11.17`)
- Mod ID: `worldawakened`
- Group: `net.sprocketgames.worldawakened`

## Product Goals (MVP)
- Configurable, datapack-defined progression stages
- Trigger-based stage unlocks (dimension, advancement, boss, etc.)
- Rule engine for stage/context-conditional actions
- Shared condition/action contracts with scope-validated execution across triggers, rules, mutators, ascension, loot, and invasions
- Optional world-context rule conditions (for example world day and player distance from spawn) as datapack inputs, not core progression drivers
- Player-scoped Ascension Choice offers with permanent exclusive rewards
- Component-based ascension reward definitions (`components[]`) with authored reward IDs
- Shared component stacking/conflict semantics (explicit duplicates, conflict sets, deterministic ordering)
- Shared status taxonomy (`implemented | planned | reserved | deprecated`) across docs, validation, and tooling
- Deterministic conflict resolution and bounded runtime evaluation
- Operator-controlled global World Awakened difficulty modifier for baseline intensity tuning
- Optional bounded challenge modifier layer (player/world scoped by policy) for accessibility and opt-in escalation
- Spawn-time mutation definitions (`mob_mutators`) composed from mutation components
- Loot evolution through profile-driven injection/replacement rules
- Apotheosis loot compatibility that composes additively and preserves Apotheosis-owned tier-gated loot behavior when integration is active
- One configurable invasion/raid-like event loop
- Generic support for modded bosses and mobs through entity IDs, tags, and datapack-defined boss classification
- Optional compatibility layer with explicit toggles
- Optional Apotheosis World Tier integration (conditions + scalar inputs)
- Debug command suite for inspection, validation, and targeted operator recovery/reset
- Browser-based datapack authoring/validation/import/export companion using the same canonical datapack format as runtime
- A framework jar that remains inert until a server/world installs a datapack

## Non-Goals for v1
- Arbitrary user scripting language
- Full vanilla spawn engine replacement
- Retroactive mutation of already-loaded mobs
- Full in-game admin GUI rule editor beyond the browser authoring companion

## Planned High-Level Implementation Sequence
1. Phase 0 (Complete): Data contracts/codecs + datapack reload + validation pipeline + initial test harness
2. Phase 1 (Complete): Stage core + persistence + stage commands
3. Phase 2 (Complete): Trigger engine + stage unlock flow
4. Phase 3 (Complete): Rule engine core + deterministic active-rule introspection + optional world-context condition evaluation + single-pass stage propagation guarantee + debug trace ID support
5. Phase 4 (Complete): Ascension Choice System + minimal GUI/packet flow + permanent reward reconciliation
6. Phase 5: Mutators + mutation pools + spawn application + mob inspect + spawn-time performance budgets (`max_mutators_per_spawn`, `max_components_per_mutator`)
7. Phase 6: Spawn pressure controls + rule-event budget guardrails (`maximum_rules_evaluated_per_event`, `maximum_actions_per_rule`) + shared effective-scalar service for global/optional challenge modifiers
8. Phase 7: Loot evolution profiles and bonus drop integration (including safe additive behavior on Apotheosis-sensitive targets)
9. Phase 8: Invasion system (scheduler, waves, and command mode MVP)
10. Phase 9: Compatibility framework + Apotheosis world tier and external tier providers (including loot-target sensitivity enforcement)
11. Phase 10: Browser web authoring tool (project model, visual/structured/raw editors, validation/import/export, performance-budget warnings)
12. Phase 11: Hardening, example datapack, and release-prep docs + `/wa debug perf|rules|mutators` surfaces

## Dev Notes
- Keep all gameplay authority server-side.
- Keep stage identifiers data-defined (no hardcoded progression names/order in code).
- Keep ascension selections player-scoped, permanent, and exclusive per offer.
- Keep mutation and ascension behavior Java-defined at the component-type layer; keep named presets/data compositions datapack-authored.
- Keep all condition/action/scope semantics on the shared framework contracts; do not create subsystem-specific variants.
- Keep component composition semantics explicit (duplicate/conflict/order/companion requirements); do not rely on accidental stacking.
- Support unknown future boss/mob mods generically through entity IDs/tags first; add dedicated compat only when a mod needs custom hooks or APIs.
- Runtime evaluation is snapshot-based and single-pass in v1; newly unlocked stages affect subsequent events, not the currently running pass.
- Global/challenge modifiers are bounded scalar layers for World Awakened-owned numeric difficulty outputs only; they do not alter stage unlocks or trigger eligibility.
- Optional world-context conditions are rule inputs only; they are not primary progression systems and fail closed (`false`) when context is unavailable.
- When Apotheosis compat is active, World Awakened loot must compose with Apotheosis and never silently override or remove Apotheosis-owned tier-gated loot behavior.
- Unsafe Apotheosis-sensitive loot operations are handled by explicit policy outcomes (block, downgrade to additive, or disable branch) with diagnostics.
- Hot-path evaluation should run on compiled cached structures, not runtime JSON parsing.
- Save compatibility should degrade gracefully when datapacks change, including stage aliases for renames.
- Integrations must be optional and fail-safe when absent.
- Broken datapack objects should be isolated or disabled with clear logs; unrelated systems should continue when startup integrity allows.


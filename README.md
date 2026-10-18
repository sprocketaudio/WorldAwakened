# World Awakened

World Awakened is a NeoForge mod framework for Minecraft 1.21.1 focused on **server-authoritative progression-driven difficulty**.

- Last updated: 2026-10-18
- Documentation index/read order: [docs/README.md](docs/README.md)

The core model is:
- Java code defines execution systems and behavior types.
- Datapacks define progression stages, rules, authored mutation/reward compositions, loot evolution, invasions, and integrations.
- All subsystems share one canonical condition model, action model, scope model, component composition/conflict model, and status taxonomy.
- Datapack object files use the standard layout `data/<namespace>/<object_type>/*.json` (no duplicate namespace folder layer).
- TOML config provides operator-level overrides, safety gates, and the `/wa debug` command-tree kill switch.
- The mod jar ships a conservative Standard gameplay profile through the same datapack schemas available to pack authors.
- External datapacks remain supported and can add definitions or replace bundled resources by normal resource-pack precedence.

Repository/distribution note:
- Optional example content currently lives under `example_datapacks/worldawakened_example_pack/` and must be installed like a normal datapack.

## Status
Phase 8 core complete.
Phase 0 foundation systems, Phase 1 stage progression, and Phase 2 trigger flow are implemented end-to-end. Phase 3 is implemented: compiled generic rule evaluation (`world | player | entity | spawn_event`), deterministic priority/cooldown/chance ordering, optional world-context condition evaluation (`world_day_gte`, `player_distance_from_spawn`) with fail-closed behavior, single-pass stage propagation (pre-action snapshots), runtime rule cooldown/consumed persistence, per-pass debug trace IDs, explicit `global`/`player` operator targeting for stage/trigger/rule inspection in `PER_PLAYER` mode, optional command-side dimension overrides for manual trigger/rule inspection, and `/wa dump active_rules`. Phase 4 is now implemented: player-scoped ascension runtime offer instances with idempotent grant keys and queued one-pending semantics, clickable chat notifications, minimal client GUI + packet selection flow, server-authoritative selection validation, login/respawn/reload reward reconciliation, WA-owned runtime carriers for refreshable passive reward behavior (including fire resistance and lightmap-backed client night vision), reward-level and component-level suppression controls for owned rewards, operator-friendly ascension selection/reversal/suppression controls (`choose`, `active`, `reopen`, `clear`, `suppress`, `unsuppress`, `reconcile`), copy/suggest chat actions for common operator paths, and explicit `/wa debug reset|clear` persistence-bucket commands for global/player testing and recovery. Phase 5 is now implemented: compiled selector-index mutator narrowing, required per-pool initial and conditional-additional chance gates, bounded any-compatible or curated multi-mutation selection, spawn-time mutator application budgets, deterministic provenance metadata (`WA_MUTATION_*` keys), recursion/re-entry guards, data-driven vanilla-slot mob equipment via `worldawakened:equip_item`, silhouette outline mutation visuals via `worldawakened:glow_style`, simple vanilla effect-style visual emitters via `worldawakened:effect_particles`, advanced raw particle emitters via `worldawakened:ambient_particles`, and command-driven mutator verification (`/wa mob inspect`, `/wa debug mutators evaluate|force_pool|force_mutator`, `/wa debug spawn test`) including explicit chance-pass/chance-fail debug output. Phase 6 is implemented: bounded spawn-pressure + difficulty scalar composition and command/debug evaluation surfaces. Phase 7 is implemented: live loot-profile resolution on WA reward-capable events, canonical loot-context evaluation, Apotheosis-sensitive safety behavior, concise operator loot commands (`/wa loot evaluate|force_profile`), and deep debug loot inspection (`/wa debug loot evaluate|force_profile`).
Phase 8 is implemented: invasion pressure-event scheduler with persistent active-state tracking, warning/duration/cooldown handling, temporary invasion pressure modifier context, invasion-aware condition context (`invasion_active`, `invasion_tag`) across rules/mutators/loot, and command/debug verification surfaces (`/wa invasion start|stop|inspect`, `/wa debug invasion evaluate`).
Phase 9 implementation is active in code: compatibility profile activation evaluation, Apotheosis world-tier provider integration, simple `derived_stage` threshold hooks (`stage_hooks -> tier_threshold_unlock`) for automatic WA stage unlocks, advanced tier-filter enforcement for ascension/mutator/loot paths, and compat/scalar inspect-debug command surfaces (`/wa compat list`, `/wa apotheosis tier inspect`, `/wa debug compat evaluate`, `/wa debug scalar provider`).
The built-in Standard profile is implemented as normal datapack definitions. It supplies S0-S6 milestone progression, eleven live-safe mutation archetypes (including status-on-hit, lifesteal, and block-safe death-explosion threats), unified mutation pools with progressive additional rolls and mob-restricted curated combinations, composed visible mutation names, three player-owned phase-linked Ascension offers, bounded additive mutation loot, and three warning-backed pressure events without bypassing validation, composition, indexing, or runtime budgets. Custom player-advancement milestones still reconcile idempotently on login, and periodic invasion profiles evaluate only in an allowed dimension that currently has players. Rates and rewards remain provisional pending world play-testing.
The native profile editor now opens on a WorldAwakened dashboard. World Phases presents one ordered timeline of stable shared milestones with supported world-day, dimension-entry, advancement, entity-kill, starting, and manual unlocks. Operators can combine up to three automatic milestones with a flat AND/OR choice; AND completion persists and works in either order for global or per-player progression. The phase browser shows On/Off and Unsaved state and keeps ordering arrows on the selected row, while the detail panel is limited to General, Mob Mutation Dimensions, and Unlock Conditions. Advanced datapack trigger logic and hidden progression groups are preserved when the compact editor cannot represent them safely. The editor shows authoring state only—not whether a world or player has already unlocked a phase—and shares the same Apply, Restore/Delete, Discard, Reset All Defaults, and transient feedback conventions as Mob Mutations. Mob Mutations uses one searchable browser with collapsible World Phases and Pressure Events sections; selecting an entry edits it in the same detail panel. Phase entries then let an operator choose a dimension and named Mob Config, while specialised mob selections take precedence over that dimension's Default Mobs configuration and the runtime still performs one ordinary weighted selection pass. Ascension uses the same native-editor conventions: each offer edits General, Ascension Choices, then When to Offer; availability can follow a World Phase or use up to three independent AND/OR conditions, and the reward selector owns selection, creation, editing, and confirmed deletion/restoration. A compact native choice may combine up to three distinct effects from the genuinely live reward handlers. New rewards and their referencing offer apply atomically. Configuration membership, removal, and gameplay enablement are distinct: disabled configurations remain visible and editable, the Mob Config picker owns Create, Rename, Enable/Disable, Remove, and source-backed Restore actions, and the main editor header remains a selector with boxed read-only status. Pressure-event mutation pools remain gameplay-independent even though they share the editor. Operators can use Standard, start an empty custom profile, create an independent world-persistent copy of Standard, or use the separately confirmed Reset All Defaults action to clear world-authored editor state and restore built-in global settings. External datapack files and earned progression are not reset. World-authored definitions and overrides are persistent, revision-protected, operator-checked, codec-backed, validated on the server, and atomically overlaid without modifying bundled or external datapack files. Existing track/tier metadata is preserved as a compatibility layer, but those implementation terms are not exposed in the normal editor workflow.
The hosted web-authoring platform is retired. Later native-editor milestones may add the remaining definition editors and simplified rule authoring; advanced datapacks remain supported.

## Primary Documentation
- Documentation index and required read order: [docs/README.md](docs/README.md)
- Friendly wiki index: [docs/wiki/README.md](docs/wiki/README.md)
- Full design spec: [docs/SPECIFICATION.md](docs/SPECIFICATION.md)
- Simplification/default gameplay plan: [docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md](docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md)
- Standard gameplay contract: [docs/DEFAULT_GAMEPLAY_SPEC.md](docs/DEFAULT_GAMEPLAY_SPEC.md)
- Retired web authoring plan: [docs/WEB_AUTHORING_TOOL_SPEC.md](docs/WEB_AUTHORING_TOOL_SPEC.md)
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
- Superseded in-game/admin UI proposal: [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md)
- Agent operating guidance: [AGENTS.md](AGENTS.md)

## Documentation Sync Policy
- `docs/README.md` defines required read order, cross-update matrix, and shared reference-doc format expectations.
- `docs/wiki/README.md` defines the friendly wiki layer and when it must be updated.
- `docs/SPECIFICATION.md` is the source-of-truth design contract.
- `docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md` is the active product plan for built-in gameplay and the native editor.
- `docs/WEB_AUTHORING_TOOL_SPEC.md` is a retired historical pointer.
- `docs/DATAPACK_AUTHORING.md` is the source-of-truth authoring format contract for user datapacks.
- `docs/COMPONENT_REFERENCE.md` is the canonical component catalog and must be updated whenever a component type/schema changes.
- `docs/COMPOSITION_AND_STACKING.md` is the canonical component composition resolver contract (duplicate/conflict/order/budget/no-op).
- `docs/CONDITION_REFERENCE.md`, `docs/ACTION_REFERENCE.md`, and `docs/SCOPE_MATRIX.md` are canonical shared-contract references for condition/action/scope legality.
- `docs/DEBUG_AND_INSPECTION.md` is the canonical command/trace/provenance contract for runtime inspection.
- `docs/PERFORMANCE_BUDGETS.md` is the canonical hot-path performance and runtime budget guardrail contract.
- `docs/VALIDATION_AND_ERROR_CODES.md` is the canonical diagnostics taxonomy and should be updated with validator/runtime diagnostic changes.
- `docs/PRESET_CATALOG.md` must stay aligned with shipped external example-pack content or canonical authored preset/template compositions.
- `docs/FUTURE_IDEAS.md` is a non-normative backlog and does not expand MVP scope by itself.
- `docs/FUTURE_ADMIN_UI.md` records that its older deferred proposal has been superseded.
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
- Suppression-aware ascension reconciliation so chosen rewards remain permanent while live effects can be temporarily disabled
- Shared component stacking/conflict semantics (explicit duplicates, conflict sets, deterministic ordering)
- Shared status taxonomy (`implemented | planned | reserved | deprecated`) across docs, validation, and tooling
- Deterministic conflict resolution and bounded runtime evaluation
- Operator-controlled global World Awakened difficulty modifier for baseline intensity tuning
- Optional bounded challenge modifier layer (player/world scoped by policy) for accessibility and opt-in escalation
- Command-driven inspect/evaluate/force/live-test verification surfaces for Phases 5-9 runtime systems
- Spawn-time mutation definitions (`mob_mutators`) composed from mutation components
- Server-side live mutation combat handlers for bounded on-hit effects, lifesteal, and block-safe one-shot death explosions
- Loot evolution through profile-driven injection/replacement rules
- Apotheosis loot compatibility that composes additively and preserves Apotheosis-owned tier-gated loot behavior when integration is active
- One configurable invasion pressure-event loop
- Generic support for modded bosses and mobs through entity IDs, tags, and datapack-defined boss classification
- Optional compatibility layer with explicit toggles
- Optional Apotheosis World Tier integration (conditions + scalar inputs)
- Debug command suite for inspection, validation, and targeted operator recovery/reset
- A conservative playable Standard profile loaded through the canonical datapack engine
- A native, server-authoritative in-game configuration interface with registry-aware selectors, delivered incrementally

## Non-Goals for v1
- Arbitrary user scripting language
- Full vanilla spawn engine replacement
- Retroactive mutation of already-loaded mobs
- A second gameplay/configuration engine separate from the datapack compiler

## Planned High-Level Implementation Sequence
1. Phase 0 (Complete): Data contracts/codecs + datapack reload + validation pipeline + initial test harness
2. Phase 1 (Complete): Stage core + persistence + stage commands
3. Phase 2 (Complete): Trigger engine + stage unlock flow
4. Phase 3 (Complete): Rule engine core + deterministic active-rule introspection + optional world-context condition evaluation + single-pass stage propagation guarantee + debug trace ID support
5. Phase 4 (Complete): Ascension Choice System + minimal GUI/packet flow + permanent reward reconciliation
6. Phase 5 (Complete): Mutators + mutation pools + spawn application + mob inspect + mutator evaluate/force/live-test commands + spawn-time performance budgets (`max_mutators_per_spawn`, `max_components_per_mutator`)
7. Phase 6 (Complete): Spawn pressure controls + rule-event budget guardrails (`maximum_rules_evaluated_per_event`, `maximum_actions_per_rule`) + shared effective-scalar service for global/optional challenge modifiers
8. Phase 7 (Complete): Loot evolution profiles and bonus reward integration as downstream WA-owned event pipelines (inject-first defaults, deterministic attribution, and scalar-isolated reward resolution in Phase 7)
9. Phase 8 (Complete): Invasion pressure-event system (scheduler, active state, warning window, temporary pressure modifiers, and command mode MVP)
10. Phase 9: Compatibility framework + Apotheosis world tier and external tier providers (integration-tier influence limited to mutation pressure, mutation pool eligibility/scalar gates, and reward scaling; no wave assumptions)
11. Default gameplay: play-test and expand Standard, then add Relaxed/Challenging presets through the same engine
12. Native editor foundation and mutation-pool editor (implemented): dashboard, Global Settings, server-derived living-entity selection, revisioned per-world pool overrides, validation and source reversion; remaining definition editors and simplified rules remain later work
13. Hardening: regression verification, documentation, example datapacks, and `/wa debug perf|rules|mutators` surfaces

## Dev Notes
- Keep all gameplay authority server-side.
- Keep stage identifiers data-defined (no hardcoded progression names/order in code).
- Keep ascension selections player-scoped, permanent, and exclusive per offer.
- Keep mutation and ascension behavior Java-defined at the component-type layer; keep named presets/data compositions datapack-authored.
- Keep all condition/action/scope semantics on the shared framework contracts; do not create subsystem-specific variants.
- Keep component composition semantics explicit (duplicate/conflict/order/companion requirements); do not rely on accidental stacking.
- Keep long-lived refreshable/revocable player or entity effects on WA-owned attribute keys or WA-owned runtime carriers, including client-visual carriers where needed; do not clear shared vanilla/modded effect state during reconcile.
- Support unknown future boss/mob mods generically through entity IDs/tags first; add dedicated compat only when a mod needs custom hooks or APIs.
- Runtime evaluation is snapshot-based and single-pass in v1; newly unlocked stages affect subsequent events, not the currently running pass.
- Global/challenge modifiers are bounded scalar layers for World Awakened-owned numeric difficulty outputs only; they do not alter stage unlocks or trigger eligibility.
- Optional world-context conditions are rule inputs only; they are not primary progression systems and fail closed (`false`) when context is unavailable.
- When Apotheosis compat is active, World Awakened loot must compose with Apotheosis and never silently override or remove Apotheosis-owned tier-gated loot behavior.
- Unsafe Apotheosis-sensitive loot operations are handled by explicit policy outcomes (block, downgrade to additive, or disable branch) with diagnostics.
- Phase 7 reward/loot resolution is downstream-event-only (`entity_killed`, `invasion_completed`, and explicitly documented WA reward-capable events), must not mutate progression logic, and remains unaffected by global/challenge scalar layers until a later phase explicitly enables reward scaling.
- Phase 7 reward-capable subsystems contribute reward intent only; one canonical resolver gathers contributors, resolves deterministic final outcomes, applies rewards once per event, and blocks duplicate non-repeatable contributors.
- Phase 7 loot profiles are condition-driven and evaluate against one canonical loot context (`loot_context_type`, `target_type`, `target_id`, `loot_table_id`, entity/mutation context, player/dimension, stage/invasion context, compat/scalar policy context) rather than direct subsystem attachment.
- Phase 8 invasions are event-state pressure systems in v1; World Awakened does not spawn invasion waves and instead exposes invasion context + temporary pressure modifiers that existing spawn systems resolve against.
- Hot-path evaluation should run on compiled cached structures, not runtime JSON parsing.
- Save compatibility should degrade gracefully when datapacks change, including stage aliases for renames.
- Integrations must be optional and fail-safe when absent.
- Broken datapack objects should be isolated or disabled with clear logs; unrelated systems should continue when startup integrity allows.


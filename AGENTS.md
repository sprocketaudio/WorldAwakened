# AGENTS.md

## Purpose
This repository is building **World Awakened**, a Minecraft 1.21.1 + NeoForge framework for server-authoritative, data-driven world progression and difficulty scaling.

This file defines how coding agents should operate in this repo.

- Last updated: 2026-10-19
- Documentation index/read order: [docs/README.md](docs/README.md)

## Documentation Intake Before Any Task

Use the smallest reliable documentation set. `docs/README.md` routes every task to its relevant contract; it is not a reason to read every reference document.

### 1. Always read
1. [AGENTS.md](AGENTS.md)
2. [docs/README.md](docs/README.md)
3. [README.md](README.md) when a public-facing feature, scope, or status is relevant
4. The relevant current-contract section of [docs/SPECIFICATION.md](docs/SPECIFICATION.md)

### 2. Read for the task's subsystem

Read only the references named by the route table in `docs/README.md`. For example, a Loot-editor layout fix does not require the complete action, condition, or mutation-component catalogues. Read shared contracts when the task changes them, and read the relevant wiki page when a user workflow changes.

### 3. Read when affected

Read additional references only when the requested change affects their contract: bundled balance uses `DEFAULT_GAMEPLAY_SPEC.md`; datapack shape uses `DATAPACK_AUTHORING.md`; deferred/historical scope uses the applicable future or historical document. Use `rg` to locate the current contract section rather than relying on unrelated historical prose.

Task citation rule:
- In every task response, list the docs consulted before implementation.

If there is a conflict, follow this priority:
1. User request in the current task
2. `docs/SPECIFICATION.md`
3. `README.md`
4. Existing code style and project conventions

## Core Product Rules
- WorldAwakened is unreleased greenfield software. Do not preserve abandoned internal schemas, aliases,
  migration paths, or replacement representations unless the user explicitly requires a public migration.
- Refactor onto the current authoritative model and remove the obsolete implementation; current-format save
  persistence, datapack loading, NeoForge compatibility, and optional integrations are not legacy paths.
- Keep gameplay authority on the server.
- Keep content and balance data-driven via datapacks and config.
- Keep one shared datapack/data contract across raw datapacks, bundled defaults, runtime state, and native-editor overrides.
- Keep one shared framework contract for conditions, actions, scopes, component composition semantics, and status taxonomy across all systems.
- Never hardcode stage names, ordering, or progression paths.
- Internal logic must use stage IDs, not display names.
- Keep ascension choices player-scoped, permanent, and exclusive per offer unless the spec explicitly adds another mode.
- Treat named mutations and named ascension rewards as authored datapack definitions, not privileged engine enums/constants.
- Implement behavior extensions through component-type registries and Java execution semantics, not hardcoded preset identities.
- Do not add legacy schema fallback paths for mutation/reward authoring models unless the user explicitly requests migration compatibility.
- Treat named loot profiles and invasion profiles as authored datapack definitions; do not hardcode named presets as engine-owned gameplay truth.
- Keep global/challenge difficulty modifiers as bounded scalar layers for World Awakened-owned numeric difficulty outputs only; they must not mutate stage unlock state or trigger eligibility.
- Support unknown modded bosses and mobs through generic entity ID/tag rules before introducing bespoke compat code.
- Preserve deterministic conflict resolution and priority semantics when adding rules, actions, mutators, or integrations.
- Preserve the snapshot-based, single-pass event pipeline unless the spec explicitly introduces a bounded multi-pass exception.
- Treat world-context conditions (for example world day and distance from spawn) as optional datapack rule inputs, not core progression systems; when context is unavailable they should fail closed.
- When Apotheosis compat is active, treat Apotheosis-owned tier-gated loot behavior as authoritative; World Awakened loot changes should compose additively on Apotheosis-sensitive targets and never silently destroy Apotheosis behavior.
- Optional integrations (including Apotheosis) must never be hard dependencies.
- If integration-specific data is present but integration is unavailable, skip safely and log clearly.

## Architecture Boundaries
- Java defines behavior engines and execution flow (`HOW`).
- Datapacks define stage/rule/profile content (`WHAT`).
- The native in-game editor is a server-authoritative authoring/validation layer; it must compile into the existing runtime definitions rather than become another gameplay engine.
- The core mod jar should ship conservative gameplay-active defaults using the same schema exposed to pack authors.
- Example content may remain external; custom datapacks must have predictable additive/override behavior with bundled defaults.
- TOML config defines server/operator overrides and kill-switches.
- Do not introduce arbitrary scripting language in v1.
- Do not attempt full replacement of vanilla spawning in v1.
- Do not implement retroactive mutation of already-loaded entities in v1.

## Delivery Priorities (MVP Order)
1. Phase 0: Data contracts/codecs + reload + validation foundation + initial test harness
2. Phase 1: Stage core + persistence + stage command baseline
3. Phase 2: Trigger engine + unlock flow
4. Phase 3: Rule engine core + deterministic active-rule introspection + optional world-context condition evaluation before downstream systems + single-pass stage propagation guarantee + debug trace ID support
5. Phase 4: Ascension Choice System + minimal GUI/network flow + selection validation
6. Phase 5: Mutators + mutation pools + spawn-time application + inspect tools + spawn-time performance budgets (`max_mutators_per_spawn`, `max_components_per_mutator`)
7. Phase 6: Spawn pressure controls with hard guardrails + rule-event performance limits (`maximum_rules_evaluated_per_event`, `maximum_actions_per_rule`) + shared effective-scalar provider for global/optional challenge modifiers
8. Phase 7: Loot evolution integration + Apotheosis-sensitive loot safety behavior
9. Phase 8: Invasion pressure-event MVP implementation (scheduler + active-state + temporary pressure modifier; no WA-owned wave spawning)
10. Phase 9: Compatibility framework + Apotheosis world tier/external tier support + loot-target sensitivity enforcement (invasion-adjacent influence limited to mutation pressure, mutation pool eligibility/scalar gates, and reward scaling)
11. Default gameplay: conservative Standard profile, world play-testing, then Relaxed/Challenging presets using the same engine
12. Native editor foundation: `/wa config`, permissions, revisioned server validation/apply, preset and common difficulty controls
13. Native editor growth: cached server registry browser, mutation editing, simplified `WHEN -> IF -> THEN` rules with inferred internal scopes
14. Validation hardening + docs + example datapacks + regression checks for Phase 7-9 guardrails + `/wa debug perf|rules|mutators`

## Engineering Expectations
- Prefer small, testable, additive changes.
- WRITE CHUNK RULE: When generating very large files, do not attempt a single giant write operation. Split generation/writes into chunks of about 500-900 lines per write pass (final remainder may be shorter). The completed file itself may exceed 900 lines.
- Add or update validation when adding new datapack object fields.
- Add or update component-composition validation (empty/unknown/incompatible/invalid/no-op/budget/duplicate cases) when touching mutation or ascension schemas.
- Add or update validation and fallback diagnostics when adding new compatibility constraints (especially destructive-mode restrictions).
- Include debug visibility for progression and rule outcomes with explicit rejection reasons or state summaries when the system already exposes those evaluation paths.
- Keep logs actionable: object ID, reason, and resolution path.
- Add or update automated tests in the relevant spec-defined categories when implementing a subsystem or regression fix.
- Before running Gradle tests on Windows, clear `build/test-results/junit-binary` to avoid stale file-lock cleanup failures.
- Compile selectors and other hot-path evaluators during reload; avoid runtime JSON parsing in spawn/event paths.
- All hot-path systems must operate on reload-compiled data structures.
- Runtime evaluation must not iterate over entire datapack collections.
- All rule engines must be scope-indexed.
- All spawn-time mutation must respect mutator budget limits.
- Maintain bounded reroll behavior and prevent recursive rule execution.
- Preserve failure isolation: disable the broken object or branch, not unrelated systems.
- Preserve compatibility toggles; integrations must be controllable via config.
- Avoid hidden magic behavior that pack makers cannot inspect or override.
- Preserve authored definition IDs in runtime/debug output and include resolved component lists where those paths already expose mutation/reward state.
- Enforce shared condition/action node shape and scope legality in validation paths where typed nodes are processed.
- Enforce shared component duplicate/conflict/ordering semantics in mutation and ascension composition validation.
- Use shared status taxonomy labels (`implemented`, `planned`, `reserved`, `deprecated`) consistently in docs, validation diagnostics, and tooling-facing metadata.
- Do not treat a later phase as complete while earlier phase exit criteria are failing.

## Data-Driven Design Constraints
- All major systems must accept datapack objects:
  - stages
  - trigger_rules
  - rules
  - ascension_rewards
  - ascension_offers
  - mob_mutators
  - mutation_pools
  - loot_profiles
  - invasion_profiles
  - integration_profiles
- Unsupported or broken objects should disable only themselves or their dependent objects; unrelated systems must not be taken down unless startup integrity requires it.

## Commands and Debug Support
Maintain and extend the `/wa` command tree described in the spec.  
Any new mechanic should be inspectable or testable via command/debug output.
All new `/wa` command surfaces must follow the documented output-layer contract:
- player-facing notifications stay minimal and human-readable
- operator command feedback stays concise by default
- dense raw IDs, provenance, and reason paths belong in inspect/debug surfaces first
- `general.debug_logging` may append extra raw detail to operator output, but must not replace the concise operator layer or leak debug-heavy output into normal gameplay notifications
- in `WorldAwakenedCommands`, prefer shared output helpers (`sendOperatorSummary`, `sendOperatorDetail`, `sendDebugHeader`, `sendDebugSection`) instead of ad-hoc inline formatting so future stages keep consistent presentation layers
- commands that consume dynamic runtime IDs should provide Brigadier suggestions from runtime state wherever practical (for example replay IDs, trace IDs, runtime instance IDs)
- rejection/failure text for player/operator-facing command output must use human-readable mapping helpers (for example `describe*` formatters); raw codes/details are debug payloads first
- clickable rich-text affordances (copy/prefill/run) should be used for operator/inspect/debug workflows where they reduce mistakes, not in normal gameplay notifications
- when extending command trees in later phases, preserve and reuse these helper patterns rather than introducing subsystem-local formatting styles

## Code Organization Target
Use `net.sprocketgames.worldawakened` package root and keep systems separated:
- `api`, `ascension`, `config`, `data`, `progression`, `rules`, `mutator`, `spawning`, `loot`, `invasion`, `compat`, `command`, `debug`, `network`, `util`

## Documentation Rule
When behavior changes, update:
1. [docs/SPECIFICATION.md](docs/SPECIFICATION.md) for design/contract changes
2. [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md) for datapack format/schema/reference changes
3. [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md) for component catalog/schema/status changes
4. [docs/COMPOSITION_AND_STACKING.md](docs/COMPOSITION_AND_STACKING.md) for shared duplicate/conflict/order/budget/no-op semantics
5. [docs/CONDITION_REFERENCE.md](docs/CONDITION_REFERENCE.md) for shared condition catalog/shape/status changes
6. [docs/ACTION_REFERENCE.md](docs/ACTION_REFERENCE.md) for shared action catalog/shape/status changes
7. [docs/SCOPE_MATRIX.md](docs/SCOPE_MATRIX.md) for scope legality/guaranteed-context changes
8. [docs/DEBUG_AND_INSPECTION.md](docs/DEBUG_AND_INSPECTION.md) for debug command/trace/provenance contract changes
9. [docs/PERFORMANCE_BUDGETS.md](docs/PERFORMANCE_BUDGETS.md) for hot-path limits, rule indexing, and runtime guardrail changes
10. [docs/VALIDATION_AND_ERROR_CODES.md](docs/VALIDATION_AND_ERROR_CODES.md) for diagnostics taxonomy/code changes
11. [docs/PRESET_CATALOG.md](docs/PRESET_CATALOG.md) for canonical preset/template status/composition changes
12. [docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md](docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md) for built-in gameplay and native-editor direction changes
13. [docs/DEFAULT_GAMEPLAY_SPEC.md](docs/DEFAULT_GAMEPLAY_SPEC.md) for bundled Standard content, provisional balance, rewards, and pressure-event changes
14. [docs/README.md](docs/README.md) for read order or cross-update matrix changes
15. [docs/wiki/README.md](docs/wiki/README.md) and affected files under `docs/wiki/` for friendly operator/author/testing/troubleshooting guidance
16. [README.md](README.md) for user-facing scope/status changes
17. [AGENTS.md](AGENTS.md) for contributor/agent workflow expectation changes

## Documentation Currency Rule
- Treat documentation sync as mandatory, not optional cleanup.
- Treat `docs/README.md` as the index for read order, shared reference format, and cross-update mapping.
- Treat `docs/wiki/` as the friendly companion layer for operators, pack authors, testers, and server owners.
- Keep the `docs/wiki/` layer plain-language, example-driven, and practical; it must explain the system in human terms, not just restate contract tables.
- If a concept caused confusion during testing, implementation, review, or operator use, update the relevant wiki docs in the same task.
- Shared-contract lock rule: if any shared framework contract (conditions, actions, scopes, component composition semantics, status taxonomy) changes, update all related shared-contract docs in the same task.
- Any spec expansion must update all impacted docs in the same task/commit:
  - [docs/README.md](docs/README.md) (documentation set map and cross-update contract)
  - [docs/wiki/README.md](docs/wiki/README.md) and affected files under `docs/wiki/` when user-facing workflows, explanations, command usage, testing patterns, recipes, or troubleshooting are impacted
  - [docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md](docs/SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md) (built-in gameplay and native-editor product contract)
  - [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md) (user datapack schema/content contract)
  - [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md) (canonical mutation/ascension component reference; keep implemented vs planned accurate)
  - [docs/COMPOSITION_AND_STACKING.md](docs/COMPOSITION_AND_STACKING.md) (shared composition resolver contract for duplicates/conflicts/ordering/budget/no-op behavior)
  - [docs/CONDITION_REFERENCE.md](docs/CONDITION_REFERENCE.md) (shared condition catalog/status/scope contract)
  - [docs/ACTION_REFERENCE.md](docs/ACTION_REFERENCE.md) (shared action catalog/status/scope contract)
  - [docs/SCOPE_MATRIX.md](docs/SCOPE_MATRIX.md) (shared scope legality/guaranteed-context contract)
  - [docs/DEBUG_AND_INSPECTION.md](docs/DEBUG_AND_INSPECTION.md) (shared runtime inspection/trace/provenance contract)
  - [docs/PERFORMANCE_BUDGETS.md](docs/PERFORMANCE_BUDGETS.md) (shared hot-path performance and runtime budget guardrail contract)
  - [docs/VALIDATION_AND_ERROR_CODES.md](docs/VALIDATION_AND_ERROR_CODES.md) (diagnostic taxonomy/code contract)
  - [docs/PRESET_CATALOG.md](docs/PRESET_CATALOG.md) when canonical preset/template definitions or status tags change
  - [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md) when promoting, removing, or reshaping backlog ideas
  - [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md) when promoting, reshaping, or narrowing the deferred in-game/admin runtime UI feature
  - [docs/SPECIFICATION.md](docs/SPECIFICATION.md) (source-of-truth detail)
  - [README.md](README.md) (concise public summary)
  - [AGENTS.md](AGENTS.md) (contributor/agent behavior expectations)
- If docs are out of sync, fix docs before or alongside code changes.

## Human Docs Rule
- Maintain a separate friendly/wiki-like docs layer under `docs/wiki/`.
- This layer must cover concepts, quickstarts, operator guidance, cookbook patterns, troubleshooting, and FAQ content.
- Write this layer for humans first: plain language, practical examples, decision rules, and recovery steps.
- Do not leave an important behavior explained only in deep technical docs when operators or pack authors are likely to hit it in normal use.
- Avoid roadmap/implementation jargon in `docs/wiki/` (for example phase labels or internal build/process language); explain features and workflows in user/operator terms.

## Backlog Rule
- `docs/FUTURE_IDEAS.md` is not active scope by itself.
- `docs/FUTURE_ADMIN_UI.md` is superseded historical context; active native-editor scope comes from the specification and simplification plan.
- Do not implement backlog items unless the user explicitly asks for that feature or it is promoted into [docs/SPECIFICATION.md](docs/SPECIFICATION.md).
- Do not promote new post-v1 systems into active scope while the Phase 0-3 foundation remains unproven unless the user explicitly redirects the project.

## Current Project Stage
Phase 0 is complete (contracts/codecs + reload + validation foundation + initial tests).  
Phase 1 is complete (stage core + persistence + stage command baseline).  
Phase 2 is complete (trigger engine + unlock flow baseline).  
Phase 3 is complete (rule engine core + deterministic active-rule introspection + optional world-context evaluation + single-pass stage propagation + debug trace IDs).  
Phase 4 is complete (ascension runtime offers + queue semantics + minimal GUI/network flow + server-authoritative selection validation + login/respawn reconciliation + ascension command/inspect surface).  
Phase 5 is complete (mutators + mutation pools + spawn-time application + inspect/debug surfaces + spawn-time guardrails).  
Phase 6 is complete (spawn pressure controls + rule-event guardrails + shared effective-scalar provider).  
Phase 7 is complete (loot evolution integration + Apotheosis-sensitive loot safety behavior + loot debug evaluate/force surfaces).  
Phase 8 is complete (invasion pressure-event scheduler + active-state + warning/duration/cooldown + temporary pressure modifier + invasion command/debug surfaces).  
The native `/wa config` dashboard, Global Settings, World Phases timeline, profile creation (Standard/Start Empty/Copy Standard), phase/dimension/named mob configurations, mutation editor, server-derived selectors, and revisioned world-authoring bridge are implemented. World Phases and their direct mutation-pool relationships are the only WorldAwakened progression-authoring model. Current work is in-game singleplayer/dedicated-server GUI validation and hardening before full mutator-component, reward, event, or generic rule-editor expansion; preserve and validate the Phase 9 optional-integration work already present.


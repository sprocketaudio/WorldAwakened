# AGENTS.md

## Purpose
This repository is building **World Awakened**, a Minecraft 1.21.1 + NeoForge framework for server-authoritative, data-driven world progression and difficulty scaling.

This file defines how coding agents should operate in this repo.

## Read Order Before Any Task
1. [README.md](README.md)
2. [docs/SPECIFICATION.md](docs/SPECIFICATION.md)
3. [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md) when touching datapack shape, validation, IDs, references, or examples
4. [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md) when touching mutation/ascension component IDs, schemas, validation, or examples
5. [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md) only when discussing post-v1 backlog or promoting deferred ideas
6. [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md) only when discussing the deferred admin authoring/inspection UI concept

If there is a conflict, follow this priority:
1. User request in the current task
2. `docs/SPECIFICATION.md`
3. `README.md`
4. Existing code style and project conventions

## Core Product Rules
- Keep gameplay authority on the server.
- Keep content and balance data-driven via datapacks and config.
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
- Built-in/default content should be shipped as bundled datapack presets whenever practical, using the same schema exposed to pack authors.
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
6. Phase 5: Mutators + mutation pools + spawn-time application + inspect tools + optional mutator budget guard
7. Phase 6: Spawn pressure controls with hard guardrails + optional dimension pressure baselines + shared effective-scalar provider for global/optional challenge modifiers
8. Phase 7: Loot evolution integration + Apotheosis-sensitive loot safety behavior
9. Phase 8: Invasion scheduler/waves MVP implementation
10. Phase 9: Compatibility framework + Apotheosis world tier/external tier support + loot-target sensitivity enforcement
11. Phase 10: Validation hardening + docs + example datapack + release prep

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
- Maintain bounded reroll behavior and prevent recursive rule execution.
- Preserve failure isolation: disable the broken object or branch, not unrelated systems.
- Preserve compatibility toggles; integrations must be controllable via config.
- Avoid hidden magic behavior that pack makers cannot inspect or override.
- Preserve authored definition IDs in runtime/debug output and include resolved component lists where those paths already expose mutation/reward state.
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

## Code Organization Target
Use `net.sprocketgames.worldawakened` package root and keep systems separated:
- `api`, `ascension`, `config`, `data`, `progression`, `rules`, `mutator`, `spawning`, `loot`, `invasion`, `compat`, `command`, `debug`, `network`, `util`

## Documentation Rule
When behavior changes, update:
1. [docs/SPECIFICATION.md](docs/SPECIFICATION.md) for design/contract changes
2. [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md) for datapack format/schema changes
3. [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md) for component catalog/schema/status changes
4. [README.md](README.md) for user-facing scope/status changes

## Documentation Currency Rule
- Treat documentation sync as mandatory, not optional cleanup.
- Any spec expansion must update all impacted docs in the same task/commit:
  - [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md) (user datapack schema/content contract)
  - [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md) (canonical mutation/ascension component reference; keep implemented vs planned accurate)
  - [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md) when promoting, removing, or reshaping backlog ideas
  - [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md) when promoting, reshaping, or narrowing the deferred UI feature
  - [docs/SPECIFICATION.md](docs/SPECIFICATION.md) (source-of-truth detail)
  - [README.md](README.md) (concise public summary)
  - [AGENTS.md](AGENTS.md) (contributor/agent behavior expectations)
- If docs are out of sync, fix docs before or alongside code changes.

## Backlog Rule
- `docs/FUTURE_IDEAS.md` is not active scope by itself.
- `docs/FUTURE_ADMIN_UI.md` is not active scope by itself.
- Do not implement backlog items unless the user explicitly asks for that feature or it is promoted into [docs/SPECIFICATION.md](docs/SPECIFICATION.md).
- Do not promote new post-v1 systems into active scope while the Phase 0-3 foundation remains unproven unless the user explicitly redirects the project.

## Current Project Stage
Phase 0 is complete (contracts/codecs + reload + validation foundation + initial tests).  
Phase 1 is complete (stage core + persistence + stage command baseline).  
Phase 2 is complete (trigger engine + unlock flow baseline).  
Phase 3 is complete (rule engine core + deterministic active-rule introspection + optional world-context evaluation + single-pass stage propagation + debug trace IDs).  
Current active target is Phase 4 unless the user redirects scope.


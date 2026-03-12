# World Awakened

World Awakened is a NeoForge mod framework for Minecraft 1.21.1 focused on **server-authoritative progression-driven difficulty**.

The core model is:
- Java code defines execution systems and behavior types.
- Datapacks define progression stages, rules, authored mutation/reward compositions, loot evolution, invasions, and integrations.
- Datapack object files use the standard layout `data/<namespace>/<object_type>/*.json` (no duplicate namespace folder layer).
- TOML config provides operator-level overrides and safety gates.
- Built-in defaults (mutations, ascension rewards/offers, and where practical loot/invasion presets) are shipped as bundled datapack presets using the same schemas available to pack authors.

## Status
Phase 3 core complete.  
Phase 0 foundation systems, Phase 1 stage progression, and Phase 2 trigger flow are implemented end-to-end. Phase 3 is now implemented: compiled generic rule evaluation (`world | player | entity | spawn_event`), deterministic priority/cooldown/chance ordering, optional world-context condition evaluation (`world_day_gte`, `player_distance_from_spawn`) with fail-closed behavior, single-pass stage propagation (pre-action snapshots), runtime rule cooldown/consumed persistence, per-pass debug trace IDs, and `/wa dump active_rules`.
Architecture baseline correction is applied: mutation definitions and ascension reward definitions are component-based authored objects, and built-in defaults are shipped as bundled datapack presets using the same model.
Current active milestone is Phase 4 (Ascension Choice System).

## Primary Documentation
- Full design spec: [docs/SPECIFICATION.md](docs/SPECIFICATION.md)
- Datapack format/authoring guide: [docs/DATAPACK_AUTHORING.md](docs/DATAPACK_AUTHORING.md)
- Component authoring reference: [docs/COMPONENT_REFERENCE.md](docs/COMPONENT_REFERENCE.md)
- Future backlog and long-term ideas: [docs/FUTURE_IDEAS.md](docs/FUTURE_IDEAS.md)
- Future admin UI concept: [docs/FUTURE_ADMIN_UI.md](docs/FUTURE_ADMIN_UI.md)
- Agent operating guidance: [AGENTS.md](AGENTS.md)

## Documentation Sync Policy
- `docs/SPECIFICATION.md` is the source-of-truth design contract.
- `docs/DATAPACK_AUTHORING.md` is the source-of-truth authoring format contract for user datapacks.
- `docs/COMPONENT_REFERENCE.md` is the canonical component catalog and must be updated whenever a component type/schema changes.
- `docs/FUTURE_IDEAS.md` is a non-normative backlog and does not expand MVP scope by itself.
- `docs/FUTURE_ADMIN_UI.md` is a non-normative future feature document and does not expand MVP scope by itself.
- If the spec changes, update `README.md` summary sections in the same change.
- If datapack format/shape rules change, update `docs/SPECIFICATION.md` and `docs/DATAPACK_AUTHORING.md` together.
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
- Optional world-context rule conditions (for example world day and player distance from spawn) as datapack inputs, not core progression drivers
- Player-scoped Ascension Choice offers with permanent exclusive rewards
- Component-based ascension reward definitions (`components[]`) with authored reward IDs
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
- Debug command suite for inspection and validation

## Non-Goals for v1
- Arbitrary user scripting language
- Full vanilla spawn engine replacement
- Retroactive mutation of already-loaded mobs
- Full GUI rule editor

## Planned High-Level Implementation Sequence
1. Phase 0 (Complete): Data contracts/codecs + datapack reload + validation pipeline + initial test harness
2. Phase 1 (Complete): Stage core + persistence + stage commands
3. Phase 2 (Complete): Trigger engine + stage unlock flow
4. Phase 3 (Complete): Rule engine core + deterministic active-rule introspection + optional world-context condition evaluation + single-pass stage propagation guarantee + debug trace ID support
5. Phase 4: Ascension Choice System + minimal GUI/packet flow + permanent reward reconciliation
6. Phase 5: Mutators + mutation pools + spawn application + mob inspect + optional mutator budget guard
7. Phase 6: Spawn pressure controls with hard safety caps + optional dimension pressure baselines + shared effective-scalar service for global/optional challenge modifiers
8. Phase 7: Loot evolution profiles and bonus drop integration (including safe additive behavior on Apotheosis-sensitive targets)
9. Phase 8: Invasion system (scheduler, waves, and command mode MVP)
10. Phase 9: Compatibility framework + Apotheosis world tier and external tier providers (including loot-target sensitivity enforcement)
11. Phase 10: Hardening, example datapack, and release-prep docs

## Dev Notes
- Keep all gameplay authority server-side.
- Keep stage identifiers data-defined (no hardcoded progression names/order in code).
- Keep ascension selections player-scoped, permanent, and exclusive per offer.
- Keep mutation and ascension behavior Java-defined at the component-type layer; keep named presets/data compositions datapack-authored.
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


# Simplification and Default Gameplay Plan

- Document status: Active product-direction and delivery plan
- Last updated: 2026-10-17
- Scope: Phase A audit, built-in gameplay rollout, and native in-game editor direction

This document replaces the hosted web-authoring plan as the concise product plan. The runtime datapack contract remains authoritative for advanced authors.

## 1. Audit Result

World Awakened does not need an engine rewrite. Its server-authoritative runtime foundation is usable as-is:

- atomic datapack reload and immutable compiled snapshots
- validation, dependency rejection, and failure isolation
- stage persistence and global/per-player progression buckets
- trigger and rule engines with deterministic ordering and scope indexes
- ascension offers, permanent choices, and reconciliation
- spawn-time mutators, component composition, candidate indexes, and hard budgets
- shared scalar resolution, loot evolution, invasions, and optional compatibility services
- operator/debug commands, trace IDs, diagnostics, and existing network registration patterns

The current implementation status is:

| Area | Status | Simplification decision |
|---|---|---|
| Data contracts, reload, validation | Functional | Reuse unchanged; all defaults and future editor overrides compile through it. |
| Stages, triggers, rules, persistence | Functional | Keep internal model; present milestones and `WHEN -> IF -> THEN` in the editor. |
| Ascension, mutators, spawn pressure, loot, invasions | Functional | Expose outcome-focused presets and toggles incrementally. |
| Optional compatibility | Functional foundation, integration-specific growth ongoing | Keep optional and fail closed when an integration is absent. |
| Built-in gameplay | Playable Standard profile implemented; balance provisional | Validate and tune through real-world play tests without bypassing datapack contracts. |
| Distinctive mutation combat | First live slice implemented (`on_hit_effect`, `life_steal`, `death_explosion`) | Exercise through development/example content before promoting any ability into bundled defaults. |
| Native in-game editor | Dashboard and mutation-pool slice implemented | `/wa config` provides Global Settings plus codec-backed pool editing with server-derived living mobs, revisions, validation, persistence, and source reversion. Other definition editors remain later work. |

## 2. Where Complexity Comes From

The engine concepts are individually useful, but raw authoring asks users to coordinate too many of them at once: definition IDs, cross-object references, stages, triggers, conditions, actions, profiles, pools, components, priorities, and two kinds of scope.

`source_scope` and `execution_scope` should remain internal contracts. They protect real differences:

- event-source context and trigger cooldown/one-shot ownership
- rule indexing and guaranteed player/entity/world context
- global versus per-player persistence
- safe action target availability

The native editor should infer those contracts from the chosen event, conditions, outcome, and friendly target choice. When more than one valid interpretation exists, it should ask in plain language such as `Everyone in this world`, `Only the player involved`, or `The affected mob`. It must reject ambiguous or impossible combinations before generating definitions.

## 3. Default Gameplay Loading

Built-in gameplay is ordinary datapack content under `data/worldawakened/...` in the mod jar. It passes through the same codecs, validation, dependency resolution, compilation, indexing, and runtime services as external content. There is no second default-gameplay engine.

The Standard profile contains seven ranked stages, milestone and personal-qualification triggers, eleven verified live archetypes, one unified ranked pool per stage/dimension context, curated rare combinations, three player-owned Ascension offers, additive mutation loot, and three warning-backed pressure events. Hindering on-hit effects enter at S2, rare Vampiric lifesteal enters at S3, Volatile death explosions enter at S4, and curated combat combinations begin at S4. Mutated mobs receive composed visible names unless they already have a custom name. Initial rates progress from 2% after day 3 to 13%/16%/18% in the post-Dragon Overworld/Nether/End; conditional additional chances progress from 2% at S4 to 5% at S5 and 9% at S6. Spawner and trial-spawner mobs are excluded. No bundled default pool selects more than two mutators, although the schema supports bounded higher-order combinations.

The detailed gameplay contract, exact values, safety boundaries, and remaining play-test work are in [DEFAULT_GAMEPLAY_SPEC.md](DEFAULT_GAMEPLAY_SPEC.md).

External datapacks remain supported. Normal resource-pack precedence applies: an external resource at the exact same namespace/path replaces the bundled object; definitions at other IDs are additive and participate in the existing deterministic selection rules. Authors should deliberately disable or replace bundled IDs when they want a fully custom experience rather than accidentally layering another profile over them.

Operators who want no bundled definitions can set `general.enable_builtin_gameplay=false`. Reload then filters the entire `worldawakened:defaults/...` family without disabling custom namespaces or other World Awakened IDs.

Relaxed and Challenging presets should follow only after the Standard profile has been validated in real worlds. They must use the same engine and remain bounded by existing budgets.

## 4. Native Editor Bridge

The implemented `/wa config` settings flow is a thin client over server-owned configuration:

1. The server checks command and packet permissions.
2. The command sends a revisioned configuration snapshot; a cached server-derived registry catalogue remains a later milestone.
3. The client edits a draft locally; cancel discards it.
4. Apply sends the draft plus its base revision to the server.
5. The server validates bounded runtime settings; when the bundled-profile gate changes, it compiles a candidate through the ordinary datapack loader and publishes it only after validation succeeds.
6. The server returns concise validation/apply results and synchronizes the accepted revision.

Registry catalogues should be built from the running server and refreshed on relevant registry/datapack lifecycle changes. Entries should carry ID, translated/fallback name, namespace, tags, and safe classifications. Missing IDs stay visible as invalid references; they are never silently replaced.

The first editor milestone covers the Standard-profile status, intensity, read-only progression information, mutation frequency and caps, pressure-event enablement/frequency, Ascension enablement, and bundled-gameplay enablement. Progression mode is intentionally not editable until a saved-history migration preview exists. Component descriptors can later supply simple control metadata, but this must not become a second schema language or require bespoke screens for every component.

The settings are stored in world `SavedData`. A world's first native snapshot is seeded from relevant common-config values and its saved global intensity; afterward the world settings are authoritative for exposed controls, while TOML kill switches and safety bounds still win. Runtime multipliers and caps layer over all compiled definitions, including external datapacks, without rewriting their JSON. Disabling bundled gameplay recompiles only the `worldawakened:defaults/...` family filter. Reset changes the client draft to built-in Standard values and still requires Apply; Cancel never sends the draft.

Mutation-pool edits use a separate profile revision and world `SavedData`. Each override is a canonical `MutationPoolDefinition`, decoded with the existing codec and validated against the source snapshot, installed mutators, running-server dimensions, living-entity registry, numeric bounds, and safety cap. Publication overlays only matching source IDs onto the normal datapack snapshot and increments its generation, so existing compiled-graph caches invalidate normally. Reload reapplies overrides once; Revert deletes one override and republishes its current bundled or external source definition.

## 5. Compatibility and Migration Risks

- Existing installations that expected an inert jar will now receive bundled gameplay unless they override/disable the bundled definitions.
- A custom pack using the same bundled namespace/path replaces that object; a different ID adds another eligible object and may change selection probabilities.
- Existing worlds do not retroactively mutate loaded entities. New definitions affect future events/spawns after reload.
- Changing progression mode or removing definitions can leave meaningful saved history; editor migrations need previews and explicit confirmation rather than silent deletion.
- Per-player progression needs an attributable player for spawn-time evaluation and continues to fail closed when none is available.
- Modded registry content can appear or disappear between launches. Saved selections must retain missing IDs and report them clearly.
- The client cannot be trusted for permissions, registry truth, validation, revisions, or apply authority.

## 6. Delivery Sequence

1. Audit and simplify the product model. **Complete.**
2. Ship the ranked Standard profile, curated rare pairs, conservative personal Ascension, additive mutation rewards, and warning-backed pressure events. **Implemented; runtime balance and distribution play-testing remain.**
3. Complete compatibility polish and measured multiplayer/economy/performance validation.
4. Build the `/wa config` server-authoritative settings foundation. **Implemented.**
5. Add the registry browser, mutation editing, and simplified `WHEN -> IF -> THEN` rules incrementally.

The retired hosted web plan is retained only as historical context in [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md). The previous deferred admin-UI note is superseded by this active plan.

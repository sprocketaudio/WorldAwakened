# Simplification and Default Gameplay Plan

- Document status: Active product-direction and delivery plan
- Last updated: 2026-10-18
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
| Native in-game editor | Dashboard, World Phases, profile management, and phase-based Mob Mutations implemented | `/wa config` provides Global Settings, shared stage/trigger authoring, phase/dimension/named mob configurations, codec-backed mutation editing, revisions, validation, persistence, creation/deactivation semantics, and source reversion. |

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

The editor exposes shared progression as one World Phases timeline without showing raw scopes, sequence IDs, or sort indices. Its searchable phase browser shows authored On/Off state, amber Unsaved state for the one active draft, and selected-row ordering arrows. Those arrows update the same ordered draft that Apply persists. Starting means available from the beginning and does not pin or automatically move a phase; cumulative progression may intentionally have more than one starting phase. The right panel contains General, Mob Mutation Dimensions, then Unlock Conditions. One to three searchable runtime-backed milestones may use one flat AND/OR operator; centred AND/OR separators and a directly adjacent explanation make that relationship visible, while the section outline wraps its content. AND remembers each completion durably in either order, while Starting Phase and Manual are exclusive single modes. Existing advanced/multi-sequence datapack content remains preserved when it cannot be represented safely, and new phases join the profile's main progression automatically. Browser rows describe authoring state only; runtime player/world unlock ticks, Current State, and low-level Used By listings are intentionally excluded. Supported milestones compile to ordinary trigger/condition/action definitions plus a small persistent editor gate backed by existing trigger SavedData. Mob Mutations uses one collapsible browser for World Phases and Pressure Events and one shared detail editor. A phase is selected first, followed by its dimension and a named configuration such as Default Mobs or Dangerous Skeletons. Disabled configurations remain visible and editable; the Mob Config picker owns Create, Rename, Enable/Disable, Remove, and source-backed Restore actions while the main header uses aligned selectors and a boxed read-only On/Off status. Enabled specialised configurations require explicit mob selections and remove those mobs from the default configuration during compilation, so specialised content wins deterministically without a second spawn roll; disabling one stops that subtraction. Event pools retain their event conditions and never become ordinary phase pools. Removed source-backed configurations remain recoverable, local draft edits can be discarded, and advanced authored conditions are preserved even when the compact UI does not expose them. Reusable mutators are authored in the same local draft: the component picker is grouped by gameplay purpose, supports all registered mutation component types, and opens a typed settings form whenever a component needs parameters. A mutator is deliberately capped at six components so those settings remain readable. Rarity is not an editor control: pool weights, eligibility, compatibility, and caps determine selection, while existing datapack rarity metadata remains presentation-only. World Phases and Mob Mutations share matching Reset All Defaults, Apply, source restore/delete, Discard Changes, and transient breadcrumb-feedback conventions. Confirmation pages use the specific action as their affirmative button label and centre wrapped explanatory text. A separately confirmed Reset All Defaults action restores bundled Standard and built-in global settings while preserving external datapack files and earned progression. The existing track/tier records remain migration-safe authoring metadata underneath this view; ambiguous older records are preserved without being counted as ordinary active Mob Configs. Publication still emits ordinary mutation pools and stage gates, so the hot path gains no second state machine or collection scan. Progression mode remains informational until a saved-history migration preview exists.

Apply and validation feedback appears temporarily at the right of the breadcrumb row. Success text is deliberately short, stale-edit feedback gives a concise recovery action, and lengthy validation details move into a wrapped hover tooltip. This keeps the editor layout stable while retaining the server's complete diagnostic.

Ascension follows the same editor language instead of exposing trigger-engine terminology. The right panel is ordered General, Ascension Choices, then When to Offer. Phase-linked availability is the ordinary path and follows later changes to that phase automatically; custom availability reuses up to three AND/OR conditions, with the operator always visible and Copy from World Phase creating an independent snapshot. An offer defaults to once per player, counting both pending and resolved instances, so dismissing the choice GUI cannot manufacture another grant. The explicit once-per-source mode preserves intentional advanced repeatability. The reward selector owns selection, creation, editing, and confirmed deletion/restoration. A compact world-authored choice can combine one to three distinct effects from the nine component types with confirmed live handlers; the reward and offer are revision-checked, validated, persisted, and published together.

The settings are stored in world `SavedData`. A world's first native snapshot is seeded from relevant common-config values and its saved global intensity; afterward the world settings are authoritative for exposed controls, while TOML kill switches and safety bounds still win. Runtime multipliers and caps layer over all compiled definitions, including external datapacks, without rewriting their JSON. Disabling bundled gameplay recompiles only the `worldawakened:defaults/...` family filter. Reset changes the client draft to built-in Standard values and still requires Apply; Cancel never sends the draft.

Profile authoring uses a separate revision and world `SavedData`. It distinguishes a source-backed override from a world-created stage, trigger, pool, or track. Related definitions are submitted and validated as one candidate before publication. Revert reveals a bundled/external source; Delete removes only a world-owned definition and is rejected while dependencies remain. Start Empty filters bundled gameplay but keeps external datapacks and the bundled mutator library available for authoring. Copy Standard materializes all bundled definitions into independent world storage and disables the bundled source, so both copies cannot run together.

Native mutator authoring safety note: the component picker may display registered component schemas that do not yet have a safe live runtime handler. Those entries are visible but disabled, with a hover explanation; they cannot be newly selected until the handler exists. Existing datapack definitions remain preserved rather than silently deleted. Component settings use searchable selectors for supported mob effects, ambient particle IDs, entities, equipment slots, and items. Effect Particles are status-effect-coloured visuals only; Ambient Particles use a raw supported particle ID and are purely atmospheric. The Item selector follows the chosen slot: hand slots allow held items, while armour/body slots show items whose vanilla `Equipable` data declares that slot.

Native Ascension authoring uses the same rule. The reward-effect picker lists the complete registered player-effect catalogue, but only effects with a live player reconciliation handler can be selected. Unavailable effects remain visible and disabled with an explanation; existing authored definitions are preserved.

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

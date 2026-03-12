# World Awakened Specification

World Awakened Framework for Minecraft 1.21.1 + NeoForge

- Document status: Active implementation spec (Phase 3 complete, Phase 4 active)
- Last updated: 2026-03-12
- Mod ID: `worldawakened`
- Base package: `net.sprocketgames.worldawakened`

---

## 0. Document Governance

- This file is the primary design and behavior contract for the mod.
- Whenever this spec is expanded or changed, update these files in the same change:
  - `docs/DATAPACK_AUTHORING.md` for concrete user datapack format/schema updates
  - `docs/COMPONENT_REFERENCE.md` for canonical mutation/ascension component IDs, statuses, and parameter schema details
  - `docs/FUTURE_IDEAS.md` when promoting, removing, or reclassifying deferred ideas
  - `docs/FUTURE_ADMIN_UI.md` when promoting, removing, or reshaping the deferred admin UI feature
  - `README.md` for high-level goals, status, and roadmap summary
  - `AGENTS.md` for contributor/agent workflow and guardrails
- New implementation work should not proceed on stale documentation when the mismatch is known.
- Maintain the `Last updated` field at the top of this file on spec revisions.
- `docs/FUTURE_IDEAS.md` is a non-normative backlog and does not alter MVP scope unless an idea is promoted into this spec.
- `docs/FUTURE_ADMIN_UI.md` is a non-normative future feature document and does not alter MVP scope unless it is promoted into this spec.

---

## 1. Purpose

Build a server-authoritative progression and difficulty framework intended for modpacks.

The framework must:
- define configurable progression stages
- keep stage IDs and names fully data-driven
- react to progression triggers (dimension entry, boss kills, advancements, etc.)
- scale mob stats/behavior/spawn pressure from current progression context
- support a world-scoped operator-controlled global difficulty modifier for World Awakened-owned numeric difficulty outputs
- support an optional bounded challenge modifier layer (player-scoped or world-scoped by policy)
- evolve loot and exploration rewards with progression
- grant permanent player-scoped ascension choices as progression or optional external difficulty scalars rise
- run invasion-style world events with configurable scaling
- auto-detect compatible mods while allowing explicit disable toggles
- support generic modded boss and mob handling through entity IDs, tags, and datapack classification even without dedicated compat modules
- support user-extensible rules through datapacks
- expose optional world-context inputs (for example world day and player distance from spawn) as datapack rule conditions, not primary trigger types
- support Apotheosis World Tiers as an optional condition/scalar input
- keep gameplay logic on server side
- keep balance/content externalized to data files

Non-goals for v1:
- arbitrary user scripting language
- AI behavior scripting editor
- full replacement of vanilla spawning
- retroactive mutation of already-loaded mobs

---

## 2. Product Pillars

1. Data-driven content
- Pack makers define progression content and balancing data.
- Java provides reusable behavior implementations and evaluators.

2. Server authority
- Rule evaluation and progression state changes occur server-side.
- Client receives only minimum data required for UX and debug overlays.

3. Optional integrations
- Integrations are additive and never required to load core mod.
- Missing integrations should degrade cleanly without crashes.

4. Operator control
- TOML config remains final runtime gate for risky/expensive systems.
- Datapacks can express capability, config decides runtime permission.

5. Generic-first compatibility
- Unknown future boss/mob mods must first be supportable through generic entity ID/tag matching and datapack boss classification.
- Dedicated Java compat should only be added when generic data-driven support is insufficient.

---

## 2A. Glossary

- `stage`: a data-defined progression marker used as a logic condition and progression milestone
- `trigger`: an event-driven detector that emits actions or named events when its conditions match
- `rule`: a declarative condition-to-action object evaluated against runtime context
- `mutation definition`: a named datapack-authored mob mutation composed from one or more mutation components
- `mutation component type`: a Java-registered behavior type used inside mutation definitions
- `mutation pool`: a grouped set of mutators and selection rules used to roll entity mutations
- `ascension offer`: a player-facing set of candidate permanent rewards from which exactly one may be chosen
- `ascension reward`: a permanent player-scoped named datapack definition composed from one or more ascension components
- `ascension component type`: a Java-registered behavior type used inside ascension rewards
- `runtime instance identity`: the canonical identity used to track a concrete runtime occurrence of a templated definition, such as a granted ascension offer for one player
- `pressure tier`: an optional external or pack-defined progression scalar that may gate rules or offers but is not a required built-in World Awakened subsystem in v1
- `pressure tier provider`: a runtime integration, pack-defined source, or derived evaluator that exposes a numeric pressure tier to World Awakened
- `external scalar`: any non-stage numeric or tiered context input supplied by integrations, pack logic, or world state and consumed by World Awakened evaluation
- `global difficulty modifier`: a world-scoped operator baseline scalar for World Awakened-owned numeric difficulty outputs
- `challenge modifier`: an optional bounded scalar layer applied on top of baseline scaling according to scope and policy
- `effective difficulty scalar`: the deterministic resolved scalar output returned by the shared scalar provider after combining baseline World Awakened values with global and optional challenge modifiers

---

## 3. Core Design Model

The entire framework centers on **stages**.

A stage is a data-defined progression marker that can be:
- unlocked by triggers, conditions, or integration signals
- referenced by rules and profiles
- optionally hidden from players
- grouped/tiered for progression logic
- configured with display metadata independently from logic identity

Examples:
- `worldawakened:baseline`
- `worldawakened:nether_opened`
- `worldawakened:end_reached`
- `worldawakened:bosses_awakened`
- `worldawakened:apocalypse`

Hard constraints:
- stage IDs are never hardcoded in Java as progression ordering logic
- stage names are not logic keys
- internal references always use stage IDs (`ResourceLocation`)
- progression order is data-defined, not code-defined

### 3A. Built-In Content Location Rule

World Awakened built-in/default content should live in bundled datapack objects wherever practical.

Java owns:
- component registries
- component validation and composition safety rules
- execution semantics
- reconciliation and application logic
- persistence logic
- debug and introspection behavior

Bundled datapack content owns:
- default mutation definitions
- default ascension reward definitions
- default ascension offers
- default loot profiles where practical
- default invasion profiles where practical
- default pools/rules/stages where appropriate

Default progression mode in v1:
- `GLOBAL`

Supported modes:
- `GLOBAL` (implemented in v1)
- `PER_PLAYER` (implemented in v1 for stage state, trigger context, and ascension scope; unsupported interactions must be explicitly documented or disabled)
- `HYBRID` (reserved design, deferred implementation)

---

## 4. Stage System

### 4.1 Stage Definition

Each stage is a datapack object with fields:
- `id: ResourceLocation` (required)
- `aliases: ResourceLocation[]` (optional legacy IDs for migration)
- `display_name: ComponentLike` (required)
- `short_name: ComponentLike` (optional)
- `description: ComponentLike` (optional)
- `icon` (optional item/entity/icon ref)
- `sort_index: int` (default 0)
- `visible_to_players: bool` (default true)
- `enabled: bool` (default true)
- `tags: string[] | resource tags` (optional)
- `style` (optional color/style hint)
- `progression_group: string` (optional)
- `unlock_policy: cumulative | exclusive_group | replace_group` (default cumulative)
- `default_unlocked: bool` (default false)

### 4.1A Parallel Stage Groups

World Awakened supports multiple independent progression tracks simultaneously.

A world may have several stage groups active at the same time. Examples:

- `worldawakened:nether_opened`
- `worldawakened:magic_progression_3`
- `worldawakened:tech_progression_2`

These stages may belong to different `progression_group` values and progress independently.

Rules:
- stage groups are logical partitions of progression state
- unlocking a stage in one group must not implicitly modify stages in another group
- rule conditions may reference stages across groups simultaneously
- progression ordering within a group is data-defined through rule and trigger design, not through implicit numeric ordering
- groups may implement mutually exclusive policies when `unlock_policy` specifies `exclusive_group` or `replace_group`

Design goal:
- allow multiple pack-defined progression tracks (technology, magic, exploration, etc.) to coexist without forcing a single linear progression axis

### 4.2 Identity and Naming Rules

- Stage IDs must be unique across all loaded data packs.
- Display names may change without code changes.
- Any user-facing text is presentation only.
- Rules and APIs must resolve stage IDs at validation/load time.
- Stage aliases, when present, must resolve legacy saved references to the canonical current stage ID.

### 4.3 Stage State Persistence

Persist world-level state via `SavedData`:
- unlocked stage IDs
- unlock timestamps
- unlock source metadata
- active group stage (if exclusive/replace policy used)
- invasion cooldown trackers
- world scalar values
- global difficulty modifier value and bounds context
- world-scoped challenge modifier value and policy trackers when enabled

Optional per-player state:
- visited dimensions
- player stage states (for `PER_PLAYER` mode)
- trigger counters/cooldowns
- debug inspection flags
- ascension pending and resolved offers
- chosen and forfeited ascension rewards
- player-scoped challenge modifier value and cooldown/usage trackers when enabled

### 4.4 Stage API Contract

Core API methods:
- `isStageUnlocked(stageId)`
- `unlockStage(stageId, source)`
- `lockStage(stageId)`
- `getUnlockedStages()`
- `getHighestStageInGroup(groupId)`
- `getEffectiveStageContext(world, player?, entity?)`

API behavior requirements:
- idempotent unlock calls
- clear result status (`unlocked`, `already_unlocked`, `blocked`, `invalid`)
- event hook dispatch for successful unlocks

### 4.5 Save Data Ownership and Mode Transition Rules

State ownership must remain clearly separated.

World-level state owns:
- unlocked stages
- stage unlock timestamps
- invasion cooldown trackers
- world scalar values
- global difficulty modifier state
- world-scoped challenge modifier state and vote state when enabled

Player-level state owns:
- visited dimensions
- per-player stage state when `PER_PLAYER` mode is active
- player trigger cooldowns
- debug inspection flags
- pending ascension offers
- resolved ascension offers
- chosen ascension rewards
- forfeited ascension rewards
- player-scoped challenge modifier value and associated cooldown/usage state when enabled

Mode transitions must not corrupt existing save data.

Fallback rules:
- switching progression mode must preserve stored data unless a field has no equivalent in the target mode; if no equivalent exists, preserve the raw stored value and use world state as the authoritative runtime fallback
- if resolution is ambiguous, world state becomes the authoritative fallback
- removed stages remain recorded in save data but marked inactive
- renamed stages should resolve through stage aliases where provided

---

## 5. Progression Modes

### 5.1 GLOBAL (v1 default)
- One world progression state shared by all players.
- Unlock from any valid source affects all players.

### 5.2 PER_PLAYER (v1 partial)
- Player-specific stage state and trigger context.
- World-level events can still read aggregate context if configured.

### 5.3 HYBRID (design only for v1)
- Combined world + player stage spaces.
- Resolution strategy documented but implementation deferred.

Reserved hybrid design concept:
- Rules specify source scope preference (`world`, `player`, `merged`)
- `merged` resolves by explicit policy order

### 5.4 Ascension Scope Across Progression Modes

Ascension choices are primarily player-scoped even when world progression is global.

Rules:
- global world progression may trigger an ascension offer for each eligible player independently
- per-player progression may trigger ascension offers from that player's own progression state
- player choice results never become world-global in v1

---

## 5A. Ascension Choice System

The Ascension Choice System is a permanent player progression reward layer that responds to rising difficulty or progression milestones.

### 5A.1 Purpose and Design Goals

Goals:
- make progression feel like transformation, not only punishment
- give players meaningful long-term build choices
- support replayability through mutually exclusive selections
- remain data-driven in content while keeping effect behavior Java-defined

Hard design rules:
- each qualifying offer instance allows exactly one permanent choice
- rejected options for that offer become permanently forfeited
- resolved offers must never reopen for reselection

### 5A.2 Core Model and Lifecycle

Terminology:
- ascension choice: a single selectable permanent reward
- ascension offer: a set of candidate rewards shown together
- ascension tier link: the mapping between progression context and an offer definition

Recommended v1 lifecycle:
1. a qualifying progression event occurs
2. the engine checks player eligibility for a new offer
3. the offer becomes pending for that player
4. the player receives a clickable message
5. the player opens the ascension GUI
6. the player selects exactly one reward
7. the selection is persisted
8. the offer becomes resolved and cannot be changed

Optional behavior:
- pending offers may remain until chosen
- reminder notifications may repeat if configured
- v1 should keep only one pending offer visible at a time per player and queue later offers if needed

### 5A.2A Offer Definition Identity vs Runtime Instance Identity

Ascension offers need two separate identity layers.

Definition identity:
- the offer definition ID identifies the reusable offer template from datapack content

Runtime instance identity:
- each player-facing granted offer is tracked by a runtime instance key
- v1 canonical instance key should be `(player UUID, offer definition ID, source progression key)`
- the source progression key should be the stage ID, pressure tier key, explicit rule source ID, or another canonical grant source when available
- if no distinct source progression key exists, the engine may fall back to the offer definition ID as the source key for v1

Rules:
- grant behavior is idempotent on runtime instance identity
- reminders, pending-state checks, forfeiture state, and resolution state must attach to the runtime instance identity
- duplicate grant attempts for the same runtime instance must not create a second pending offer

### 5A.3 Trigger Sources and Scope

### 5A.3A Pressure Tier Terminology

For v1, `pressure tier` is always an optional external or pack-defined progression scalar.

Rules:
- it is not a required built-in World Awakened core subsystem in v1
- packs may map it from external mods, custom rules, or other pack-defined logic
- if a pack does not define or expose pressure-tier context, pressure-tier conditions simply evaluate false or do not match

Offers may be triggered by:
- stage unlock
- pressure tier increase
- Apotheosis world tier increase
- explicit `grant_ascension_offer` rule action

Recommended v1 trigger path:
- stage unlock
- optionally pressure-tier threshold if that system exists in the pack

Scope rules:
- ascension offers are player-scoped
- even in `GLOBAL` world progression mode, each player resolves their own ascension rewards
- shared or world-global ascension selection is out of scope for v1

### 5A.4 GUI and Chat Entry Flow

Required UX:
- when a new offer becomes available, send a chat or system message
- the message includes clickable text that opens the ascension GUI
- the GUI shows offer title, description when provided, and a small candidate set

Each reward card should show:
- display name
- short description
- icon
- optional rarity or style hints

Selection rules:
- selecting a reward requires explicit confirmation
- the client only requests the selection
- the server validates pending state, reward eligibility, and lockout rules
- once confirmed, the GUI closes or updates to resolved state

### 5A.5 Persistence Rules

Ascension state is player-scoped persistent data.

Persist:
- pending offer IDs
- resolved offer IDs
- chosen reward IDs
- forfeited reward IDs by offer
- unlock timestamps
- source stage or tier metadata for audit and debug

Hard rules:
- chosen rewards are permanent unless future admin or debug tooling explicitly revokes them
- forfeited rewards from a resolved offer must never reappear for that same offer
- if a chosen reward definition is later removed, the saved chosen reward ID remains recorded, the effect is no longer applied once reconciliation detects the missing definition, no automatic substitution occurs, and a structured warning should be emitted for operators in validation/debug output

### 5A.6 Reward Definition Schema

Ascension rewards are named datapack-authored definitions composed from ascension components.

Each reward definition should support:
- `id`
- `display_name`
- `description`
- `icon`
- `enabled`
- `components[]`
- `rarity`
- `tags[]`
- `tier_weight` or `offer_weight`
- `unique_group`
- `exclusion_tags[]`
- `requires_conditions[]`
- `forbidden_conditions[]`
- `ui_style`
- `max_rank` (reserved for future, default `1`)

Design rule:
- datapacks define what reward can appear and when
- datapacks define reward component combinations and parameters
- Java defines what each ascension component type actually does
- `offer_weight` is used when selecting rewards within a concrete offer candidate set
- `tier_weight` is used only for higher-level tier or pool-driven reward generation when that generation mode exists

Identity boundary:
- reward ID is the authored identity used by offers, persistence, and inspect/debug output
- component types are behavior primitives and are not the user-facing reward identity

### 5A.7 Ascension Component Types

Examples of v1 ascension component types:
- `max_health_bonus`
- `movement_speed_bonus`
- `armor_bonus`
- `attack_damage_bonus`
- `knockback_resistance_bonus`
- `luck_bonus`
- `xp_gain_bonus`
- `loot_quality_bonus`
- `potion_resistance`
- `fire_resistance_like_passive`
- `extra_revival_buffer`
- `night_vision_like_passive`
- `fall_damage_reduction`
- `healing_efficiency_bonus`
- `mob_detection_bonus`
- `invasion_reward_bonus`
- `mutation_resistance_bonus`

All ascension component applications must be:
- permanent
- server-authoritative
- safely reapplied on login and respawn when needed
- inspectable through debug tools

### 5A.8 Offer Definition Schema

Ascension offers are datapack objects that map progression context to a candidate reward pool.

Fields:
- `id`
- `display_name`
- `description`
- `enabled`
- `trigger_conditions[]`
- `stage_filters`
- `pressure_tier_filters`
- `apotheosis_tier_filters`
- `choice_count`
- `selection_count`
- `candidate_rewards[]`
- `candidate_reward_tags[]`
- `offer_mode`
- `weighting_rules`
- `ui_priority`
- `allow_duplicates_across_players`
- `allow_reward_reuse_across_offers`

Offer modes:
- `explicit_list`
- `weighted_from_pool`
- `weighted_from_tag_group`

Recommended v1:
- `selection_count = 1`
- `explicit_list` and `weighted_from_pool` only

### 5A.9 Selection and Lockout Rules

Rules:
- a player may select exactly one reward from an offer
- once selected, the offer becomes resolved
- all unchosen rewards in that offer become permanently forfeited
- forfeited rewards from that offer never appear again for that player
- a chosen reward cannot be selected again unless explicitly allowed by reward config
- `unique_group` may block future similar rewards

Suggested evaluation precedence:
1. already chosen reward exclusion
2. forfeited reward exclusion
3. `unique_group` exclusion
4. `requires_conditions` and `forbidden_conditions`
5. weighted selection or explicit display ordering

### 5A.10 Runtime Application Rules

Chosen rewards are the source of truth. Live effects are derived from saved chosen rewards.

Required reapplication points:
- player login
- player respawn
- datapack reload when reward definitions remain valid
- dimension change only if an effect implementation requires refresh

Application model:
- rewards grant persistent player state or modifiers
- component handlers reconcile active bonuses from saved selections
- repeated login or respawn must not duplicate stack the same reward component outcomes
- if a chosen reward definition is missing during reconciliation, its saved ID remains recorded but its live effect is not applied

### 5A.11 Validation and Debug Expectations

On datapack reload (and config reload for config-owned settings), validate:
- duplicate reward IDs
- duplicate offer IDs
- missing reward references
- `choice_count < 1`
- `selection_count != 1` in v1
- no valid candidate rewards after filtering
- empty `components[]`
- unknown component types
- invalid component parameters
- incompatible component compositions
- duplicate component types when duplicates are unsupported
- component compositions with no valid runtime result
- invalid icon references
- contradictory or impossible conditions
- offers that can never trigger

Required debug and command support:
- `/wa ascension list <player>`
- `/wa ascension pending <player>`
- `/wa ascension open <player>`
- `/wa ascension grant_offer <player> <offer_id>`
- `/wa ascension choose <player> <offer_id> <reward_id>`
- `/wa ascension revoke <player> <reward_id>`
- `/wa ascension inspect <player>`

Inspect output should show:
- pending offers
- resolved offers
- chosen rewards
- forfeited rewards
- active permanent effects
- source stages or tiers for each reward

Debug trace should be able to explain:
- why an offer triggered
- why a reward was eligible or ineligible
- whether a reward was filtered by forfeiture, `unique_group`, or missing definition state

Example ascension inspect/debug lines:
- `Chosen Ascension Reward: my_pack:unyielding_hunter`
- `Components: hostile_wall_sense, debuff_resistance, attack_damage_bonus`

### 5A.12 v1 Recommendation

Keep the first implementation tight:
- trigger offers from stage unlocks
- allow one pending offer at a time per player
- open GUI from clickable chat message
- show 2 or 3 rewards
- allow exactly 1 choice
- keep rewards passive and permanent
- do not allow rerolls, expiry, or rank upgrades in v1

Hard line:
- datapacks choose which rewards can appear and when
- datapacks author component compositions
- Java defines how ascension component types function
- player choice is permanent and exclusive per offer
- forfeited options stay lost

---

## 5B. Difficulty and Challenge Modifiers

World Awakened may expose two scalar layers for operator and player-facing tuning:
- global difficulty modifier (world-scoped baseline)
- challenge modifier (optional adjustment layer with scope and policy control)

Hard contract:
- these systems only affect World Awakened-owned numeric difficulty outputs
- these systems do not mutate stage unlock state
- these systems do not change trigger eligibility or trigger execution
- scalar composition is deterministic and provided by one shared resolver

### 5B.1 Global Difficulty Modifier

World Awakened may expose a separate operator-controlled global difficulty modifier.

Purpose:
- provide a simple server-level tuning control
- allow server owners or pack maintainers to tune World Awakened harder or easier without editing datapacks
- act as a baseline intensity scalar independent of progression stages, pressure tiers, or player challenge adjustments

Scope:
- world-scoped only
- controlled by config and operator or admin commands only
- applies regardless of progression mode (`GLOBAL` or `PER_PLAYER`)

Rules:
- affects only World Awakened-owned numeric difficulty outputs
- does not replace stage progression
- does not alter trigger eligibility or trigger execution
- does not directly change stage unlock state
- stacks deterministically with other World Awakened difficulty scalars using a documented scalar-composition rule

Examples of affected outputs:
- mob stat scaling
- mutator chance
- spawn pressure
- invasion wave budget
- invasion elite chance
- World Awakened-owned reward scaling when configured

Examples of unaffected systems:
- stage IDs and unlock state
- datapack rule definitions
- trigger eligibility and trigger type behavior
- non-World Awakened systems unless explicitly integrated

Recommended command support:
- `/wa difficulty global get`
- `/wa difficulty global set <value>`
- `/wa difficulty global reset`

Design rule:
- progression determines when the world changes
- global difficulty modifier determines baseline intensity of World Awakened-owned numeric difficulty outputs

### 5B.2 Optional Challenge Modifier System

World Awakened may expose an optional challenge modifier system to raise or lower challenge independently of progression stages.

Purpose:
- support accessibility for weaker players
- support optional extra challenge for stronger players
- support mixed-skill multiplayer groups
- allow challenge tuning without changing datapacks or core stage progression

Hard rules:
- optional subsystem
- must not replace progression stages
- must not alter trigger logic
- must only affect World Awakened-owned numeric difficulty outputs
- must stack cleanly with the global difficulty modifier
- must remain bounded by explicit config limits

Conceptual model:
- progression controls what is unlocked
- global difficulty modifier controls baseline intensity
- challenge modifier controls optional easing or escalation on top

Example scalar composition:
- `effective_value = base_wa_value * global_difficulty_modifier * challenge_modifier`

Exact composition may be implementation-defined, but must be deterministic and documented.

### 5B.3 Scope Resolution Rules

Supported scope modes:
- `auto`
- `player`
- `world`

Resolution behavior:
- `auto` + `PER_PLAYER` progression resolves challenge modifier as player-scoped
- `auto` + `GLOBAL` progression resolves challenge modifier as world-scoped
- `player` forces player scope only when evaluation path can safely support it
- `world` forces world scope

Safety rule:
- unsupported scope combinations must be rejected or disabled with validation diagnostics rather than silently misbehaving

### 5B.4 Player-Scoped Challenge Modifier Behavior

When scope is player:
- each player has their own challenge modifier value
- one player's adjustment must not directly change another player's modifier
- player-context World Awakened evaluations may use that player's modifier for World Awakened-owned numeric difficulty outputs

Use cases:
- stronger players opting into higher challenge
- weaker players lowering challenge for accessibility
- mixed-skill groups without forcing one world-wide setting

Examples of affected player-scoped outputs (when those systems evaluate with player context):
- mob stat scaling in player-context encounters
- mutator chance for encounters resolved against that player context
- invasion or reward scaling only for systems that explicitly support player-scoped scaling

Restrictions:
- must not mutate global stage state
- must not alter another player's ascension, stage, or reward state

### 5B.5 World-Scoped Challenge Modifier Behavior

When scope is world:
- one shared world value affects all players
- any accepted change applies globally

Safety rule:
- if non-operator world-scoped changes are allowed, configurable approval or vote flow should be required unless explicitly disabled by server policy

### 5B.6 Player Permissions and Control Policy

Configurable policy controls should include:
- whether challenge adjustment is enabled
- whether players may raise challenge
- whether players may lower challenge
- whether player adjustments are allowed only in player scope
- whether world-scoped changes require vote approval
- whether operator or admin override is always allowed

Design rule:
- player challenge adjustment is optional and server-controlled
- accessibility-focused lowering and opt-in escalation should both be possible when enabled

### 5B.7 Bounds, Step Size, and Frequency Controls

Challenge modifier changes must be bounded and rate-limited.

Configurable controls:
- minimum and maximum values
- step size per adjustment
- cooldown between changes
- max changes per player
- max total world changes (world scope)
- optional reset behavior
- optional once-only behavior

Rules:
- out-of-bounds attempts must be rejected cleanly
- changes during cooldown must be rejected cleanly
- changes beyond configured counts must be rejected cleanly

### 5B.8 Change Lifetime Options

Supported lifetime models may include:
- persistent until changed
- time-limited
- once-only
- limited-use
- reset on configured policy trigger

Recommended v1 behavior:
- persistent until changed, subject to cooldown and usage limits

### 5B.9 Activation Paths

Supported activation paths may include:
- operator or admin command
- player command (if enabled)
- consumable item (optional later)
- ritual or block interaction (optional later)
- future integration hook

Recommended command support:
- `/wa difficulty personal get`
- `/wa difficulty personal raise`
- `/wa difficulty personal lower`
- `/wa difficulty personal set <value>`
- `/wa difficulty world get`
- `/wa difficulty world raise`
- `/wa difficulty world lower`
- `/wa difficulty world set <value>`
- `/wa difficulty vote yes`
- `/wa difficulty vote no`

Any non-command activation must still obey bounds, cooldowns, permissions, and scope policies.

### 5B.10 Vote System for World-Scoped Changes

When world-scoped player-triggered adjustment is enabled, vote or approval flow may be required.

Vote controls should include:
- whether voting is required
- approval threshold ratio
- minimum eligible voters
- vote timeout window
- abstention handling
- operator override behavior
- whether only online eligible players are counted

Vote behavior:
- proposed change enters pending-vote state
- players vote yes or no during configured window
- threshold met: change applies
- threshold not met: change is rejected
- cooldown and usage limits still apply after approval

### 5B.11 Persistence Rules

Challenge modifier persistence follows scope.

Player-scoped persistence may include:
- current player modifier value
- cooldown tracker
- number of changes used
- pending request state when relevant

World-scoped persistence may include:
- current world modifier value
- world cooldown tracker
- number of world changes used
- active or pending vote state (if persisted by policy)

Rule:
- persistence must be deterministic across restarts according to configured policy

### 5B.12 Validation and Failure Rules

Validate and report:
- invalid scope mode
- invalid min or max bounds
- step size <= 0
- default value outside bounds
- cooldown < 0
- incompatible scope settings for active progression mode
- vote required without valid vote settings
- activation path enabled while system disabled

Failure behavior:
- disable only the affected challenge subsystem branch where possible
- unrelated World Awakened systems continue unless startup integrity is impossible

Recommended diagnostics:
- `WA_DIFFICULTY_GLOBAL_INVALID`
- `WA_CHALLENGE_SCOPE_INVALID`
- `WA_CHALLENGE_BOUNDS_INVALID`
- `WA_CHALLENGE_STEP_INVALID`
- `WA_CHALLENGE_VOTE_CONFIG_INVALID`
- `WA_CHALLENGE_MODE_UNSUPPORTED`

### 5B.13 Shared Design Rules and API Contract

Design rules:
- global difficulty modifier is the server baseline knob
- challenge modifier is an optional player or world adjustment layer
- neither system replaces progression stages
- both systems are bounded intensity scalars applied only to World Awakened-owned numeric difficulty outputs

Implementation note:
- global and challenge modifiers should be resolved via one shared scalar provider or service
- World Awakened subsystems should query that service instead of duplicating scaling logic

Conceptual API contract:
- `getEffectiveDifficultyScalar(world, player?, context)`

---

## 6. Apotheosis World Tier Support

Goal:
Integrate Apotheosis World Tiers when present, without introducing hard dependency.

### 6.1 Runtime Activation

Apotheosis integration active only if:
1. Apotheosis mod is loaded
2. `compat.apotheosis.enabled = true`
3. Integration profile and rules are enabled

If any gate fails:
- Apotheosis-specific conditions evaluate false or are skipped
- data errors become validation warnings where appropriate
- no crashes

### 6.2 v1 Scope

World tier can be used as:
- condition provider
- stage unlock trigger provider
- scalar input for mutators/spawn pressure
- scalar input for loot
- scalar input for invasions

### 6.3 Supported Condition Semantics

- `current_apotheosis_world_tier == X`
- `current_apotheosis_world_tier >= X`
- `current_apotheosis_world_tier in [a,b,c]`

### 6.4 Mapping Modes

- `independent`: stages and tiers separate, rules can reference both
- `derived_stage`: configured tier thresholds unlock stages
- `scalar`: tier only modifies numeric scaling
- `hybrid`: derived stage + scalar modifiers together

### 6.5 Safety Rules

- no hard class references without safe gating
- integration code isolated in `compat.apotheosis`
- log when Apotheosis-tagged data is skipped
- integration remains read-only unless explicit upstream API contract requires writes

### 6.6 Apotheosis Loot Compatibility Rules

When Apotheosis integration is active, World Awakened must treat Apotheosis world-tier loot behavior as authoritative for Apotheosis-managed tier-gated loot outcomes.

Authority rules:
- Apotheosis remains authoritative for its own world-tier chest loot scaling
- Apotheosis remains authoritative for affix loot generation
- Apotheosis remains authoritative for gem-related loot generation
- Apotheosis remains authoritative for any other Apotheosis-owned tier-gated loot outcomes
- World Awakened may extend loot results, but must not replace or suppress Apotheosis-owned world-tier behavior

Allowed World Awakened behavior:
- inject additional loot entries
- add bonus pools
- add conditional stage-based rewards
- add conditional mutation or invasion rewards
- scale World Awakened-owned injected rewards using Apotheosis world tier when configured

Forbidden World Awakened behavior while Apotheosis loot compat is active:
- replacing Apotheosis-managed chest loot behavior
- removing Apotheosis tier-gated loot paths
- bypassing Apotheosis world-tier loot conditions
- forcing destructive loot-table replacement on targets known to be affected by Apotheosis world-tier loot handling

Execution and composition rules:
- World Awakened loot behavior must compose with Apotheosis rather than override it
- World Awakened should assume Apotheosis world-tier logic may already have modified loot quality, rarity, or selection weight before World Awakened-owned reward extensions are applied
- World Awakened must treat its own loot effects as additive unless a future explicitly documented compat-safe exception exists

Preferred behavior on Apotheosis-sensitive loot targets:
- `inject`
- `add_bonus_pool`

Restricted behavior on Apotheosis-sensitive loot targets:
- `replace_entries`
- `remove_entries`

If a restricted behavior is encountered:
- validation should emit a structured warning or error
- the profile should be blocked, downgraded to a safe additive mode, or disabled according to the active validation policy
- the engine must never silently destroy Apotheosis tier-gated loot behavior

Apotheosis-sensitive loot target detection:
A loot target is Apotheosis-sensitive when any of the following is true:
- the target is known to be modified by Apotheosis world-tier loot conditions
- the target contains Apotheosis world-tier-gated outcomes
- the target is explicitly marked as Apotheosis-sensitive by compat metadata or future compat registries

Validation requirements when Apotheosis compat is active:
- detect and report unsafe replacement or removal behavior against Apotheosis-sensitive targets
- detect and report incompatible loot profile modes
- detect and report attempted destructive overrides of Apotheosis-owned tier-gated loot paths

Validation output should include:
- World Awakened loot profile ID
- target loot table ID
- blocked or unsafe mode
- canonical error or warning code
- fallback action taken

Recommended canonical diagnostics:
- `WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED`
- `WA_APOTHEOSIS_LOOT_MODE_UNSAFE`
- `WA_APOTHEOSIS_LOOT_TARGET_SENSITIVE`

Fallback behavior preference order:
1. block the unsafe operation and log it
2. preserve Apotheosis behavior unchanged
3. apply only safe additive World Awakened rewards when possible
4. otherwise disable the offending World Awakened loot profile branch

Design rule:
Apotheosis world tier and World Awakened progression should synergize, not compete.

That means:
- Apotheosis controls its own tier-based loot progression
- World Awakened adds contextual progression rewards on top
- both systems may influence final rewards without one erasing the other

Future compatibility note:
If a later Apotheosis version exposes explicit compat APIs, metadata, or registries for loot-stage interaction, World Awakened should prefer those over heuristic target detection.

---

## 7. Trigger Engine

A trigger consumes events and emits actions or rule events.

Responsibility split:
- triggers react directly to external events
- rules react to evaluated runtime context snapshots

### 7.1 Trigger Types (v1)

- player enters dimension
- player completes advancement
- entity with configured tag/ID dies
- configured boss dies
- item crafted
- block placed
- block broken
- command/manual debug trigger
- Apotheosis tier threshold reached
- integration-specific signal event

World-context checks such as world day and distance-from-spawn must be modeled as rule conditions (Section 8), not as core trigger types.

### 7.2 Trigger Output Actions

- unlock stage
- emit named event
- increment counter
- start/update cooldown
- schedule invasion chance check

### 7.3 Trigger Rule Schema

Fields:
- `id`
- `enabled`
- `priority`
- `trigger_type`
- `conditions[]`
- `actions[]`
- `cooldown`
- `one_shot`
- `source_scope: world | player`

Execution rules:
- higher priority resolves first
- one-shot triggers persist consumed state
- cooldown keyed by scope and trigger ID

### 7.4 Boss Kill Classification

Boss-kill triggers must not rely exclusively on hardcoded entity lists.

Supported boss classification inputs:
- explicit entity ID match
- entity tag match
- datapack-provided boss flag maps
- dedicated compat provider result when available

Resolution order:
1. explicit entity ID rule
2. entity tag match
3. datapack boss flag map
4. integration-provided classification

### 7.5 Selector and Entity Matching Semantics

Selectors determine which entities, mobs, or bosses are targeted by triggers, rules, mutators, and invasion composition filters.

Supported match types:
- explicit entity ID
- entity tag
- namespace wildcard
- mob category
- datapack boss flag map
- blacklist selectors

Matching precedence:
1. explicit entity ID
2. explicit entity tag
3. namespace wildcard
4. mob category
5. datapack boss flag map
6. default eligibility maps

Combination rules:
- positive selector entries are ORed by default
- blacklist entries always apply as AND exclusions
- blacklist matches always override positive matches

Selector implementations must compile into cached matchers during datapack reload to avoid runtime parsing in hot paths.

---

## 8. Rule Engine

Rules are declarative `conditions -> actions` objects.

### 8.1 Rule Schema

- `id`
- `enabled`
- `priority`
- `conditions[]`
- `actions[]`
- `weight` (for weighted selections)
- `chance`
- `cooldown`
- `execution_scope: world | player | entity | spawn_event`
- `tags[]`

### 8.2 Condition Types (v1)

- `stage_unlocked`
- `stage_locked`
- `ascension_reward_owned`
- `ascension_offer_pending`
- `current_dimension`
- `current_biome`
- `world_day_gte`
- `player_distance_from_spawn`
- `loaded_mod`
- `config_toggle_enabled`
- `entity_type`
- `entity_tag`
- `entity_not_boss`
- `entity_is_mutated`
- `player_count_online`
- `local_difficulty_range`
- `apotheosis_world_tier_compare`
- `random_chance`
- `moon_phase`
- `structure_context`
- `invasion_active`

### 8.2A Optional World-Context Condition Semantics

World-context conditions are optional datapack-driven rule inputs. They must not be treated as primary built-in progression systems.

Supported optional world-context conditions include:
- `world_day_gte`
  - true when the world day counter is greater than or equal to the configured threshold
  - expected payload field: `value` (integer day threshold)
- `player_distance_from_spawn`
  - true when the player's distance from world spawn is within the configured range
  - expected payload fields: `min` and/or `max` (inclusive range; at least one must be present)

Design rules:
- these conditions are optional and datapack-driven
- World Awakened progression must not rely on them as primary progression systems
- pack authors may use them to build custom scaling rules where useful
- if a condition cannot be evaluated in the current context, it evaluates false

### 8.3 Action Types (v1)

- `unlock_stage`
- `lock_stage`
- `grant_ascension_offer`
- `apply_mutator_pool`
- `apply_stat_profile`
- `inject_loot_profile`
- `trigger_invasion_profile`
- `send_warning_message`
- `drop_reward_table`
- `mark_rule_consumed`
- `set_world_scalar`
- `set_temp_invasion_modifier`

### 8.4 Evaluation Guarantees

- deterministic condition parsing and validation
- bounded action execution (no infinite loops)
- per-rule cooldown and chance handling
- structured debug trace for rule decisions in debug mode

### 8.5 Rule Conflict Resolution

When multiple rules, mutators, profiles, or integrations match the same context, the engine must resolve them deterministically.

Rule evaluation order:
1. filter by enabled state
2. filter by config gates
3. filter by integration gates
4. filter by selector, entity, and execution scope matches
5. filter by stage, tier, optional world-context, and datapack conditions
6. sort by priority, highest first
7. apply cooldown validation
8. apply one-shot validation
9. apply chance roll
10. execute actions

Conflict policies:

Stat profile conflicts:
- attribute modifiers stack unless explicitly marked exclusive
- mutators with identical `stacking_group` obey `max_stack_count`
- exclusive mutators cancel lower-priority candidates

Action conflicts:
- `unlock_stage` is idempotent and always safe
- `lock_stage` only succeeds if allowed by config policy
- `grant_ascension_offer` is idempotent per player and offer identity
- `set_world_scalar` uses last-writer-wins after priority sorting

Loot conflicts:
- `replace_entries` overrides inject-style entries unless blocked by integration safety rules
- inject-style entries merge additively unless config forbids them
- when Apotheosis integration is active and a target is Apotheosis-sensitive, World Awakened loot changes must remain additive (`inject`, `add_bonus_pool`) and must not destructively override Apotheosis-owned tier-gated loot behavior

Invasion conflicts:
- if multiple invasion rules trigger simultaneously, only the highest-priority rule executes unless the invasion profile explicitly allows concurrency

Integration conflicts:
- datapack rules always override integration defaults
- explicit datapack configuration takes precedence over integration-provided data

### 8.6 Recursion and Re-evaluation Safety

- rule recursion must be prevented
- repeated evaluation within the same tick must not reroll the same outcome for the same evaluation context
- action execution must not create unbounded self-triggering loops

---

## Runtime Evaluation Graph and Execution Semantics

This section is normative. It defines the exact runtime order in which World Awakened evaluates progression, triggers, rules, mutators, loot effects, and invasions.

### Runtime Evaluation Layers

All World Awakened logic executes within explicit runtime layers:

Layer 0: static data load
- datapack object parsing
- codec validation
- selector compilation
- rule graph compilation
- integration activation resolution
- config gate resolution
- validation summary generation

Layer 1: persistent state read
- world stage state
- player progression state
- player ascension state
- invasion state
- cooldown state
- counters and scalars

Layer 2: event capture
- vanilla event hook
- NeoForge event hook
- compat signal hook
- command or debug trigger

Layer 3: trigger evaluation
- trigger rule matching
- trigger cooldown checks
- trigger one-shot checks
- trigger actions emitted

Layer 4: rule evaluation
- generic rules evaluated from current context
- condition tree execution
- bounded action queue built

Layer 5: action application
- stage unlock or lock
- ascension offer grant and reward-state reconciliation
- scalar updates
- mutator application
- invasion scheduling
- loot profile activation
- reward emission

Layer 6: client and debug output
- toasts and messages
- optional debug overlay sync
- command and query output
- structured trace logging

Hard rules:
- later runtime layers must never mutate compiled definitions created by earlier layers during live gameplay
- runtime actions operate on state, not on loaded content definitions

### Event-to-Action Pipeline

All live gameplay events must pass through this canonical flow:
1. receive event context
2. build evaluation context snapshot
3. resolve progression mode context
4. evaluate trigger rules
5. apply trigger outputs
6. evaluate generic rules
7. build bounded action queue
8. resolve action conflicts
9. apply actions
10. emit notifications and debug trace

Evaluation context snapshot must include where applicable:
- world reference
- player reference
- entity reference
- dimension
- biome
- structure context when known
- active stages
- config gates
- loaded integrations
- Apotheosis tier context when active
- global difficulty modifier context
- challenge modifier context (if enabled)
- effective difficulty scalar context
- player ascension state when relevant
- current cooldown state
- random source handle
- event type and event payload

Hard rule:
- the snapshot is immutable for the duration of a single evaluation pass except for explicit state changes applied during action application

This prevents mid-pass rule drift.

### Spawn Event Pipeline

Spawn-path logic must remain tightly bounded and deterministic.

For an eligible spawn event:
1. receive spawn event
2. reject immediately if feature or config gates disable processing
3. build spawn evaluation context snapshot
4. resolve active stage context
5. resolve external scalar context
6. match trigger rules bound to `spawn_event` if any
7. match generic spawn rules
8. collect candidate mutation pools
9. filter by selectors and exclusions
10. filter by boss rules and compat restrictions
11. roll candidate mutators
12. enforce stack and exclusivity constraints
13. apply stat profile changes
14. apply behavioral mutators
15. attach provenance and debug metadata
16. finalize spawn

External scalar context should include when enabled:
- global difficulty modifier
- challenge modifier (resolved per scope)
- effective difficulty scalar
- integration-provided scalars (such as Apotheosis tier scalars when active)

Hard guarantees:
- no datapack parsing in this path
- no unbounded rerolls
- no recursive re-entry from mutator application
- no more than one final application pass per entity spawn

Failure behavior:
- if no valid mutator or rule matches, the entity spawns normally
- optional debug trace may still record the rejection chain
- if World Awakened spawn processing fails, log structured diagnostics and complete the spawn without applying World Awakened modifications

### Loot Evaluation Pipeline

Loot modification must occur through a bounded read-then-assemble flow:
1. receive loot context
2. identify target loot table or drop context
3. build evaluation context snapshot
4. resolve active stage context
5. resolve external scalars
6. match eligible loot profiles
7. sort by priority when relevant
8. apply config restrictions
9. apply integration safety restrictions for sensitive targets (including Apotheosis when active)
10. resolve replace, inject, and remove semantics
11. emit final modified loot result

Required behavior:
- loot profile evaluation remains read-only until the final assembly step
- replace semantics apply before inject semantics when both are present in the same resolved context
- invalid loot entries disable the containing loot profile and do not trigger automatic substitution
- if an unsafe loot mode is blocked by compat safety rules, fallback handling must be explicit and logged; Apotheosis-owned tier-gated behavior must remain intact

Covered loot source types:
- chest and structure loot
- invasion rewards
- mutated mob bonus drops
- boss bonus rolls
- stage unlock rewards

### Invasion Evaluation Pipeline

Invasion logic must be explicit because it affects global pacing, entity counts, and player-facing events.

Invasion scheduler pipeline:
1. tick scheduler or receive trigger signal
2. check global invasion feature gate
3. check active invasion cap
4. check cooldowns
5. build world or player target context
6. match invasion trigger rules
7. match invasion profiles
8. resolve profile priority and chance
9. schedule invasion instance
10. emit warning phase
11. spawn waves on interval
12. track active invasion state
13. resolve rewards, failure, and cleanup
14. enter cooldown state

Wave spawn sub-pipeline:
1. build wave context snapshot
2. resolve profile-defined composition
3. apply stage scalars
4. apply global and challenge modifier scalars
5. apply Apotheosis scalars when active
6. roll unit composition
7. apply mutator pools to eligible invasion units
8. enforce hard caps and safe-zone rules
9. spawn wave
10. record active entity tracking

Hard rules:
- invasion scheduling and wave spawning are separate phases
- warning phase must occur before the first wave when the profile requires it
- failed wave spawn must degrade gracefully rather than leaving the invasion permanently stuck active

### Stage Unlock Propagation Rules

Stage changes can affect future evaluation, but they must not retroactively rewrite the already-running pass unless explicitly documented otherwise.

Propagation contract:
- stage unlocks become authoritative immediately after the action application phase completes
- rules evaluated later in the same event pass may only see newly unlocked stages if the engine explicitly supports multi-pass resolution
- v1 uses single-pass resolution

v1 rule:
- event snapshots use pre-action stage context
- action results apply after evaluation
- newly unlocked stages affect subsequent events, not the currently running event pass

Optional future extension:
- a bounded second-pass resolver may be added later for explicitly advanced rules, but it is out of scope for v1

### Single-Pass Stage Propagation Guarantee

World Awakened uses single-pass evaluation semantics in v1.

Rule:
- stage unlocks never re-evaluate the same event that triggered them

Example:
- boss kill event occurs
- rule unlocks stage `worldawakened:bosses_awakened`
- the same event does not re-run rule evaluation with that new stage active

The newly unlocked stage affects only subsequent events.

Purpose:
- prevent recursive rule cascades
- maintain deterministic evaluation order
- avoid accidental duplicate rewards, repeated mutation rolls, or same-event reprocessing

Future versions may optionally introduce bounded multi-pass evaluation, but this is explicitly out of scope for v1.

### Re-entrancy and Recursion Guards

World Awakened must prevent self-triggered runaway behavior.

The following must be guarded:
- rule actions causing the same rule to refire indefinitely
- mutator application causing repeated spawn re-processing
- invasion reward logic retriggering invasion rules
- stage unlock actions recursively retriggering identical unlocks

Required safeguards:
- per-pass evaluation token
- action queue depth limit
- recursion detection for rule IDs
- spawn-processing marker on the entity
- one-shot and cooldown persistence
- explicit no-reentry zones for invasion resolution

Failure behavior:
- abort the recursive branch
- log a structured warning
- preserve server stability over feature completion

### Runtime Caching Model

All runtime evaluation must use compiled or cached objects produced at datapack reload time.

Required caches:
- compiled stage lookup map
- compiled rule objects
- compiled trigger objects
- compiled ascension reward definitions
- compiled ascension offer matchers
- compiled selector matchers
- compiled mutator pool matchers
- compiled loot profile matchers
- compiled invasion profile matchers
- compat activation registry
- boss classification cache

Cache invalidation rules:
- datapack reload invalidates and rebuilds all compiled objects
- config reload invalidates runtime gate views as needed
- live gameplay must never mutate compiled definitions

Performance rule:
- no JSON tree walking during spawn paths or other hot paths
- expensive selector and tag resolution should be precompiled during reload; if that is impossible for a specific hook, the result must be resolved once and cached before repeated hot-path use

### Debug Trace Contract

Every major evaluation path must be traceable in debug mode.

### Debug Trace ID

Each evaluation pass should produce a unique trace identifier.

Purpose:
- correlate multi-stage rule evaluations across logs
- simplify debugging in multiplayer environments
- allow operators to trace complex rule chains

Example debug output:
- `Trace ID: 8F3A2`
- `Event: entity_spawn`
- `Stage Context: worldawakened:nether_opened`
- `Eligible Pools: undead_tier_1, overworld_night_stage_2`
- `Selected Pool: undead_tier_1`
- `Applied Mutation: my_pack:summoner_juggernaut`
- `Applied Mutation Components: reinforcement_summon, summon_cooldown, max_health_multiplier, armor_bonus`
- `Rejected Mutation: my_pack:glass_cannon (component conflict)`

Trace IDs must remain stable throughout the entire evaluation pass.

Debug traces should be structured around:
- event type
- context snapshot summary
- matched objects
- rejected objects
- rejection reasons
- executed actions
- final state changes

Required rejection reason categories:
- feature disabled
- config gate failed
- integration inactive
- selector mismatch
- stage condition failed
- world-context condition unavailable
- forfeited by resolved offer
- unique-group conflict
- cooldown active
- one-shot consumed
- chance roll failed
- exclusive conflict
- hard cap reached
- invalid referenced object
- safe-zone blocked

Traceable systems:
- trigger evaluation
- rule evaluation
- ascension offer triggering and reward selection
- mutator selection
- loot profile selection
- invasion scheduling
- invasion wave spawning
- stage unlock attempts

Command and debug output must summarize rather than dumping raw internals unless explicitly requested in operator mode.

### Failure Isolation Rules

A broken World Awakened object must not crash unrelated systems unless the framework cannot safely continue.

Isolation rules:
- invalid trigger disables only that trigger object
- invalid ascension reward disables only that reward object
- invalid ascension offer disables only that offer object
- invalid mutator disables only that mutator object
- invalid pool disables only that pool object
- invalid loot profile disables only that profile
- invalid invasion profile disables only that profile
- invalid stage is severe because other objects may depend on it

Stage failure policy:
- if a referenced stage ID is invalid, dependent objects fail validation and are disabled
- if baseline or otherwise required core stage data is missing and startup depends on it, startup may fail with a clear error

Runtime failure policy:
- hot-path runtime errors should fail closed for the affected World Awakened action; only framework-integrity failures may justify aborting startup or hard-failing the subsystem
- isolated failures must emit structured diagnostics

### Priority, Chance, and Cooldown Ordering

All candidate objects using these controls evaluate in the same canonical order:
1. enabled
2. config gate
3. integration gate
4. selector, entity, and context match
5. stage, tier, and optional world-context conditions
6. priority sort
7. cooldown check
8. one-shot check
9. chance roll
10. final execution

Hard rules:
- chance rolls occur only after full eligibility is known
- cooldown failures do not consume chance rolls
- one-shot consumed state blocks evaluation before chance roll
- priority affects order of consideration, not validity

### Multi-System Interaction Rules

When a single event can affect multiple World Awakened systems, the engine processes them in this order unless a subsystem explicitly documents otherwise:
1. triggers
2. stage actions
3. ascension offer actions
4. scalar and state actions
5. spawn and mutator actions
6. loot actions
7. invasion scheduling
8. notifications and debug output

Examples:
- boss kill event: trigger, unlock stage, grant ascension offer if eligible, apply reward or loot, schedule retaliation invasion, notify
- player enters Nether: trigger, unlock stage, optionally grant ascension offer, update future mutator eligibility for later spawns, notify

### Single Source of Truth Rules

To prevent split-brain behavior:
- loaded datapack definitions are the source of truth for content definitions
- TOML config is the source of truth for runtime permission gates
- `SavedData` is the source of truth for persistent stage and event state
- saved chosen ascension reward IDs are the source of truth for permanent ascension effects
- the live evaluation snapshot is the source of truth for a single event pass
- integration providers are the source of truth only for the data they explicitly expose

No subsystem may silently override another subsystem's truth domain outside the documented precedence rules.

---

## 9. Mob Mutator System

Mob mutations are named datapack-authored definitions composed from mutation components.

### 9.1 Goals

- spawn-time variant generation with controllable rarity
- stage/context-gated elite behavior
- configurable rewards and readable debug provenance

### 9.2 Mutation Definition Fields

- `id`
- `display_name`
- `enabled`
- `components[]`
- `rarity`
- `weight`
- `stacking_group`
- `exclusive_with[]`
- `max_stack_count`
- `eligible_entities[]`
- `eligible_entity_tags[]`
- `excluded_entities[]`
- `excluded_entity_tags[]`
- `required_conditions[]`
- `component_budget` (optional)
- `reward_modifier{}`
- `visuals{}`
- `sounds{}`
- `applies_to_bosses`
- `applies_to_invaders`

### 9.2A Identity Boundary

- mutation definition ID is the authored identity used in datapacks, pools, inspect output, and debug traces
- mutation component types are behavior primitives and are not the user-facing mutation identity
- built-in names such as Juggernaut or Summoner are bundled datapack presets, not privileged Java constants

### 9.3 Application Contexts

Supported:
- `on_spawn`
- `on_invasion_spawn`
- `on_special_event_spawn`

Default:
- `on_spawn`

### 9.4 v1 Mutation Component Types

Examples:
- `max_health_bonus`
- `max_health_multiplier`
- `attack_damage_bonus`
- `attack_damage_multiplier`
- `armor_bonus`
- `armor_multiplier`
- `armor_toughness_bonus`
- `movement_speed_bonus`
- `follow_range_bonus`
- `knockback_resistance_bonus`
- `wall_sense`
- `target_range_bonus`
- `pursuit_speed_boost`
- `anti_kite_behavior`
- `debuff_resistance`
- `damage_type_resistance`
- `temporary_shield`
- `projectile_resistance`
- `on_hit_effect`
- `life_steal`
- `reinforcement_summon`
- `summon_cooldown`
- `summon_cap`
- `death_spawn`
- `burst_movement`
- `projectile_modifier`
- `fire_package`
- `frost_package`
- `lightning_package`
- `poison_package`
- `damage_aura`
- `death_explosion`
- `retaliation_thorns`
- `glow_style`
- `ambient_particles`

### 9.5 Stat Domains

- max health
- attack damage
- armor
- armor toughness
- movement speed
- follow range
- knockback resistance
- reinforcement chance
- loot bonus chance
- XP bonus

### 9.6 Behavior Domains

- on-hit effect package
- on-death effect package
- reinforcement summon pulse
- target switching rule
- temporary shield state
- cooldown burst action
- projectile modifier
- elemental status package

### 9.7 Spawn-Time Selection Pipeline

At eligible spawn:
1. build effective stage context
2. collect external scalars (including Apotheosis if active)
3. gather matching mutation pools
4. filter by entity eligibility and exclusion
5. filter by config gates and boss restrictions
6. roll weighted mutator candidates
7. enforce stack/exclusive rules and component-composition validation outcomes
8. apply mutators and stat deltas
9. write persistent metadata for inspect/debug

Selection constraints:
- spawn-time evaluation must run in bounded passes
- mutator rerolls must respect a global reroll limit
- mutator selection must never loop indefinitely
- spawn-time results must be deterministic for a given spawn event context

### 9.8 Mutator Budget System (Optional Guard)

In addition to `max_mutators_per_entity`, World Awakened may enforce component budgets to prevent excessive mutation stacking.

Purpose:
- prevent extreme mob power escalation caused by overlapping mutation pools
- provide pack authors with fine-grained control over mutator complexity
- avoid accidental overstacked elite mobs from aggressive datapack combinations

Optional schema additions:

Mutation definition:
- `component_budget` (integer, optional per-definition cap)

Mutation component type registration:
- each component type may declare an intrinsic budget cost

Selection behavior:
1. initialize entity mutation budget
2. validate selected mutation component composition budget cost
3. reject definitions whose component cost exceeds applicable budget
4. selection ends when budget reaches zero or the candidate pool is exhausted

Example:
- `component_budget = 3`
- `reinforcement_summon` cost = 3
- `summon_cooldown` cost = 1

Outcome:
- definition with `reinforcement_summon + summon_cooldown` is rejected under budget 3

Design rule:
- budget enforcement must remain deterministic and bounded
- if budget is not configured, normal `max_mutators_per_entity` and composition validity rules apply

---

## 10. Mutation Pools

Mutation pools are context-gated groups used for mutator rolling.

Fields:
- `id`
- `enabled`
- `weight`
- `conditions[]`
- `stage_filters`
- `apotheosis_tier_filters`
- `eligible_dimensions[]`
- `eligible_biomes[]`
- `eligible_entities[]`
- `mutators[]`
- `max_mutators_per_entity` (optional override)
- `reroll_policy`

Use cases:
- `undead_tier_1`
- `overworld_night_stage_2`
- `apotheosis_high_tier_elites`
- `endgame_invaders`

---

## 11. Spawn Pressure System

Purpose:
Increase world threat level without replacing vanilla spawning pipeline.

### 11.1 Adjustable Parameters

- spawn pack size multiplier
- reinforcement chance
- elite/mutator chance
- invasion event frequency
- dimension hostile pressure scalar
- conservative category cap multipliers
- special event spawn composition

When configured, spawn pressure adjustments may also indirectly increase elite presence through higher mutator-pool eligibility or higher mutator selection chance.

### 11.1A Dimension Pressure Baselines

World Awakened may optionally define baseline spawn pressure per dimension.

Purpose:
- provide simple dimension-level difficulty tuning without requiring rule objects
- allow pack authors to scale hostile pressure naturally across world layers

Optional configuration example:
- `minecraft:overworld = 1.0`
- `minecraft:nether = 1.25`
- `minecraft:end = 1.50`

Behavior rules:
- this baseline multiplies the base spawn pressure values
- stage scaling and other scalars apply after the dimension baseline
- dimension baselines are applied only to World Awakened-owned spawn pressure calculations

Scalar order example:
- `effective_spawn_pressure = base_pressure * dimension_pressure_baseline * global_difficulty_modifier * challenge_modifier * integration_scalars`

If not configured:
- dimension baseline defaults to `1.0`

### 11.2 Guardrails

- config hard caps for all multipliers
- obey peaceful mode
- obey vanilla or NeoForge category restrictions when exposed by the hook; otherwise fail closed and do not apply the modifier
- bounded rerolls only
- fallback behavior when no pool/profile matches

### 11.3 Optional Apotheosis Inputs

- elite spawn chance scalar
- mutator chance scalar
- invasion wave budget scalar
- reward quality scalar

### 11.4 Scalar Composition Contract

For World Awakened-owned numeric difficulty outputs, scalar composition should be deterministic and centralized.

Recommended conceptual formula:
- `effective_value = base_wa_value * global_difficulty_modifier * challenge_modifier * integration_scalars`

Rules:
- global difficulty modifier is world-scoped baseline
- challenge modifier is optional and resolved by scope/policy
- stage progression still controls unlock-state and eligibility, not these scalar layers
- subsystems should read a shared resolved scalar provider rather than reimplementing scalar composition independently

---

## 12. Loot Evolution System

Purpose:
Progress exploration and combat rewards alongside stage context.

### 12.1 Supported Targets

- chest/structure loot tables
- invasion rewards
- mutated mob bonus drops
- boss bonus rolls
- stage unlock reward drops

### 12.2 Loot Profile Schema

- `id`
- `enabled`
- `target_loot_tables[]`
- `conditions[]`
- `apotheosis_tier_filters`
- `stage_filters`
- `replace_mode: inject | replace_entries | remove_entries | add_bonus_pool`
- `entries[]`
- `weight_multiplier`
- `quality_scalar`
- `config_gate`
- `mod_conditions`

### 12.3 Design Rule

Prefer loot table injection/modifier hooks over building an isolated loot engine.

### 12.4 Apotheosis Composition Guardrails

When Apotheosis compat is active, loot composition must follow Section 6.6.

Guardrails:
- treat Apotheosis-owned tier-gated loot behavior as authoritative
- compose World Awakened loot effects additively on Apotheosis-sensitive targets
- allow destructive loot modes only when explicitly verified compat-safe in a documented future exception
- enforce explicit fallback behavior (block, downgrade, or disable) with structured diagnostics when a profile is unsafe

---

## 13. Invasion Event System

Separate from vanilla raids.

### 13.1 Event Goals

- provide configurable high-threat world events
- integrate with stage and optional external scalars
- surface warnings and clear reward loop

### 13.2 Invasion Profile Schema

- `id`
- `display_name`
- `enabled`
- `trigger_mode`
- `conditions[]`
- `stage_filters`
- `apotheosis_tier_filters`
- `dimensions[]`
- `biome_filters[]`
- `min_players`
- `cooldown`
- `warning_time`
- `wave_count`
- `wave_interval`
- `spawn_budget`
- `spawn_composition[]`
- `elite_chance`
- `mutator_pool_refs[]`
- `reward_profile`
- `boss_wave` (optional)
- `max_active_entities`
- `safe_zone_rules`

### 13.3 Trigger Modes

- `random_periodic`
- `stage_unlock_reaction`
- `boss_retaliation`
- `structure_proximity`
- `command_forced`
- `time_based`
- `apotheosis_tier_threshold`

### 13.4 Reward Strategy

- profile-driven reward generation
- scale by stage and/or Apotheosis tier
- optional guaranteed chase item chance

---

## 14. Compatibility Framework

### 14.1 Goals

- detect supported mods automatically
- expose per-integration config toggles
- allow datapack conditional integration data
- keep integration behavior explicit and inspectable
- allow most boss/mob mods to function without dedicated compat code

### 14.2 Integration Profile Schema

- `mod_id`
- `display_name`
- `enabled_by_default`
- `config_key`
- `stage_hooks[]`
- `trigger_hooks[]`
- `entity_tags` / `boss_tags`
- `loot_targets`
- `special_conditions`
- `notes`

For `mod_id = apotheosis`, `loot_targets` may be used as compat metadata to identify Apotheosis-sensitive loot targets for the enforcement rules in Section 6.6.

### 14.3 Generic Boss and Mob Support Without Dedicated Compat

The core framework must support modded entities generically through datapack-driven classification and targeting.

Generic support includes:
- rules targeting modded entity IDs directly
- rules targeting modded entity tags
- boss kill triggers based on configured IDs/tags/maps
- mutator eligibility for modded entities via IDs/tags
- invasion spawn composition using modded entity IDs
- loot and reward rules conditioned on modded entity context where supported

This means many content mods that add mobs or bosses can be supported by pack authors without any Java compat module.

### 14.4 When Dedicated Compat Is Needed

Dedicated integration should be added later only if a mod exposes important behavior that cannot be expressed through generic entity targeting alone.

Typical reasons for deep compat:
- custom boss classification API
- custom progression/tier systems
- custom events not visible through standard entity or death hooks
- special loot/reward systems worth reading directly
- unusual spawn pipelines that bypass normal hooks

Deep compat modules remain optional and should live alongside the generic system, not replace it.

### 14.5 Runtime Enable Rule

Integration active only if:
1. mod is loaded
2. integration config toggle is enabled
3. integration profile/object is enabled

### 14.6 Apotheosis Profile

Dedicated profile requirements:
- `mod_id = apotheosis`
- world-tier condition provider
- world-tier trigger provider
- optional scalar provider for loot/mutators/invasions
- compat-safe identification of Apotheosis-sensitive loot targets when metadata is available

---

## 15. Configuration Contract (TOML)

Baseline config groups (v1):

### 15.1 General
```toml
[general]
enable_mod = true
debug_logging = false
validation_logging = true
```

### 15.2 Progression
```toml
[progression]
mode = "global"
announce_stage_unlocks = true
allow_stage_regression = false
allow_hidden_stages_in_debug = true
```

### 15.3 Mutators
```toml
[mutators]
enable_mutators = true
max_mutators_per_mob = 2
respect_boss_blacklist = true
apply_on_spawn_only = true
```

### 15.4 Spawning
```toml
[spawning]
enable_spawn_scaling = true
allow_pack_size_adjustments = true
allow_special_reinforcements = true
natural_spawn_scaling_cap = 2.0
```

### 15.5 Loot
```toml
[loot]
enable_loot_evolution = true
inject_only = true
allow_entry_replacement = false
```

### 15.6 Invasions
```toml
[invasions]
enable_invasions = true
global_cooldown_minutes = 90
warning_seconds = 20
max_concurrent_invasions = 1
```

### 15.6A Ascension
```toml
[ascension]
enable_ascension = true
one_pending_offer_per_player = true
remind_pending_offers = true
reminder_minutes = 30
allow_admin_revoke = true
```

### 15.6B Global Difficulty Modifier
Detailed behavior contract: Section 5B.1.

Recommended configuration shape:
```toml
[difficulty.global]
enabled = true
value = 1.0
min_value = 0.75
max_value = 1.50
```

### 15.6C Optional Challenge Modifier System
Detailed behavior contract: Sections 5B.2 through 5B.13.

Recommended configuration shape:
```toml
[difficulty.challenge]
enabled = true
scope_mode = "auto" # auto | player | world
allow_player_adjustment = true
allow_raise = true
allow_lower = true
default_value = 1.0
min_value = 0.75
max_value = 1.50
step = 0.10
cooldown_minutes = 120
max_changes_per_player = 5
max_world_changes = 10
require_vote_in_global = true
vote_threshold = 0.60
vote_timeout_seconds = 120
admin_override = true
```

### 15.7 Compatibility
```toml
[compat]
auto_detect = true
default_enable_detected_integrations = true

[compat.apotheosis]
enabled = true
mode = "hybrid" # independent | derived_stage | scalar | hybrid
allow_world_tier_conditions = true
allow_world_tier_stage_unlocks = true
allow_world_tier_loot_scaling = true
loot_unsafe_mode_policy = "block" # block | downgrade_additive | disable_profile_branch
allow_world_tier_invasion_scaling = true
allow_world_tier_mutator_scaling = true
```

### 15.8 Client
```toml
[client]
show_affix_names = true
show_stage_toasts = true
show_invasion_warning_overlay = true
show_ascension_notifications = true
```

---

## 16. Datapack Object Sets

Required custom object types:
- `stages`
- `trigger_rules`
- `rules`
- `ascension_rewards`
- `ascension_offers`
- `mob_mutators`
- `mutation_pools`
- `loot_profiles`
- `invasion_profiles`
- `integration_profiles`

Path convention:
- datapack objects load from `data/<namespace>/<object_type>/*.json`
- example: `data/worldawakened/ascension_rewards/ember_blood.json` -> `worldawakened:ember_blood`
- do not add an extra `worldawakened/` folder layer under the namespace folder

Optional mapping datasets:
- entity type to boss flags
- entity type to default mutation eligibility
- item to icon/loot metadata
- structure/loot table to category hints
- optional later ascension reward pool mappings

Design note:
- `entity type to boss flags` exists specifically so pack authors can classify future modded bosses without waiting for dedicated compat code.

Implementation note:
- object load should support datapack reload
- object disable should be granular

Detailed authoring contract:
- `docs/DATAPACK_AUTHORING.md`

### 16.1 Datapack Schema Versioning

All World Awakened datapack objects may optionally include:
- `schema_version: integer`

Behavior:
- missing version defaults to schema version `1`
- newer schema handlers should preserve compatibility with earlier supported schema versions unless the newer handler explicitly declares that version incompatible and raises a validation error
- incompatible schema versions must raise validation errors
- schema validation errors disable the offending object and must not crash the mod by themselves

---

## 17. JSON Shape Requirements

### 17.0 Shared Object Field

All World Awakened object types may optionally include:
- `schema_version`

### 17.1 `stage.json`
Required support:
- `id`
- `aliases`
- `display_name`
- `description`
- `icon`
- `sort_index`
- `visible_to_players`
- `tags`
- `default_unlocked`
- `progression_group`
- `unlock_policy`

### 17.2 `mutator.json`
Required support:
- `id`
- `display_name`
- `components[]`
- `enabled`
- `weight`
- `eligibility`
- `conditions`
- component composition options (`enabled`, `priority`, `parameters`, `conditions`, `exclusions`, `conflicts_with`)
- stacking rules
- visuals

### 17.3 `mutation_pool.json`
Required support:
- `id`
- `enabled`
- `conditions`
- mutator refs
- weights
- stage filters
- Apotheosis tier filters

### 17.4 `trigger_rule.json`
Required support:
- `id`
- `trigger_type`
- `conditions`
- `actions`
- `cooldown`
- `one_shot`

### 17.5 `invasion_profile.json`
Required support:
- `id`
- `display_name`
- trigger mode
- conditions
- stage filters
- Apotheosis tier filters
- waves
- composition
- rewards
- cooldowns

### 17.6 `loot_profile.json`
Required support:
- `id`
- target tables
- entries
- replace mode
- conditions
- stage filters
- Apotheosis tier filters

### 17.7 `ascension_reward.json`
Required support:
- `id`
- `display_name`
- `description`
- `icon`
- `components[]`
- `requires_conditions`
- `forbidden_conditions`
- `unique_group`

### 17.8 `ascension_offer.json`
Required support:
- `id`
- `display_name`
- `trigger_conditions`
- `stage_filters`
- `choice_count`
- `selection_count`
- candidate reward refs or tags
- `offer_mode`

---

## 18. Debug and Command Surface

Required commands (v1):
- `/wa stage list`
- `/wa stage unlock <id>`
- `/wa stage lock <id>`
- `/wa trigger fire <id>`
- `/wa invasion start <profile>`
- `/wa invasion stop`
- `/wa mob inspect`
- `/wa compat list`
- `/wa apotheosis tier inspect`
- `/wa ascension list <player>`
- `/wa ascension pending <player>`
- `/wa ascension open <player>`
- `/wa ascension grant_offer <player> <offer_id>`
- `/wa ascension choose <player> <offer_id> <reward_id>`
- `/wa ascension revoke <player> <reward_id>`
- `/wa ascension inspect <player>`
- `/wa dump active_rules`
- `/wa reload validate`

Optional difficulty commands (when difficulty/challenge subsystems are enabled):
- `/wa difficulty global get`
- `/wa difficulty global set <value>`
- `/wa difficulty global reset`
- `/wa difficulty personal get`
- `/wa difficulty personal raise`
- `/wa difficulty personal lower`
- `/wa difficulty personal set <value>`
- `/wa difficulty world get`
- `/wa difficulty world raise`
- `/wa difficulty world lower`
- `/wa difficulty world set <value>`
- `/wa difficulty vote yes`
- `/wa difficulty vote no`

### 18.1 Command Permission Model

Command permissions must be explicit.

Player-safe self-service commands:
- `/wa ascension open` for the executing player
- `/wa ascension pending` for the executing player
- `/wa ascension list` for the executing player
- `/wa ascension inspect` for the executing player when server policy allows self-inspection
- optional `/wa difficulty personal *` commands when player adjustment is enabled by server policy
- optional `/wa difficulty vote yes|no` when a world-scope vote is active and voting is enabled

Operator or admin commands:
- all stage mutation commands
- all manual trigger and invasion commands
- `/wa dump active_rules`
- `/wa reload validate`
- `/wa mob inspect`
- `/wa compat list`
- `/wa apotheosis tier inspect`
- any ascension command targeting another player
- `/wa ascension grant_offer`
- `/wa ascension choose`
- `/wa ascension revoke`
- all `/wa difficulty global *` commands
- all `/wa difficulty world *` commands unless vote-based policy explicitly allows non-operator initiation
- operator override paths for difficulty vote flows

Console behavior:
- dedicated-server console has operator-equivalent authority
- console execution must bypass player-only targeting restrictions when a valid target is supplied
- console may bypass difficulty vote gates only when `admin_override` policy allows it

`mob inspect` output must include:
- entity type
- active mutators
- source mutation pool
- source rules
- stage context
- Apotheosis tier context (when active)
- final attribute deltas

`ascension inspect` output must include:
- pending offers
- resolved offers
- chosen rewards
- forfeited rewards
- active permanent effects
- source stages or tiers for each reward

---

## 19. Validation, Performance, and Observability

### 19.1 Validation Rules

On datapack reload, validate:
- duplicate IDs
- duplicate ascension reward IDs
- duplicate ascension offer IDs
- missing stage refs
- missing mutator refs
- missing ascension reward refs in offers
- invalid condition/action types
- invalid config gates
- impossible pool selections
- `choice_count < 1`
- `selection_count != 1` in v1
- no valid candidate rewards after filtering
- empty mutation `components[]`
- empty ascension reward `components[]`
- unknown mutation component types
- unknown ascension component types
- incompatible component combinations
- invalid component parameters
- impossible component compositions
- component compositions with no valid runtime result
- over-budget mutation compositions when budget rules apply
- unsupported duplicate component combinations when duplicates are disallowed
- invalid icon references for ascension rewards or offers
- conflicting exclusive-stage definitions
- Apotheosis-only conditions while integration disabled
- unsafe replacement/removal behavior against Apotheosis-sensitive loot targets when Apotheosis compat is active
- incompatible loot profile modes for Apotheosis-sensitive targets
- attempted destructive overrides of Apotheosis-owned tier-gated loot paths
- unsupported entity IDs/tags
- invasion profiles with no valid spawn entries
- unsupported or incompatible schema versions
- invalid global difficulty config bounds/defaults
- invalid challenge scope mode or unsupported scope-mode combination
- invalid challenge bounds/defaults/step/cooldown
- invalid challenge vote configuration when vote requirement is enabled
- activation-path settings that conflict with disabled difficulty/challenge subsystems

Validation policy:
- log clear object-scoped errors
- disable the broken object and any directly dependent objects, not unrelated World Awakened systems
- avoid whole-mod crash unless startup integrity is impossible
- never silently substitute a different ascension reward or offer without logging
- never silently destroy Apotheosis tier-gated loot behavior; enforce explicit fallback behavior when unsafe overrides are encountered
- for unsafe Apotheosis-sensitive loot operations, the policy outcome must be explicit: block operation, downgrade to safe additive behavior, or disable the offending profile branch
- surface summary through debug command

### 19.1A Canonical Error Code Naming Guidance

Structured diagnostics should use stable uppercase snake-case error codes in this form:
- `WA_<DOMAIN>_<DETAIL>`

Guidelines:
- `DOMAIN` should identify the subsystem, such as `STAGE`, `SCHEMA`, `ASCENSION`, `RULE`, `PRESSURE_TIER`, `NETWORK`, or `INTEGRATION`
- `DETAIL` should describe the failure condition precisely
- published error codes should remain stable once used in logs, tests, or tools

Examples:
- `WA_STAGE_REF_MISSING`
- `WA_SCHEMA_UNSUPPORTED`
- `WA_ASCENSION_REWARD_INVALID`
- `WA_RULE_RECURSION_BLOCKED`
- `WA_PRESSURE_TIER_PROVIDER_INVALID`
- `WA_DIFFICULTY_GLOBAL_INVALID`
- `WA_CHALLENGE_SCOPE_INVALID`
- `WA_CHALLENGE_BOUNDS_INVALID`
- `WA_CHALLENGE_STEP_INVALID`
- `WA_CHALLENGE_VOTE_CONFIG_INVALID`
- `WA_CHALLENGE_MODE_UNSUPPORTED`
- `WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED`
- `WA_APOTHEOSIS_LOOT_MODE_UNSAFE`
- `WA_APOTHEOSIS_LOOT_TARGET_SENSITIVE`
- `WA_COMPONENT_TYPE_UNKNOWN`
- `WA_COMPONENT_COMPOSITION_INVALID`

### 19.2 Performance Constraints

- datapack objects must compile into cached runtime structures during reload
- no raw JSON parsing may occur during spawn-time or event-time evaluation
- rule evaluation must operate on compiled condition trees or equivalent cached evaluators
- selector matching must use precompiled matchers
- spawn-time evaluation must run in bounded passes

### 19.3 Safety Guards

- global reroll limits must exist for mutator selection
- invasion spawn budgets must enforce hard caps
- spawn pressure scalars must obey config hard limits
- rule recursion must be prevented
- repeated evaluation within the same tick should not reroll the same context outcome

### 19.4 Observability Guarantees

Debug mode must provide structured insight into evaluation.

The system must be able to answer:
- which rules matched
- which rules failed and why
- which ascension offer triggered and why
- why a reward was eligible or ineligible
- which mutator pool was selected
- why a mutator was rejected
- which stage conditions were active
- which integration gates were applied
- which effective difficulty scalar was applied
- why difficulty/challenge adjustment requests were rejected (bounds, cooldown, permissions, scope, vote)

Minimum command support for observability:
- `/wa mob inspect`
- `/wa dump active_rules`
- `/wa reload validate`

Example spawn debug output:
```text
Entity: minecraft:zombie
Stage Context: worldawakened:nether_opened
Eligible Pools: undead_tier_1, overworld_night_stage_2
Selected Pool: undead_tier_1
Applied Mutation: my_pack:summoner_juggernaut
Components: reinforcement_summon, summon_cooldown, max_health_multiplier, armor_bonus
Rejected Mutation: my_pack:glass_cannon (component conflict)
```

### 19.5 Data Migration and Save Compatibility

World saves must remain stable when datapacks change.

Migration rules:
- removed stages remain recorded but marked inactive
- renamed stages should support alias mapping through stage aliases
- removed mutators on existing mobs remain until the entity despawns or is otherwise rebuilt
- removed invasion profiles must not invalidate already active invasions
- removed chosen ascension rewards remain recorded in save data, are not automatically substituted, and stop applying live effects once reconciliation detects the missing definition
- persisted difficulty/challenge modifier values that fall outside new config bounds should be clamped on load with structured warnings

Authoring-model migration rule:
- old assumptions that preset names (for example Juggernaut or Summoner) are privileged engine mutation types are invalid
- old assumptions that ascension `effect_type` names are final authored identities are invalid
- canonical authored identities are mutation/reward definition IDs backed by component compositions

Migration behavior must prefer graceful degradation over crashes.

### 19.6 Randomness and Determinism

Random behavior includes:
- mutator selection
- invasion composition
- loot bonus rolls

Requirements:
- randomness must use server-side seeded RNG or event-scoped derived seeds for authoritative outcomes
- repeated evaluation within the same tick should not reroll
- spawn-time results must be deterministic for a given spawn event
- multiplayer evaluation must not diverge between equally scoped observers because of client-side randomness

---

## 20. Networking and Client Sync

v1 client sync is intentionally minimal.

Must sync:
- stage unlock announcements
- invasion warning notifications
- ascension offer notifications and GUI-open data
- ascension selection request and resolved-state sync
- optional mutator nameplate/tooltip markers
- optional operator debug overlay data

Do not sync:
- full rule graph
- complete server evaluation internals

### 20.1 Packet Trust and Networking Authority Rules

Networking trust boundaries must remain explicit.

Clients may:
- request opening the ascension GUI
- request selecting an ascension reward from a pending offer
- request optional client-visible debug or inspect views only when permitted
- request challenge-modifier adjustments only through allowed server policy paths

The server is the sole authority for:
- pending offer existence
- runtime offer instance identity
- reward eligibility
- forfeiture and lockout state
- final reward application
- stage state
- invasion state
- any persistent progression or ascension mutations
- global difficulty and challenge modifier mutations

Rules:
- invalid, stale, or forged client packets must be ignored
- invalid or stale packets should emit debug-mode diagnostics without crashing the server
- no client packet may directly grant an offer, unlock a stage, or apply a permanent reward without server-side validation
- no client packet may bypass challenge/difficulty permissions, bounds, cooldowns, scope, or vote requirements

---

## 20A. Testing Strategy

Implementation should be backed by a small set of stable test categories.

Minimum categories:
- codec and datapack loading validation tests
- stage persistence save and load tests
- parallel stage-group independence and cross-group condition-reference tests
- deterministic reroll-prevention tests
- networking packet validation and stale-packet rejection tests
- stage alias and migration compatibility tests
- pressure-tier provider and condition-evaluation tests
- single-pass stage propagation guarantee tests (no same-event re-evaluation after unlock)
- global difficulty modifier bound and reset tests
- challenge modifier scope-resolution and policy-validation tests
- challenge modifier cooldown/usage-limit and vote-flow tests
- dimension pressure baseline composition and default-fallback tests
- optional world-context condition tests, including false-on-unavailable-context behavior
- mutation component budget enforcement tests (`component_budget` plus registered component costs) when enabled
- Apotheosis loot compatibility tests for additive composition and unsafe-mode fallback behavior
- debug trace ID stability tests per evaluation pass

Recommended rule:
- each implemented phase should add or update automated coverage in at least one relevant test category
- runtime-determinism and migration regressions should be treated as high-priority failures

---

## 21. MVP Implementation Order

### Phase 0 (Complete) - Contracts and Loading Foundation
- finalize JSON schema contracts and codecs for all World Awakened object sets
- establish datapack load/reload pipeline for World Awakened content
- implement baseline validator framework and structured validation summary
- create config categories and runtime feature gates
- establish debug logging categories and diagnostics format
- establish the initial automated test harness for codec, validation, persistence, and deterministic-runtime coverage
- define shared entity selector and boss-classification contracts for generic modded mob support
- define conflict-resolution, recursion-prevention, and schema-version semantics before downstream systems are built
- define and validate payload contracts for optional world-context rule conditions (`world_day_gte`, `player_distance_from_spawn`)
- define immutable evaluation snapshot model, runtime layer boundaries, and canonical event pipeline

Exit criteria:
- `/wa reload validate` returns a structured summary
- malformed objects are disabled without crashing the mod
- all required object folders can be discovered and parsed
- hot-path evaluators operate on compiled runtime structures, not raw JSON
- optional world-context condition payloads validate with actionable diagnostics
- runtime traces can identify the active evaluation layer and rejection reason category

### Phase 1 (Complete) - Stage Core and Persistence
- implement stage registry/cache and ID resolution
- implement world `SavedData` progression state store
- scaffold per-player progression state store (even if partial behavior in v1)
- implement stage API contract (`unlock`, `lock`, queries, group resolution)
- implement command baseline for stage operations
- support inactive legacy stages and alias-based resolution for renamed stages

Exit criteria:
- stage unlock/lock persists across world restart
- stage IDs are authoritative in logic path
- `/wa stage list`, `/wa stage unlock`, `/wa stage lock` function end-to-end
- progression mode transitions do not corrupt existing save data

### Phase 2 (Complete) - Trigger Engine
- implement trigger adapters for v1 event inputs
- load and evaluate `trigger_rules` with scope, cooldown, and one-shot semantics
- wire trigger actions to stage unlocks/event emissions/counters
- implement manual trigger test path
- implement boss-kill detection against entity IDs, tags, and boss-flag data maps
- enforce snapshot immutability during trigger evaluation

Exit criteria:
- dimension entry, advancement, and boss-kill triggers operate correctly
- trigger cooldown and one-shot behavior is enforced
- `/wa trigger fire <id>` can exercise trigger flow for debugging

### Phase 3 (Complete) - Rule Engine Core
- implement generic condition/action evaluation framework
- support execution scopes: `world`, `player`, `entity`, `spawn_event`
- implement optional world-context condition evaluators (`world_day_gte`, `player_distance_from_spawn`) for rules
- implement deterministic priority + chance + cooldown handling
- enforce single-pass stage propagation guarantee for v1 event passes
- add active-rule introspection support
- emit stable per-pass debug trace IDs when debug tracing is enabled
- enforce conflict-resolution and recursion-prevention contracts

Exit criteria:
- rules can be evaluated independently of mutators/loot/invasions
- invalid condition/action types are rejected with clear diagnostics
- world-context rule conditions evaluate deterministically and fail closed when context is unavailable
- stage unlocks triggered during an event pass do not re-evaluate that same event pass
- `/wa dump active_rules` reports usable rule state
- same-context re-evaluation within a tick does not reroll outcomes

### Phase 4 - Ascension Choice System
- implement player-scoped ascension offer state and persistence
- implement ascension component-type registry plus reward/offer definition loading
- implement clickable chat notification flow and server-authoritative selection
- implement minimal GUI, packet, and confirmation flow
- implement one-pending-offer queue semantics for v1
- implement passive permanent reward reconciliation on login and respawn
- implement ascension command and inspect surface

Exit criteria:
- eligible stage or rule events can grant pending offers
- players can choose exactly one reward from an offer
- forfeited rewards remain locked out for that offer
- client selection requests are validated server-side and cannot bypass lockout or eligibility rules
- repeated login or respawn does not duplicate-stack rewards

### Phase 5 - Mutators and Pools
- implement mutation component-type registry and v1 behavior set
- implement mutation pool matching and weighted selection
- apply mutators at spawn-time with stacking/exclusivity enforcement
- implement optional mutation component budget enforcement (`component_budget` plus registered component costs)
- persist entity metadata for provenance and debugging
- compile selector definitions into cached matchers

Exit criteria:
- eligible spawns can receive mutators from matching pools
- configured caps and exclusions are respected
- when configured, mutator budget enforcement rejects over-budget candidates deterministically
- `/wa mob inspect` shows source pool, active mutators, and stat deltas
- mutator rerolls remain bounded and rejected candidates are inspectable

### Phase 6 - Spawn Pressure Controls
- implement conservative spawn pressure scalars and category modifiers
- implement optional per-dimension pressure baselines for spawn pressure
- implement shared effective-difficulty scalar provider for global and optional challenge modifiers
- implement global difficulty modifier config and command surface
- implement optional challenge modifier scope/policy/bounds/cooldown handling (including optional vote flow)
- implement hard safety caps and loop guards
- support dimension and stage-conditioned pressure adjustments

Exit criteria:
- pressure adjustments remain bounded by server config caps
- missing dimension baseline entries default to `1.0`
- dimension baseline composition order is deterministic and precedes global/challenge/integration scalar layers
- effective scalar composition is deterministic and consistently applied across World Awakened-owned numeric difficulty outputs
- out-of-bounds or unauthorized difficulty/challenge changes are rejected with diagnostics
- no uncontrolled spawn loops under malformed data
- peaceful mode is respected and category restrictions are obeyed when exposed by the hook; otherwise modifiers fail closed

### Phase 7 - Loot Evolution
- implement `loot_profiles` loading and condition matching
- integrate profile actions into loot table modification hooks
- implement mutated mob bonus drop path and profile-based scaling
- apply replace, remove, and inject semantics in the documented assembly order
- enforce additive composition rules for Apotheosis-sensitive targets when compat is active

Exit criteria:
- targeted chest tables receive profile-driven changes as configured
- replace/inject modes obey config restrictions
- unsafe loot modes against Apotheosis-sensitive targets are blocked, downgraded, or disabled with structured diagnostics
- broken loot profiles are isolated and logged without global failure

### Phase 8 - Invasion System (One Mode MVP)
- implement invasion scheduler and active invasion state tracking
- ship at least one robust trigger mode (`random_periodic` + `command_forced`)
- implement wave spawning, warning window, and reward profile output
- separate scheduler phase from wave spawn phase and guard both against stuck-active failures

Exit criteria:
- `/wa invasion start <profile>` and `/wa invasion stop` operate reliably
- warning and wave cadence follow profile configuration
- invasion entity cap and safe-zone guards are enforced

### Phase 9 - Compatibility Framework and Apotheosis
- implement generic integration activation pipeline (`mod loaded` + config + profile)
- implement integration profiles and compatibility registry reporting
- implement Apotheosis world tier provider (conditions, triggers, scalar inputs)
- implement Apotheosis loot compatibility detection for sensitive targets and enforce compose-not-override behavior
- support ascension offer triggers from compatible external tier providers where configured
- support mapping modes (`independent`, `derived_stage`, `scalar`, `hybrid`)
- keep dedicated boss/mob mod compat additive; do not move generic entity-based support behind compat modules

Exit criteria:
- no hard dependency crash when Apotheosis is missing
- Apotheosis-specific objects are safely skipped or evaluate false when inactive
- Apotheosis-managed tier-gated loot behavior is preserved on sensitive targets while World Awakened additive rewards still apply
- external tier integrations can feed rule and ascension eligibility safely when enabled
- `/wa compat list` and `/wa apotheosis tier inspect` are functional

### Phase 10 - Hardening, Examples, and Release Prep
- complete validation rule coverage and improve error quality
- add reference example datapack set for all major object types
- complete docs pass across spec/readme/authoring/agents
- perform performance sanity checks for reload and spawn-path logic
- verify save compatibility behavior across datapack changes and schema-version handling

Exit criteria:
- validation output is actionable for pack authors
- example datapack loads cleanly and demonstrates end-to-end flow
- docs are synchronized and reflect implemented behavior
- migration and downgrade behavior degrade safely without corrupting saves

### Cross-Phase Quality Gates
- each phase ends with successful compile/build and server startup smoke test
- each phase includes at least one command-driven verification path
- each phase adds or updates automated coverage in at least one relevant test category when behavior changes
- each phase updates docs for any schema/behavior change
- debug-enabled evaluation paths should expose a stable per-pass trace ID for correlation across logs and command output
- later phases may add supporting scaffolding early, but a phase is not complete unless all earlier phase exit criteria still pass
- no phase may introduce unbounded loops or unsafe fallback behavior
- no hot-path phase may rely on raw JSON parsing at runtime

---

## 22. MVP Boundaries

Must-have:
- configurable stages with configurable names
- trigger-based stage unlocks
- ascension choice system with player-scoped permanent exclusive rewards
- mutator system
- loot evolution
- one invasion type
- generic modded boss/mob targeting through entity IDs, tags, and boss classification maps
- deterministic rule/conflict resolution and bounded spawn-time evaluation
- global difficulty modifier baseline control for World Awakened-owned numeric difficulty outputs
- optional challenge modifier system with bounded scope/policy controls
- datapack schema version handling with graceful validation failure
- compatibility toggles
- Apotheosis tier as conditions and scalar input
- Apotheosis loot compatibility that preserves Apotheosis-owned tier-gated behavior while allowing additive World Awakened loot extensions
- optional world-context conditions as datapack rule inputs (non-core progression drivers)
- debug commands
- datapack-driven rules

Deferred:
- full hybrid conflict resolver
- GUI rule editor
- large integration catalog
- custom boss bar visuals
- retroactive mutation of pre-existing entities
- advanced custom AI pathing editor

---

## 23. Recommended Package Layout

Use base package `net.sprocketgames.worldawakened`:

- `WorldAwakenedMod` (entrypoint)
- `api`
- `ascension`
- `config`
- `progression`
- `rules`
- `mutator`
- `spawning`
- `loot`
- `invasion`
- `compat`
- `compat.apotheosis`
- `data`
- `network`
- `command`
- `debug`
- `util`

---

## 24. Final Design Rule

Hard line:
- Datapacks choose **what applies**.
- Java code defines **how it behaves**.

Therefore:
- stage names configurable: yes
- stage IDs configurable: yes
- rule content configurable: yes
- mutation and ascension definition content configurable: yes
- built-in presets shipped as datapack compositions: yes
- ascension player choice permanent and exclusive: yes
- arbitrary new behavior type implementation purely in datapack: no

This boundary keeps the framework extensible without becoming unstable.

---

## 25. Implementation Status Note

Current status:
- Phase 0 is complete.
- Phase 1 is complete.
- Phase 2 is complete.
- Phase 3 is complete.

Phase 0 completion confirmation:
- docs in this file are treated as implementation contract
- README links this spec (done)
- AGENTS guidance points contributors to this spec (done)
- configuration and package targets align with `gradle.properties` (done)

Phase 1 completion confirmation:
- stage registry/cache and alias-based ID resolution are implemented
- world `SavedData` progression state persistence is implemented
- per-player progression scaffold `SavedData` is implemented
- stage API unlock/lock/query/group-resolution and effective-context methods are implemented
- `/wa stage list`, `/wa stage unlock`, and `/wa stage lock` are functional end-to-end

Phase 2 completion confirmation:
- trigger adapters are implemented for dimension entry, advancement completion, entity kills, boss kills, and manual debug fire paths
- trigger evaluation runs from immutable stage/cooldown/one-shot snapshots within a pass
- trigger scope-aware cooldown and one-shot state persistence is implemented for world and player contexts
- trigger actions for stage unlock/lock, named trigger event emission, and counter updates are wired
- `/wa trigger fire <id>` executes manual debug triggers end-to-end

Phase 3 completion confirmation:
- generic rule compilation and evaluation are implemented with execution scopes `world`, `player`, `entity`, and `spawn_event`
- deterministic evaluation ordering is implemented (`enabled/scope+conditions -> priority -> cooldown -> consumed -> chance`)
- optional world-context condition evaluation is implemented for `world_day_gte` and `player_distance_from_spawn`, with fail-closed behavior when context is unavailable
- runtime rule cooldown and consumed markers persist in world/player progression `SavedData`
- single-pass stage propagation is enforced by evaluating rules from pre-action stage snapshots captured at event-pass start
- `/wa dump active_rules` reports current eligibility, rejection reason, cooldown remaining, and consumed state
- debug-enabled rule passes emit stable per-pass trace identifiers
- completed Phase 1-3 contracts were audited to ensure mutation/reward references remain authored-ID driven and are not coupled to hardcoded preset behavior identities


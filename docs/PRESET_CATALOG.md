# World Awakened Preset Catalog

Reference catalog for higher-level authored presets and templates built from shared framework primitives.

- Document status: Active reference catalog
- Last updated: 2026-03-13
- Scope: Preset/template composition patterns and shipped/example status taxonomy

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever shipped presets, canonical template patterns, or composition guidance changes.
- Keep preset IDs, status tags, and template expectations aligned with datapack contracts and component/action/condition catalogs.

---

## 1. Purpose

World Awakened Preset Catalog.

Purpose:
- bridge low-level component/condition references to practical authored content patterns
- provide reusable patterns for pack authors and contributors
- define shipped/default vs example-only vs conceptual template status clearly
- supply a canonical source for future web authoring preset/template browsing

Primary rule:
- presets are authored compositions, not privileged code-only identities

## 2. Overview

A preset is a named higher-level content composition assembled from framework primitives:
- mutation components in `mob_mutators`
- ascension components in `ascension_rewards`
- shared conditions/actions in `rules`, `trigger_rules`, `mutation_pools`, `ascension_offers`, and future profile objects

Preset vs primitive component:
- primitive component: one behavior unit (for example `worldawakened:max_health_multiplier`)
- preset/template: an authored bundle of multiple primitives plus gating/selection context

Content source distinctions:
- shipped content: distributed in the optional external example pack at `example_datapacks/worldawakened_example_pack/data/worldawakened/...`
- example content: documented and recommended, but not distributed in the shipped external example pack
- conceptual templates: design patterns for bespoke packs or future example-pack sets

Why presets exist:
- faster authoring
- easier onboarding
- practical composition examples
- better future "build from template" UX in the web authoring tool

## 3. Status / Source Taxonomy

Every catalog entry must declare one source status:

| Status | Definition |
| --- | --- |
| `shipped` | Included in the external optional example pack under `example_datapacks/worldawakened_example_pack/data/worldawakened/`. |
| `example-only` | Documented as a concrete reference pattern but not included in the shipped external example pack. |
| `conceptual` | Design pattern template for bespoke/future content. |
| `deprecated` | Preserved compatibility/reference pattern; not recommended for new authoring. |

## 4. Preset / Template Categories

Catalog categories:
- A. Mutation presets
- B. Ascension reward presets
- C. Mutation pool templates
- D. Ascension offer templates
- E. Rule templates
- F. Invasion profile templates
- G. Starter pack templates

Compact index:

| Preset / Template | Category | Source Status | Role |
| --- | --- | --- | --- |
| `juggernaut` (`worldawakened:juggernaut_preset`) | Mutation | shipped | Durable slow frontline elite |
| `summoner` (`worldawakened:summoner_preset`) | Mutation | shipped | Reinforcement pressure |
| `hunter` (`worldawakened:hunter_preset`) | Mutation | shipped | Pursuit/tracking pressure |
| `predator` (`worldawakened:vampiric_predator`) | Mutation | shipped | Lifesteal aggression |
| `elemental_elite` family | Mutation | example-only | Thematic elemental variants |
| `thorned_defender` | Mutation | conceptual | Retaliation tank pattern |
| `unyielding_hunter` (mutation template) | Mutation | conceptual | Durable pursuit hybrid |
| `summoner_juggernaut` (`worldawakened:summoner_juggernaut`) | Mutation | shipped | Advanced leader hybrid |
| `titan_blood` | Ascension reward | conceptual | Permanent health/armor identity |
| `relentless` | Ascension reward | conceptual | Offensive sustain identity |
| `hunters_instinct` | Ascension reward | example-only | Tracking/crit hunter identity |
| `unyielding` | Ascension reward | conceptual | Mitigation/survival identity |
| `treasure_sense` | Ascension reward | example-only | Loot utility identity |
| `ember_blood` (`worldawakened:ember_blood`) | Ascension reward | shipped | Durable sustain hybrid |
| `predator_sense` | Ascension reward | example-only | Elite tracking/offense utility |
| `unyielding_will` | Ascension reward | conceptual | Late survivability anchor |
| `early_overworld_elite_pool` | Mutation pool | shipped | Early controlled elite intro |
| `nether_escalation_pool` | Mutation pool | shipped | Mid-game nether pressure |
| `tank_offer` | Ascension offer | shipped | Defensive reward offer path |
| `offensive_offer` | Ascension offer | shipped | Damage/sustain offer path |
| `explorer_offer` | Ascension offer | shipped | Mobility/utility offer path |
| `loot_focused_offer` | Ascension offer | example-only | Loot-progression path |
| `unlock_stage_on_dimension_entry` | Rule | conceptual | Progression gate unlock |
| `activate_mutation_pool_by_progression_stage` | Rule | conceptual | Stage-driven mutator injection |
| `schedule_invasion_on_milestone` | Rule | conceptual | Invasion orchestration |
| `milestone_retaliation` | Invasion profile | conceptual | Triggered retaliation event |
| `vanilla_plus_progression_pack` | Starter pack | example-only | Minimal-risk starter bundle |

## 5. Per-Entry Template

Each entry follows this format:
- `Category`
- `Source Status`
- `Design Intent`
- `Composition Summary`
- `Key Components`
- `Key Conditions`
- `Related Objects`
- `Example Use Case`
- `Notes`

`Related Objects` may include supporting rules, pools, offers, triggers, profiles, and file references.

## 6. Mutation Preset Coverage (Category A)

### `juggernaut` (`worldawakened:juggernaut_preset`)
- **Category:** Mutation preset
- **Source Status:** `shipped`
- **Design Intent:** Heavy durability + anti-knockback + reduced mobility to create a slow high-threat frontline elite.
- **Composition Summary:** High survivability (`max_health_multiplier`, `armor_bonus`, `knockback_resistance_bonus`) offset by mild speed penalty.
- **Key Components:** `worldawakened:max_health_multiplier`, `worldawakened:armor_bonus`, `worldawakened:knockback_resistance_bonus`, `worldawakened:movement_speed_bonus` (negative value).
- **Key Conditions:** No mutator-local `required_conditions`; typically gated by pool/rule context.
- **Related Objects:** `worldawakened:end_elite_pool`.
- **Example Use Case:** Endgame melee elites that are hard to displace and survive long enough to force positioning decisions.
- **Notes:** Counterplay: kiting, ranged burst, terrain abuse. Authoring level: beginner-friendly. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/mob_mutators/juggernaut_preset.json`.

### `summoner` (`worldawakened:summoner_preset`)
- **Category:** Mutation preset
- **Source Status:** `shipped`
- **Design Intent:** Reinforcement pressure and encounter pacing control through bounded adds.
- **Composition Summary:** Spawns helper mobs on a cooldown with cap limits and a moderate health bonus.
- **Key Components:** `worldawakened:reinforcement_summon`, `worldawakened:summon_cooldown`, `worldawakened:summon_cap`, `worldawakened:max_health_bonus`.
- **Key Conditions:** No mutator-local `required_conditions`.
- **Related Objects:** `worldawakened:nether_pressure_pool`, `worldawakened:invasion_pool`.
- **Example Use Case:** Mid-game fights where players must decide between burning the summoner or cleaning adds.
- **Notes:** Counterplay: crowd control and target-priority play. Authoring level: intermediate. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/mob_mutators/summoner_preset.json`.

### `hunter` (`worldawakened:hunter_preset`)
- **Category:** Mutation preset
- **Source Status:** `shipped`
- **Design Intent:** Pursuit/tracking pressure that punishes passive line-of-sight breaks.
- **Composition Summary:** Detection (`wall_sense`, `target_range_bonus`) plus chase speed (`pursuit_speed_boost`, `movement_speed_bonus`).
- **Key Components:** `worldawakened:wall_sense`, `worldawakened:target_range_bonus`, `worldawakened:pursuit_speed_boost`, `worldawakened:movement_speed_bonus`.
- **Key Conditions:** No mutator-local `required_conditions`.
- **Related Objects:** `worldawakened:starter_elite_pool`, `worldawakened:overworld_night_pool`, `worldawakened:invasion_pool`.
- **Example Use Case:** Night overworld elite variants that keep pressure during repositioning.
- **Notes:** Counterplay: burst windows, movement items, vertical separation. Authoring level: beginner-friendly. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/mob_mutators/hunter_preset.json`.

### `predator` (`worldawakened:vampiric_predator`)
- **Category:** Mutation preset
- **Source Status:** `shipped`
- **Design Intent:** Aggressive sustain attacker that extends fights through self-heal and debuff pressure.
- **Composition Summary:** Lifesteal + flat damage + speed + on-hit weakness.
- **Key Components:** `worldawakened:life_steal`, `worldawakened:attack_damage_bonus`, `worldawakened:movement_speed_bonus`, `worldawakened:on_hit_effect`.
- **Key Conditions:** `worldawakened:world_day_gte` (mutator-local threshold in the shipped external example-pack object).
- **Related Objects:** `worldawakened:starter_elite_pool`.
- **Example Use Case:** Higher-risk melee elite in early-mid progression.
- **Notes:** Counterplay: kite and deny hit uptime. Authoring level: intermediate. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/mob_mutators/vampiric_predator.json`.

### `elemental_elite` (family template)
- **Category:** Mutation preset
- **Source Status:** `example-only`
- **Design Intent:** Thematic elemental package variants that produce readable damage identity.
- **Composition Summary:** Start from one elemental package and pair with one pressure axis (pursuit, burst, or aura).
- **Key Components:** `worldawakened:frost_package`, `worldawakened:lightning_package`, `worldawakened:poison_package`, plus `worldawakened:on_hit_effect`, `worldawakened:damage_aura`, or `worldawakened:burst_movement`.
- **Key Conditions:** Commonly `worldawakened:world_day_gte`; optional biome/dimension gating via pool or rule conditions.
- **Related Objects:** Shipped examples: `worldawakened:frost_hunter`, `worldawakened:lightning_berserker`, `worldawakened:plague_walker`; pools: `overworld_night_pool`, `nether_pressure_pool`, `invasion_pool`.
- **Example Use Case:** Offer elemental variance without custom code by combining existing components differently.
- **Notes:** Counterplay should be element-legible (fire/frost/lightning prep). Authoring level: advanced due readability/balance tuning.

### `thorned_defender`
- **Category:** Mutation preset
- **Source Status:** `conceptual`
- **Design Intent:** Punish reckless melee through retaliation while retaining tank identity.
- **Composition Summary:** Durable shell plus retaliation and temporary mitigation windows.
- **Key Components:** `worldawakened:retaliation_thorns`, `worldawakened:armor_bonus`, `worldawakened:temporary_shield`, `worldawakened:max_health_bonus`.
- **Key Conditions:** Optional `worldawakened:world_day_gte`; optional `worldawakened:entity_not_boss`.
- **Related Objects:** Candidate pool targets: early overworld or fortress-defense style pools.
- **Example Use Case:** Defensive elite for melee-heavy worlds where players need safer engage patterns.
- **Notes:** Counterplay: ranged burst, spacing, shield-break windows. Authoring level: beginner-friendly.

### `unyielding_hunter` (mutation template)
- **Category:** Mutation preset
- **Source Status:** `conceptual`
- **Design Intent:** Durable tracker hybrid for late-mid game pursuit pressure.
- **Composition Summary:** Hunter tracking base plus resilience layer to survive disengage tactics.
- **Key Components:** `worldawakened:wall_sense`, `worldawakened:pursuit_speed_boost`, `worldawakened:max_health_bonus`, `worldawakened:debuff_resistance`.
- **Key Conditions:** `worldawakened:world_day_gte` and optional stage gate.
- **Related Objects:** Similar gameplay space to shipped `worldawakened:hunter_preset` (mutation) and shipped `worldawakened:unyielding_hunter` (ascension reward ID).
- **Example Use Case:** High-pressure chase elites in packs that reward mobility mastery.
- **Notes:** Counterplay: disable/slow immunity management and focused burst. Authoring level: advanced. Use a distinct mutator ID to avoid confusion with the shipped ascension reward name.

### `summoner_juggernaut` (`worldawakened:summoner_juggernaut`)
- **Category:** Mutation preset
- **Source Status:** `shipped`
- **Design Intent:** Hybrid leader elite that combines reinforcement tempo with frontline durability.
- **Composition Summary:** Summon loop with explicit cooldown/cap layered on health multiplier + armor.
- **Key Components:** `worldawakened:reinforcement_summon`, `worldawakened:summon_cooldown`, `worldawakened:summon_cap`, `worldawakened:max_health_multiplier`, `worldawakened:armor_bonus`.
- **Key Conditions:** `worldawakened:world_day_gte` (mutator-local threshold in the shipped external example-pack object).
- **Related Objects:** `worldawakened:end_elite_pool`.
- **Example Use Case:** Late-game elite commanders that require coordinated target focus.
- **Notes:** Counterplay: crowd-control summons, anti-heal/sustain denial, burst the leader. Authoring level: advanced due multi-axis pressure. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/mob_mutators/summoner_juggernaut.json`.

## 7. Ascension Reward Preset Coverage (Category B)

### `titan_blood`
- **Category:** Ascension reward preset
- **Source Status:** `conceptual`
- **Design Intent:** Permanent tank identity centered on raw survivability.
- **Composition Summary:** Large health and armor scaling with small sustain reinforcement.
- **Key Components:** `worldawakened:max_health_bonus`, `worldawakened:armor_bonus`, `worldawakened:armor_toughness_bonus`, `worldawakened:healing_efficiency_bonus`.
- **Key Conditions:** Optional `worldawakened:world_day_gte` or stage gate for late unlock.
- **Related Objects:** Offer templates: `tank_offer`; nearest shipped analog: `worldawakened:tempered_steel`.
- **Example Use Case:** Players who want to survive attrition-heavy invasion or elite encounters.
- **Notes:** Permanent benefit profile: durable baseline stats. Player fantasy: unstoppable frontline. Synergy: pairs with sustain/offhand utility.

### `relentless`
- **Category:** Ascension reward preset
- **Source Status:** `conceptual`
- **Design Intent:** Permanent offensive sustain for pressure-oriented players.
- **Composition Summary:** Damage/crit with minor lifesteal and recovery efficiency.
- **Key Components:** `worldawakened:attack_damage_bonus`, `worldawakened:crit_bonus`, `worldawakened:life_steal_minor`, `worldawakened:healing_efficiency_bonus`.
- **Key Conditions:** Optional `worldawakened:world_day_gte` for pacing.
- **Related Objects:** Offer templates: `offensive_offer`; nearest shipped analog: `worldawakened:blood_reaper`.
- **Example Use Case:** Duel/skirmish playstyles that want consistent damage uptime.
- **Notes:** Permanent benefit profile: offensive sustain curve. Player fantasy: relentless duelist. Synergy: movement bonuses and elite detection builds.

### `hunters_instinct`
- **Category:** Ascension reward preset
- **Source Status:** `example-only`
- **Design Intent:** Persistent hunter toolkit for threat tracking and precision bursts.
- **Composition Summary:** Detection + reach + crit package for target acquisition and finish pressure.
- **Key Components:** `worldawakened:hostile_wall_sense`, `worldawakened:attack_reach_bonus`, `worldawakened:crit_bonus`, `worldawakened:elite_detection`.
- **Key Conditions:** Typical gate: `worldawakened:world_day_gte`.
- **Related Objects:** Shipped close implementation: `worldawakened:predator_instinct`.
- **Example Use Case:** Precision players that value awareness and fast priority target kills.
- **Notes:** Permanent benefit profile: tracking + burst utility. Player fantasy: apex tracker. Synergy: movement-heavy or conquest offers.

### `unyielding`
- **Category:** Ascension reward preset
- **Source Status:** `conceptual`
- **Design Intent:** General survivability anchor not tied to one damage type.
- **Composition Summary:** Layered mitigation and health padding with crowd-control resilience.
- **Key Components:** `worldawakened:max_health_bonus`, `worldawakened:debuff_resistance`, `worldawakened:extra_revival_buffer`, `worldawakened:knockback_resistance_bonus`.
- **Key Conditions:** Optional late-game gates (`worldawakened:world_day_gte`, stage thresholds).
- **Related Objects:** Nearest shipped analogs: `worldawakened:tempered_steel`, `worldawakened:void_resilience`.
- **Example Use Case:** Hardcore progression where reliability is more important than burst.
- **Notes:** Permanent benefit profile: anti-failure safety net. Player fantasy: unbreakable survivor. Synergy: tank or invasion defense paths.

### `treasure_sense`
- **Category:** Ascension reward preset
- **Source Status:** `example-only`
- **Design Intent:** Loot and progression utility identity for exploration-focused players.
- **Composition Summary:** Luck and loot quality gains with discovery utility.
- **Key Components:** `worldawakened:loot_detection`, `worldawakened:luck_bonus`, `worldawakened:loot_quality_bonus`, `worldawakened:xp_gain_bonus`.
- **Key Conditions:** Optional adventure pacing gates (`worldawakened:world_day_gte`, biome/dimension gates via offers/rules).
- **Related Objects:** Shipped close implementation: `worldawakened:arcane_luck`.
- **Example Use Case:** Exploration and chest-hunt worlds where resource quality is a core progression loop.
- **Notes:** Permanent benefit profile: economy/utility scaling. Player fantasy: fortune-seeker. Synergy: explorer + low-risk combat rewards.

### `ember_blood` (`worldawakened:ember_blood`)
- **Category:** Ascension reward preset
- **Source Status:** `shipped`
- **Design Intent:** Durable sustain/offense hybrid for early to mid progression.
- **Composition Summary:** Armor-toughness, offense, and resilience package.
- **Key Components:** `worldawakened:armor_toughness_bonus`, `worldawakened:attack_damage_bonus`, `worldawakened:fire_resistance_passive`, `worldawakened:debuff_resistance`, `worldawakened:healing_efficiency_bonus`.
- **Key Conditions:** None in reward object.
- **Related Objects:** Offers: `worldawakened:starter_adaptation`, `worldawakened:survival_path`; rules: `player_starter_offer_cycle`, `player_survival_offer_cycle`.
- **Example Use Case:** Reliable first ascension choice for players entering sustained combat or attrition-heavy encounters.
- **Notes:** Permanent benefit profile: balanced offense/survival baseline. Player fantasy: hardened survivor. Synergy: `tempered_steel` paths and sustain combat builds. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/ascension_rewards/ember_blood.json`.

### `predator_sense`
- **Category:** Ascension reward preset
- **Source Status:** `example-only`
- **Design Intent:** Elite hunter awareness package emphasizing threat discovery and strike timing.
- **Composition Summary:** Sensing + elite detection + crit pressure.
- **Key Components:** `worldawakened:hostile_wall_sense`, `worldawakened:elite_detection`, `worldawakened:crit_bonus`, `worldawakened:movement_speed_bonus`.
- **Key Conditions:** Common gate: `worldawakened:world_day_gte`.
- **Related Objects:** Shipped close implementation: `worldawakened:predator_instinct`; offer path: `worldawakened:predator_path`.
- **Example Use Case:** Players that actively route around elite targets and prioritize tempo kills.
- **Notes:** Permanent benefit profile: hunt utility + damage conversion. Player fantasy: predator specialist. Synergy: conquest or hunter-aligned offers.

### `unyielding_will`
- **Category:** Ascension reward preset
- **Source Status:** `conceptual`
- **Design Intent:** Late-game fail-safe defensive capstone.
- **Composition Summary:** Revival buffer and resistance layering for high-pressure encounters.
- **Key Components:** `worldawakened:extra_revival_buffer`, `worldawakened:debuff_resistance`, `worldawakened:mutation_resistance_bonus`, `worldawakened:max_health_bonus`.
- **Key Conditions:** Usually late gate (`worldawakened:world_day_gte` and/or stage milestone).
- **Related Objects:** Nearest shipped analog: `worldawakened:void_resilience`.
- **Example Use Case:** Endgame survival-focused players handling elite waves or future invasions.
- **Notes:** Permanent benefit profile: anti-wipe survivability. Player fantasy: indomitable veteran. Synergy: tank-focused offer bundles.

## 8. Multi-Object Template Coverage (Categories C-G)

### C. Mutation Pool Templates

#### `early_overworld_elite_pool` (`worldawakened:starter_elite_pool`)
- **Category:** Mutation pool template
- **Source Status:** `shipped`
- **Design Intent:** Introduce elite behavior early with controlled intensity.
- **Composition Summary:** Pool with low cap (`max_mutators_per_entity: 1`) and weighted starter mutators.
- **Key Components:** Through mutator refs: hunter tracking (`wall_sense` set), defense (`temporary_shield` set), sustain offense (`life_steal` set).
- **Key Conditions:** `worldawakened:current_dimension` (overworld), `worldawakened:world_day_gte` (0), plus rule-side `worldawakened:random_chance`.
- **Related Objects:** Pool `worldawakened:starter_elite_pool`; rule `worldawakened:mutators/overworld_starter_mutation_pressure`; mutators `hunter_preset`, `unyielding_guard`, `vampiric_predator`.
- **Example Use Case:** Starter worlds that want visible progression pressure without sudden difficulty spikes.
- **Notes:** Good baseline template for "vanilla-plus" style packs. File references: `example_datapacks/worldawakened_example_pack/data/worldawakened/mutation_pools/starter_elite_pool.json`, `example_datapacks/worldawakened_example_pack/data/worldawakened/rules/mutators/overworld_starter_mutation_pressure.json`.

#### `undead_progression_pool`
- **Category:** Mutation pool template
- **Source Status:** `example-only`
- **Design Intent:** Theme pool around undead progression across early/mid stages.
- **Composition Summary:** Entity-targeted pool that ramps from pursuit to plague/volatile patterns over stage gates.
- **Key Components:** Via mutator refs: `wall_sense`, `on_hit_effect` (`poison`), `damage_aura`, `death_explosion`.
- **Key Conditions:** Typical: `worldawakened:stage_unlocked`, `worldawakened:world_day_gte`, optional biome or moon phase.
- **Related Objects:** Candidate mutators: `hunter_preset`, `plague_walker`, `volatile_abomination`.
- **Example Use Case:** Undead-focused packs that want explicit escalation themes.
- **Notes:** Recommended as a documented example, not included in the shipped external example pack by default.

#### `nether_escalation_pool` (`worldawakened:nether_pressure_pool`)
- **Category:** Mutation pool template
- **Source Status:** `shipped`
- **Design Intent:** Mid-game nether pressure with higher burst/chaos profile.
- **Composition Summary:** Nether-only pool referencing berserker/summoner/volatile mutators with two-mutator cap.
- **Key Components:** Through mutator refs: `lightning_package`, `reinforcement_summon`, `death_explosion`, `damage_aura`.
- **Key Conditions:** `worldawakened:current_dimension` (the_nether), `worldawakened:world_day_gte` (10), rule-side `worldawakened:random_chance`.
- **Related Objects:** Pool `worldawakened:nether_pressure_pool`; rule `worldawakened:mutators/nether_mutation_pressure`; mutators `lightning_berserker`, `summoner_preset`, `volatile_abomination`.
- **Example Use Case:** Packs that want nether entry to feel like an immediate difficulty tier shift.
- **Notes:** Tune weights before increasing mutator cap; this template can spike quickly. File references: `example_datapacks/worldawakened_example_pack/data/worldawakened/mutation_pools/nether_pressure_pool.json`, `example_datapacks/worldawakened_example_pack/data/worldawakened/rules/mutators/nether_mutation_pressure.json`.

#### `endgame_invader_pool`
- **Category:** Mutation pool template
- **Source Status:** `example-only`
- **Design Intent:** Late-game/high-event mutation bundle for invasion or wave content.
- **Composition Summary:** Compose invasion-themed and end-elite mutators under high day thresholds and invasion-active gates.
- **Key Components:** Through mutator refs: summoning + heavy defense + elemental burst + aura/retaliation.
- **Key Conditions:** `worldawakened:invasion_active`, `worldawakened:world_day_gte`, optional `worldawakened:stage_unlocked` milestone.
- **Related Objects:** Shipped building blocks: `worldawakened:invasion_pool`, `worldawakened:end_elite_pool`, and corresponding mutator pressure rules.
- **Example Use Case:** Endgame servers that want invasions to feel distinct from baseline spawn pressure.
- **Notes:** Keep rerolls bounded and mutator caps explicit to avoid runaway encounter density.

### D. Ascension Offer Templates

#### `tank_offer` (implemented by `worldawakened:survival_path`)
- **Category:** Ascension offer template
- **Source Status:** `shipped`
- **Design Intent:** Defensive/tank reward choice set for survivability-focused players.
- **Composition Summary:** Explicit 3-choice/1-selection offer built from defensive rewards.
- **Key Components:** Via reward refs: `armor_bonus`, `armor_toughness_bonus`, `max_health_bonus`, `extra_revival_buffer`, `damage_type_resistance`, `debuff_resistance`, `fire_resistance_passive`.
- **Key Conditions:** Offer `trigger_conditions` with `worldawakened:world_day_gte` (16), plus rule-side `worldawakened:random_chance` and cooldown.
- **Related Objects:** Offer `worldawakened:survival_path`; rule `worldawakened:ascension/player_survival_offer_cycle`; rewards `tempered_steel`, `void_resilience`, `ember_blood`.
- **Example Use Case:** Survival-first progression where death tolerance matters more than burst output.
- **Notes:** Good default for player retention on harder packs. File references: `example_datapacks/worldawakened_example_pack/data/worldawakened/ascension_offers/survival_path.json`, `example_datapacks/worldawakened_example_pack/data/worldawakened/rules/ascension/player_survival_offer_cycle.json`.

#### `offensive_offer` (implemented by `worldawakened:conquest_path` and `worldawakened:predator_path`)
- **Category:** Ascension offer template
- **Source Status:** `shipped`
- **Design Intent:** Damage/pressure-oriented reward paths.
- **Composition Summary:** Two shipped offensive families: conquest (late, high aggression) and predator (earlier hunter offense).
- **Key Components:** Via reward refs: `attack_damage_bonus`, `crit_bonus`, `life_steal_minor`, `hostile_wall_sense`, `elite_detection`.
- **Key Conditions:** `worldawakened:world_day_gte` thresholds (8 and 24) and rule-side `worldawakened:random_chance`.
- **Related Objects:** Offers `worldawakened:predator_path`, `worldawakened:conquest_path`; rules `player_predator_offer_cycle`, `player_conquest_offer_cycle`.
- **Example Use Case:** Combat-focused servers where players choose between early pressure or late power spikes.
- **Notes:** Keep `reward_repeat_policy: block_all` to preserve identity separation and prevent previously forfeited rewards from resurfacing later.

#### `explorer_offer` (implemented by `worldawakened:explorer_path`)
- **Category:** Ascension offer template
- **Source Status:** `shipped`
- **Design Intent:** Mobility and utility progression path.
- **Composition Summary:** Explicit-list offer emphasizing movement, traversal, and loot utility.
- **Key Components:** Via reward refs: `movement_speed_bonus`, `step_height_bonus`, `night_vision_passive`, `knockback_resistance_bonus`, `loot_detection`, `luck_bonus`.
- **Key Conditions:** Offer `worldawakened:world_day_gte` (12) and rule-side chance/cooldown controls.
- **Related Objects:** Offer `worldawakened:explorer_path`; rule `worldawakened:ascension/player_explorer_offer_cycle`; rewards `grave_stride`, `storm_chaser`, `arcane_luck`.
- **Example Use Case:** Exploration-heavy packs with broad world traversal loops.
- **Notes:** Strong template for non-hardcore player progression variety. File reference: `example_datapacks/worldawakened_example_pack/data/worldawakened/ascension_offers/explorer_path.json`.

#### `loot_focused_offer`
- **Category:** Ascension offer template
- **Source Status:** `example-only`
- **Design Intent:** Reward path centered on loot quality and discovery tools.
- **Composition Summary:** Candidate list anchored by utility/loot rewards and low direct combat scaling.
- **Key Components:** `worldawakened:loot_detection`, `worldawakened:luck_bonus`, `worldawakened:loot_quality_bonus`, `worldawakened:xp_gain_bonus`.
- **Key Conditions:** Common: `worldawakened:world_day_gte` plus optional exploration-biome gates.
- **Related Objects:** Candidate rewards likely include `worldawakened:arcane_luck` plus custom utility rewards.
- **Example Use Case:** Adventure servers where economy/discovery is a primary progression vector.
- **Notes:** Keep one direct-survival fallback reward to avoid trap offers for newer players.

### E. Rule Templates

#### `unlock_stage_on_dimension_entry`
- **Category:** Rule template
- **Source Status:** `conceptual`
- **Design Intent:** Gate progression by world transition milestones.
- **Composition Summary:** Trigger or rule checks dimension and unlocks a stage exactly once.
- **Key Components:** None (action/condition composition template).
- **Key Conditions:** `worldawakened:current_dimension`, optional one-shot gate (`trigger_consumed` when promoted).
- **Related Objects:** `stages`, `trigger_rules` or `rules`, action `worldawakened:unlock_stage`.
- **Example Use Case:** Unlock `my_pack:nether_opened` when player first enters nether.
- **Notes:** Canonical snippet:

```json
{
  "type": "worldawakened:unlock_stage",
  "parameters": { "stage": "my_pack:nether_opened" }
}
```

#### `unlock_stage_on_boss_kill`
- **Category:** Rule template
- **Source Status:** `conceptual`
- **Design Intent:** Milestone progression from boss defeat events.
- **Composition Summary:** Boss-kill trigger condition gates stage unlock and optional event emit.
- **Key Components:** None (action/condition composition template).
- **Key Conditions:** Trigger compatibility condition `boss_killed` (trigger path), optional `worldawakened:entity_type` in shared contexts.
- **Related Objects:** `trigger_rules`, `stages`, optional follow-up `rules`.
- **Example Use Case:** Unlock endgame stage after wither or pack-defined boss kill.
- **Notes:** Keep boss identity data-driven by entity ID/tag maps.

#### `grant_ascension_on_stage_unlock`
- **Category:** Rule template
- **Source Status:** `conceptual`
- **Design Intent:** Tie progression milestones to player reward-choice moments.
- **Composition Summary:** Player-scope rule checks stage state and grants offer with cooldown/consumption protections.
- **Key Components:** None (action/condition composition template).
- **Key Conditions:** `worldawakened:stage_unlocked`, optional `worldawakened:ascension_offer_pending` negate gate.
- **Related Objects:** `rules`, `ascension_offers`, `ascension_rewards`; action `worldawakened:grant_ascension_offer`.
- **Example Use Case:** Unlocking `my_pack:bosses_awakened` grants one curated ascension offer.
- **Notes:** Keep idempotency explicit using cooldown/consumed flags.

#### `activate_mutation_pool_by_progression_stage`
- **Category:** Rule template
- **Source Status:** `conceptual`
- **Design Intent:** Stage-driven spawn pressure escalation.
- **Composition Summary:** Spawn-event rule gates `apply_mutator_pool` by stage + world context.
- **Key Components:** Through referenced mutator pool and mutator definitions.
- **Key Conditions:** `worldawakened:stage_unlocked`, `worldawakened:current_dimension`, `worldawakened:random_chance`.
- **Related Objects:** `rules`, `mutation_pools`, `mob_mutators`; action `worldawakened:apply_mutator_pool`.
- **Example Use Case:** Activate nether pool only after `my_pack:nether_opened`.
- **Notes:** `apply_mutator_pool` remains a planned canonical action in shared docs; validate runtime support path before relying on it in production packs.

#### `schedule_invasion_on_milestone`
- **Category:** Rule template
- **Source Status:** `conceptual`
- **Design Intent:** Trigger invasion scheduling from major progression milestones.
- **Composition Summary:** World-scope milestone rule that triggers immediate invasion profile or delayed check.
- **Key Components:** None (action/condition composition template).
- **Key Conditions:** `worldawakened:stage_unlocked`, optional `worldawakened:player_count_online`, optional `worldawakened:random_chance`.
- **Related Objects:** `rules`, `invasion_profiles`, actions `worldawakened:trigger_invasion_profile` or `worldawakened:schedule_invasion_check`.
- **Example Use Case:** Start retaliation wave after endgame stage unlock.
- **Notes:** Invasion action handlers are planned; keep template marked conceptual until runtime lands.

#### `world_context_mutation_pressure_cycle` (shipped rule family)
- **Category:** Rule template
- **Source Status:** `shipped`
- **Design Intent:** Use dimension/day/chance gates to apply different mutation pools by context.
- **Composition Summary:** Bundle of `rules/mutators/*.json` that each map one context profile to one pool.
- **Key Components:** Through referenced pools and mutators.
- **Key Conditions:** `worldawakened:current_dimension`, `worldawakened:world_day_gte`, `worldawakened:random_chance`, optional `worldawakened:invasion_active`.
- **Related Objects:** `overworld_starter_mutation_pressure`, `overworld_night_mutation_pressure`, `nether_mutation_pressure`, `end_elite_mutation_pressure`, `invasion_mutation_pressure`.
- **Example Use Case:** Baseline shipped mutation pressure pipeline while Phase 5 runtime matures.
- **Notes:** File references: `example_datapacks/worldawakened_example_pack/data/worldawakened/rules/mutators/*.json`.

### F. Invasion Profile Templates

#### `milestone_retaliation`
- **Category:** Invasion profile template
- **Source Status:** `conceptual`
- **Design Intent:** Deterministic retaliation event for major stage milestones.
- **Composition Summary:** Invasion profile plus world rule that triggers it on stage unlock.
- **Key Components:** Via wave mutator refs, reward profile refs, and spawn composition definitions.
- **Key Conditions:** `worldawakened:stage_unlocked`, `worldawakened:player_count_online`, optional `worldawakened:current_dimension`.
- **Related Objects:** `invasion_profiles`, `rules`, `mutation_pools`, optional `loot_profiles`.
- **Example Use Case:** One-time retaliation siege after defeating a progression boss.
- **Notes:** Keep concurrency limits explicit so milestone events do not overlap unpredictably.

#### `random_periodic_siege`
- **Category:** Invasion profile template
- **Source Status:** `conceptual`
- **Design Intent:** Ambient invasion pressure on a bounded periodic cadence.
- **Composition Summary:** Scheduler-driven invasion checks with chance and cooldown gates.
- **Key Components:** Through invasion profile wave/mutator definitions.
- **Key Conditions:** `worldawakened:world_day_gte`, `worldawakened:player_count_online`, `worldawakened:random_chance`.
- **Related Objects:** `invasion_profiles`, rules using `worldawakened:schedule_invasion_check`.
- **Example Use Case:** Long-running servers where periodic conflict is desired without milestone triggers.
- **Notes:** Prefer schedule action over immediate trigger for bounded load and better pacing.

#### `endgame_elite_assault`
- **Category:** Invasion profile template
- **Source Status:** `conceptual`
- **Design Intent:** High-intensity late-game assault combining elite mutator themes.
- **Composition Summary:** Endgame profile chaining elite pools, stronger wave mixes, and optional bonus rewards.
- **Key Components:** Through mutator refs such as `summoner_juggernaut`, `juggernaut_preset`, elemental elites.
- **Key Conditions:** `worldawakened:stage_unlocked` (endgame milestone), optional `worldawakened:invasion_active` exclusivity checks.
- **Related Objects:** `invasion_profiles`, `mutation_pools`, `rules`, optional ascension follow-up offers.
- **Example Use Case:** Final-tier content loop for hardened progression worlds.
- **Notes:** Keep profile clearly labeled as high-risk; recommended for advanced pack audiences.

### G. Starter Pack Templates

#### `vanilla_plus_progression_pack`
- **Category:** Starter pack template
- **Source Status:** `example-only`
- **Design Intent:** Lightweight upgrade over vanilla pacing with minimal behavior surprise.
- **Composition Summary:** Reuse shipped starter/night/nether pools + starter/explorer/survival ascension paths with conservative rule chances.
- **Key Components:** Existing shipped mutator/reward components, no custom component IDs required.
- **Key Conditions:** Keep to stable implemented conditions (`current_dimension`, `world_day_gte`, `random_chance`).
- **Related Objects:** Shipped objects in `mob_mutators`, `mutation_pools`, `ascension_rewards`, `ascension_offers`, and `rules`.
- **Example Use Case:** First-time pack authors who want safe defaults and easy tuning.
- **Notes:** Recommended baseline template for future web "create project from template" flow.

#### `hardcore_escalation_pack`
- **Category:** Starter pack template
- **Source Status:** `conceptual`
- **Design Intent:** Aggressive difficulty growth for high-skill/co-op groups.
- **Composition Summary:** Increase elite weight, add stricter conditions, and chain high-pressure offer/reward paths.
- **Key Components:** Durability + retaliation + summon heavy mutator sets; survivability and sustain ascension mixes.
- **Key Conditions:** Stage/day gates + stricter chance/cooldown tuning; optional online-player scaling conditions.
- **Related Objects:** Mutation pools, rules, offers, future invasion profiles.
- **Example Use Case:** Challenge-focused servers aiming for persistent tension.
- **Notes:** Strongly advise incremental rollout and telemetry/debug review (`/wa dump active_rules`).

#### `exploration_reward_pack`
- **Category:** Starter pack template
- **Source Status:** `example-only`
- **Design Intent:** Reward traversal and discovery more than pure combat escalation.
- **Composition Summary:** Explorer/utility offer paths and lower baseline mutation pressure with selective spikes.
- **Key Components:** Mobility and utility rewards (`grave_stride`, `storm_chaser`, `arcane_luck`) plus moderate hunter-style mutators.
- **Key Conditions:** Dimension/biome/day gates with conservative random chance.
- **Related Objects:** `explorer_path`, `player_explorer_offer_cycle`, starter/night pools.
- **Example Use Case:** Adventure servers and quest-driven packs.
- **Notes:** Keep at least one defense path available so utility players are not forced into glass-cannon play.

#### `invasion_heavy_pack`
- **Category:** Starter pack template
- **Source Status:** `conceptual`
- **Design Intent:** Center gameplay around invasion events and siege recovery loops.
- **Composition Summary:** Invasion profile-first design with mutator pool hooks and reward incentives.
- **Key Components:** Invasion reward bonuses, commander/hunter reward identities, elite mutation pools.
- **Key Conditions:** Milestone and periodic invasion schedules; optional population gates.
- **Related Objects:** Future `invasion_profiles`, rule templates (`schedule_invasion_on_milestone`), and endgame pool templates.
- **Example Use Case:** PvE event servers that want recurring world-level threats.
- **Notes:** Keep config kill-switches and bounded scheduler settings exposed to operators.

## 9. Composition Fidelity Rule

All entries in this catalog must stay aligned with real framework primitives:
- list real component IDs for composition summaries
- list real condition/action IDs (implemented or planned status noted)
- mark conceptual/example-only entries explicitly
- never present a conceptual label as shipped external example-pack content

Authoring-shape note:
- canonical shared docs use `type + parameters` condition/action nodes
- shipped example-pack objects use the same canonical `type + parameters` shape
- this catalog preserves shipped IDs and semantics while referencing canonical type identities

## 10. Shipped Content Alignment

Shipped alignment snapshot date: 2026-03-12.

External example-pack source root for shipped entries:
- `example_datapacks/worldawakened_example_pack/data/worldawakened/`

Current shipped example-pack object counts:
- `mob_mutators`: 10
- `ascension_rewards`: 10
- `ascension_offers`: 5
- `mutation_pools`: 5
- `rules`: 10
- `invasion_profiles`: 0

Shipped template/preset entries in this catalog map directly to external example-pack files in these folders:
- mutation presets: `mob_mutators/*.json`
- ascension rewards: `ascension_rewards/*.json`
- ascension offers: `ascension_offers/*.json`
- mutation pool templates: `mutation_pools/*.json`
- shipped rule template family: `rules/mutators/*.json` and `rules/ascension/*.json`

## 11. Future Web-Editor Relevance

This catalog is the intended source document for:
- preset browser candidates
- starter template cards
- guided authoring examples
- future "build from template" flows in the web authoring tool

UI/tooling guidance:
- expose `Source Status` badges directly (`shipped`, `example-only`, `conceptual`, `deprecated`)
- allow filtering by category and status
- keep template cards linked to real object IDs/paths when `shipped`

## 12. Contributor Update Rules

Update this file whenever shipped presets/templates are:
- added
- removed
- renamed
- substantially reworked

Maintenance requirements:
- keep `Source Status` labels explicit and correct
- keep composition summaries aligned to real components/conditions/actions
- keep shipped entries synchronized with external example-pack datapack IDs and file paths
- do not let conceptual patterns drift into presented-as-shipped fiction
- add practical, high-signal examples whenever new major systems land
- if a template becomes obsolete but is kept for compatibility reference, move it to `deprecated` with migration notes


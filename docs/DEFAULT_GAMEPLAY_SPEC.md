# The Awakening (Standard) Gameplay Contract

- Document status: Active gameplay contract; balance values remain provisional until play-tested
- Last updated: 2026-10-17
- Scope: Bundled install-and-play Standard profile

This document incorporates the approved default-gameplay brief into the repository. World Awakened defaults are ordinary datapack definitions under `worldawakened:defaults/...`; they use the same codecs, validators, compiler, indexes, and runtime services as custom content.

## 1. Non-Negotiable Rules

- Progress principally through milestones, not endless world-age scaling.
- Most hostile mobs remain normal at every stage.
- Mutations must be readable and bounded; bosses, spawner mobs, and trial-spawner mobs are excluded by default.
- Default definitions target a conservative tested vanilla allowlist. Unknown modded entities require explicit author opt-in until safe classification work lands.
- Default loot, ascension, and invasions must use existing canonical pipelines and preserve third-party authority such as Apotheosis loot behavior.
- Custom packs may replace a bundled object at the same resource path or add objects at different IDs.
- `general.enable_builtin_gameplay=false` removes every `worldawakened:defaults/...` definition during reload for fully custom setups.

## 2. Ranked Global Progression

The Standard preset uses global world progression and player-owned Ascension.

| Rank | ID | Display | Milestone |
|---:|---|---|---|
| S0 | `worldawakened:defaults/dormant` | Dormant | Default-unlocked baseline |
| S1 | `worldawakened:defaults/stirring` | Stirring | World day 3 |
| S2 | `worldawakened:defaults/unease` | Unease | `minecraft:story/mine_diamond` |
| S3 | `worldawakened:defaults/nether_reached` | Ignition | Enter the Nether |
| S4 | `worldawakened:defaults/gathering_storm` | Gathering Storm | `minecraft:nether/obtain_blaze_rod` |
| S5 | `worldawakened:defaults/end_reached` | Fracture | Enter the End |
| S6 | `worldawakened:defaults/world_awakened` | World Awakened | `minecraft:end/kill_dragon` |

Milestone triggers unlock their rank and all lower ranks. A higher milestone therefore does not require every lower advancement to have happened first.

S1 uses the generic `worldawakened:world_day_milestone` trigger event. The server evaluates this event once per second in the Overworld; the authored one-shot rule and `world_day_gte` condition perform the actual activation. No stage ID or day threshold is hardcoded in the event handler.

## 3. Mutation Rates and Rare Combinations

Each stage/dimension has one unified mutation pool. Pre-S6 pools require their current stage and require the next stage to remain locked. Each pool rolls `mutation_chance` once; from S4 onward, a successful first mutation has a small conditional chance to resolve an eligible curated combination.

| Stage | Overworld initial / additional | Nether initial / additional | End initial / additional | Maximum |
|---|---:|---:|---:|---:|
| S0 | 0% / 0% | — | — | 0 |
| S1 | 2% / 0% | — | — | 1 |
| S2 | 4% / 0% | — | — | 1 |
| S3 | 6% / 0% | 8% / 0% | — | 1 |
| S4 | 8% / 2% | 11% / 2% | — | 2 |
| S5 | 10% / 5% | 13% / 5% | 14% / 5% | 2 |
| S6 | 13% / 9% | 16% / 9% | 18% / 9% | 2 |

The 2% (S4), 5% (S5), and 9% (S6) values are conditional additional rolls, not separate pool-selection shares or extra initial spawn rolls. Curated combination entity lists preserve the narrower eligibility of the former pair definitions. Consequently, a mob eligible for the parent pool but not its curated combination safely receives at most one ordinary mutation. Actual double-mutation frequency can therefore be below the theoretical maximum and must still be measured during play testing.

## 4. Verified Archetypes

The bundled profile only uses component paths with confirmed live runtime application:

| ID suffix | Display role | Live behavior basis |
|---|---|---|
| `weathered` | Hardened | health and armor additions |
| `restless` | Restless | movement multiplier and follow range |
| `sharp_eyed` | Sharp-Eyed | target/follow range |
| `bulwark` | Bulwark | absorption shield and armor |
| `hunter` | Hunter | pursuit speed, follow range, modest damage |
| `relentless` | Relentless | modest damage and movement multipliers |
| `nether_hardened` | Nether-Hardened | health, armor, and knockback resistance |
| `void_hardened` | Void-Hardened | health, armor, and absorption shield |
| `hindering` | Hindering | 35% chance to apply Slowness I for 3 seconds after damaging a target |
| `vampiric` | Vampiric | heals for 10% of actual health damage dealt, capped before overkill |
| `volatile` | Volatile | one block-safe power-2.5 explosion on death with chain prevention |

Every archetype uses a restrained `glow_style` with `see_through_walls=false`. Hindering, Vampiric, and Volatile also use existing particle emitters as ability tells; Volatile uses a bright orange pulsing outline plus flame particles so its death risk is visible before engagement. Applied mutator display names are composed in deterministic component order with the vanilla entity name (for example, `Vampiric Volatile Skeleton`) and shown above the mob. An existing custom name is always preserved.

The combat rollout is intentionally staged:

- S2 introduces Hindering at weight 6 in the Overworld single-mutator table.
- S3 retains Hindering and introduces rare Vampiric entries at weight 3 in the Overworld and Nether tables.
- S4 introduces Volatile at weight 2 in eligible Overworld and Nether tables.
- S4 begins curated `Hunter + Bulwark` and `Hunter + Nether-Hardened` combinations at a 2% conditional additional chance.
- S5 adds curated `Vampiric + Bulwark`, `Volatile + Nether-Hardened`, and `Hindering + Void-Hardened` combinations at 5%.
- S6 adds curated `Hindering + Vampiric`, `Hunter + Vampiric`, and `Volatile + Void-Hardened` combinations at 9%.

Every ranked ordinary-mutator table totals 100. Initial per-stage mutation chances are unchanged; conditional additional rolls replace the old duplicate pair-pool split without increasing the chance that a mob receives its first mutation. Boss protections, spawner/trial-spawner exclusions, and component/mutator budgets remain unchanged. Registered abilities without a safe live application path—including elemental packages, summoning, and retaliation—remain excluded.

## 5. Persistence and Overrides

Enabled `default_unlocked` stages are reconciled idempotently into the selected world/player progression bucket when that bucket is accessed. The unlock source is `default_unlocked`; this initialization does not synthesize a normal milestone event.

The built-in-profile gate filters only IDs whose namespace is `worldawakened` and whose path begins with `defaults/`. Other World Awakened definitions and other namespaces remain loaded.

Stable IDs from the initial slice are retained where practical:

- `nether_reached` and `end_reached` now represent the ranked Ignition and Fracture milestones
- `reach_nether` and `reach_end` remain their trigger IDs
- `weathered`, `nether_hardened`, and `void_hardened` remain valid mutator IDs
- `overworld_intro`, `nether_escalation`, and `end_escalation` remain active pool IDs at their corresponding ranks

## 6. Ascension

Three one-time, player-scoped offers use only reward components with confirmed live application paths:

| World Phase | Offer | Choices |
|---|---|---|
| Ignition (`worldawakened:defaults/nether_reached`) | First Adaptation | +2 max health, +0.01 movement speed, or +0.5 luck |
| Fracture (`worldawakened:defaults/end_reached`) | Path of Survival | +1 armor, +0.08 knockback resistance, or WA-owned passive fire resistance |
| World Awakened (`worldawakened:defaults/world_awakened`) | Awakened Choice | +1 attack damage, +1 armor toughness, or +1 luck |

Each offer presents three explicit candidates, accepts one permanent choice, and uses `once_per_player`. Pending and resolved instances both prevent duplicate grants, while closing the choice screen leaves the original pending offer available. The offer follows its linked phase, so changing that phase's milestone does not require duplicating the milestone in an Ascension trigger. A phase unlock evaluates every online player, and login/respawn reconciliation grants a still-missing eligible phase offer to players who were offline. Custom advancement-authored offers may still use the normal one-shot login reconciliation path.

## 7. Loot Rewards

Mutation rewards use three mutually exclusive stage bands and `inject` mode only:

- S1-S2: 10% chance of a small iron-nugget or arrow addition.
- S3-S4: 15% chance of a small gold-nugget, redstone, or glowstone addition.
- S5-S6: 20% chance of a bounded iron-ingot, gold-ingot, amethyst, or glowstone addition.

Profiles require mutation provenance and target only the tested vanilla roster. Spawner and trial-spawner enemies cannot enter the default mutation pools, so they cannot qualify through this path. No XP multiplier is claimed because the current loot pipeline does not apply one. Apotheosis-sensitive behavior remains additive and authoritative.

## 8. Pressure Events

| Profile | First stage | Dimension | Warning | Active duration | Profile cooldown | Pressure |
|---|---|---|---:|---:|---:|---:|
| Restless Night | S4 | Overworld | 40s | 4m | 90m | 1.15 |
| Nether Surge | S5 | Nether | 40s | 4m | 120m | 1.20 |
| Awakened Hunt | S6 | Overworld | 60s | 5m | 120m | 1.25 |

Periodic scheduling evaluates a profile in one of its allowed dimensions only while that dimension has a player. The global cooldown and `max_concurrent_invasions=1` remain authoritative. Start and end notices identify the profile and affected dimension. These events add temporary mutation pressure; they do not spawn waves, edit terrain, or grant timeout rewards. `Restless Night` and `Awakened Hunt` are not currently restricted by time of day because `time_of_day_range` is not implemented in the invasion evaluator.

## 9. Verification Status and Remaining Validation

Automated regression coverage verifies all 57 bundled definitions, exact ranked initial/additional mutation probabilities, 100-point ordinary-mutator weight tables, intended ability stage boundaries, exact curated combination membership, definition validation, persistent combat-state and override round trips, obsolete override rejection, overkill-safe lifesteal accounting, one-shot/chain-safe death explosions, generated/preserved names, and the unchanged state of unmutated mobs.

Human in-game testing remains required for feel and integration behavior that unit tests cannot establish: melee and projectile attribution, attacks against players with PvP enabled and disabled, visual readability at combat distance, save/restart persistence, datapack reload behavior on existing and new mobs, simultaneous multiplayer combat, measured server cost, spawn distribution, survival balance, and reward economy.

- Distribution, TPS/MSPT, survival, multiplayer, and economy play testing remains required; values are provisional.
- Debug sampling should be extended if field testing cannot establish multi-mutation/profile distributions from existing inspect traces.
- Default automatic mutation remains a tested vanilla allowlist. Server-derived modded-entity classification is deferred.
- The first native `/wa config` settings screen is implemented. Rich definition editing and Relaxed/Challenging presets remain future work.

## 10. Balance Status

The values in this document are starting targets, not claims of measured balance. Before release they require survival, Nether, post-Dragon, and multiplayer sessions plus spawn-distribution and server-cost instrumentation. Until that evidence exists, documentation must say `provisional` rather than `balanced`.

Operators can tune the live Standard experience through `/wa config`. Difficulty intensity scales World Awakened-owned numeric difficulty outputs, mutation frequency multiplies the authored final pool chance, the multiple-mutation controls can only tighten authored pool and engine caps, and event frequency scales future profile/global cooldowns. These world-level overrides do not rewrite the bundled JSON or increase hard performance budgets.

## 11. Combat Validation Checklist

For isolated ability testing, copy `dev_datapacks/worldawakened_combat_test` into the world's `datapacks` directory, set `general.enable_builtin_gameplay=false`, keep mutators and debug commands enabled, restart, and run `/wa reload validate`. The pack makes these live spawn tests deterministic:

```text
/wa debug spawn test minecraft:zombie
/wa debug spawn test minecraft:husk
/wa debug spawn test minecraft:creeper
/wa debug spawn test minecraft:skeleton
/wa debug spawn test minecraft:stray
```

They create, respectively, a lifesteal test mob, on-hit-effect test mob, death-explosion test mob, lifesteal/death-explosion combination, and on-hit-effect/lifesteal combination. Look at a spawned mob and run `/wa mob inspect` to verify its persisted pool, mutators, components, and stage context. `force_mutator` and `force_pool` are evaluation tools; they do not create a live entity, so use `spawn test` for combat.

Manual acceptance checks:

1. Damage each attacker first, then confirm lifesteal heals only after real health damage and never for more damage than the victim had remaining.
2. Confirm melee and mob-owned projectiles attribute effects to the mutated attacker; player-owned damage must not activate mob abilities.
3. Repeat against a player with server PvP enabled and disabled: mob-owned damage should behave the same in both modes, while player-owned PvP damage must never activate these mob abilities.
4. Kill Volatile alone and beside another Volatile; blocks must remain intact and one death must not synchronously chain another WA death explosion.
5. Save/restart and inspect a surviving test mob, then reload datapacks and compare an existing mob with a newly spawned mob.
6. Run the combinations with two players and confirm one hit produces one effect/heal and one death produces one explosion.

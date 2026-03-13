# World Awakened Future Ideas Backlog

Deferred and long-term feature backlog for World Awakened.

- Document status: Non-normative backlog
- Last updated: 2026-03-12
- Purpose: Record future feature ideas without expanding MVP scope

---

Related contracts:
- [docs/README.md](README.md)
- [SPECIFICATION.md](SPECIFICATION.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file when backlog entries are promoted, deprecated, removed, or reclassified.
- Keep this file aligned with active-scope boundaries in `SPECIFICATION.md` and workflow rules in `AGENTS.md`.

---

## 0. How To Use This Doc

This document records post-v1 and long-term ideas.

Hard rules:
- this file is not the implementation contract
- this file does not override `docs/SPECIFICATION.md`
- backlog items must not silently expand MVP scope
- an idea only becomes active scope when it is promoted into `docs/SPECIFICATION.md`

Use this document to:
- preserve design intent
- collect subsystem expansion ideas
- guide post-v1 planning once core systems prove stable

Related deferred design docs:
- `docs/FUTURE_ADMIN_UI.md` for the dedicated post-v1 in-game/admin runtime authoring and inspection UI concept

Related active companion scope:
- `docs/WEB_AUTHORING_TOOL_SPEC.md` defines the v1 browser datapack authoring/validation tool and is not a backlog item

---

## 1. Post-V1 Feature Backlog

These ideas are intentionally deferred from the initial release.

They should not block:
- stages
- triggers
- rules
- mutators
- invasions
- loot evolution
- ascension choices

### 1.1 World Mutations

Concept:
Biomes gradually change behavior as the world awakens.

Examples:
- forests spawn more wolves or hostile fauna
- deserts gain ambush packs or sandstorm events
- oceans spawn more aggressive creatures
- caves spawn elite packs

Possible mechanics:
- stage-conditioned biome behavior modifiers
- spawn pressure adjustments
- ambient environment effects
- optional fog, light, or sound changes

Goal:
Make the world itself feel like it reacts to progression, not only the mobs.

Possible future datapack object:
- `world_mutation_profiles`

### 1.2 Nemesis Mob System

Concept:
Rare mobs that remember the player and evolve into persistent personal enemies.

Possible triggers:
- a mob kills a player
- a mob survives multiple encounters
- a mob escapes an invasion

Possible features:
- unique name or title
- additional mutators over time
- kill count tracked against a player
- special loot when defeated

Goal:
Create memorable emergent stories and long-term enemies.

### 1.3 Relics

Concept:
Rare world artifacts that alter systems rather than acting as simple gear upgrades.

Examples:
- relic that increases invasion rewards
- relic that increases elite spawn chance
- relic that reveals mutated mobs nearby
- relic that improves loot quality while raising world hostility

Goal:
Introduce meaningful risk and reward tradeoffs.

Likely interaction surfaces:
- spawn pressure
- invasions
- mutation rarity
- loot profiles

### 1.4 World Corruption or Pressure System

Concept:
A global hostility meter influenced by player actions.

Actions that increase pressure:
- boss kills
- exploration milestones
- use of powerful relics
- killing elite mobs

Actions that reduce pressure:
- clearing invasions
- defeating nemesis mobs
- special rituals or events

Effects of high pressure:
- invasion frequency increases
- elite spawn rate increases
- biome mutation effects intensify

Goal:
Make the world react dynamically to player actions.

### 1.5 Mutated Structures

Concept:
Existing structures gain awakened variants at higher stages.

Examples:
- awakened village under siege
- awakened dungeon with elite guardians and relic loot
- awakened stronghold with mutated defenders

Effects:
- improved loot
- stronger enemies
- stage-based structure variants

Goal:
Keep exploration interesting later in progression.

### 1.6 Elite Mob Titles

Concept:
Mutated mobs receive readable titles that signal threat and identity.

Examples:
- Bloodmarked Zombie
- Ravenous Hunter Skeleton
- Stormcaller Pillager

Possible effects:
- small stat bonuses
- extra drops
- stronger visual distinction

Goal:
Improve readability of elite enemies.

### 1.7 Player Reputation With The World

Concept:
The world reacts differently depending on how aggressively players progress.

Examples:

Defiant players:
- more invasions
- stronger enemies
- better loot

Cautious players:
- slower escalation
- fewer invasions
- lower rewards

Goal:
Add personality to progression behavior.

### 1.8 Legendary Invasion Events

Concept:
Extremely rare global events that dramatically increase danger.

Examples:
- The Awakening Storm
- Night of the Thousand Dead

Effects:
- very high elite spawn rate
- special enemies
- massive rewards if survived

Goal:
Create memorable world-scale events.

### 1.9 Mob Evolution Families

Concept:
Mobs evolve along recognizable archetype paths instead of only receiving random mutators.

Example:

```text
Zombie
 |- Ravager Zombie
 |- Plague Zombie
 \- Juggernaut Zombie
```

Goal:
Allow players to recognize enemy archetypes and adapt strategy.

### 1.10 Stage Lore Messages

Concept:
Progression stages broadcast atmospheric world messages.

Examples:
- The world stirs.
- The old powers awaken.
- The creatures grow restless.
- The world is no longer safe.

Goal:
Add narrative flavor to progression.

---

## 2. Legendary Future Ideas

These concepts are intentionally beyond the initial framework scope.

They should only be considered after:
- stages
- triggers
- rules
- mutators
- invasions
- loot evolution
- ascension choices

are stable in real modpack use.

### 2.1 Adaptive World Intelligence

Concept:
The world dynamically adapts to player strategies over time.

Possible tracked metrics:
- ranged vs melee preference
- reliance on fire or elemental damage
- base-building style
- armor or tank playstyle
- frequent mob farming
- repeated use of specific weapons or enchantments

Possible responses:
- bow-heavy players cause more shielded mobs
- vertical bases cause more climbing or flying elites
- fire-heavy builds cause more fire-resistant variants
- heavy mob farming raises mutation rarity

Possible mechanics:
- behavior metrics accumulate over time
- metrics influence enemy evolution bias
- metrics decay slowly to avoid permanent lock-in

Goal:
Prevent players from solving combat permanently. The world learns and counters dominant strategies.

### 2.2 The Awakening Boss

Concept:
A persistent world antagonist that represents the awakening world itself.

Possible behaviors:
- scales with world progression
- spawns emissaries or elite agents
- appears during major invasions
- may retreat instead of dying under some conditions
- temporarily calms the world when defeated

Example concept:
- The Warden of Awakening

Possible mechanics:
- stage-linked health and ability scaling
- summons invasion units
- drops rare relics or ascension opportunities
- only appears after major thresholds
- may have multiple evolving forms

Goal:
Provide a central narrative enemy that embodies the mod concept.

### 2.3 The Final Awakening

Concept:
A late-game world state that transforms the entire save into a higher challenge mode without resetting it.

Possible triggers:
- reaching the final stage
- defeating a world boss
- crossing a maximum world difficulty threshold

Possible effects:
- greatly increased elite spawn rates
- additional mutator pools unlocked
- invasion frequency increases
- relic tier unlocks
- legendary enemies begin spawning
- new ascension choices become available

Optional mechanics:
- players intentionally trigger it
- players delay or suppress it through world actions

Goal:
Provide an endgame challenge layer for advanced players and long-running worlds.

---

## 3. Promotion Rule

Before any backlog idea moves into active implementation scope:
1. define the idea in `docs/SPECIFICATION.md`
2. update `README.md` if it changes scope, roadmap, or product summary
3. update `docs/DATAPACK_AUTHORING.md` if it introduces new object types or schema
4. update `AGENTS.md` if it changes contributor rules or delivery priorities

---

## 4. Design Guideline

These ideas exist to preserve long-term direction, not to pressure the MVP.

Hard line:
- core engine stability takes precedence over expansion features
- future systems must not compromise performance or observability
- future mutation and ascension ideas should compose through Java-registered component behavior types and datapack-authored named definitions
- future systems should reuse the existing data-driven framework unless a new core contract is explicitly introduced
- legendary features should only be explored after the base framework proves stable in real-world use


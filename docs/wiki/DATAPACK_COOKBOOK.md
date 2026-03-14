# Datapack Cookbook

Copyable authoring patterns with plain-language explanation.

- Document status: Active human-friendly authoring cookbook
- Last updated: 2026-03-14
- Scope: Common trigger, rule, mutator, and ascension patterns

---

## How To Read This File

Each recipe answers three questions:
1. what behavior you want
2. why the chosen scope/shape is correct
3. which JSON pattern to copy from

Use the technical docs for the full contract.
Use this file when you need a working pattern.

## Recipe 1: Unlock A Shared Stage When Any Player Enters The Nether

Use this when:
- the save should progress globally
- one player's first Nether entry should advance the whole server
- cooldown/one-shot tracking should be shared

```json
{
  "schema_version": 1,
  "id": "my_pack:unlock_nether_on_entry",
  "enabled": true,
  "priority": 100,
  "trigger_type": "worldawakened:player_enter_dimension",
  "source_scope": "world",
  "conditions": [
    {
      "type": "worldawakened:current_dimension",
      "parameters": {
        "dimension": "minecraft:the_nether"
      }
    }
  ],
  "actions": [
    {
      "type": "worldawakened:unlock_stage",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "one_shot": true
}
```

Why this uses `world`:
- the event may be caused by a player
- but the trigger should behave like one shared world milestone
- the unlock, cooldown, and one-shot behavior should be shared

## Recipe 2: Let One Player's Action Unlock Shared Progression And Then Grant That Player A Personal Offer

Use this when:
- progression mode is `global`
- one player's action should advance the shared stage state
- a downstream ascension offer should still go to the player who caused the event

```json
{
  "schema_version": 1,
  "id": "my_pack:open_trial_gate",
  "enabled": true,
  "priority": 100,
  "trigger_type": "worldawakened:manual_debug",
  "source_scope": "player",
  "conditions": [],
  "actions": [
    {
      "type": "worldawakened:unlock_stage",
      "parameters": {
        "stage": "my_pack:trial_gate_open"
      }
    }
  ],
  "one_shot": false
}
```

Then pair it with an ascension offer that watches the unlocked stage:

```json
{
  "schema_version": 1,
  "id": "my_pack:trial_offer",
  "display_name": "Trial Offer",
  "enabled": true,
  "trigger_conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:trial_gate_open"
      }
    }
  ],
  "choice_count": 3,
  "selection_count": 1,
  "candidate_rewards": [
    "my_pack:reward_a",
    "my_pack:reward_b",
    "my_pack:reward_c"
  ],
  "candidate_reward_tags": [],
  "offer_mode": "explicit_list",
  "weighting_rules": {},
  "ui_priority": 10,
  "allow_duplicates_across_players": true,
  "reward_repeat_policy": "block_all"
}
```

Why this uses `player`:
- the trigger must carry a player source
- global stage progression can still be unlocked if progression mode is `global`
- the player source is preserved for the follow-up player-owned ascension flow
- because the example offer uses `reward_repeat_policy: block_all`, rewards the player previously rejected in earlier offers should not be offered again later either

## Recipe 3: Per-Player Advancement Progression

Use this when:
- each player should advance independently
- one player's advancement should not unlock stages for everyone else
- server config uses `progression.mode = "per_player"`

```json
{
  "schema_version": 1,
  "id": "my_pack:first_advancement_unlock",
  "enabled": true,
  "priority": 100,
  "trigger_type": "worldawakened:advancement_completed",
  "source_scope": "player",
  "conditions": [
    {
      "type": "worldawakened:advancement_completed",
      "parameters": {
        "advancement": "minecraft:story/mine_stone"
      }
    }
  ],
  "actions": [
    {
      "type": "worldawakened:unlock_stage",
      "parameters": {
        "stage": "my_pack:stone_age"
      }
    }
  ],
  "one_shot": true
}
```

Why this uses `player`:
- the advancement belongs to a player
- in `per_player` mode, the unlocked stage lands in that player's stage state

## Recipe 4: World Rule That Reacts To A Shared Stage

Use this when:
- one unlocked stage should activate shared follow-up logic later
- you want deterministic single-pass behavior

```json
{
  "schema_version": 1,
  "id": "my_pack:shared_followup_rule",
  "enabled": true,
  "priority": 10,
  "execution_scope": "world",
  "conditions": [
    {
      "type": "worldawakened:stage_unlocked",
      "parameters": {
        "stage": "my_pack:nether_opened"
      }
    }
  ],
  "actions": [
    {
      "type": "worldawakened:mark_rule_consumed",
      "parameters": {}
    }
  ],
  "weight": 1,
  "chance": 1
}
```

Why this uses `world` execution scope:
- it is reacting to shared stage state
- it is meant to consume or act once for the shared world timeline

## Recipe 5: Manual Debug Trigger For Safe Local Testing

Use this when:
- you want a deterministic authored trigger path
- you do not want to wait for the live gameplay condition

```json
{
  "schema_version": 1,
  "id": "my_pack:test_unlock",
  "enabled": true,
  "priority": 100,
  "trigger_type": "worldawakened:manual_debug",
  "source_scope": "player",
  "conditions": [],
  "actions": [
    {
      "type": "worldawakened:unlock_stage",
      "parameters": {
        "stage": "my_pack:test_stage"
      }
    }
  ]
}
```

Run it with:

```text
/wa trigger fire my_pack:test_unlock player Dev
```

Use `source_scope: "world"` instead if you want a shared global debug trigger with no player-bound downstream actions.

## Recipe 6: Authoring Choice Guide

If you are stuck between `world` and `player`, decide like this:

Use `world` when:
- the trigger's identity should be shared for the whole save
- one-shots/cooldowns/counters should be shared
- the downstream behavior is world-owned

Use `player` when:
- the trigger needs a real player source
- the downstream behavior is player-owned
- you want independent player trigger history
- ascension or messaging needs a bound player

## Recipe 7: Testing The Pattern You Authored

After authoring a new trigger/rule pattern:
1. validate the datapack
2. inspect stage state before firing anything
3. fire the trigger with the correct target and optional dimension
4. inspect stage state again
5. inspect active rules
6. inspect ascension state if the pattern is supposed to touch offers

This catches scope mistakes early.

## Recipe 8: Give A Mutated Mob Gear Without Breaking The Whole Mutation

Use this when:
- you want a mutator to hand a mob one or more authored items
- you want the runtime to pick the natural slot from the item when possible
- you do not want one unsupported equipment branch to kill the rest of the mutator

```json
{
  "id": "my_pack:armed_guard",
  "display_name": "Armed Guard",
  "weight": 1,
  "eligible_entities": ["minecraft:zombie"],
  "components": [
    {
      "type": "worldawakened:max_health_bonus",
      "parameters": {
        "amount": 6.0
      }
    },
    {
      "type": "worldawakened:equip_item",
      "parameters": {
        "item": "minecraft:iron_sword",
        "slot": "auto",
        "drop_chance": 0.1,
        "enchantments": [
          { "id": "minecraft:sharpness", "level": 2 }
        ]
      }
    }
  ]
}
```

Why this shape works:
- `slot: "auto"` keeps authoring simple and lets the runtime use the item's normal vanilla equipment slot
- you can author multiple `equip_item` entries in one mutator when you want several gear pieces
- if the mob cannot use the resolved slot or cannot hold the authored hand item, World Awakened skips only that `equip_item` component and keeps other valid components on the mutator

Testing loop:
1. reload and validate the datapack
2. force or spawn the target mob
3. run `/wa mob inspect`
4. if the gear did not apply, check failed-closed component entries for `WA_MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE`

## Recipe 9: Add Mutation Visuals (Outline + Vanilla Particles) Without Gameplay Potions

Use this when:
- you want a clear mutation outline tell that works on awkward mob shapes
- you want vanilla-looking visual flair on top of the outline
- you do not want to make custom particle art
- you want potion-style particles without applying the gameplay effect itself

```json
{
  "id": "my_pack:berserker_visuals",
  "display_name": "Berserker Visuals",
  "weight": 1,
  "eligible_entities": ["minecraft:zombie"],
  "components": [
    {
      "type": "worldawakened:movement_speed_multiplier",
      "parameters": {
        "multiplier": 1.2
      }
    },
    {
      "type": "worldawakened:glow_style",
      "parameters": {
        "color": "#66ff66",
        "brightness": 0.9,
        "see_through_walls": false,
        "pulse": false
      }
    },
    {
      "type": "worldawakened:effect_particles",
      "parameters": {
        "effect_type": "minecraft:strength",
        "color": "#66ff66",
        "count": 3,
        "interval_ticks": 10
      }
    }
  ]
}
```

Why this shape is correct:
- `movement_speed_multiplier` changes the actual mob speed
- `glow_style` adds the WA-owned silhouette outline mutation tell
- `effect_particles` reuses the vanilla mob-effect particle visual only
- no gameplay potion effect is applied to the mob

Direct particle example:

```json
{
  "type": "worldawakened:ambient_particles",
  "parameters": {
    "particle": "minecraft:dust",
    "color": "#33ff66",
    "size": 0.8,
    "count": 4,
    "offset_x": 0.3,
    "offset_y": 0.6,
    "offset_z": 0.3,
    "speed": 0.01,
    "interval_ticks": 8
  }
}
```

Notes:
- use `glow_style` as the primary mutation readability tell
- use `effect_particles` for vanilla potion-style visuals
- use `ambient_particles` for direct simple particle emission
- use `/wa mob inspect` to confirm active `glow_style` and particle visual state on the mutated mob

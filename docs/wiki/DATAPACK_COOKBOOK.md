# Datapack Cookbook

Copyable authoring patterns with plain-language explanation.

- Document status: Active human-friendly authoring cookbook
- Last updated: 2026-03-12
- Scope: Common trigger, rule, and ascension patterns

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

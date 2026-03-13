# World Awakened Phase 4 Test Pack

Deterministic datapack for validating Phase 1-4 behavior:

- manual debug trigger unlocks one test stage
- one world rule proves Phase 3 single-pass behavior
- one ascension offer auto-grants on stage unlock
- four additional ascension offers stay command-driven for queue, invalid-selection, and reward-repeat tests

## Install

Copy `worldawakened_phase4_test` into your world's `datapacks/` folder, then run:

```text
/reload
/wa reload validate
```

## Key Commands

```text
/wa stage list global
/wa trigger fire wa_test:unlock_phase4_gate player <player>
/wa dump active_rules global
/wa ascension grant_offer <player> wa_test:starter_path
/wa ascension grant_offer <player> wa_test:repeat_all_path
/wa ascension grant_offer <player> wa_test:repeat_block_path
/wa ascension pending <player>
/wa ascension inspect <player>
/wa ascension open <player>
/wa ascension choose <player> "<instance_id>" wa_test:tempered_core
```

## Expected Behavior

Phase 1-3 verification:

1. Before the first trigger:
   - `/wa stage list global` shows `wa_test:phase4_gate` as locked
   - `/wa dump active_rules global` shows `wa_test:phase4_rule_consumes_once` as `eligible=false consumed=false`
2. First `/wa trigger fire wa_test:unlock_phase4_gate player <player>`:
   - reports `unlocks=1`
   - reports `rules_matched=0 rules_executed=0`
   - this is the single-pass proof: the same event that unlocked the stage did not re-evaluate rules against the post-action stage state
3. After the first trigger:
   - `/wa stage list global` shows `wa_test:phase4_gate` as unlocked
   - exactly one pending offer should exist: `wa_test:starter_path`
4. On a later inspection pass:
   - `/wa dump active_rules global` may already show `wa_test:phase4_rule_consumes_once` as `consumed=true`
   - that is valid once a subsequent evaluation pass observes the unlocked stage and runs `mark_rule_consumed`
5. Second `/wa trigger fire wa_test:unlock_phase4_gate player <player>`:
   - reports `unlocks=0`
   - the rule remains consumed

Phase 4 note:

- `wa_test:starter_path` is the only offer that should auto-grant from the stage unlock.
- `wa_test:survival_path`, `wa_test:skirmisher_path`, `wa_test:repeat_all_path`, and `wa_test:repeat_block_path` are intended to be granted with `/wa ascension grant_offer ...` for deterministic queue and repeat-policy testing.
- `wa_test:starter_path`, `wa_test:skirmisher_path`, and `wa_test:repeat_block_path` use `reward_repeat_policy: "block_all"`.
- `wa_test:survival_path` uses `reward_repeat_policy: "allow_forfeited_only"` so the queue test can still surface previously forfeited rewards without ever repeating a chosen reward.
- `wa_test:repeat_all_path` uses `reward_repeat_policy: "allow_all"` so a previously chosen reward can appear again for the same player when the offer is granted later.
- `wa_test:unlock_phase4_gate` is authored with `source_scope: "player"`, so use the explicit `player <player>` trigger form when testing from operator commands.
- runtime ascension `instance_id` values may contain characters such as `:` and `|`; when typing them into commands manually, wrap them in quotes.

## Small `allow_all` Repeat Test

Assuming you already completed the base Phase 0-4 flow and chose `wa_test:tempered_core` from `wa_test:starter_path`:

1. Grant the dedicated repeat-policy test offer:

```text
/wa ascension grant_offer <player> wa_test:repeat_all_path
/wa ascension pending <player>
```

2. Confirm the pending instance for `wa_test:repeat_all_path` still lists:
   - `wa_test:tempered_core`
   - `wa_test:ember_heart`
   - `wa_test:grave_step`

3. Choose the previously chosen reward again from the new instance:

```text
/wa ascension choose <player> "<repeat_all_instance_id>" wa_test:tempered_core
```

4. Pass condition:
   - the command is accepted
   - the offer resolves normally
   - this proves `reward_repeat_policy: "allow_all"` can intentionally resurface a previously chosen reward

## Small `block_all` Comparison Test

After the `allow_all` test above:

1. Grant the dedicated block-all comparison offer:

```text
/wa ascension grant_offer <player> wa_test:repeat_block_path
/wa ascension pending <player>
```

2. Confirm the pending instance for `wa_test:repeat_block_path` does not list the previously chosen reward `wa_test:tempered_core`.

3. Pass condition:
   - the pending candidate list only shows rewards still allowed under `block_all`
   - this proves the repeat-policy difference is real and not just offer-instance dedupe

## Ascension Suppression Test (Reward + Component)

Assumed state:
- the pack is installed and reloaded (`/reload` then `/wa reload validate`)
- the player has chosen `wa_test:grave_step` as an owned reward

If needed, establish that state first:

```text
/wa ascension grant_offer <player> wa_test:survival_path
/wa ascension pending <player>
/wa ascension choose <player> <instance_id> wa_test:grave_step
```

Reward-level suppression flow:

```text
/wa ascension inspect <player>
/wa ascension suppress reward <player> wa_test:grave_step
/wa ascension inspect <player>
/wa ascension unsuppress reward <player> wa_test:grave_step
/wa ascension inspect <player>
```

Expected:
- inspect shows `wa_test:grave_step` transitioning between `active` and `suppressed`
- ownership remains present in `Chosen rewards` throughout

Component-level grouped suppression flow:

```text
/wa ascension inspect <player>
/wa ascension suppress component <player> wa_test:grave_step 0|worldawakened:movement_speed_bonus
/wa ascension inspect <player>
/wa ascension unsuppress component <player> wa_test:grave_step 1|worldawakened:night_vision_passive
/wa ascension inspect <player>
```

Expected:
- `grave_step` exposes suppressible component keys for both movement and night vision
- because both components share grouped suppression metadata, suppressing either key suppresses the full group
- inspect shows grouped/partial suppression state and effective suppressed component keys
- component commands also accept numeric index shorthand (`0`, `1`, ...)

# Operator Guide

Practical guide to using the `/wa` command tree for inspection, testing, recovery, and administration.

- Document status: Active human-friendly operator handbook
- Last updated: 2026-03-13
- Scope: Command usage, targeting, recovery, and testing support

---

## 1. Operator vs Debug

World Awakened has two command layers.

### Operator commands

Use these when you want to work with actual gameplay state.

Typical jobs:
- inspect current state
- advance or reverse progression
- fire a trigger intentionally
- grant or resolve an ascension offer
- recover from a wrong ascension choice

### Debug commands

Use these when you want to clear or reset persistence buckets directly.

Typical jobs:
- wipe global stage/rule/trigger state for testing
- clear one player's trigger history
- clear one player's ascension runtime instances
- perform a broad clean reset for development

If you are unsure which layer to use:
- operator commands run the system
- debug commands clean the system

## 1A. Understand Output Layers

World Awakened command output is intentionally split into layers.

### Player-facing notifications

These are the lines normal players should see during play.

Rules:
- short
- readable
- display-name-first
- no raw runtime IDs unless the surface is explicitly an inspect/debug surface

### Operator command feedback

These are the lines you get back from normal `/wa` control commands.

Rules:
- concise by default
- enough information to understand what happened and what to do next
- may include copy/suggest actions where they help operator workflows

### Inspect and debug output

These are the dense surfaces.

Examples:
- `/wa ascension inspect`
- `/wa dump active_rules`
- `/wa debug ...`

Rules:
- raw IDs are expected here
- provenance and reason paths belong here
- this is the right place for deep diagnosis

### `general.debug_logging = true`

This setting adds extra raw detail to normal operator output.

It should:
- keep the concise operator line
- add the raw line after it

It should not:
- turn ordinary player-facing notifications into debug spam

## 2. Understand Targeting First

Most operator mistakes come from targeting the wrong thing.

### `global`

Targets the shared save-wide progression bucket.

Use it for:
- shared stage inspection
- shared trigger tests
- shared rule inspection

Examples:

```text
/wa stage list global
/wa dump active_rules global
```

### `player <player>`

Targets one bound player context and that player's persistence where relevant.

Use it for:
- per-player progression inspection
- player-scoped trigger testing
- ascension commands

Examples:

```text
/wa stage list player Dev
/wa trigger fire wa_test:unlock_phase4_gate player Dev
/wa ascension inspect Dev
```

### `dimension <dimension_id>`

Available on manual trigger fire and rule inspection paths.

Use it when you want to test dimension-sensitive logic from a controlled command path.

Examples:

```text
/wa trigger fire my_pack:test_trigger global dimension minecraft:the_nether
/wa dump active_rules player Dev dimension minecraft:the_end
```

This only changes world-context evaluation for that command pass.
It does not move saved state into another bucket.

## 3. Baseline Inspection Commands

Use these before changing anything.

```text
/wa reload validate
/wa stage list global
/wa stage list player Dev
/wa dump active_rules global
/wa dump active_rules player Dev
/wa ascension pending Dev
/wa ascension inspect Dev
```

What these tell you:
- whether content loaded cleanly
- which stages are unlocked
- which rules are currently active or blocked
- whether the player has pending or resolved ascension instances
- which WA-owned carriers are currently active on the player, including client-visual carriers such as passive night vision that now drive a lightmap-backed client render path

## 4. Baseline Mutation Commands

### Stages

```text
/wa stage unlock <id> global
/wa stage lock <id> global
/wa stage unlock <id> player Dev
/wa stage lock <id> player Dev
```

Use these when you want to move stage state directly.

### Triggers

```text
/wa trigger fire <id> global
/wa trigger fire <id> player Dev
/wa trigger fire <id> global dimension minecraft:the_nether
/wa trigger fire <id> player Dev dimension minecraft:the_end
```

Use these when you want to test the authored trigger path rather than forcing state manually.

### Ascension

```text
/wa ascension grant_offer Dev <offer_id>
/wa ascension open Dev
/wa ascension choose Dev <instance_id> <reward_id>
/wa ascension active Dev <reward_id>
/wa ascension suppress reward Dev <reward_id>
/wa ascension unsuppress reward Dev <reward_id>
/wa ascension suppress component Dev <reward_id> <component_key>
/wa ascension unsuppress component Dev <reward_id> <component_key>
/wa ascension reconcile Dev
/wa ascension revoke Dev <reward_id>
/wa ascension reopen Dev <instance_id>
/wa ascension clear Dev <instance_id>
```

Use these when you want to work with actual offer instances.

## 5. Which Ascension Command Should I Use?

### `grant_offer`

Use when you want to create a pending offer by command.

### `choose`

Use when you want to resolve a pending offer instance.

It uses `instance_id`, not offer template ID, because selection must be exact.
Runtime `instance_id` values are short opaque command-safe IDs such as `wao_ab12cd34`, so you can type them directly when needed.

### `active`

Use when you want to resolve the currently active pending offer without typing the runtime `instance_id`.

### `suppress reward` / `unsuppress reward`

Use when a player should keep ownership of a chosen reward but temporarily stop or re-enable its live effects.

### `suppress component` / `unsuppress component`

Use when a reward component is explicitly authored as component-suppressible and you want partial suppression without disabling the whole reward.

Notes:
- component keys come from `/wa ascension inspect <player>` and command suggestions
- inspect/suggestions emit canonical component keys in `index|namespace:component_type` form (example: `0|worldawakened:movement_speed_bonus`); index-only shorthand like `0` is also accepted
- component-level suppression policy must be authored with `suppressible_individually=true`
- grouped suppression metadata can cause one component command to affect multiple linked component keys

### `reconcile`

Use when you want to force a fresh ascension projection pass immediately (for example after datapack reload or suppression changes).

This is the fastest operator path in v1.

### `revoke`

Use when you need to remove a chosen reward and reverse the associated resolved offer history where applicable.

### `reopen`

Use when a player should get the same offer instance back as pending.

### `clear`

Use when you want an instance gone entirely without reopening it.

## 6. Debug Reset And Clear Commands

These exist for development, testing, and targeted state surgery.

### Broad reset

```text
/wa debug reset global stages
/wa debug reset global triggers
/wa debug reset global rules
/wa debug reset global all
/wa debug reset player Dev stages
/wa debug reset player Dev triggers
/wa debug reset player Dev rules
/wa debug reset player Dev ascension
/wa debug reset player Dev all
```

Use reset when you want to clear a whole persistence section.

### Surgical clear

```text
/wa debug clear global stage <id>
/wa debug clear global trigger <id>
/wa debug clear global rule <id>
/wa debug clear player Dev stage <id>
/wa debug clear player Dev trigger <id>
/wa debug clear player Dev rule <id>
/wa debug clear player Dev ascension_instance <instance_id>
```

Use clear when you know exactly which entry should be removed.

Important:
- the `/wa debug` tree exists only when `general.enable_debug_commands = true`
- do not build normal gameplay or operator workflows around debug commands
- non-debug operator outputs should stay concise by default
- use `general.debug_logging = true` when you want operator commands to append extra raw IDs and reason codes without switching fully into inspect/debug flows

## 7. Recommended Testing Sequence

For deterministic testing:
1. `/wa reload validate`
2. inspect starting state
3. fire a trigger if you want authored behavior
4. inspect again
5. resolve ascension by runtime `instance_id` or use `/wa ascension active <player> <reward_id>`
6. use operator recovery if the runtime path is the thing under test
7. use debug reset only when you want a broader clean rerun

This keeps the test closer to real gameplay.

## 8. Common Recovery Patterns

### Undo a shared stage unlock

```text
/wa stage lock <stage_id> global
```

Use when you want to reverse the stage state itself.

### Undo one player's bad ascension choice

```text
/wa ascension reopen Dev <instance_id>
```

Use when the player should be able to choose again.

### Remove a bad ascension instance entirely

```text
/wa ascension clear Dev <instance_id>
```

Use when the instance should disappear instead of being reopened.

### Temporarily disable one owned reward without revoking ownership

```text
/wa ascension suppress reward Dev <reward_id>
/wa ascension inspect Dev
/wa ascension unsuppress reward Dev <reward_id>
```

Use when the player should keep the reward choice permanently but pause live effects.

### Clean-room development reset

```text
/wa debug reset global all
/wa debug reset player Dev all
```

Use when you want a fast fresh-state development loop.

## 9. Common Command Mistakes

### Mistake: using `global` on a player-scoped trigger

Symptom:
- `evaluated=1, matched=0`

Why:
- the trigger exists, but it requires a player source

Fix:
- use `player <player>` for the trigger fire command

### Mistake: expecting `dimension` to select a different save bucket

Why it is wrong:
- `dimension` only changes the world-context evaluation for the command pass

Fix:
- use `global` or `player <player>` to choose the target bucket
- use `dimension` only to test dimension-sensitive conditions

### Mistake: using offer template ID where an instance ID is required

Symptom:
- choose fails or hits the wrong assumption

Fix:
- use `/wa ascension active Dev <reward_id>` first when you do not care which exact runtime instance key is pending
- inspect pending offers when you need the exact runtime `instance_id`

## 10. Practical Rule Of Thumb

Use the most explicit form of the command that matches what you are trying to test.

Good:
- `/wa stage list global`
- `/wa trigger fire my_pack:test player Dev dimension minecraft:the_nether`
- `/wa dump active_rules player Dev`

Less good for serious testing:
- shorthand variants that rely on fallback or implied context

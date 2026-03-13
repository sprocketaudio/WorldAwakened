# Troubleshooting

Practical checks for when World Awakened does not behave the way you expected.

- Document status: Active human-friendly troubleshooting guide
- Last updated: 2026-03-12
- Scope: Validation, command behavior, targeting mistakes, and common runtime surprises

---

## Start With These Three Questions

1. Did the datapack load?
2. Did the command target the correct bucket or player?
3. Did the authored scope match the event source you simulated?

Those three questions solve a large percentage of problems.

## Problem: `/wa reload validate` Reports Errors

What it means:
- the datapack loaded badly or partially
- the system is protecting itself by rejecting broken content

What to do:
1. read the first diagnostic carefully
2. fix that object first
3. rerun `/wa reload validate`
4. do not trust in-game behavior until validation is clean

Good habit:
- always start testing with validation clean

## Problem: `Unknown or incomplete command`

Common causes:
- wrong command shape
- outdated syntax from old notes
- the `/wa debug` tree is disabled in config

Checks:
1. confirm the command spelling and argument order
2. confirm you are using `global`, not an old alias
3. confirm `general.enable_debug_commands = true` if you are trying to use debug commands

## Problem: `evaluated=1, matched=0` On `trigger fire`

This is one of the most important diagnostics.

What it usually means:
- the trigger exists and was found
- but the authored conditions or source scope did not match the event you simulated

Most common cause:
- using `global` on a trigger authored with `source_scope: "player"`

Example:
- authored trigger requires `player`
- command used `global`
- result: the trigger evaluates but does not match

Fix:
- use `player <player>` when the trigger requires a player source

## Problem: A Stage Unlocked Globally When You Expected Personal Progress

Check these in order:
1. is config `progression.mode = "global"`?
2. did you inspect with `/wa stage list global` instead of `/wa stage list player <player>`?
3. did your authored pattern intentionally target shared stage progression?

Important:
- player-scoped trigger source does not force per-player progression
- progression mode controls where stage state is stored

## Problem: A Trigger Used `player`, But The Stage Still Changed Globally

This is valid when progression mode is `global`.

Why it happened:
- `player` described the event source
- `global` described the stage persistence bucket

Read [CONCEPTS.md](CONCEPTS.md) if this distinction is the confusing part.

## Problem: The Ascension Offer Did Not Appear

Check these in order:
1. confirm ascension is enabled by feature gates/config
2. confirm the player is actually eligible for the offer conditions
3. confirm the stage state the offer checks is really unlocked in the correct bucket
4. confirm you inspected the correct player
5. confirm the offer instance was not already granted, resolved, or blocked by prior state

Useful commands:

```text
/wa ascension pending Dev
/wa ascension inspect Dev
/wa stage list global
/wa stage list player Dev
```

## Problem: I Chose A Reward, But Want To Reverse It

Use the operator path that matches the recovery you want.

### Give the player the same offer again

```text
/wa ascension reopen Dev <instance_id>
```

### Remove the instance entirely

```text
/wa ascension clear Dev <instance_id>
```

### Broad clean reset for development

```text
/wa debug reset player Dev ascension
```

## Problem: I Revoked Something, But State Still Feels Wrong

Check whether you used the right layer.

- operator commands reverse real gameplay state in targeted ways
- debug commands clear saved buckets directly

If the goal is a clean development rerun, use debug reset.
If the goal is to recover one real player state, use operator recovery first.

## Problem: `choose` Fails Or Seems Awkward

Check whether you used an offer template ID instead of a runtime `instance_id`.

Correct flow:
1. inspect pending offers
2. copy the runtime `instance_id`
3. choose using that exact opaque runtime ID if you are typing it manually

## Problem: The Same Event Did Not Re-Run A Rule Immediately

This is usually expected.

World Awakened is snapshot-based and single-pass.
That means a stage unlocked during one pass is generally observed by dependent rules on a later pass.

What to do:
- inspect state after the triggering action
- then perform a later inspection or later event
- expect the follow-up rule behavior then

## Problem: I Need A Clean Test Loop Fast

Use a reset pattern explicitly.

For shared/global testing:

```text
/wa debug reset global all
```

For one player's development state:

```text
/wa debug reset player Dev all
```

If the entire world is now conceptually polluted, it may still be faster to use a new world.

## Problem: I Am Not Sure Whether The Bug Is In The Pack Or The Framework

Use this divide:

Likely datapack problem:
- validation errors
- wrong `source_scope`
- wrong condition parameters
- wrong stage/reward/offer IDs
- wrong command target for the authored pattern

Likely framework/runtime problem:
- validation is clean
- command target is correct
- authored scope is correct
- runtime state still lands in the wrong bucket or gives the wrong downstream outcome

When reporting an issue, include:
- the exact command used
- the exact output line
- the relevant JSON object ID
- whether progression mode is `global` or `per_player`

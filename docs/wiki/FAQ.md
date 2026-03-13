# FAQ

Short answers to the questions people actually ask.

- Document status: Active human-friendly FAQ
- Last updated: 2026-03-13
- Scope: Common operator and author questions

---

## Does `global` mode mean every trigger should be `world` scoped?

No.

`global` mode controls where stage progression is stored.
It does not force every trigger to use `source_scope: "world"`.

A player-scoped trigger can still unlock a shared global stage.

## Then What Does `source_scope` Control?

It controls what kind of event source the trigger expects.

- `player`: requires a player source
- `world`: treats the trigger as a world/global trigger context

It also affects where trigger-specific state like one-shots and cooldowns lives.

## What Is The Difference Between `global` And `player <player>` On Commands?

Those command targets choose the persistence bucket or bound player context.

- `global`: shared save-wide bucket
- `player <player>`: one player's bucket/context

## Why Can A Player-Scoped Trigger Still Change Global Progression?

Because event source and persistence target are separate concepts.

A trigger can say:
- this event came from a player

while progression mode says:
- the resulting stage state is shared globally

That is a normal pattern.

## Why Is Ascension Player-Scoped Even In Global Progression?

Because that is the v1 design.

Shared world progression can make a player eligible for an ascension offer, but the offer, choice, and reward remain owned by that player.

## When Should I Use `world`-Scoped Triggers?

Use them when:
- the milestone should be shared
- one-shots/cooldowns/counters should be shared
- the downstream action is world-owned rather than player-owned

Classic example:
- first player enters the Nether and unlocks one shared stage for the whole save

## When Should I Use `player`-Scoped Triggers?

Use them when:
- the trigger needs a real player source
- a player-specific downstream action needs a bound player
- trigger history should be independent per player
- the pattern is meant to feed ascension or player messaging

## What Does `reward_repeat_policy` Actually Do?

It controls whether previously forfeited rewards may show up again in later offers for that player.

- `block_all`: previously chosen and previously forfeited rewards stay blocked later
- `allow_forfeited_only`: previously chosen rewards stay blocked, but previously forfeited rewards may appear again later
- `allow_all`: previously chosen and previously forfeited rewards may both appear again later

## What Does `dimension <dimension_id>` Actually Do?

It changes world-context evaluation for that one command pass.

It does not:
- create a different save bucket
- move progression into another dimension
- change `global` into `player` or vice versa

## Why Does Ascension `choose` Use `instance_id` Instead Of Offer ID?

Because the runtime needs an exact offer instance, not just the template definition.

That avoids ambiguity and makes operator recovery safer.

Runtime `instance_id` values are generated as opaque command-safe IDs now, so you can type them directly when you need the exact instance.

If you do not care about the exact runtime key, use:

```text
/wa ascension active <player> <reward_id>
```

That command resolves the currently active pending offer for you.

## Why Do Some Command Outputs Look Clean While Others Look Dense?

Because the command surface is layered on purpose.

- player-facing notifications should stay short and readable
- operator command feedback should stay concise by default
- inspect/debug surfaces are where the dense raw IDs and reason paths belong

If `general.debug_logging = true`, normal operator commands may append an extra raw-detail line.

That setting should add detail to operator output.
It should not turn ordinary gameplay notifications into debug spam.

## Why Does The Framework Ship Without Built-In Gameplay Content?

Because the project is meant to be a modular framework.

The mod jar should not impose gameplay-active content by default.
Gameplay comes from installed datapacks.

## Where Should I Look First When Something Feels Wrong?

In this order:
1. [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. [CONCEPTS.md](CONCEPTS.md)
3. [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
4. technical docs in `docs/` if you need exact contract wording

## Will This Wiki Stay Updated?

It is supposed to.

The repository governance now requires the human-friendly wiki layer to be updated alongside technical docs when behavior, workflows, testing patterns, or common failure modes change.

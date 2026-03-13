# Core Concepts

Plain-language explanation of how World Awakened thinks about progression, triggers, rules, and ascension.

- Document status: Active human-friendly concept guide
- Last updated: 2026-03-12
- Scope: Mental model for operators and datapack authors

---

## The Short Version

World Awakened is a framework that watches events, checks authored rules, and applies authored outcomes.

The most useful way to think about it is:
1. Something happened.
2. The framework decided what kind of event it was.
3. The framework looked at the authored datapack definitions that care about that event.
4. Matching definitions mutated stage state, counters, offers, or later systems.

## The Four Questions That Matter

When something behaves in a surprising way, ask these four questions:

1. What happened?
   Example: a player entered the Nether, a boss died, a command fired a manual debug trigger.
2. Who was the source?
   Example: was this treated as a `player` event or a `world` event?
3. Where does the result get stored?
   Example: shared global progression state or one player's progression state?
4. Who receives the downstream outcome?
   Example: everyone shares the unlocked stage, but only one player gets an ascension offer.

Those four questions explain most confusing behavior.

## Progression Mode: Global vs Per-Player

Progression mode answers one question only:

Where is stage progression stored?

### `global`

Use `global` when the whole save shares one progression state.

Examples:
- the first player to reach the Nether unlocks a stage for everyone
- a shared boss milestone opens the next difficulty tier for the whole server
- stage-driven rules should look at one shared stage timeline

### `per_player`

Use `per_player` when each player should have their own progression state.

Examples:
- each player earns their own unlocks
- stage-driven rewards should depend on individual progress
- a co-op server wants personal progression rather than a shared campaign

Important: progression mode does not decide what kind of event happened. It decides where stage state is persisted.

## Trigger `source_scope`: World vs Player

Trigger `source_scope` answers a different question:

What kind of event source must this trigger match?

### `player`

Use `player` when the trigger needs a real player source.

Examples:
- a player entered a dimension
- a specific player completed an advancement
- a player-scoped action such as `grant_ascension_offer` needs a bound player

### `world`

Use `world` when the trigger should be treated as a shared world/global event.

Examples:
- any player's action should advance one shared world state
- one-shot or cooldown behavior should be shared across the whole save
- the trigger should increment global counters rather than player counters

Important: `source_scope` does not decide whether stage progression is global or per-player. It decides how the event is matched and where trigger-specific state like one-shots and cooldowns live.

## Rule `execution_scope`

Rules are separate from triggers.

A trigger answers: should this event cause something to happen right now?

A rule answers: given the current state, which authored logic is active for this scope?

### Common rule scopes

- `world`: inspect and mutate shared world-owned state
- `player`: inspect and mutate player-owned state
- `entity`: inspect an entity-specific context
- `spawn_event`: inspect a spawn-path context before or during spawn-time mutation logic

In practice:
- triggers react to events
- rules express ongoing or follow-up logic

## One Of The Most Important Distinctions

A player-scoped trigger can still mutate global progression.

Example:
- the trigger requires a player source
- the action is `unlock_stage`
- progression mode is `global`
- result: the stage unlock is shared globally even though the event source was a player

This is valid and common.

It is the right pattern when:
- one player's action should advance shared progression
- but downstream player-owned systems still need to know which player caused the event

## Why Ascension Still Stays Player-Scoped

Ascension offers and rewards are player-owned, even when world progression is shared.

That means:
- a shared global stage can unlock because of one player's action
- that unlock can make one player eligible for a personal ascension offer
- the choice and reward stay tied to that player

This is intentional.

The framework does not make ascension world-global in v1.

## Offer Template vs Runtime Offer Instance

This distinction matters a lot in Phase 4 testing.

### Offer template

The datapack definition.

Example:
- `wa_test:starter_path`

### Runtime offer instance

The actual pending or resolved offer saved on a player.

Examples:
- one player may have a pending instance of `wa_test:starter_path`
- later that same player may have a resolved instance created from the same offer template

Why the commands use `instance_id` for selection:
- one offer template can theoretically exist more than once over time
- choosing by template ID becomes ambiguous
- choosing by runtime instance ID is precise and operator-safe

## Persistence Buckets

World Awakened stores different things in different buckets.

### Global bucket

Shared for the save:
- global stage progression
- world-scoped trigger cooldowns and one-shots
- world-scoped rule state

### Player bucket

Owned by one player:
- per-player stage progression when in `per_player` mode
- player-scoped trigger cooldowns and one-shots
- player-scoped rule state
- ascension offers, choices, forfeits, and rewards

When a command asks for `global` or `player <player>`, it is choosing a persistence bucket.

## Command Target vs Dimension Override

These are also separate ideas.

### Command target

This picks the persistence bucket or bound player.

Examples:
- `/wa stage list global`
- `/wa stage list player Dev`

### `dimension <dimension_id>` override

This changes only the world-context evaluation for that command pass.

Examples:
- `/wa trigger fire <id> global dimension minecraft:the_nether`
- `/wa dump active_rules player Dev dimension minecraft:the_end`

It does not move saved data into another bucket.
It does not create a separate progression state per dimension.
It only changes context for that one command evaluation.

## Operator Commands vs Debug Commands

These are meant for different jobs.

### Operator commands

Use these to inspect, advance, reverse, grant, or reopen actual gameplay state.

Examples:
- stage unlock/lock
- trigger fire
- ascension grant, choose, reopen, clear, revoke

### Debug commands

Use these to surgically clear persistence buckets or reset broad sections of state.

Examples:
- reset global rules
- clear one player trigger entry
- wipe one player's ascension state for testing

A good rule:
- use operator commands to run the system
- use debug commands to clean up or reset the system

## Single-Pass Rule Behavior

World Awakened does not keep re-running the same event pass until everything settles.

That means:
- a trigger can unlock a stage now
- a rule that depends on that stage usually becomes visible on the next evaluation pass, not in the same pass

This is why testing often looks like:
1. fire trigger
2. inspect new state
3. perform a later inspection or event
4. observe the rule now consuming or activating

That behavior is intentional and deterministic.

## The Mental Model To Keep

If you remember only one sentence, remember this:

World Awakened separates event source, persistence target, and downstream ownership.

Those are not the same thing.

That one separation explains most of the framework.

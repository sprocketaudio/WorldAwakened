# FAQ

Short answers to the questions people actually ask.

- Document status: Active friendly FAQ
- Last updated: 2026-10-19
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

## Why Is `current_dimension` Not An Extra Condition Option On `player_enter_dimension`?

Because that would be redundant.

In guided flow, the trigger preset field already captures dimension matching directly.
The additional-conditions list is reserved for extra gates that are not already represented by the preset fields.

## Can I Filter `item_crafted` By Exact Recipe Or `block_placed`/`block_broken` By Exact Block?

Not yet.

Those trigger types are implemented, but exact recipe/item/block condition matching is not currently implemented in trigger-rule runtime matching.

Current practical behavior:
- `item_crafted` matches crafted-item events
- `block_placed` matches placed-block events
- `block_broken` matches broken-block events

Use stage and dimension conditions as extra gating for now.

## Why Keep `boss_killed` If `entity_killed` Exists?

`boss_killed` is the intent-first option.

Use it when you want boss-only progression behavior without manually authoring entity ID/tag lists for every possible boss.
Use `entity_killed` when you want specific entity targeting.

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

## In Guided Builder, How Do I Create Multi-Reward Ascension Offers?

Use ascension `Create new offer` mode and add multiple reward candidates in the step.

Guided mode will create:
- one reward object per candidate
- one offer that references all candidate rewards
- one rule that grants that offer after the stage unlocks

Use the existing-offer path when you want to link a pre-authored offer instead of generating one in the flow.

`Choices shown` controls how many candidate rewards appear each time the offer is presented.
If `choices shown` is lower than candidate count, candidates are sampled using each reward's `selection weight`.

## Why Do Ascension Reward Conditions Not Show Every Condition Type In Guided Mode?

Guided mode intentionally shows a player/world-focused subset for ascension reward gating.

That avoids misleading options that are entity/spawn-event scoped (for example `Entity Tag`) which usually do not make sense for offer/reward eligibility in guided ascension authoring.

Advanced mode still allows full condition editing when you intentionally need broader condition types.

## Are All Ascension Reward Components Live Runtime Effects?

Not all registered ascension component IDs currently have a live runtime application surface in v1.

Guided mode should focus on live-supported component types for normal authoring.
If you load older or advanced content with non-live component IDs, keep them in advanced mode and verify runtime behavior via debug/inspection before shipping.

## Does Suppressing A Reward Let Players Re-Choose Or Undo Ownership?

No.

Suppression is only a live-effect toggle for already owned rewards/components.
Ownership, exclusivity, and forfeiture history stay permanent.

## How Do Mutator Caps And Overrides Work?

There are two mutator cap layers, and the lower result wins.

- the live global settings cap is a server safety ceiling
- every pool must define `max_mutators_per_entity`

Resolution order:
1. read the required pool maximum
2. clamp it to the live global cap and hard spawn-time budget

Practical use:
- keep the global default conservative
- raise caps only on specific pools where you intentionally want stronger rolls

## Why Did A Selected Mutation Pool Still Not Mutate The Mob?

Because pool selection and mutation application are separate steps.

- pool weight decides which eligible pool wins
- selected pool `mutation_chance` decides whether mutation proceeds

`mutation_chance` is required in the unified pool schema.

- `1.0`: always mutate after selection
- `0.0`: never mutate
- values between `0.0` and `1.0`: deterministic per-evaluation roll

After that first roll succeeds, `additional_mutation_chance` is rolled conditionally for each further slot. A maximum of one disables additional attempts. In curated mode, eligible combinations are chosen as whole units; in any-compatible mode, further distinct compatible mutators use the ordinary weight table.

Use `/wa debug mutators evaluate <entity_id>` and check:
- `selected_pool`
- `chance_result`
- `final_outcome` (`chance_failed` means chance gate blocked mutation)

## Do Spawner And Trial Spawner Mobs Get Mutated By Default?

No.

Mutation pools skip both sources unless the pool explicitly opts in.

Use these pool fields when you want to allow them:
- `allow_from_spawner`
- `allow_from_trial_spawner`

That keeps common modpack grinders and farm setups from getting unexpectedly buffed.

## How Does `per_player` Mode Affect Spawn-Time Mutators?

Spawn-time mutator evaluation is attributed to the nearest nearby non-spectator player.

Practical result:
- a mob near the progressed player can mutate from that player's unlocked stages
- a mob near a player who has not passed the gate should not mutate from another player's progress
- if no nearby player can be attributed, the mutator pass fails closed and the mob stays unmutated

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

## What Does `offer_repeat_policy` Do?

It controls whether the same offer definition may create more than one runtime offer for one player.

- `once_per_player` is the normal setting. A pending or resolved instance blocks another grant, even when another source tries to grant it.
- `once_per_source` is an advanced repeatable setting. The same source is still idempotent, but a distinct source key may create another runtime instance.

Closing the Ascension screen does not consume a pending offer and does not make it eligible to be granted again.

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

## What Is The Difference Between `revoke` And `suppress`?

- `revoke`: removes the chosen reward ownership and reopens/rewrites offer history paths.
- `suppress`: keeps ownership permanent and only pauses live application.

Use suppression first when the player wants a temporary pause.

## Why Do Some Command Outputs Look Clean While Others Look Dense?

Because the command surface is layered on purpose.

- player-facing notifications should stay short and readable
- operator command feedback should stay concise by default
- inspect/debug surfaces are where the dense raw IDs and reason paths belong

If `general.debug_logging = true`, normal operator commands may append an extra raw-detail line.

That setting should add detail to operator output.
It should not turn ordinary gameplay notifications into debug spam.

## Why Did A Passive Reward Component Validate But Not Apply Live?

Because ascension reconciliation is additive-first.

World Awakened only clears and reapplies World Awakened-owned player modifiers/effects.
It does not wipe or normalize shared vanilla or modded player effects just to make a reward fit.

If a component has a WA-owned carrier implementation, World Awakened uses that owned carrier instead.
If it does not, the component fails closed and logs a diagnostic instead of touching unrelated player state.

Current examples:
- `fire_resistance_passive` uses a WA-owned server runtime carrier
- `night_vision_passive` uses a WA-owned client visual carrier

The night-vision path is important: World Awakened does not inject the vanilla `NIGHT_VISION` effect just to get the visuals.
It keeps the state on a WA-owned carrier and drives the owning client's lightmap and fog visuals directly instead.

## Does World Awakened Do Anything Without A Custom Datapack?

Yes. It ships a conservative Standard profile through the normal datapack engine.

- eligible natural Overworld hostiles can receive a mild mutation after day 3 at a 2% chance
- milestone ranks grow to provisional post-Dragon rates of 13%/16%/18% in the Overworld/Nether/End
- S4+ adds explicitly weighted rare two-mutator pools; ordinary pools remain single-mutator
- three personal milestones offer one permanent choice from three conservative Ascension rewards
- mutated kills can add bounded materials, and later stages enable infrequent pressure events without wave spawning
- bundled pools exclude bosses and spawner/trial-spawner mobs

Custom datapacks remain supported. Reuse the exact bundled namespace/path to replace an object, or use a different ID to add another object. Be deliberate: additive pools can change which eligible pool is selected.

## Where Is The Configuration Screen?

Run `/wa config` in game as an operator or singleplayer owner. The server-authoritative dashboard exposes Global Settings, World Phases, Mob Mutations, Loot, and Ascension. You can search actual pools and server living mobs, author supported mutators and permanent Ascension choices, validate/apply a world override, or restore a definition to its datapack source.

World Phases is the normal progression editor. It creates and orders phases with supported milestones, dimensions, and phase-linked mob configurations. Choose Start Empty for a profile without bundled gameplay or Copy Standard for an independent editable copy. These operations retain saved stage history and do not rewrite external datapacks.

The editor deliberately keeps advanced rule authoring and unsupported component handlers in datapacks. Standard is the only bundled profile; World Phases, supported mutators, Ascension rewards/offers, and Loot profiles are available in the native editor. The former hosted web editor remains retired.

## Where Should I Look First When Something Feels Wrong?

In this order:
1. [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
2. [CONCEPTS.md](CONCEPTS.md)
3. [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
4. technical docs in `docs/` if you need exact contract wording

## Will This Wiki Stay Updated?

It is supposed to.

The repository governance now requires the friendly wiki layer to be updated alongside technical docs when behavior, workflows, testing patterns, or common failure modes change.

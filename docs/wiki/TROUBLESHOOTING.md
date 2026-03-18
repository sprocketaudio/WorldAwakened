# Troubleshooting

Practical checks for when World Awakened does not behave the way you expected.

- Document status: Active human-friendly troubleshooting guide
- Last updated: 2026-03-18
- Scope: Validation, command behavior, targeting mistakes, and common runtime surprises

---

## Start With These Three Questions

1. Did the datapack load?
2. Did the command target the correct bucket or player?
3. Did the authored scope match the event source you simulated?

Those three questions solve a large percentage of problems.

## Controlled Verification Ladder

Use this order whenever command-driven verification surfaces are available:
1. use `inspect` first to confirm current state
2. use `evaluate` before `force`
3. use `force` before `live_test`
4. if natural gameplay and controlled outputs diverge, compare candidate/rejection summaries to isolate mismatch

Why this order works:
- `inspect` tells you what the runtime believes right now
- `evaluate` tests eligibility logic without mutating gameplay state
- `force` isolates one pool/profile/provider while still enforcing policy/safety rules
- `live_test` confirms end-to-end behavior on the real runtime path

Mutators and spawn checks:
- use `/wa debug mutators evaluate ...` for candidate narrowing
- use `/wa debug mutators force_pool ...` for pool-level debugging
- use `/wa debug mutators force_mutator ...` for mutator/component debugging
- use `/wa debug spawn test ...` for end-to-end spawn-path verification
- check the `spawn_context` line on debug output for spawn origin and attributed player
- check `chance_result` to distinguish chance gating from selector/condition/budget failures
- use `/wa mob inspect` to confirm provenance (`pool`, `mutators`, `components`, `stage context`, `trace ID`, `depth/origin`)

Practical targeting note:
- `/wa mob inspect` with no target uses the mob under the executing player's crosshair
- if that is inconvenient or you are using console/automation, use `/wa mob inspect <target>` instead

Pressure, difficulty, and challenge checks:
- use `/wa debug pressure last` to inspect the latest runtime-captured spawn-pressure snapshot
- use `/wa debug pressure replay <id>` to replay a specific captured spawn-pressure snapshot
- use `/wa debug pressure evaluate ...` for manual probe/debug contexts
- use `/wa debug difficulty scalar ...` for effective-scalar composition debugging
- use `/wa difficulty ...` commands for policy, bounds, cooldown, and permission testing

Loot checks:
- use `/wa debug loot evaluate ...` for profile matching/debug
- use `/wa debug loot force_profile ...` for one-profile isolation testing
- verify the evaluated context includes canonical fields (`loot_context_type`, `target_type`, `target_id`, `loot_table_id`, `entity_is_mutated`, `mutation_tags`, `player/dimension`, stage/invasion context)
- confirm which reward contributors were gathered for the event (mutator/elite/invasion/loot-profile paths)
- if an expected second reward does not appear, check whether non-repeatable duplicate-prevention blocked the contributor/profile in that same event pass

Invasion checks:
- use `/wa debug invasion evaluate ...` for scheduler/profile debugging
- use `/wa invasion start <profile>` and `/wa invasion stop` for live end-to-end verification

Compat and integration checks:
- use `/wa debug compat evaluate ...` for integration-gate debugging
- use `/wa debug scalar provider ...` for external provider/scalar input debugging
- use `/wa compat list` and `/wa apotheosis tier inspect` for high-level activation confirmation

Web linked-session checks:
- use `/wa web session status` to confirm whether a linked editor session is active, expired, or stale
- if apply fails with revision mismatch, refresh editor state from runtime before retrying
- if a session should no longer be trusted, use `/wa web session revoke` and create a new session
- live mode selector lists should come from runtime registry metadata; if they look stale, refresh session metadata and confirm runtime connection state

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

## Problem: Linked Web Apply Fails With Revision Mismatch

What it means:
- runtime changed after your browser loaded its session snapshot
- your browser is trying to apply an older revision

What to do:
1. keep your local edits noted if needed
2. reload the linked project/session from runtime
3. re-apply your intended edits on the latest revision
4. apply again

Important:
- this is a safety guard, not random failure
- stale sessions must not overwrite newer runtime state

## Problem: Linked Web Session Shows Expired Or Stale

What it means:
- the short-lived session token expired
- or the runtime/backend link is no longer active

What to do:
1. run `/wa web session status`
2. if stale/expired, run `/wa web edit` to create a new link
3. if needed, run `/wa web session revoke` first to invalidate old links

## Problem: Live Editor Selectors Miss Modded IDs

What it usually means:
- session registry metadata is stale or incomplete
- editor fell back to offline metadata

What to do:
1. confirm linked session is active (`/wa web session status`)
2. refresh or reopen the linked editor URL
3. confirm runtime registry metadata sync completed
4. if still wrong, create a fresh linked session

Common mutator cap pitfall:
- `mutators.max_mutators_per_mob` is the global default, not a hard per-pool lock
- selected pools may override with `max_mutators_per_entity`
- if applied mutator count surprises you, inspect which pool was selected and compare that pool cap against the global TOML default

Common mutation chance pitfall:
- matching/selecting a pool does not guarantee mutation when that pool sets `mutation_chance < 1.0`
- if debug output shows `final_outcome=chance_failed`, the pool matched but chance blocked mutation

## Problem: `Unknown or incomplete command`

Common causes:
- wrong command shape
- outdated syntax from old notes
- the `/wa debug` tree is disabled in config

Checks:
1. confirm the command spelling and argument order
2. confirm you are using `global`, not an old alias
3. confirm `general.enable_debug_commands = true` if you are trying to use debug commands

## Problem: `/wa debug` Only Shows `clear` And `reset`

What it means:
- your command tree is from an older mod version
- datapack reload succeeded, but the process did not load the newer command tree

Why this happens:
- you are running an older mod jar
- or you updated data and only ran `/reload` (which does not re-register command nodes)

What to do:
1. stop the game/server
2. relaunch with the current mod build/jar
3. reconnect and run `/wa debug` again
4. confirm `mutators` and `spawn` now appear under `/wa debug`

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

## Problem: A Chosen Reward Is Annoying Right Now, But I Do Not Want To Revoke It

Use suppression instead of revoke/reopen:

```text
/wa ascension suppress reward Dev <reward_id>
/wa ascension inspect Dev
/wa ascension unsuppress reward Dev <reward_id>
```

If only one component should pause:

```text
/wa ascension suppress component Dev <reward_id> <component_key>
/wa ascension unsuppress component Dev <reward_id> <component_key>
```

Notes:
- suppression keeps ownership permanent
- suppression changes only live WA-owned projection state
- `/wa ascension inspect` shows `active`, `suppressed`, `partially_suppressed`, and suppression rejection states

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

## Problem: A Reward Is Saved But Its Effect Is Not Live

What it usually means:
- the reward is still part of saved ownership state
- but one or more live WA-owned projections failed closed

What to check:
1. run `/wa ascension inspect <player>`
2. look at:
   - `Chosen rewards` for saved ownership
   - `Active owned carriers` for carrier state
   - `Live WA-owned modifiers` for live attribute projections
   - `Failed-closed reward components` for the degraded branch
   - suppression state lines for active vs suppressed vs rejected suppression branches
3. verify the referenced reward definition still exists after reload
4. check whether the missing branch depends on an attribute, carrier, visual path, or optional integration that is unavailable in the current modpack

Important:
- World Awakened keeps the saved reward ownership record
- it does not wipe foreign state just because one WA-owned branch failed closed

## Problem: A Reward Component Was Skipped Because The Runtime Surface Is Missing

What it means:
- the component needs a runtime surface that is not available right now

Examples:
- missing player attribute
- missing gameplay carrier
- missing visual carrier
- missing compat/provider hook

What to do:
1. inspect the failed-closed section in `/wa ascension inspect <player>`
2. check diagnostics for codes such as:
   - `WA_PLAYER_ATTRIBUTE_SURFACE_MISSING`
   - `WA_REWARD_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE`
   - `WA_GAMEPLAY_CARRIER_UNAVAILABLE`
   - `WA_VISUAL_CARRIER_UNAVAILABLE`
3. confirm the required attribute/effect/capability/channel actually exists in this modpack
4. confirm World Awakened has a WA-owned carrier for that behavior class

## Problem: It Works In One Modpack But Is Skipped In Another

What it means:
- the component depends on an optional compat-sensitive runtime surface
- that surface exists in one environment but not the other

Optional versus required behavior:
- core World Awakened framework behavior is still expected to work without optional compat surfaces
- optional runtime-surface branches are allowed to fail closed when their required surface is missing
- missing optional surfaces should not break unrelated systems

How to identify the missing surface quickly:
1. inspect `/wa mob inspect` or `/wa ascension inspect`
2. check diagnostics for specific surface-unavailable codes, for example:
   - `WA_RUNTIME_SURFACE_OPTIONAL_UNAVAILABLE`
   - `WA_EXTRA_SLOT_SURFACE_UNAVAILABLE`
   - `WA_COMBAT_HOOK_UNAVAILABLE`
   - `WA_CUSTOM_ATTRIBUTE_SURFACE_UNAVAILABLE`
   - `WA_BOSS_RUNTIME_SURFACE_UNAVAILABLE`
   - `WA_CLIENT_VISUAL_CHANNEL_UNAVAILABLE`
   - `WA_COMPAT_BRANCH_SKIPPED_SURFACE_UNAVAILABLE`
3. verify the integration/provider/mod exposing that runtime surface is active
4. gate or remove the compat-sensitive component branch for packs that do not provide that surface

Important:
- World Awakened intentionally preserves foreign state instead of forcing broad fallback rewrites
- branch-local fail-closed behavior is the safety contract for mixed-mod compatibility

## Problem: A Reward Component Failed Closed Because A WA-Owned Carrier Type Does Not Exist Yet

What it means:
- the framework recognized the saved reward ownership
- but there is no safe WA-owned carrier implementation for that passive behavior class yet

What to do:
1. inspect the failed-closed section
2. look for `WA_REWARD_CARRIER_TYPE_MISSING`
3. remove that component from active datapack content for now, or wait until the carrier type exists

Design note:
- this is intentional safety behavior
- World Awakened fails closed instead of borrowing shared vanilla or modded state it cannot safely own

## Problem: Component Suppression Command Is Rejected

Common causes:
- the component type is not independently suppressible
- the component entry is missing `suppressible_individually=true` for an `independent` or `grouped` suppression policy
- grouped suppression requires linked components and a `suppression_group`
- the request would create an invalid partial grouped state
- the component key does not exist on the current reward definition

What to check:
1. run `/wa ascension inspect <player>`
2. confirm `suppressible_component_keys` includes the key you used (canonical `index|namespace:component_type`, for example `0|worldawakened:movement_speed_bonus`; index-only shorthand such as `0` is also valid)
3. check suppression diagnostics for:
   - `WA_ASC_COMPONENT_NOT_SUPPRESSIBLE`
   - `WA_ASC_SUPPRESSION_GROUP_REQUIRED`
   - `WA_ASC_SUPPRESSION_INVALID_PARTIAL`
   - `WA_ASC_SUPPRESSION_TARGET_UNKNOWN`

## Problem: A Visual Passive Is Not Active

What to check:
1. confirm the reward is still in `Chosen rewards`
2. confirm the expected visual carrier is present under `Active owned carriers`
3. check `Failed-closed reward components` for `WA_VISUAL_CARRIER_UNAVAILABLE`
4. make sure the client was fully restarted if the required visual path changed in code

Important:
- visual passives use WA-owned client carriers when ownership-safe reconciliation is needed
- if that carrier path is unavailable, the component fails closed

## Problem: Mutation Provenance Exists But The Definition Is Missing After Datapack Changes

What it means:
- the saved runtime/provenance state survived
- but the datapack definition it points to no longer exists

What to do:
1. verify the mutator or reward definition still exists after reload
2. use inspect/debug surfaces to confirm the missing ID
3. restore the definition if the historical state still matters

Important:
- World Awakened does not auto-substitute a different definition
- missing definitions degrade safely and stay inspectable

## Problem: A Mutator Component Was Skipped Due To Missing Runtime Surface Or Hook

What it means:
- the mutator branch required an entity capability, hook, or runtime surface that is unavailable or incompatible in this modpack
- World Awakened failed closed for that branch instead of mutating foreign state

What to do:
1. inspect the entity with `/wa mob inspect` and look for failed-closed component/branch entries
2. check diagnostics for:
   - `WA_ENTITY_RUNTIME_SURFACE_MISSING`
   - `WA_MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE`
3. verify the required capability/hook/channel exists for the target entity and active integration set
4. for `worldawakened:equip_item`, also verify:
   - the authored `item` exists
   - the mob can use the resolved vanilla slot
   - the mob can hold the resolved hand item if the slot is `mainhand` or `offhand`
5. if the surface is unavailable in this pack, remove or gate that mutator component for this environment

Important:
- this is intentional compatibility behavior
- World Awakened preserves foreign state and skips only the affected mutator branch

## Problem: World Awakened Did Not Remove Another Mod's Modifier, Effect, Or Visual

That is usually correct behavior.

World Awakened preserves foreign state unless a documented compat contract explicitly says otherwise.

What to do:
1. inspect `/wa ascension inspect <player>`
2. look at `Foreign state intentionally preserved`
3. confirm the state you expected to disappear is actually foreign, not WA-owned

Design rule:
- World Awakened only refreshes or removes state it owns
- foreign mod and vanilla state is preserved by design

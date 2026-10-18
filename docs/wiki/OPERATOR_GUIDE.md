# Operator Guide

Practical guide to using the `/wa` command tree for inspection, testing, recovery, and administration.

- Document status: Active friendly operator handbook
- Last updated: 2026-10-18
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

Some command outputs may include clickable helper actions (for example copy ID or prefill command buttons). Those are shortcuts only; the normal typed commands still work the same way.

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
/wa trigger fire <trigger_id> player Dev
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

## 3A. Mutator Inspect Expectations

Treat `/wa mob inspect` as the first mutator diagnosis tool.

Command output is visually layered: gold lines introduce inspect/debug results, aqua text identifies fields and sections, and gray text carries detailed values. This styling changes presentation only; raw IDs remain visible and copyable.

Targeting forms:
- `/wa mob inspect` uses the mob under your crosshair when you run it as a player
- `/wa mob inspect <target>` keeps the explicit entity form for console, command blocks, and selectors such as `@e[type=minecraft:zombie,limit=1,sort=nearest]`

Minimum fields you should expect to see:
- entity type
- current/maximum health and absorption, so combat abilities can be checked before and after an event
- readable offense, defense, and mobility summaries, followed by detailed attribute base/current/delta values
- non-empty equipment slots and currently active effects
- resolved on-hit effect settings, combined lifesteal ratio, and death-explosion power/triggered state
- mutation pool
- applied mutators
- applied components
- mutation stage context
- mutation trace ID
- mutation depth or origin marker
- spawn origin and attributed progression player on debug evaluate/force/spawn-test output

If one of these fields is missing, treat that as an observability gap and investigate before trusting balancing results.

Preflight check before using mutator debug commands:
- run `/wa debug`
- confirm `mutators`, `loot`, `invasion`, and `spawn` appear as child literals
- if `/wa debug` only shows `clear` and `reset`, restart on a current mod version; datapack reload does not refresh command registration

Use these mutator debug surfaces when a spawn path is unclear:

```text
/wa debug mutators evaluate <entity_id>
/wa debug mutators force_pool <entity_id> <pool_id>
/wa debug mutators force_mutator <entity_id> <mutator_id>
/wa debug spawn test <entity_id>
```

`evaluate`, `force_pool`, and `force_mutator` report the compiled decision and do not create a live mob. Use `spawn test` when you need an entity for combat. Test-spawned mobs receive their normal vanilla command-spawn setup first, including standard weapons such as skeleton and stray bows; an authored `equip_item` component may then deliberately replace that gear. The isolated `dev_datapacks/worldawakened_combat_test` pack maps zombie, husk, creeper, skeleton, and stray to lifesteal, on-hit, death-explosion, lifesteal/death-explosion, and on-hit/lifesteal cases respectively. Its README contains the required built-in-profile toggle and exact commands.

Generated mutation names are a quick player-facing clue, but `/wa mob inspect` remains authoritative. World Awakened preserves any custom name the mob already had.

Use these operator loot surfaces for concise checks:

```text
/wa loot evaluate <target_type> <target_id> [player] [dimension]
/wa loot force_profile <profile_id> <target_type> <target_id> [player] [dimension]
```

Use these loot debug surfaces when reward profile behavior is unclear:

```text
/wa debug loot evaluate <target_type> <target_id> [player] [dimension]
/wa debug loot force_profile <profile_id> <target_type> <target_id> [player] [dimension]
```

`/wa loot ...` returns concise operator output by default and appends raw details only when `general.debug_logging=true`.
`/wa debug loot ...` always returns the dense inspect payload.
`evaluate` runs normal candidate resolution for the target context.
`force_profile` isolates one profile against the same canonical context shape for rejection-path debugging.

Use these invasion operator surfaces for live pressure-event control:

```text
/wa invasion start <profile_id>
/wa invasion stop
/wa invasion inspect active
/wa invasion inspect profile <profile_id>
```

Use this invasion debug surface for scheduler/profile eligibility checks:

```text
/wa debug invasion evaluate <profile_id> [dimension] [x] [y] [z]
```

`/wa invasion ...` provides concise operator summaries and state snapshots.
`/wa debug invasion evaluate ...` provides the dense inspect payload including cooldown gates and invasion-gated pool eligibility.

Bundled periodic profiles only schedule when an allowed dimension currently has a player. `Nether Surge` is therefore evaluated with Nether context, not the Overworld scheduler position. The bundled profiles have no timeout-completion reward and do not spawn waves.

Use these compatibility surfaces when an integration or tier provider path looks wrong:

```text
/wa compat list
/wa apotheosis tier inspect [player] [dimension]
/wa debug compat evaluate <integration_id> [player] [entity] [dimension]
/wa debug scalar provider <provider_key> [player] [entity] [dimension]
```

`/wa compat list` is the high-level activation check.
`/wa apotheosis tier inspect` gives a quick operator read of current tier/scalar context and shows compiled `derived_stage` hook mappings when present.
`/wa debug compat evaluate ...` shows gate-by-gate activation decisions, inactive reasons, and compiled stage-hook details.
`/wa debug scalar provider ...` shows raw provider value, resolved scalar contribution, and fail-closed details.

Simple Apotheosis derived-stage test loop:
1. set or change the Apotheosis tier for your test player
2. run `/wa apotheosis tier inspect Dev`
3. run `/wa debug compat evaluate <integration_id> player Dev`
4. run `/wa stage list global` or `/wa stage list player Dev` depending on progression mode

What to expect:
- in `derived_stage` or `hybrid` mode, authored `stage_hooks` unlock WA stages automatically once the tier threshold is reached
- login/respawn picks changes up immediately
- live tier changes are also picked up by a bounded periodic server reconcile pass, so you should not need a separate WA command just to make the stage hook run

## 3B. Mutator Cap Resolution

Mutator count limits resolve by taking the lowest of:
1. required `mutation_pools[].max_mutators_per_entity` on the selected pool
2. the live global maximum from `/wa config`
3. the hard spawn-time safety budget

Operator workflow when counts look wrong:
1. run `/wa debug mutators evaluate <entity_id>` to see likely pool selection
2. run `/wa debug mutators force_pool <entity_id> <pool_id>` to isolate one pool
3. check the reported `spawn_context` line to confirm the spawn origin and attributed player
4. compare that pool's `max_mutators_per_entity` against the live global cap

## 3C. Mutation Chance Resolution

Mutation chance resolves on the selected pool, after pool selection and before mutator selection.

Rules:
1. `mutation_pools[].mutation_chance` is required and rolls once after pool selection
2. `mutation_chance=1.0` always proceeds (no roll)
3. `mutation_chance=0.0` always stops with `final_outcome=chance_failed`
4. `0.0 < mutation_chance < 1.0` performs one deterministic roll per selected-pool evaluation
5. after success, `additional_mutation_chance` performs a new conditional roll for each further slot until failure or a cap
6. curated mode selects an eligible combination as a whole; any-compatible mode selects further ordinary weighted mutators

Operator workflow when pools match but mobs still do not mutate:
1. run `/wa debug mutators evaluate <entity_id>`
2. check `selected_pool` and `chance_result`
3. if `chance_result` shows `passed=false`, you hit chance gating rather than selector/budget rejection
4. run `/wa debug mutators force_pool <entity_id> <pool_id>` if you need to bypass chance while debugging

## 3D. Configuration Editor Status

The hosted web-session commands are retired. `/wa config` opens the native gameplay-profile dashboard for an authorised operator or the owner of an integrated singleplayer server.

Global Settings workflow:
1. run `/wa config`
2. hover a control for its live-application and preservation rules
3. adjust the local draft
4. press Apply to send the draft and its base revision to the server
5. wait for the green success message; a red message means nothing was accepted

Cancel sends no settings. Reset Defaults resets only the local draft until Apply is pressed. If another operator applies first, the server rejects the stale revision and refreshes the screen.

The profile button offers bundled Standard, Start Empty, and Copy Standard. Start Empty removes bundled gameplay from the active graph while retaining external datapacks and the named-mutator library needed to author new pools. Copy Standard creates an independent world-owned graph and disables the bundled source. Progression edits shared stable-ID stages and supported milestones; renaming display text does not break references, and deletion is rejected while dependencies remain. Existing stage history, mutated-mob provenance, Ascension choices, and external datapacks are preserved.

World Phases is the normal progression view. Expand or collapse **Phases**, search, then select a phase to edit General, Mob Mutation Dimensions, and Unlock Conditions, in that order. Hover the compact name or description field to identify it; the description tooltip also shows the complete text when it is wider than the field. Every row shows authored On/Off state; amber Unsaved and compact up/down order controls appear on the selected row. An enabled arrow moves the phase exactly one place and marks the draft Unsaved; Apply saves that order across reopen and restart. Starting means the phase is available from the beginning; it does not force position 1 or prevent reordering. Cumulative progression may have several starting phases, while exclusive/replace groups reject conflicting defaults during validation. One to three automatic conditions can use **All (AND)** or **Any (OR)**; gold separators between rows show the current choice, and the explanation immediately below Add Condition describes the resulting unlock. AND progress is persisted in either completion order and follows GLOBAL/PER_PLAYER isolation. Starting Phase and Manual are exclusive single modes. Advanced datapack conditions are shown as preserved instead of being rewritten incorrectly. The rows and details describe configuration only, so they do not show whether a player or world has already unlocked the phase. Use **Apply** to publish the draft, **Discard Changes** to reload it, **Restore Source** for an overridden source phase, or **Delete Phase** for a world-created phase whose dependencies permit deletion. **Reset All Defaults** has the same destructive whole-profile meaning and confirmation used by Mob Mutations. In Mob Mutations, use the collapsible **World Phases** and **Pressure Events** groups; both edit in the same detail panel, but event configurations remain runtime-independent. For a phase, choose a dimension and edit Default Mobs or a named specialised configuration. A mob may belong to only one specialised configuration for the same phase and dimension; that specialised configuration wins over Default Mobs. Phase, configuration, stage, and related trigger changes use one profile revision, so stale simultaneous editors are rejected instead of silently overwriting each other. Older track/tier records are preserved underneath the friendly view and are not counted as ordinary active Mob Configs when they cannot be mapped safely.

Before relying on a newly edited profile in a live server, manually check:

1. Open `/wa config` in singleplayer and as an authorised dedicated-server operator; confirm a non-operator cannot apply changes.
2. Confirm World Phases lists each phase once and that changing its mutation dimensions is not published until Apply.
3. Create, select, rename, enable, disable, discard, remove, restore, and apply a named configuration. Confirm disabled configurations remain listed as Off and unsaved mutation edits prompt before switching phase, dimension, or configuration.
4. Restart the server and confirm phase/dimension/configuration names, enabled states, mob selections, weights, and order are retained.
5. Put one mob in a specialised configuration and confirm debug evaluation chooses that configuration rather than Default Mobs; confirm an ordinary spawn still receives only one pool-selection pass.
6. Expand **Pressure Events** inside Mob Mutations and confirm those event-only definitions remain separate from the seven ordinary Standard phases.
7. Load a world containing older authoring metadata and confirm it is preserved and does not appear as an ordinary active Mob Config when it cannot be mapped safely; use debug inspection to confirm whether that preserved definition still affects gameplay.

Mutation-pool workflow:

1. choose Mob Mutations from the dashboard
2. search the combined World Phases and Pressure Events browser, and expand or collapse either group
3. select a phase or event and review its friendly source (`Bundled Standard`, `External datapack`, `World override`, or `World authored`)
4. for a phase, choose Dimension and then Mob Config at the top; open the Mob Config picker for Create, Rename, explicit Enable/Disable, Remove, or Restore, then edit mutation chance, additional chance, maximum, and eligible mobs below
5. review the theoretical mutation-count preview; eligibility and compatibility may reduce actual multi-mutation outcomes
6. review the weight-sorted mutator rows and their relative selection shares; use the arrows to swap adjacent weights or `Weight: X` to enter an exact positive integer
7. choose Any Compatible or Curated; curated combinations select pool mutators, optionally narrower parent-pool mobs, and a positive relative combination weight
8. press Apply to validate and publish only this pool, Discard Changes to reload the current server value, or Restore Source for a source-backed override/removal
9. use Reset All Defaults only when you intend to clear every world-authored profile edit and global editor override; confirmation pages name the action you are about to take (such as Discard, Restore, Delete, or Reset All), and the centred help text explains its scope; resetting cannot be undone, restores bundled Standard, and leaves external datapack files and earned progression alone

An asterisk marks an overridden pool. Apply preserves unsupported fields such as conditions, biome filters, spawner policy, and integration filters. Navigation warns about an unsaved pool draft. Selector Cancel discards only that selector's changes, while pool Apply persists the assembled draft.

Labels that fit remain stationary. Longer button, slider, breadcrumb, summary, provenance, and validation text pauses at the beginning, scrolls smoothly to the end, pauses again, and repeats; hover tooltips still provide the complete context immediately.

The Mob Mutations browser uses compact two-line phase/event rows showing context, honest aggregate enabled state, selection, and unsaved status. Phase summaries count present named Mob Configs—including disabled ones—but exclude explicitly removed and unresolved older metadata. Mob and dimension selectors follow the same outlined-row layout, keep fixed checkbox columns, preserve hidden selections while searching, and show readable names above exact registry IDs. The Mob Config picker shows each actual On/Off/Removed state; selecting is separate from toggling, all configuration-management actions live in that picker, and pending state changes return to the main draft for Apply or Discard. The rare-combination editor separates mutators from inherited-or-narrowed mob eligibility; on short screens it switches those sections to simple tabs so the footer stays reachable. Curated combinations show their name separately from relative share, eligible-mob count, and weight. Mutators appear in stable descending-weight order inside outlined rows, with one-decimal relative selection shares; equal weights retain their authored order. Those shares describe the current pool weights, not final spawn probabilities, because runtime eligibility, compatibility, stacking, and multi-selection checks can still change outcomes. Lists calculate their visible rows from the current screen height. Scrollbars appear only on overflow, support wheel and thumb dragging, and preserve their position while selections or weights change. Mutation probability is shown as a percentage; only true runtime scaling controls use `x` multipliers.

Reusable mutators are edited from the Mutator picker. The component picker is searchable and grouped into stats and defence, mob behaviour, combat, summoning, equipment, elemental packages, and visuals. Direct numeric components keep their amount beside the selected component; components with several settings open a separate form. Registry-backed settings use searchable pickers. For `equip_item`, choose the slot first if it is not Automatic: hand slots accept held items, while head, chest, legs, feet, and body filter the list to items whose vanilla equipment data fits that slot. `effect_particles` shows the colour of a chosen status effect but does not apply it; `ambient_particles` emits the chosen raw particle type for atmosphere only. A mutator may contain up to six components. Some registered component names are deliberately visible but disabled: their datapack shape exists, but a safe live runtime handler does not yet. Disabled rows use muted title and detail text, do not highlight when hovered, and still explain the reason on hover. Rarity is intentionally not edited here: it does not change selection chance, which is controlled by the pool’s weights, eligibility, compatibility, and mutation limits. Ascension choice amount boxes follow the same pattern: hover the effect or its value to see the unit, valid maximum, and default value before saving.

Apply and failure notices appear temporarily at the right of the breadcrumb row. Hover a shortened red notice before it expires to read the complete wrapped server validation message.

Ascension uses those same standards. Its searchable offer browser, outlined sections, scrolling selectors, transient notices, confirmations, and bottom controls behave like World Phases and Mob Mutations. The right side is deliberately ordered **General**, **Ascension Choices**, then **When to Offer**. Open **Add / Remove** to select choices or use **+ Choice** inside the reward selector. Each reward row shows its permanent effects and provides Edit plus a confirmed delete/restore action where the definition is world-authored. A compact choice may combine up to three different effects. The effect picker groups every registered player-effect type into **Stats**, **Survival**, **Mobility**, **Combat**, **Exploration**, and **Progression**. It only enables effects with a real live player handler; unavailable rows are muted, do not highlight on hover, and explain why when hovered. Today that means maximum health, armor, armor toughness, attack damage, movement speed, knockback resistance, luck, fire resistance, and night vision are selectable. Reward edits are not saved separately: Apply validates and publishes them atomically with the offer. A phase-linked offer follows the selected phase. Custom conditions always show their AND/OR operator, repeat that operator in gold between rows, and copied phase conditions become independent. Pending and already claimed instances both count for the normal Once per player mode.

### Configuration editor release checklist

The following checks require a running game and are not replaced by unit tests:

1. In singleplayer, open `/wa config` as the world owner, apply one setting, leave and reopen the world, and confirm the value remains.
2. Change a draft and press Cancel; reopen the screen and confirm the server value did not change.
3. Press Reset Defaults, verify that only the draft changes, then Apply and confirm the Standard defaults are restored.
4. On a dedicated server, confirm an operator can open and apply the screen while a non-operator cannot open it or submit a crafted apply packet.
5. Have two operators open the screen at the same revision. Apply from one, then confirm the other's Apply is rejected as stale and refreshed from the server.
6. Restart the dedicated server and confirm the applied world settings remain.
7. With an external datapack loaded, toggle bundled default gameplay and confirm the external definitions remain available and validation errors prevent an unsafe candidate from replacing the live compiled state.
8. Confirm live behavior for each toggle: mutation spawning/handlers, pressure-event shutdown and future cooldown frequency, and Ascension-offer delivery.
9. Edit one bundled pool's chance, mob list and mutator weight; confirm unrelated pools are byte-for-byte unchanged in the server snapshot.
10. Restart and `/reload`, confirming the override appears once, remains marked, and does not duplicate mutators.
11. Revert the pool and confirm its current datapack source returns, including an external exact-ID replacement when present.
12. Test mob and dimension search at small and large GUI scales with 1, 10, 50, and a large modded catalogue; verify fixed checkbox alignment, wheel/thumb scrolling, preserved hidden selections, and unclipped footer controls.
13. Configure an 11% initial chance, 2% additional chance, and maximum two; confirm the preview reads 89.00% none, 10.78% one, and 0.22% two.
14. Add two curated combinations with different weights, verify incompatible choices are blocked locally/server-side, and confirm save, restart, `/reload`, Revert, and Cancel preserve the expected draft/live boundary.
15. Spawn eligible and ineligible mobs for a curated combination and confirm ineligible mobs safely fall back to one ordinary mutation rather than receiving the restricted combination.
16. At a reduced window height, edit a rare combination and verify the Mutators/Eligible Mobs tabs keep every row and Confirm/Cancel reachable; at a larger height verify both outlined sections are visible together.
17. Raise Maximum Mutations through its supported range and confirm the expected-mutations preview wraps inside General without changing its calculated values.

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
- The Ascension reward selector follows the Mob Config action-bar pattern. It can present five choices; rows select only, while the bottom bar creates, edits, removes, or confirms completion.

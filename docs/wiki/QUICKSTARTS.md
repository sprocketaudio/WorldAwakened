# Quickstarts

Shortest-path guides for the most common first-time workflows.

- Document status: Active friendly quickstart guide
- Last updated: 2026-10-19
- Scope: Setup, first datapack, first tests, and first recovery loop

---

## 1. Start With The Built-In Standard Experience

This is the cleanest first sanity check.

Goal:
- confirm the mod loads
- confirm the bundled Standard profile validates and affects new natural hostile spawns conservatively

Steps:
1. Install the mod normally.
2. Start a new test world.
3. Do not install any custom World Awakened datapack yet.
4. Run:

```text
/wa reload validate
```

What you should expect:
- no validation errors
- bundled `worldawakened:defaults/...` stages, triggers, mutators, pools, Ascension choices, loot profiles, and invasion profiles are loaded
- after world day 3, eligible natural Overworld hostile spawns have a small mutation chance
- reaching later milestones raises the final per-spawn rates; S4+ can very rarely choose a curated two-mutator pool
- Hindering on-hit effects begin at S2, rare Vampiric enemies at S3, Volatile death explosions at S4, and curated ability combinations at S5
- mutated mobs have visible composed names and existing glow/particle tells; existing custom names are preserved
- the Ignition, Fracture, and World Awakened phases each grant one personal one-of-three Ascension offer
- mutated kills can add small stage-bounded bonus materials
- S4+ can schedule infrequent warning-backed pressure events while players are present in the affected dimension

Spawner and trial-spawner mobs are excluded. Ordinary pools apply one mutator and only explicitly weighted curated pools may apply two.

## 2. Install The Optional Example Pack

Goal:
- load authored content deliberately
- verify the pack install path is correct

Steps:
1. Copy [example_datapacks/worldawakened_example_pack](../../example_datapacks/worldawakened_example_pack) into your world's `datapacks/` folder.
2. Run:

```text
/reload
/wa reload validate
```

What you should expect:
- datapack validation succeeds
- the loaded counts now include authored objects from the example pack

If it does not load, go to [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## 3. Install A Local Test Pack

Goal:
- load a pack you control so your test IDs and outcomes are predictable

Steps:
1. Copy your chosen World Awakened datapack into your world's `datapacks/` folder.
2. Run:

```text
/reload
/wa reload validate
```

What you should expect:
- `errors=0`
- loaded counts include your authored objects (for example stages, trigger rules, rules, rewards, and offers)

## 4. First Per-Player Stage Test

New native configurations default to `progression.mode = "per_player"`.

Goal:
- prove one player owns their own stage state
- prove the authored trigger matches the event source you expect

Steps:
1. Reset cleanly:

```text
/wa debug reset player Dev all
```

2. Inspect the starting state:

```text
/wa stage list player Dev
/wa dump active_rules player Dev
```

3. Fire a player-scoped manual trigger from a real player context:

```text
/wa trigger fire <trigger_id> player Dev
```

4. Recheck state:

```text
/wa stage list player Dev
/wa ascension pending Dev
```

What you should expect:
- the trigger matches because it is authored as `source_scope: "player"`
- the stage unlocks in Dev's personal progression bucket
- Dev receives the pending ascension offer because ascension is player-scoped

If that feels surprising, read [CONCEPTS.md](CONCEPTS.md).

## 5. Optional Per-Server Stage Test

Use this only when your config has `progression.mode = "global"`.

Goal:
- prove stage state is shared globally

Suggested flow:
1. Switch progression mode to `global` in config before progression history exists.
2. Restart the game/server.
3. Use explicit player-targeted commands:

```text
/wa stage list global
/wa trigger fire <trigger_id> player Dev
/wa stage list global
```

What you should expect:
- the shared stage state changes
- other players share that unlock

## 5A. First Apotheosis Derived-Stage Test

Use this when:
- Apotheosis compat is installed and active
- `compat.apotheosis.mode` is `derived_stage` or `hybrid`
- your integration profile uses `stage_hooks` with `worldawakened:tier_threshold_unlock`

Goal:
- prove an external Apotheosis tier threshold can unlock a normal WA stage

Steps:
1. Verify compat is active:

```text
/wa compat list
/wa apotheosis tier inspect Dev
```

2. Change the Apotheosis tier for your test player by whatever operator method your pack/mod setup exposes.
3. Inspect the compat state again:

```text
/wa apotheosis tier inspect Dev
/wa debug compat evaluate <integration_id> player Dev
```

4. Recheck the affected WA stage bucket:

```text
/wa stage list global
```

or:

```text
/wa stage list player Dev
```

What you should expect:
- the tier inspect output shows the new Apotheosis tier
- the compat debug output shows the compiled stage-hook threshold mapping
- the matching WA stage unlocks automatically without needing a separate WA trigger command

## 6. First Ascension Choice Test

Goal:
- prove one player can receive and resolve one personal ascension offer

Steps:
1. Make sure Dev has a pending offer:

```text
/wa ascension pending Dev
/wa ascension inspect Dev
```

2. Either note the runtime `instance_id`, or use the simpler `active` command.
3. Runtime `instance_id` values are opaque command-safe IDs, so you can paste or type them directly if you need the exact instance.
4. Choose one reward ID from your loaded offer:

```text
/wa ascension choose Dev <instance_id> <reward_id>
```

or:

```text
/wa ascension active Dev <reward_id>
```

5. Inspect again:

```text
/wa ascension inspect Dev
```

What you should expect:
- exactly one chosen reward
- the offer moves to resolved
- the other rewards are recorded as forfeited for that offer instance

## 7. First Safe Recovery Loop

Goal:
- recover from a bad test without deleting the whole world immediately

### Reopen a bad ascension resolution

```text
/wa ascension reopen Dev <instance_id>
```

Use this when:
- the wrong reward was chosen
- you want the same offer instance pending again

### Clear a bad ascension instance entirely

```text
/wa ascension clear Dev <instance_id>
```

Use this when:
- the instance itself is bad or stale
- you do not want to reopen it

### Reset a whole section for clean testing

```text
/wa debug reset global all
/wa debug reset player Dev all
```

Use this when:
- you want a broad clean rerun
- you are in active development, not live operations

## 8. Configuration Editing Status

Run `/wa config` as an operator (or as the owner of a singleplayer world). It opens the WorldAwakened profile dashboard. Use Global Settings for intensity and system controls, World Phases for the milestone timeline and mutation dimensions, Mob Mutations for a phase's dimension-specific named mob configurations, or Ascension for permanent player choices and their availability.

In Ascension, select or create an offer. The detail order is **General**, **Ascension Choices**, then **When to Offer**. Use **Add / Remove** to open the reward selector. There you can choose loaded rewards, create a choice with up to three different live effects, edit a compact choice, or delete/restore world-authored reward data with confirmation. Reward rows summarize their effects. Changes remain part of the local offer draft until Apply, then the offer and reward definitions save together. For the simplest availability, choose **When World Phase unlocks** and select a phase. Choose custom conditions only when the offer should be independent; the AND/OR control stays visible, gold separators show how rows combine, and **Copy from World Phase** copies the current conditions without keeping them linked. Keep **Once per player** unless distinct authored sources should intentionally grant the same offer again.

In World Phases, expand **Phases**, select a phase, and edit General, Mob Mutation Dimensions, then Unlock Conditions on the right. Rows show On/Off; the selected row also shows amber Unsaved and its ordering arrows. Configure one to three automatic conditions and choose **All conditions (AND)** or **Any condition (OR)**. AND remembers each completed condition, so day 20 and entering the Nether may happen in either order. Starting Phase and Manual are single modes. The list intentionally does not show runtime unlock ticks. Use **+ Phase** to create one, **Apply** or **Discard Changes** for the current draft, **Restore Source** for an override, and the separately confirmed **Reset All Defaults** only when the entire WorldAwakened editor state should return to bundled defaults.

In Mob Mutations, expand **World Phases** or **Pressure Events** in the left browser. Search and select either kind of entry; both open in the same right-hand editor, while their gameplay eligibility remains independent. For a World Phase, choose the configured Dimension first and the named Mob Config second. The On/Off text beside the selector is status only. Open the Mob Config selector to select, create, rename, enable, disable, remove, or restore a configuration; selecting it does not toggle it. Back in the editor, change mutation and additional chances, maximum mutations, eligible living mobs, mutator weights, or rare combinations. A disabled configuration stays visible and editable, and its preview remains the configured theoretical result even though its live gameplay chance is zero. **Apply** validates and publishes the draft, **Discard Changes** reloads the current server value without leaving, and **Restore Built-in** removes the selected source-backed override or restores a removed source-backed Mob Config. Hidden datapack conditions are preserved.

To build from scratch, choose the active profile and select Start Empty. Create or select a World Phase, choose **Mob mutation dimensions**, and select the installed dimensions that phase should affect. Apply the phase, open Mob Mutations, choose the phase and dimension, then edit Default Mobs or add a named specialised configuration. If a phase has no settings, **Add Mob Settings** opens the dimension picker directly. New specialised configurations are clean rather than copies: they start disabled with no eligible mobs or mutators, so configure both before enabling them. Pressure-event configurations remain independent.

### Manual profile-editor test checklist

These checks require a live client/server and are not replaced by unit tests:

1. In singleplayer, Start Empty, create `The Awakening`, choose Enter Dimension and The Nether, create `Nether Difficulty`, add a tier using that stage, select Nether mobs/mutators, and Apply.
2. Leave and reopen the world, then confirm the stage, trigger, track, tier, weights, and profile choice remain. Run `/reload` and confirm they remain again.
3. Enter the Nether and confirm the phase unlocks. With later phases unlocked—including unlocking a milestone out of order—use mutation debug evaluation to confirm only the current phase's ordinary configurations qualify.
4. Reuse the same stage in a second track, remove either tier, and confirm the other track and shared stage are unchanged.
5. Try deleting a referenced stage and confirm the dependency message blocks it. Rename its display name and confirm both tracks still reference it.
6. On a dedicated server, confirm a non-operator cannot open/apply the editor, two operators receive a stale-revision rejection instead of overwriting each other, and restart persistence matches singleplayer.
7. Test both GLOBAL and PER_PLAYER progression. In PER_PLAYER, verify two players advance independently and unattributed spawn context remains fail-closed. Complete an advancement before joining/rejoining and verify supported advancement reconciliation.
8. Repeat the screens at small GUI scales/resolutions. Verify search focus, row scrolling, visible scrollbars, fixed footer controls, confirmation screens, Cancel, Revert, and validation errors remain usable.

Global Settings covers intensity, bundled gameplay, mutation enablement/frequency/caps, pressure events, Ascension, and Per Player / Per Server progression mode. Standard remains the only bundled profile; advanced generic rule authoring and unsupported component handlers remain datapack-only. External datapacks keep normal priority semantics, and editor overrides never rewrite their files.

## 9. Where To Go Next

- For command usage: [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
- For authored examples: [DATAPACK_COOKBOOK.md](DATAPACK_COOKBOOK.md)
- For failures and odd behavior: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- For concise answers: [FAQ.md](FAQ.md)

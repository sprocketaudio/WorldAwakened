# Quickstarts

Shortest-path guides for the most common first-time workflows.

- Document status: Active friendly quickstart guide
- Last updated: 2026-10-17
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
- personal Nether, End, and Dragon advancements grant one-of-three Ascension offers
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

## 4. First Global Stage Test

Use this when your config has `progression.mode = "global"`.

Goal:
- prove stage state is shared globally
- prove the authored trigger matches the event source you expect

Steps:
1. Reset cleanly:

```text
/wa debug reset global all
/wa debug reset player Dev all
```

2. Inspect the starting state:

```text
/wa stage list global
/wa dump active_rules global
```

3. Fire a player-scoped manual trigger from a real player context:

```text
/wa trigger fire <trigger_id> player Dev
```

4. Recheck state:

```text
/wa stage list global
/wa ascension pending Dev
```

What you should expect:
- the trigger matches because it is authored as `source_scope: "player"`
- the stage still unlocks in the shared global stage bucket because progression mode is `global`
- Dev receives the pending ascension offer because ascension is player-scoped

If that feels surprising, read [CONCEPTS.md](CONCEPTS.md).

## 5. First Per-Player Stage Test

Use this when your config has `progression.mode = "per_player"`.

Goal:
- prove each player owns their own stage progression

Suggested flow:
1. Switch progression mode to `per_player` in config.
2. Restart the game/server.
3. Use explicit player-targeted commands:

```text
/wa stage list player Dev
/wa trigger fire <trigger_id> player Dev
/wa stage list player Dev
```

What you should expect:
- Dev's stage state changes
- other players would not automatically share the stage unlock

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

Run `/wa config` as an operator (or as the owner of a singleplayer world). It opens the WorldAwakened profile dashboard. Use Global Settings for intensity and system controls, or Mob Mutations to browse and edit actual loaded mutation pools.

In Mob Mutations, search or filter the pool list, select a pool, then change its enabled state, initial chance, conditional additional chance, maximum, dimensions, eligible living mobs, or ordinary mutator weights. The wrapped theoretical preview shows mutation-count probabilities before eligibility and compatibility can reduce them. Mob and dimension selectors use searchable two-line rows: the readable name is primary, while the exact registry ID remains visible underneath. Rare Combinations can use Any Compatible selection or curated weighted combinations with narrower mob eligibility; the mode tooltip explains the difference, and the combination editor separates mutation choices from eligible mobs. `*` marks a world override. Apply validates and persists only that pool; Revert restores the currently loaded datapack source. Selector windows and combination editors keep changes in the pool draft until the pool itself is applied.

Global Settings still covers intensity, bundled gameplay, mutation enablement/frequency/caps, pressure events, and Ascension. Standard is the only bundled profile; progression and other dashboard sections remain unavailable. External datapacks keep normal priority semantics, and pool overrides never rewrite their files.

## 9. Where To Go Next

- For command usage: [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
- For authored examples: [DATAPACK_COOKBOOK.md](DATAPACK_COOKBOOK.md)
- For failures and odd behavior: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- For concise answers: [FAQ.md](FAQ.md)

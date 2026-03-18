# Quickstarts

Shortest-path guides for the most common first-time workflows.

- Document status: Active human-friendly quickstart guide
- Last updated: 2026-03-18
- Scope: Setup, first datapack, first tests, and first recovery loop

---

## 1. Start The Framework With No Gameplay Content

This is the cleanest first sanity check.

Goal:
- confirm the mod loads
- confirm the framework is inert until a datapack is installed

Steps:
1. Install the mod normally.
2. Start a new test world.
3. Do not install any World Awakened datapack yet.
4. Run:

```text
/wa reload validate
```

What you should expect:
- no validation errors
- no gameplay content loaded from the mod jar
- the framework should be present, but it should not drive progression by itself

Use this test whenever you want to prove the framework is content-empty by default.

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

## 8. Web Authoring Modes

Use this when the hosted web authoring platform is available in your build/deployment.

Goal:
- choose the right editing mode for the job
- avoid stale-session conflicts during live linked edits

Offline project mode:
1. Open the hosted editor normally.
2. Create or import a datapack project.
3. Validate and export.

Use offline mode when:
- you do not need a running game/server
- you want repeatable file-based import/export iteration

Live linked session mode:
1. In-game, run:

```text
/wa web edit
```

2. Open the returned hosted URL.
3. Wait for linked session state + runtime registry metadata to load.
4. Edit and validate in the browser.
5. Apply changes back to runtime.

Use live linked mode when:
- you need current runtime-authored data
- you want runtime/modpack-aware selector options
- you want apply-time runtime validation feedback immediately

Safety reminders:
- linked sessions are short-lived
- stale sessions should fail safely on apply
- if apply reports revision mismatch, refresh from runtime before retrying

## 9. Where To Go Next

- For command usage: [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
- For authored examples: [DATAPACK_COOKBOOK.md](DATAPACK_COOKBOOK.md)
- For failures and odd behavior: [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- For concise answers: [FAQ.md](FAQ.md)

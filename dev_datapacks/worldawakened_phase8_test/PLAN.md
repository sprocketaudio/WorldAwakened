# Phase 8 Test Plan

This runbook validates Phase 8 invasion scheduler, active-state, temporary pressure modifier behavior, and invasion-context mutation/loot paths using `worldawakened_phase8_test`.

## 1. Prerequisites

1. Install only `worldawakened_phase8_test` (or ensure it has higher datapack priority than conflicting packs).
2. Enable debug commands in `worldawakened-common.toml`:
   - `general.enable_debug_commands=true`
3. Use an operator-capable command source.

## 2. Reload and Validate

Run:

```text
/reload
/wa reload validate
```

Expected:
- invasion, mutation pool, mutator, rule, and loot objects from namespace `worldawakened_p8test` load successfully
- no unresolved-reference errors for this pack

## 3. Profile and Active-State Smoke Test

Run:

```text
/wa invasion inspect profile worldawakened_p8test:night_undead_assault
/wa invasion inspect profile worldawakened_p8test:nether_purge
/wa invasion inspect active
```

Expected:
- both profiles are found
- active inspect shows no active invasion (or currently running state if already active)

## 4. Debug Evaluate Eligibility

Overworld:

```text
/wa debug invasion evaluate worldawakened_p8test:night_undead_assault
```

Expected:
- `profileFound=true`
- profile can become eligible with at least one online player

Outside nether:

```text
/wa debug invasion evaluate worldawakened_p8test:nether_purge
```

Expected:
- rejected by dimension gate (`minecraft:the_nether`)

Inside nether:

```text
/wa debug invasion evaluate worldawakened_p8test:nether_purge
```

Expected:
- profile can become eligible

## 5. Start, Inspect, and Stop

Overworld profile:

```text
/wa invasion start worldawakened_p8test:night_undead_assault
/wa invasion inspect active
```

Expected:
- invasion starts
- active state shows warning/duration/cooldown metadata and invasion tags

Stop:

```text
/wa invasion stop
/wa invasion inspect active
```

Expected:
- active invasion clears
- cooldown remains non-zero

Nether profile:

```text
/wa invasion start worldawakened_p8test:nether_purge
/wa invasion inspect active
/wa invasion stop
```

Expected:
- start succeeds only from valid dimension context

## 6. Invasion-Gated Mutator Flow

Baseline:

```text
/wa debug mutators evaluate minecraft:zombie
```

Expected:
- invasion-only pools are not active when no invasion is running

During overworld invasion:

```text
/wa invasion start worldawakened_p8test:night_undead_assault
/wa debug mutators evaluate minecraft:zombie
/wa invasion stop
```

Expected:
- `worldawakened_p8test:invasion_undead_strike_pool` becomes eligible

During nether invasion:

```text
/wa invasion start worldawakened_p8test:nether_purge
/wa debug mutators evaluate minecraft:blaze
/wa invasion stop
```

Expected:
- `worldawakened_p8test:invasion_nether_purge_pool` becomes eligible

## 7. Invasion Reward Loot Flow

Undead invasion reward dry-run:

```text
/wa loot evaluate invasion_reward worldawakened_p8test:night_undead_assault
/wa debug loot evaluate invasion_reward worldawakened_p8test:night_undead_assault
```

Expected:
- `worldawakened_p8test:invasion_undead_assault_rewards` matches

Nether invasion reward dry-run:

```text
/wa loot evaluate invasion_reward worldawakened_p8test:nether_purge
/wa debug loot evaluate invasion_reward worldawakened_p8test:nether_purge
```

Expected:
- `worldawakened_p8test:invasion_nether_purge_rewards` matches

## 8. Failure-Isolation Check

Run nether profile from overworld:

```text
/wa invasion start worldawakened_p8test:nether_purge
```

Expected:
- command rejects with readable reason
- unrelated systems continue working:

```text
/wa stage list global
/wa dump active_rules global
```

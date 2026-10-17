# World Awakened Combat Ability Test Pack

This isolated development pack exercises the first live mutation-combat slice. It does not ship as bundled default gameplay.

## Install

Copy `worldawakened_combat_test` into a test world's `datapacks/` folder, then run:

```text
/reload
/wa reload validate
```

Use these common-config values for deterministic results:

```text
general.enable_builtin_gameplay=false
general.enable_debug_commands=true
mutators.enable_mutators=true
```

Restart after changing the config, then reload the datapack. With bundled gameplay enabled, its pools remain additional candidates and `spawn test` is no longer guaranteed to select this pack.

## Live checks

```text
/wa debug spawn test minecraft:zombie
/wa debug spawn test minecraft:husk
/wa debug spawn test minecraft:creeper
/wa debug spawn test minecraft:skeleton
/wa debug spawn test minecraft:stray
```

- zombie: `combat_test:vampiric` (20% actual health-damage lifesteal, capped before overkill)
- husk: `combat_test:plague_touch` (guaranteed poison on hit)
- creeper: `combat_test:volatile` (power-3 block-safe death explosion)
- skeleton: guaranteed `vampiric` + `volatile` two-mutator combination
- stray: guaranteed `plague_touch` + `vampiric` two-mutator combination

The equivalent dry-run checks (they do not spawn or mutate a live mob) are:

```text
/wa debug mutators force_mutator minecraft:zombie combat_test:vampiric
/wa debug mutators force_mutator minecraft:husk combat_test:plague_touch
/wa debug mutators force_mutator minecraft:creeper combat_test:volatile
```

The isolated definitions intentionally use obvious presentation cues: Vampiric emits red regeneration-style particles, Plague-Touched emits green poison-style particles, and Volatile uses a strong orange pulse plus frequent flame particles. Treat a missing cue as a failed visual test, not as expected behavior.

Aim directly at the test mob and run `/wa mob inspect`. The first line reports `health=<current>/<maximum>` and absorption. If you need an explicit selector, include its entity type, for example `/wa mob inspect @e[type=minecraft:zombie,limit=1,sort=nearest,distance=..24]`; an unfiltered nearest-entity selector normally selects the executing player.

For Vampiric, damage the zombie and record `/wa mob inspect`, let it damage you once, then immediately inspect it again. Its current health should increase by 20% of the actual health damage it dealt, without exceeding maximum health. The death explosion damages nearby entities, never blocks, fires once, and does not synchronously chain other WA death explosions.

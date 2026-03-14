# World Awakened Phase 5 Test Pack

Deterministic datapack for validating Phase 5 runtime behavior:

- candidate-pool-first mutator evaluation
- mutator/component-count guardrails
- fail-closed unsupported component branches
- deterministic mutation provenance and `/wa mob inspect` output
- Phase 5 debug command surfaces (`evaluate`, `force_pool`, `force_mutator`, `spawn test`)

## Install

Copy `worldawakened_phase5_test` into your world `datapacks/` folder, then run:

```text
/reload
/wa reload validate
```

## Required Config Gates

Phase 5 test commands require:

- `mutators.enable_mutators = true`
- `general.enable_debug_commands = true`

## Command Tree Preflight

Before running Phase 5 command checks, verify the debug tree shape:

```text
/wa debug
```

Expected child literals include:

- `clear`
- `mutators`
- `reset`
- `spawn`

If you only see `clear` and `reset`, you are still running a pre-Phase-5 command tree.

Fix:

1. stop the game/server
2. rebuild/relaunch from current source (for example `./gradlew runClient` or `./gradlew runServer`)
3. reconnect and check `/wa debug` again

Note:
- `/reload` and `/wa reload validate` reload datapack content, but they do not re-register command nodes.

## Authored IDs

Stage and trigger:

- `wa_p5_test:phase5_gate`
- `wa_p5_test:unlock_phase5_gate`

Mutation pools:

- `wa_p5_test:zombie_stage_pool`
- `wa_p5_test:zombie_overflow_pool`
- `wa_p5_test:skeleton_pool`
- `wa_p5_test:spider_pool`
- `wa_p5_test:creeper_pool`
- `wa_p5_test:slime_speed_pool`

Mutators:

- `wa_p5_test:zombie_bulwark`
- `wa_p5_test:runner_skirmisher`
- `wa_p5_test:unsupported_summoner`
- `wa_p5_test:over_component_limit`
- `wa_p5_test:skeleton_guard`
- `wa_p5_test:spider_aura`
- `wa_p5_test:creeper_outline`
- `wa_p5_test:slime_rapid`

## Phase 5 Verification Flow

## 1) Reload + validation warnings

```text
/wa reload validate
```

Expected warnings include:

- `WA_PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED` for `wa_p5_test:over_component_limit` (11 enabled components)
- `WA_PERF_MUTATOR_COUNT_EXCEEDED` for `wa_p5_test:zombie_overflow_pool` (`max_mutators_per_entity = 9`)

## 2) Pre-stage gate check (zombie should not mutate yet)

```text
/wa debug mutators evaluate minecraft:zombie
```

Expected:

- no selected pool for zombie until `wa_p5_test:phase5_gate` is unlocked
- stage/context rejection appears for gated pools

## 3) Unlock Phase 5 gate

```text
/wa trigger fire wa_p5_test:unlock_phase5_gate global
```

Optional equivalent:

```text
/wa stage unlock wa_p5_test:phase5_gate global
```

## 4) Candidate narrowing checks

```text
/wa debug mutators summary
/wa debug mutators evaluate minecraft:zombie
/wa debug mutators evaluate minecraft:skeleton
/wa debug mutators evaluate minecraft:spider
/wa debug mutators evaluate minecraft:creeper
/wa debug mutators evaluate minecraft:slime
```

Expected:

- zombie evaluation uses zombie pools (`zombie_stage_pool`, `zombie_overflow_pool`) and does not need full-pool scan
- skeleton evaluation resolves to `skeleton_pool`
- spider evaluation resolves to `spider_pool`
- creeper evaluation resolves to `creeper_pool`
- slime evaluation resolves to `slime_speed_pool` and shows chance result (`mutation_chance=0.5`)

## 5) Force pool and verify fail-closed branches

```text
/wa debug mutators force_pool minecraft:zombie wa_p5_test:zombie_stage_pool
```

Expected rejections include:

- `WA_MUTATOR_COMPONENT_SKIPPED_UNAVAILABLE_SURFACE` for `wa_p5_test:unsupported_summoner`
- `WA_PERF_MUTATOR_COMPONENT_COUNT_EXCEEDED` for `wa_p5_test:over_component_limit`

## 6) Force unsupported mutator directly

```text
/wa debug mutators force_mutator minecraft:zombie wa_p5_test:unsupported_summoner
```

Expected:

- forced mutator is rejected (`WA_DEBUG_MUTATOR_NOT_FOUND`) because it failed eligibility/runtime support checks

## 7) Force overflow pool and verify cap enforcement

```text
/wa debug mutators force_pool minecraft:zombie wa_p5_test:zombie_overflow_pool
```

Expected limit line:

- `requested_mutator_cap=9`
- `enforced_mutator_cap=8`

## 8) Controlled live spawn test + provenance inspect

```text
/wa debug spawn test minecraft:zombie
/wa mob inspect @e[type=minecraft:zombie,limit=1,sort=nearest,distance=..24]
```

Expected inspect fields:

- entity type
- mutation pool
- applied mutators
- applied components
- mutation stage context
- mutation trace ID
- mutation depth and origin marker
- resolved vs missing mutator IDs
- failed-closed component entries (when applicable)
- final WA-owned attribute deltas

## 9) Cleanup (optional)

```text
/kill @e[type=minecraft:zombie,distance=..40]
/kill @e[type=minecraft:skeleton,distance=..40]
/kill @e[type=minecraft:spider,distance=..40]
/kill @e[type=minecraft:cave_spider,distance=..40]
/kill @e[type=minecraft:creeper,distance=..40]
/kill @e[type=minecraft:slime,distance=..40]
/wa stage lock wa_p5_test:phase5_gate global
```

## Notes

- This pack intentionally includes one over-limit mutator and one unsupported-surface mutator to validate guardrail behavior.
- Warnings and branch-level rejections are expected and are part of the test.
- Namespace is isolated (`wa_p5_test`) so it can run alongside example datapacks.

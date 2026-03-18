# World Awakened Phase 6 Test Pack

Deterministic datapack for validating Phase 6 runtime behavior:

- shared effective difficulty scalar service usage on spawn pressure paths
- global difficulty modifier commands (`/wa difficulty global ...`)
- challenge modifier commands (`/wa difficulty personal|world ...`) including optional vote flow
- pressure debug surfaces (`/wa debug pressure evaluate|last|replay`)
- rule-event guardrails:
  - `maximum_rules_evaluated_per_event`
  - `maximum_actions_per_rule`

Namespace is isolated: `wa_p6_test`.

## Install (new world)

1. Copy `dev_datapacks/worldawakened_phase6_test` into your world `datapacks/` folder.
2. Keep your existing example pack enabled if you want; this test pack does not require disabling it.
3. Run:

```text
/reload
/wa reload validate
```

## Config Preflight

Recommended before testing:

- `general.enable_debug_commands = true`
- `general.debug_logging = true` (needed to see verbose `rules_eval=...` in trigger output)
- `mutators.enable_mutators = true`
- `spawning.enable_spawn_scaling = true`
- `performance.maximum_rules_evaluated_per_event = 50`
- `performance.maximum_actions_per_rule = 10`

For vote-path testing (optional):

- set `difficulty.challenge.admin_override = false`
- keep `difficulty.challenge.require_vote_in_global = true`

After config edits, restart the world/server.

## Authored IDs

Stage + trigger:

- `wa_p6_test:phase6_gate`
- `wa_p6_test:unlock_phase6_gate`

Spawn pressure probe content:

- pool: `wa_p6_test:zombie_pressure_pool` (`mutation_chance = 0.40`)
- mutator: `wa_p6_test:zombie_pressure_probe`

Rule-budget probes:

- action-overflow rule: `wa_p6_test:action_overflow_rule` (11 actions, last action is `mark_rule_consumed`)
- 55 budget rules: `wa_p6_test:budget_rule_01` .. `wa_p6_test:budget_rule_55`

## Test Flow

## 1) Command tree preflight

```text
/wa debug
```

Expected child literals include:

- `difficulty`
- `pressure`
- `mutators`
- `spawn`

## 2) Unlock Phase 6 gate

First fire unlocks the stage:

```text
/wa trigger fire wa_p6_test:unlock_phase6_gate global
```

(Optional equivalent)

```text
/wa stage unlock wa_p6_test:phase6_gate global
```

## 3) Pressure scalar baseline checks

```text
/wa debug pressure evaluate
/wa debug pressure evaluate minecraft:the_nether
/wa debug pressure evaluate minecraft:the_end
/wa debug difficulty scalar
```

Expected:

- pressure output shows base, dimension baseline, global modifier, challenge modifier, integration inputs, final effective scalar
- default dimension baseline behavior should show stronger baseline in nether/end than overworld (when using default config)

## 4) Global difficulty command path

```text
/wa difficulty global get
/wa debug mutators evaluate minecraft:zombie
/wa difficulty global set 1.50
/wa debug mutators evaluate minecraft:zombie
/wa difficulty global reset
/wa difficulty global get
```

Expected:

- `global set` within bounds succeeds
- zombie mutation chance in debug evaluate increases when global is raised
- reset returns to configured default

## 5) Challenge command path

Default global progression + `scope_mode=auto` resolves to world scope.

```text
/wa difficulty world get
/wa difficulty world set 1.10
/wa difficulty world get
/wa difficulty personal set 1.10
```

Expected:

- world set/get succeed (unless vote policy is active and override is disabled)
- personal set is rejected under default global scope resolution (`scope invalid`) and reports reason

## 6) Optional vote flow

If `difficulty.challenge.admin_override = false`:

```text
/wa difficulty world set 1.20
/wa difficulty world get
/wa difficulty vote yes
```

Expected:

- world set starts a vote instead of committing immediately
- vote state appears in world get output
- successful vote commits exactly one change

## 7) Captured spawn snapshot replay

```text
/wa debug spawn test minecraft:zombie
/wa debug pressure last
```

From `pressure last`, copy snapshot id and replay it:

```text
/wa debug pressure replay <id>
```

Expected:

- `last` shows captured runtime context (dimension, pos, entity, category, pool, stage context, chance data)
- `replay` reports scalar recomposition through the same scalar-service path

## 8) Rule budget guardrails

Fire the trigger again after stage is already unlocked:

```text
/wa trigger fire wa_p6_test:unlock_phase6_gate global
```

With `general.debug_logging = true`, expected verbose line includes:

- `rules_eval=50`

Also check server log for:

- `WA_PERF_RULE_EVENT_LIMIT_EXCEEDED`
- `WA_PERF_RULE_ACTION_COUNT_EXCEEDED`

Inspect overflow rule state:

```text
/wa dump active_rules global
```

Expected for `wa_p6_test:action_overflow_rule`:

- still `consumed=false` while `maximum_actions_per_rule=10`
- reason: the 11th `mark_rule_consumed` action is truncated by the action limit

Optional confirmation:

1. set `performance.maximum_actions_per_rule = 11`
2. restart
3. fire trigger again
4. run `/wa dump active_rules global`

Then `wa_p6_test:action_overflow_rule` should be able to consume.

## Notes

- This pack intentionally adds many world-scope rules to force event-budget truncation.
- If the example pack is also enabled, total rule counts are higher; this is fine for budget-limit validation.
- For the cleanest budget-only signal (less log noise), temporarily disable extra datapacks during this test.

# World Awakened Phase 7 Test Pack

Deterministic datapack for validating Phase 7 loot runtime behavior:

- loot profile candidate selection by target loot table
- canonical context condition evaluation (`event_type`, `loot_table`, `entity_is_mutated`)
- stage-gated loot behavior (`stage_filters`)
- forced profile evaluation (`/wa debug loot force_profile`)
- policy guardrails for destructive modes
- optional Apotheosis-sensitive safety outcomes (block/downgrade/disable policy)
- live entity-kill loot hook parity with debug expectations

Namespace is isolated: `wa_p7_test`.

## Install (new or existing world)

1. Copy `dev_datapacks/worldawakened_phase7_test` into your world `datapacks/` folder.
2. Run:

```text
/reload
/wa reload validate
```

## Config Preflight

Recommended before testing:

- `general.enable_debug_commands = true`
- `general.debug_logging = true`
- `loot.enable_loot_evolution = true`
- `loot.inject_only = true`
- `loot.allow_entry_replacement = false`
- `compat.apotheosis.enabled = true` (only needed for optional Apotheosis-sensitive checks)

After config edits, restart the world/server.

## Authored IDs

Stage + trigger:

- `wa_p7_test:phase7_gate`
- `wa_p7_test:unlock_phase7_gate`

Integration metadata:

- `wa_p7_test:apotheosis_sensitive_targets`

Loot profiles:

- `wa_p7_test:dungeon_stage_inject`
- `wa_p7_test:dungeon_event_guard`
- `wa_p7_test:unsafe_replace_sensitive`
- `wa_p7_test:zombie_kill_bonus`
- `wa_p7_test:mutated_entity_bonus`

## Phase 7 Test Plan

## 1) Command tree preflight

```text
/wa debug
```

Expected child literals include:

- `loot`
- `mutators`
- `spawn`

## 2) Evaluate valid target before gate unlock (stage filter rejection)

```text
/wa debug loot evaluate loot_table minecraft:chests/simple_dungeon
```

Expected:

- candidate list includes `wa_p7_test:dungeon_stage_inject`
- profile is rejected with stage filter reason while gate is still locked
- `wa_p7_test:dungeon_event_guard` is rejected because debug event is not `worldawakened:entity_killed`

## 3) Unlock Phase 7 gate

```text
/wa trigger fire wa_p7_test:unlock_phase7_gate global
```

Optional equivalent:

```text
/wa stage unlock wa_p7_test:phase7_gate global
```

## 4) Re-evaluate target after gate unlock

```text
/wa debug loot evaluate loot_table minecraft:chests/simple_dungeon
```

Expected:

- `wa_p7_test:dungeon_stage_inject` matches
- final outcome includes `minecraft:emerald x1`
- `wa_p7_test:dungeon_event_guard` still fails event-type check in debug mode

## 5) Force one profile on target context

```text
/wa debug loot force_profile wa_p7_test:dungeon_stage_inject loot_table minecraft:chests/simple_dungeon
```

Expected:

- only forced profile is evaluated
- outcome remains deterministic (`minecraft:emerald x1`)

## 6) Invalid target checks

```text
/wa debug loot evaluate entity minecraft:not_a_real_entity
/wa debug loot force_profile wa_p7_test:not_loaded loot_table minecraft:chests/simple_dungeon
```

Expected diagnostics:

- invalid target: `WA_DEBUG_LOOT_TARGET_INVALID`
- missing forced profile: `WA_DEBUG_LOOT_PROFILE_NOT_FOUND`

## 7) Destructive policy guardrail check (default config)

```text
/wa debug loot force_profile wa_p7_test:unsafe_replace_sensitive loot_table minecraft:chests/simple_dungeon
```

With default preflight values (`inject_only=true`, `allow_entry_replacement=false`), expected:

- branch rejected with policy block reason
- diagnostic includes `WA_REWARD_POLICY_DESTRUCTIVE_MODE_BLOCKED`

## 8) Optional Apotheosis-sensitive compatibility check

Run this section only when Apotheosis is installed and enabled.

1. Set:
   - `loot.inject_only = false`
   - `loot.allow_entry_replacement = true`
2. Restart.
3. Evaluate:

```text
/wa debug loot force_profile wa_p7_test:unsafe_replace_sensitive loot_table minecraft:chests/simple_dungeon
```

Then vary `compat.apotheosis.loot_unsafe_mode_policy` and retest:

- `block`: expect blocked branch (`WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED`)
- `downgrade_additive`: expect matched branch with resolved additive behavior (`WA_APOTHEOSIS_LOOT_MODE_UNSAFE`)
- `disable_profile_branch`: expect blocked/disabled branch (`WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED`)

## 9) Live event parity check (entity killed)

Spawn and kill a zombie after the gate is unlocked:

```text
/summon minecraft:zombie ~ ~ ~
```

Kill it and inspect drops.

Expected:

- `wa_p7_test:zombie_kill_bonus` applies on `worldawakened:entity_killed`
- drop list includes one `minecraft:paper` bonus item from WA profile

Optional dry-run parity:

```text
/wa debug loot evaluate entity minecraft:zombie
```

You should see the same profile candidate/match reasoning, except debug event-specific condition results where applicable.

## Notes

- This pack intentionally includes both matching and rejecting profiles to validate rejection-path diagnostics.
- `wa_p7_test:dungeon_event_guard` is expected to fail in debug evaluate because its `event_type` condition is pinned to live kill events.
- `wa_p7_test:mutated_entity_bonus` is expected to fail unless entity mutation provenance is present.
- Keep `loot.inject_only=true` in normal testing unless intentionally validating destructive-policy behavior.

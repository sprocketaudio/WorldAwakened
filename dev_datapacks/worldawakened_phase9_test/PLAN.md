# Phase 9 Test Plan

This runbook validates Phase 9 compatibility framework behavior, Apotheosis world-tier integration, tier-filter enforcement across ascension/mutator/loot paths, and fail-closed behavior using `worldawakened_phase9_test`.

## 1. Prerequisites

1. Install `worldawakened_phase9_test` (or ensure it has higher datapack priority than conflicting packs).
2. Install Apotheosis for active-tier-path testing.
3. Enable debug commands in `worldawakened-common.toml`:
   - `general.enable_debug_commands=true`
4. For destructive-mode safety checks (Section 8), set:
   - `loot.inject_only=false`
   - `loot.allow_entry_replacement=true`
5. Keep compat gates enabled (default):
   - `compat.apotheosis.enabled=true`
   - `compat.apotheosis.allow_world_tier_stage_unlocks=true`
   - `compat.apotheosis.allow_world_tier_loot_scaling=true`
   - `compat.apotheosis.allow_world_tier_mutator_scaling=true`
6. Restart after config edits.
7. Use a player-operator command source for tier-context commands.

## 2. Reload and Validate

Run:

```text
/reload
/wa reload validate
```

Expected:
- objects under namespace `worldawakened_p9test` load successfully
- no unresolved-reference errors for this pack

## 3. Compatibility Activation Checks

Run:

```text
/wa compat list
/wa debug compat evaluate worldawakened_p9test:apotheosis_profile
/wa debug compat evaluate worldawakened_p9test:apotheosis_profile <player>
```

Expected:
- integration is listed
- activation block shows loaded-mod/config/profile gates
- with Apotheosis installed and enabled, integration is active
- with missing/disabled compat, integration is inactive with explicit reason

## 4. Tier Provider and Scalar Checks

Run:

```text
/wa apotheosis tier inspect <player>
/wa debug scalar provider apotheosis_world_tier <player>
```

Expected:
- tier command returns current world-tier value and scalar contribution
- scalar-provider debug resolves `apotheosis_world_tier`
- unavailable provider paths report readable fail-closed reason + debug code

## 5. Stage Bootstrap and Offer Triggering

Run:

```text
/wa trigger fire worldawakened_p9test:unlock_phase9_gate player <player>
/wa stage list global
/wa ascension pending <player>
```

Expected:
- stage `worldawakened_p9test:phase9_gate` unlocks
- exactly one tier-filtered offer becomes pending:
  - tier `< 2`: `worldawakened_p9test:apotheosis_tier_low_offer`
  - tier `>= 2`: `worldawakened_p9test:apotheosis_tier_high_offer`

## 6. Mutator Pool Tier-Filter Checks

Run:

```text
/wa debug mutators evaluate minecraft:zombie
```

Expected after gate unlock:
- exactly one tier-filtered pool is eligible:
  - tier `< 2`: `worldawakened_p9test:apotheosis_low_tier_pool`
  - tier `>= 2`: `worldawakened_p9test:apotheosis_high_tier_pool`
- opposite tier pool shows filter rejection in debug details

## 7. Loot Tier-Filter Checks

Run:

```text
/wa debug loot evaluate loot_table minecraft:chests/simple_dungeon <player>
```

Expected after gate unlock:
- `worldawakened_p9test:apotheosis_tier_bonus_inject` matches only when tier `>= 2`
- for tier `< 2`, the same profile is rejected by `apotheosis_tier_filters`

## 8. Apotheosis-Sensitive Safety Enforcement

Sensitive target (marked via integration profile `loot_targets`):

```text
/wa debug loot force_profile worldawakened_p9test:apotheosis_sensitive_unsafe_replace loot_table minecraft:chests/simple_dungeon <player>
```

Expected (default `compat.apotheosis.loot_unsafe_mode_policy=block`):
- unsafe destructive operation is blocked on sensitive target
- diagnostics include Apotheosis safety outcome (`WA_APOTHEOSIS_LOOT_OVERRIDE_BLOCKED` / `WA_APOTHEOSIS_LOOT_MODE_UNSAFE`)

Control target (non-sensitive):

```text
/wa debug loot force_profile worldawakened_p9test:non_sensitive_replace_probe loot_table minecraft:chests/shipwreck_supply <player>
```

Expected:
- no Apotheosis-sensitive target block reason
- profile follows normal destructive-mode policy (allowed with preflight settings from Section 1)

## 9. Invasion-Adjacent Influence Boundary Check

Run:

```text
/wa invasion inspect active
/wa debug scalar provider apotheosis_world_tier <player>
/wa debug mutators evaluate minecraft:zombie
/wa debug loot evaluate loot_table minecraft:chests/simple_dungeon <player>
```

Expected:
- no WA invasion profile/wave behavior is introduced by this compat pack
- tier influence is visible in scalar output, mutation-pool eligibility, and loot reward evaluation
- no unexpected stage unlock side-effects beyond authored stage/offer logic

## 10. Fail-Closed Compatibility Check

Set in config:
- `compat.apotheosis.enabled=false`

Restart, then run:

```text
/reload
/wa reload validate
/wa compat list
/wa debug compat evaluate worldawakened_p9test:apotheosis_profile <player>
/wa debug scalar provider apotheosis_world_tier <player>
/wa debug mutators evaluate minecraft:zombie
/wa debug loot evaluate loot_table minecraft:chests/simple_dungeon <player>
```

Expected:
- datapack objects using `apotheosis_tier_filters` are rejected/disabled with `WA_INTEGRATION_INACTIVE`
- compat/scalar debug commands report unavailable provider paths with explicit reasons
- unrelated systems remain functional (`/wa stage list global`, `/wa dump active_rules global`)

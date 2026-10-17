# World Awakened Phase 9 Test Pack

Standalone compatibility validation datapack for Phase 9.

This pack is self-contained and can be installed without `worldawakened_example_pack`.

## Install

Copy `worldawakened_phase9_test` into a world's `datapacks/` folder, then run:

```text
/reload
/wa reload validate
```

## Contents

- compat metadata (`integration_profiles/`)
- stage + trigger bootstrap for deterministic unlock-driven tests (`stages/`, `trigger_rules/`)
- Apotheosis-tier gated ascension offers/rewards (`ascension_offers/`, `ascension_rewards/`)
- Apotheosis-tier gated mutation pools and mutators (`mutation_pools/`, `mob_mutators/`)
- Apotheosis-sensitive and non-sensitive loot profile probes (`loot_profiles/`)

## Test Runbook

- [PLAN.md](PLAN.md)

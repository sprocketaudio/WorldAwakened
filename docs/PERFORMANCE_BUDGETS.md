# World Awakened Performance Budgets and Runtime Guardrails

Canonical contract for hot-path runtime limits, indexing guarantees, and evaluation guardrails.

- Document status: Active shared-contract reference
- Last updated: 2026-10-17
- Scope: Rule engine, spawn-time mutation, and loot/reward evaluation performance contracts

---

## 0. Governance and Maintenance

This file is part of the shared framework reference set.

Related contracts:
- [SPECIFICATION.md](SPECIFICATION.md)
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [docs/README.md](README.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file in the same change whenever hot-path architecture, runtime limits, evaluation budgets, or performance diagnostics change.
- Keep this file aligned with rule execution contracts in `SPECIFICATION.md` and validation diagnostics in `VALIDATION_AND_ERROR_CODES.md`.

---

## 1. Runtime Philosophy

The native World Phase view is compiled at publish time into the existing stage-gated mutation-pool indexes. Named mob configurations do not add a second spawn roll, runtime phase scan, or per-spawn traversal of editor metadata.

- World Awakened must scale to large datapacks (including thousands of authored objects) without degrading server TPS.
- Runtime evaluation must never perform unbounded scans across full rule sets for hot-path events.
- Hot-path evaluators must operate on precompiled structures produced during datapack reload.
- Runtime performance protections must fail closed per object/branch rather than destabilizing unrelated systems.
- Owned runtime carrier executors for damage, tick, render, or other hot-path surfaces must use keyed WA-owned state and typed handler logic; they must not parse raw JSON on every event.
- Client visual carrier handlers must stay local-player-only and constant-time per frame; they may read synced WA-owned state and feed the owning client's render/lightmap hooks, but they must not scan world entities or rebuild authored data during render/tick hooks.
- Tick-driven mutator presentation behavior must run only on entities that already carry WA-owned typed state; `effect_particles` and `ambient_particles` must use per-entity emitter state and must not scan loaded entities globally.
- `glow_style` client outline rendering must run only for entities with synced WA-owned glow state, use frustum checks before rendering, keep the outline pass bounded, and clamp authored `brightness` to `0.0..1.0`.
- external-tier derived-stage reconciliation must use reload-compiled hook metadata and bounded cadence; it must not parse raw `integration_profiles.stage_hooks` JSON during live player or tick paths
- `world_day_milestone` evaluation is limited to the Overworld once per second and uses the compiled trigger-type bucket; authored one-shot state prevents repeated execution after a milestone matches

External-tier derived-stage guardrail:
- Phase 9 `derived_stage` Apotheosis reconciliation is limited to compiled `tier_threshold_unlock` hooks
- reconciliation runs on player login/respawn and on a bounded periodic server-level pass (target cadence: once per second per loaded level)
- the tick pass evaluates only loaded players in that level and only compiled active hooks for the relevant integration
- repeated evaluations must be idempotent and rely on normal stage-service duplicate handling rather than extra retry loops

---

## 2. Rule Engine Performance Model

Rule runtime architecture must be scope-indexed.

### 2.1 Rule Index Buckets

Minimum scope buckets:
- `world`
- `player`
- `entity`
- `spawn_event`
- future scopes (`loot`, `invasion`, `event_context`, others when promoted)

Rules must be indexed by `execution_scope` during reload.

Hard rule:
- runtime evaluation inspects only the active scope bucket.

Example:

```text
spawn_event
  -> evaluate spawn_event rule bucket only
```

Never:

```text
spawn_event
  -> scan all rules
```

### 2.2 Complexity Contract

- target complexity per event pass is `O(bucket_size)`, never `O(total_rules)`.
- selector/context matching must run on precompiled matcher structures, not raw JSON or ad-hoc parsing.

---

## 3. Rule Evaluation Limits

Recommended limits:

| Limit | Recommended value | Intent |
| --- | --- | --- |
| `maximum_rules_per_bucket` | `500` | Keep per-scope datasets bounded and operationally reviewable. |
| `maximum_rules_evaluated_per_event` | `50` | Bound worst-case event-pass cost. |
| `maximum_actions_per_rule` | `10` | Prevent oversized action payloads in one candidate. |

Budget handling contract:
- limits are validated and surfaced as diagnostics.
- when limits are exceeded, emit validation warnings at minimum.
- policy may optionally disable an offending rule/object branch when configured.
- budget warnings must never crash the server.

Runtime enforcement rules:
- `maximum_rules_evaluated_per_event` must stop evaluation at a deterministic boundary in the already-deterministic candidate order.
- when event-pass truncation occurs, emit `WA_PERF_RULE_EVENT_LIMIT_EXCEEDED` with event type, scope bucket, evaluated count, limit, and trace context when available.
- enforcement must not add hidden rerolls/retries to compensate for truncation.
- `maximum_actions_per_rule` overflow must emit `WA_PERF_RULE_ACTION_COUNT_EXCEEDED`.
- validation must warn at minimum for oversized action lists; policy may disable the offending object/branch.
- action-overflow paths must never execute oversized action lists unpredictably.

Stable ordering requirement:
- candidate order must be deterministic before truncation.
- hash-map iteration order must never be used as execution/truncation order.

---

## 4. Spawn-Time Mutation Budget

Spawn processing is a critical hot path and must remain tightly bounded.

Recommended limits:

| Limit | Recommended value | Intent |
| --- | --- | --- |
| `max_mutators_per_spawn` | `8` | Bound candidate application complexity per entity spawn. |
| `max_components_per_mutator` | `10` | Bound component resolution complexity per selected mutator. |

Budget handling contract:
- if limits are exceeded, apply deterministic truncation or validation failure based on active policy.
- resolution outcomes must be deterministic for identical inputs.
- no budget overflow path may trigger unbounded retries.

## Mutator Evaluation Guardrail

Mutator evaluation must remain bounded even in large modpacks.

Hard requirements:
- pool selection must occur before mutator evaluation
- selected-pool `mutation_chance` must be evaluated after pool selection and before mutator evaluation
- the initial chance is rolled once; conditional additional rolls are bounded by `max_mutators_per_entity`, the global effective cap, and the hard spawn budget
- curated combinations must be reload-compiled and selected as whole weighted units; spawn evaluation must not parse their JSON or scan unrelated pools
- an additional-roll failure ends selection immediately; selection must not reroll indefinitely to fill a requested count
- mutator evaluation must operate only on candidate pools
- selector indexing must prevent full pool scans per spawn
- component-count guardrails must prevent unbounded stacking

Recommended limits:
- max candidate pools per spawn context should remain small
- max mutators applied per entity must be bounded
- component-count guardrails must remain enforceable even under forced debug commands
- forced debug pool/mutator paths may bypass chance gates but must still remain bounded by the same mutator/component guardrails

Performance goal:
- mutator systems must not introduce per-spawn evaluation costs proportional to total defined pools or mutators

---

## External Spawn Controller Coexistence Guardrail

Spawn-path performance and safety must account for other mods that also modify spawn outcomes.

Hard requirements:
- World Awakened must not perform expensive mutation-pool evaluation on spawn events that are already cancelled or invalidated
- World Awakened must exit early when upstream hooks have denied the spawn
- World Awakened must evaluate only the final surviving spawn context made available by the hook chain
- World Awakened must not perform recursive mutation processing for redirected or replacement spawns unless explicitly marked safe by policy

Performance/safety goal:
- avoid wasted hot-path work on spawns that another mod has already blocked
- avoid duplicate processing when multiple mods share the same spawn hook chain
- avoid recursive spawn explosions caused by indirect entity creation during mutation application

Recommended diagnostics:
- `WA_SPAWN_SKIPPED_UPSTREAM_CANCEL`
- `WA_SPAWN_CONTEXT_INVALIDATED`
- `WA_SPAWN_REENTRY_BLOCKED`
- `WA_SPAWN_EXTERNAL_TRANSFORM_DETECTED`

Recommended observability:
Current `/wa debug mutators` and future `/wa debug perf` outputs should expose:
- spawn events skipped due to upstream cancellation
- external-transform detection count
- re-entry blocks
- mutation evaluations avoided by early-exit checks

## Ownership-Safe Reconciliation and Carrier Guardrail

Runtime reconciliation must remain bounded, event-driven, and ownership-scoped.

Hard requirements:
- player reward reconciliation must target World Awakened-owned identifiers only
- carrier rebuilds must be event-driven, not broad per-tick rescans
- reconciliation must not scan or rewrite arbitrary third-party modifier/effect/visual collections beyond what is necessary to locate World Awakened-owned contributions
- removed or missing definition handling must not trigger repeated expensive rebuild loops
- degraded branches must fail once per reconcile/event path with bounded diagnostics behavior
- client visual carrier sync must be change-driven, not tick-spammed

Performance/safety goal:
- avoid broad attribute/effect rewrites
- avoid repeated failed reconciliation churn
- preserve compatibility while keeping login/respawn/reconcile cost bounded
- keep carrier systems cheap by treating them as derived cached state, not a per-tick simulation engine

Personal advancement catch-up guardrail:
- login reconciliation inspects only enabled, player-scoped, one-shot `advancement_completed` trigger definitions
- consumed triggers are removed before advancement-state lookup
- advancement IDs are deduplicated before replay
- replay uses the normal compiled trigger matcher and does not enumerate or synthesize unrelated advancements

Phase-linked Ascension reconciliation is also event-bounded: phase unlock evaluates the compiled offer set once for the currently online players, while login/respawn performs one eligibility pass for that player. Automatic grants use one stable per-offer source key, so reload/login reconciliation cannot create a new once-per-source identity on every pass. Pending/resolved once-per-player checks occur before candidate selection, avoiding repeated reward-pool work for already-owned offers.

## Ownership-Safe Mutator Application Guardrail

Spawn-time mutator application must remain bounded, ownership-scoped, and fail-closed on unavailable runtime surfaces.

Hard requirements:
- mutator application must target World Awakened-owned runtime identities/projections only
- mutator handlers must not broadly rewrite foreign entity modifier/effect/capability surfaces to force compatibility
- unavailable entity capabilities/hooks/runtime surfaces must fail closed once per relevant branch/event path with bounded diagnostics behavior
- removed-definition provenance checks must not trigger repeated expensive retries during the same spawn/event path
- degraded mutator branches must not cause recursive spawn reprocessing or repeated rebuild loops
- mutation combat events may inspect only the attacking/dying mob's bounded WA-owned compiled state; they must not scan mutation definitions or parse JSON
- per mob, live `on_hit_effect` state is capped at 8 entries, lifesteal is pre-aggregated to at most `1.0`, and death explosion state stores one strongest power
- lifesteal overkill accounting may retain only weak, in-flight pre-damage health snapshots; it must not retain entities or create an unbounded combat history
- death explosions must suppress synchronous WA explosion chains and use no block interaction

Performance/safety goal:
- keep spawn evaluation deterministic and cheap even in incompatible mixed-mod environments
- preserve third-party entity state while still reporting actionable reasons for skipped mutator branches
- avoid churn from repeatedly reattempting the same unavailable runtime surface in one spawn path

--- 

## 4A. Phase 7 Loot/Reward Evaluation Guardrail

Phase 7 reward evolution runs on hot gameplay-event paths and must remain bounded.

Hard requirements:
- reward evaluation must start from explicit WA-owned reward-capable events only
- all reward-capable subsystems must feed one canonical resolver pipeline (intent/contributor gather -> resolve -> apply once)
- canonical loot-context builder must produce stable field shapes (`loot_context_type`, `target_type`, `target_id`, `loot_table_id`, `entity_type`, mutation context, player/dimension, stage/invasion context, compat state, scalar input policy context) across all reward-capable sources
- loot/reward matching must run on reload-compiled structures and target-indexed candidate sets
- runtime must not scan all `loot_profiles` for each reward-capable event when narrower target indexing exists
- contributor collection must be bounded and deterministic; subsystem-local direct apply bypass paths are forbidden
- identical evaluation snapshots must produce identical matched/rejected profile decisions and final reward outcomes
- same-event duplicate contributor/profile applications must be blocked by default unless explicit repeatable policy allows them
- destructive reward modes must pass explicit policy checks without introducing unbounded retries
- Phase 7 reward evaluation must not invoke difficulty/challenge scalar composition paths
- reward evaluation must not mutate progression state (stage unlocks/trigger eligibility/rule identity)

Observability requirements:
- debug/evaluate outputs for loot/reward flows should expose source event, candidate ordering, rejection reasons, and final applied WA-owned reward outcomes
- performance diagnostics for loot/reward passes should remain trace-correlated with the same root evaluation trace IDs used by other subsystems

Periodic invasion scheduling remains once per second on the canonical Overworld scheduler tick. For each compiled periodic profile it resolves at most one allowed loaded dimension containing players, then evaluates that profile against the dimension snapshot. It must not scan entities, chunks, or unloaded dimensions.

--- 

## 5. Action Chain Depth

Rule/action recursion must remain bounded.

Runtime contract:
- evaluation is snapshot-based and single-pass.
- actions cannot trigger additional rule evaluation in the same pass.

Canonical bound:
- `max_action_chain_depth = 1`

Meaning:
- one event pass may evaluate rules and execute its bounded action queue once.
- same-pass re-entry into rule matching is forbidden.

---

## 6. Reload Compilation Guarantees

During datapack reload, the engine must:
- compile rule conditions
- compile entity selectors
- build scope index tables
- validate mutator composition
- validate performance budgets

Runtime must never parse datapack JSON for hot-path rule or spawn mutation evaluation.

Atomicity rule:
- runtime uses either the previous compiled graph or the new compiled graph after reload completion, never a mixed graph.

---

## 7. Debug Performance Tools

Required future debug command surfaces:
- `/wa debug perf`
- `/wa debug rules`
- `/wa debug mutators`

These surfaces should report at minimum:
- scope bucket sizes
- event-pass rule evaluation counts
- spawn mutator and component counts
- rule evaluation timing summaries
- budget-limit exceedance diagnostics

Canonical payloads and trace integration must align with [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md).

---

## 8. Phase Alignment (MVP Roadmap)

This contract aligns to future implementation phases as follows:
- Phase 5: enforce spawn-time mutator/component count guardrails and deterministic overflow behavior
- Phase 6: enforce per-event rule/action budget guardrails with deterministic truncation boundaries, diagnostics-first policy, and stable ordering guarantees
- Phase 7: enforce deterministic and bounded loot/reward profile evaluation on explicit reward-capable event paths
- Native editor settings foundation: implemented overrides are constant-time runtime reads; the mutation cap can only tighten the existing hard budget, and bundled-profile changes compile off the hot path before publication
- Native mutation-pool editing cannot raise the engine hard cap above eight mutators per mob; the existing global and authored caps may tighten it further. Registry enumeration and living-type checks occur only when an authorised operator opens the editor, never in spawn evaluation.
- Native Difficulty Tracks compile during profile publication into the existing pool-condition structures. Spawn evaluation does not scan tracks or progression stages: each tier pool carries precompiled `stage_unlocked` and higher-tier `stage_locked` gates, and ordinary selector indexing remains authoritative.
- Later native editor work: expose richer component-budget warnings in guided mutator-definition workflows

### Native-editor phase condition groups

- A phase group is capped by the UI at three conditions.
- Each event evaluates an ordinary scope-indexed trigger rule and performs at most three bounded trigger-counter lookups.
- Completion is stored in existing trigger SavedData; no collection-wide scan, expression tree, or second progression engine is introduced.
- Login reconciliation replays only inspectable evidence (completed advancements, recorded/current dimensions, and current world day). Historical kills and manual events are never invented.
- Hardening: ship `/wa debug perf|rules|mutators` observability surfaces and verify editor apply/reload workflows cannot bypass compiled hot-path indexing, budget limits, or deterministic ordering guarantees

Roadmap sync rule:
- if phase scope changes affect hot-path limits or budget policy, update this file and `SPECIFICATION.md` in the same change.

---

## 9. Contributor Update Rules

- Keep limits and examples aligned with authoritative rule/mutator runtime contracts.
- Keep diagnostics mapping synchronized with [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md).
- Keep debug command expectations synchronized with [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md).

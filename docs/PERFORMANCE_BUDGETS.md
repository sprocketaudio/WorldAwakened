# World Awakened Performance Budgets and Runtime Guardrails

Canonical contract for hot-path runtime limits, indexing guarantees, and evaluation guardrails.

- Document status: Active shared-contract reference
- Last updated: 2026-03-12
- Scope: Rule engine and spawn-time mutation performance contracts

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

- World Awakened must scale to large datapacks (including thousands of authored objects) without degrading server TPS.
- Runtime evaluation must never perform unbounded scans across full rule sets for hot-path events.
- Hot-path evaluators must operate on precompiled structures produced during datapack reload.
- Runtime performance protections must fail closed per object/branch rather than destabilizing unrelated systems.

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
- Phase 5: enforce spawn-time mutator/component budgets and deterministic overflow behavior
- Phase 6: enforce per-event rule/action budget guardrails and diagnostics-first policy
- Phase 10: expose performance-budget warnings in web authoring validation workflows
- Phase 11: harden performance telemetry and ship `/wa debug perf|rules|mutators` observability surfaces

Roadmap sync rule:
- if phase scope changes affect hot-path limits or budget policy, update this file and `SPECIFICATION.md` in the same change.

---

## 9. Contributor Update Rules

- Keep limits and examples aligned with authoritative rule/mutator runtime contracts.
- Keep diagnostics mapping synchronized with [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md).
- Keep debug command expectations synchronized with [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md).

# World Awakened Documentation Map and Update Contract

Documentation index, read-order contract, and cross-update policy for all repository markdown docs.

- Document status: Active governance index
- Last updated: 2026-03-13
- Scope: Required documentation intake, cross-references, and currency rules

---

## 0. Purpose

This file defines:
- which docs must be read before implementation work
- how docs reference each other
- which docs must be updated together when contracts change
- the standard format expected for shared reference docs

Use this as the primary index for the `docs/` folder.

---

## 1. Required Read Order for Every Task

Minimum required docs before any code or doc changes:
1. [AGENTS.md](../AGENTS.md)
2. [README.md](../README.md)
3. [SPECIFICATION.md](SPECIFICATION.md)
4. [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
5. [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
6. [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
7. [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
8. [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
9. [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
10. [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
11. [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
12. [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
13. [PRESET_CATALOG.md](PRESET_CATALOG.md)
14. [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)

Read when relevant to scope changes:
1. [wiki/README.md](wiki/README.md) when changes affect user-facing workflows, operator flows, testing guidance, troubleshooting, or author mental models
2. [FUTURE_IDEAS.md](FUTURE_IDEAS.md) for post-v1 backlog promotions/reclassifications
3. [FUTURE_ADMIN_UI.md](FUTURE_ADMIN_UI.md) for deferred in-game/admin runtime UI promotions/reclassifications

Task execution rule:
- Each implementation task must explicitly reference the governing docs it used.
- Do not implement against stale docs; align docs before or with code changes.

---

## 2. Documentation Set Map

Core contracts:
- [SPECIFICATION.md](SPECIFICATION.md): Primary design and runtime behavior contract.
- [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md): Canonical datapack shape and authoring contract.
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md): Browser authoring/validation tool contract.
- [README.md](../README.md): Project summary and status.
- [AGENTS.md](../AGENTS.md): Contributor and coding-agent operating contract.

Shared reference docs:
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md): Component IDs, schemas, status, and composition contracts.
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md): Canonical duplicate/conflict/order/budget/no-op resolver contract.
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md): Condition IDs, wrappers, and status contracts.
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md): Action IDs, scope legality, and status contracts.
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md): Canonical scope guarantees and legality matrix.
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md): Command, trace, and provenance inspection contract.
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md): Hot-path performance budgets, scope indexing rules, and runtime guardrails.
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md): Stable diagnostics taxonomy.
- [PRESET_CATALOG.md](PRESET_CATALOG.md): Canonical preset/template catalog and status tags.

Human-friendly companion docs:
- [wiki/README.md](wiki/README.md): Human-readable wiki index and maintenance rules.
- [wiki/CONCEPTS.md](wiki/CONCEPTS.md): Plain-language mental model for progression, scopes, and ascension.
- [wiki/QUICKSTARTS.md](wiki/QUICKSTARTS.md): Shortest-path setup and testing flows.
- [wiki/OPERATOR_GUIDE.md](wiki/OPERATOR_GUIDE.md): Practical command and recovery handbook.
- [wiki/DATAPACK_COOKBOOK.md](wiki/DATAPACK_COOKBOOK.md): Copyable authoring recipes with explanations.
- [wiki/TROUBLESHOOTING.md](wiki/TROUBLESHOOTING.md): Problem-first debugging guidance.
- [wiki/FAQ.md](wiki/FAQ.md): Concise answers to common operator/author questions.

Deferred scope docs:
- [FUTURE_IDEAS.md](FUTURE_IDEAS.md): Non-normative backlog.
- [FUTURE_ADMIN_UI.md](FUTURE_ADMIN_UI.md): Deferred post-v1 in-game/admin runtime UI concept.

---

## 3. Cross-Update Matrix

When updating shared framework contracts (conditions/actions/scopes/status taxonomy):
- Update [SPECIFICATION.md](SPECIFICATION.md)
- Update [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- Update [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- Update [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- Update [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- Update [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- Update [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- Update [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- Update [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- Update [README.md](../README.md)
- Update [AGENTS.md](../AGENTS.md)

When updating component IDs/schemas/composition/runtime support:
- Update [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- Update [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- Update [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md)
- Update [SPECIFICATION.md](SPECIFICATION.md)
- Update [PRESET_CATALOG.md](PRESET_CATALOG.md) if preset composition guidance changes
- Update [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md) if tooling behavior/labels change

When updating presets/templates:
- Update [PRESET_CATALOG.md](PRESET_CATALOG.md)
- Update [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md), [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md), and [ACTION_REFERENCE.md](ACTION_REFERENCE.md) if canonical mappings change
- Update [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md) when examples or object-shape guidance changes

When updating behavior that changes user understanding, testing flows, command usage, or troubleshooting:
- Update the affected docs under [wiki/README.md](wiki/README.md)
- Keep the wiki wording plain-language, example-driven, and practical
- Do not leave a concept explained only in the technical reference docs if it is likely to confuse operators or pack authors

When updating command surfaces, operator UX, or runtime output layering:
- Update [SPECIFICATION.md](SPECIFICATION.md)
- Update [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- Update affected human docs under [wiki/README.md](wiki/README.md), especially [wiki/OPERATOR_GUIDE.md](wiki/OPERATOR_GUIDE.md), [wiki/TROUBLESHOOTING.md](wiki/TROUBLESHOOTING.md), and [wiki/FAQ.md](wiki/FAQ.md)
- Update [AGENTS.md](../AGENTS.md) if the change alters contributor expectations for future command additions
- Keep the concise-vs-debug output split explicit rather than implied by examples

When promoting deferred scope into active scope:
- Update [SPECIFICATION.md](SPECIFICATION.md)
- Update [README.md](../README.md)
- Update [AGENTS.md](../AGENTS.md)
- Update [FUTURE_IDEAS.md](FUTURE_IDEAS.md) and/or [FUTURE_ADMIN_UI.md](FUTURE_ADMIN_UI.md)
- Update any impacted reference docs in the same change

---

## 4. Shared Reference Doc Format Standard

These files must stay aligned to a common structure and detail level:
- [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md)
- [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md)
- [CONDITION_REFERENCE.md](CONDITION_REFERENCE.md)
- [ACTION_REFERENCE.md](ACTION_REFERENCE.md)
- [SCOPE_MATRIX.md](SCOPE_MATRIX.md)
- [DEBUG_AND_INSPECTION.md](DEBUG_AND_INSPECTION.md)
- [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md)
- [VALIDATION_AND_ERROR_CODES.md](VALIDATION_AND_ERROR_CODES.md)
- [PRESET_CATALOG.md](PRESET_CATALOG.md)

Required format elements:
- title and one-line purpose summary
- metadata block (`Document status`, `Last updated`, `Scope`)
- governance/maintenance section with related-doc links
- canonical rules section(s)
- contributor/update rules section

Required detail level:
- explicit status taxonomy usage (`implemented`, `planned`, `reserved`, `deprecated`) where applicable
- concrete schema/shape notes for typed nodes where applicable
- scope legality and fail-closed behavior notes where applicable
- examples for canonical usage patterns

---

## 5. Human-Friendly Wiki Doc Style Standard

These files exist to make the technical contracts understandable to real users:
- [wiki/README.md](wiki/README.md)
- [wiki/CONCEPTS.md](wiki/CONCEPTS.md)
- [wiki/QUICKSTARTS.md](wiki/QUICKSTARTS.md)
- [wiki/OPERATOR_GUIDE.md](wiki/OPERATOR_GUIDE.md)
- [wiki/DATAPACK_COOKBOOK.md](wiki/DATAPACK_COOKBOOK.md)
- [wiki/TROUBLESHOOTING.md](wiki/TROUBLESHOOTING.md)
- [wiki/FAQ.md](wiki/FAQ.md)

Required style:
- plain language before internal engine jargon
- explain why, not just what
- prefer examples, workflows, and decision rules over abstract taxonomy
- write for operators, pack authors, testers, and server owners
- explicitly call out common mistakes and recovery paths

Maintenance rule:
- if a behavior change required explanation in a conversation, testing session, or review, update the relevant wiki docs in the same task
- keep examples and commands current with the actual implemented command surface
- do not let the wiki drift into stale marketing copy or vague summaries

---

## 6. Linking and Path Rules

Inside `docs/`:
- Prefer sibling links (for example `[SPECIFICATION.md](SPECIFICATION.md)`).
- Link root docs with `../` (for example `[README.md](../README.md)` and `[AGENTS.md](../AGENTS.md)`).
- Avoid `docs/...` prefixes from inside `docs/` files.

At repository root:
- Link docs with `docs/...` paths.

---

## 7. Currency Checklist

Before merging:
1. Confirm all changed behavior/contracts are reflected in docs.
2. Confirm any affected human-facing workflows or explanations are reflected in `docs/wiki/`.
3. Confirm all cross-doc links resolve to existing files.
4. Confirm every touched doc updates its `Last updated` date when content changed.
5. Confirm reference docs remain format-aligned.
6. Confirm stale or orphan docs are removed or redirected.

# World Awakened Future In-Game/Admin Runtime Authoring and Inspection UI

Deferred future design for an in-game/admin runtime-facing inspection and authoring interface.

- Document status: Non-normative future feature
- Last updated: 2026-03-13
- Scope: Post-v1 only

---

Related contracts:
- [docs/README.md](README.md)
- [SPECIFICATION.md](SPECIFICATION.md)
- [WEB_AUTHORING_TOOL_SPEC.md](WEB_AUTHORING_TOOL_SPEC.md)
- [README.md](../README.md)
- [AGENTS.md](../AGENTS.md)

Update rule:
- Update this file when deferred in-game/admin runtime UI scope is promoted, narrowed, or split.
- Keep this file aligned with active-scope boundaries in `SPECIFICATION.md` and backlog handling rules in `AGENTS.md`.

---

## 0. Status

This feature is:
- deferred
- post-v1
- not required for MVP

Scope boundary note:
- browser-based datapack authoring/validation for v1 is now active scope and is defined in `docs/WEB_AUTHORING_TOOL_SPEC.md`
- this file remains focused on deferred in-game/admin runtime inspection and advanced live-authoring concepts

It should only be considered after the core data model, rule engine, validation flow, and debugging surface are proven stable.

Hard rule:
- this document does not expand current implementation scope by itself
- `docs/SPECIFICATION.md` remains the implementation contract

---

## 1. Purpose

Provide an in-game or companion admin-facing UI that makes World Awakened content easier to understand, inspect, test, and eventually author than raw text editing alone.

Core design rules:
- the UI must make authoring easier than manual JSON editing
- the UI must not become a second competing rules system
- datapack content remains the source of truth unless a later generated-datapack workflow is explicitly adopted

This feature is intentionally staged so the project does not accidentally become three separate products at once.

---

## 2. Problem Statement

World Awakened is intentionally data-driven, which is powerful but can become intimidating once the number of stages, rules, mutators, offers, loot profiles, and invasion profiles grows.

A future UI should solve:
- loaded content is hard to visualize at a glance
- debugging rule interactions from text alone is slow
- authoring complex JSON by hand is error-prone
- pack makers benefit from guided forms, previews, and validation
- admins may want to test or inspect systems without opening files

Goal:
Reduce friction without introducing more ceremony.

---

## 3. Non-Goals

The first version of any admin UI should not:
- replace the datapack system entirely
- introduce a scripting language
- bypass validation rules
- silently generate hidden state outside normal content paths
- allow arbitrary client-side authority over gameplay logic
- become required for using the mod

The UI must remain optional and additive.

---

## 4. Staged Delivery Model

This feature should be developed in three stages.

### 4.1 Stage A: Inspection UI

Purpose:
Provide a clean visual way to understand loaded content and current runtime behavior without editing raw files.

Core capabilities:
- view loaded World Awakened content
- inspect active state
- inspect why rules matched or failed
- inspect stages, mutators, invasions, ascension offers, and related objects

### 4.2 Stage B: Assisted Editing UI

Purpose:
Make content easier to create and modify than hand-writing JSON while still keeping datapacks as the final artifact.

Core capabilities:
- duplicate existing objects
- edit selected fields through forms
- preview resulting JSON
- export JSON for a datapack
- avoid writing directly into live authoritative content by default

### 4.3 Stage C: Generated Datapack Authoring UI

Purpose:
Allow a pack maker or admin to build and maintain a generated datapack through the UI.

Core capabilities:
- create or edit content fully through forms
- save into a generated datapack workspace
- support validation and packaging
- optionally reload generated content safely

Recommended implementation order:
1. Stage A
2. Stage B
3. Stage C

Hard rule:
- do not attempt full authoring UI before the core schema has stabilized and real users have authored datapacks manually

---

## 5. Stage A: Inspection UI

Required capabilities:
- browse loaded stages
- browse trigger rules
- browse generic rules
- browse mutators
- browse mutation pools
- browse loot profiles
- browse invasion profiles
- browse ascension rewards
- browse ascension offers
- browse compatibility and integration status

Per-object inspection should show:
- ID
- resolved component list for component-based definitions (mutations and ascension rewards)
- enabled or disabled state
- source datapack namespace or path when available
- key fields
- validation status
- linked object references
- why the object is active or inactive when relevant

Runtime inspection should show:
- active stages
- active world scalars
- active invasions
- active integration gates
- player ascension state
- selected mutators on inspected entities
- recently triggered rules if debugging is enabled

Ownership/fail-closed visibility should include:
- WA-owned vs foreign-state-preserved markers on inspected player/entity surfaces where relevant
- failed-closed mutator branch reasons for unavailable runtime surfaces/capabilities/hooks
- persisted runtime-provenance entries that reference removed/missing definitions
- canonical diagnostic code display for ownership and fail-closed outcomes (not just free-form text)

Value:
This phase delivers high usability without taking on the complexity of full authoring.

---

## 6. Stage B: Assisted Editing UI

Core model:
- load existing object
- display editable form
- allow duplication or cloning
- edit fields through controls
- validate live
- preview canonical JSON
- export JSON for use in a datapack

Recommended editable actions:
- duplicate an existing rule
- duplicate a mutation definition preset
- duplicate an ascension offer
- duplicate a loot profile
- edit IDs, names, conditions, actions, tags, and basic filters
- edit component arrays for mutation and ascension reward definitions (type, params, conditions, exclusions/conflicts)
- edit weighting and priority values
- preview references and validation issues

Important design choice:
- Stage B should prefer exportable JSON over saving directly into live runtime content

Why:
This keeps the datapack as source of truth and avoids hidden edits.

---

## 7. Stage C: Generated Datapack Authoring UI

Possible capabilities:
- create new datapack workspace
- create or edit World Awakened object types through forms
- auto-generate JSON files
- generate folder structure
- validate generated content
- package or export the datapack
- optionally place the generated datapack in the server datapacks folder
- optionally request a safe reload

Hard rules:
- generated files are still normal datapack files
- generated content must remain human-readable
- manual editing of generated files must remain possible
- no opaque database-only authoring model for the first version
- avoid creating content that only the UI can understand

---

## 8. User Types

The UI should consider at least three user roles.

### 8.1 Operator or Admin

Needs:
- inspect state
- trigger debug tools
- see rule failures
- test content quickly

### 8.2 Pack Maker or Designer

Needs:
- create stages, offers, mutators, and rules
- validation and previews
- exportable content

### 8.3 Advanced Technical User

Needs:
- reliable visualization and helper tooling
- round-trip-safe export behavior
- freedom to continue editing JSON directly

The UI should not punish advanced users for understanding the underlying format.

---

## 9. Design Principles

1. The UI must be simpler than JSON editing.
2. Datapack remains source of truth, especially in early versions.
3. Round-trip safety matters.
4. Validation should be immediate and readable.
5. Object relationships should be visible.
6. Live runtime editing should be treated cautiously.
7. Security and authority remain server-side.

Expanded rules:
- no unnecessary nesting
- no hidden complexity
- no multi-screen maze for basic edits
- load, edit, and export should not silently destroy fields or semantics
- the server validates and controls all meaningful state changes

---

## 10. Scope of UI Support By Object Type

The UI should eventually support:
- stages
- trigger_rules
- rules
- ascension_rewards
- ascension_offers
- mob_mutators
- mutation_pools
- loot_profiles
- invasion_profiles
- integration_profiles

Recommended rollout:
1. stages
2. ascension rewards and offers
3. mutators and pools
4. rules and triggers
5. loot and invasions
6. integration profiles

Reason:
Some object types are much easier to present cleanly than others.

---

## 11. Required Inspector Features

### 11.1 Search and Filter

- by ID
- by namespace
- by object type
- by enabled or disabled state
- by validation status
- by tags

### 11.2 Object Detail Panel

- display important fields in readable form
- show linked object references
- show source file path when known
- show validation warnings and errors

### 11.3 Runtime State Panels

- current unlocked stages
- active world progression mode
- current invasions
- integration activation states
- player ascension summary
- selected mutators on target entity

### 11.4 Debug Explanation View

- why an object matched
- why an object did not match
- cooldown, one-shot, and chance status
- selector success or failure
- config gate status

This is the most valuable early UI surface.

---

## 12. Required Assisted Editing Features

A useful Stage B editing experience should include:
- create from template
- duplicate existing object
- edit as form
- preview raw JSON
- validate now
- show linked references
- export object
- export full object set

Preferred early editing targets:
- ascension rewards
- ascension offers
- stages
- mutators

Component-model requirement for these editors:
- preset names remain authored IDs
- behavior controls are edited at the component list level
- no UI mode should reintroduce hardcoded preset-name behavior

These are easier to form-edit than full generic rule graphs.

---

## 13. Rule Editor Caution

The generic rule system is the most dangerous thing to represent poorly in UI.

Risks:
- nested condition trees become unreadable
- complex action chains become form-spaghetti
- users may create logic they do not understand
- the UI may become slower and more confusing than JSON

Recommendation:
- do not begin with a full freeform rule editor
- provide visual inspection of rules first
- support cloning small or safe rule patterns
- provide guided editors for simple rule templates before allowing arbitrary complex rule composition

Possible safe templates:
- unlock stage when X happens
- grant ascension offer on stage unlock
- apply mutator pool in dimension
- trigger invasion on boss death

Goal:
Use templates before offering raw graph editing.

---

## 14. Round-Trip and Serialization Rules

If the UI edits or generates World Awakened objects, it must follow these rules:
- canonical output remains valid JSON
- output preserves `schema_version` when present
- unknown fields are not silently discarded when they can be preserved
- export format remains human-readable
- references are validated before export
- canonical field ordering is recommended for readability

Round-trip goal:
- load object
- inspect or edit
- export

The result should remain semantically equivalent unless the user intentionally changed something.

---

## 15. Validation UX Requirements

The UI should surface validation more helpfully than raw logs alone.

Validation UX should support:
- field-level errors
- object-level warnings
- reference resolution warnings
- impossible condition warnings
- integration gating warnings
- schema version incompatibility warnings

Severity bands:
- error
- warning
- info

The UI should never pretend an invalid object is safe.

---

## 16. Runtime Authoring vs Offline Authoring

This must be explicitly separated.

Offline or generated-datapack authoring:
- safest
- easiest to reason about
- easiest to version control
- preferred default

Live runtime authoring:
- convenient
- riskier
- may be unsafe during active gameplay
- should be restricted and carefully validated

Recommendation:
- treat live runtime editing as a later advanced feature, not the first version of the UI

---

## 17. Multiplayer and Server Considerations

In multiplayer or dedicated-server environments:
- only authorized users should access admin authoring tools
- viewing runtime state may be less privileged than editing content
- content generation or export should be server-controlled or clearly scoped to an authorized operator workflow
- client-side UI must not become a trust-boundary bypass

Possible permission tiers:
- viewer
- operator
- content editor
- full admin

At minimum:
- normal players should not have authoring access

---

## 18. Debug and Testing Integration

The admin UI should eventually integrate with World Awakened debug systems.

Useful capabilities:
- test a stage unlock
- test a trigger rule
- simulate an ascension offer
- inspect an entity's mutator state
- start or stop an invasion
- view the last evaluation trace for a selected object

This is another reason Stage A is more valuable early than full authoring.

---

## 19. Export Model Options

Possible export approaches:

Option A: export single object JSON
- simplest
- good for copy or paste workflows

Option B: export object bundle
- selected related objects exported together

Option C: export generated datapack
- full folder structure created automatically

Recommended progression:
1. A first
2. then B
3. then C

This mirrors complexity growth sensibly.

---

## 20. Technical Architecture Options

Possible implementation forms:
- in-game admin GUI
- external companion desktop or web editor
- hybrid approach

In-game GUI pros:
- immediate access
- direct runtime inspection
- integrated with server state

In-game GUI cons:
- limited screen space
- harder complex form UX
- more networking and permission complexity

External tool pros:
- richer interface
- easier large-form editing
- easier export and packaging workflows

External tool cons:
- less immediate runtime context
- separate distribution and maintenance burden

Pragmatic recommendation:
- in-game inspector first
- external-style form complexity only if clearly needed later

---

## 21. MVP For This Future Feature

If this future feature begins, the recommended first usable version is:
- read-only inspector UI
- search and filter support
- object detail view
- validation summary view
- runtime state view
- debug trace summary view
- no full authoring yet

Second milestone:
- duplicate, edit, and export for simple objects
- especially stages and ascension objects

Third milestone:
- guided editing for more complex object types

This staged approach reduces project risk.

---

## 22. Major Risks

Primary risks:
- building a UI that is harder than raw JSON editing
- creating a second hidden rules system
- poor round-trip behavior that loses information
- weak validation causing broken exports
- overbuilding before the schema stabilizes
- trying to support arbitrary rule graphs too early

Mitigation:
- inspector first
- templates before freeform editors
- datapack remains source of truth
- export JSON early
- do not hide complexity behind fake simplicity

---

## 23. Success Criteria

This feature succeeds only if:
- it is genuinely easier than manual text editing for common tasks
- it improves debugging and understanding of World Awakened content
- it preserves the datapack-first model unless intentionally changed
- it does not compromise server authority or validation rules
- it reduces authoring mistakes rather than creating new ones

If it fails these tests, it should not be shipped in that form.

---

## 24. Final Design Rule

The admin UI is a convenience layer, not the core engine.

Hard line:
- World Awakened content rules still live in data
- World Awakened runtime rules still live in Java
- the UI exists to inspect, assist, and eventually generate data
- the UI must never become more painful than editing the underlying files directly

If the UI becomes slower, more confusing, or more restrictive than raw authoring, it has failed its purpose.



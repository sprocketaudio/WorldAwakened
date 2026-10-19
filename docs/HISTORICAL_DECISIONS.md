# World Awakened Historical Decisions

Context retained for contributors without mixing retired plans into the active contract.

- Document status: Historical reference
- Last updated: 2026-10-19
- Scope: Retired designs, migration context, and completed delivery history

---

## How To Use This Document

This document explains *why* older documents or persisted data may mention terms that are no longer normal operator-facing concepts. It does not create implementation requirements.

For current behavior, use [SPECIFICATION.md](SPECIFICATION.md), then the relevant subsystem reference selected by [README.md](README.md).

## Retired Designs

The Difficulty Track *schema* and its tier-named editor workflow were retired before the first release. Their useful
capabilities were retained as direct Phase Mutation Configurations: ordered phase assignments, dimension-specific
defaults, specialised mob configurations, precompiled stage gates, and ordinary mutation-pool composition all remain
supported. The retired `difficulty_tracks` payload and save key are not accepted schemas or save formats.
The WorldAwakened `HYBRID` progression mode was also retired before the first release. Stage-ID aliases remain a supported advanced datapack compatibility feature, although they are intentionally absent from the simplified World Phases editor.
The hosted web authoring proposal is retired; `/wa config` is the active authoring path.

## Historical Material Locations

- [SPECIFICATION.md](SPECIFICATION.md) section 21 keeps the milestone timeline for archaeology and release notes.
- [SPECIFICATION.md](SPECIFICATION.md) section 25 keeps older completion notes.
- [FUTURE_IDEAS.md](FUTURE_IDEAS.md) contains non-normative future ideas.
- [FUTURE_ADMIN_UI.md](FUTURE_ADMIN_UI.md) remains a superseded historical proposal.

When a historical statement disagrees with a current contract, the current contract wins. Do not restore retired
schemas or editor flows merely because historical documentation mentions them; do preserve equivalent advanced
runtime and datapack capability.

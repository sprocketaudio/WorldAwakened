# Retired Hosted Web Authoring Plan

- Document status: Retired
- Last updated: 2026-10-17
- Scope: Historical pointer only

The hosted React/Next.js authoring frontend, relay service, and live-linked browser session plan are no longer active World Awakened product requirements.

World Awakened is moving to:

- playable built-in defaults loaded through the normal datapack engine
- a native, server-authoritative NeoForge configuration interface opened with `/wa config`
- server-derived registry catalogues for searchable selectors
- a simplified outcome-focused authoring model that infers internal scopes where safe
- continued raw datapack support for advanced authors

The active audit, architecture decisions, risks, and delivery sequence are in [SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md](SIMPLIFICATION_AND_DEFAULT_GAMEPLAY.md). The canonical runtime and datapack contracts remain in [SPECIFICATION.md](SPECIFICATION.md) and [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md).

The existing root-level `web/` workspace is not an active deliverable and must not be treated as runtime authority. It may be removed separately after any useful work has been preserved; this direction change does not require deleting unrelated worktree files.

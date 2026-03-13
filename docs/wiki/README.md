# World Awakened Wiki

Human-friendly guide set for operators, pack authors, testers, and server owners.

- Document status: Active companion guide index
- Last updated: 2026-03-13
- Scope: Plain-language concepts, recipes, workflows, troubleshooting, and FAQs

---

## What This Layer Is

This `docs/wiki/` layer exists to explain World Awakened the way a real user needs it explained:
- what the system is doing
- why it behaved that way
- which command to run next
- which authored pattern to use
- how to test and recover safely

Use this layer when you need the framework explained in plain language rather than as a strict reference contract.

## What This Layer Is Not

This wiki is a companion layer, not the source of truth for formal contracts.

If there is ever a conflict:
1. [SPECIFICATION.md](../SPECIFICATION.md)
2. the relevant technical reference doc in `docs/`
3. this wiki layer

The goal is not to restate every contract table. The goal is to make the contract understandable and usable.

## Start Here

1. [CONCEPTS.md](CONCEPTS.md)
   What the important ideas mean in plain language.
2. [QUICKSTARTS.md](QUICKSTARTS.md)
   Fast paths for first setup, first datapack, and first testing loop.
3. [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md)
   Day-to-day command usage, targeted recovery, and reset guidance.
4. [DATAPACK_COOKBOOK.md](DATAPACK_COOKBOOK.md)
   Copyable patterns for common authored behaviors.
5. [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
   What to check when the system does not behave as expected.
6. [FAQ.md](FAQ.md)
   Short answers to the questions people actually ask.

## When To Use Which Doc

- Use [CONCEPTS.md](CONCEPTS.md) when something feels conceptually wrong or surprising.
- Use [QUICKSTARTS.md](QUICKSTARTS.md) when you want a shortest-path setup or test loop.
- Use [OPERATOR_GUIDE.md](OPERATOR_GUIDE.md) when you are running a server or testing in-game.
- Use [DATAPACK_COOKBOOK.md](DATAPACK_COOKBOOK.md) when you want a pattern you can author from.
- Use [TROUBLESHOOTING.md](TROUBLESHOOTING.md) when a command or datapack object is not behaving.
- Use [FAQ.md](FAQ.md) for concise answers and sanity checks.

## Maintenance Rule

This wiki layer must be kept current alongside the technical docs.

Update the affected wiki docs in the same task whenever a change affects:
- user mental models
- operator workflows
- command usage
- testing flows
- datapack authoring patterns
- recovery/reset behavior
- common failure modes

Write this layer for humans first:
- plain language before engine jargon
- examples before abstractions
- workflows before internals
- practical cautions before edge-case theory

If a concept caused confusion during development or testing, document it here.

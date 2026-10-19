# Rarity and Generated Effects

Authoritative reference for World Awakened's data-defined rarity and generated-effect backend.

- Document status: Active shared reference
- Last updated: 2026-10-19
- Scope: configured effects, generated mob mutations, generated Ascension choices, persistence, and rarity-aware eligibility

Related contracts: [DATAPACK_AUTHORING.md](DATAPACK_AUTHORING.md), [COMPONENT_REFERENCE.md](COMPONENT_REFERENCE.md), and [COMPOSITION_AND_STACKING.md](COMPOSITION_AND_STACKING.md).

## The model

Rarity belongs to a completed resolved combination, not to an individual effect.

- An effect instance has independent `power`, `selection_weight`, and `parameters`.
- `power` is an author-defined strength estimate used only to classify the completed combination.
- `selection_weight` controls how often an eligible effect is picked.
- `parameters` control gameplay. Changing a weight never changes power or live gameplay values.

Generation pools support `curated`, `generated`, and `mixed` modes. Every resolved combination persists its actual source as either `curated` or `generated`. A `mixed` pool chooses between those sources from its explicit source weights, then follows that source's normal rules.

## Rarity definitions

Rarities are datapack objects under `data/<namespace>/worldawakened/rarities/`.

```json
{
  "id": "my_pack:rare",
  "display_name": { "text": "Rare", "color": "aqua" },
  "minimum_power": 20
}
```

The resolver selects the enabled rarity with the greatest `minimum_power` that does not exceed the resolved total power. Ties are rejected during reload so that classification remains deterministic. Bundled Common, Uncommon, Rare, Epic, and Legendary entries are defaults only; packs can replace or add tiers.

## Configured effects and pools

Configured effects are reusable, runtime-backed instances. A disabled, missing, unsupported, wrong-scope, or invalid effect cannot enter a generated pool.

```json
{
  "id": "my_pack:fast_hunter",
  "scope": "mob_mutation",
  "effect_type": "worldawakened:movement_speed_multiplier",
  "parameters": { "multiplier": 1.12 },
  "power": 6,
  "selection_weight": 18,
  "allow_repeat": false
}
```

```json
{
  "id": "my_pack:keen_hunter",
  "scope": "mob_mutation",
  "effect_type": "worldawakened:follow_range_bonus",
  "parameters": { "bonus": 8.0 },
  "power": 4,
  "selection_weight": 12,
  "allow_repeat": false
}
```

An effect generation pool chooses configured effects with weighted selection and a weighted combination-size distribution. `stage_filters` use the active progression context, including independent progression groups; historical unlocked phases do not keep a filtered pool eligible.

```json
{
  "id": "my_pack:late_mutation_effects",
  "scope": "mob_mutation",
  "mode": "mixed",
  "curated_source_weight": 3,
  "generated_source_weight": 1,
  "configured_effects": ["my_pack:fast_hunter", "my_pack:keen_hunter"],
  "combination_sizes": [{ "size": 1, "weight": 80 }, { "size": 2, "weight": 20 }],
  "stage_filters": { "any_of": ["my_pack:late_game"] }
}
```

For a working mixed mutation pool, reference the generation pool and also include the curated mutator candidates that may be selected when the curated source wins:

```json
{
  "id": "my_pack:late_hunters",
  "mutation_chance": 0.12,
  "max_mutators_per_entity": 2,
  "effect_generation_pool": "my_pack:late_mutation_effects",
  "mutators": ["my_pack:authored_bulwark", "my_pack:authored_hunter"]
}
```

Generation is server-authoritative and deterministic from its assigned stable instance ID. It respects existing component composition and per-spawn budgets. If no compatible combination can be made, that generated source produces no result; it does not silently substitute a different source.

## Persistence and live execution

Generated mobs persist their resolved component parameters, individual powers, total power, rarity, configured-effect provenance, and generated instance identity on the entity. Generated Ascension offers persist each offered reward's fully resolved reward definition and selected effects in player progression state.

Runtime handlers execute those persisted parameters. Reloading datapacks or changing a configured effect, pool weight, or rarity threshold cannot reroll or alter an existing mutated mob or pending/selected generated Ascension reward.

## Rarity-aware loot eligibility

Loot profiles may restrict themselves by resolved mutation rarity. This is eligibility-only: it never alters the profile's own item chance, weight, or quantities.

```json
"mutation_rarity_filters": {
  "minimum_rarity": "my_pack:rare",
  "none_of": ["my_pack:cursed"]
}
```

`minimum_rarity` compares the selected mutation rarity's configured threshold to the referenced rarity threshold. Exact filters may be combined with it using `rarity`, `rarities`, `any_of`, or `one_of`. `none_of` and `excluded` reject exact rarities. Missing or disabled references disable the affected loot profile at reload rather than weakening it into an unrestricted profile.

## Inspection and testing

Use the existing mutator debug/evaluate and inspect surfaces to see the selected source, configured effect IDs, resolved components, total power, rarity, and provenance. Generated results are safe to inspect after datapack reload because the stored snapshot is authoritative for that instance.

For authoring checks, test at least one curated, generated, and mixed pool; test each size weight and repeat rule; and verify a mutated entity after a reload. Test loot filters with a lower tier, the threshold tier, and a higher tier.

# W5 — Submod catalog contributions

Status: IN PROGRESS

Written: 2026-08-22

Updated: 2026-08-25 — promoted around an external OC-faction provider; additive discovery and primary/armor/template flow are implemented, while special runtime and the sibling tileset adoption remain.

Read `moddable-weapons-nouns.md` before implementing this story.

## Scope

Ship a public, deterministic contribution contract that lets an enabled mod
add weapon, special-equipment, armor, faction-roster, and collectible-template
catalogs without editing Starsector Marines files. Definitions keep provider
and path provenance, collisions fail with both claimants, and faction-generated
units consume contributed definitions rather than requiring new Java enum
constants.

## Contract

- Each enabled mod opts in with the fixed
  `data/marines/starsector-marines.catalog.json` manifest.
- The manifest explicitly lists resource paths by catalog family; filesystem
  glob order is never authority.
- Contributions are additive. An id or faction assignment may have one owner;
  a second claim stops application load and names both mod ids and paths.
- A contributed roster may omit `fallbackProfile`; the core catalog owns the
  single global fallback.
- Catalog files load from their declaring mod exactly, not from a merged virtual
  path that could obscure provenance.
- Details and a provider example live in `submod-catalog-contract.md`.

## Landed in this story branch

- enabled-mod manifest discovery and exact-mod resource loading;
- additive weapon, special-equipment, armor, roster, and template registries;
- provenance-aware duplicate diagnostics;
- data-authored built-in template-card eligibility and cargo costs;
- contributed primary definitions through defender stat resolution, burst
  behavior, ballistics, shot FX, lighting, and audio;
- contributed armor definitions through faction selection, appearance-family
  selection, durability, mobility, and hit-profile modifiers;
- an external-provider acceptance fixture proving an OC faction can add its
  weapon, armor, roster, and collectible cards without enum constants.

## Remaining

- Replace the remaining `MarineSecondary` battle handle so a contributed
  special-equipment id can execute in a generated faction roster. Contributed
  special definitions and their collectible cards already load and validate;
  assigning one to a roster remains fail-loud rather than silently dropping it.
- Move persisted player doctrine/billet equipment identity off the remaining
  enums so a learned contributed card can be selected and materialized, not
  merely discovered, validated, carried, and learned. This is the W5 consumer
  that advances the W4 persistence migration rather than duplicating it.
- Adopt the same manifest/provenance machinery for tilesets instead of growing
  a second discovery contract.
- Run an in-game two-mod smoke test against real Starsector enabled-mod order
  and capture the actionable collision message.

# W5 — Submod catalog contributions

Status: IN PROGRESS

Written: 2026-08-22

Updated: 2026-08-25 — adopted the shared manifest/provenance contract for tilesets and mappings; live two-mod acceptance remains.

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
- contributed learned primary and armor cards through the player doctrine
  editor, stable-id saves, cargo-backed squad issue, and campaign deployment;
- warning-backed save repair to the starter rifle and field fatigues when a
  persisted equipment provider is removed;
- contributed special cards through doctrine authoring, cargo-backed player
  issue, faction roster generation, ECS deployment, typed AI/activation,
  rendering, audio, persistence, and warning-backed empty-slot repair;
- an external-provider acceptance fixture proving an OC faction can add its
  weapon, armor, roster, and collectible cards, then learn, author, issue,
  save/load, and deploy them without enum constants;
- contributed tileset definitions and tile mappings through exact-provider
  loading, additive merge, cross-reference preflight, and provenance-aware
  collision diagnostics, with an OC sheet/pool acceptance fixture.

## Remaining

- Run an in-game two-mod smoke test against real Starsector enabled-mod order
  and capture the actionable collision message.

# Fort sandbag damage art

Status: DRAFT

Written: 2026-10-13

## Intent

Fort-belt sandbags (`doodad.fort-sandbag-*`) break through damaged and destroyed
states, but those states are a procedural placeholder derived from the intact
art by `art-source/doodads/derive_fort_sandbag_damage_frames.py`: notches,
scorch and loose bags drawn as shaded ovals. Replace them with real art.

## Scope

- Author damaged and destroyed fort sandbag pieces through the geometry-first
  workflow in `art-source/geometry-first-imagegen.md`: code owns the silhouette,
  breach voids and shared endpoints; ImageGen reskins the retained guide in the
  fort-earthworks material.
- Straight and corner identities; exact quarter turns supply the other facings,
  as for plain sandbags.
- Keep the ids, cover, ballistic half heights and `fightingPosition` roles the
  placeholder declares, so the swap is an art change with no code change.
- Retire the placeholder script and its sources once the real art is exported.

## Acceptance

- The `sandbags` suite shows fort pieces reading as intact, damaged and
  destroyed in the fort material at game scale.
- Catalog descriptions no longer say placeholder.

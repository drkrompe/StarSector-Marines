# Ruins ground

Status: PLANNED — owner direction; nothing built. Slice 4 of
`ruins-expedition-fork.md`.

Written: 2026-11-09

Read `mapgen-nouns.md` (Grown settlements, Authored damage layers, and the
ruined-lot rule), `settlement-off-map-link.md`, and `ruins-expedition.md`
(Shape of the battle) first.

## Goal

A ruins site gets its own ground: an abandoned settlement with no road out and
no supply link (`SettlementLink.NONE`), laid as a **delve** from a landing area
at one end to a vault at the other through the grade's **layers**.

## Slices

1. **The ruin as a settlement.** A ruins site grows its settlement from the
   ruins grade (scattered small, vast largest) and the planet's surface, with
   every lot a ruin: burned-out footprints, rubble ground, collapsed corners,
   from the existing ruined-lot vocabulary. `SettlementLink.NONE`: no arterial
   off the edge, no working pad.
2. **Landing area and vault.** A clear landing area at one map end, and a vault
   at the far end by route length: one hardened room or small suite with a
   single published source cell and a reachable approach. The vault and landing
   area are published for the mission, as other set pieces publish their
   places.
3. **Layers.** One to four bands across the route (the grade's count), each a
   published place an occupant can hold: a collapsed street with one or two ways
   through, a breached wall, a sealed gate with a side way round. A layer never
   closes the route; every layer has at least two ways through at the larger
   grades.
4. **Side rooms.** Small ruined rooms off each layer where the field-cache
   placement prefers to put its finds.
5. **Pools.** Run `storyMapPools` and `generatedSpaceEvidence` over every grade
   and surface: vault reachable, layer count as declared, no closed route.

## Constraints

- Ruins are a settlement value, not an exception: no law gains a carve-out.
- No new hazard system. The ground is terrain.
- Art reuses the ruined-lot and rubble materials; a new wall or floor set is a
  separate art decision with picks.

## Done

The battle story plays on this ground at every grade. Fold the rules into
`mapgen-nouns.md`, add a row to the mapgen `shipped.md`, and delete this story.

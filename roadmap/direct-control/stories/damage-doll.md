# Damage doll

> Read where a machine is hurt at a glance, the way MechWarrior does.

Status: IN PROGRESS
Written: 2026-10-20

Read `direct-control-nouns.md` and `ui-nouns.md` first. The locations the doll
draws are owned by `d5-located-armor.md`.

## Goal

Replace the boxed mech section schematic with a line-art **damage doll** for
every controllable machine — mechs and vehicles — drawn as a **side elevation
and a top plan** together (owner decision 2026-10-20). Each part of the outline
is a location, coloured by its state.

## Model

- A **doll** is one SVG per silhouette under `mod/graphics/ui/dolls/`, holding
  both views. Each location is a group `<g id="part-<LOCATION>">` whose fills use
  `currentColor`; outline and detail strokes carry fixed colours. One location
  may appear in both views (a tank's LEFT plate is the side view's whole flank
  and a strip of the top plan).
- `SvgAsset` remembers which `part-*` group each layer came from, and draws a
  `currentColor` layer in the colour the doll asks for that part. Everything else
  draws as authored.
- A location's colour reads its state: intact, armor worn, armor gone (structure
  showing), structure hit, destroyed. A destroyed location is drawn hatched dark
  red; an unfitted one is outline only.
- Silhouettes: three mechs (Hound, Bulwark, Sirocco) and the vehicle families
  (APC, tank, dozer, technical, truck, scout car, hover scout, drone skimmer, LRM
  carrier, hauler). Variants of a family share a doll.
- Outlines start from the shipped top-down sprites: the top plan is traced from
  the sprite's alpha; the side elevation is drawn to match it.

## Acceptance

- Mech and vehicle takeovers show the doll in the status corner; every location
  named in `d5-located-armor.md` changes colour when damaged there.
- Until located vehicle armor lands, a vehicle doll shows the turret's own state
  and the hull's armor and structure across the plates.
- Renders at 1920x1080 / 100% for each silhouette, intact and damaged.

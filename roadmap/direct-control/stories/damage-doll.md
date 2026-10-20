# Damage doll

> Read where a machine is hurt at a glance, the way MechWarrior does.

Status: IN PROGRESS — dolls, colouring and the HUD plate shipped; waits on located vehicle armor
Written: 2026-10-20
Updated: 2026-10-20 — every silhouette drawn and live in the takeover HUD; vehicle plates read the one hull pool until D5

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
- Outlines are drawn, not traced, from proportions measured off the shipped
  top-down sprites (`art-source/dolls/build_dolls.py` writes every doll); a
  traced alpha edge is jagged at HUD size. A hull stacks its elevation above its
  plan; a walker, taller than it is long, stands them side by side.
- The doll plate replaces the boxed section schematic beside the takeover status
  and above the selected-mech panel; its width is bounded by the takeover HUD's
  clear centre at the smallest supported hosts.

## Remaining

- When `d5-located-armor.md` lands, read each plate, the drive and the mech rear
  torso from their own pools (`DamageDollCanvas.View.readVehicle` and `read`),
  and light IMMOBILE from the drive.
- Live pass: drive a mech and a vehicle in game and compare the doll with the
  hits taken.

## Acceptance

- Mech and vehicle takeovers show the doll in the status corner; every location
  named in `d5-located-armor.md` changes colour when damaged there.
- Until located vehicle armor lands, a vehicle doll shows the turret's own state
  and the hull's armor and structure across the plates.
- Renders at 1920x1080 / 100% for each silhouette, intact and damaged.

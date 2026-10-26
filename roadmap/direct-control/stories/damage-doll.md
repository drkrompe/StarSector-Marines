# Damage doll

> Read where a machine is hurt at a glance, the way MechWarrior does.

Status: IN PROGRESS — dolls, colouring and the HUD plate shipped; located vehicle armor shipped; live pass remains
Written: 2026-10-20
Updated: 2026-10-23 — located vehicle armor is always on, so a vehicle doll reads each plate, the drive and the hull from its own pool

Read `direct-control-nouns.md` and `ui-nouns.md` first. The locations the doll
draws are owned by `convoy-nouns.md` (a hull holds its damage where it lands)
and `mechs-nouns.md` (sections and the torso's rear plate).

## Goal

Replace the boxed mech section schematic with a line-art **damage doll** for
every controllable machine — mechs and vehicles — drawn as a **front and a rear
elevation** side by side, captioned `R FRONT L` and `L REAR R` the way MechWarrior
Online draws its paper doll (owner, 2026-10-20; an earlier side-and-plan pair read
as goofy). Each part of the outline is a location, coloured by its state.

## Model

- A **doll** is one SVG per silhouette under `mod/graphics/ui/dolls/`, holding
  both views. Each location is a group `<g id="part-<LOCATION>">` whose fills use
  `currentColor`; outline and detail strokes carry fixed colours. One location
  may appear in both views: a hull's LEFT and RIGHT plates are the cheeks either
  side of the front and the rear, mirrored between them, so a side's state is
  implied in both rather than given a view of its own (owner, 2026-10-20). The
  drive (tracks, wheels, skirt) stands at the outer edges, the hull is a band
  across the top, and the end plate is FRONT in one view and REAR in the other.
- A mech faces the viewer in its front view: legs, torso, shoulders (the viewer's
  left is the mech's RIGHT), forearms and head. Its rear view is TORSO_REAR and
  the shoulders only; the legs are dropped as thin and the same from behind.
- `SvgAsset` remembers which `part-*` group each layer came from, and draws a
  `currentColor` layer in the colour the doll asks for that part. Everything else
  draws as authored.
- A location's colour reads its state: intact, armor worn, armor gone (structure
  showing), structure hit, destroyed. A destroyed location is drawn hatched dark
  red; an unfitted one is outline only.
- Silhouettes: three mechs (Hound, Bulwark, Warden) and the vehicle families
  (APC, tank, dozer, technical, truck, scout car, hover scout, drone skimmer, LRM
  carrier, hauler). Variants of a family share a doll.
- The style is MechWarrior Online's: flat angular panels that tile without
  overlapping, a constant gap between neighbours, a bright outline in the
  location's colour over a dim fill of it, and no ornament (owner, 2026-10-20:
  the first passes overlapped parts and read as childish). A faint fixed outline
  keeps an unfitted location's shape.
- Mech silhouettes follow the owner's references: the Hound a Locust, the
  Warden a Catapult, the Bulwark a Fafnir. Every outline is one damageable
  location and thinner lines inside it are detail; a leg is one outline. A
  pod-bodied mech's hull is three panels: the torso in the middle and its
  shoulder sections either side, each carrying its mount (the Hound's rocket
  pod, the Warden's missile boxes); the nose gun under it is the ARMS location.
- Outlines are drawn, not traced, from proportions measured off the shipped
  top-down sprites (`art-source/dolls/build_dolls.py` writes every doll); a
  traced alpha edge is jagged at HUD size.
- The doll plate replaces the boxed section schematic beside the takeover status
  and above the selected-mech panel; its width is bounded by the takeover HUD's
  clear centre at the smallest supported hosts.
- The briefing's armor lift section draws each bay machine with the same doll
  before launch: a mech from its frozen deployment, carrying the wear an
  unfinished repair leaves; a vehicle whole, since the bay keeps no damage on one
  (`air-nouns.md`, The briefing reads lift before launch).

## Remaining

- Live pass: drive a mech and a vehicle in game and compare the doll with the
  hits taken.

## Acceptance

- Mech and vehicle takeovers show the doll in the status corner; every location
  named in `convoy-nouns.md` and `mechs-nouns.md` changes colour when damaged there.
- Renders at 1920x1080 / 100% for each silhouette, intact and damaged.

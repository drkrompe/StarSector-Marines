# Kite (P): surfacing

Status: ACTIVE. Pirate refit livery on the clean build; source art only, nothing here ships.
Written: 2026-10-19

The Kite (P)'s inputs to the shared harness in `../second-wind-surface/` (see its README):
`surface_hull.py` (classes, swatch layout, wear response, review views), `groups/` (part tables),
`palette.json` (measured off the finish reference, plus the owned wear colours), `wear.json`
(the `pirate` row: heavy, neglected wear, no damage), prompts, Codex generations and guides.
`rebuild.sh` replays everything except the Codex calls.

- **Livery** read off `aeroshuttle_pirates.png`: a very dark, rusty warm brown-grey hull, a grimy
  bone cabin spine and turret hump, rust-brown welded scrap plates, gunmetal drives and launchers,
  bone heat-shield bands on the drive cans (the sprite's pale banding).
- **Four Codex generations**: the finish reference (registered at IoU 0.995), two part sheets
  (`body`: fuselage top, port neck flank, nose, spine wall; `deck`: the whole wing deck, which is
  one polygon tip to tip, and the cabin roof with the hump), and the five-class swatch sheet.
  Tiles registered at IoU 0.90-0.998 (the thin spine-wall strip is the low one); every other base
  face carries a graded class swatch.
- **Texel density** 6.5-39 per unit (the fuselage top 6.5, the wing 8.5). The shots see the Kite at
  350-550 m, where it covers about 4 pixels a unit.
- **The red claw slashes** are the sprite's own marking, laid on after grading by `livery_decal.py`:
  the sprite's redness becomes a packed 82x66 mask, projected straight down in the fuselage's frame
  (so it stays put when a shot moves the ship) onto upward-facing hull faces, with a ragged sprayed
  edge, worn-through flecks and the grime beneath showing through.

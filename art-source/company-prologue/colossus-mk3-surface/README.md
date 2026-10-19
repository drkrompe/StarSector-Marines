# Colossus Mk.III: surfacing

Status: SHIPPED (owner accepted 2026-10-19). Pirate refit livery with battle wear on the clean build; source art only, nothing here ships.
Written: 2026-10-19

The Colossus Mk.III's inputs to the shared harness in `../second-wind-surface/` (see its README):
`surface_hull.py` (classes, swatch layout, wear response, review views), `groups/` (part tables,
forked from `../colossus-surface/` and fitted to the cut-down modules, the launch bay and the refit
skirts), `palette.json` (measured off the finish reference, plus owned wear colours), `wear.json`
(the `battle` row: hard service plus fighting, no damage), prompts, Codex generations and guides.
`rebuild.sh` replays everything except the Codex calls.

- **Livery** read off `colossus3_pirate.png`: dirty crimson enamel on the hold, the aft body and the
  bridge's base deck; blackened, rusted brown on the side modules and the drive pods, with crimson
  blocks on the pods' aft plates; the Colossus's pale grey longerons and bridge; dull gunmetal
  sponsons, gun tubs and bolted armour skirts. There are no faded-blue panel bands on this hull (the
  refit stripped them), so no blue class; the scavenged primer-grey and faded-blue plates keep
  their own materials.
- **Twelve Codex generations**: the finish reference (registered at IoU 0.994), ten part sheets
  (35 parts; starboard faces are mirrored reuses) and the five-class swatch sheet. Every tile
  registered at IoU 0.973 or better. All ten sheets were generated fresh, including the six whose
  geometry is the Colossus's own: its tiles are service-clean, and regrading them would not carry
  the battle finish.
- **Swap check**: the pods' crimson aft plate (`red_w`) flags on two tiles because Codex painted it
  brighter and more saturated than the measured crimson; the region is the right paint, and grading
  brings it to the palette.
- **The sprayed marking** (`livery_decal.py`, forked from the Kite (P)'s): the sprite's winged heart
  on the aft hatch cover and the scarlet splashes on the drive pods, laid on after grading. The hull
  is itself crimson, so a texel is marked only where the sprite is red and bright, inside two windows
  (hatch cover, pods); the rest of the sprite's red is already the paint.

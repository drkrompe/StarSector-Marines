# Air — open stories

Status: ACTIVE

Written: 2026-08-23

Updated: 2026-08-30 — fighter fold state.

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — the overlay is deleted and wings fly as air entities; the `FighterProfile` clean-up and the package rename remain | Fighters are ordinary air entities flying ordinary sorties. Committed wings arrive on an `AirCorridor` from off the map and are dispatched by `AirCoverSystem`. |
| `runway-airbase.md` | ACTIVE — the strip is published; the lot variant and the ground procedure remain | An airbase variant that bases armed aircraft in hangars and flies them off its own runway: taxi out, roll, rotate, and the same in reverse. Built on based aircraft first, with the flyby fighters folded onto the same seam after `fighter-air-entities.md`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.

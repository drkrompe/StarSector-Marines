# Air — open stories

Status: ACTIVE

Written: 2026-08-23

| Story | State | Outcome |
| --- | --- | --- |
| `fighter-air-entities.md` | ACTIVE — prior motion slices shipped | Move fighters from the legacy flyby owner into the common air-entity model, retaining their hull-derived body motion, existing combat presentation, and cycling-pass behavior while retiring the duplicate ownership. |
| `runway-airbase.md` | ACTIVE — the strip is published; the lot variant and the ground procedure remain | An airbase variant that bases armed aircraft in hangars and flies them off its own runway: taxi out, roll, rotate, and the same in reverse. Built on based aircraft first, with the flyby fighters folded onto the same seam after `fighter-air-entities.md`. |
| `air-manual-acceptance.md` | PLANNED | Verify centre-of-gravity rotation, hardpoint placement and per-mount sight, and thrust-weighted engine effects together in a live battle. |

Future ship collision, wing composition, anti-air/survivability, modeled fighter
fire, and camera-Z work are extension paths in `air-nouns.md`, not contracted
stories yet.

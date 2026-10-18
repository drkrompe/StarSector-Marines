# Hermes: surfacing

Status: ACTIVE — round 1 (base skin on every face, light service wear) is with the owner for review.
Written: 2026-10-18

The Hermes's folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here; textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex.

## Livery

The vanilla Midline courier, read off `hermes_base.png`
(`reference/sprite-livery.png`, the sprite at 8x): an ivory-cream spine and
fairing, light cool grey sponsons, drive block and deck plates, gunmetal
turrets, vents and fittings, near-black recesses, tinted glazing.
`surface_hull.py` maps the base materials to these five classes. The
grey-blue vent blocks, the interiors (troop bay, cockpit), lamps, chrome,
rubber, the radome, drive bell liners and hazard paint keep their own
materials. Wear runs the `service` row of `wear.json`: a working civilian
courier, light abrasion, pitting, fade and seam grime, soot at the three
drives and the stern.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's port-quarter camera with the sprite as colour key,
  registered at IoU 0.996. `palette.py` reads the classes off it; the
  gunmetal and recess spreads are owned (edge contamination inflated them)
  and glass is owned. The background is mid blue, as on the Mudskipper,
  because the cream and pale grey sit inside the segmenter's threshold of
  the default grey.
- **Swatches**: one Codex sheet of four squares (cream, grey, gunmetal,
  dark; glass regraded from dark), tiled at 40 hull units (4.4 m).

## Parts

Three groups, one Codex sheet each, no retries: 12 generated sources, 7 free
reuses (starboard mirrors).

| Group | Sources | Reuses | Fitted IoU |
|---|---|---|---|
| `spine` | port flank, dorsal deck (ridge, spine and well plates, cheek fairings), nose from ahead, belly with service covers | starboard flank | 0.945-0.998 |
| `sponsons` | port sponson wall, deck and raked bow face; the whole wing-body underside (one connected face group) | starboard wall, deck, bow face | 0.986-0.997 |
| `aft` | drive block port side, deck with the turret-well plates, stern face, port horseshoe arm | starboard side, starboard arm | 0.953-0.998 |

Spine deck and belly are single polygons about 50 x 65 units: 6 texels per
unit, which at 0.11 m/u is about 55 per metre (the Second Wind's 15 t/u is
23 per metre).

The three `aft` grey tiles report as swaps (12-26% grey) because Codex
painted them lighter and a little warmer than the sponsons (raw L 76, b +2):
by lightness they sit on the cream mean. They are grey to the eye and graded
back to grey (`forced_back_to_declared`); no paint was swapped.

## Coverage

Share of the ship's on-screen pixels at 1920x1080 (`compose_review.py`): 96-99%
textured on every whole view (skin 54-68%), 92-100% on the close-ups. The
remainder is the materials kept on purpose (see Livery).

## Condition states (pass 5)

Deferred until a cinematic needs them (owner). Names reserved:
`Scar H<nn> ...` objects, `--state wrecked|patched` in `build_hermes.py`.

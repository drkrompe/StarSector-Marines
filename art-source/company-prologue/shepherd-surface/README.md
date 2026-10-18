# Shepherd: surfacing

Status: ACTIVE — round 1 (base skin on every face, light service wear) is with the owner for review.
Written: 2026-10-18

The Shepherd's (Morrow's) folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here; textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex.

## Livery

Ko Combine drone tender, read off `drone_tender.png`
(`reference/sprite-livery.png`, the sprite at 8x): sand-tan hull plating on
the body, spine, crossbeam and bridge hull; chalky cream crown plates and
fittings; a light grey machinery drum and sensor head; burnt orange drive pod
casings and outrigger chords; gunmetal frames, turret, walkways and the bay's
walls; near-black recesses, bay deck and workshop block.
`surface_hull.py` maps the base materials to these six painted classes. Lamps,
glow strips, hazard paint, drive bell liners and the glazing keep their own
materials: a swatch is a painted plate, and on the windows it read as steel
(owner), so the glass is a dark tinted shader with a soft sheen and two cool
highlight streaks (`sh_bridge.build`).
Wear runs the `service` row of `wear.json`; the owned wear colours in
`palette.json` use a sand chalk (faded sand goes paler and greyer) and a
work-yard dust.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera with the sprite as colour key,
  registered at IoU 0.988. `palette.py` reads the classes off it; the spreads
  are owned in `derive_palette`, and glass is owned.
- **Swatches**: one Codex sheet of six squares (sand, cream, grey, orange,
  gunmetal, dark), tiled at 7 hull units (5.5 m).

## Parts

Six groups, one Codex sheet each, no retries: 24 generated sources, 3 free
reuses (starboard mirrors). Every tile fitted at IoU 0.991-1.000.

| Group | Sources | Reuses |
|---|---|---|
| `hull` (two sheets) | body deck, starboard flank, bow nose, spine top; crossbeam deck, forward face, port end, spine flank | crossbeam starboard end |
| `bridge` | crown with the cream plating, port and starboard flanks, nose and chin | |
| `gantry` | drum crown, outboard flank, nose; turret deck with its plating | |
| `bay` | back wall, aft end on the crossbeam, hangar pod exterior, workshop outboard face | |
| `engines` | port pod casing top and outboard flank (two islands each, either side of the saddle), machinery block top and stern | starboard pod top and flank |

No class swaps were reported at grading.

## Condition states (pass 5)

Deferred by the owner: texture first.

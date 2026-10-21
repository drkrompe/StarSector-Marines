# Borer: cinematic asset

The Shepherd's built-in mining drone (`borer_wing`, four Borers; `drone_borer.ship`), exported
as one appendable Blender collection. Geometry: `../fighters-geometry/ft_borer.py`
(0.25 m/unit, 3.6 x 4.0 x 2.3 m). Surfacing: `../fighters-surface/borer/` in the Shepherd's Ko
Combine livery family.

## Using it

Append `BORER` from `borer.blend` for the bare drone. Append `BORER` and `SWFX` in **one** load
for the effects, which ride the drone's own anchors. Move, rotate or scale only the root,
`BORER | root`.

- **Size.** 3.6 m long, sized to fit in the Shepherd (owner, 2026-10-21): the bay's cradles and
  mouths hold it. The fleet-combat sprites would make it 11.7 m, which no bay holds.
- **Scale.** In a metre scene, scale the root by 0.25. Beside the Shepherd (0.78 m/u), scale it
  by 0.25 / 0.78 = 0.3205.
- **On a cradle.** Put `Anchor cradle seat`, which sits on the skid shoes' plane under the .ship centre,
  on a Shepherd `Drone bay WS 003 launch point <i>`, heading 0 degrees. The drone then stands on
  the cradle deck on its four skid shoes, with the docking keel's socket over the charging contact.
  - `../fighters-geometry/borer_cradle.py` checks this on all three stations with no render:
    no overlap, skid shoes on the deck, a 2 mm socket gap, and a clear lift to the bay mouth.
  - Its result is `cradle-check.json`.
- **Drives.** The anchors are `Engine 1|2|3 exhaust anchor`, with +X down each slot's exhaust
  heading. The two side drives splay 20 degrees outboard (slot angles 160 and 200).
  - In the effects, `thr_throttle` and `thr_throttle_e<i>` on `SWFX controls` drive them.
- **Tool.** The variant's mining laser (WS 001, HIDDEN) fires from `Anchor weapon WS 001 muzzle`
  on the cutting head's lens.
  - The beam is the kit's `beams` group. It stays dark until `beam_t_ws001` is raised, so it is
    opt-in in every shot.
  - `beam_charge_ws001` is the glow at the emitter. `beam_len_ws001` is the distance to the work
    in hull units.
  - Colours, charge-down and range are vanilla. The width is sized to the 0.24 m emitter rather
    than vanilla's 17 px, which would be a 4 m beam at this scale.
  - The laser hangs under the nose on a sling (yoke, hanger arms to trunnions, rear link).
  - `Anchor tool cutting head` and `Anchor tool grab port|starboard` mark where a shot puts the
    work: the barrel's root, and between each pair of grab tines.
- **Lift jets.** `RCS <side> <fore|aft> lift jet anchor` on the four ventral lift thrusters, +X
  straight down; the kit gives them cold-gas puffs (`thr_rcs_*`, `thr_rcs_gain` on `SWFX controls`).
- **Lamps.** Two warm work lamps on the pod noses and an amber beacon on the spine that blinks.
  `lit_*` on `SWFX controls`.
- **Nothing moves.** There are no gear, doors or folds (skids are fixed), so there is no control empty.

`manifest.json` lists every anchor with its position, the Shepherd's launch points, the lamps,
counts and source hashes. `validation.json` is `verify_ship.py`'s result on an append.

## Rebuilding

```bash
./finalize.sh                 # geometry, skin, export, effects, verify, cradle check (no render)
./finalize.sh --skip-surface  # from the existing skinned build
./finalize.sh --publish       # also copy the asset to Drive builds/borer/<commit>/
```

`finalize.sh` needs the accepted Shepherd build at `build/ships/shepherd/` (repo root), copied
from Drive `builds/shepherd/<commit>/`.

The look render is `review_look.py`: four 960x540 panels at 16 samples. It runs as one
render-queue light job (`review-*.job.json`), never from a session's shell. `compose_review.py`
tiles the panels into a 1920x1080 sheet.

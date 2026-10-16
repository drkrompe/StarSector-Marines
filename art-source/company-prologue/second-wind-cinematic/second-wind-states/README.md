# Second Wind: per-state cinematic assets

Status: ACTIVE. Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-16

The Second Wind in each campaign condition, as one appendable Blender
collection per state (`second-wind-pipeline.md`, Cinematic output). These
replace `../second-wind-full-surface/`, which was built on the superseded
landing-wreck geometry at a provisional 1.5 m per unit.

![patched](patched/review/views.png)

## Use

Append the collection, not objects:

| State | File | Collection | Root |
|---|---|---|---|
| derelict | `derelict/second-wind-derelict.blend` | `SECOND WIND \| derelict` | `SECOND WIND \| derelict \| root` |
| patched | `patched/second-wind-patched.blend` | `SECOND WIND \| patched` | `SECOND WIND \| patched \| root` |
| refit | `refit/second-wind-refit.blend` | `SECOND WIND \| refit` | `SECOND WIND \| refit \| root` |
| restored | `restored/second-wind-restored.blend` | `SECOND WIND \| restored` | `SECOND WIND \| restored \| root` |

```python
with bpy.data.libraries.load(path, link=False) as (src, dst):
    dst.collections = ['SECOND WIND | patched']
scene.collection.children.link(dst.collections[0])
root = bpy.data.objects['SECOND WIND | patched | root']
```

- **Move the root only.** Every ship part and anchor descends from it. The
  model is in hull units (+X bow, +Y port, +Z dorsal); `metres_per_unit` is
  0.65 (in the manifest and as a custom property on the root), so the ship is
  65.6 m long. A shot that works in metres scales the root by 0.65; one that
  works in hull units leaves it at 1. Nothing inside the asset is in metres.
  The old full-surface asset was 1.5 m per unit, so a shot switching over
  re-solves its framing from this length.
- **Engines.** The geometry's `Engine 1..4 exhaust anchor` empties sit at
  each bell mouth, local +X down the exhaust (hull -X); 1 and 2 are the port
  pod, 3 and 4 starboard. Each carries `running` for this state, and the
  root's `engines_running` lists them: derelict none; patched and refit 3 and
  4 (the jury-rigged coil; the port pod is capped); restored all four. The
  drive glow is the geometry's own emissive throat, on only where a drive runs.
- **RCS.** `RCS <cluster> <axis> anchor` empties (24, in 8 clusters), +X down
  each thruster's exhaust, with `working` for this state (`manifest.json`
  `rcs`, `rcs_working`).
- **Turrets.** `Turret <i> pivot` (slots WS 001-004) turns about its local Z
  to train; the rest pose is the slot angle (`rest_angle_deg`). Gun house,
  mantlet and barrels ride the pivot; the barbette foot, collar and traverse
  gap stay. `Anchor turret <i> <gun> muzzle` rides with it, +X along the line
  of fire (turret 4 has a port and a starboard gun). The derelict's guns are
  stripped: its pivots exist and carry nothing.
- **Lights** are the ship's own emissive lamps and viewports; nothing adds a
  light object. The derelict has none lit (`lamps_lit` 0, checked dark below).
- **Wear.** Skins and swatches carry the base wear, group `SW wear <state>`
  (`../../second-wind-surface/wear.py`, eight years in the belt). On top of it
  the state overlay, group `SW overlay <state>` (`wear_overlay.py`,
  `overlay/<state>.json`); its `SW overlay master` value scales it (0 leaves
  the base wear alone).
- `manifest.json` per state: names, bounds in units and metres, engine, RCS
  and turret anchors, lamps, every scar's status, its recorded sites and its
  footprint, wear summary and source hashes. `validation.json`: the checks
  below.

The meshes are baked: scar booleans, bevels and dent shape keys are real
geometry under the same object names, so moving the root costs nothing to
evaluate. Every mesh carries `hull_rest` (rest position, hull units, point)
and `hull_normal` (face normal, hull space); the base wear also reads its own
`hull_nrm`. The masks ride the ship when it moves or a turret trains.

## What each state is

| State | Geometry (scar table) | Overlay |
|---|---|---|
| derelict | open craters, torn plates, stripped guns, sheared mast, smashed can; every lamp dead | old dark scorch at every open strike; torn metal and backing aged hardest; no running soot |
| patched | salvage plates, welds, splints, cable runs, starboard jury coil, port pod capped | dark burns at the still-open strikes, heat-tint rings round patched welds; fresh soot on drives 3, 4; salvage plates and welds newly fitted (little grime) |
| refit | a few proper replacement plates with fresh seams; the rest as patched | as patched, but replaced plates are clean shop primer: no scorch, no wear, smooth primer detail |
| restored | structurally whole; scars faded under paint; both pods running | one coat over cream, oxide, pale and the gunmetal collars (heat enamel, 0.7 tone); old seams, dents and the belt wear read through it as tone; plates mismatched by about 7%; light working soot on all four drives |

## Build

`./finalize.sh` replays everything for all four states: `build_state.sh`
(geometry `--state S`, `apply_skin.py --wear S`, `export_state.py`),
`verify_state.py`, `review_state.py` (stills with projected scar sites, a
lights-off still and a 96-frame turn), `encode_turn.py`, then
`review_sheet.py`. One state is about 2 min to build and about 20 min to
review on an RTX 3080. No Codex calls: the base skin is the one generated
set, and repair surfacing, wear and overlay are ours.

`review_state.py --wear-off` renders without the overlay, `--lights-off` with
the studio off (ship emissives only), `--device CPU` when the GPU is busy.

## Checks

`verify_state.py` appends each collection into a factory-empty scene and
checks: every object under the root; nothing still linked; every image
packed; object and polygon counts match the manifest; the hull attributes
exist and `hull_rest` is the rest position; base wear on every skin and
swatch and the overlay on every family material; four engine anchors
pointing aft with the manifest's running flags; turning a turret pivot 30
degrees swings its muzzle anchors 30 degrees and leaves the barbette; moving
the root moves the ship rigidly.

## Known limits

- **No effects collection yet.** The VFX kit (`second-wind-vfx/`) is not on
  main; when it lands, a per-state effects collection appended from it (not
  forked) joins each export. Its anchors already match: +X along the effect
  axis, parented to the parts they ride.
- **Size.** Each `.blend` is about 22 MB, almost all of it the packed skin
  textures, repeated per state so each file stands alone.
- **Soot is subtle** at whole-ship distance; it reads at the stern close-ups.
- **A faded pod gouge still reads as an open crack** on the restored port pod's
  upper casing (geometry, `sw_damage`; the overlay paints over it but its
  groove stays dark). Left for the damage-states package.
- The review studio is neutral; lighting is each shot's.

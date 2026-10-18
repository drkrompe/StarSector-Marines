# Second Wind: per-state cinematic assets

Status: ACTIVE. Source art for cinematics; nothing here ships in the mod.
Written: 2026-10-16

The Second Wind in each campaign condition, as one appendable Blender
collection per state (`second-wind-pipeline.md`, Cinematic output). These
replace the earlier full-surface asset and the hand-built skinned patched-state
blend; the C1, C5 and entrance-card renders append the patched state. The old
asset was built on the superseded landing-wreck geometry at a provisional 1.5 m
per unit.

The `.blend` files, review stills and sheets are build output and
are not tracked (`art-source/company-prologue/.gitignore`): `./finalize.sh`
writes them here; the latest build's review set is copied to Google Drive
(`starsector/review/second-wind/`, one dated folder per review). Tracked: the scripts, each state's
`manifest.json` and `validation.json`, and `review/scar-continuity.{json,md}`.

![four states](review/states-port-quarter.png)
![starboard](review/states-starboard-quarter.png)

Per state: `<state>/review/views.png` (four views), `close.png` (bow, port
flank, stern, and a studio-off still showing only the ship's own lights).
Review is stills only; `./finalize.sh --turn 96` adds an optional turntable
video (`turn.mp4`, the root yawed 360 degrees under a fixed camera), which
nothing reads.

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
  Engine 1's can is crushed in derelict, patched and refit (mouth flattened
  about 40%, bell cocked 5 degrees): its anchor and manifest entry carry
  `mouth_deformed`, `mouth_minor_m`, `mouth_major_m`, `mouth_round_m`,
  `mouth_minor_dir`, `mouth_axis_dir`, `mouth_tilt_deg` and
  `mouth_centre_offset_m`, so a plume can be shaped to the oval mouth.
- **RCS.** `RCS <cluster> <axis> anchor` empties (24, in 8 clusters), +X down
  each thruster's exhaust, with `working` for this state (`manifest.json`
  `rcs`, `rcs_working`).
- **Turrets.** `Turret <i> pivot` (slots WS 001-004) turns about its local Z
  to train; the rest pose is the slot angle (`rest_angle_deg`). Gun house,
  mantlet and barrels ride the pivot; the barbette foot, collar and traverse
  gap stay. `Anchor turret <i> <gun> muzzle` rides with it, +X along the line
  of fire (turret 4 has a port and a starboard gun). A **seized** mount
  (`seized` on the pivot and in the manifest) carries nothing and has no
  muzzle anchor: turret 1 is jammed 24 degrees off its slot (`jam_deg`;
  `rest_angle_deg` 114, the slew already in the mesh) with its barrel bent,
  and turret 3 is holed with its barrel snapped, in derelict, patched and
  refit. Their wreck parts (`Scar sp-turret-1/3 ...`) stay on the root. The
  derelict's turrets 2 and 4 are stripped: their pivots carry nothing either.
  Restored has all four guns back and trainable.
- **Sparks.** `Scar <id> spark anchor` / `spark-2 anchor` empties at torn
  conduits, +X along the spray, with `sparking` and `radius_m`
  (`manifest.json` `sparks`, `sparking`). Live only in patched, where the crew
  has powered the buses: the port waist rip (two), the starboard waist rip and
  turret 3's feed. The VFX kit builds one intermittent emitter per live anchor
  (its `smk_sparks` control).
- **Lights** are the ship's own emissive lamps and viewports; nothing adds a
  light object. The derelict has none lit (`lamps_lit` 0, checked dark below).
- **Wear.** Skins and swatches carry the base wear, group `SW wear <state>`
  (`../../second-wind-surface/wear.py`, eight years in the belt). On top of it
  the state overlay, group `SW overlay <state>` (`wear_overlay.py`,
  `overlay/<state>.json`); its `SW overlay master` value scales it (0 leaves
  the base wear alone).
- **Effects (optional).** Each file also holds the VFX kit's `SWFX`
  collection for that state (`../../second-wind-vfx/`, applied by
  `export_effects.py` with the kit's own `swfx.apply`, no lighting-rig
  preset): drive plumes, cores and bell glow on the engines that run, RCS on
  the thrusters that work, haze and seal wisps, gun effects and the ship's
  lamps, all animatable from the `SWFX controls` empty. Append it **in the
  same load** as the ship (`dst.collections = ['SECOND WIND | patched',
  'SWFX']`); its anchors ride the ship's parts, so the root moves them too.
  The ship alone appends without it; the kit drives the lamp lenses from
  `SWFX controls`, so that empty (and three other unlinked kit objects) comes
  along as a dependency without being linked into the scene. A shot that
  calls `swfx.apply` itself replaces the collection.
  ![effects](review/states-effects.png)
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
| derelict | open craters, five dorsal deck rips, torn plates, turrets 1 and 3 wrecked (1 jammed, 3 holed), 2 and 4 stripped, sheared mast, smashed can, Engine 1 crushed, port engine bay smashed (aft drum band and fins flattened, heat sink cut, housing corner torn); every lamp dead | old dark scorch at every open strike; torn metal and backing aged hardest; no running soot |
| patched | salvage plates, welds, splints, cable runs, forward deck rip pressure-plated, starboard jury coil, port pod capped, turrets 1 and 3 still wrecked; four live spark anchors | the derelict's paint wear unchanged (nobody repaints before the restoration); dark burns at the still-open strikes, heat-tint rings round patched welds; fresh soot on drives 3, 4; salvage plates and welds newly fitted (little grime) |
| refit | proper replacement plates with fresh seams: both waist rips, the forward deck rip, the port bow cheek, the starboard bow deck cluster and three torn or loose deck plates; the rest as patched, sparks dead | as patched (grime 0.9), but replaced plates are clean shop primer: no scorch, no wear, smooth primer detail |
| restored | structurally whole; scars faded under paint; Engine 1's can renewed; all four guns back; both pods running | one coat over cream, oxide, pale and the gunmetal collars (heat enamel, 0.7 tone); old seams, dents and the belt wear read through it as tone; plates mismatched by about 7%; light working soot on all four drives |

## Build

`./finalize.sh` replays everything for all four states: `build_state.sh`
(geometry `--state S`, `apply_skin.py --wear S`, `export_state.py`),
`verify_state.py`, `review_state.py` (stills with projected scar sites and a
lights-off still), then `review_sheet.py`. `export_effects.py` adds the kit's `SWFX` collection after
each build. One state is about 2 min to build and 1-2 min to
render its stills on an RTX 3080 (a turn adds 15 min or more, longer while
other renders share the GPU). No Codex calls: the base skin is the one generated
set, and repair surfacing, wear and overlay are ours.

`review_state.py --effects` appends the `SWFX` collection too,
`--wear-off` renders without the overlay, `--lights-off` with
the studio off (ship emissives only), `--device CPU` when the GPU is busy.

## Checks

`verify_state.py` appends each collection into a factory-empty scene and
checks: every object under the root; nothing still linked; every image
packed; object and polygon counts match the manifest; the hull attributes
exist and `hull_rest` is the rest position; base wear on every skin and
swatch and the overlay on every family material; four engine anchors
pointing aft with the manifest's running flags; turning a turret pivot 30
degrees swings its muzzle anchors 30 degrees and leaves the barbette; seized
mounts carry nothing and have no muzzle; spark anchors and crushed-mouth tags
match the manifest; moving the root moves the ship rigidly.

### Scar continuity

`review_sheet.py` checks that every scar id reads in the same place in every
state (`review/scar-continuity.md`, `.json`, and
`review/scar-continuity-<view>.png`, where each recorded site the camera sees
is ringed and each id's footprint numbered by scar-table row, coloured by its
status in that state):

- **Recorded sites: identical.** The 41 ids with cast sites (craters,
  clusters, spalls, gouges) put every site at the same hull position in all
  four states: 0.0 units, 0 px on screen, the same count everywhere. Sites
  are cast once on the clean ship, so this holds by construction, and the
  check proves the states did not drift. `pod-port-outer-can` and
  `pod-port-engine-bay` have their sites in the three damaged states and none
  in restored, where the pod is renewed.
- **Footprints: same place, different extent.** 58 ids touch something in at
  least one state (their own parts, or base parts they hide, dent or
  override). The footprint centre moves at most about 5 units between states,
  and 10 for the starboard drive, where the bolted-on jury coil enlarges what
  the row touches. Turret rows move 3-5 units between stripped, wrecked and
  reinstalled; the dorsal rips about 1 unit.
- Four RCS rows (`rcs-bow-port`, `rcs-pod-stbd`, `rcs-stern-stbd`,
  `rcs-ventral`) change only material and anchor flags, so they have no
  location to compare.

## Known limits

- **Size.** Each `.blend` is about 22 MB, almost all of it the packed skin
  textures, repeated per state so each file stands alone.
- **Soot is subtle** at whole-ship distance; it reads at the stern close-ups.
- The review studio is neutral; lighting is each shot's.

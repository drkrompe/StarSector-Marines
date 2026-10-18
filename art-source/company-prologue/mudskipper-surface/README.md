# Mudskipper: surfacing

Status: ACTIVE. Pass 6 (texturing): part skins and class swatches generated and graded; not yet applied or reviewed.
Written: 2026-10-18

The Mudskipper's package for the shared surfacing harness
(`../second-wind-surface/`, see its README "Other hulls" and `hull.py`). This
folder holds only the hull's own inputs and outputs; every script is the
harness's, run with `--hull`.

## Livery

Read off the vanilla sprite (`reference/sprite-livery.png`): off-white hull
plate, grey-green sponsons, deep red chevron stripes angling in toward the
bridge, tan modules and fenders, dark grey-brown drive steel with pinkish
burnished bands. Seven palette classes (`surface_hull.py`): white, red, tan,
grey, steel, dark, burnished, all measured off the finish reference.

The finish reference (`reference/finish-reference.png`) is one Codex repaint
of the clean build's port-quarter view (`guide/reference-guide.png`, unlit
white-world guide) with the sprite as livery reference: a working civilian
transport kept in service for years, light even wear. Its silhouette registers
at identity (bounds within 1 px).

Tiles sit on a **mid-blue background** (70, 110, 170), not the Second Wind's
214 grey: the off-white plate is within `imgops.segment`'s threshold of that
grey, which cut plates out as background.

## Parts

| Group | Sources / reuses | What |
|---|---|---|
| `port` | 4 / 4 | waist wall (fore flank is the same polygon), aft flank with locker, sponson side, cargo bay doors; starboard as mirrors |
| `tops` | 4 / 0 | waist deck with stripes and cargo hatch, fore deck with stripes, aft deck round the turret coaming, cabin hood |
| `bow` | 4 / 1 | bow face (sponsons, fenders, armour modules), bow rake with windscreen sill, cockpit and bridge crown, sponson bow-quarter faces |
| `drives` | 4 / 5 | pod outer side, pod top, pod aft face (starboard pod mirrors), main drive casing top (its flanks reuse it turned) |

Every source registers at fitted IoU 0.974 to 1.000 and holds its paint family
on every declared region except `bow-face`'s thin recess seams (0.596, forced
back to dark by grading). Native density 13.1 to 35.4 texels per unit; the
three decks and the bow face are single large polygons, which caps them.

**Codex generations: 8.** Finish reference 1, swatch sheet 1, `port` 1,
`bow` 1, `tops` 2 (the retry kept the hatch frames tan), `drives` 2 (the retry
was worse; the first generation is the master, the retry is kept as
`generated/sheet-drives-a-try2.png`).

Known: `pod-outer-port` and `pod-top-port` carry thin white edge streaks where
the generation moved the casing's forward edge about 10 px (boundary p95 88 and
69 px); to judge on the model. Small fittings, glazing, lamps, the junction
structure, turret, bells and throats are swatch by design.

## Rebuild

From this folder (Blender 5.2; Python with numpy and Pillow):

    blender -b --factory-startup --python ../mudskipper-geometry/build_reference.py -- --out "$PWD/build/geom" --views port-side --samples 4
    blender -b --factory-startup --python ../second-wind-surface/render_parts.py -- --hull .
    python ../second-wind-surface/sheets.py compose --hull .
    blender -b --factory-startup --python ../second-wind-surface/render_reference_classes.py -- --hull .
    python ../second-wind-surface/palette.py --hull .
    python ../second-wind-surface/register.py --hull .
    python ../second-wind-surface/grade.py --hull .
    python ../second-wind-surface/swatches.py --hull .

`textures/` and `review/` are outputs (ignored). Re-running the above on the
shared harness reproduced every guide, part list, palette class and all 23
textures exactly.

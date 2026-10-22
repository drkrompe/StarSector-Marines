# Asharu surface detail

The planet under the Asharu battle is the game's own `desert01.jpg` (1024 x 512, about 31 km
a pixel at the equator) wrapped on a 5,000 km sphere. From the convoy's 250 km orbit that is
soft everywhere a camera looks: S1a's straight-down frame is 94 km across, three map pixels.
This package lays ImageGen detail tiles over the map where the cameras look, and paints a few
static settlements into them. `battle_blockout.py` composites them in the planet material
(`surface_layers`); `SURFACE_DETAIL` is the knob and `--surface base` renders the control.

## Tiles

Each tile is a square on the tangent plane at the battle's sub-point (planet-object X east,
Y north, metres), so a direction from the planet's centre lands on it orthographically. The
same projection samples the game map, so a tile and the map agree point for point.

| Tile | Centre (km) | Side | m/px | For |
|---|---|---|---|---|
| regional | (300, 0) | 3,400 km | 3,300 | everything out to the 1,580 km horizon |
| mid | (0, 0) | 1,300 km | 1,270 | the near ring of every wide shot |
| aft | (-240, 350) | 500 km | 490 | S4, looking back past the platform (north-west) |
| flank | (230, -360) | 440 km | 430 | S1, looking forward past the convoy (south-east) |
| track | (520, 20) | 420 km | 410 | S12, down the track toward the northern canyons |
| near | (0, 0) | 160 km | 156 | S1a, straight down from 11 km |

**Each generation refines the layer below it.** A tile's guide is the planet as it stands
without it: the game map under the regional tile, then the base map with every finished tile
over it (`surface_detail.guide_for`). ImageGen reskins that guide with detail at the tile's
scale and keeps its layout and colours, so canyons, dark plateaus and sand seas carry through
every level and the planet still reads as desert01 from orbit.

**Seams.** A finished tile is its generation's high frequencies over the guide's low
frequencies (`match_low`, at about three cells of the layer below). Where a tile's edge fades
out over the outer 14% of its half-width, both layers already agree in everything the lower
one can show, so the fade does not show.

## Settlements

`surface_detail.SETTLEMENTS` places them, in metres on the same plane, where `--check` shows
them in frame: under S1a a walled town where two wadis meet, a landing grid and a terraced
open-pit mine on the ridge, joined by haul roads; down S12's track a spaceport on the salt pan,
a walled city at the canyon mouth on the edge of the sand sea, and strip mines on the massif's
flank. No traffic and no lights: the battle is on the day side.

They are painted by inpainting: `settlement_guides.draw` sketches each plan (wall, block grid,
pads, benches, roads) over the finished tile, ImageGen renders the sketch as built ground
(`raw/<tile>-settled.png`), and only the pixels inside the settlement mask are composited back,
colour-matched to the tile (`mask_composite`).

## Files

- `surface_detail.py`: tiles, projection, compositing and settlements. Pure numpy, so the same
  code runs offline and inside Blender (which bundles numpy).
- `make_guides.py`: writes the guides (and with `--finish`, the finished tiles) under
  `build/asharu-surface/`. Guides are the game's map resampled, so they are rebuilt from the
  install and never committed.
- `settlement_guides.py`: the settlement plan sketches.
- `prompts/<tile>.txt`: the brief each generation was made from.
- `raw/<tile>.png` + `.provenance.json`: the accepted generations, with prompt, reference and
  role as sent. These are the only committed images.
- `ab_render.py`: the A/B queue job, the same key frames with the tiles and without.

To redo a tile: `python make_guides.py`, then generate from `build/asharu-surface/guides/<tile>.png`
with `tools/codex_imagegen.py` and the tile's prompt. A parent changed means its children's
guides changed: regenerate them too, from the top down.

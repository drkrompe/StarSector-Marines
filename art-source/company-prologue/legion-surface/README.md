# Legion: surfacing

Status: REVIEW (pass 6, texture pass 1 with the owner, 2026-10-19).
Written: 2026-10-19

The Legion (XIV) battlecarrier's folder for the shared surfacing harness
(`../second-wind-surface/`, run with `--hull`; see its README for the method).
Inputs and Codex generations live here. Textures, grading, review renders and
builds are script outputs and are not committed. `./rebuild.sh` replays
everything except Codex. The size stays at 0.99 m/u (292 m).

## Livery

Hegemony XIV battlecarrier, read off `legion_xiv.png`
(`reference/sprite-livery.png`, the sprite at 2x):
- warm grey hull plating on the torso, spines, sponsons and belly;
- XIV burnt orange armour on the bow blades, belts and wing plates;
- sandy tan plating on the wing armour's second course;
- the bone-white command dome;
- gunmetal machinery;
- near-black recesses, with a dull dark plate on the flight decks.

`surface_hull.py` maps the base materials to these classes. The turrets keep
the shared turret kit's own military livery, and the glazing and every lamp
keep their own materials. Wear runs the `service` row of `wear.json`, so the
hull stays damage-free on patrol.

- **Finish reference** (`reference/finish-reference.png`): one Codex reskin of
  the clean build's three-quarter camera, with the sprite as the colour key.
  It registers at IoU 0.997. The spreads are owned in `derive_palette`.
- **Swatches**: one Codex sheet of six squares (grey, orange, tan, bone,
  gunmetal, dark), tiled at 9 hull units (8.9 m). The deck is derived from dark.

## Parts

At 292 m the hull cannot be cut into whole faces at a usable density, so the
parts are **box tiles**. `make_groups.py` writes `groups/*.py` from the built
geometry:
- Each tile is a world-space box seen square-on from one side.
- Its members are the objects that a ray grid hits inside the box.
- Each box carries a containment `pad` (a `parts.py` box option): a face must
  also lie within the box grown by 10 units. A long plate whose centre falls in
  a tile therefore stays on its class swatch rather than stretching the tile
  across the ship.
- Port tiles are painted. Each starboard tile reuses its port twin mirrored
  (`skin_from`, `mirror_u`).

Rerun `make_groups.py` after any geometry rebuild, then `render_parts.py`.

The cut gives 35 generated sources plus 35 mirrored reuses, on nine Codex
sheets with one call each and no retries. Every tile holds 5.2–12 texels per
hull unit.

| Group | Sheets | Tiles |
|---|---|---|
| `top` | top-a, top-b, top-c | bow, fore, dome, waist, aft, machinery bed and stern decks; five wing-arm tiles |
| `side` | side-a, side-b, side-c | fore, mid and aft flank, upper and lower; bow upper and lower; stern inboard and outboard, upper and lower |
| `belly` | belly-a, belly-b, belly-c | bow, then fore, mid-fore, mid, mid-aft and stern, each inboard and outboard |

Tiles fit at IoU 0.76–0.99. The fragmented tiles (many small plates per tile)
fit lower than whole faces do, and the belly's stern tiles fit lowest (0.76–0.80).
In views, about 40–60% of the hull is skinned and the rest is on swatch.

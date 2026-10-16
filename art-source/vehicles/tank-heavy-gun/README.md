# Heavy-gun tank variant

The breakthrough tank (`art-source/vehicles/tank/`) carrying one long heavy gun:
thick tube, heavy mantlet block, fume-extractor collar partway along and a
squared double-baffle muzzle brake. Same hull, same angular turret housing,
same paint and weathering. Output: `mod/graphics/battle/vehicles/army-tank-heavy-gun.png`,
3072x768, four 768x768 frames (live hull, live turret, wreck hull, wreck turret),
all facing LEFT, strict top-down.

## Sources

- `guide.py` writes the edit targets and geometry guides from the tank masters:
  `turret-target.png` / `wreck-target.png` are `turret-master.png` /
  `wreck-master.png` with the hull erased (turret alone, same canvas, pose and
  scale); `turret-guide.png` / `wreck-guide.png` add the heavy gun as flat blocks
  (the wreck's bent slightly near the brake).
- `turret-raw.png` and `wreck-raw.png` are unmodified 1774x887 RGBA ImageGen
  edits (one generation each, `tools/codex_imagegen.py`), made with
  `turret-prompt.txt` / `wreck-prompt.txt`: the target as identity/material
  authority, the guide as gun geometry authority. Provenance sits beside each.
  Both kept the housing registered to within ~3 px of the masters but drew the
  gun about 110 px (live) / 80 px (wreck) longer than the guide.
- `pack.py` + `registration.json` produce the sheet. Hull frames are copied
  verbatim from `army-tank.png`, so both variants share one hull pixel-for-pixel.
  Turret frames take the raw, remove straight runs of plain tube (`shorten`:
  live 65 px ahead of and 44 px behind the fume extractor; wreck 30 + 25 px) to
  bring the muzzle to its registered length, clear generator alpha haze <= 8,
  and register the bearing to the frame center (Lanczos, premultiplied).

```
python art-source/vehicles/tank-heavy-gun/guide.py   # targets and guides only
python art-source/vehicles/tank-heavy-gun/pack.py
```

## Registration

| Frame | Source bearing | Scale | Frame cells |
|-------|----------------|-------|-------------|
| live hull | as `army-tank.png` frame 0 | 0.73 | 4.3 |
| live turret | (1498,454) in `turret-raw.png` | 0.7 | 7.789194 |
| wreck hull | as `army-tank.png` frame 2 | 0.835 | 4.3 |
| wreck turret | (1344,447) in `wreck-raw.png` | 0.855 | 7.789194 |

Bearings land on the frame centers; 98.6 frame px per turret cell.

## Muzzle

Live muzzle, after shortening, is source (1012,454): **3.450 cells** forward of
the bearing with **zero lateral offset** (the tube's measured center is within
1 frame px, ~0.01 cells, of the axis). That is 1.54x the breakthrough cannon's
2.2434501 cells. The wreck's bent barrel ends at about 3.50 cells, ~0.07 cells
off-axis; it never fires.

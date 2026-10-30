# Screened tank variant (retired hull; the active-protection kit's source)

The Parapet is no longer a hull: active protection is a rack kit (`VehicleKit.ACTIVE_PROTECTION`) and the Parapet is
the Onager with it (`VehicleStockFit.PARAPET`). Its sheet no longer ships. These ImageGen sources stay as the kit's
art source: `pack.py` now writes only the live turret frame to
`art-source/vehicles/kit-active-protection/screened-turret-frame.png`, and that folder's `build.py` lifts the
launchers and sensor panels off it. What follows is the original record.

The breakthrough tank (`art-source/vehicles/tank/`) carrying active protection: a
compact interceptor launcher box on each rear turret flank (2x3 tube mouths,
hanging slightly outboard), a flat radar/sensor panel inset on each cheek wedge
and a short sensor mast on the rear roof. Same hull, same housing and cannon,
same paint and weathering. Output: `mod/graphics/battle/vehicles/army-tank-screened.png`,
3072x768, four 768x768 frames (live hull, live turret, wreck hull, wreck turret),
all facing LEFT, strict top-down.

## Sources

- `guide.py` writes the edit targets and geometry guides from the tank masters:
  `turret-target.png` / `wreck-target.png` are the masters with the hull erased;
  `turret-guide.png` / `wreck-guide.png` add the fit as flat blocks (the wreck's
  upper launcher knocked askew).
- `turret-raw.png` and `wreck-raw.png` are unmodified 1774x887 RGBA ImageGen
  edits (one generation each, `tools/codex_imagegen.py`) made with
  `turret-prompt.txt` / `wreck-prompt.txt`: target as identity/material
  authority, guide as geometry authority. Provenance sits beside each.
- `pack.py` + `registration.json` produce the sheet. Hull frames are copied
  verbatim from `army-tank.png`.

```
python art-source/vehicles/tank-screened/guide.py   # targets and guides only
python art-source/vehicles/tank-screened/pack.py
```

## Registration

| Frame | Source bearing | Scale | Frame cells |
|-------|----------------|-------|-------------|
| live hull | as `army-tank.png` frame 0 | 0.73 | 4.3 |
| live turret | (1495,457) in `turret-raw.png` | 0.636364 | 7.789194 |
| wreck hull | as `army-tank.png` frame 2 | 0.835 | 4.3 |
| wreck turret | (1344,447) in `wreck-raw.png` | 0.855 | 7.789194 |

The live edit came back 1.10x the target about (1495,457) (alpha IoU 0.988
outside the launchers), so its scale is 0.7 / 1.1. Its muzzle is source
(1149,458): 2.233 cells forward, ~0.01 cells lateral, against the breakthrough
mount's 2.2434501 — the shared `TANK_CANNON` mount needs no change.

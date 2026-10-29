# Laser tank (Tri-Tachyon)

The breakthrough hull (`art-source/vehicles/tank/`) in Tri-Tachyon's white livery, carrying the
vanilla red PD laser as its turret on a ship's turret mount. The owner's direction (2026-10-22): the
turret is the space game's own PD laser, up-res'd, so a player recognises it from fleet combat;
its shutter opens on the lens while it fires. Output:
`mod/graphics/battle/vehicles/army-tank-laser.png`, 3840x768, five 768x768 frames (live hull,
live turret, wreck hull, wreck turret, firing turret), all facing LEFT, strict top-down.

## Sources

Everything here is one ImageGen generation per raw (`tools/codex_imagegen.py`), with its prompt
and provenance beside it.

- **Turret.** `upres_guide.py` turns the install's `pd_laser_turret_base.png` (24 px) to face left
  and enlarges it 40x (nearest) as `pd-upres-guide.png`, git-ignored because it is the game's art.
  `pd-upres-raw.png` repaints it (`pd-upres-prompt.txt`); `pd-upres-open-raw.png` is that edit with
  the dome's shutter open on a red lens (`pd-open-prompt.txt`). The skins are edits of
  `pd-upres-raw.png` that keep its shape: `pd-skin-red` (vanilla colours, weathered; shipped),
  `pd-skin-tt` (white ceramic, graphite, cyan) and `pd-skin-redwhite` (red collar on a white body),
  each with an `-open` edit (`pd-skin-open-prompt.txt`); `pd-skin-red-wreck-raw.png` is the wreck.
- **Mount.** Starsector draws no separate mount sprites: a turret's mount is painted into its hull.
  `mount_guide.py` crops a disc of the Eagle around its small energy slot `WS 010`, turned to face
  left and enlarged 30x (`mount-upres-guide.png`, git-ignored); `mount-upres-raw.png` repaints it.
- **Hull.** `hull_guide.py` writes `hull-target.png` (the tank master with the turret half erased)
  and `hull-guide-b.png` (the up-res mount set into the deck at the bearing, sized for a 1.25-cell
  turret, and the capacitor pack blocked over the rear grilles). `hull-raw-b.png` is the livery
  pass, with the Tri-Tachyon scout hull as livery reference; `hull_b4_guide.py` + `hull-raw-b4.png`
  are the shipped pass on top of it: crew window raised to the crest of a sloped glacis, cool
  neutral wear (an earlier warm-grime pass read as gold). `hull-wreck-raw.png` wrecks it.
- `pack.py` + `registration.json` produce the sheet.

```
python art-source/vehicles/tank-laser/upres_guide.py    # vanilla guides, from the install
python art-source/vehicles/tank-laser/mount_guide.py
python art-source/vehicles/tank-laser/hull_guide.py     # hull target and guide
python art-source/vehicles/tank-laser/pack.py
```

## Registration

| Frame | Source | Registration |
|-------|--------|--------------|
| live / wreck hull | `hull-raw-b4.png` / `hull-wreck-raw.png` | bearing (508,451) at 0.73, as the tank master: 4.3 cells per frame, so the tread strips `VehicleTreadRender` scrolls land on its tracks |
| live / wreck / firing turret | `pd-skin-red*.png` | pivot at the vanilla sprite's centre, (627,627); the 668-px body is 1.25 cells; a frame spans 1.6 cells (`turretVisualCells`) |

ImageGen sometimes returns an edit at a different size (the red/white skin 1.32x, the wreck turret
about 1.3x), so `pack.py` fits each turret source to `pd-upres-raw.png` by its alpha bounding box.
The muzzle is the lens front, 0.62 cells forward on the axis (`mount.tank-burst-laser`).

## Two variants (2026-10-23)

The owner kept two turret skins and split the tank in two:

- **Burst laser tank** (`LASER_TANK`, Tri-Tachyon): `registration.json` packs
  `hull-raw-b4.png` with the `tt` skin (`pd-skin-tt-raw.png`, its open frame, and
  `pd-skin-tt-wreck-raw.png`) into `mod/graphics/battle/vehicles/army-tank-laser.png`.
- **PD laser tank** (`PD_LASER_TANK`, League; retired 2026-10-28, its gun is the PD laser part and the shipped sheet
  is deleted; these sources are kept): `registration-pd.json` packs
  `hull-raw-pd.png` with the `red` skin into `army-tank-pd-laser.png`:
  `python art-source/vehicles/tank-laser/pack.py registration-pd.json mod/graphics/battle/vehicles/army-tank-pd-laser.png`.

`hull-raw-pd.png` is an edit of `hull-raw-b4.png` on the same canvas (so the
master's registration and tread strips hold), with `hull_pd_guide.py` /
`hull-guide-pd.png` blocking a fire-control radar and a three-cell bank over the
capacitor pack's footprint (`hull-prompt-pd.txt`): worn gunmetal, red lamps, red
hazard trim. `hull-wreck-pd-raw.png` wrecks it (`hull-wreck-pd-prompt.txt`), and
`pd-skin-tt-wreck-raw.png` wrecks the `tt` turret (`pd-skin-tt-wreck-prompt.txt`).
The third skin (red collar on white) was not kept and is deleted.

## The red head on the laser tank's sheet (2026-10-29)

`army-tank-laser.png` is eight frames: the five above, then the red skin's live, wreck and firing turret (frames 5, 6
and 7, the PD laser tank's own, packed by `registration.json` from `pd-skin-red-raw.png`, `-wreck-raw.png` and
`-open-raw.png`). A laser tank with the heavy PD laser part fitted draws them in place of the white head
(`MechWeaponComponent.tankHead`, `VehicleType.headFrame`); with the heavy burst laser part fitted or none, it draws the white
head. Both fire from the head's lens, 0.625 cells forward, whichever part is fitted.

# Dozer breacher tank

The breakthrough tank's hull (`../tank/master.png`, `../tank/wreck-master.png`)
with a dozer blade on its nose and the APC's roof MG in place of the cannon.
Marine Operations art direction, not vanilla canon.

The sheet is drawn at `visualLengthCells` 4.6 (the blade adds length), so the
hull is re-registered at the breakthrough scales times 4.3/4.6 (0.68239 live,
0.78054 wreck) on the same bearings, and draws at the same on-screen size as
the 4.3-cell tank. Turret slots 1 and 3 are `army-apc.png` frames 1 and 3,
copied pixel-exact and padded 96 px top and bottom, so their scale and muzzle
registration are the APC's.

The blade is the only new art. `blade_geometry.py` is the geometry authority
(intact and wrecked silhouettes in 768-pixel frame coordinates). `guide.py`
writes `guide-live.png` / `guide-wreck.png`: the registered hull at 2x with the
blade blocked in as flat grey. Built-in ImageGen edited each guide once
(`prompt-live.txt`, `prompt-wreck.txt`; raw results `blade-*-raw.png` with
provenance). `pack.py` resamples a raw edit to the frame, keeps only pixels
inside the geometry silhouette (dilated 6 px, left of x=124) and draws the
registered hull exactly over it, so every hull pixel is the breakthrough tank's.

```powershell
python art-source/vehicles/tank-dozer/guide.py   # only to regenerate guides
python art-source/vehicles/tank-dozer/pack.py
```

Measured in the 768 frame: blade leading edge x=8 (2.25 cells forward of
center), span y=172..595. Tread bands for `VehicleTreadRender` scale with the
hull: x=107..667, y=200..227 and y=539..562, repeat about 101 px starting at
x=110. The blade and arms lie wholly outside those bands. The wreck blade is
knocked askew and torn, the upper arm kinked and its ram snapped; no painted
smoke or fire.

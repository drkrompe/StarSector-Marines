# Missile carriers

`pack.py` packs `mod/graphics/battle/vehicles/lrm-carrier.png` and `srm-technical.png` (four 768x576 frames each:
live hull, live turret, wreck hull, wreck turret). The hulls are the heavy APC's and the technical's, verbatim.

## LRM-15 launcher box

The LRM carrier carries one fifteen-tube LRM-15 box on its roof turntable (it carried two mech LRM pods until
2026-10-23). The box is geometry-first:

- `lrm15_guide.py` draws `lrm15-guide.png`: a 1024x1024 overhead box facing left, centred on the canvas, with a
  sunken tube face of fifteen mouths (three deep, five across), a raised cover and centre strap, side lugs and a
  tail trunnion. The canvas centre is the turntable pivot.
- `lrm15.prompt.txt` is the exact ImageGen brief; the guide was the geometry reference and the mech
  `lrm-pod.png` the material reference. `lrm15-master.png` is the untouched 1254x1254 result, with its
  provenance beside it. It registers to the guide at a uniform 1254/1024.
- `pack.py` snaps the master's near-opaque alpha, scales it so the box is 0.72 cells nose to tail at the APC's
  320 px per cell, and keeps the canvas centre on the frame centre. The wreck turret is the same box darkened and
  knocked 9 degrees off its bearing.

The mount's 15 muzzles in `turret-emplacements.turret.json` (`mount.vehicle-lrm`) are those tube mouths in cells,
`[lateral, forward]` from the pivot: lateral 0, +/-0.122, +/-0.244 and forward 0.269, 0.149, 0.029, listed in an
order that walks across the face so a salvo ripples over the whole box. Change the box size or the guide and they
move with it.

```
python art-source/vehicles/missile-carriers/lrm15_guide.py
python art-source/vehicles/missile-carriers/pack.py
python art-source/dolls/build_dolls.py
```

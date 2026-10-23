# Missile carriers

`pack.py` packs `mod/graphics/battle/vehicles/lrm-carrier.png` and `srm-technical.png` (four 768x576 frames each:
live hull, live turret, wreck hull, wreck turret). The hulls are the heavy APC's and the technical's.

## LRM carrier: one pod that stands up to fire

The carrier carries ONE mech LRM pod (`mech-modular-topdown/lrm-pod.png`, the LRM-15's rack) alone in its turret
frame, centred on the frame centre, which is the turntable pivot, and 0.72 cells nose to tail. The turntable plate
is painted on the hull frames at the APC's roof bearing (335, 288) instead of under the pod, because the pod frame
is foreshortened when it raises and a flat turntable must not tilt with it.

The raise is a render transform, not a second frame. `GroundTurret.elevation` runs 0 to 1 over 0.6 s while the
launcher has a target, is firing, or fired in the last two seconds, and back down otherwise. The renderer draws
the turret frame `VehicleType.turretRaiseFold` (0.78) as long along its firing axis at full raise, keeps the pod's
tail hinge (`turretRaiseHingeCells`, half the pod) where it was, and draws the frame a little wider, since the
nose has come up toward the eye. The wreck pod lies flat.

The mount's nine muzzles (`mount.vehicle-lrm`) are the pod's tube tips at the raised pose: lateral 0 and
+/-0.144, forward 0.142, 0.082 and 0.031 cells, in a Latin-square order so a salvo walks across the pod. Change the
pod's size, the fold or the hinge and they move with it.

```
python art-source/vehicles/missile-carriers/pack.py
python art-source/dolls/build_dolls.py
```

# Missile carriers

`pack.py` packs `mod/graphics/battle/vehicles/lrm-carrier.png` and `srm-technical.png` (four 768x576 frames each:
live hull, live turret, wreck hull, wreck turret). The hulls are the heavy APC's and the technical's.

## LRM carrier: one pod that stands up to fire

The carrier carries ONE mech LRM pod (`mech-modular-topdown/lrm-rack.png`, the LRM-15's rack) alone in its turret
frame, centred on the frame centre, which is the turntable pivot, and 0.72 cells nose to tail. The turntable plate
is painted on the hull frames at the APC's roof bearing (335, 288) instead of under the pod, because the pod frame
is foreshortened when it raises and a flat turntable must not tilt with it.

The raise is a render transform, not a second frame. `GroundTurret.elevation` runs 0 to 1 over 0.6 s while the
launcher has a target, is firing, or fired in the last two seconds, and back down otherwise. The renderer pitches the
pod to the slope its missiles' drawn arc leaves at and draws it along that arc's first tangent on screen, so it is
turned and foreshortened, a little wider for its nose coming up toward the eye, and always centred on the turntable:
the raise never moves the pod off its ring. Firing toward the bottom of the screen it stands only as tall as still
points at the target. The wreck pod lies flat.

## Wrecked pods

A wrecked pod is a real damage edit, not the live pod darkened: `wreck_guide.py` sets each live pod nose-left on a
1024 px canvas, ImageGen breaks it in place (`lrm-wreck.prompt.txt`, `srm-wreck.prompt.txt`; the raws and their
provenance sit beside them), and `pack.py` lays each raw back over its edit target with `wreck_fit.fit_to`
(`art-source/vehicles/`), scales it like the live pod and throws it a few degrees askew. The image tool's violence
filter refused the SRM edit twice; the shipped prompt describes it as a broken equipment box and passes the LRM wreck
as the condition reference.

The mount's nine muzzles (`mount.vehicle-lrm`) are the pod's tube tips at a representative raise
(`VehicleType.turretRaiseFold`, 0.78 of their flat reach about the pod's centre): lateral 0 and +/-0.144, forward
0.222, 0.161 and 0.110 cells, in a Latin-square order so a salvo walks across the pod. Change the pod's size or the
fold and they move with it.

```
python art-source/vehicles/missile-carriers/wreck_guide.py
python art-source/vehicles/missile-carriers/pack.py
python art-source/dolls/build_dolls.py
```

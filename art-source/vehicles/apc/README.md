# APC hull and roof MG

Created with built-in ImageGen using `shape.prompt.txt`, `guide.png` as geometry,
and `material-reference.png` as the material reference. `guide.ps1` reproduces
the silhouette: a left-facing six-wheel overhead hull with a tapered wedge
nose, sloped glacis and shoulder planes, squared troop compartment and rear
ramp. Plane values imply height without exposing vertical sidewalls. An opaque
roof bearing receives a separate left-facing twin MG; only tire tread tops
are visible. The material reference came from the retained `first-guide.png`
and `prompt.txt` / `first-pass.png`, followed by `twin-mg.prompt.txt`.

`master.png` retains the unmodified 2048x768 generation. ImageGen moved and
rescaled the components relative to the guide, so `registration.json` records
their measured centers. `pack.ps1` translates each component and applies the
same uniform 2/3 scale, preserving original alpha, into two 768x576 frames.
It does not stretch, repaint, or key out the art. Run from PowerShell:

```powershell
& art-source/vehicles/apc/guide.ps1
& art-source/vehicles/apc/pack.ps1
```

The packed runtime asset is `mod/graphics/battle/vehicles/army-apc.png`.
Both hull and gun point left and use a -90 degree facing offset. The hull's
roof bearing is at (335.333, 288) within its frame; the body reference is
(384, 288). At the existing hull sizes this gives a forward mount offset of
0.15208333 cells for HEAVY_APC and 0.20277778 cells for ESCORT_APC, with zero
lateral offset. The gun bearing is at its frame center (384, 288), so neither
variant needs a compensating turret pivot offset. Both production and headless
loaders retain these full frames instead of trimming their transparent margins.

Both variants carry the same MG at a 1.42-cell frame width. Measured muzzle
centers are 314 pixels forward and +/-60.667 pixels lateral: 0.580573 and
0.112170 cells. The existing shared MG alternates between ballistic hardpoints
0.58 forward and +/-0.13 lateral; both lie on the muzzle-brake faces (the
lateral difference is under two pixels in the close view). Do not scale the
gun with the chassis or alter the shared weapon definition to fit its picture.

Review through `gradlew createSnapshots -Psnapshot=apc`: cardinal hull poses,
battle-scale still, a muzzle annotation, and a full independent turret sweep.
These are controlled art poses through production convoy rendering, not a
simulation of traverse timing. Frames are 1920x1080 with 100% UI; only the
camera changes zoom. Inspect the roof bearing for wobble and compare each
small cross (a production ballistic muzzle) with the corresponding barrel tip.

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
their measured centers. `pack.ps1` translates each live component at the
original uniform 2/3 scale, preserving original alpha, into four 768x576 frames:
live hull, live turret, wreck hull, wreck turret.
It does not stretch, repaint, or key out the art. Run from PowerShell:

```powershell
& art-source/vehicles/apc/guide.ps1
& art-source/vehicles/apc/pack.ps1
```

The packed runtime asset is `mod/graphics/battle/vehicles/army-apc.png`.
Both hull and gun point left and use a -90 degree facing offset. The hull's
roof bearing is at (335.333, 288) within its frame; the body reference is
(384, 288). At the existing hull sizes this gives a forward mount offset of
0.15208333 cells for the APC chassis (HEAVY_APC and the gun carriers built on it), with zero
lateral offset. The gun bearing is at its frame center (384, 288), so neither
variant needs a compensating turret pivot offset. Both production and headless
loaders retain these full frames instead of trimming their transparent margins.

Both variants carry the same MG at a 1.42-cell frame width. Measured muzzle
centers are 314 pixels forward and +/-60.667 pixels lateral: 0.580573 and
0.112170 cells. The existing shared MG alternates between ballistic hardpoints
0.58 forward and +/-0.13 lateral; both lie on the muzzle-brake faces (the
lateral difference is under two pixels in the close view). Do not scale the
gun with the chassis or alter the shared weapon definition to fit its picture.

The built-in ImageGen damage edit retains its exact `wreck-prompt.txt`, input
`wreck-guide.png` and untouched `wreck-master.png`. The guide is the registered
live sheet. A 0.7 uniform scale registers the wreck hull reference at
(552.524,363.5), its roof bearing at (483,363.5), and the turret bearing at
(1690,375). The hull remains an opaque carrier with a breached troop roof,
crushed rear tires and scorched thin plates; the independent twin MG has a
broken barrel and bent armor. No fire, smoke or ground shadow is baked in.
Live frame pixels and all ballistic hardpoints remain unchanged.

`VehicleTreadRender` scrolls six narrow tire-top UV windows, sharing the tank's
distance-driven renderer. Each wheel keeps its rounded ends and the gaps between
wheels stay empty. The three x intervals are 148..246, 344..444 and 552..652;
upper y=111..141, lower y=432..460. Each reuses a 42-pixel painted tread repeat
from its own wheel. Signed accepted travel and yaw drive each side after the
collision sweep, so reverse reverses the animation and a blocked or stopped
APC does not roll. This preserves the six-wheel silhouette and steering model.
Road movement omits loose-ground dust bars.

Both APC variants use a smaller destruction blast and exact roof/rear-compartment
emitters scaled to their hulls. The roof burns for three seconds and smokes for
20; the rear burns for ten seconds and smokes for 55. The heavier escort's effects
are larger than the reinforcement carrier's but smaller than the tank's.
Destruction keeps both final bearings and stops tread animation. Passenger,
damage, navigation and cover rules are unchanged.

Review through `gradlew createSnapshots -Psnapshot=apc`: cardinal hull poses,
battle-scale still, a muzzle annotation, and a full independent turret sweep.
These are controlled art poses through production convoy rendering, not a
simulation of traverse timing. Frames are 1920x1080 with 100% UI; only the
camera changes zoom. Inspect the roof bearing for wobble and compare each
small cross (a production ballistic muzzle) with the corresponding barrel tip.

The same suite renders `wreck-headings.png` for both sizes at independent
bearings, then Thunder Run seed 9301 through its production campaign factory.
`campaign-comparison.png` keeps initial vehicle positions. `tracks-drive.gif`
drives an escort with controlled forward/steer/brake/reverse inputs and empty
magazines; `destruction.gif` follows a controlled lethal hit through the real
effect drains; `campaign-wreck.png` omits particles to show the damage beside
the tank and freight. These are art/motion fixtures, not mission balance or
live-save acceptance.

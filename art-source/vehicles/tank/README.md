# Breakthrough tank and single cannon

Built-in ImageGen produced the approved Abrams/Type 10-inspired hull and armor
from the original prompt in `prompt.txt`. The current `guide.ps1` / `guide.png`
retain that flat-fronted chassis and angular turret, with a shorter single cannon.
The exposed tube is roughly half its previous length, with one modest collar
and a plain muzzle. The compact armor cheeks and squared rear bustle still
carry the silhouette. Faded khaki/olive-gray paint, ochre remnants and replacement
panels suggest maintained, century-old Hegemony salvage. This provenance is
Marine Operations art direction, not vanilla canon.

`master.png` is the unmodified 1774x887 original generation and remains the
hull source. `turret-master.png` is the unmodified 1774x887 ImageGen edit made
with `turret-prompt.txt`, using the original master as the edit target and
the updated guide as the barrel geometry reference. Only its turret is used;
the approved hull pixels are retained exactly.

`registration.json` names the source, measured rectangle, bearing and uniform
scale for each component. `pack.ps1` preserves alpha and proportions while
registering four full 768x768 slots (live hull, live turret, wreck hull, wreck turret) into
`mod/graphics/battle/vehicles/army-tank.png`. Reproduce with:

```powershell
& art-source/vehicles/tank/guide.ps1
& art-source/vehicles/tank/pack.ps1
```

The hull bearing remains (508,451), scaled by 0.73. The new turret bearing is
(1498,454), scaled by 0.7; its muzzle is (1182,454). Both components land on
their output frame centers, with zero mount/pivot compensation. The unchanged
4.3-cell hull frame and 7.789194-cell turret frame retain their alpha margins.
The new centered muzzle is 2.2434501 cells forward, down from 3.21, with zero
lateral offset. These are measured art registration values, not balance tuning.

The existing two-round burst, damage, ammunition, range and recoil remain.
Both rounds now leave the same barrel. The visible catalog name is Breach
Cannon; internal `tank-twin-cannon` ids and localization keys remain stable
for compatibility with existing references.

The built-in ImageGen wreck edit is retained unmodified in `wreck-master.png`,
with its exact `wreck-prompt.txt` and registered input `wreck-guide.png`.
Its hull bearing (448,443), scale 0.835, and turret bearing (1344,447), scale
0.855, register to the last two frame centers. The destruction transition keeps
both final bearings. Torn armor, broken tread and damaged engine grilles remain
visible after the live effects expire; there is no painted smoke or fire.

`TankTrackRender` overlays two narrow wrapped UV strips from the live hull's
own tread art. In the 768-pixel hull frame, the bands run x=88..687, y=187..216
and y=550..574; a 108-pixel repeat starts at source x=91. Fixed armor and end
caps keep their original pixels. The chassis controller accumulates accepted
signed travel and yaw after collision resolution, and each side adds/subtracts
its roughly one-cell distance from the center. Forward, reverse and turning
therefore move the tread material; stopped, blocked and wrecked tanks do not.
Teleport resets the visual odometers. This adds presentation, not differential
steering or track-slip physics. Tank road movement omits the loose-ground dust bars.

Tank destruction emits one bounded blast and two exact-position emitters:
a six-second roof burn with 35 seconds of smoke, and an 18-second rear-engine
burn with 90 seconds of cooling smoke. These use the shared live FX drains,
cause no secondary damage and do not change navigation or cover.

Run `gradlew createSnapshots -Psnapshot=tank` for headings, battle scale,
marked muzzle rotation and actual autonomous firing/recoil. The suite also
builds Thunder Run seed 9301 through its production campaign factory and renders
`campaign-context.png` and `campaign-comparison.png`: the actual starting
tank, two APC escorts, technical and three cargo haulers. Initial positions and
terrain are untouched; turret bearings are posed forward for visual comparison.
`tracks-drive.gif` then uses controlled throttle/steering through the shipping
chassis and collision sweep on that map (empty gun magazines for the review).
`destruction.gif` feeds the real lethal-damage event and every subsequent tick
into the production impact recipe. `campaign-wreck.png` omits transient
particles to expose the persistent damage artwork beside the other vehicles.
All artifacts are 1920x1080 at 100% UI; close views change only camera zoom.
These are controlled art/motion/destruction fixtures, not mission balance,
player tank-control availability or live-save acceptance.

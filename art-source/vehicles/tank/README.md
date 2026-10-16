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
registering two full 768x768 slots into
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

Run `gradlew createSnapshots -Psnapshot=tank` for headings, battle scale,
marked muzzle rotation and actual autonomous firing/recoil. The suite also
builds Thunder Run seed 9301 through its production campaign factory and renders
`campaign-context.png` and `campaign-comparison.png`: the actual starting
tank, two APC escorts, technical and three cargo haulers. Initial positions and
terrain are untouched; turret bearings are posed forward for visual comparison.
All artifacts are 1920x1080 at 100% UI; close views change only camera zoom.
This is pre-combat art evidence, not a mission balance or live-save acceptance run.

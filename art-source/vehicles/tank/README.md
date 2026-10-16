# Breakthrough tank and twin cannon

Generated with built-in ImageGen from the geometry in `guide.png` and the exact
saved `prompt.txt`. This pass replaces the rejected projecting bow and rounded
turret with a broad, flat-fronted chassis inspired by the M1 Abrams and Japanese
Type 10: long track runs, shallow front corner clips, a sloping upper glacis,
driver position forward of the bearing and a rectangular rear engine deck.

The compact independent turret has separate angular cheek modules, a narrow
mantlet and a squared rear ammunition bustle. Broad plane values and restrained
bevels imply height from strict overhead. The twin cannons retain the existing
weapon contract. Faded khaki/olive-gray paint, ochre remnants and mismatched
replacement panels suggest maintained, scavenged Hegemony equipment a century
old. This is user-directed Marine Operations fiction, not vanilla provenance.

`master.png` is the unmodified 1774x887 generated result. ImageGen changed the
guide's component scale and placement. `registration.json` records the measured
rectangles, centers and uniform scales. `pack.ps1` preserves source alpha and
proportions while registering two full 768x768 slots into
`mod/graphics/battle/vehicles/army-tank.png`. Reproduce with:

```powershell
& art-source/vehicles/tank/guide.ps1
& art-source/vehicles/tank/pack.ps1
```

The hull bearing is at source (508,451), uniformly scaled by 0.73. The turret
bearing is (1498,460.5), uniformly scaled by 0.7309469115702443. Both land on
the centers of their output frames; mount and pivot compensation remain zero.
The registered-frame loader retains alpha margins in production and headless
review, rather than trimming the barrels and shifting the pivot.

The unchanged hull frame is 4.3 cells long; the gun frame is 7.789194 cells.
Gun source tips (1065,435) and (1065,486) land 3.21 cells forward and
+/-0.18904 cells laterally, within 0.001 cells of the shared +/-0.19 hardpoints.
Frame size includes transparency and is not the physical turret width.
Weapon damage, ammunition, cadence, range, chassis footprint, movement and
existing visual recoil are unchanged.

Review using `gradlew createSnapshots -Psnapshot=tank`. The suite renders
four hull headings with independent gun bearings, a battle-scale still,
a full posed turret rotation with crosses on both ballistic muzzles, and actual
autonomously fired paired shots through the existing recoil renderer. All artifacts
are 1920x1080 at 100% UI; close views change only the camera. Rotation poses
are art evidence, not traverse timing or mission acceptance.

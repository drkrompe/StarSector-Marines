# Breakthrough tank and twin cannon

Generated with built-in ImageGen from `guide.png` and `prompt.txt`, using
`../apc/master.png` as the material reference. `guide.ps1` retains the geometry:
a projecting wedge nose, sloped glacis, layered shoulder armor, continuous
track tops and a squared rear engine deck. The independent turret has thick
forward cheeks, a raised crown and a rear bustle. Broad plane values and bevels
imply volume from strict overhead, so front and rear remain readable at any
hull or gun bearing.

`master.png` is the unmodified 1774x887 generated result. ImageGen changed the
guide's component scale and placement. `registration.json` records the measured
rectangles, centers and uniform scales. `pack.ps1` preserves source alpha and
proportions while registering two full 768x768 slots into
`mod/graphics/battle/vehicles/army-tank.png`. The previous unused third component
is gone. Reproduce the guide and packed output with:

```powershell
& art-source/vehicles/tank/guide.ps1
& art-source/vehicles/tank/pack.ps1
```

The hull's opaque bearing is centered at source (507,461), and the independent
turret at (1530,465). Both land on the centers of their output frames; mount
and pivot compensation are zero. The registered-frame loader retains alpha
margins in production and in the headless review, rather than auto-trimming
the gun's long barrel and shifting its pivot.

Hull art uses the existing 4.3-cell frame length. The gun's mostly transparent
frame is 7.789194 cells wide, placing the visible muzzle tips 3.21 cells ahead
of its bearing. This is a frame size, not the turret's physical width. Source
tips at (1108,441) and (1108,490), uniformly reduced by 0.75, give lateral
offsets +0.18256 and -0.19017 cells. Both lie within 0.008 cells of the shared
paired-cannon hardpoints (+/-0.19). The weapon's damage, ammunition, cadence,
range, chassis footprint, movement and existing visual recoil remain unchanged.

Review using `gradlew createSnapshots -Psnapshot=tank`. The suite renders
four hull headings with independent gun bearings, a battle-scale still, a
full posed turret rotation with crosses on both ballistic muzzles, and actual
autonomously fired paired shots through the existing recoil renderer. All artifacts
are 1920x1080 at 100% UI; close views change only the camera. Rotation poses
are art evidence, not traverse timing or mission acceptance.

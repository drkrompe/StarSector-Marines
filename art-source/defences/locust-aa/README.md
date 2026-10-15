# Locust ground AA art

Geometry-guided art for a ground emplacement inspired by the vanilla Locust
SRM Launcher. The intended family also includes the Swarmer SRM Launcher and
Proximity Charge Launcher; those two art passes are still pending.

## Deliverables

- `locust-mount.master.png`: unmodified built-in ImageGen reskin of the approved
  elongated microphone-shaped head, its forward launch tubes, clipped side
  trunnions and circular gimbal floor. Olive-gray armor, graphite bearings and
  restrained panel detail use the vanilla sprite as a palette/style reference.
- `locust-reskin-guide.png`: centered 1024-square overhead render of the approved
  Blender assembly. `render-reskin-guide.py` derives it from the retained
  `proposals/microphone-head/proposal.blend` without changing that scene.
- `ground-base.master.png`: separate stationary foundation, up-resed from the
  project's Ridgewatch base. This is new ground-emplacement art, not a vanilla
  Locust layer.
- `layers/locust-mount.png` and `layers/ground-base.png`: 512 x 512 RGBA exports,
  with registration pivots recorded in `export-metadata.json`. The mount includes the
  rotating gimbal floor; the ground base is a separate stationary foundation.
  Draw base then mount; only the mount rotates. Forward is image up. Pitch is
  baked into this sprite at 20 degrees, not independently animated.
- `review.png`: 1920 x 1080 art contact sheet at 100% scale, with the original,
  approved guide, reskin and 64/128/192-pixel canvas size studies. These sizes
  show readability, not an established runtime footprint.
- `composite.png`: transparent 512-square tower/mount assembly at the shared
  center pivot. `composite-review.png` shows forward and 45-degree traverse
  views plus 256/192/128/64-pixel canvas studies at 1920 x 1080.
- The two `*.prompt.txt` files preserve the exact built-in ImageGen prompts.
  The revised mount prompt uses the new circular-floor overhead guide and
  `sources/locust-square-platform.png`, the prior approved painted mount,
  as its material and unchanged-head reference. The guide owns the new floor
  silhouette; the retained art supplies the established finish and head details.

The elongated head was already approved. The user requested a rounded rotating
platform after reviewing it over the tower; its new painted finish remains for
review. The circular floor has radius 1.02, thickness 0.22 and center Z 0.22
(top Z 0.33). Rear cooling ribs taper inside its perimeter. Head dimensions,
18 parallel launch bores, 20-degree displayed pitch, side cheeks, trunnions and
axle height are fixed. The foundation remains a separate stationary layer.

These are art-review assets. No runtime catalog entries, combat stats, world
footprint, destruction states or firing origins are authored by this pass.
The approved Blender assembly owns the shape; vanilla art supplies only the
material reference. This is a new interpretation, not recovered original
high-resolution detail. Visual comparison retains the elongated silhouette,
visible bore arrangement and gimbal connections, but ImageGen is not a
pixel-exact geometry renderer. Author firing origins against this assembly
when the weapon is integrated; vanilla offsets do not define its new bores.

## Reproduce the exports

From the repository root in PowerShell:

```powershell
./art-source/defences/locust-aa/export.ps1
./art-source/defences/locust-aa/composite.ps1
```

To regenerate the input guide first:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python art-source/defences/locust-aa/render-reskin-guide.py
```

The exporter retains both complete master frames and downsamples them without
trimming, recentering, recoloring or painting. It checks square dimensions,
real transparency, nonempty visible bounds and clear visible borders, then
writes `export-metadata.json` and the review sheet. The circular floor encloses
the complete mount, so its visible bounds locate the painted yaw center at
(256.5, 248.5) in the 512-square export. This compensates for ImageGen's small
framing drift through draw-time registration, preserving every source pixel.
The separate composite script aligns that pivot to the foundation's (256, 256)
and rotates around it. It reads both masters and metadata beside itself and
writes the assembly previews there without cropping or repainting either layer.
Generated masters arrived
at 1254 x 1254 despite the requested 1024 square canvas. The masters have faint
alpha residue in some empty pixels; alpha is preserved, and the boundary
check rejects alpha >= 8. Visible bounds are measured at alpha >= 32.

## Source provenance and remaining family

The reference install is Starsector 0.98a-RC8 at
`C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core`.
Vanilla art belongs to Fractal Softworks. The retained Locust sprite in
`sources/` is an unmodified copy; the weapon spec has only trailing whitespace
and line endings normalized for Git. `sources/ridge-aa-base.png` preserves
the project's existing foundation used as the second edit target.

| Planned ground weapon | Vanilla weapon id | Turret sprite under `graphics/weapons/` | Art state |
| --- | --- | --- | --- |
| Locust SRM Launcher | `locust` | `locust_turret.png` | Approved elongated geometry reskinned; art review |
| Swarmer SRM Launcher | `swarmer` | `swarmer_srm_launcher_turret_base.png` | Pending |
| Proximity Charge Launcher | `phasecl` | `phase_charge_launcher_turret_base.png` | Pending |

All three installed `.wpn` definitions specify one turret sprite and an
alternative hardpoint sprite. Hardpoint and turret images are alternative
mounting views, not composited layers; none declares a separate recoil or
glow image. Ground foundations are therefore authored independently. Keep
each future launcher visually distinct, and decide its gameplay from its own
weapon behavior rather than inheriting Locust's treatment by name.

User-supplied design references:
[Locust](https://starsector.wiki.gg/wiki/Locust_SRM_Launcher),
[Swarmer](https://starsector.wiki.gg/wiki/Swarmer_SRM_Launcher), and
[Proximity Charge](https://starsector.wiki.gg/wiki/Proximity_Charge_Launcher).
The pages returned HTTP 403 during this pass; asset and layer findings above
come from the installed game files.

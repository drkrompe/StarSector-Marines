# Weapon material-ID ImageGen passes

Generated with the built-in ImageGen tool on 2026-08-31. These five images are
semantic classification aids, not shipping color or geometry. The builder keys
their magenta regions into reviewed weapon paint masks, then applies those masks
to the original weapon pixels.

## Shared prompt

Each pass used its original full-resolution source as Image 1 and the sole edit
target. The bracketed casing and protected-part descriptions came from the
table below.

> Use case: precise-object-edit
>
> Asset type: semantic material-ID pass for a top-down game weapon sprite
>
> Input images: Image 1 is the sole edit target and exact registered source asset.
>
> Primary request: recolor only [PAINTABLE CASING] to extremely saturated uniform #ff00ff magenta across every painted face, including darker shaded portions of those same painted plates. This magenta edit identifies the faction-paintable material.
>
> Style/medium: preserve the existing pixel-painted game-sprite rendering, strict zenith orthographic view, texture resolution, and lighting.
>
> Composition/framing: preserve the exact original canvas size, object registration, orientation, silhouette, outer contour, transparent padding, and every internal part boundary.
>
> Constraints: change only the RGB color of the specified paintable casing; preserve [PROTECTED PARTS] exactly in identity and placement; exactly one weapon module; genuinely transparent background; no external shadow or halo; no text; no logo; no watermark.
>
> Avoid: redesigning geometry, moving or resizing anything, changing alpha, recoloring protected hardware, adding parts, removing parts, smoothing edges, changing perspective, cropping, scenery, or checkerboard backgrounds.

| Retained pass | Original edit target | Paintable casing | Protected parts |
| --- | --- | --- | --- |
| `chaingun-arm.png` | `../chaingun-arm-v2.png` | Olive external armor, side armor, cross braces, and painted structural casing | Multi-barrel cluster, muzzles, vents, mechanisms, bolts, lamps, and outlines |
| `heavy-cannon.png` | `../heavy-cannon.png` | Olive lower housing and its painted side and face plates | Cannon barrel and collars, mechanisms, vents, bolts, lamps, warning marks, and outlines |
| `linear-cannon-variant.png` | `../linear-cannon-concept.png` | Olive housing, side supports, and casing beneath the barrels | Six barrel tubes, mechanisms, vents, bolts, copper/orange pipes and lamps, and outlines |
| `srm-pod.png` | `../srm-pod.png` | Olive outer casing, top rail, and painted front covers | Missile caps, rack grid and cavities, mechanisms, bolts, lamps, warning marks, and outlines |
| `lrm-pod.png` | `../lrm-pod.png` | Olive outer casing, top rail, center brace, and painted front covers | Missile caps, rack grid and cavities, mechanisms, bolts, lamps, warning marks, and outlines |

ImageGen returned an RGB checker preview rather than real alpha, and its SRM
canvas was one pixel wider and one pixel shorter than the source. Neither fact
enters the asset: `derive_faction_weapons.py` reads only magenta dominance,
resamples that semantic field to the original source canvas, normalizes it with
the original alpha crop, and takes all shipped pixels from the accepted weapon.

# Weapon material-ID ImageGen passes

Generated with the built-in ImageGen tool on 2026-08-31. These ten images are
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
| `shoulder-laser-cannon.png` | `../weapon-concepts/shoulder-laser-cannon.png` | Broad rear shoulder-housing plates, rear center mounting cover, and the paired olive side housings beside the barrel base | Twin long gunmetal focusing spars, recessed cyan beam guide, edge-on exit slit, capacitor indicator, collars, frame, conduits, heat sinks, vents, mounting lip, lamps, fasteners, gaps, cavities, and outlines |
| `pulse-laser-arm.png` | `../weapon-concepts/pulse-laser-arm.png` | Broad rear mounting housing and center armor plate, paired olive side housings beside the focusing rails, olive plates around the capacitor banks, and rear mounting tab | Twin gunmetal focusing rails, recessed cyan optical path, edge-on exit slit, capacitor indicators, frames, conduits, vents, fasteners, lamps, mechanisms, gaps, cavities, and outlines |
| `hegemony-bastion-autocannon.png` | `../weapon-concepts/hegemony-bastion-autocannon.png` | Every olive-drab external armor casing plate, including its small painted service marks | Black barrel and shroud, muzzle slit, recoil rails and cylinders, ammunition belt and feed, hoses, vents, fasteners, gaps, cavities, outlines, and bare metal |
| `pather-demolition-cannon.png` | `../weapon-concepts/pather-demolition-cannon.png` | Olive and rust-red external plates, welded armor braces, access hatch, and painted rear mount casing | Oversized barrel and muzzle slit, weld beads, recoil ram, pressure-cylinder hardware, hoses, pipes, clamps, bolts, joints, gaps, cavities, outlines, and bare steel |
| `lions-guard-thermal-lance.png` | `../weapon-concepts/lions-guard-thermal-lance.png` | Deep-red lacquer plates, cream identification chevrons, and gold/brass external trim plates | Dark emitter body, edge-on amber emitter seam, cooling fins, heat exchangers, lamps and glowing cells, pipes, vents, fasteners, gaps, cavities, outlines, gunmetal mechanisms, and mounting socket |

ImageGen returned RGB checker previews rather than real alpha. Its SRM canvas
was one pixel wider and one pixel shorter than the source; the redesigned laser
pass is `1054x1492` against a `1058x1487` source. Neither fact enters an asset:
`derive_faction_weapons.py` reads only magenta dominance, resamples that semantic
field to the original source canvas, normalizes it with the original alpha crop,
and takes all shipped pixels from the accepted weapon.
The laser-cannon material-ID pass was regenerated after the in-plane barrel
redesign. Its prompt explicitly protected both focusing spars and the narrow
cyan optical path while selecting the olive shoulder housing. After resampling
the semantic field to the source canvas, it classifies 31.3% of the opaque
source as paintable casing. Registration waited until the weapon's runtime
name, shoulder layout, and accepted canvas could be implemented together. That
registration is now complete:
the builder emits `shoulder-laser-cannon.png` at `76x128` plus every faction
casing variant from this retained semantic pass.

The three faction-signature passes used the same classification-only handoff.
The Bastion and demolition passes returned aligned checker previews. The
thermal-lance pass added a non-authoritative magenta halo; it is harmless
because the builder resamples the semantic field to the accepted source crop
and multiplies it by the normalized base sprite's exact alpha before measuring
or applying paint. All three passed the reviewed 20–82% casing-coverage guard.

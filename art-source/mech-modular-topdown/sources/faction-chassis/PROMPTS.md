# Faction chassis ImageGen source prompts

Generated with the built-in ImageGen tool on 2026-08-31. Each of the ten
human visual families has a Bulwark, Hound, and Sirocco master. Remnants are
deliberately absent: `remnants-lore-guide.md` calls for purpose-built machine
ecologies, not human chassis painted cyan.

## Inputs and output mapping

Image 1 was always the sole edit target:

| Chassis | Image 1 source | Retained filename | Runtime filename |
| --- | --- | --- | --- |
| Bulwark | `../hull-clean-v2.png` | `bulwark.png` | `chassis.png` |
| Hound | `../sirocco-hull.png` | `hound.png` | `chassis-hound.png` |
| Sirocco | `../hound-hull.png` | `sirocco.png` | `chassis-sirocco.png` |

The Hound and Sirocco source-name reversal is intentional and matches
`build_assets.py`: those accepted generated silhouettes acquired their gameplay
names after being flipped north/up.

Image 2 was a style/material reference only:

| Family | Image 2 reference |
| --- | --- |
| Hegemony | `../../../marine-modular-topdown/sources/army-green-body.png` |
| Tri-Tachyon | `../../../marine-modular-topdown/sources/faction-armor/specter-heavy/body.png` |
| Persean League | `../../../marine-modular-topdown/sources/faction-armor/bulwark-heavy/body.png` |
| Luddic Church | `../../../marine-modular-topdown/sources/faction-armor/reliquary-heavy/body.png` |
| Knights of Ludd | `../../../marine-modular-topdown/sources/faction-armor/reliquary-heavy/body.png` |
| Luddic Path | `../../../marine-modular-topdown/sources/faction-armor/foundry-breaker/body.png` |
| Sindrian Diktat | `../../../marine-modular-topdown/sources/faction-armor/furnace-line/body.png` |
| Lion's Guard | `../../../marine-modular-topdown/sources/faction-armor/lions-mantle/body.png` |
| Pirates | `../../../marine-modular-topdown/sources/faction-armor/reaver/body.png` |
| Independent / mercenary | `../../../marine-modular-topdown/sources/charcoal-body.png` |

## Shared prompt

The following scaffold was used once per chassis and faction, with the bracketed
values replaced by the chassis role and faction treatment below.

> Use case: precise-object-edit
>
> Asset type: high-resolution faction mech chassis master for a top-down game sprite
>
> Input images: Image 1 is the sole edit target and exact [CHASSIS] chassis geometry; Image 2 is the accepted [FACTION] material and rendering-style reference only.
>
> Primary request: reskin only Image 1 as [FACTION TREATMENT] [CHASSIS], [ROLE NOTE]. Preserve the exact chassis model. [SURFACE DIRECTION]
>
> Style/medium: preserve Image 1's crisp painted game-sprite rendering, texture density, lighting direction, and strict 90-degree zenith orthographic view.
>
> Composition/framing: preserve Image 1's exact canvas registration, centered north/up orientation, scale, silhouette, outer contour, hardpoint openings, panel boundaries, vents, joints, and transparent padding.
>
> Constraints: change only paint, surface materials, tiny non-readable markings, wear, and minor plate-face detailing; keep every mechanical component and all geometry unchanged; exactly one isolated chassis; genuinely transparent background with clean alpha; no shadow or halo outside; no text; no logo; no watermark.
>
> Avoid: redesigning the chassis, changing proportions, adding weapons or parts, cropping, rotation, perspective, isometric view, extra objects, scenery, caricature props, or excessive glow.

Role notes were `the durable all-band anchor`, `the quick close-assault
long-spine chassis`, and `the fragile broad-wedge long-range support chassis`.

## Faction treatments

- **Hegemony:** standardized olive-drab slab plates, dark gunmetal structure,
  replaceable sections, restrained orange chevrons, off-white service bars,
  visible bolts, repair seams, and disciplined wear. Domain-descended military
  hardware, not primitive equipment.
- **Tri-Tachyon:** near-black graphite, deep desaturated midnight composite,
  cool titanium edges, flush sensor apertures, fine neural/HUD traces,
  restrained cyan insets, tiny white datum marks, tight proprietary seams, and
  controlled wear.
- **Persean League:** dark slate and muted navy-gray laminate, gunmetal, dull
  tan replaceable edges, faded-teal maintenance tags, standardized locking
  rails, actuator access marks, redundant connectors, and three abstract bars.
- **Luddic Church:** weathered ivory ceramic, sage and deep-olive structure,
  antique iron, aged brass, hand-fitted panels, repaired seams, faded-ochre
  seals, and a small abstract sunburst. Old and dignified through stewardship.
- **Knights of Ludd:** antique iron and deep olive dominant, selected ivory
  faces, brass locks, hardened access plates, oxblood oath stripes, campaign
  repairs, and restrained vow marks. Professional custody, not Pather or
  fantasy imagery.
- **Luddic Path:** soot black, oxidized brown, faded industrial orange, bare
  steel, old hazard patches, weld seams, bolted repairs, stripped ownership,
  and tiny scavenged cyan lamps. Technically competent conversion work with no
  universal martyr hardware.
- **Sindrian Diktat:** furnace-dark crimson and burgundy laminate, soot-black
  frame, oily gunmetal, heat-stained copper, amber warnings, rigid bilateral
  marks, rationed maintenance, and one abstract state/rank plate.
- **Lion's Guard:** polished deep crimson laminate, blackened gunmetal,
  heat-stained copper, brass edge guards, amber lamps, meticulous alignment,
  controlled wear, and abstract golden mane chevrons. Prestige rather than a
  brighter ordinary-Diktat recolor.
- **Pirates:** coherent mixed provenance in oxidized brown, brick red, soot
  black, bare steel, mustard replacement panels, repaired cells, welds,
  patched seals, stripped ownership, and scavenged cyan indicators.
- **Independent / mercenary:** one established company's charcoal and dusty
  blue-gray service plates, gunmetal, field-green or tan replacements,
  off-white bars, faded-teal tags, orange hazard points, and disciplined wear.
  This is a practical fallback, not a universal Independent culture.

Most built-in outputs painted their transparency preview into RGB. Run
`clean_imagegen_backgrounds.py` after replacing a master; it removes only the
edge-connected neutral-light field and border debris. `build_assets.py` refuses
opaque masters, normalizes each result, and applies the accepted base chassis's
exact alpha mask so faction paint cannot change gameplay silhouette.

# Modular mech raster authoring

This directory holds the retained sources and deterministic tools that produce
the modular mech sprites under `mod/graphics/battle/mech-modular-topdown/`.
Generated concept art and semantic material-ID images stay here; only normalized
runtime layers and faction variants ship in `mod/`.

## Weapon flow

1. Generate or edit a high-resolution neutral-base weapon concept under
   `sources/`. Lock the intended battlefield direction before refining color or
   small detail.
2. Inspect the concept as a silhouette and at approximate gameplay scale. Reject
   a direct-fire weapon that reads as firing toward the camera, even if its
   rendering is otherwise attractive.
3. Retain the accepted prompt beside the source. The current weapon concept log
   is `sources/weapon-concepts/PROMPTS.md`.
4. Give ImageGen the accepted source as the sole edit target for a semantic
   material-ID pass. Ask it to recolor only faction-paintable casing to uniform
   high-chroma magenta and preserve every functional part.
5. Clean a baked checker to real alpha with `clean_imagegen_backgrounds.py`.
   ImageGen may change the canvas; the semantic field is resampled to the source
   rather than becoming a new geometry authority.
6. Register an accepted runtime weapon, output size, and material-ID image
   together in `derive_faction_weapons.py`. Until then, a retained concept is
   deliberately art-only.
7. Run `build_assets.py`, then regenerate both faction contact sheets with
   `render_faction_variants.py` and `render_faction_weapon_variants.py`.
8. Verify exact alpha equality against the base runtime weapon and inspect the
   contact sheets at gameplay scale. All shipped pixels, lighting, noise, wear,
   and protected hardware come from the base weapon, never from the magenta pass.

## Prompting a horizontal barrel from a zenith camera

`top-down`, `orthographic`, and `north-facing` are not enough by themselves.
ImageGen often interprets a visible glowing aperture as a barrel pointing upward
toward the camera. The prompt must describe the camera/weapon relationship and
what that relationship makes physically invisible.

Use this block for a direct-fire barrel that lies in the battlefield plane:

> Critical perspective: strict 90-degree zenith orthographic top-down view. The
> camera looks straight down at the TOP surfaces of the weapon. The weapon fires
> horizontally toward the NORTH/TOP edge of the canvas, parallel to the
> battlefield plane. The muzzle faces AWAY from the camera, so the camera cannot
> see a circular bore, aperture face, dish, lens, or glowing front surface. At
> the extreme northmost tip show only a very thin, almost edge-on horizontal exit
> slit between two long north-south barrel or focusing rails.

For an energy weapon, add:

> Keep the optical path narrow, recessed, and mostly dark. Do not use a broad
> glowing window on the top face. Carry the horizontal firing axis through the
> silhouette with long parallel focusing rails around the optical path.

The second paragraph matters. In the rejected shoulder-laser revision the bore
was hidden, but a broad cyan channel remained on the visible top surface. It
still read as a vertical emitter or reactor. The accepted version moved the read
into two long rails, a narrow recessed optical path, and a nearly invisible
edge-on muzzle line.

### Diagnose the image before iterating

| Read in the generated image | Prompt correction |
| --- | --- |
| Circular or oval aperture is visible | State that the muzzle faces away and its bore/front face is physically invisible to the camera. |
| Bright cyan plate or window faces the camera | Replace it with a narrow recessed seam; explicitly forbid a broad emissive top surface. |
| Barrel looks foreshortened or points upward | Require two long parallel rails running north-south in the image plane and constant-width top surfaces. |
| Weapon reads as a reactor or engine | Put the capacitor housing at the rear and make the forward half an unmistakable barrel assembly. |
| Weapon becomes a rifle or arm cannon | State the shoulder footprint, the rear mounting cover, and the desired barrel-to-housing proportions. |
| A reference donates the wrong geometry | Label every input by role and say what must not be copied from it. |

Make one targeted correction per iteration and repeat the critical perspective
and invariants every time. Do not rely on the previous prompt remaining implied.

## Reference-image roles

Assign one job to each input in the prompt. For the shoulder laser cannon:

- The rejected laser pod was the edit target and material-language reference.
- The shipped linear cannon was a perspective and visual-scale reference only;
  its six-barrel geometry was explicitly forbidden.
- The LRM pod was a shoulder-footprint and rear-mount reference only; missile
  cells and rockets were explicitly forbidden.

This separation lets a reference answer one useful question without silently
becoming the design. Use the same pattern for future concepts: `edit target`,
`perspective reference`, `footprint reference`, or `material/style reference`,
followed by a short list of geometry that reference must not donate.

## Material-ID prompt handoff

Once the base geometry is accepted, stop asking ImageGen to improve the weapon.
The next pass is classification only:

> Recolor ONLY the faction-paintable external armor casing to extremely
> saturated uniform `#ff00ff` magenta, including shaded faces of those same
> painted plates. Preserve the exact geometry, registration, alpha, perspective,
> lighting, and protected hardware. Do not recolor barrels, optical paths,
> mechanisms, vents, fasteners, ordnance, lamps, gaps, cavities, or outlines.

Name the casing and protected parts for the specific weapon. Preserve the raw
semantic pass and its prompt under `sources/weapon-material-id/`; it is evidence
for the mask, not a source of shipping color or pixels.

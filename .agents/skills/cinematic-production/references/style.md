# Approved style and reference authority

The user approved the Jangala realism proofs on 2026-10-15. The target is
grounded cinematic realism consistent with the campaign stills. A painted plate
is an authoring technique; visible broad brushwork is not the desired finish.
Grit comes from physical materials, use and repair, while Jangala remains lush,
bright and readable. Do not turn realism into a uniformly dark or desaturated look.

## Visual target

- **Land:** irregular canopy crowns, species and height variation, glimpses of
  branching, fine foliage scaled to altitude, natural banks and clearings. Reduce
  fine contrast with distance; avoid rounded repeated tree clumps and equally
  sharp detail from foreground to horizon.
- **Air and water:** volumetric clouds with luminous daylight edges, selective
  humidity in hollows, sky-reflecting water and plausible surface ripples. Avoid
  evenly spaced white mist ribbons. The approved proofs still allow refinement
  of their regular mist and crisp distant canopy.
- **Industry:** heavy, assembled structures with plausible load paths, fasteners,
  welds, seals, service access and differentiated materials. Wear follows use:
  exposed-edge chips, water/grime paths, heat staining and repaired panels.
- **Light and color:** convincing sun/sky relationship, open shadows, readable
  midtones and varied greens. Match direction and exposure across plates and
  geometry. Avoid blanket teal/brown grading, glossy toy surfaces and procedural
  detail added everywhere at equal strength.
- **Composition:** preserve the approved geography, scale cues and shot purpose.
  Clouds and distant industrial traffic make this a lived-in science-fiction
  world. Their apparent size, altitude and speed must agree within the shot.

## Reference roles

These links resolve from this file into the same repository checkout.

| Reference | Authority and limits |
|---|---|
| [Window realism master](../../../../art-source/company-prologue/second-wind-cinematic/realism-lookdev/window-realism-v1.png) | Approved daylight, foliage and worn cabin look in context. |
| [Landscape realism master](../../../../art-source/company-prologue/second-wind-cinematic/realism-lookdev/landscape-realism-v1.png) | Matching wide environment style; input for new layer extraction. |
| [Look-development provenance](../../../../art-source/company-prologue/second-wind-cinematic/realism-lookdev/README.md) | Exact input roles, prompts and dimensions; both raw masters are 1672×941. The 1080p review copies are resampled. |
| [Cargo billets](../../../../art-source/company-prologue/cargo-billets.master.png) and [yard hull](../../../../art-source/company-prologue/yard-hull.master.png) | Optional campaign material/lighting exemplars. Their older ship geometry has no authority over the accepted V3 model. |
| [Parallax study](../../../../art-source/company-prologue/second-wind-cinematic/parallax-window/README.md) | Accepted motion technique; the retained movie has the earlier, more painted surface style. |

The two realism masters preserve broad composition, not pixel registration.
Treelines, riverbanks, clouds and small window details changed during generation.
Re-extract layers and validate their edges; do not drop a full window image onto
the moving scenery or assume it aligns with earlier masks. The accepted Blender
camera/model owns spatial relationships. See [dynamic objects](dynamic-objects.md)
for ship geometry and damage references.

## Prompt construction

Use the inspected guide first, followed by only the relevant style/state
references. Adapt this pattern to the current asset:

> Image 1 controls camera, silhouette, apertures and layout. Images 2–3 provide
> material/lighting style only. Apply grounded cinematic science-fiction realism:
> [specific materials, wear and vegetation]. Keep [listed hardpoints/landmarks]
> fixed. Preserve [required alpha canvas or layer registration]. Match [sun
> direction, exposure, depth haze]. Deliver [one named layer or guided surface].

For extraction, specify which foreground elements to remove, which scenery to
complete behind them, and the full-motion overscan required. Preserve the source
canvas and registration where requested; inspect the actual result because the
prompt alone cannot guarantee either. For reskinning, request material changes
without inviting a new ship design. Retain the exact submitted prompt.

## Jangala continuity

The surface yard is an authored location, distinct from orbital Jangala Station.
Read the storyboard's retained lore sources before adding geography or exposure
claims; its chosen river, city layout and weather break are visual authorship.
The journey runs from atmosphere/cloud break through forest, managed land and
port to a level cradle. A closed crew viewport provides the first perspective.
Keep the approach heading, window side, light and background flow consistent.

Arrival precedes Nessa's inspection and the limited berthing conversion. The
passengers include miners, cargo hands and families; Sabine's twelve are a
distinct escort section. They have reached help, not a restored ship. People
working in vacuum need sealed rugged industrial suits; boarding/loading belongs
in a sheltered dock or controlled access area appropriate to the environment.

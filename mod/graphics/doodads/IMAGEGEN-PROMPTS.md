# ImageGen prompt recipe

The initial doodad atlas used the built-in ImageGen workflow. Every generated
asset used the existing `urban-tileset-imagegen.png` as the primary rendering
reference and the modular `army-green/body.png` as a scale and pixel-density
reference.

Shared prompt contract:

```text
Use case: stylized-concept
Asset type: single 32x32-ready top-down tactical game doodad sprite
Scene/backdrop: perfectly flat solid #ff00ff chroma-key background for removal
Style/medium: pixelated-realistic strict top-down game sprite, crisp clustered
pixels, restrained detail legible after reduction to 32x32, matching the
existing Starsector Marines assets
Composition/framing: orthographic 90-degree top-down, centered, generous
padding, full object visible
Lighting: subtle overhead upper-left light, object-local shading only
Constraints: exactly one isolated asset; uniform background; no floor; no cast
shadow; no text, logo, or watermark; no #ff00ff in the subject
Avoid: isometric perspective, scenery, people, photoreal photo, vector art
```

The subject line was varied for:

- L-shaped olive/tan two-bag-thick sandbag corner connecting east and south
- straight horizontal olive/tan two-bag-thick sandbag wall
- three reinforced gunmetal/olive cargo crates
- three weathered chemical drums
- gunmetal power-cable reel
- olive portable field generator
- closed blue-gray industrial dumpster
- banded bundle of gunmetal pipes
- stack of empty composite cargo pallets
- compact pile of damaged panels, rebar, machinery plates, and a gear

The corner and straight outputs are preserved in `imagegen-masters/` and turned
into the eight exact ring frames by `derive_sandbag_frames.py`. Raw chroma-key
outputs are preserved under `imagegen-raw/`; cleaned production cutouts live in
`sources/`.

## Military-compound additions

The military set was generated with the built-in ImageGen tool in
`stylized-concept` mode. These outputs supplied clean alpha directly, so the
untouched RGBA files are preserved under `imagegen-raw/` and copied to
`sources/` without chroma-key cleanup. The atlas builder still crops and
normalizes them to the 32x32 runtime grid.

Shared suffix used for all four prompts:

```text
Scene/backdrop: genuinely transparent background with clean alpha.
Style/medium: pixelated-realistic strict top-down game sprite, crisp clustered
pixels, restrained detail that survives reduction to 32x32, matching a gritty
blue-gray and olive sci-fi military tileset.
Composition: orthographic 90-degree top-down, centered, generous even padding,
full object visible.
Lighting: subtle overhead upper-left object-local highlights only.
Constraints: exactly one isolated asset; no floor, base tile, cast shadow,
people, text, logo, watermark, scenery, or magenta; no isometric or perspective
view.
```

Per-asset primary requests:

- `military-radar-dish.png`: one compact military radar dish on a squat armored
  rotating pedestal, olive drab and dark gunmetal, with the concave dish and
  support yoke unmistakably legible from directly above; chipped armor panels,
  dark pivots, tiny amber status lamps, compact field-deployable silhouette.
- `military-command-console.png`: one compact military command-and-control
  computer console station, a low angular operator desk with three embedded
  cyan-blue tactical screens, olive armor and gunmetal housing; console only,
  no chair and no operator.
- `military-tactical-table.png`: one square tactical planning table, a recessed
  cyan-and-amber illuminated battlefield map display inside a reinforced olive
  and gunmetal rim, clearly distinct from an operator console; simple grid and
  unit markers with no readable text.
- `military-bunk.png`: one compact military barracks bunk or field cot seen
  directly from above, dark metal frame, neatly rolled olive blanket, muted tan
  pillow, and a small folded kit at the foot; long axis vertical and no person.

## Civic-headquarters additions

The civic office set also used the built-in ImageGen tool in
`stylized-concept` mode with genuinely transparent backgrounds. Untouched RGBA
outputs are preserved under `imagegen-raw/` and copied to `sources/`; the atlas
builder performs the only crop/scale operation needed for runtime use.

Shared rendering contract:

```text
Asset type: a single 32x32-ready top-down tactical-game doodad sprite.
Scene/backdrop: genuinely transparent background with clean alpha.
Style/medium: pixelated-realistic strict top-down game sprite, crisp clustered
pixels, restrained detail that survives reduction to 32x32, matching a gritty
blue-gray sci-fi urban tileset and the existing doodad atlas.
Composition: orthographic 90-degree top-down, centered, generous even padding,
full object visible.
Lighting: subtle overhead upper-left object-local highlights only.
Constraints: exactly one isolated asset; no floor, base tile, cast shadow,
people, readable text, logo, watermark, scenery, or magenta; no isometric or
perspective view.
```

Per-asset primary requests:

- `office-workstation-bank.png`: one compact civilian workstation bank, a low
  L-shaped blue-gray desk with slim cyan terminal, keyboard, paperwork tray,
  short muted-teal cubicle panel, gunmetal trim, and small amber indicators;
  no chair or person.
- `office-server-rack.png`: one compact civilian data-center cabinet, a tall
  rectangular blue-gray/gunmetal rack with vented top panels, cable ports, and
  cyan/amber status-light rows; opaque and dense at tiny game scale.
- `office-conference-table.png`: one compact oval or softly rounded rectangular
  blue-gray civic conference table with an inset cyan presentation/map screen,
  tidy document pads, dark gunmetal trim, and subtle amber status lights; long
  axis horizontal, no chairs or people.

## Industrial-facility additions

The large-factory set used the built-in ImageGen tool in `stylized-concept`
mode. The generated PNGs contain genuine alpha; untouched RGBA outputs are
preserved under `imagegen-raw/` and copied to `sources/` for atlas normalization.

Shared rendering contract:

```text
Asset type: a single 32x32-ready top-down tactical-game doodad sprite.
Scene/backdrop: genuinely transparent background with clean alpha.
Style/medium: pixelated-realistic strict top-down game sprite, crisp clustered
pixels, restrained detail that survives reduction to 32x32, matching a gritty
blue-gray, olive, and hazard-yellow sci-fi urban tileset and the existing
industrial doodad atlas.
Composition: orthographic 90-degree top-down, centered, generous even padding,
full object visible.
Lighting: subtle overhead upper-left object-local highlights only.
Constraints: exactly one isolated asset; no floor, base tile, cast shadow,
people, readable text, logo, watermark, scenery, or magenta; no isometric or
perspective view.
```

Per-asset primary requests:

- `industrial-machine-tool.png`: one compact heavy industrial machine tool, a
  squat rectangular gunmetal and hazard-yellow automated press/CNC cell with a
  dense central work head, side motor housings, guarded feed bed, small cyan
  status screen, and amber warning lights; long axis horizontal, no operator.
- `industrial-fluid-tank.png`: one compact tall industrial pressure vessel or
  fluid tank, a large circular blue-gray steel tank with reinforced rim,
  central inspection cap, valve manifold, short pipe stubs, hazard-yellow
  bands, and tiny cyan/amber gauges; dense, solid, and opaque.
- `industrial-control-console.png`: one compact civilian industrial control
  console, a low angular blue-gray and gunmetal operator panel with three cyan
  process screens, amber emergency indicators, chunky switches, cable ports,
  and hazard-yellow edge rails; no chair or operator.

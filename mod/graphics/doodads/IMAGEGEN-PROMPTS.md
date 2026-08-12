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

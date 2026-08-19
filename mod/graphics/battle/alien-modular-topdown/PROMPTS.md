# Built-in ImageGen prompts

The original three sources use the existing `alien.png` sheet as the identity/style
reference and the corresponding accepted marine layer as the geometry and
registration reference. Each request requires one strict 90-degree zenith,
north-up object on a genuinely transparent background, with no perspective,
floor, shadow, text, sprite sheet, extra objects, logo, or watermark.

- **Body:** a headless dark charcoal-purple chitinous shoulder/upper-body mass
  with restrained amber nodes and a clean centered oval head socket; no head,
  weapon, hands, feet, legs, or tail.
- **Head:** an isolated elongated ridged alien skull crown with a pointed
  north-facing brow and subtle amber sensory details; no shoulders, torso,
  collar, arms, weapon, legs, or tail.
- **Foot:** one compact digitigrade alien foot with three forward talon tips,
  dark segmented chitin, and restrained bone-gray claw edges; no leg above the
  ankle or other body parts.

The fore-claw source was generated with the built-in ImageGen tool from
`alien.png`, the accepted `body.png`, and the accepted `foot.png` using this
prompt:

> Use case: stylized-concept. Asset type: modular top-down game-character melee
> forearm/claw layer. Input images: Image 1 is the original alien
> identity/anatomy reference; Image 2 is the accepted modular xeno torso
> color/material/style reference; Image 3 is the accepted small-scale xeno
> appendage rendering reference. Primary request: create exactly one isolated,
> self-contained alien forearm and swiping claw appendage for the existing
> modular creature. Subject: a compact segmented chitinous forearm that begins
> at a narrow shoulder socket at the south/base and extends north into a broad
> predatory hand with three long, clearly separated hooked talons; the claws
> must project conspicuously beyond the forearm so they remain readable at
> strategy-game scale. Style/medium: crisp painted 2D strategy-game sprite
> matching the accepted xeno layers; dark charcoal-purple chitin, organic
> gunmetal ridges, restrained bone-gray talon edges, tiny warm amber joint
> detail only. Composition/framing: one complete appendage centered vertically,
> strict 90-degree zenith orthographic top-down view, shoulder/base pointing
> south and claw tips pointing north, neutral axial geometry suitable for reuse
> as either arm by runtime rotation, generous transparent padding. Constraints:
> genuinely transparent background; one forearm/claw only; no torso, head,
> second arm, foot, leg, tail, weapon, blood, attack trail, perspective, floor,
> cast shadow, glow spill, text, border, sprite sheet, extra objects, logo, or
> watermark. Preserve a clean narrow base pivot and an unmistakable three-talon
> silhouette.

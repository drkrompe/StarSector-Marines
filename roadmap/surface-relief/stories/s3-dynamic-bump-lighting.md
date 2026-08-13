# S3 — Dynamic bump lighting (design sketch)

Light the composed ground from battle events — muzzle flashes, explosions,
fires — using the S1 normal sheets. Per the Welsh paper, composes with S2:
compute the parallax-offset coordinate first, then sample the normal map at
the offset coordinate, then N·L against each light.

Sketch (firm up when picked up):

- A **normal target** joins the S2 FBO pair (ground normals composed in
  screen space, same pass structure as the height target).
- Light sources: small fixed-size uniform array (nearest N lights),
  fed from existing FX events; falloff + color per light.
- The S2 fullscreen pass grows the lighting math (it already has the
  offset coordinate in hand).
- Ambient term keeps the current look as the floor; lights add on top —
  the effect should be invisible when nothing is firing.

Blocked on: S1 (normal sheets), S2 (screen-space pass to host it).

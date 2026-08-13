# S4 — Unit relief: soldiers / vehicles / agents (stretch, design sketch)

Extend the derived-maps approach to unit sprites, so soldiers, vehicles,
and mechs pick up the same dynamic lighting as the ground (S3): a mech lit
from the side by an explosion, muzzle-flash glint on armor.

Notes for when this is picked up:

- **Lighting, not parallax** — parallax is a ground-plane effect; sprites
  are small, above the ground layer, and drawn post-parallax. Units get
  the N·L half only.
- **Derivation** — same kernel, run over unit sprite sheets /
  animation frames. Sprite sheets are frame grids like tile sheets, so the
  S1 atlas-aware wrapper applies as-is; polarity/strength recipes likely
  differ per sheet (painted highlights on units bias luminance).
- **Rotation** — units rotate; tangent-space normals must rotate with the
  sprite (rotate the sampled normal's xy by the sprite's heading in the
  shader). This is the substantive new problem vs. terrain — terrain never
  rotates.
- **Render path** — unit quads draw through their own batched pass; this
  needs a per-pass shader (sample albedo + normal, apply light array), not
  the fullscreen composite. Alpha/transparency needs care at sprite edges.

Blocked on: S1 (kernel vendored), S3 (light-source plumbing).

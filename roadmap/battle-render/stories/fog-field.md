# Fog is a field, not a command stream

Status: PLANNED — owner direction on 2026-09-02: a 13 ms frame on the dev
machine is not a budget met, because the users' machines are not this one.

Written: 2026-09-02

## What the measurement says

With the ground and the relief fields resident, a whole-map 560x336 Conquest
frame costs about 13 ms of our own time, and FOG is 5.7 of it. The layer is
collection-bound in the sharpest form there is: 168,339 commands, one per cell
the player cannot fully see, drained in a single draw call. Nothing is wrong
with the drain; the cost is walking every visible cell every frame and
emitting a quad for each, and it scales with the map exactly as the ground
did before it became resident. The render noun doc records it as the next
ceiling and guesses that residency is not the answer because fog changes every
tick. That guess is what this story disproves.

## The model

**Fog is a scalar per cell, and a scalar per cell is a texture.** The player's
vision state is already a map-sized bitmap with reference counts, owned by the
vision service; what the FOG layer draws is a function of that state — unseen,
explored, seen — per cell. So the layer keeps one map-sized single-channel
texture, one texel per cell, and draws the whole map as **one quad** that
samples it, with the fog colour and the per-state opacity applied at draw
time. Bilinear sampling gives the soft edge between seen and unseen for free,
where today it costs a quad per cell.

**The upload is the delta, and the delta is already known.** The vision
service knows which cells changed this tick — that is what its reference
counts are for — so the field is patched with a sub-image upload of the
changed rows' bounding rectangle rather than rewritten. A whole-map rewrite is
188 KB and stays as the fallback and the control; on most ticks the patch is a
few rows.

**What fog gates is untouched.** The unit-visibility gate, the building
visibility pass and every consumer that asks "can the player see this cell"
keep reading the vision service. Only the picture changes how it is drawn.

**It is a resident thing, so it obeys the residency laws.** A host that
declines residency, or a GL failure at any point, returns the layer to the
per-cell path with the same picture; the field can only read what the vision
service reports; and painter order is unchanged — FOG still draws where FOG
draws.

## Alternatives, and why not

- A resident fog *mesh* with per-vertex alpha patched from the same deltas
  works too, but a texture is a smaller upload, a simpler patch, and gives
  the edge softening the mesh would have to model with extra vertices.
- Gating fog by framing is wrong: fog is the one layer that must read at every
  framing, since it is what the player's picture *is*.

## Acceptance

- `renderEvidence` whole-map 560x336 Conquest: FOG under 1 ms with the field
  on, against its own control run (`battle.render.fogField=false`) in the
  same report; close and mid not worse.
- The field draws pixel-equal to the per-cell path for a fully unseen map, a
  fully seen map and a mixed one, on `HeadlessGl` under `shaderEvidence`'s
  skip-when-no-driver rule, after a tick that changes vision has been patched
  through the delta path. If bilinear edges are adopted, the comparison is
  made with sampling set to nearest and the softened edge is looked at and
  accepted deliberately in the `perception-sweep` snapshot suite.
- `createSnapshots -Psnapshot=perception-sweep` byte-identical at its authored
  zooms, or the difference is the softened edge and is accepted.
- `verifyModJarBoundary`, full `:test`, `shaderEvidence` green.

## Plan

1. The vision service exposes its per-tick changed-cell extent (or the field
   derives it from the reference-count bitmap's dirty rows); unit tests on the
   extent derivation, GL-free.
2. `FogField`: the texture, the sub-image patch, the one-quad draw under the
   FBO/texture bracket rules; the FOG collector emits the field when residency
   is allowed and the per-cell stream otherwise.
3. Measure both ways; fold into `battle-render-nouns.md` (residency now covers
   fog; the "next ceiling" paragraph is replaced by whatever the profile names
   after this) and `fog-of-war`'s noun doc if the changed-extent seam is new
   vocabulary there.

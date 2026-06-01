# Perf spike — FBO-cache the static ground layer

> **Status: design-stage / not started.** Follow-up to
> [`perf-quadbatch-flush.md`](perf-quadbatch-flush.md). The bigger of the two
> render levers (work-reduction, not submission-mechanism).

## Premise

The flush rewrite attacked *per-quad submission cost*. This attacks *quad
count*. GROUND walks all ~38k cells every frame and emits one
`DrawCommand`/quad each, even though the terrain is **static geometry** — only
the camera transform changes frame to frame. Re-emitting + re-submitting the
whole ground every frame is wasted work.

**Idea:** render the ground layer once into an FBO (in world space, or at a
fixed zoom), then each frame draw a *single* textured quad with the current
camera transform. GROUND drops from ~38k quads/frame to **1**. Potential
order-of-magnitude win — far larger than any submission-mechanism choice — but
a bigger architectural change that cuts against the "everything is a per-frame
command" draw-list model.

## Open questions (the spike)

- **Invalidation.** What dirties the cache? Map load, destructible-terrain
  edits (rubble?), day/night lighting if it bakes into ground, fog (no — fog is
  a separate overlay layer). Need a cheap dirty flag + re-bake trigger.
- **Resolution / zoom.** Bake at world resolution (large texture, crisp at all
  zooms but VRAM-heavy on 100×100 maps) vs. at a reference zoom (cheaper,
  resamples on zoom). What's the map-size ceiling and VRAM budget?
- **Fit with the draw-list model.** GROUND becomes a `Custom` command (own-GL
  FBO blit) or a dedicated command. The other layers (decals, units, fog) still
  paint per-frame on top — confirm paint order survives.
- **Fog / decal interaction.** Decals already accumulate into an FBO
  (`DecalAccumulator`); craters draw on the ground. Does the ground bake
  include decals (then decal writes must also dirty it) or stay separate
  (decals composite after the ground blit)? Likely the latter.
- **Camera transform.** Blitting one quad under pan/zoom needs the same
  `BattleCamera` mapping the per-tile path uses — verify no seam/filtering
  artifacts at cell boundaries (NEAREST vs bilinear; cf. pixel-art uniform-cell
  memory).

## Why deferred behind the flush fix

The flush rewrite is low-risk, in-place, and resolves the confirmed dominant
leaf today. This spike is higher-effort and design-heavy (cache invalidation is
the hard part). Open it as its own story once the flush win is measured — its
payoff depends on what fraction of render CPU GROUND still represents after the
submission cost is gone.

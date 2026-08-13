# surface-relief — next-session handoff

## State of play (2026-08-13)

- Feature docs created (overview + S1–S4). S1 (derivation pipeline) and
  S2 (screen-space parallax) are the active implementation slice,
  orchestrated in a worktree; S3 (bump lighting) and S4 (unit relief) are
  design sketches.
- Kernel source of truth:
  `MoonLightEngine/asset-pipeline/.../tools/assets/terrain/TerrainMaterialDerivationKernel.java`
  (NOT yet vendored — the mod repo's asset-pipeline only carries
  mesh/animation/material code).

## Decisions locked

- Screen-space composite, not per-quad texcoord offsets (atlas bleed).
- Offset-limited form only; fake-perspective eye from screen center;
  max offset ~2–4 px.
- Macro (tile-id metadata) × micro (derived sheet) height composition.
- Derivation is build-time in `:asset-pipeline` tool source set; per-cell
  clamp addressing; per-sheet percentile window.
- Hard off-toggle; shader failure degrades to plain rendering.

## Next up

1. S1 + S2 implementation (in flight — see worktree/orchestration state).
2. Playtest the aesthetic question: does relief read well on pixel art?
   Water is the first tuning target.
3. Then S3; S4 after light plumbing exists.

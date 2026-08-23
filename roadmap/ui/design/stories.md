# UI foundation open work

Status: ACTIVE — four open stories
Written: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` before changing a UI-foundation story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `u1-retained-layout-workbench.md` | IN PROGRESS — live acceptance | Java tree, host adapter, row/column/stack boxes, paint/hit parity, and reachable preview are implemented and headless-tested. |
| `u2-clipping-scroll-and-focus.md` | IN PROGRESS — live acceptance | Clipping, retained scrolling, semantic keys/focus, explicit capture, and procedural canvas input are implemented and headless-tested in the reachable workbench. |
| `u3-theme-and-transitions.md` | IN PROGRESS — live acceptance | Closed CSS-named cascade, responsive lengths, scoped component rules, replaceable themes, semantic states, and retained transitions are headless-tested in the workbench. |
| `u4-mlx-components-and-bindings.md` | Planned | Depends on the authoritative Java tree and theme surface; adds scoped components, signals, keyed rows, and explicit hot reload. |

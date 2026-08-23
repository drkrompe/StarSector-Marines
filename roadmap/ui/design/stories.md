# UI foundation open work

Status: ACTIVE — four open stories
Written: 2026-08-23

Read `ui-nouns.md` and `ui-toolkit.md` before changing a UI-foundation story.

| Story | Status | Dependencies / freshness |
| --- | --- | --- |
| `u1-retained-layout-workbench.md` | IN PROGRESS — live acceptance | Java tree, host adapter, row/column/stack boxes, paint/hit parity, and reachable preview are implemented and headless-tested. |
| `u2-clipping-scroll-and-focus.md` | IN PROGRESS — overflow clipping shipped for review | Shared nested padding-box clips now govern paint and hit-testing; scroll, keyboard focus, pointer capture, and canvas input remain. |
| `u3-theme-and-transitions.md` | Planned | Depends on U1–U2 interaction states; centralizes the visual language and retained animation. |
| `u4-mlx-components-and-bindings.md` | Planned | Depends on the authoritative Java tree and theme surface; adds scoped components, signals, keyed rows, and explicit hot reload. |

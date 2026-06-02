# Phase 3c — highlight overlay → command model (Bucket C)

> **✅ SHIPPED & VERIFIED** (2026-06-02; `a6e0db3` S1, `f9b419d` S2). The fate
> decision was forced by a finding that **changed the premise**: all three
> highlight sources (`SRC_SELECTED_SQUAD`, `SRC_CAPTAIN`, `SRC_ACTION_CELLS`) were
> published **only** by the `@DebugOnly` `SquadPlanDebugPanel` — so in a prod build
> the overlay was never populated and the pass was dead. The "gameplay source"
> Option 1 assumed *didn't exist yet*.
>
> Resolution (user pick): **build the real selection highlight** — promote the
> selected-squad cue to a genuine production feature, not just migrate the render.
> Two slices:
> - **S1 — render migration.** `HighlightOverlay.render` (own-GL `GL_QUADS` fills +
>   `GL_LINE_LOOP` outlines) → a `HighlightRenderer` (`ops.battleview`) emitting
>   `SOLID_RECT` fills + `LINE` outlines (no `POLY` — cells are axis-aligned rects).
>   `HighlightOverlay` became a pure source-state holder with a `sourceLists()`
>   accessor (kept in `battle.ui.highlight` to avoid an `ops.battleview` cycle).
> - **S2 — production publisher.** New `SelectionHighlightPublisher` feeds
>   `SRC_SELECTED_SQUAD` from the shared `Selection` every frame in
>   `BattleScreen.advance` (off post-tick positions; self-clears on deselect/wipe).
>   `SquadPlanDebugPanel` no longer owns that source; the gold captain badge
>   (`SRC_CAPTAIN`) + cyan action cells (`SRC_ACTION_CELLS`) stay `@DebugOnly`.
>
> Verified in-game: selecting a squad (world-click or squad-overview row, both
> production paths) highlights its members green; tracks movement; clears on
> deselect. **Follow-up (deferred):** promote the captain badge to production when
> that feature actually lands — today it stays debug.
>
> ---
> *Original design analysis below (kept for context).* Mixed debug/gameplay
> pass — *not* cleanly migratable as a whole, and *not* cleanly debug-only either.
> Resolve the split below before any command-model work. Do after Bucket A
> ([`geometry-fog-roofs-command-model.md`](geometry-fog-roofs-command-model.md));
> independent of Bucket B's arc decision.

## Scope

- **`highlights`** = `HighlightOverlay` (`battle.ui.highlight`), drawn via the
  `RenderLayer.HIGHLIGHTS` producer (`BattleRenderer:211`). Cell highlights drawn
  as `GL_QUADS` fills + per-cell `GL_LINE_LOOP` outlines, immediate-mode.

## Why it's mixed-fate (the gut-check)

`HighlightOverlay`'s **own Javadoc calls it "debug overlays on the battle map"**
and "a debug overlay capped at a few hundred cells." But its three sources don't
share a fate:

| Source constant | Publisher | Fate |
|---|---|---|
| `SRC_ACTION_CELLS` | GOAP debug panel (per-step action cells) | **debug** → `@DebugOnly` territory |
| `SRC_SELECTED_SQUAD` | unit selector (picked squad) | **gameplay** — real selection feedback |
| `SRC_CAPTAIN` | "future captain badge" | gameplay (unbuilt) |

So the overlay is a *shared mechanism* serving both a debug source and a gameplay
source. Migrating it wholesale would drag debug viz into the production command
model; leaving it whole as `@DebugOnly` would strip a real gameplay selection cue
from a prod build.

## The decision (resolve before coding)

Pick one:

1. **Split sources by fate.** Keep `HighlightOverlay` as the debug-source sink
   (`SRC_ACTION_CELLS`), mark it `@DebugOnly`, leave it `Custom`. Move the
   gameplay selection highlight (`SRC_SELECTED_SQUAD`, + captain when built) into a
   small production highlight pass that emits commands. Cleanest fate-alignment;
   most code.
2. **Migrate whole, drop the "debug" framing.** Decide the overlay is a
   permanent production HUD mechanism (debug panels are just *one consumer*), fix
   the Javadoc, and migrate it. Simplest, but blesses a debug-panel-fed mechanism
   as production.
3. **Leave entirely as `Custom` for now.** It's cheap (capped at a few hundred
   cells), correct, and not on the perf hot path. Defer until the captain badge
   (or another gameplay consumer) actually lands and forces the question.

**Recommendation (revisit when picked up):** Option 3 until there's a second
gameplay consumer — migrating a few-hundred-cell overlay buys little, and the
fate genuinely isn't settled while `SRC_CAPTAIN` is unbuilt. When the captain
badge lands, do Option 1.

## Migration mechanics (if 1 or 2 is chosen)

- **Fills** — `GL_QUADS` per cell → `SOLID_RECT` commands (one per cell). Trivial,
  existing kind.
- **Outlines** — per-cell `GL_LINE_LOOP` → four `LINE` commands per cell (the
  `LINE` kind shipped in Phase 2 / F-track). Or accept fills-only at first.
- Source insertion order = paint order; preserve it across emission.

## Verification (when done)

- Selected-squad cells still highlight green; GOAP debug panel action cells still
  show (if that source is kept). No paint-order regression between sources.

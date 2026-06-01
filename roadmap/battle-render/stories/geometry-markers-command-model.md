# Phase 3b — objective + compound markers → command model (Bucket B)

> **Status: design-stage / blocked on a decision.** The two gameplay-geometry
> passes that draw **arc/ring geometry**, which the current `DrawCommand` set
> can't express. Grouped because they share the same blocker and the same shapes.
> Do **after** Bucket A ([`geometry-fog-roofs-command-model.md`](geometry-fog-roofs-command-model.md))
> and after the arc-primitive decision below.

## Scope

- **`renderObjectiveMarkers`** (`BattleRenderer:615`, `RenderLayer.OBJECTIVES`) —
  SABOTAGE charge sites + equipment drops. Two visual parts:
  - **Icons** — `drawTintedIcon` → `SpriteAPI.renderAtCenter` (danger/alarm/star,
    with a pulse scale). Migrates cleanly to the existing `SPRITE` command.
  - **Progress arc** — `drawProgressArc`: a clockwise-filling annulus sweep drawn
    as an immediate-mode `GL_QUADS` triangle fan. **No command kind for this.**
- **`compoundMarkers`** (`CompoundMarkerRenderer`, `RenderLayer.COMPOUND`) —
  compound capture-state rings. Three parts:
  - **Faction ring** — `drawAnnulus`: full `GL_QUADS` annulus (same blocker).
  - **Capture arc** — `drawProgressArc` (same blocker) + a hairline `GL_LINES`
    rim (maps to `LINE` commands).
  - **Kind glyph** — `BitmapFont.drawString` of a single letter (its own text
    path, not a `DrawCommand`; leave as-is or treat as a separate concern).

## The blocker — no arc/poly primitive

`DrawCommand.Kind` is `SHEET_QUAD | SPRITE | SOLID_RECT | LINE | RIBBON | CUSTOM`.
Rings and progress arcs are **tessellated triangle fans** — neither an
axis-aligned rect nor a line. Both passes build them inline today. Migrating
forces a decision:

### Option A — add a `POLY` (triangle-strip / quad-fan) command + batcher

A general primitive: the command carries a vertex span (positions + a flat
color), the drain has a `PolyBatch` that submits them via client-side vertex
arrays (same mechanism as the post-perf-spike `QuadBatch.flush`). Rings, arcs,
and any future filled-shape overlay become `POLY` emissions.

- **Pro:** the "right tool" — closes the gap permanently; matches the
  [build-the-real-thing] preference over per-pass `Custom`. Reusable by any future
  radial/filled-shape HUD geometry.
- **Con:** most infra — a new command kind, a new batcher, pooled vertex storage
  on the command (variable-length, unlike the fixed-field kinds — needs care with
  the pooled-slot reuse model in `DrawList`).

### Option B — tessellate arcs into existing primitives at collect time

The producer expands each arc/ring into a fan of `SOLID_RECT`-ish quads (or
`LINE` segments) using the existing commands; no new kind.

- **Pro:** no engine change; keeps the command set small.
- **Con:** pushes trig into the producer every frame; an annulus quad isn't
  axis-aligned so `SOLID_RECT` (opposing-corners) can't represent a rotated quad —
  would need a rotated solid quad the set also lacks. In practice this likely
  *also* needs a small engine add (rotated solid quad), making A the cleaner buy.

**Recommendation (revisit when picked up):** lean Option A — the rotated-quad gap
means B isn't actually infra-free, and a `POLY` primitive is reusable. But confirm
the pooled-slot variable-length-vertex concern is tractable first.

## Gut-check before investing (per the triage rule)

- `compoundMarkers` **self-describes as provisional v1 viz** — its Javadoc says
  "Future iteration can swap this [glyph] for proper icon sprites (a small
  barracks / armory / command flag)." Before building arc infra *for it*, confirm
  with the user whether the ring+arc+glyph language is staying or getting reworked
  into sprite-based markers (which would migrate via `SPRITE`, no arc primitive
  needed). **Don't build a `POLY` primitive to serve a marker that's about to
  become sprites.**
- `renderObjectiveMarkers` is less provisional (charge sites are a shipped
  objective), but the same question applies to its progress arc specifically.

## Suggested sequencing if it proceeds

1. Resolve the provisional-viz question for compound markers (sprites vs.
   ring/arc). If sprites win, that pass migrates via `SPRITE` with no arc work.
2. Resolve Option A vs B for whatever genuinely needs arcs.
3. Migrate the icon/sprite parts first (cleanly `SPRITE`), arcs last.

## Verification (when done)

- Charge-site alarm/danger icons pulse + progress arc fills clockwise from 12
  o'clock; equipment-drop stars pulse.
- Compound rings show DEFENDER (crimson) / CONTESTED (amber, throbbing) /
  MARINE (blue), capture arc fills, glyph centered. All verified working in the
  Final collapse pass — migration must be visually identical.

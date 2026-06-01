# Story — Global pixel-density scale (`METERS_PER_PX`)

> Shared-core member of the [`air/`](../overview.md) category. Implements the
> "Reconciliation point" called out in
> [`hull-extraction.md`](../hull-extraction.md) § Scale and the resolved
> direction in [`ships/overview.md`](../ships/overview.md) § "Scale & altitude".

## Shipped — `c553532` (machinery) + calibration/turret-scale follow-up

> **Turret-placement part superseded by
> [`turret-mounts-from-ship-spec.md`](turret-mounts-from-ship-spec.md) (`5290e17`).**
> `TURRET_AUTHORING_HULL_CELLS` and the per-ship `turretSpread` factor were
> deleted — turret positions now come from each hull's real `weaponSlots` at this
> same density, no per-ship factor. The hull-footprint + `METERS_PER_PX` work
> here is unchanged.

Landed: `battle/air/AirScale` (the one constant), `HullFootprintResolver`
(cached `.ship`-height → derived length), `ShuttleType` stripped of the authored
`visualLengthCells` and the `turretVisualScale` hack (`+ renderHullId()`), and
`ShuttleRenderSystem` / `EngineSlotResolver` routed through the resolver so a
hull and its engine FX share one derived length. Parser left pure; flyby
fighters left for the fighters track (disjoint hull ids).

**Calibration + turret param-awareness (follow-up commit).** The realistic
`0.65` was too big in practice, so `METERS_PER_PX` was dialled to **`0.045`** —
smallest shuttle (Kite) back to ~3 cells, Valkyrie ~12, a true ~4× ladder beside
~1-cell infantry. At that scale the literal "1 cell = 1 m" anchor is relaxed:
these are gameplay-scale units, not realistic metres (documented on the
constant). Turret **placement** was also made **param-aware** — mount offsets
are read in a hull frame normalized to `AirScale.TURRET_AUTHORING_HULL_CELLS`
(default `4`) and scaled by `derivedHullLength / that`, so mounts sit correctly
on a hull of any size instead of clustering at the centre. Two clean knobs
result: `METERS_PER_PX` (overall ship size) and `TURRET_AUTHORING_HULL_CELLS`
(mount spread). Note: turret **size** is *not* hull-scaled — a `TurretKind`
renders at its fixed `visualCells` on every hull (like a ground `MapTurret`); an
initial version wrongly scaled size by the hull factor and was corrected in the
per-turret-LoS follow-up.

**Outstanding:** final in-game eyeball of both knobs (a full `gradlew build` was
blocked at commit time by an unrelated concurrent-session error in
`ShotRenderService`, not this story; files verified clean via IDE inspection).
Realistic absolute scale + 512-map growth is now a deliberate *future* lever,
not a blocker.

## Goal

Stop sizing each air hull by a hand-authored `visualLengthCells` and instead
size **every** vanilla/modded hull off a single global pixel-density constant,
anchored at **1 cell = 1 m**. Vanilla's own internal art consistency (all
Starsector sprites share one pixel density) then reproduces the whole
relative-size ladder — fighter < frigate < destroyer < cruiser — for free, and
modded hulls inherit it without special-casing.

This is the "fix the render dimensions of our fighters/ships off the pixel
density we discovered from the base game" work.

## Decisions (locked)

- **`METERS_PER_PX` calibrated to `0.045` (gameplay scale).** First locked at the
  realistic `0.65` (Kite ~43 cells, Valkyrie ~172 — true metres, presuming 512+
  maps), but that dwarfed current maps in practice, so it was dialled back to
  `0.045`: Kite ~3 cells, Valkyrie ~12, a true ~4× ladder beside ~1-cell
  infantry. The literal "1 cell = 1 m" anchor is therefore relaxed — these are
  play-scale units. Realistic absolute scale + bigger maps remain a deliberate
  future lever; the machinery makes it a one-constant change.
- **Re-scale shuttles now (full adoption).** Shuttles are the only shipped
  on-map air craft and the live calibration target.
- **The parser stays a pure "render at length L" function.** The global-density
  *policy* lives in the callers (they pass the derived length); the parser keeps
  its `visualLengthCells` param unchanged. This keeps flyby consistent and the
  change surgical.
- **Flyby fighters (`battle/flyby/`) are out of scope.** Their hull ids
  (talon/wasp/broadsword/thunder/dagger) are **disjoint** from shuttle hull ids,
  so the shared engine-slot cache never collides. Flyby is slated for wholesale
  replacement by the fighters track (S4); re-sizing a doomed system now is
  wasted ([[feedback_no_stopgap_dev]]). They convert when rebuilt on `AirBody`.
- **Per-craft override field deferred (YAGNI).** No current shuttle is
  deliberately off-scale — every authored value is exactly what we're discarding.
  The override seam is the resolver; add an override only when a real off-scale
  craft appears.
- **AEROSHUTTLE borrows Kite's footprint.** It has no `.ship` hull (no
  `aeroshuttle.ship`) and empty `matchingHullIds` by design, but renders Kite's
  sprite — so its render hull id resolves to `"kite"`. Its engine-FX behavior is
  unchanged (still none — only the footprint borrows).

## Why now

- It's the prerequisite that makes fighters and ships ([`fighters/`](../fighters/overview.md),
  [`ships/overview.md`](../ships/overview.md)) render at believable *relative*
  sizes the moment they're recaptured — without it, every new hull needs another
  eyeballed `visualLengthCells` and another `turretVisualScale` patch.
- Shuttles (shipped) already expose the problem and give us a live calibration
  target, so we lock the constant against real on-screen craft rather than a
  spreadsheet.

## Current state (the problem)

- `ShuttleType.visualLengthCells` is **hand-authored per hull** (Aeroshuttle 3.0,
  Hermes 2.8, Buffalo 4.0, Nebula 5.0, Valkyrie 5.0…). These were eyeballed so
  each shuttle "reads," **not** derived from pixel density — so they *compress*
  the true ladder (a Nebula barely out-sizes a Hermes on screen).
- `ShipSpecEngineParser` derives `pxPerCell = spriteHeightPx / visualLengthCells`
  **per ship**, which deliberately discards relative size — each hull normalizes
  to its own authored length.
- `ShuttleType.turretVisualScale` (e.g. Valkyrie `0.55`) exists **only** to
  counter that per-hull squashing so a fixed-cell turret doesn't overhang a
  hull that got squashed narrow.
- `ShuttleRenderSystem` sizes the hull quad as
  `pxLen = type.visualLengthCells * cellPx * scaleMult` (`ShuttleRenderSystem.java:69`).

## Design

### The constant

A single `METERS_PER_PX` (calibration target **~0.65 m/px**; tune in playtest).
With **1 cell = 1 m**, the derived quantities are:

```
visualLengthCells = specHeightPx * METERS_PER_PX     // forward extent, in cells
pxPerCell         = 1 / METERS_PER_PX                 // the px↔cell ratio, CONSTANT
```

`specHeightPx` is the vanilla `.ship` `height` field — the sprite's pixel extent
along the ship's forward (+X) axis (ships are drawn nose-up). It's already read
by `ShipSpecEngineParser`, sandbox-safe via `SettingsAPI.loadJSON`, and
modded-hull-correct.

### The key collapse

Substituting the derived length back into the old per-ship divisor:

```
pxPerCell = specHeightPx / visualLengthCells
          = specHeightPx / (specHeightPx * METERS_PER_PX)
          = 1 / METERS_PER_PX          // specHeightPx cancels — same for every hull
```

So **`pxPerCell` becomes one global constant**. The engine-slot transform and
the hull footprint converge on the same number — which is exactly the
unification [`hull-extraction.md`](../hull-extraction.md) § Scale describes
("the same `pxPerCell` then unifies footprint with the slot-scraping
transform"). The collapse happens *at the caller*: shuttles pass the **derived**
length into the unchanged parser, so `pxPerCell` lands on `1/METERS_PER_PX`
without the parser itself needing to know the global constant. Keeping the
parser a pure "render at length L" function is what lets flyby stay untouched.

### Where the constant lives

One battle-tier constant in `battle/air/` (proposed `AirScale.METERS_PER_PX`
with `PX_PER_CELL = 1f / METERS_PER_PX`), so shuttles, fighters, ships, and the
slot parser all read one source of truth. Kinematic `SCALE` (su→cells for
speed/accel) stays **separate** — this story touches *footprint* only, not feel.

### Derived length resolution

`visualLengthCells` flips from an authored enum field to a **derived, cached**
lookup off the hull id — parallel to `EngineSlotResolver`:

- Proposed `HullFootprintResolver.visualLengthCells(hullId)` →
  `loadJSON("data/hulls/<id>.ship").height * METERS_PER_PX`, lazy-cached,
  shared across air entities, degrades to a sane fallback on miss (log once).
- Keep an **explicit per-craft override** seam for deliberately
  non-vanilla-scale craft (the doc's caveat) — a small override map or an
  optional field, defaulting to "derive."

## Implementation slices

1. **Constant.** Add `battle/air/AirScale` — `METERS_PER_PX = 0.65`,
   `METERS_PER_CELL = 1`, `PX_PER_CELL = 1/METERS_PER_PX`, a `cellsForHeightPx`
   helper, and a `FALLBACK_LENGTH_CELLS`. Parser left **unchanged**.
2. **Derived footprint resolver.** Add `battle/air/engine/HullFootprintResolver`
   — `visualLengthCells(hullId)` = `.ship "height"` px × `METERS_PER_PX`,
   lazy-cached, fallback on miss. Single authority for a hull's render length,
   shared by render + engine-slot scaling + the author panel.
3. **Re-scale shuttles (full adoption).** Remove the authored
   `ShuttleType.visualLengthCells` field; add `renderHullId()` (first
   `matchingHullId`, or `"kite"` for AEROSHUTTLE). Route `ShuttleRenderSystem`'s
   `pxLen` and `EngineSlotResolver.resolve(type)` through the resolver so the
   hull and its engine FX share one derived length.
4. **Retire `turretVisualScale`.** With one true density, hull widths are real
   and a fixed-cell turret fits naturally — remove the field + its multiply in
   `ShuttleRenderSystem`. Re-check turret `visualCells` against the now-larger
   hulls in playtest.
5. **Fix up the author panel + preview test.** Route `TurretAuthorPanel` through
   the resolver. The `EngineSlotPreviewTest` round-trip is invariant to the
   length value (it cancels), so derive it from the spec height there too.
6. **Playtest / map-scale follow-up.** Confirm the ladder reads and turrets sit
   right; the 0.65 absolute scale presumes 512+ maps — log map-growth as the
   downstream dependency (see Consequences).

## Consequences & risks

- **Shipped shuttle visuals change.** Re-scaling onto real density resizes every
  shuttle — needs a playtest pass (deploy + `runStarsector`), not just a unit
  test. This is accepted scope (full adoption), not a regression.
- **Turret sizing drifts.** Turret sprites (`TurretKind.visualCells`) were tuned
  against squashed hulls; once hulls grow to true scale they may read small.
  Re-tune in the same playtest; this is *footprint-only*, kinematics untouched.
- **Aspect handling unchanged.** `ShuttleSpriteCache.aspect` (w/h) still drives
  width; we only change how the forward extent (`pxLen`) is computed.

## Out of scope

- Concave-polygon collision footprint from `bounds` (use bounds extent, not
  sprite height) — that's the [`ships/`](../ships/overview.md) collision story.
  This story sizes the **render quad**; collision geometry comes later.
- Kinematic `SCALE` (speed/accel feel) — separate factor, untouched.
- Camera-Z / altitude axis — render-layer track, see
  [`ships/overview.md`](../ships/overview.md) § "Scale & altitude".

## Testing

- Unit: `ShipSpecEngineParser` slot positions are invariant under the refactor
  for a fixed hull at the chosen constant (the existing `EngineSlotPreviewTest`
  hulls are the fixtures). `HullFootprintResolver` returns
  `specHeight * METERS_PER_PX` for a known hull (e.g. Kite/Wayfarer) and the
  fallback on a missing id.
- Playtest: deploy, drop shuttles, confirm the relative-size ladder reads and
  turrets sit on their hulls.

## Done when

- One `METERS_PER_PX` sizes every air hull; `visualLengthCells` is derived
  (override-only as a field), `turretVisualScale` retired (or justified).
- Shuttles render on the real ladder and play correctly.
- `hull-extraction.md` § Scale and `ships/overview.md` § Scale updated from
  "Reconciliation point / open question" to "shipped — see this story," and the
  story moves to `roadmap/air/complete/`.

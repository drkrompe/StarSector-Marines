# Room-purpose refactor

> Carve-time `RoomPurpose` labels on `CellTopology` replace post-hoc
> zone-graph chamber inference. Generalizes to N-chamber partitions
> (immediate target: three-chamber keep), and to future map types
> (station fills will extend the enum with HANGAR / CORRIDOR /
> HABITATION / COMMAND).

## Goal

Make rooms a **first-class concept** the partitioner writes at carve
time, instead of a connectivity property stampers reverse-engineer
from the grid.

**Today (Slice A shipped):** `BuildingShellCore` labels chambers on
the way out of `carve()`. `KeepEntryChamberStamper` reads labels
directly. Same behavior as before; the zone-graph dependency is gone.

**Goal of full sequence:** any consumer that wants "what kind of room
is this cell in?" calls `topology.getRoomPurpose(x, y)`. Adding a
new map type means implementing a `PartitionStrategy` that writes the
labels its post-fill stampers expect — no zone-graph
reverse-engineering, no geometry inference, no special-cases in
consumers.

## Slice progression

### Slice A — labels (shipped)

Commits: `82c76a9` (foundation), `d3f659d` (polish).

- **`RoomPurpose` enum** in `battle.map` next to `BuildingKind`.
  Initial set: `GENERIC`, `KEEP_ENTRY`, `KEEP_INNER`, `KEEP_THRONE`.
  Extensible — `ordinal()+1` byte storage in `CellTopology` is
  opaque to the rest of the codebase, so adding values is non-breaking.
- **Per-cell storage** on `CellTopology` (matching `BuildingKind` /
  `NatureTile` pattern). Implicit zero = "unset"; `getRoomPurpose`
  returns `null` for unset.
- **`BuildingConfig.chamberPurposesByAnchorDistance`** — distance-
  indexed `RoomPurpose[]`. Index 0 = anchor's chamber (THRONE for
  keep); index 1 = chambers one partition away (ENTRY for binary,
  INNER for ternary); index 2 = two partitions away (ENTRY for
  ternary). Carries through to any chamber count without changing the
  config shape.
- **`BuildingShellCore.labelRooms`** writes labels for every walkable
  non-doorway interior cell. Uses `chamberIndex(wall, x, y)` to map
  cells to chamber indices (currently binary; bisect-generalizes for
  ternary).
- **`MilitaryBaseFiller.COMMAND_CONFIG`** opts in with
  `[KEEP_THRONE, KEEP_ENTRY]`.
- **`KeepEntryChamberStamper`** drops `ZoneGraph` entirely; reads
  `KEEP_ENTRY`-labeled cells directly. Walkability re-check guards
  against later passes mutating labeled cells.
- **Tests**: stamper exercises both horizontal + vertical partition
  cases, label-on-non-walkable-cell defensive case, full-carve
  roundtrip via new `BuildingShellCoreLabelTest` in `mapgen.bsp.fill`.

### Slice B — `PartitionStrategy` interface (pending)

Pure extraction. No new behavior.

- New `PartitionStrategy` interface in `mapgen.bsp.fill`:
  ```java
  interface PartitionStrategy {
      InteriorWall carve(NavigationGrid grid, CellTopology topology,
                         int bl, int bt, int br, int bb,
                         Random rng, GroundKind interiorGround);
  }
  ```
  (Return type may become `PartitionLayout` to carry N axes — decide at
  implementation time.)
- `BinaryPartitionStrategy` — lifts the current `maybeAddInteriorWall`
  body verbatim. Singleton `INSTANCE`.
- `BuildingConfig` carries a `PartitionStrategy` field (defaults to
  `BinaryPartitionStrategy.INSTANCE`).
- `BuildingShellCore.carve()` dispatches via the strategy instead of
  calling the static helper.
- Static `maybeAddInteriorWall` deleted; logic now lives in the
  strategy.

**Why it's its own slice:** Slice C is a real behavior change (3
chambers); shipping the extraction separately lets the binary path
ship with a clean interface before any new geometry lands. Cheap to
review, cheap to bisect.

### Slice C — `TernaryPartitionStrategy` (pending)

Three chambers along the longer axis. Asymmetric placement — middle
chamber is narrowest so the BFS-from-center anchor reliably lands in
an end chamber (which then gets labeled `KEEP_THRONE` by the
distance-indexed labeling).

- New `TernaryPartitionStrategy`. Min building dimension ~14 cells on
  the long axis (3 chambers + 2 partition walls + room for the middle
  chamber).
- Carves two parallel partition walls. Punches an interior doorway
  between each adjacent chamber pair (3 chambers → 2 interior
  doorways).
- Returns a layout with 2 axes; `chamberIndex` generalizes via
  sorted-array bisect.
- `MilitaryBaseFiller.COMMAND_CONFIG` opts in when the COMMAND sub-
  building is large enough; otherwise falls back to
  `BinaryPartitionStrategy` (the 2-chamber path stays valid).
- `chamberPurposesByAnchorDistance` becomes
  `[KEEP_THRONE, KEEP_INNER, KEEP_ENTRY]` — labeling works
  automatically once the strategy emits 3 chambers.

**Open question to resolve at implementation:** does the strategy pick
its asymmetry deterministically (e.g. "long axis split into 40-20-40")
or via RNG within bounds? Probably RNG within bounds for visual
variety; constrained enough that the middle stays narrow.

### Slice D — multi-chamber emission (pending)

- `KeepEntryChamberStamper` walks each labeled non-throne chamber and
  emits one `INNER_POSITION` per chamber.
- `KEEP_ENTRY` chambers get smaller forward garrison (current size:
  3 marines, priority 60).
- `KEEP_INNER` chambers get mid-strength garrison — tune at
  implementation (probably 4 marines, priority 65 — sits between
  ENTRY and the COMMAND_POST's 95).
- Tests: 2-chamber case still emits one `INNER_POSITION` (existing
  behavior preserved); 3-chamber case emits two `INNER_POSITION`s.

## Critique findings from Slice A

Background-agent review of `82c76a9` (full report in chat history
2026-05-22). Severity-labeled:

- **DESIGN — Two-field labeling contract didn't scale.** Original
  Slice A used `anchorRoomPurpose` + `otherRoomPurpose` on
  `BuildingConfig`. Critique flagged this as bakes-in-binary-thinking;
  ternary would require a third field. **Resolved in `d3f659d`** by
  switching to `RoomPurpose[] chamberPurposesByAnchorDistance` —
  Manhattan-distance addressing scales to N chambers.
- **NIT — Stamper didn't re-check walkability.** A later pass that
  mutates a labeled cell to non-walkable would silently bias the
  centroid. **Resolved in `d3f659d`.**
- **NIT — `axis` read unconditionally in `labelRooms` even when
  partition is NONE.** Cosmetic. **Folded into the `chamberIndex`
  refactor in `d3f659d`.**
- **NIT — No vertical-partition stamper test, no full-carve roundtrip
  test.** **Both added in `d3f659d`.**
- **NIT — Old `skipsCommandPostWithUnwalkableAnchor` test removed.**
  The new label-driven path doesn't read the anchor coord at all, so
  the test's invariant doesn't apply. Replaced with
  `emitsNothingWhenBuildingHasNoLabels` which covers the defensive
  "no carver labeled this" case.

## Risk: corridors-as-first-class

The audit (`pipeline-audit.md`) flags corridors as the real blocker
for station fills. The current refactor sequence handles room labels
but doesn't touch BSP's "leaves are atomic buildings, roads are
perimeter filler" assumption.

Station fills will likely want a **different orchestrator** (corridors
as primary connective structure, rooms as vertices) rather than
retrofitting BSP. When that work lands, `PartitionStrategy` may need
to become more general — e.g. carving sub-rooms inside an arbitrary
polygon, not just an axis-aligned bbox. Capturing here so neither the
strategy interface nor `RoomPurpose` accidentally bakes in BSP-only
geometry.

For now: keep `PartitionStrategy` simple. The strategy interface signs
up to handle "carve sub-rooms inside this bbox + label them"; station
fills will use a different abstraction for inter-room corridors.

## Cross-refs

- [`pipeline-audit.md`](pipeline-audit.md) — the audit this refactor
  was scoped against.
- [`../conquest/central-keep.md`](../conquest/central-keep.md) —
  Slice 6 of central-keep is the immediate consumer (the keep is the
  only fortified COMMAND building today).
- [[battle_services_systems]] — the decomposition pattern this
  refactor's strategy extraction follows.

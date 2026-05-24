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

### Slice B — `PartitionStrategy` interface (shipped)

Commit: `042d084`.

- `PartitionStrategy` interface in `mapgen.bsp.fill` with single
  `partition()` method returning `PartitionLayout`.
- `BinaryPartitionStrategy` lifts `maybeAddInteriorWall` body
  verbatim. Owns `multiRoomChance` as a constructor parameter
  (was a static constant, then a `BuildingConfig` field — now
  belongs to the strategy). `DEFAULT` singleton at 0.65.
- `PartitionLayout` replaces private `InteriorWall` / `InteriorWallOrient`.
  `chamberIndex()` moved onto the layout. Initially single `int axis`;
  generalized to `int[] axes` in Slice C.
- `BuildingConfig` carries `PartitionStrategy` field (7-arg constructor);
  existing 5/6-arg constructors default to `BinaryPartitionStrategy.DEFAULT`.
- `maybeAddInteriorWall`, `openInteriorDoorway`, `InteriorWall`,
  `InteriorWallOrient`, `chamberIndex` static method all deleted from
  `BuildingShellCore`.

### Slice C — `TernaryPartitionStrategy` (shipped)

Commit: `ee55eb0`.

- `TernaryPartitionStrategy` carves two parallel interior walls along
  the longer axis. Falls back to `BinaryPartitionStrategy` when the
  building is too small (long axis < 14 cells, short axis < 7).
- Wall pair placed on the same side of center so BFS-from-center
  anchor reliably lands in an end chamber (THRONE). RNG within bounds
  for visual variety; middle chamber constrained to be narrower.
- `PartitionLayout` generalized from single `int axis` to `int[] axes`
  with sorted-bisect `chamberIndex` (0 chambers for NONE, 2 for
  binary, 3 for ternary).
- `punchDoorwayOnSide` updated with `pickAlongRange(min, max, int[] excludes, rng)`
  to handle multiple partition wall exclusions.
- Tested across 50 seeds — all produce three labeled chambers with
  ≥3 cells each. Diagnostic preview at
  `build/zone-previews/ternary-partition-labels.png`.

### Slice D — multi-chamber emission (shipped)

Commit: `b8b7b9d`.

- `MilitaryBaseFiller.COMMAND_CONFIG` wired to `TernaryPartitionStrategy.DEFAULT`
  with `[KEEP_THRONE, KEEP_INNER, KEEP_ENTRY]`. Falls back to binary
  for small keeps; binary labels distance 0 → THRONE, distance 1 → INNER.
- `KeepEntryChamberStamper` generalized from KEEP_ENTRY-only to all
  non-THRONE purposes. Emits one `INNER_POSITION` per chamber:
  KEEP_INNER (priority 65, garrison 4), KEEP_ENTRY (priority 60,
  garrison 3). A 3-chamber keep emits 2 nodes; 2-chamber emits 1.
- Room-purpose overlay added to `BspMapPreviewTest` conquest renders
  (semi-transparent blue/yellow/red on labeled keep interiors).
- Tests: 3-chamber emission (2 INNER_POSITIONs with correct anchor
  placement + priority ordering).

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

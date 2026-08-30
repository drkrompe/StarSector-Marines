# Authored room layouts

Status: IN PROGRESS

Written: 2026-08-30

Give the workbench a page that edits a shipboard room directly — its footprint,
its floors and walls, and the fixtures standing on it — and let the result be
what the game generates, compared against what it generates today before it is
accepted.

## Why this is possible at all

A ship room's footprint is **fixed, and only its pose varies**.
`RoomPacker.Request` takes the recipe's `RoomShape` verbatim and `Placed`
records a `RoomPose`; nothing scales a room to fit a gap. `RoomFloor.toLocal`
and `toCanonical` already exist to carry a canonical-frame arrangement into a
turned or mirrored room, because that is how the procedural fittings survive
being rotated.

That is the whole enabling fact. **Hand-authored canonical cells are already
compatible with packing**, so this story adds an artifact and a page and does
not touch the placer.

## What is actually missing

Three things stand between the code today and the tool:

1. **A room's contents are a program.** A `RoomFitting` per purpose emits
   fixtures procedurally. There is nothing to hand-edit, and no file to write.
2. **A room has no surfaces of its own.** Floor comes from `GroundKind`, a
   vocabulary shared with the whole game, and walls from the single global
   `SurfaceRole.WALL`. Reskinning *this room's* deck has nowhere to live —
   this is a data-model gap, not merely a missing editor.
3. **A footprint is a Java constant.** `RoomRecipe.TROOP_BERTHING` is
   `RoomShape.rectangle(8, 6)` in source.

## The artifact

`RoomLayout` — one authored document per `(RoomPurpose, RoomFit)`, holding the
footprint mask, a per-cell floor surface, the wall surface for its bulkhead
ring, its placed fixtures (id, cell, orientation, affordance, task point), and
its authored hookups.

**Keyed by refit level from the first commit.** An authored arrangement is one
arrangement and cannot express the ladder a procedure expresses for free, so the
key keeps all three reachable: author `STANDARD` first, and a level with no
document falls back to its procedural fitting. Nothing regresses and every
option stays open — which is why this is not a question that has to be answered
before the model is built.

## Seed, then edit

The fitting becomes the **seed generator rather than the runtime authority**.
"Generate the initial room and size" runs the existing `RoomFitting` and
captures its output as an editable document; from there it is hand-corrected and
saved. This is the shape the tileset walkthrough already uses — measure and
propose automatically, correct by hand, write atomically — and it is why no
authored room ever starts from an empty grid.

A purpose with no document keeps its procedural fitting, so this lands additive.

## Comparison

Law 17 governs: a deck is seen through the battle renderer, never through a
second painter. The A/B therefore **generates the same deck at the same seed
twice** — once with the document suppressed, once with it applied — and renders
both through `BattleReviewFrameRenderer`. It is a real before-and-after of the
room in the hull, at the pose the packer actually chose, rather than a mockup of
the room alone that would drift from what ships.

## Scope

- `RoomLayout` and its JSON, loaded through the same `ingestSheet`-style seam
  the tile catalog uses so tests can build one without a game process.
- `RoomFittings` consults an authored layout for `(purpose, refit)` before
  falling back to the procedural fitting.
- A per-room surface: a floor surface per cell and a wall surface for the ring,
  resolved through the existing block catalog rather than a new art path.
- A workbench page of numbered screens: which room, the footprint, the surfaces,
  the fixtures, then compare and save.
- Seeding a document from the procedural fitting at a chosen pose and level.
- Atomic validated writes, as the other pages do.

## Constraints

- **A fitting only ever names an id the registry already has.** Carried over
  from `facility-room-themes.md`: a missing id makes `place` return false
  silently, so the editor validates against the catalog and refuses rather than
  writing a room that comes out bare with nothing to say why.
- **The placer is not touched.** If this story finds itself editing
  `RoomPacker`, the canonical-frame premise above has failed and the design is
  wrong.
- Authoring is a tool; the shipped jar must not acquire the workbench. The
  boundary is already enforced by `verifyModJarBoundary`.
- Law 13 and law 14 still hold: an authored door is a door-sized hole in the
  ring, and a layout may not open a bulkhead.

## The consequence worth naming

**Capacity is the fixture count** (law 4), and `RoomRecipe.provides` currently
asserts it separately — nine bunks for a berth. Once fixtures are authored, that
number is derived from the document, so authoring a room changes the ship's
berth count and therefore what the company can lift. Either it is wired through
or the two disagree silently, which is exactly how a ship comes to berth more
people than she has bunks. Wire it, and let the editor show the figure it is
about to change.

## Acceptance

- A deck generated with no authored document is **byte-identical** to today's,
  proven by the deck fingerprint rather than by inspection.
- An authored document changes the room it names and nothing else on the deck.
- A layout survives every pose: the same document laid down turned and mirrored
  puts its fixtures in the corresponding cells, and its door stays in its ring.
- A layout naming an unknown doodad id is refused at load with the id in the
  message, not placed silently.
- The comparison renders both decks through the battle renderer at one seed.

## Out of scope

Ground-battle compound rooms, which are packed from a different recipe family
and are not fixed-footprint. Authoring the refit ladder — the key exists, the
other two levels stay procedural until somebody wants them. Any change to how
art is drawn.

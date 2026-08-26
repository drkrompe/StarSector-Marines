# Ship deck family

Status: PLANNED

Written: 2026-08-26

Read `ship-interiors-nouns.md` before implementing this story. Read
`mapgen-nouns.md` for the recipe, context, stage, and validation obligations this
family inherits.

Add the longitudinal deck family alongside the existing station recipes. The
station layouts organize space around a core; this one organizes it around an
axis. The station recipes' layout-neutral topology tier is reused as-is — this
story adds a spatial premise, not a second topology model.

## Scope

- A **hull profile** stage that establishes the deck silhouette as beam per
  frame: narrow forward, broadest amidships, tapering aft, and free to differ
  port from starboard. Everything outside the profile is hull, not playable
  space.
- A **spine** stage that runs the primary fore-aft corridor and assigns each
  frame a longitudinal zone.
- A **compartment carve** stage that hangs purposed compartments off the spine,
  choosing purpose by zone affinity rather than a coordinate table.
- A **transverse bulkhead** stage that divides the deck at chosen frames with
  authored hatches, producing an ordered chokepoint sequence.
- A **breach point** stage that places the boarding entry on a flank at a chosen
  frame, and sets the longitudinal assault gradient from it.
- A deck graph that carries the shared topology tier plus this family's
  annotation: each compartment's frame span, zone, and side of the spine.

## Constraints

- Determinism is inherited: identical request inputs and recipe composition
  produce the same deck. No stage draws for an absent feature, and no unordered
  iteration decides an outcome.
- Decoration cannot create movement, cover, or wall rules; bulkheads and hatches
  are structural and declare their effect.
- The layout-specific ring, core, and port annotations on the existing station
  graph are not a ship's. Decide in this story whether the shared tier is
  extracted, renamed, or annotated in place, and state the reason in the commit.

## Acceptance

- Generated decks are elongated and not mirrored fore to aft. A determinism sweep
  over representative seeds shows beam genuinely varying with frame; a symmetric
  or rectangular result fails the family.
- Depth-from-entry correlates with longitudinal distance from the breach point,
  so the assault gradient runs along the axis rather than radially.
- Transverse bulkheads appear in the graph as an ordered chokepoint sequence,
  and every hatch is passable.
- Every compartment is reachable from the spine, and the deck remains connected
  and deployable across the seed sweep.
- Consumers read zone, frame, and spine membership from the published graph. No
  test or stage re-derives them from cell coordinates.

## Out of scope

Compartment interiors, fixtures, ambient life, upgrades, and any adoption by an
operations screen. This story ends at a validated empty deck with correct
topology.

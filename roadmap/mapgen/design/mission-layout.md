# Mission layout

Status: ACTIVE — lines of works and sites are built; roads, structures and
blocks are not.

Written: 2026-10-23

`mapgen-nouns.md` is the model this sits in. A mission that needs a structure
at a place it chose (a picket line, a set piece for a beat, ground held for a
ridge) states it in its plan's **mission layout**, and generation lays it over
the country it grows. The machinery is mission-neutral: it knows lines, sites
and when a piece is resolved, never which mission asked. The Cordon
(`cordon-map-intent.md`) and the heart (`heart-map-intent.md`) both build on
it.

## Pieces

A layout is a list of **named pieces**; a name is used once. A piece is
**required** (a map that cannot hold it is unfit for its mission) or
optional (reported, and the map stands).

- **Line.** Works along a path of cells, facing the front: a trench with wire
  in front, a trench alone, a sandbag course, or a **street line** (a sandbag
  course that, where a house stands across it, runs along the house's front
  instead). A trench is dug as deep as the line asks where the ground allows,
  with a sandbag parapet on its front lip, its wire a few cells further out,
  and a band of turned earth either side: what makes a line read as a
  fieldwork from a whole-map view rather than a hairline. A line is broken
  wherever a road crosses it, with a margin either side (the **gap**), so
  roads always pass; a line may barricade its gaps, closing each street mouth
  with hedgehogs and crates but its middle lane. Wire is opened in **funnels**
  at a fixed pitch. A run long enough takes a nest at each end and one at a
  pitch between.
- **Site.** Ground held by name, for a purpose. An **open** site is ground for
  a set piece or an event, kept clear of trees, rises and props. A **ground**
  site is held for authored terrain: nothing is built on it, and ridges and
  rises are free to use it.
- **Deferred.** Pieces that can only be stated once the ground exists — a line
  from one place's claimed edge to the next's. A deferred piece resolves at
  its **phase** against a read-only view of the grown ground (claims, roads,
  walkability, and a seed per place, never the generation random stream), and
  what it returns is laid under the returned pieces' own names. A site it
  returns holds its ground against the works resolved with it.

## When things are laid

- **Before growth.** A site stated in the layout itself is known before
  anything grows: no road arm, claimed place, field or farm grows onto it.
- **Works phase.** Once the places, roads and lanes stand and before the
  landscape grows round them, deferred pieces resolve and every line and site
  is laid. The landscape then keeps clear of what was laid, except ground held
  for authored terrain.
- A map whose plan states no layout runs none of this and is generated exactly
  as before.

## Laws

- **Placements yield.** A work goes only on open ground that is not road: a
  line through a town is broken by its buildings and reported **partial**.
- **Never sealed.** Works never cut the walkable ground apart. Wire, nests and
  barricades that would are not laid, and the line says so. A line that closes ground
  leaves at least one way through.
- **Reported by name.** Every piece comes back as laid, partial or refused,
  with its cells, its ways through and its nests, on the finished map. A later
  objective asks "is this here" by name.
- **One check.** Whether a laid layout keeps its promises (required pieces
  stand, every gap is open, no line seals) is one check that a mission's
  acceptance and the story-map pools both read.

## Not yet

Roads (supply roads to a redoubt's rear gate), structures (a farmstead, a
granary) and authored blocks are further piece kinds, with the structures
phase they need. A nest is published as a light defence post; a mission that
does not seat map posts seats its own guns in the nests it is reported (the
Cordon's garrison does).

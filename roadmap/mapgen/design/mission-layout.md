# Mission layout

Status: ACTIVE — lines of works, sites, roads, structures, blocks and ridges are
built.

Written: 2026-10-23

Updated: 2026-10-24 — a Ridge may carry terraces, places stood on its crest.

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
- **Road.** A drivable road between two named places' sides (or their open
  gate nearest that side, so it arrives where the wall lets it in), by a
  dog-leg (out, across at halfway, in) and optionally through a point on the
  way. It is carved into the road network as the places grow, so the gates,
  paving and road graph come from the stages that make every road. It is laid
  only on open country, not across another place's ground or the surveyed
  country (that ground's own streets serve), and only where it joins the
  network: a stretch cut off is left out rather than welded across somebody's
  ground.
- **Structure.** Something raised at a place the mission chose, by kind: a
  farmstead (a farmhouse and a barn on a yard), a granary (a long store on a
  yard), an ammo dump (a sandbag ring round stacked crates), an observation
  post (a small sandbag ring), a square (open paving with a marker in the
  middle). Its whole footprint must be open ground nobody built on, or it is
  refused rather than squeezed; buildings come from their authored plans, so
  they are buildings like any other. The square's marker stands in for a bell
  until there is bell art.
- **Block.** Cells closed or fortified by hand: a barricade (hedgehogs and
  crates, which fire breaks and a dozer blade pushes), a wreck across a road
  (closed to vehicles until a dozer clears it; laid as crate stacks until
  there is wreck art), or sandbag positions facing a direction.
- **Ridge.** High ground the works stand on: a band along a path
  (`elevation-nouns.md`, Ridges), raised at the start of the works phase,
  before any line is laid, so a line resolved along it is dug on its crest. It
  breaks into a pass wherever a road, a place, a structure or a site crosses
  it, and lawn painted on open country does not break it. The access routes
  the landscape keeps between places do not break it either: its own access
  proof stands for them, and a route along a line would break every stretch.
  Reported by its ramps' feet. Elevation is stamped once, so every ridge goes
  up together: the layout's ridges and the landscape's own (its ridges, held
  ground and valley sides) in one plan, with the landscape's tracks and brook
  banks kept for them; a band held for a layout Ridge is raised once.
  Plateaus are planned apart, so a plan asking for plateaus beside a Ridge is
  refused at generation rather than losing them silently. The Cordon's line
  and the heart's forward line stand on one. A Ridge may carry **terraces**
  (`elevation-nouns.md`, Terraces): places' boxes stood on its crest, their
  roads ramped down, which the band runs on into rather than breaking at.
  Each is reported as a ground piece of its own under its name (its box's
  corners, its ramps' feet as the ways up), and a required one that cannot
  stand fails the layout check like any required piece. A landscape's knolls
  (`elevation-nouns.md`, Knolls) are terraces raised in the same stamp and
  reported the same way, and a plan with a knoll runs the works phase even
  when its layout is empty. A deferred piece can ask whether the landscape
  held a cell before the places grew (`LayoutGround.isHeld`), to keep a site
  off a ridge it would otherwise break.
- **Deferred.** Pieces that can only be stated once the ground exists — a line
  from one place's claimed edge to the next's. A deferred piece resolves at
  its **phase** against a read-only view of the grown ground (claims, roads,
  walkability, and a seed per place, never the generation random stream), and
  what it returns is laid under the returned pieces' own names. A site it
  returns holds its ground against the works resolved with it.

## When things are laid

- **Before growth.** A site stated in the layout itself is known before
  anything grows: no road arm, claimed place, field or farm grows onto it.
- **As the places grow.** Once the places have claimed their ground, their
  ways out are cut and the country is surveyed, roads are carved.
- **Structures phase.** Once the places are built and before the map is
  finished, structures are raised (deferred pieces of this phase resolve
  first), so the finishing stages treat their buildings as buildings.
- **Works phase.** Once the places, roads and lanes stand and before the
  landscape grows round them, deferred pieces resolve, ridges are raised, and
  every line, block and site is laid. The landscape then keeps clear of what was laid, except ground held
  for authored terrain.
- A map whose plan states no layout runs none of this and is generated exactly
  as before.

## Laws

- **Placements yield.** A work goes only on open ground that is not road
  (road includes the welds and roads the places' network holds in open
  country): a line through a town is broken by its buildings and reported
  **partial**.
- **Never sealed.** Works never cut the walkable ground apart. Wire, nests,
  barricades and blocks that would are not laid, and say so. A line that closes ground
  leaves at least one way through.
- **Reported by name.** Every piece comes back as laid, partial or refused,
  with its cells, its ways through and its nests, on the finished map. A later
  objective asks "is this here" by name.
- **One check.** Whether a laid layout keeps its promises (required pieces
  stand, every gap is open, no line seals) is one check that a mission's
  acceptance and the story-map pools both read.

## Not yet

A block that closes a road a mission's vehicles must use is a set piece for
a beat to lay when it runs, not one to lay at generation: until a beat can
clear it, the map only holds its ground. A road changes what grows round
it, so on a vetted map a mission may have to leave an optional road out to
keep the map (the Cordon does, and reports it refused). A nest is published as a light defence post; a mission that
does not seat map posts seats its own guns in the nests it is reported (the
Cordon's garrison does).

# Mission layout

Status: ACTIVE — lines of works, sites, roads, structures, blocks, props and ridges are
built.

Written: 2026-10-23

Updated: 2026-10-31 — nothing but paving stands on a way; a layout may keep a column clearance either side of the columns' ways and break its lines at tracks; an airfield is sited beside a way with a hamlet round it; a layout may carry structures placed by hand. Earlier, 2026-10-25: a road resolved with the works follows the standing streets.

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
  ground. A road that reaches the ground of the walled square place it leads
  to is joined there, since that place's wall opens onto it or its arms reach
  out to it: in a town no other road need run near (a town's Cordon supply
  roads touched nothing at either end and were all thrown away). A road a
  deferred piece returns in the works phase is not carved: it is the way along
  the **streets already standing** between its ends, for ground with no open
  country to carve across (a city). The way keeps to the widest street it can
  (each step dearer the narrower the street, dearest off the road), never
  enters a building or a site, and is **cleared**: every prop with cover
  standing wholly on the road beside it is taken off, except on its end places'
  own ground. The works laid after it keep off its cells, so no barricade
  closes it.
- **Structure.** Something raised at a place the mission chose, by kind: a
  farmstead (a farmhouse and a barn on a yard), a granary (a long store on a
  yard), an ammo dump (a sandbag ring round stacked crates), an observation
  post (a small sandbag ring), a square (open paving with a marker in the
  middle), an airfield (an allied base lot, `AirbaseLot.BASE`, and its verge, its strip on the
  side its turn names, its hardstands an allied field's and no node to take;
  laid from its own draw, with a worked track from its gate to the way it
  stands beside), crew quarters (a prefab bunkhouse on a yard), a store shed
  (a short prefab module on a yard) and a pump hut (a one-room hut on a yard).
  Its whole footprint must be open ground nobody built on, or it is
  refused rather than squeezed. Nothing but a square stands on a way, the
  farmland's tracks and the landscape's lane tracks included; an airfield and
  its hamlet's own kinds also keep out of the column clearance (Laws, the
  ways' keeping). Buildings come from their authored plans, so
  they are buildings like any other. The square's marker stands in for a bell
  until there is bell art.
- **Airfield siting** (`AirfieldSiting`, mission-neutral). A mission that
  wants an airfield names the ground and what to keep clear; the field is
  sited on it never on or across a way nor in the column clearance, beside a
  way: its gate (on the apron side, away from the strip) opens onto a road or
  track within fourteen cells, else a short side's gate does, else a dogleg
  from the apron gate (out past the fence, along past the field's end, back
  toward the strip's side) reaches one, joined by a short access track that
  never enters the clearance, so it never joins a column's way where the
  column forms up. Every footprint cell by cell is tried. Unfarmed ground is
  preferred, then a short track, then nearness to where it was wanted; its
  strip runs along x and faces whichever way puts the apron toward the way.
  Round it grows a **hamlet** of up to five buildings (crew quarters, a store
  shed, a farmstead, a pump hut, a granary) on its flanks within its own
  depth, clustered toward the gate, doors toward the way, off the field, its
  access track, the ways, farmed ground, and the strip's rows for thirty
  cells past either end; fewer or none where there is no room.
- **Placed by hand.** A layout may carry structures a person put down for one
  map (`MissionLayout.placed`, from a story map's patch: `story-map-pools.md`,
  The patch). A deferred piece resolving a structure of that name takes the
  placed one instead of siting it.
- **Block.** Cells closed or fortified by hand: a barricade (hedgehogs and
  crates, which fire breaks and a dozer blade pushes), a wreck across a road
  (closed to vehicles until a dozer clears it; laid as crate stacks until
  there is wreck art), or sandbag positions facing a direction.
- **Props.** Props set down by hand, each a doodad by id at a cell and turn,
  laid with the works through the ordinary prop placement, which skips one
  whose ground is not open. One piece, laid when any prop stood and reporting
  how many did: set dressing that says what a place was (a freight yard's
  containers, a lab's tank racks), never a building or cover a beat relies on.
  A tree's canopy is set down through the forest brush, as the woods are: it
  is planted, so paving under it is lifted to a grass bed, and it is refused
  over a road, a building or anything already standing.
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
- **Nothing built on a way.** No structure but paving (a square) stands on a
  way: a road, the farmland's roads and tracks, or a landscape's lane track.
  The laid layout reports every structure or works cell on a way, by piece,
  and every cell of the farmland's own farms (`farmland-farmstead-N`) on one
  (`LaidLayout.onWays`), so a pool check can read it. The reading counts ways
  as drawn (`LayoutGround.isUsedWay`): not the network's border along the map's
  edge, nor a reservation growth left in the surveyed country.
- **The ways' keeping** (`MissionLayout.Ways`). A layout may keep a **column
  band**: so many cells either side of every column's way (a road other than
  the map's own edge, a country or farm road of the farmland, a lane track;
  `LayoutGround.isColumnWay`), the **column clearance**
  (`LayoutGround.inColumnClearance`), which an airfield, its hamlet and its
  access track keep out of, and which a mission's own siting reads, so columns
  form up at the way's foot and drive past with nothing built beside it. It
  may also have its lines **break at every way**, the tracks as well as the
  roads. A layout that keeps neither is laid as before.
- **Reported by name.** Every piece comes back as laid, partial or refused,
  with its cells, its ways through and its nests, on the finished map; a
  refused structure names the first cell that refused it and why. A later
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

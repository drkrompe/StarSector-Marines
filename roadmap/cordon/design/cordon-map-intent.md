# The Cordon: maps with intent

Status: DRAFT. Direction for the Cordon's controlled maps (step 2). Built: the layout machinery, the picket courses as fieldworks with the garrison's guns in their nests, the switch line, the supply roads (a city's along its streets), the Road's second road, a placeholder eruption site (a city's cleared lot), the city's tenement and observation posts, the ridge the line stands on with every redoubt on its crest (rural and town), and the beats as optional objectives over the set pieces.

Written: 2026-10-23

Updated: 2026-10-31 — the allies' air base, a field lot held in a rear corner. Earlier, 2026-10-25: a city's set pieces: the tenement, the posts in buildings, the cleared lot, the supply streets.

`cordon-nouns.md` is the model. Today the Cordon's map is generated country
with four redoubts and a base dropped into fixed bands. Nothing on it says
that a field commander has held this line for weeks, chosen where to stand,
and dug in. This doc says what the mission places and why; generation only
grows the country around it.

## The law: the mission places its structures

The same law as the heart's (`heart-map-intent.md`; owner, 2026-10-23):
structure placement belongs to the mission, not to map generation. The
Cordon's **layout** lays every structure that the line, its logistics or a
story beat needs, at a place the mission chose, over the grown country.

- **Shared machinery.** The layout is mission-neutral and lives in map
  generation (`MissionLayout`, `mission-layout.md`). It is built once for the Cordon and the heart,
  with the heart's authored ground beside it. Placements are named and
  reported, so an optional objective's "fits this map" is a lookup
  (*Order of work*, step 4).
- **Placed, then proven.** `storyMapPools` checks every layout on every pooled
  seed at every density: each placement on walkable ground, and the line,
  base and corridor still reachable.

## Where a field commander stands

A line drawn across country by someone who has had weeks to choose:

- **The line on the high ground.** The redoubts stand on the rise the
  country offers nearest the bloom: a low ridge across the map at the line's
  depth (authored ground), with the front falling away below it. It is a berm
  the colony graded years ago, regular because it was built: straight runs,
  faces drawn as a turfed bank, cut square at every pass (owner, 2026-10-25;
  `elevation-nouns.md`, Relief). Redoubts sit
  on its crest at the roads, because roads are how the swarm comes and how
  the allies move.
- **Redoubts where roads cross.** Each redoubt guards a road through the line.
  The gaps between them are covered from both sides, never wider than two
  redoubts' guns can reach. Measured (`CordonGapLaw`) gun to gun along the
  line, a redoubt's towers through the picket nests to the next redoubt's,
  against twice the heavy machine gun's range plus its spread and splash at
  each end (53.6 cells). Redoubts stand a quarter of the map apart, a hundred
  and more cells, so the towers alone never meet and the nests carry the law.
  The owner closed it with more picket nests (2026-10-24): once the redoubts
  are built, every pair still breaking it gets nests along the line until no
  step is over the law, on the crest in open country and at street mouths in
  a town or a city, where a gun sees down the street rather than into a wall
  (`cordon-nouns.md`, Picket line). It is acceptance: a map whose line still
  breaks it is not a Cordon map.
- **A picket line between them.** A trench with wire in front runs redoubt to
  redoubt along the crest, broken where roads pass (the gaps). Nests stand at
  each end of a course, and more wherever the gap law needs one.
- **A switch line behind.** A second, shorter trench behind the middle of the
  line, unmanned at the start: where a garrison falls back to (beat L3).
- **Supply roads.** A road from the logistics base to the rear gate of each
  redoubt (beat L2). The base stands on the main road behind the middle, as it
  does now.
- **The air base.** The allies' airbase lot (`mapgen-nouns.md`, airbase lot),
  a field: a strip, three berths and two sheds, room for the wing's four
  airframes, where a station's sixty cells would crowd the rear hamlets. It is
  an `Airfield` piece held from before the places grow, so nothing grows onto
  it, in the rear corner on the side away from the front's installation, four
  cells in from the rear edge and six from the side, clear across the rear
  from the relief corridor and the landing field. Its strip lies along the edge
  toward the front. It is allied (`LandingPad.Purpose.ALLIED_AIRFIELD`): no
  compound for the swarm to take, and no pad the company lands on
  (`cordon-nouns.md`, Air base).
- **Observation.** A sandbagged post forward of the line on each side's high
  point; in a city, the biggest building in front of each flank redoubt.

## By density

The same works at each density (`cordon-nouns.md`, Density). Density changes
what they are built from. The owner asked for a mix rather than one look per
density (2026-10-24): the compound is the professional post, the farm, school,
walled-up block and tenement are troops making do, and a line that mixes them
reads as a front thrown up in a hurry. Each is picked per redoubt from the seed
(`cordon-nouns.md`, Construction):

| | Rural | Town | Urban |
| --- | --- | --- | --- |
| Redoubt | Earth-and-timber work on the crest at a crossroads | A mix: the compound, a fortified farm, a fortified school | A mix: the compound, the buildings round a crossroads walled up, a fortified tenement |
| Picket line | Trench and wire across fields | Trench across fields, barricades across streets | Barricaded street mouths and loopholed houses; no trench |
| Switch line | A trench behind the middle | A trench and a barricaded street | A second row of barricaded blocks |
| Supply road | Farm tracks to each rear gate | The town's streets | Cleared streets, the boulevard before a back street |
| Set pieces | Farmstead, posts in sandbag rings, eruption site in open ground | As rural | A tenement, posts in the biggest buildings, a cleared lot |
| Base | A fenced yard on the main road | A fenced yard on the main road, or the town's goods yard | One of the fenced yard and shed, a warehouse depot or a car-park depot, the depots with open-air mech gantries |

## The beats' set pieces

Every beat the owner picked (all twelve) declares its set piece. The layout
reserves it on every map that can hold it; the objective roll then decides
which beats run.

| Beat | Set piece | Where |
| --- | --- | --- |
| L1 Stragglers | A farmstead: a house, a barn and a yard; in a city a tenement | In the near front between two redoubts, with a lane back to the gap |
| L2 The guns run dry | An ammo dump in the base, and supply roads (in a city, cleared streets) | Base to each rear gate |
| L3 The switch line | The switch line | Behind the middle redoubt that has the most room |
| L4 A bore behind the line | An eruption site: open ground, no structure; in a city a cleared lot | Between the line and the base, off the supply roads |
| L5 The last hamlet | A meeting point: a square with a bell | In the rear hamlet farthest from the base |
| L6 Burn the granary | A granary: a long store building | In the colony, toward the line |
| R1 Clear the road | A wreck across the corridor, and a dozer at the base | On the corridor in the suburb, past the gap, at the nearest cell where the street is narrow enough for the wreck to close it kerb to kerb (thirteen cells), never across a square |
| R2 A truck goes down | No new structure (the corridor's built stretch) | The corridor through the suburb |
| R3 The last loading | The compound's gate square, holdable | At the compound |
| R4 The militia ride out | No new structure (the compound's militia) | At the compound |
| R5 A maw on the road | A second drivable road through the line | Through redoubt 2's gap, rejoining the corridor |
| R6 The gap falls | Sandbag positions either side of the road in the gap | In the gap |

Built: the farmstead (L1), the ammo dump and supply roads (L2), the switch
line (L3), the eruption site (L4), the square, its bell a placeholder marker
(L5), the granary (L6), the second road (R5) and the gap's sandbag positions
(R6), and an observation post forward of each flank redoubt. A city leaves
no open country to raise them on, so its pieces are what stands there
(`CordonCityPieces`, laid with the works): L1's **tenement** is the house of
flats nearest the middle of the near front between two redoubts (the middle
pair first) from which a walk out of its doors reaches the line between them;
each **post** is the biggest building whose middle lies where the rural post
would stand (the map records no height, so the biggest block on the street
stands for the tallest), its front windows sandbagged from inside; L4's site is
a **cleared lot**, open ground inside the city's blocks (a yard, a square, a
park's lawn) off the streets and off the works' own ground; and the **supply
roads** are the city's streets, followed from the base's gate to each rear gate,
the widest first and cleared of the props in their carriageway
(`mission-layout.md`, Road). On every pooled city map the tenement, both posts
and all four supply roads stand; the lot stands on all but two, which have no
square of open ground big enough behind the line. R1's
wreck has its ground held but is not laid: laid at generation it closed the
road to every convoy for the whole battle (`cordon-road` evidence,
2026-10-23: a convoy stood at it from minute five to the end; one pool map in
twelve held with it laid, four with its ground only held), and nothing
clears it until the base fields a dozer. The beat lays
it at setup only when R1 is rolled, and fields the dozer that clears it; the
corridor is proved through its ground. R2 to R4 need no new structure.

## Order of work

1. The layout machinery, and the Cordon's line, picket courses, supply roads
   and base placed through it. Built: the machinery (lines, sites, deferred
   pieces, the shared check), the picket courses (a two-deep trench, parapet,
   wire and turned earth in the country, barricaded street mouths in a town,
   a street line along the house fronts in a city; allied guns in the nests),
   the switch line, the supply roads (base gate to each redoubt's rear gate,
   across open country; a town's or city's streets where its ground is in the
   way), the Road's second road through redoubt 2's gap, and the L4 eruption
   site as a placeholder open site. An optional road that would cost a pooled
   map its Cordon is left out of that map and reported. A city's supply roads
   are followed along its streets with the works.
2. The authored ground (the ridge), from the heart's side of the machinery.
   Built (`cordon-nouns.md`, The map): a layout ridge raised before the works,
   so the picket lines are dug on its crest, and every redoubt stood on the
   crest at its road as a terrace (`elevation-nouns.md`, Terraces), its roads
   ramped down to the rear and the front falling away. A map on which a
   redoubt cannot stand so is not a Cordon map. Rural and town; an urban line
   has none.
3. The set pieces, one per beat, each checked on the pools. Built; R1's
   wreck is laid by its beat (above).
4. Then the optional-objective mechanism, shared with the heart, which rolls
   beats against what the layout placed. Built: the mechanism
   (`optional-objectives.md`) and the Cordon's pool and runtimes
   (`cordon-nouns.md`, Optional objectives).

## Open

- The split of the machinery between the Cordon and the heart (proposed: the
  Cordon builds placements, lines and roads; the heart builds authored
  ground).
- The `place` op on a pooled seed's patch (`story-map-pools.md`).

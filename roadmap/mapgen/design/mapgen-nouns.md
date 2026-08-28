# Map generation nouns

Status: ACTIVE — the recipe/context/stage model is shipped; bounded content expansion remains open.

Written: 2026-08-23

Updated: 2026-08-28 — added the Conquest fortress ward, family-neutral room
fittings, and the obligations a replacing stage carries; shipped
shared-edge windows for Conquest bunkers and ordinary building shells,
widened compound firing aprons while preserving functional parcel members,
added role-readable compound dressing and authored multi-cell civic room
programs with wall- and aisle-aware fixture rotation, guaranteed standable
interior anchors, and made pocket sealing yield to a compound.

Map generation turns a deterministic request into a validated tactical world. It
owns authored spatial intent; runtime systems own subsequent mutation and play.

## Generation request and result

A **generation request** selects a seed, a map family, and an optional target
profile. The seed is part of the contract: identical request inputs and recipe
composition produce the same map. A stage must not add an RNG draw for a silent
or absent feature, and unordered iteration must never decide an outcome.

A **map result** is the finished tactical contract: terrain and movement
topology, reachable spawns, points of interest, tactical nodes, buildings,
defense posts, landing pads, decorative placements, and the authored road or
biome data that battle consumers need. Recipe-internal analysis such as station
graphs and tactical-region maps may remain in the context until an external
consumer justifies result plumbing. Final validation protects connectivity,
deployability, doors, and other cross-system assumptions. Decoration may
express a place but cannot secretly create its movement, cover, or wall rules.

## Recipe, context, and stage

A **recipe** is a named, immutable arrangement of stages for one map family.
A **stage** is a stateless transformation of a fresh **generation context**.
The context is the run-local mutable spine: it contains the seed-owned random
stream, spatial work products, and typed shared facts. Recipes describe the
pipeline at a high level; a stage owns its own algorithm and may publish only
facts that later stages actually need. This makes a new map family an explicit
composition choice rather than a widening conditional inside the generator.

## Tactical space

The tile grid and its topology are tactical authority. A cell owns standability;
a shared cardinal edge owns the transition between two standable cells. A thin
edge barrier may therefore divide adjacent usable cells without consuming
either cell, and zone connectivity must honor that closed transition. Open-city
generation starts from traversable terrain and adds structures; station
generation starts from solid space and carves a reachable interior. A city road
graph is planning and visual structure, not the authority for unit movement. A
station graph is the authored relationship between rooms and corridors, used to
reason about reachability and roles without making incidental tile shapes into
API.

Edge passability is navigation topology, not by itself a structural wall. A
production thin barrier must publish one authored identity from which movement,
ballistic interception, visibility, directional cover, durability, and
presentation derive. Closing an edge only for A* would create a secret obstacle;
drawing a narrow wall without closing its edge would create dishonest scenery.
Runtime topology retains the permissive-mutation law: destruction may open an
authored edge, while construction that closes an edge under existing paths is a
separate future problem.

A **shared-edge barrier** is that authored identity. It is stored once on a
canonical east- or north-facing edge, while reciprocal lookup from either
adjacent cell returns the same feature. Both cells must already be walkable and
the transition initially open: the feature divides usable space instead of
smuggling a cell wall into edge metadata. Its profile defines sight and direct-
projectile policy, directional cover and vertical catch, structure, and
appearance together. Canonicalization also retains the adjacent authoring cell
whose structure owns the feature. That structural side orients presentation
today and can resolve through the map's building registry for later destruction
objectives without turning visual thickness into collision. The first profile
is a transparent firing **window**: it
blocks movement, passes sight and direct rounds, supplies low cover to both
adjacent positions, and can be broken by structural blast damage. Destruction
removes the identity and cover, then opens the reciprocal edge through the map
editor so zones, retained paths, vantage caches, and the region mesh advance
together at the normal topology flush. Runtime construction remains excluded.
Diagonal traversal consumes the neighboring cardinal transitions as well, so a
unit cannot slip around a closed barrier endpoint in one diagonal step.
Arbitrary angled or within-cell dividers remain outside this model: a shape
that partitions one cell into disconnected interiors would require explicit
subcell regions or a finer low-level lattice, not more meanings on a cell edge.

The greedy navigation mesh is derived runtime structure, not generated map
authority. It combines compatible walkable cells into deterministic rectangular
regions, preserves doorways and closed edges as seams, and publishes passable
boundary intervals between regions. Generation and runtime destruction mutate
cells or shared edges; the ordinary navigation-topology flush then rebuilds the
mesh alongside zones and geometry-dependent caches. A generator must never
author a region directly or depend on a particular greedy decomposition.

**Room purpose** is a carve-time semantic label, such as a room, corridor, or
special facility. Consumers ask the purpose instead of rediscovering regions
from coordinates. It must remain an authored fact, not a post-hoc convention.

**Tactical regions** express larger-scale city texture and positional reasoning
such as overwatch; they do not replace tile topology. Buildings and compounds
are coherent tactical places with circulation, a readable identity, and a
reason for their interior geometry.

A tactical node's **anchor** is the stable identity of a place and may sit on
an intentionally non-walkable wall or turret mount. An authored **stand
position** is different: it is an exact walkable member cell belonging to that
place, such as the protected cell behind a bunker firing aperture. Consumers
prefer authored stand positions before deriving nearby cells, and later
generation stages must not overwrite their enclosing authored footprint.
Because an anchor carries identity rather than standability, a consumer that
needs somewhere to stand — or a room to resolve — derives that cell from the
place's footprint instead of reading it off the anchor.

A late stamper that overwrites earlier structure can strand walkable ground
behind it. Such an **orphaned pocket** is normally filled in solid, because a
room with no way in is scenery, and cutting it a new entrance would scatter
openings through authored wall geometry. A pocket holding a compound is the
exception: it is breached open, because a compound is a mission's win condition
and burying one does not cost a room, it makes the mission unwinnable. The
general law is that structure yields to a place the mission depends on, never
the other way round — and that declining to bury such a place is not enough on
its own, since ground nobody can walk to is as useless as ground that is gone.
Pocket reachability follows shared-edge passability, not cell adjacency: a
window recess is not an entrance. Sealing removes any sparse barrier identity
touching the discarded cells, while a required breach destroys any barrier it
crosses before publishing the open route.

A point of interest's **interior anchor** is the opposite promise: the cell a
mission objective is placed on, and therefore standable whenever the footprint
encloses a standable cell. A footprint carved solid encloses none, and its
interior anchor falls back to the exterior interaction anchor. The guarantee
cannot hold where the anchor is authored, because later stages keep changing
the map underneath it — furnishing drops a fixture on the cell, wall stampers
paint over the footprint. It is therefore reconciled once against the finished
grid, after the last stage to touch it. An anchor that fails the guarantee does
not merely look wrong: mission layouts filter candidate sites on it, and
room-scoped objectives resolve no room at all from a blocked or doorway cell.

An ordinary compound's wings are sized by the partition rather than by their
purpose, and a purpose that needs a minimum footprint has no way to ask for one.
`compound-programs.md` holds the direction that inverts this — a program that
sizes the place, as a deck's does — with the measurement that motivates it and
the parts of the deck model that deliberately do not transfer. The Conquest
fortress ward is built that way already; every other compound is not.

Parcel ownership is established before content fills. A filler may own one leaf
or an already-claimed compound, but it must not infer a multi-leaf building by
overwriting roads or neighboring fills after dispatch. Cross-leaf structures
therefore require an explicit planning stage that publishes the claimed
footprint and its circulation obligations.

Ordinary hollow building shells place shared-edge windows on facade runs
belonging to tactically usable rooms. The former facade wall cell becomes a
walkable, building-hinted firing recess; only its outside transition is closed,
and that recess is retained as the barrier's structural-owner side. Secured
storage and infrastructure rooms may deliberately remain opaque. Compound
perimeter walls remain thick structural cells and place apertures in adjacent
two-cell pairs only on sufficiently long straight runs with walkable firing
space on both sides. Compound buildings retain a two-cell internal apron to
support oblique firing angles, while gates, hardpoints, corners, and reserved
road circulation remain clear. The two models are intentional: ordinary facade
glass is a narrow boundary with presentation projected inward, while a compound
aperture belongs to a heavy wall cell. The apron expands the minimum parcel
claimed by a walled compound; it may not reduce an intended member below a
hollow shell with a standable interior and doorway.
Compound dressing is clustered by role and place: logistics props explain each
military wing, while planters and seating define shared domestic space. Hard
fixtures and visual-only clutter both preserve gates, building thresholds,
two-cell circulation, road reservations, and final walkable connectivity.
Purpose-built interiors express their function through grouped equipment rather
than a single symbolic prop: command rooms organize console banks around a
planning focus, server rooms organize repeated racks around a service aisle,
and both retain doorway, firing-position, and room-connectivity obligations.
Civic headquarters follow the same authored-program rule as ship mech labs:
their two-cell spine, opposing entrances, room depths, and fixture groups are
designed as one floor-plan family rather than discovered by random door and
prop placement. A qualifying headquarters lot is at least 15 by 13 cells. Each
office must fit its workstation-and-records group, each reception wing its
counter, the conference room its central table, and the server room repeated
racks around a service aisle. Multi-cell fixtures rotate at placement time so
their backs meet supporting walls, their working faces meet open room cells,
and centerpieces are centered by their complete footprint. On the compact
footprint, office facade windows yield to that required room capacity;
reception and conference facades retain the building's firing apertures.

## City, station, and ship families

City recipes compose a trunk, parcels, circulation, zoning, purpose-specific
fillers, tactical linking, and final validation. The conquest recipe also
places its fortress and defense structure, while legacy/preview city generation
can use the same spatial vocabulary without importing campaign concerns.

A **ward** is the exception to parcel-first fill, and the conquest fortress is
the one place that has it. Rather than being filled leaf by leaf and then walled,
its band is laid out from a program of authored building footprints, packed into
depth bands measured from the attacker's approach, furnished by the shared room
fittings, and opened as parade ground wherever packing leaves ground over — and
the wall is drawn around that result. The order is the point: a wall stamped
first can only ever enclose whatever the fill happened to leave, however good the
wall is. `compound-programs.md` owns the model, the measurements behind its
sizing rules, and what remains before an ordinary compound can be built the same
way.

**Room fittings are family-neutral.** A furnishable room is a floor, a pose, its
doors, and its purpose; a deck compartment and a packed fortress building both
present one, and the same authored fitting furnishes either. A machine berth is
likewise a map fact rather than a shipboard one — a fortress vehicle shed
publishes the same berths a mech bay does, and what occupies one remains the
host's decision from a roster.

A stage that replaces what an earlier stage built owns everything the earlier one
recorded, not only what it drew: decorative placements, points of interest,
tactical nodes, and authored shared-edge identities all belong to the ground being
replaced. Because an edge carries exactly one authored identity, a leftover
window is not merely scenery whose building is gone — it is an edge the next
stage cannot author on.

Station recipes compose a chosen layout, partitioning, room carving, corridors,
spawn placement, station-topology analysis, tactical linking, and finalization.
The concentric, diamond, and partitioned interior layouts are alternative
spatial premises, not separate game modes. Stations must preserve a legible
entry-to-objective structure while allowing deliberately chosen loops and
hardpoints.

Ship recipes are a third interior premise. Where a station organizes space
around a core — radial depth, mirrored geometry, converging ports — a ship deck
organizes it around a longitudinal axis: an elongated, deliberately asymmetric
hull whose beam varies along its length, a fore-aft spine, ordered transverse
bulkheads, and an assault gradient that runs along the axis from a breach point
rather than inward from a perimeter. The layout-neutral topology tier is shared
with stations; the ring, core, and port annotations are not. `ship-interiors-nouns.md`
owns that family's model, its facility compartments, and its boundaries.

## Defense-post layouts

A **defense-post layout** is a bounded, anchor-relative arrangement of occupied
cells. Barrier cells own their appearance and outward facing; pad cells may host
a referenced turret structure or the distinct drone-hub occupant. Layouts own
initial geometry only. Defense-post tiers continue to own garrison size,
tactical priority, patrol and budget policy, while turret catalogs own the
behavior and durability of every referenced structure.

The Conquest fortress's open-backed forward bunkers use the same separation of
place from position: a blocked center turret remains the bunker anchor, while
two attacker-facing shared-edge windows bound the garrison's authored member
positions. Each exterior neighbor remains walkable and a one-cell front-and-
flank halo proves a legal route around the intact pane. The road reservation,
halo, and reachable rear approach are placement requirements, so a bunker is
omitted rather than publishing an isolated fighting cell.

The layout catalog loads after turret structures and rejects unknown structure
ids, duplicate or out-of-bounds cells, turrets placed off ordinary pads, and
invalid tier composition. Generation selects among the layouts authored for a
tier through its request-owned random stream. A tier with one layout consumes
no selection draw; a tier with several variants consumes exactly one, preserving
determinism as fixed stamps become data. Connectivity checks and tactical-node
bounds consume the selected layout's actual occupied cells and declared bounds,
not a parallel hard-coded silhouette.

## Target profile and economic identity

A **target profile** carries campaign-resolved facts into generation without
bringing campaign types into the generator. Its economic-function mix is a
stable vocabulary for choosing district character; an empty/neutral mix remains
the historical neutral output. Structural geography still owns its bands: an
economy signal may sharpen compatible city content but cannot erase the purpose
of a harbor, perimeter, or fortress.

An **economic district** earns its identity through navigable geometry, cover,
destruction, and sightlines. Spaceport aprons exemplify this: they are open
tarmac with sparse, destructible hard cover and a reachable control hardpoint,
not a visual theme. New district types must state their tactical promise and
prove that the whole result remains connected and deployable.

Generator-published tactical facts need a named consumer. Do not widen
`MapResult` with preview-only or speculative analysis artifacts; an in-pipeline
consumer reads the context, while an external consumer justifies the result
contract it needs.

Target faction identity may select an authored treatment for a facility only
through a named content consumer. Such a treatment can express materials,
footprint composition, cover, and approach character, but it may not change the
facility's owning battle semantics or bypass connectivity/deployment validation.
`target-faction-facility-treatment.md` owns the first bounded consumer over the
hard-installation family.

## Boundaries

Generation authors the initial world. The desktop emplacement editor stages and
validates layout data, but does not decide runtime placement or become a second
geometry authority. Map-editor or battle services may mutate
topology later, but they must not be smuggled into recipe logic. Content-specific
fillers own their geometry; broad generator facades own recipe selection and
result assembly. Current active work is indexed in `stories.md`; completed
historical slices are recorded in `shipped.md`.

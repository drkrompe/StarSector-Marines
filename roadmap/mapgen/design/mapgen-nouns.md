# Map generation nouns

Status: ACTIVE — the recipe/context/stage model is shipped; bounded content expansion remains open.

Written: 2026-08-23

Updated: 2026-08-24 — named target-faction facility treatment as a bounded content consumer over shared installation semantics.

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

The tile grid and its topology are tactical authority. Open-city generation
starts from traversable terrain and adds structures; station generation starts
from solid space and carves a reachable interior. A city road graph is planning
and visual structure, not the authority for unit movement. A station graph is
the authored relationship between rooms and corridors, used to reason about
reachability and roles without making incidental tile shapes into API.

**Room purpose** is a carve-time semantic label, such as a room, corridor, or
special facility. Consumers ask the purpose instead of rediscovering regions
from coordinates. It must remain an authored fact, not a post-hoc convention.

**Tactical regions** express larger-scale city texture and positional reasoning
such as overwatch; they do not replace tile topology. Buildings and compounds
are coherent tactical places with circulation, a readable identity, and a
reason for their interior geometry.

Parcel ownership is established before content fills. A filler may own one leaf
or an already-claimed compound, but it must not infer a multi-leaf building by
overwriting roads or neighboring fills after dispatch. Cross-leaf structures
therefore require an explicit planning stage that publishes the claimed
footprint and its circulation obligations.

## City and station families

City recipes compose a trunk, parcels, circulation, zoning, purpose-specific
fillers, tactical linking, and final validation. The conquest recipe also
places its fortress and defense structure, while legacy/preview city generation
can use the same spatial vocabulary without importing campaign concerns.

Station recipes compose a chosen layout, partitioning, room carving, corridors,
spawn placement, station-topology analysis, tactical linking, and finalization.
The concentric, diamond, and partitioned interior layouts are alternative
spatial premises, not separate game modes. Stations must preserve a legible
entry-to-objective structure while allowing deliberately chosen loops and
hardpoints.

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

Generation authors the initial world. Map-editor or battle services may mutate
topology later, but they must not be smuggled into recipe logic. Content-specific
fillers own their geometry; broad generator facades own recipe selection and
result assembly. Current active work is indexed in `stories.md`; completed
historical slices are recorded in `shipped.md`.

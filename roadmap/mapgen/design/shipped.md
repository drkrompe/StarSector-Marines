# Map generation shipped ledger

Status: SHIPPED — retired implementation stories are folded into the noun model.

Written: 2026-08-23

| Retired document | Shipped | Evidence | Folded into |
|---|---|---|---|
| `composable-pipeline.md` | 2026-05-29–2026-05-30 | `5e5ae915`, `65c5686b`, `8666b8f0`, `7016b8e1` | `mapgen-nouns.md` |
| `civic-headquarters.md` | 2026-08-19 | `4d929309` | `mapgen-nouns.md` |
| `commercial-compound.md` | 2026-08-19 | `7bb9b4f2` | `mapgen-nouns.md` |
| `dense-urban-rows.md` | 2026-08-19 | `4f04d47d` | `mapgen-nouns.md` |
| `gen-context.md` | 2026-05-29 | `5e5ae915` | `mapgen-nouns.md` |
| `gen-recipe.md` | 2026-05-30 | `7016b8e1` | `mapgen-nouns.md` |
| `gen-stages.md` | 2026-05-30 | `65c5686b`, `8666b8f0` | `mapgen-nouns.md` |
| `industrial-compound.md` | 2026-08-19 | `7296aeec` | `mapgen-nouns.md` |
| `industrial-facilities.md` | 2026-08-19 | `e8ad9c4b` | `mapgen-nouns.md` |
| `medical-campus.md` | 2026-08-19 | `adf608b4` | `mapgen-nouns.md` |
| `military-compound-interiors.md` | 2026-08-19 | `fa0533ee` | `mapgen-nouns.md` |
| `multicell-residential-doodads.md` | 2026-08-19 | `fc212e56` | `mapgen-nouns.md` |
| `oriented-residential-furniture.md` | 2026-08-19 | `8e7a4815` | `mapgen-nouns.md` |
| `residential-courtyard-compound.md` | 2026-08-19 | `444e0f97` | `mapgen-nouns.md` |
| `room-purpose-refactor.md` | 2026-05-22–2026-05-23 | `82c76a9e`, `d3f659d6`, `042d0842`, `ee55eb0f`, `b8b7b9db` | `mapgen-nouns.md` |
| `standalone-apartments.md` | 2026-08-19 | `d4595b97` | `mapgen-nouns.md` |
| `station-concentric-rings.md` | 2026-06-02 | `c4471040` | `mapgen-nouns.md` |
| `station-diamond.md` | 2026-06-02 | `f04c2d51` | `mapgen-nouns.md` |
| `station-interiors-slice-1.md` | 2026-06-02 | `aae42444` | `mapgen-nouns.md` |
| `station-topology-roles.md` | 2026-06-02 | `6a07e8f6` | `mapgen-nouns.md` |
| `tactical-commercial-interiors.md` | 2026-08-19 | `d14ce6a8` | `mapgen-nouns.md` |
| `slice-0-substrate.md` | 2026-06-01 | `f2ade6d1`, `f6da21a9` | `mapgen-nouns.md` |
| `slice-1-spaceport.md` | 2026-06-01; expanded 2026-08-12 | `0c4caf52`, `6810ed1b`, `d17554b1`, `d765698a`, `a1701ffa`, `09bc9794` | `mapgen-nouns.md` |
| `structural-taxonomy.md` | 2026-06-01 | `537ca031`, `3426109f`, `9320da74` | `mapgen-nouns.md` |
| `corridors-first-class.md` | 2026-06-02 | `aae42444`, `6a07e8f6`, `c4471040`, `f04c2d51` | `mapgen-nouns.md` |
| `station-interior-fills.md` | 2026-06-02 | `aae42444` | `mapgen-nouns.md` |
| `thin-edge-barriers.md` | 2026-08-28 | this commit | `mapgen-nouns.md` — canonical shared-edge identity, transparent window profile, Conquest bunker consumer, and reactive destruction; `continuous-positions-nouns.md`, `ballistics-nouns.md`, `combat-durability-nouns.md`, and `battle-render-nouns.md` — routing, tracing, durability, and presentation boundaries |
| `garrison-airfield-reinforcement.md` | 2026-08-28 | this commit | `air-nouns.md` — the ground-loading sortie phase; `reinforcement-nouns.md` — the airfield supply gate and the shared delivery-deployment policy |
| `spaceport-campus-window.md` | 2026-08-31 | this commit | `mapgen-nouns.md` — placing a facility where it fits, the scorer-predicts-the-outcome law, and why a drivable centreline cannot be flooded across |
| `precinct-interior-coherence.md` | 2026-09-01 | this commit | `precincts.md` — the character part of the noun, a zoned place says what it is inside, and the three laws: hinterland has no character, a centre is a place's own, what a place is never moves where it is |
| `precinct-mission-wiring.md` | 2026-09-01 | `124352605`, `6d69c4cd1`, `108bf7f1d`, this commit | `precincts.md` — which battles are made of places, a derived plan fits the map it is given, a programmed place claims first, and a garrison's stores as points of interest |
| `grown-road-graph.md` | 2026-09-01 | this commit | `mapgen-nouns.md` — grown settlements: density is one knob and arm length must not scale with it, the hinterland is not a parcel, zoning does not consult it; `precincts.md` — the `BiomeKind` question, answered by the front as a depth |
| `fortress-first-conquest.md` | 2026-09-01 | this commit | superseded by `precincts.md`: a fortress is a programmed precinct, the wall is drawn around what grew, the gates are where roads cross it; the two landed slices (the ward is told where it goes, a program may owe none or many of a facility) already stand in `compound-programs.md` and `precincts.md` |
| `conquest-on-precincts.md` | 2026-09-01 | `c227221d3`, `fc67e96b5`, `4ba0f3304`, `e3fcc26cb`, this commit | `precincts.md` — which battles are made of places (Conquest placed from its axis at its own scale, sprawl as mission vocabulary), the front is a depth not a biome, a place can be landed on / has one keep / owns its guns; `reinforcement-nouns.md` — the front band; `mission-tier-nouns.md` — Conquest's mission-owned scale; `conquest-nouns.md` — the map a mission requires on a precinct map |
| `approach-standoff.md` | 2026-09-01 | `98e03c605`, `41ec64f7f`, this commit | `precincts.md` — a mission says how far out its force lands: the standoff as vocabulary, resolved after growth, the approach carried, why cells and not fractions, and the measured first-contact ticks; `conquest-nouns.md` — the standoff beside the arrival doctrine |
| `landing-place.md` | 2026-09-02 | this commit | `precincts.md` — the landing place is a place: its kind, the apron every kind owes, the standoff entering derivation, the claim the berths are confined to, and the compound the marines start holding; `conquest-nouns.md` — the stated landing kind and a compound that is not the defender's |
| `lane-paths.md` | 2026-09-02 | `08dde8d5`, `5917e8e1`, `d1d03b7f`, `2c22cf81`, this commit | `precincts.md` — a lane is a path, its derived meander and the stream it draws from, the slide rule for a refused waypoint, the recorded walkable route and the polyline that draws it, and the beachhead taking its ground before the ladders; `mapgen-nouns.md` — lane-route data on the map result |
| `lane-seed-separation.md` | 2026-09-02 | `aa30b91d8`, this commit | `precincts.md` — the ladder's two separations stay distinct only while the two seed lists do, a rung's jitter window is bounded by the map margin rather than by its lane, the deepest waypoint gives way backward only, and the main settlement shares the garrison's emptiest-cell exemption |

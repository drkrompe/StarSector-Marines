# Convoy nouns

Status: ACTIVE

Written: 2026-08-23

## Purpose and boundary

A convoy is the battle-layer **ground delivery means**: it brings a
reinforcement payload from an off-map perimeter, lands at a scored rally-area
drop-off, disembarks it, provides any variant-specific on-site behavior, and
leaves. Convoy owns vehicle movement and the delivery lifecycle. The
reinforcement layer owns whether a request exists, which means may fulfill it,
and the ticket/supply economy; a convoy only fulfills a request it receives.

Convoy is the ground counterpart to shuttle delivery, not a generic ground-unit
system. It is nevertheless the chassis seam for later vehicle roles: a future
vehicle may have a different body, payload, or parked behavior without changing
the delivery lifecycle or treating roads as kinematic rails.

The current operational variant is the defender `HEAVY_APC`: four militia
passengers, a roof weapon, and a timed overwatch after disembarkation. The old
`MILITIA_TRUCK` is retired. Parked map vehicles are separate static scenery and
obstacles, not convoy actors.

## Delivery authority and lifecycle

`ConvoyMeans` is the reinforcement provider. Defender-side ARMORY supply gates
it: when no defender ARMORY remains, convoy cannot fulfill and the
reinforcement dispatcher may choose another means. It selects a defender-side
perimeter approach and a viable interior rally drop-off, avoiding the player's
entry edge and preferring separation from active convoy destinations. The road
graph still supplies this map-aware selection vocabulary; it does not constrain
the route between selected points.

A convoy mission moves through a single lifecycle:

`PENDING → INCOMING → LANDED → OVERWATCH → DEPARTING → GONE`.

`PENDING` is the off-map stagger. `INCOMING` drives from an off-map staging
point to the landing zone. `LANDED` releases passengers one at a time into a
nearby free cell and assigns their new squad to the reinforcement objective.
An armed APC then `OVERWATCH`s before `DEPARTING`; a variant that does not
linger may go straight to departure. `GONE` is terminal and removes the world
actor. A failed route suppresses that delivery rather than creating a
teleporting or stranded vehicle.

The vehicle is a world-resident actor but not a normal grid combatant. Its
identity, motion, mission, and optional turret authority are separate from its
passenger payload. It is rendered and can fire while visible; it does not yet
occupy the infantry grid or participate in ordinary collision/damage handling.
That distinction is intentional and makes the infantry-interaction and
vehicle-damage stories real extensions rather than claims about present
behavior.

## Route, corridor, and motion

Routing and motion are one convoy model, not separate features. The route layer
chooses an advisory corridor; the control layer drives a physically plausible
body toward it. Nested cost-field-routing and navigation-rework were narrow
implementation tracks and are folded here.

Roads are a **cost preference**, not topology a vehicle must follow. A route
search favors road and hardscape cells, accepts costlier open terrain for a
genuine shortcut, and avoids ugly terrain where possible. A clearance mask for
the vehicle's width gates the search and its straight-line simplification, so a
route never deliberately threads a static gap the vehicle cannot fit. Endpoint
snapping accounts for perimeter and wall-adjacent cells removed by clearance.
The resulting sparse corridor remains advisory: it is not an animation rail.

The controller continuously tracks a rolling, local kinematically feasible
trajectory against the live navigation grid. The bicycle body, speed-aware
lookahead, corner-speed governor, and terminal docking maneuver make turns
continuous. The only pose playback is the short, validated terminal docking
maneuver; ordinary route travel is always body-driven. The local planner may
fall back to corridor pursuit where no short trajectory is available, including
the deliberately off-map entry and exit tails.

Recovery is progressive rather than a permission to clip geometry:

1. Ordinary feasible drift receives a fresh local trajectory.
2. A wall-blocked or geometrically impossible forward turn commits to a bounded
   reverse that creates room for a new forward plan.
3. Lack of corridor progress re-routes around the failing area through the
   cost field, choosing a new initial bearing when needed.
4. If no such route exists, the vehicle holds rather than thrashing; the
   remaining terminal policy is open work.

The macro terrain cost and clearance inputs are built for a battle and reused
by recovery. They do not currently rebake for every terrain change; the local
planner sees live navigation changes, while a later policy can decide when a
macro reroute must refresh its inputs.

## Standing laws

- Reinforcement orchestration, supply production, and request priority stay
  outside convoy. Convoy must not mint itself a delivery opportunity.
- A route is clearance-valid and cost-biased; it is neither a road-graph-only
  path nor an exact sequence of poses to replay.
- Motion owns kinematic feasibility. Route preference alone cannot promise that
  an approach has enough turning room, so recovery must remain safe and visible
  until turn-aware routing is proven.
- Arrival is not failure. Reaching the terminal corridor region must transition
  to landing/departure instead of triggering a false stuck recovery.
- A vehicle either moves under its own body, recovers, or holds. It never solves
  a failure by crossing a wall, snapping through a corner, or looping forever.
- Passenger deboarding uses the same faction roster and squad/objective
  conventions as other reinforcement means. The convoy creates delivery; it
  does not create a separate infantry ruleset.

## Adjacent domains and extension points

The road graph is enduring generator data: road reservation, compound
circulation, validation, preview/debug information, and rally/approach
selection still use it. Only its former role as the sole vehicle router is
retired. `road_graph_design` remains the durable generator rationale.

Convoy meets `architecture.md` at the reinforcement-means boundary,
`central-keep.md` at ARMORY-driven defender supply, and `faction-roster.md` at
passenger composition. The air domain remains the delivery counterpart, while
the future air-to-ground interaction depends on a real vehicle damage model.

Future variants belong behind vehicle capabilities rather than another parallel
convoy model: payload/deboard effect, chassis/body, clearance/handling profile,
armed or unarmed parked behavior, and authority to affect reinforcement supply.
Tanks and player-controlled vehicles are broader combat features, not simple
APC enum additions. A vehicle-spawned squad currently enters the normal free
agent pool; explicit commander registration is deferred until the commander
has a concrete need for a distinct convoy-arrival signal.

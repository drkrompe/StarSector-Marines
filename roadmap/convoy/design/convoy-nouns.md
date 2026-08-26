# Convoy

Status: ACTIVE — ground delivery uses a shared convoy lifecycle, with the defender `HEAVY_APC` as its operational variant and vehicle interaction, damage, variants, scale, and terminal recovery as extension paths.

Written: 2026-08-23

Updated: 2026-08-26 — added transactional route proof and Conquest rear-front deployment policy.

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

The current operational variant is the defender `HEAVY_APC`: four
faction-rostered infantry passengers, a roof weapon, and a timed overwatch
after disembarkation. The old
`MILITIA_TRUCK` is retired. Parked map vehicles are separate static scenery and
obstacles, not convoy actors.

## Delivery authority and lifecycle

`ConvoyMeans` is the reinforcement provider. Defender-side ARMORY supply gates
it: when no defender ARMORY remains, convoy cannot fulfill and the
reinforcement dispatcher may choose another means. Outside Conquest it retains
the legacy defender-side perimeter and rally policy. In Conquest, defender
command supplies a dispatch-time deployment hint and minimum safe forward band;
the convoy accepts only the strict defender rear edge and a drop junction behind
that band. The policy is frozen once a route commits rather than retargeting a
vehicle already in motion. The road graph still supplies this map-aware
selection vocabulary; it does not constrain the route between selected points.

A convoy mission moves through a single lifecycle:

`PENDING → INCOMING → LANDED → OVERWATCH → DEPARTING → GONE`.

`PENDING` is the off-map stagger. `INCOMING` drives from an off-map staging
point to the landing zone. `LANDED` releases passengers one at a time into a
nearby free cell and assigns their new squad to the reinforcement objective.
An armed APC then `OVERWATCH`s before `DEPARTING`; a variant that does not
linger may go straight to departure. `GONE` is terminal and removes the world
actor. Dispatch proves inbound and outbound travel before creating the vehicle.
Entries, junctions, and exits are tried in stable ranked order, so one bad route
does not suppress a later valid candidate. A perimeter route stages far enough
inside the map for the full body to fit while its visible path still begins and
ends off-map. If no complete journey exists, the means rejects atomically and
the reinforcement dispatcher may try its next provider; no ticket-consuming
false success or stranded actor is created.

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
body toward it.

Roads are a **cost preference**, not topology a vehicle must follow. A route
search favors road and hardscape cells, accepts costlier open terrain for a
genuine shortcut, and avoids ugly terrain where possible. A clearance mask for
the vehicle's width gates the search and its straight-line simplification, so a
route never deliberately threads a static gap the vehicle cannot fit. Endpoint
snapping accounts for perimeter and wall-adjacent cells removed by clearance.
The sparse result then receives a vehicle-specific turn pass: material vertices
become footprint-checked minimum-radius fillets. A bend that the chassis cannot
drive forward is masked and the bounded cost search tries another corridor;
failure suppresses the dispatch instead of exporting an impossible corner.
The resulting corridor remains advisory: it is not an animation rail.

The controller continuously tracks a rolling, local forward-only,
kinematically feasible trajectory against the live navigation grid. Rolling
goals carry the corridor tangent and require heading agreement, so proximity
before a bend is not false success. Reverse is an explicit committed recovery,
not a hidden cusp in an ordinary plan. The bicycle body, speed-aware lookahead,
corner-speed governor, and terminal docking maneuver make turns continuous.
Docking aligns the parked APC with its outbound corridor. The only pose playback
is that short validated maneuver; ordinary route travel is always body-driven.
Coarse pursuit is limited to deliberate off-map entry and exit tails. Once the
full footprint is on-grid, a missing local trajectory means brake and reroute,
never "drive the rejected coarse corner anyway."

Recovery is progressive rather than permission to clip geometry. Ordinary
feasible drift receives a fresh local trajectory. A wall-blocked or
geometrically impossible forward turn commits to a bounded reverse that creates
room for a new forward plan. Loss of corridor progress invokes a cost-field
reroute around the failing area, with failed areas excluded for that travel leg
so later attempts cannot ping-pong through an earlier bad bend. If no such route
exists, reroute attempts are rate-limited while ordinary tracking continues;
the durable abort, hold, or deliver-in-place terminal outcome remains open.

The macro terrain cost and clearance inputs are built for a battle and reused
by recovery. They do not currently rebake for every terrain change; the local
planner sees live navigation changes, while a later policy can decide when a
macro reroute must refresh its inputs.

## Standing laws

- Reinforcement orchestration, supply production, and request priority stay
  outside convoy. Convoy must not mint itself a delivery opportunity.
- A route is cost-biased, clearance-valid, and minimum-radius-valid for its
  vehicle profile; it is neither a road-graph-only path nor an exact sequence
  of poses to replay.
- Route construction proves ordinary forward bends. Live motion remains the
  final kinematic authority, and any changed-grid failure stops and recovers
  instead of degrading to raw polyline pursuit.
- Arrival is not failure. Reaching the terminal corridor region must transition
  to landing/departure instead of triggering a false stuck recovery.
- A vehicle moves under its own body or performs a bounded recovery. It never
  solves failure by crossing a wall or snapping through a corner; the durable
  terminal outcome for an unrecoverable route remains open.
- Passenger deboarding uses the same faction roster and squad/objective
  conventions as other reinforcement means. The convoy creates delivery; it
  does not create a separate infantry ruleset.

## Adjacent domains and extension points

The road graph is enduring generator data: road reservation, compound
circulation, validation, preview/debug information, and rally/approach
selection still use it. Only its former role as the sole vehicle router is
retired. `[[road_graph_design]]` remains the durable generator rationale.

Convoy meets `reinforcement-nouns.md` at the orchestration, supply-gate, and
faction-roster boundary, and `conquest-nouns.md` at compound capture ownership.
The air domain remains the delivery counterpart, while the future air-to-ground
interaction depends on a real vehicle damage model.

Future variants belong behind vehicle capabilities rather than another parallel
convoy model: payload/deboard effect, chassis/body, clearance/handling profile,
armed or unarmed parked behavior, and authority to affect reinforcement supply.
Tanks and player-controlled vehicles are broader combat features, not simple
APC enum additions. A vehicle-spawned squad normally enters the reinforcement
assignment flow. Conquest is the explicit exception: its defender policy carries
`conquest-defender` ownership through the vehicle mission, and the squad is born
with the requested node-hold or zone-clear objective already under that commander.

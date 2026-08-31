# Convoy

Status: ACTIVE — ground delivery uses a shared convoy lifecycle, with the defender `HEAVY_APC` as its operational, damageable variant and moving-vehicle interaction, variants, scale, and terminal recovery as extension paths.

Written: 2026-08-23

Updated: 2026-08-30 — map generation now guarantees a drivable corridor from
the defender's rear edge to the city, so the strict rear entry admits a hull.

Updated: 2026-08-30 — a wreck writes nothing to the navigation or sight map,
a live vehicle wears the shared durability gauge, and a carrier that has
unloaded departs rather than holding armed overwatch on its drop point.

Updated: 2026-08-30 — a chassis is an ordinary body: it carries `IDENTITY`,
lives in the unit spatial index, and is a squad contact like any other enemy.
The explicit convoy candidate set is gone from targeting.

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
faction-rostered infantry passengers and a roof weapon. The old `MILITIA_TRUCK`
is retired. Parked map vehicles are separate static scenery and obstacles, not
convoy actors.

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

What the map owes a convoy is width, and it is now stated rather than hoped
for. Map generation reserves a **vehicle corridor** — a drivable band from the
defender's rear map edge, through the fortress, out to the city — before
anything is built on it, so the guaranteed rear entry a Conquest dispatch
insists on is guaranteed to admit a hull rather than merely to exist. Before
that contract a convoy could get from the rear edge into the city on twelve of
forty generated maps: the road was there and was narrower than the vehicle
wherever the fortress had touched it. See the vehicle-circulation section of
`mapgen-nouns.md`; the corridor is a width contract, where the road graph is a
centerline for choosing among places.

A convoy mission moves through a single lifecycle:

`PENDING → INCOMING → LANDED → DEPARTING → GONE`, with `WRECKED` as a terminal
transition from any visible live state.

`PENDING` is the off-map stagger. `INCOMING` drives from an off-map staging
point to the landing zone. `LANDED` releases passengers one at a time into a
nearby free cell and assigns their new squad to the reinforcement objective,
then the carrier turns for its outbound corridor. `GONE` is terminal and
removes the world actor. `WRECKED` stops motion and weapons, removes combat
targetability, and retains the chassis as presentation. Dispatch proves inbound
and outbound travel before creating the vehicle.

**A carrier that has unloaded leaves.** There is no phase between setting the
payload down and departing, whatever is on the roof. An armed APC that stayed
to work the drop zone was a free gun emplacement the reinforcement never paid
for, arriving on a timer rather than on anything the battle decided. The roof
weapon is what the vehicle defends *itself* with over a drive through contested
ground — it is live for the whole errand, inbound and outbound alike — not a
reason to park. The air transports lost the same phase for the same reason; see
`air-nouns.md`.

A wreck writes nothing to the map. It is a rendered chassis, not terrain: it
closes no navigation cell and casts no line-of-sight shadow. A hull dies
wherever it happens to be standing — in a doorway, on the one street joining
two halves of a district — and terrain a destroyed vehicle can permanently
close is terrain that can be permanently islanded, by an event no map author
anticipated and no generator can validate. Sight and fire cross a wreck for
the same reason: the squad that just killed the vehicle in its firing line
keeps that line.
Entries, junctions, and exits are tried in stable ranked order, so one bad route
does not suppress a later valid candidate. A perimeter route stages far enough
inside the map for the full body to fit while its visible path still begins and
ends off-map. If no complete journey exists, the means rejects atomically and
the reinforcement dispatcher may try its next provider; no ticket-consuming
false success or stranded actor is created.

## A vehicle is a unit

A chassis is a **body**, on the same terms as a soldier, a mech, a turret and a
parked airframe. It carries `IDENTITY` — faction, the `GROUND_VEHICLE`
archetype, a greppable name — so the code that sees, scores, targets and damages
it does not have to know what kind of thing it is holding. Its ground identity,
kinematics, mission, shared `HEALTH`/`ARMOR`, and optional turret authority stay
separate from its passenger payload, and its continuous body supplies target
position, velocity, radius, and height.

It reaches the scans through the **unit spatial index**, which is an index over
bodies rather than a bucketed copy of the dense infantry roster. That
distinction is the whole design. For a while a vehicle was instead a world
entity with `HEALTH`/`ARMOR` and no identity, reached by an explicit convoy
candidate set that each scan had to remember; six sites carried that sweep and
three of them forgot, so an APC could drive across a squad's front unengaged.
The rule that replaced it: **a new proximity scan gathers from the index and is
correct for every body by construction.** Nothing in the targeting layer knows
what a convoy is — `TacticalScoring` does not import `ConvoyService`.

What is genuinely per-chassis dispatches once, in the roster, beside the same
arms that answer for a turret's structure and a mech's variant: physical radius,
target-plane half-height, and threat reach. A vehicle carries no `COMBAT`
component — its turret runs its own aim loop rather than the infantry fire path
— so "how far can this body shoot" is asked through `UnitRosterService.threatRange`
rather than the fail-loud `World.attackRange`. An unarmed hull answers zero,
which is the right answer rather than a missing one: it is a thing to shoot at,
not a thing to take cover from.

What a chassis deliberately does **not** carry is `POSITION`. That is what keeps
the occupancy map and the separation pass off it: those queries are keyed on
that component, so a moving hull still neither claims infantry cells nor takes
part in unit-unit collision. Everything else the ECS skips by
membership-narrowing — no `COMBAT`, no `MOVEMENT`, no `ROLE`, no `AI_STATE`,
so the fire system, the mover, separation and the planner never see it.

A vehicle **is** an ordinary squad contact. It is believed in, it counts in the
contact picture, it moves doctrine and it raises the alert level, because an APC
bearing down is a warning exactly as much as a rifleman stepping into view. The
one accommodation it needs is that it has no dense-roster slot, so the awareness
pass records it through the slotless observation overload rather than the
per-slot dedupe.

One dense-roster walk still carries an explicit convoy sweep: the area-detonation
splash in `Detonations`. It scans the whole live population rather than querying
the index, and it stays that way on purpose — the reason is worth writing down,
because "use the index" looks obviously right here and is not.

A broad phase over the index returns the bodies whose **centre** sat within the
asked radius **at the last rebuild**. Both halves of that need padding, and only
one of them can be bounded. The radius half now can: `UnitSpatialIndex.maxBodyRadius()`
measures the largest body it has ever held, so a caller asking "does this circle
touch that body" widens its query by a number that is measured rather than
authored. That matters because the inputs are content: a turret's radius comes
from the turret-emplacement JSON and a chassis's is derived from its art
dimensions, so a hardcoded margin goes stale on a content edit, in a file nobody
would connect to blast damage.

The staleness half cannot be bounded. Index positions are a tick-start snapshot,
and a body can be **teleported** between rebuilds — deboarding, an equipment drop,
any `setCellPos`. There is no speed term that covers a teleport, so an index query
can miss a body that is genuinely inside the blast, and the failure is silent:
one fewer casualty, no error. Converting the splash proved this rather than
predicting it — a satchel charge that follows a target which then moves five
cells stopped destroying it, because the snapshot still had the target where it
had been. Ballistics accepts the same risk knowingly (its consequence is a missed
round, and its corridor query pads for motion); an area effect that silently
spares a victim is a worse trade than a full walk over a few hundred live bodies.

Splash therefore reads live positions over the live population, and pays the
explicit convoy sweep as the price. That is a considered exception to "every
proximity scan goes through the index", not an unconverted leftover.

A vehicle is seen but never sees. It carries no perception components at all, so
any code that reads a target's sight stats must treat "target" and "perceiver"
as separate roles: the seeing side of a line-of-sight pair reads the fail-loud
sight accessor, and the seen side reads the tolerant one that answers with plain
grid line-of-sight for an entity that has no vision. The same rule covers a
remembered contact that has since become a corpse.

`HEAVY_APC` durability is 220 structure behind 160 armor at rating 18. It uses
the shared durability law: low-penetration rifles chip armor slowly, while
marine rockets and mech LRMs defeat it in a few committed hits or one strong
salvo. HP zero is once-only. Any passengers still onboard die except for one
or two faction-rostered infantry who eject into nearby free cells at 25% HP.
The live mission becomes `WRECKED`, its turret and body stop, and stacked or
late impacts cannot repeat wreck or passenger effects.

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
When docking or ordinary pursuit can reach the exact drop-off, they remain
authoritative. If no safe forward segment remains after an inbound vehicle has
entered the aligned terminal goal region, its current footprint-valid pose is
the landing pose; terminal proximity must not strand the payload as a false
planning failure.
Coarse pursuit is limited to deliberate off-map entry and exit tails. Where a
vehicle stands is the wrong way to recognise one: the tail begins where the
*planning horizon* leaves the map, not where the footprint does. A route's exit
waypoint sits a fixed pad beyond the perimeter, so a departing vehicle aims its
rolling goal off-grid a full horizon — about ten cells for a HEAVY_APC — before
any part of it crosses the edge. Reading that stretch as an ordinary planning
failure halts a departure inside the map permanently: no bounded local search
can reach a goal that is off the grid, and no reroute can move a waypoint that
is off the map by design. Within the tail, coarse pursuit drives the corridor
out; the footprint gate still applies while the carrot is on-grid, so the
concession is to the map edge and not to walls. Everywhere else the rule is
unchanged: with on-grid route still to solve, a missing local trajectory means
brake and reroute, never "drive the rejected coarse corner anyway."

Recovery is progressive rather than permission to clip geometry. Ordinary
feasible drift receives a fresh local trajectory. A wall-blocked or
geometrically impossible forward turn commits to a bounded reverse that creates
room for a new forward plan. Loss of corridor progress invokes a cost-field
reroute around the failing area, with failed areas excluded for that travel leg
so later attempts cannot ping-pong through an earlier bad bend. If no such route
exists, reroute attempts are rate-limited while ordinary tracking continues;
the durable abort, hold, or deliver-in-place terminal outcome remains open.

The macro terrain cost input is built for a battle and reused by recovery.
Clearance is an immutable snapshot derived from live walkability, and later
dispatches always derive a fresh mask. Because a wreck closes no cell, that
mask does not change when a vehicle dies, and a later convoy plans the street
it planned before.

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
  to landing/departure instead of triggering a false stuck recovery, and aiming
  at an off-map exit is arrival in progress rather than an unsolvable route.
- A vehicle moves under its own body or performs a bounded recovery. It never
  solves failure by crossing a wall or snapping through a corner; the durable
  terminal outcome for an unrecoverable route remains open.
- Passenger deboarding uses the same faction roster and squad/objective
  conventions as other reinforcement means. The convoy creates delivery; it
  does not create a separate infantry ruleset.
- Vehicle durability uses the shared armor/structure authority. A destroyed
  vehicle stops being a combat target and a weapon platform, and stops there.
  It never edits the battlefield: no walkability write, no line-of-sight
  write, no clearance invalidation. A combat outcome must not be able to
  reshape the map's connectivity, because nothing validates the map a
  mid-battle closure leaves behind.
- A vehicle is a unit with a hull. It reads its durability the way every other
  combat actor does — the shared ownership-coded gauge over its chassis, armor
  row over structure row — for as long as it is alive. A wreck drops the gauge;
  the darkened hull is the whole report.

## Adjacent domains and extension points

The road graph is enduring generator data: road reservation, compound
circulation, validation, preview/debug information, and rally/approach
selection still use it. Only its former role as the sole vehicle router is
retired. `[[road_graph_design]]` remains the durable generator rationale.

Convoy meets `reinforcement-nouns.md` at the orchestration, supply-gate, and
faction-roster boundary, and `conquest-nouns.md` at compound capture ownership.
The air domain remains the delivery counterpart. Its existing ground-target
fire may damage vehicles through the shared target and damage boundaries;
airborne-specific targeting policy remains Air-owned.

Future variants belong behind vehicle capabilities rather than another parallel
convoy model: payload/deboard effect, chassis/body, clearance/handling profile,
armed or unarmed parked behavior, and authority to affect reinforcement supply.
Tanks and player-controlled vehicles are broader combat features, not simple
APC enum additions. A vehicle-spawned squad normally enters the reinforcement
assignment flow. Conquest is the explicit exception: its defender policy carries
`conquest-defender` ownership through the vehicle mission, and the squad is born
with the requested node-hold or zone-clear objective already under that commander.

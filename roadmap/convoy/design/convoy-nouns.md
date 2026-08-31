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

Updated: 2026-08-31 — an arrival gate is derived from the step a body takes in
one tick, not authored as a bare distance, so a faster variant cannot drive
through its own LZ without arriving.

Updated: 2026-08-31 — route construction proves the turn from the way a truck
arrives to the way it must leave, and a departure that finds itself misaligned
backs and fills onto its corridor instead of holding.

Updated: 2026-08-31 — the control layer takes a route and a leg rather than an
inbound/outbound flag, so what arrival means is a property of the journey and a
route need not belong to a delivery.

Updated: 2026-08-31 — a vehicle can be given a move order, and an order that
cannot be carried out is refused with a reason rather than parked on.

Updated: 2026-08-31 — the departure turn is proved from the drop point with no
run-up, because the maneuver that would have earned the run-up is an attempt
rather than a guarantee.

Updated: 2026-08-31 — a chassis can be deployed with no errand and commanded,
and only its owner may command it.

Updated: 2026-08-31 — a vehicle can carry a named squad rather than a count;
mounting narrows a unit instead of deleting it, and a ride ends the objective
but not the claim.

Updated: 2026-08-31 — mounting and dismounting are contextual right-click
orders on the vehicle itself rather than buttons.

Updated: 2026-08-31 — the pointer says which contextual order a right-click
would issue, resolved through the same services the order systems use.

Updated: 2026-08-31 — a chassis is one implementation of the shared body
surface rather than its own target model; the explicit convoy loops in the
spatial index, ballistics, the splash sweep and the damage route are gone.

## Purpose and boundary

A convoy is the battle-layer **ground delivery means**: it brings a
reinforcement payload from an off-map perimeter, lands at a scored rally-area
drop-off, disembarks it, provides any variant-specific on-site behavior, and
leaves. Convoy owns vehicle movement and the delivery lifecycle. The
reinforcement layer owns whether a request exists, which means may fulfill it,
and the ticket/supply economy; a convoy only fulfills a request it receives.

Delivery is what a convoy is dispatched *for*, and no longer all a vehicle can
be doing: a chassis may also be given a **move order** — sent to a cell somebody
picked, under its own kinematics, with the delivery errand standing still until
it is done. Convoy is still not a generic ground-unit system in the sense of
owning squads or objectives; it owns a chassis, its motion, and its errands.
It is the seam for later vehicle roles: a future vehicle may have a different
body, payload, or parked behavior without changing the delivery lifecycle or
treating roads as kinematic rails.

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

`ecs-nouns.md` owns what a body is and what carrying one obliges;
`ConvoyService` is Convoy's implementation of that surface, and it is the same
surface an aircraft implements. Convoy states nothing about how a body is
perceived, damaged or killed that is not the shared answer — what stays here is
what is genuinely about a chassis: it is present while it is on the map and not
a wreck, it is never airborne, its radius comes from its own art dimensions, its
reach is its turret's, and dying means `GroundSystem` stops it and leaves the
hull. Whether a particular shooter may engage it is the shared relation, which
for a chassis reduces to presence because nothing about a truck is a question of
altitude.

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
asked radius **at the last rebuild**. Both halves of that are wrong for a
physical question, and only one of them can be fixed.

The size half is fixed, and the fix was to stop asking the wrong question.
`gather` means "whose centre is in this circle"; a caller asking about contact
wants "whose circle touches this circle", and the difference is the body's own
radius. Making each caller pad for that is the same defect as making each caller
remember a convoy sweep — it works until someone forgets, and forgetting is
silent. So the index owns it: `gatherOverlapping` and `gatherAlongSegment` add
the body-size pad themselves, from a bound measured off the bodies they actually
hold rather than authored as a constant. Measured matters because the inputs are
content — a turret's radius comes from the turret-emplacement JSON and a
chassis's is derived from its art dimensions, so a hardcoded margin goes stale on
an edit to a file nobody would connect to blast damage. The bound is not public;
a number every caller must remember to add is not an improvement on a sweep every
caller must remember to run.

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

Splash therefore reads live positions over the live population, and pays a
second walk over the off-roster bodies as the price. That is a considered
exception to "every proximity scan goes through the index", not an unconverted
leftover — and note that the overlap query does not rescue it. Query semantics
and snapshot staleness are different axes; fixing the first does nothing for the
second.

That walk is over *bodies*, not over convoys. It was a convoy loop with an
aircraft loop bolted on beside it, which is how a blast sized itself against a
chassis correctly and against every aircraft in the game identically.

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

The control layer is handed **a route and a leg** — what the vehicle is driving
this route for — rather than a flag naming which half of a delivery it is on.
The leg is what makes reaching the end of a route mean something: how near
counts as arrived, whether the body settles exactly on the last waypoint,
whether the docking maneuver may earn it a departure heading, whether the
planner's soft terminal region is good enough, and whether the vehicle may back
and fill to get started. A delivery supplies two legs in sequence; the route is
not otherwise special, which is what leaves room for a vehicle to be sent
somewhere that is not a delivery at all — see `vehicle-as-commandable-unit.md`.

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
- It also proves the one bend that lies on neither polyline: the turn from the
  heading a vehicle arrives on to the heading its exit demands — asked with no
  run-up, from the drop point itself. The docking maneuver would often rescue
  the pairing and is evaluated a trigger distance back up the approach, which
  can be materially more road than the drop point has; but docking is an
  attempt rather than a guarantee, and a truck that arrives through the plain
  distance gate is left standing on the drop point on the heading it drove in
  on. Dispatch must believe the pessimistic answer, because that is the
  situation it cannot rule out. An entry and an
  exit can each be perfectly drivable and still be an impossible pairing,
  because reversing a chassis costs lateral room the road may not have — the
  shipped APC turns inside about four cells and needs about three of swing to
  come round, which a five-cell road does not give it. Such a delivery succeeds
  and then strands: marines unload, and the vehicle can never point at its way
  out. Dispatch rejects the pairing and tries another exit rather than leaving
  it for a recovery ladder that plans forward motion and cannot help.
- A departure that is misaligned anyway backs and fills onto its corridor. Only
  the docking maneuver lands a truck on its departure heading, so one that
  arrived through the plain distance gate keeps whatever heading it came in on;
  the same Reeds-Shepp maneuver, aimed a short way down the outbound corridor,
  is what turns it round. Short deliberately — a nearer goal buys a tighter
  swing, and lateral room is the scarce thing. The attempt is bounded, because
  a pose no maneuver can rescue must cost a couple of tries rather than loop.
- An order that cannot be carried out is refused, out loud. A dispatched route
  is proven before dispatch commits to it, and a destination somebody picked is
  not — so a move order is where feasibility is actually decided, and the
  recovery ladder stops being a safety net and becomes the mechanism. A request
  with no drivable route for that chassis never becomes an order; an accepted
  one that stops converging is abandoned with a reason. Holding position
  silently for the rest of the battle is the terminal state this replaces, and
  it is unacceptable for a vehicle somebody is watching.
- What a vehicle is asked for and where it is going are separate facts. A
  clicked cell is resolved to the nearest ground the chassis can actually
  occupy — its footprint and its turning circle, not the infantry answer — and
  both the request and the destination are kept so the interface can say "here,
  not quite there."
- **Carrying a number and carrying a person are different things.** Delivery
  counts passengers: a convoy holds a remaining count and mints a fresh marine
  per unload, and an evacuating civilian is deleted while a counter goes up.
  That is right for arrivals and departures, where the individual is either not
  yet real or gone for good. A **ride** is the other case — the squad that gets
  out is the squad that got in, with its casualties, its loadout and its squad
  still attached — so a passenger is the unit itself, narrowed rather than
  counted.
- Mounting takes away being somewhere and nothing else: position, and the path
  it was following. Everything carrying authored numbers stays. Removing a
  component drops its values for good, so a passenger stripped of its combat
  data would come back disarmed — same identity, same squad, no weapon — which
  no test about the squad surviving the ride would notice.
- A ride ends a squad's objective and leaves its command claim alone. The
  objective is a statement about a place, and a ride across the battlefield
  makes it stale by construction: a squad that kept it would dismount and walk
  straight back the way it was carried. The claim is untouched because nothing
  about who owns the squad changed — a claim is handed off when it changes
  hands, and being carried is not that.
- A chassis need not be on an errand at all. A **deployed** vehicle is one put
  on the field with no delivery to run: it holds where it was set down until it
  is told to go somewhere. That is what a vehicle the player owns is doing
  between orders, and it is what makes delivery one of the things a vehicle can
  be doing rather than the whole of what a vehicle is. Its route arrays are
  degenerate on purpose — fabricating a journey it never took would give it an
  errand to resume the moment an order was released.
- Only the player's own chassis takes the player's orders. Orders are queued by
  entity id, so the order system is where that is decided rather than the
  picker; an enemy vehicle is a target, not a unit, and asking it to move is
  refused the same way any other impossible order is.
- **The vehicle is the target, and what a click means follows from what is in
  it.** Pointing a squad at a friendly transport with room is an order to get
  in — it walks over and boards, and the destination follows the vehicle rather
  than staying where the click landed, because a vehicle is not a cell and can
  drive off while the squad is still walking. Pointing a loaded transport at
  itself is an order to unload; pointing an empty one at itself is an ordinary
  move. None of it needs a button, which is the grammar Red Alert 2 settled and
  there is no reason to re-litigate.
- **A contextual order has to be legible before it is issued.** The click means
  different things over different ground, and nothing on screen says so until
  after the fact — which makes a good interaction an undiscoverable one. The
  pointer therefore carries the verb it would perform, and stays silent for an
  ordinary move, which is the default and would only be noise.
- That preview asks the same services the order systems ask, never a second copy
  of the rule. A cursor that offers a ride the order then refuses is worse than
  a cursor that says nothing, so "can this squad board that vehicle" and "is
  this click on that hull" each have exactly one implementation, and the
  cursor's promise is tested against what the click actually does rather than
  against itself.
- Boarding is all or nothing on the squad, and "all" means the squad rather
  than the seats. A squad ordered to a vehicle arrives strung out in formation,
  so admitting whoever got there first boards one marine and leaves the rest
  standing in the road with the order marked done.
- A move order owns locomotion and nothing else. The turret, the payload, and
  the delivery obligation are untouched, and releasing the order hands the
  vehicle straight back to the errand it was on.
- Arrival is not failure. Reaching the terminal corridor region must transition
  to landing/departure instead of triggering a false stuck recovery, and aiming
  at an off-map exit is arrival in progress rather than an unsolvable route.
- An arrival gate is a distance, and a body samples its position once a tick.
  Every such gate is therefore at least as wide as the ground the vehicle
  covers between two samples, or it is a gate the vehicle jumps: outside on one
  tick, outside on the next, arrival never firing, and the recovery ladder
  inheriting a problem that was never about the route. Authored tolerances are
  floors under that derivation, never the whole of it — the margin the shipped
  `HEAVY_APC` enjoys is an accident of it being the only variant, and the
  planned light scout is specified as faster. The same law holds on the air
  side, where a gate carrying a term it had no business carrying put a
  fighter's wheels down a runway's length from the numbers.
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

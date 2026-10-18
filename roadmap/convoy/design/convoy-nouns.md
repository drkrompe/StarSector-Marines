# Convoy

Status: ACTIVE — ground delivery, deployed APC orders, and direct control share vehicle authority; shared vehicle/infantry interaction and terminal recovery remain extension paths.

Written: 2026-08-23

Updated: 2026-10-15 — hulls repel infantry; story columns may drive reactively.

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

The ordinary reinforcement variant is the `HEAVY_APC`: a defender delivery carries
four faction-rostered infantry passengers and a roof weapon; a deployed Marine
APC uses the same chassis and may receive orders or direct control. The old `MILITIA_TRUCK`
is retired. The `MILITIA_TECHNICAL` is a separate light pickup with a pintle
machine gun: faster and much less durable than an APC, with finite mount
ammunition, no troop capacity, and no faction-specific combat exceptions.
Authored missions may field it for local forces; its first use is Wayline's
rear-route support in Thunder Run, not player ownership or a reinforcement-pool
change. Parked map vehicles are separate static scenery and obstacles, not
convoy actors.

The technical's overhead pickup silhouette hides its four tires beneath metal
fenders. Its hood, cab and open rear bed remain distinct, with a separately
registered pintle MG on the bed bearing. Destroyed hull and gun art preserve
their final bearings; a small blast, front-engine fire and brief pintle flash
cool to smoke, with the last emitter expiring by 35 seconds. The covered wheels
have no exposed tread animation. These are presentation states, with ordinary
weapon and durability authority unchanged.

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

The road graph's eligible perimeter candidates are static for a battle and
retained. Feasibility and arrival estimates check only the full-body footprint
at each candidate's staging cell against current walkability. A chosen convoy
captures raw navigation and ground kinds, then derives clearance and terrain
cost only where its inbound and outbound route searches actually explore.

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
part in unit-unit collision.

Instead, a hull **repels** bodies (`VehicleRepulsionSystem`). Every visible,
unwrecked hull is an oriented rectangle with two fields. Contact pushes an
overlapping body out along the shallower axis; in front of a moving hull it is
always out to the side, carried forward with the bow while it goes, so a truck
bulldozes a man aside rather than passing over him. A bow wave ahead of a moving
hull pushes bodies off its centerline before contact, harder the faster the hull
is going and the nearer they are. It is physical and faction-blind, deals no
damage, and runs after separation so a shove apart cannot put anybody back under
a hull. Mechs accept a reduced share; emplacements, aircraft, drones and other
hulls do not take part. Every displacement passes the same topology guard as
separation, so a body pinned against a wall stays where it is and the hull
passes. The push is applied to positions, not plans: infantry still path as if
hulls were absent, and a body walking into a parked hull is pushed out again
each tick. `-Dbattle.vehicle.repulsion=false` is the control. Everything else the ECS skips by
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

An ordinary vehicle is seen but never sees. It carries no perception components at all, so
any code that reads a target's sight stats must treat "target" and "perceiver"
as separate roles: the seeing side of a line-of-sight pair reads the fail-loud
sight accessor, and the seen side reads the tolerant one that answers with plain
grid line-of-sight for an entity that has no vision. The same rule covers a
remembered contact that has since become a corpse. The spotter pickup is the one hull
that looks, and it does so without becoming a perceiver: its mast is a squadless
observer beside the body (the light roles, below), so the perception components
stay off every chassis.

`HEAVY_APC` durability is 220 structure behind 160 armor at rating 18. It uses
the shared durability law: low-penetration rifles chip armor slowly, while
marine rockets and mech LRMs defeat it in a few committed hits or one strong
salvo. HP zero is once-only. Any passengers still onboard die except for one
or two faction-rostered infantry who eject into nearby free cells at 25% HP.
The live mission becomes `WRECKED`, its turret and body stop, and stacked or
late impacts cannot repeat wreck or passenger effects. The transition also
queues one compact visual blast and short roof/rear-compartment burns that
cool into smoke, with the last APC emitter expiring by 55 seconds. The escort's
effects scale with its larger hull but stay smaller than the tank's. This is presentation only: no
secondary damage, navigation obstruction or smoke concealment is manufactured.

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

A failed bounded recovery is not proof of permanent unreachability. The vehicle
may retain that answer while the exact recovery request and its frozen routing
inputs remain unchanged, avoiding repeated identical proofs. A different
request, exclusion set, routing snapshot, or control intent requires a fresh
answer. This reuse neither suppresses ordinary physical tracking nor grants
permission to cross terrain; it only replaces an identical failed computation.

Fresh recovery against frozen on-demand routing inputs likewise retains its
departure candidates, failed-turn exclusions and active frontier across ticks.
A per-vehicle expansion allowance and admission of at most one fresh search
per tick spread the proof without restarting it or changing its bearing
preference. Pending is neither a route nor a failed result to memoize. Safe
tracking and braking remain authoritative while it waits; changed route,
terrain, exclusions, control or starting context retires obsolete work before
installation. This is not a shared fleet budget or a hard wall-time bound:
search storage and final kinematic refinement still cost work of their own.

An actual convoy proof retains a frozen routing view: raw navigation and ground
kinds are copied once, while footprint clearance and terrain cost are evaluated
and memoized only as the route or later recovery asks for a cell. Unexamined
ground is neither passable nor blocked until queried. Another convoy captures
the current map; a vehicle wreck closes no navigation cell, so it does not
change the next convoy's clearance.

## Direct control of a deployed APC

The session in `direct-control-nouns.md` may temporarily own an exact live
Marine HEAVY_APC in DEPLOYED state. Delivery legs, non-Marine hulls, and terminal
states remain outside that authority. Entry checks the complete oriented body
against current terrain. A vehicle keeps its own ground kinematics, mission,
and turret components; it does not acquire infantry position, movement, combat,
or AI components to participate in the shared input mode.

W/S supplies forward/reverse throttle and A/D wheel steering to the ordinary
bicycle body. Acceleration, braking, steering rate, reverse speed, and turning
radius remain physical. Both ordinary and manual vehicle motion check the full
oriented footprint throughout translation and rotation, including closed edges
between walkable cells. A blocked step ends at the last legal pose; neither an
intermediate collision nor a newly changed wall is excused by legal endpoints.
Manual ownership clears old corridor, trajectory, docking, and recovery work
so only its accepted drive input can move the hull.

A suspended move keeps its requested destination and relinquishes its old
route. New orders received during control replace that request without a route
proof or hull movement. They remain destination intent, so pointing at the
loaded controlled hull does not unload it during control or at handback. Exit
stops manual drive and queues fresh route construction from the actual pose to
the latest request. A reached destination completes into deployed hold; an
unreachable request keeps the ordinary visible refusal. Without a retained
request the deployed hull holds. Loss of live deployed eligibility forgets
suspension and queued intent. Ownership changes preserve both the passenger
manifest and delivery payload rather than manufacturing a delivery transition.

The independent roof turret keeps one magazine and cadence authority. Mouse
bearing drives its authored traverse, and a held primary trigger fires only
when ordinary alignment, cooldown, ammunition, and burst gates permit. A burst
retains its world point; later cursor movement applies to the next trigger.
Manual ownership suppresses this hull's autonomous acquisition and trigger,
while other vehicle turrets remain autonomous. Entry, exit, and input
suspension discard queued rounds and targets without resetting ammunition or
cooldown. The actual posed hardpoint supplies the ground ballistic source, and
the structural body-to-barrel check prevents launching beyond a wall.

The APC hull and twin roof MG are separate overhead silhouettes. The tapered
nose and sloped glacis distinguish the front from the broad troop compartment
and squared rear ramp, including when the gun faces elsewhere. The roof bearing
is on the chassis centerline and the gun rotates about its own frame center;
the retained source registration owns their measured placement. Heavy and
escort APCs carry the same-sized MG even though their chassis sizes differ.
The visible muzzles follow the shared heavy-MG ballistic hardpoints through
independent hull and turret rotation (`art-source/vehicles/apc/`).

The six exposed tire tops use the same distance-driven tread presentation as
the tank. Each wheel retains its own silhouette and the gaps remain open;
accepted chassis travel and yaw move only the tread material. Reverse reverses
the texture motion, stopped or blocked hulls stay still, and teleport resets
its phase. Steering and traction remain the ordinary vehicle motion model.
Every APC variant retains separately registered wreck hull and MG art at their
final bearings, with torn troop roofs, damaged tires and bent turret plates.
The up-armored APC is the same hull drawn darker for its welded plate. The
fire-support APC keeps the hull and replaces the sheet's MG with its heavy-mortar
mount's own catalog art, drawn on the roof bearing at the mount's intrinsic size
the way an aircraft's hull turrets are, and darkened on the wreck.
Destruction stops the tread animation and leaves the wreck after the effects cool.

A directly controlled hull repels bodies exactly as an autonomous one does.
Navigation occupancy, crushing and predictive avoidance remain the open parts
of `truck-infantry-interaction.md`. Direct control grants no special
collision immunity, crushing rule, or infantry separation policy. The vehicle
retains the same existing cross-domain behavior as an autonomous hull.

## Standing laws

- Reinforcement orchestration, supply production, and request priority stay
  outside convoy. Convoy must not mint itself a delivery opportunity.
- A route is cost-biased, clearance-valid, and minimum-radius-valid for its
  vehicle profile; it is neither a road-graph-only path nor an exact sequence
  of poses to replay.
- Route construction proves ordinary forward bends. Live motion remains the
  final kinematic authority, and any changed-grid failure stops and recovers
  instead of degrading to raw polyline pursuit.
- **Reachability is proved as the route search expands, not by preparing a
  global label map.** A road-graph connection does not imply a vehicle fits:
  the pathfinder's own step rule tests clearance, edges, and diagonal corners.
  A failed unmasked search has exhausted its reachable region, so later
  endpoints outside it can be skipped without confusing an unknown cell with a
  blocked one. The search may ultimately inspect the whole region when no
  route exists, but does so across ticks rather than in one burst.
- **A proof belongs to a frozen raw map and a live revision.** Its raw routing
  inputs are copied once; their clearance and cost derivatives are filled only
  on demand. A grid revision during proof invalidates it, including partial A*
  frontiers. A committed route retains its frozen view for later recovery, so
  unqueried cells cannot silently read a newer map. A settled aircraft can
  close ground; a vehicle wreck does not write to navigation.
- **A route proof is bounded in searches, not in the map.** The proof is an
  enumeration — entries against junctions against exits — so a limit of eight
  retries per endpoint pair bounds nothing a player is waiting on. One budget,
  counted in grid searches, spans the whole enumeration. Exhausting it is not
  an error: the dispatch reports no route, which is what it would have reported
  more slowly, and the request falls through to another means.
- **Bound both attempts and per-tick expansion.** The whole proof is bounded by
  search attempts, while each tick has a shared node-expansion allowance across
  active convoy proofs. An A* frontier that reaches that allowance resumes on
  another tick without paying for another attempt. Only perimeter entries are capped by
  count, because what an entry costs before any search is a road-graph flood
  and a sort rather than a search. Capping drops and exits by count as well
  looked harmless at six and three and was a bar set in the dark: the canonical
  240x160 rear-entry map proves its route at about the seventh ranked drop, and
  the cap refused a delivery the budget would have paid for. A cap by count
  cannot know what a candidate costs; the budget is charged for exactly what it
  is trying to limit.
- **One budget for the enumeration is a redistribution, not only a ceiling, and
  the redistribution is the part that pays.** Measured on a 560x336 Conquest
  map: the best-ranked drop routed its inbound leg on the first search and
  needed eighteen more before its outbound leg was drivable. Rationed to eight
  the dispatch abandoned it, and the next ninety-odd ranked drops in turn at
  eight searches each — 745 searches, three seconds of one tick whose other work
  came to twenty milliseconds — and then committed to a drop three times further
  from the ground the request asked for. Spending the budget on the candidate
  the ranking already called best cost nineteen searches and delivered nearer.
  Where the retries go matters more than how many there are.
- **A proof is spread across ticks, because a bound in total is not a bound in
  a frame.** Neither the enumeration nor a single disconnected A* may consume
  an unbounded tick. The dispatch that starts the proof reads its finished
  result later; the delivery itself takes seconds to appear and much longer to
  arrive, so this preparation can tolerate that delay.
- **Every cursor in that enumeration is state a restart would destroy**, which
  is why it is an object and not a smaller budget passed in again. The
  accumulating failed-turn exclusions and one A* frontier inside an endpoint
  pair are the obvious ones, but so is which entry is
  being tried and how many drops beneath it have already been refused. Beginning
  again each tick would spend the whole budget on the first candidate forever.
- **Standing a proof up still costs a snapshot, so admission precedes that
  allocation.** A selected request first waits as lightweight intent in FIFO
  order; repeated dispatches retain its place. Each convoy means starts at most
  one proof per tick and retains at most four prepared proofs, including
  finished results awaiting dispatch. Topology invalidation releases obsolete
  prepared state without renewing queue seniority. Waiting is retryable, not a
  route rejection or permission to consume a ticket. Queueing and topology
  restarts share one 900-tick preparation deadline. On expiry the means refuses
  the attempt so another provider can answer; this is an effort policy, not
  proof of geometric unreachability. Deadline retirements share the one-per-tick
  admission allowance, so an expired wave cannot all fall through at once.
  An admitted proof copies
  raw map inputs but
  neither erodes every footprint nor prices every terrain cell nor labels all
  connectivity. Its first search begins on a later tick. A proof nobody has
  asked after for two dispatch cadences is abandoned: the request was dropped
  or served elsewhere, and nothing tells a means it lost.
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
  `HEAVY_APC` enjoyed was an accident of it being the only variant, and the
  spotter pickup is nearly twice as fast. The same law holds on the air
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
  mid-battle closure leaves behind. A living dozer blade is the one vehicle
  that edits terrain, and only in the opening direction, through the map
  editor's destruction path: like every destruction op it can only make the
  map more permissive, so the ordinary opened-cell flush is all it needs.
- A vehicle is a unit with a hull. It reads its durability the way every other
  combat actor does — the shared ownership-coded gauge over its chassis, armor
  row over structure row — for as long as it is alive. A wreck drops the gauge;
  the registered wreck art is the whole report.

A **story column** is an employer-owned group of deployed chassis released by
public scenario conditions. It uses ordinary movement legs, footprint checks,
steering and weapons; it does not obtain player order authority or become a
second reinforcement provider. Delivery of identified freight is confirmed by
physical exit, while destruction retains the ordinary wreck and damage rules.
A story column may drive reactively through a mission driver that authors its
lane polylines and lowers the mission's speed cap (Thunder Run's column driver,
`early-operation-nouns.md`). The driver chooses routes and pace only; chassis
steering, footprint sweep and recovery remain the vehicle's own, and a route
rebuilt mid-leg is an ordinary new corridor.
A **heavy lead** is a story-support tank with finite armor, structure and a
single-cannon magazine. Its independently traversing turret uses normal
ballistic contact and blast payloads, so it can breach a gun line while lighter
escorts screen infantry. It carries no passengers. The tracked silhouette uses
a conservative rolling-turn envelope shared by route proof and body motion;
neutral pivot, track slip and player-owned tank logistics are not modeled.

Its overhead hull has a broad, flat-fronted glacis between the tracks and a recessed
engine deck at the rear, with continuous track tops and layered shoulder
plates. The compact independent turret distinguishes its separate angular cheeks
and narrow mantlet from the rear bustle. Both components retain their authored frame centers so
traverse cannot move the bearing. A short, single barrel follows a centered
ballistic hardpoint; its two-round autoloaded burst retains the existing damage
and cadence. Armor volume comes from sloped planes and bevels,
not a tilted camera or baked ground shadow. Its Abrams/Type 10-inspired shape, faded
ochre paint and replacement panels depict scavenged, century-old Hegemony surplus.
This provenance is Marine Operations art direction, not a claim about vanilla canon.

The tank's exposed tread tops scroll separately from its fixed armor and track
end caps. Accepted signed chassis travel drives their UV phase after the
collision sweep; yaw adds outer-track travel and subtracts inner-track travel.
Reversing reverses the texture motion, and a stopped, blocked or destroyed hull
does not animate. Teleport resets the presentation odometers. This does not
introduce differential steering or track slip into the bicycle motion model.

Tank destruction replaces both registered frames with authored torn-armor wreck
art at the final hull and turret bearings. One bounded blast starts a short roof
fire and a longer rear-engine fire; both cool into finite smoke and leave the
permanent wreck. The tank-specific emitters expire by 90 seconds. These effects
use the shared presentation drains and cause no secondary damage, cover,
concealment or navigation changes.

The tank's two variants each draw from their own sheet with the same four
registered slots. The heavy-gun tank keeps the breakthrough hull exactly and
swaps the turret for the same angular housing carrying one long, thick gun with
a fume-extractor collar and muzzle brake; its ballistic hardpoint follows that
longer barrel. The dozer breacher is the breakthrough hull with a broad blade on
push arms ahead of the glacis; its longer footprint frame draws the hull at the
same on-screen size, so its tread bands are the tank's scaled about the frame
center. In place of the cannon turret it carries the APC's twin roof MG at the APC's
own registration, sitting on the empty turret ring. Both keep authored wreck
frames; the dozer's blade buckles and tears with its hull
(`art-source/vehicles/tank-heavy-gun/`, `art-source/vehicles/tank-dozer/`).

The dozer's **blade** opens ground (`DozerBlade`). A blocking cell is
*pushable* when its structure is spent through the ordinary wall channel — a
wall cell or a sandbag, fence or crate barricade — and is above zero and at most
the blade's threshold of 100. Ordinary city and gated-housing walls fall to it;
fortified and fortress walls (150 and up) stand until fire has worn them under
the threshold. Natural obstructions, registered equipment and entity-owned
fixtures are never pushed. Low crash debris under the hull is graded flat. The
blade bears on a strip just past the nose, across the hull's width, and spends
80 structure a second on every pushable cell in it at once, through the same
destruction path a breaching charge uses: a pushed wall becomes rubble, a
pushed barricade its destroyed art, and the cell is ordinary walkable ground for
every vehicle and unit afterwards. Bearing on pushable ground is progress, so
the wall-contact and stall recovery leave a pushing hull alone; bearing on a
fortified wall is not, and recovery treats it as any other wall.

Route proof for a bladed hull reads a **breach view**: a routing copy of the
grid with every pushable cell open. Its move-order proof and rolling local
planner both use it, so ground the dozer can open is reachable for the dozer and
for nobody else, while its physical sweep still reads the live grid — which is
what holds the hull against a wall until the blade has pushed it down. A troop
carrier ordered across an intact wall is refused; ordered again after the dozer
is through, its ordinary proof finds the gap. Wall cells cost a route dearly, so
the dozer breaches only where going round is dearer. The convoy's dispatched
delivery proof is unchanged; the blade reaches player-ordered and directly
driven hulls. Acceptance is the `dozer-breach` scene: the dozer opens a gap a
troop APC drives through, against a control where the APC alone is refused.

An industrial freight hauler has no passenger or turret payload; its long chassis
and slower handling distinguish cargo from the armed escort. Reinforced story
escorts have their own durability profile rather than changing every ordinary
reinforcement APC. Their fire uses the shared turret and damage model. Mission-owned
travel is dormant outside an explicit allied deployed route; player orders and
ordinary arrive/deboard/depart missions retain their existing lifecycle.

The freight hauler reads as a blunt cab, recessed machinery saddle and long
raised cargo module. Beveled roof planes imply volume from straight overhead;
covered bogies and orange shoulder rails retain its industrial identity.
Its destroyed frame keeps the same center and final heading, with a crushed
cab and torn freight roof over opaque cargo remains. A modest blast starts
separate cab and cargo burns, leaving finite smoke for up to 90 seconds.
It is ordinary freight loss, with no secondary explosions, damage, cover,
concealment or navigation changes.

### The light roles: supply pickup and spotter pickup

Two company hulls exist for a job rather than a fight. Both are the militia
technical's pickup with the pintle gone (`art-source/vehicles/light-roles/`),
and like the technical they are **field rigs** — jerry-rigged civilian chassis,
not any military's issue (`mechs-nouns.md`, vehicle patterns). Both ride a Light
cradle, and both pay for their job with the same three things: **no gun, no
seats, and less plate than the technical**. Neither is ever a combat buff —
nothing either does changes a hit roll, a weapon or a morale check.

The **supply pickup** (`SUPPLY_TRUCK`) carries its crates in the bed. Its stock is a **carried
resupply cache**: the same `ResupplyCache` an emergency-resupply drop sets down,
registered with the battle's one resupply authority (`ResupplySystem`) and
served by its rules — one special use or mech rack round at a time, to the
nearest friendly within reach that needs it, paused while a hostile stands over
it, drawn from a finite stock. The truck adds only placement: the cache rides the
hull, **opens only while the hull is halted**, and is **lost with the hull** when
it is wrecked or leaves the map (`SupplyTruckSystem`). A truck is therefore a cache
that can be driven, never a second supply rule; and because delivery is a cache
rather than a deboard, it supplies whoever stands beside it, not a squad it
brought. Its stock is twice an air-dropped cache's. Acceptance is the
`supply-truck` scene: a spent squad beside the halted truck is resupplied while
one out of reach is not, nothing passes while the truck is moving, and a wreck
ends service.

The **spotter pickup** (`SCOUT_CAR`) is the pickup cut short on a shorter
wheelbase, the fastest hull in the bay and the thinnest, with a salvaged sensor
mast where the gun stood. Its job
is information. The mast is a **squadless observer** (`ScoutSight`): each tick it
is a temporary ground-level fog source for its side when that side's sight is
shown to the player — the same seam a sensor sweep or a recon ping uses — and it
records every hostile combatant it has ordinary line of sight to within its
range, which reaches the side's commander as direct contacts reported by no squad,
as a fort gun's target does. No squad's beliefs are written: the scout tells
command and the player, and a squad learns of what it saw by being sent toward
it. Its sight is plain line of sight from the ground, so it has to be driven
somewhere it can look. Acceptance is the `scout-car` scene: a position no marine
can see is sighted, reaches the commander and is revealed to the player, and a
scout parked behind a wall reports nothing.

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
The light roles are the pattern: a supply hull reaches munitions through the
battle's resupply authority by carrying a cache, and a sight hull reaches the
contact picture through the fog's temporary sources and the commander's
sighting watch; neither owns a parallel rule.
Additional controllable vehicle families need their own hardware and
handling contracts; sharing the APC input session does not supply those contracts. A vehicle-spawned squad normally enters the reinforcement
assignment flow. Conquest is the explicit exception: its defender policy carries
`conquest-defender` ownership through the vehicle mission, and the squad is born
with the requested node-hold or zone-clear objective already under that commander.

# Convoy

Status: ACTIVE — ground delivery, deployed vehicle orders, and direct control share vehicle authority; shared vehicle/infantry interaction and terminal recovery remain extension paths.

Written: 2026-08-23

Updated: 2026-10-27 — a lost drive cripples a hull to a crawl instead of stranding it. Earlier: every hull gun builds flux (hull flux), most at a nominal rate; the haze rises only off authored vents.

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
machine gun: faster and much less durable than an APC, with a mount
magazine its loader refills slowly, no troop capacity, and no faction-specific combat exceptions.
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
is going and the nearer they are. The shove is physical and faction-blind, and
runs after separation so a shove apart cannot put anybody back under a hull.
Emplacements, aircraft and drones do not take part. Every displacement passes
the same topology guard as separation, so a body pinned against a wall stays
where it is and the hull passes. The push is applied to positions; plans learn
of a hull through occupancy instead. A **stopped** hull's footprint, widened by
a body's reach, carries heavy occupancy in the per-tick occupancy map, so every
occupancy-aware route goes round a parked truck rather than through it. A
route into one used to hold its walker at the hull's end for good, stepping in
as fast as the contact pushed him out. A moving hull claims no cells: its bow
wave clears the road, and its cells would change every tick. Hulls still stamp
no walkability, so a route has a way through when nothing else is left.
`-Dbattle.vehicle.repulsion=false` is the control, and takes the occupancy with
it. Everything else the ECS skips by membership-narrowing — no `COMBAT`, no
`MOVEMENT`, no `ROLE`, no `AI_STATE`, so the fire system, the mover, separation
and the planner never see it.

**Mass decides the shove, and the other side is rammed** (`HullRam`). Every hull
and body has a mass in tonnes: a rifleman a tenth, a Runner less, a Brute three
by its stage, a mech ten per unit of its variant's relative mass, a Matriarch
forty, and the hulls from a hover scout's one and a half to a gun tank's
forty-five. A body takes the share of a contact push its mass leaves it, and the
rest comes back onto the hull through its own footprint sweep: a man goes almost
the whole way, a mech meets an APC halfway, and a pickup is held up by a Brute
or a Matriarch rather than driving into her. The bow wave is a courtesy to
friends only. A hostile body on the bow of a moving hull is struck on the first
tick it is there: the two exchange one impulse as point masses at the hull's own
closing speed. A body running into the hull adds nothing, and a parked hull
rams nobody; a Brute's charge into a hull is the charge's own exchange. Each is
hurt by the velocity change it could not shed, past a safe one, through the
shared armour law at a blunt penetration, and the damage grows with the square
root of mass, because health in this game grows much slower than mass. So a
tank through infantry or a Runner pack kills, a technical through the same pack
kills almost as surely, and a technical into a Brute is brought up short and
hurt while the Brute is barely bruised. The body is knocked down or staggered
and the hull slowed and stalled by what each took. Friendly, allied and civilian
bodies are shoved and never hurt. A parked hull's brakes hold it against
anything lighter than itself, which then takes the whole push.

**Mechs are machines on the same terms** (`MechRamSystem`). One mass model
covers both, so a mech against a hull resolves by weight like any other pair: a
Hound at eight tonnes yields to an APC, and a Bulwark at eighteen outweighs one.
- **Rams.** A walking mech rams a hostile body it reaches on the first tick of
  contact, by the same exchange. Between two machines, a mech and a hull or two
  mechs, both drives close the gap, and a hostile mech meets a hull from any
  side, not only on its bow.
- **Shoving bodies.** The separation pass weighs any pair with a mech in it in
  tonnes. What stands ahead of a walking mech is pushed off its line, not
  carried down it: a man to the side he is already on, and dead ahead to the
  mech's left. For two mechs head-on that is each one's own right, so they pass.
- **Hulls.** A walking mech that meets a hull slides round its side and is held
  off the end, instead of standing off the tail or shoving a parked hull down
  the road.

Mech against mech is still the soft separation, with the lighter machine
yielding, plus the existing stall escape (`MechCollisionEscapeSystem`) as the
deadlock backstop.

**Hulls do not drive through each other** (`HullContact`, `HullContactSystem`).
After the ground pass has driven them, every pair of visible, unwrecked hulls
whose footprints come within a skin of each other is resolved. The choice
between a soft separating force and a yield rule is not a choice; each fails
alone. Separation alone is a spring: a column driving into the hull ahead of it
shoves that hull along forever. A yield rule alone deadlocks a head-on meet:
both hulls are closing, and both wait. So both run:
- **Yield.** The closing speed is taken off whoever must yield. A follower takes
  the pace of the hull ahead, a hull crossing another's bow keeps going while
  that one waits, and head-on the lighter hull (on equal mass, the later
  spawned) stops while the other comes on at a crawl.
- **Separation.** What overlap remains is shoved apart along the contact by
  mass, so the lighter hull goes most of the way.
- **Passing.** Hulls nose to nose each pull to their own side at one pace,
  whatever they weigh. Passing is two drivers steering, not a shove. A
  mass-split sideways push puts the light hull against the wall, inside the
  route planner's clearance, and from there its planner refuses to start and
  the order is abandoned.

Every shove is swept on the planner's padded footprint first and on the bare
one only for what neither hull can take that way. The shove never touches a
route: a hull that is held up falls through to the stall ladder like any other.
Hostile hulls do not brake for each other: they close at full speed, ram on the
first touch, and then can go no further. A wreck is not a hull here.
A pair that stays in contact for six seconds without both hulls getting
anywhere is jammed (a lane too narrow for the two plus the planner's clearance,
a column backed into trucks coming out, a truck inching a parked car along a
road it cannot leave): neither planner reverses out, so the pair is let through
each other, as hulls drove before there was contact, until they are clear.

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

An ordinary vehicle is seen and looks, but is never a perceiver. It carries no perception components at all, so
any code that reads a target's sight stats must treat "target" and "perceiver"
as separate roles: the seeing side of a line-of-sight pair reads the fail-loud
sight accessor, and the seen side reads the tolerant one that answers with plain
grid line-of-sight for an entity that has no vision. The same rule covers a
remembered contact that has since become a corpse. Every hull looks, and does so
without becoming a perceiver: it is a squadless observer beside the body
(`HullSightings`; the light roles, below), out to a marine's sight, or to its mast's
where it has one, so the perception components stay off every chassis.

**A vehicle instance is a hull and a loadout** (owner, 2026-10-30). A `VehicleType` is a bare,
generic chassis: its footprint, drive, plate, gun card size and bay. Everything that makes one
machine differ from another of the same hull is equipment: the engine in its engine core, the
plating at its armor point, the part in its gun card, the bank and vent arrays and kits on its
rack. An instance is the hull with one such loadout (`VehicleDeploymentSpec`), and every hull in
a battle is set down from one through the same path (`ConvoyService.spawn(VehicleDeploymentSpec, …)`).
The player designs instances in the Mech Lab, fitting parts to a hull the company owns. An
encounter chooses its instances to hit the difficulty it wants: a deliberately weak one (stock
engine, factory plating, an empty rack, a light part or none) as readily as a strong one, any
loadout the parts allow, not only a named fit. A named instance a mission or evidence reuses is a
**stock fit** (`VehicleStockFit`, below: the convoy escort, the Parapet); it is a loadout with a
name, never hull data, so a new named variant of a hull is a new fit, not a new `VehicleType`.
Only a chassis that differs in its card, bay or body is a hull of its own.

**The APC is one chassis.** `HEAVY_APC` is the troop carrier, six seats and a
small omni gun card; the mortar carrier (`FIRE_SUPPORT_APC`) and the LRM carrier
(`LRM_CARRIER`) are the same chassis with the troop bay given over to a typed gun
card two rows tall (ballistic and missile), and share every footprint, handling and
durability number with it. A tougher or faster APC is not a hull: it is the APC
with plating and an engine at its armor point and engine core (`mechs-nouns.md`),
fitted by a company or named as a **stock fit** a mission fields
(`VehicleStockFit`). The convoy escort is the APC in heavy plating on a military
engine with a spall liner (`VehicleStockFit.CONVOY_ESCORT`): 264
structure behind 224 armor at rating 18, a fifth faster than the bare hull.

`HEAVY_APC` durability is 220 structure behind 160 armor at rating 18. It uses
the shared durability law: low-penetration rifles chip armor slowly, while
marine rockets and mech LRMs defeat it in a few committed hits or one strong
salvo. HP zero is once-only. Any passengers still onboard die except for one
or two faction-rostered infantry who eject into nearby free cells at 25% HP.
The live mission becomes `WRECKED`, its turret and body stop, and stacked or
late impacts cannot repeat wreck or passenger effects. The transition also
queues one compact visual blast and short roof/rear-compartment burns that
cool into smoke, with the last APC emitter expiring by 55 seconds. An APC's
effects stay smaller than the tank's. This is presentation only: no
secondary damage, navigation obstruction or smoke concealment is manufactured.

**A tank's turret is a section of its hull, and its gun can be shot away**
(`HullTurretSection`). It is the mech section law (`mechs-nouns.md`, Section
pools) with one section beside the hull. The turret holds an authored share of
the hull's armor capacity and structure, and an authored share of the hits the
hull takes land on it, rolled on the battle's random stream. The hull's
`HEALTH`/`ARMOR` columns keep the totals, so every whole-body reader (a
health bar, a threat estimate, a matchup's integrity) still sees one hull. A
hit on the turret meets the turret's armor and then its structure. What the
turret cannot hold goes on into the hull, which meets it with its own armor
first, on the plate the hit arrives on (below). A hit on a turret already at
zero goes through the hole to that plate. Every pool resolves against the hull's
one armor rating, as a mech's sections share their chassis's rating. What that
rating and plate are worth on a given hit depends on its arc (below).

- **Only the hull's own structure kills.** The turret's remainder dies with it.
- **The turret at zero destroys the gun, and nothing else.** The hull drives on
  unarmed. The turret stops firing and traversing and stays drawn where it last
  pointed, and it bursts on its ring and smoulders there; a lost drive smokes at
  the running gear and a badly spent hull off its engine deck
  (`combat-durability-nouns.md`, Damage smoke). The hull reads as unarmed to every question that asks: its threat
  reach is zero, a duel band reads it as a machine with no weapons, and it
  leaves `VehicleEngagement`. Direct control keeps the hull but offers no gun.
  A disarmed hull **withdraws**, the way a disarmed mech breaks contact. A hull
  carries one gun, so there is no secondary armament left to fight from cover
  with. Between orders it drives away from every hostile that can still shoot it
  (mech, armed hull or infantry), weighted toward the deepest reach it stands
  inside, until it is six cells outside each one's reach, and holds there. With
  no drivable route it backs off the nearest threat with its nose on it. A player
  order still owns it while the order lasts. `-Dbattle.vehicle.disarmedWithdraw=false`
  is the control, where it holds where it stands.
- **An empty magazine is a different fact.** It ends fire but leaves the gun,
  and only until the loader has put a pack back (the loader, below). The hull
  still threatens what it can see, and its threat reach stays its own.
- **The section is authored per hull** (`VehicleType.turretHitShare` /
  `turretPoolShare`). Today only the gun tanks (the Onager and the laser tank) author one: a fifth of the
  hits, and a quarter of the pools (`heavy-equipment-progression.md`, a tank
  that loses its gun). A hull with no section is one body and its gun
  cannot be lost. That covers the APCs, the technical, the dozer's MG and the
  light roles. Any armed hull may author one later. The turret's emplacement
  durability in the turret catalog is the turret's as an emplacement, and a
  hull never reads it. A turret on a hull is part of the hull's own pools,
  never extra plate on top of them.
- **The roll is drawn only for a hull with a section.** A battle with none
  draws exactly what it drew before. `-Dbattle.vehicle.turretDamage=false` is
  the control with no turret section. `battle.vehicle.turretHitShare` and
  `battle.vehicle.turretPoolShare` override the authored shares for evidence
  runs.

**A hull holds its damage where it lands** (`HullLocations`), as a mech does,
so a flank is a tactic and a damage doll can show something true (owner,
2026-10-20; shipped 2026-10-23). Vehicles join mechs as the deliberate exception
to the law that a mitigation arc is no argument for localized armor
(`combat-durability-nouns.md`).

- **Locations.** Four armor **plates**, `FRONT`, `LEFT`, `RIGHT` and `REAR`;
  the **hull**, the structure behind them and the only pool whose zero is
  death; the **turret** section above (armor and structure, optional); and the
  **drive** (tracks, wheels, lift fans), a structure pool whose zero
  **cripples** the hull: it drives, reverses and turns on at a crawl
  (`Crippled.SPEED_SHARE` of its speed), still traverses its turret and fires,
  and is not dead. Crippled is slowed, never stranded (owner): a hull a story
  or an order needs somewhere still gets there, late. These names are the contract the damage
  doll draws (`direct-control-nouns.md`) and change only in both.
- **Which plate.** A hit meets the plate on the side it arrives from: front and
  rear by the arc (below), a side hit split left or right by which side of the
  hull its origin stands. The arc's rating and plate multipliers still apply to
  that plate, so a front plate is both larger and more effective. What the plate
  cannot hold reaches the hull, and every hit on a side whose plate is gone goes
  straight through, while the other plates stay whole.
- **The drive** takes an authored share of hull hits by arc
  (`VehicleType.driveHitShares`), larger from the sides and rear where the
  running gear shows, rolled on the battle's random stream after the turret
  roll. Such a hit still meets that side's plate first; what gets through
  spends the drive, and what the drive cannot hold goes on into the hull.
- **Sizing.** The front plate holds the whole of the armor a hull of one pool
  had, and the side and rear plates (`VehicleType.plateArmor`, multiples of it)
  and the drive (`driveStructureShare`) are held on top. A hull shot head-on
  lasts as long as it did; splitting the old pool instead made every head-on
  fight about 35% shorter, a broad nerf rather than a flank bonus. The
  `HEALTH`/`ARMOR` columns carry the totals (every plate and the turret's armor;
  hull, turret and drive structure), so whole-body readers still see one
  machine.
- **No origin, front plate.** A hit with no origin meets the front plate and
  never rolls the drive: letting it pick a plate walked unattributed damage
  across all four.
- **A hull that turns its face spreads the damage.** A hull held side- or
  rear-on dies much sooner than a hull of one pool; a free hull that turns its
  front onto its attackers spends the side plates they stripped meanwhile on
  top of the front's whole pool, so a fight it un-flanks runs longer. That is
  accepted as the model, not a defect (`heavy-equipment-progression.md`,
  located plates, which keeps the readings and the alternatives rejected).
- **Prediction reads the location too.** An infantry special's commit gate and
  the projection target choice prices crowding by (`TacticalScoring`) resolve a
  contribution against the plate facing its source and the hull's own
  structure, never the summed columns, which over-read both.

**A hull's plate depends on the arc a hit arrives on** (`ArmorArcs`,
`VehicleType.armorArcs`), so flanking a machine pays. The arc is read from the
bearing of the hit's origin against the facing of the part it lands on: front is
45 degrees either side of the nose, rear 45 degrees either side of the tail, and
each side the ninety between. An arc carries two multipliers. Its **rating**
scales the hull's armor rating, the number penetration is read against, so it
only matters to rounds that do not already out-penetrate the plate. Its
**plate** is what each point of armor counts for on that
arc: thick front plate strips more slowly, thin rear plate faster, whatever the
round. It applies to whichever plate the hit meets (above).

- **One shared profile, per-type overrides.** Every hull's front is rating 1.0
  and plate 1.1, its sides 0.9 / 0.9, its rear 0.75 / 0.7. The tank line has a
  thick glacis (1.0 / 1.15), mild flanks (1.0 / 0.95) and a thin engine deck
  (0.6 / 0.6). The dozer is the tank line without the glacis bonus, and the SRM
  technical's welded sheet is the shared profile without its front bonus. Both
  overrides keep a cheaper or sidegrade hull from beating a dearer one by facing
  alone (`heavy-equipment-progression.md`, balance is not parity).
- **The turret faces its own way.** A hit rolled onto the turret is read against
  the turret's facing, not the hull's. A turret trained on its target shows that
  target its front, so a lone attacker meets the mantlet while a second machine on
  the flank, unwatched by the gun, gets the turret's side or rear. What goes on
  through the turret into the hull meets the hull's own arc.
- **The turret's hit share stays fixed on every arc.** A share that grew with the
  silhouette would also change how often a flanker shoots the gun away, and that
  share was tuned as the gun-loss rate (`heavy-equipment-progression.md`, a tank
  that loses its gun). Arcs are about plate; how the hits divide stays its own
  lever.
- **Origin is where the shooter stands now.** It covers every attributed hit
  alike: an aimed round, an arcing shell, a missile, a blast. A ballistic or
  arcing round's ground track runs from its shooter, and a top-attack round is
  not read as a roof hit: the anti-armor LRM already defeats a stock tank by
  design, and the active-protection kit is that threat's answer. A
  blast is read from its firer, not from where it burst. A hit with no origin
  (unattributed or scripted damage, a shooter already gone) meets the hull's
  nominal plate on no arc, and no arc draws on the random stream.
- **What presents the front.** Production sets a dropped hull down facing the way
  its carrier flew in. A duelling hull drives to its band nose first. Facing a
  threat does not use the neutral pivot (route travel's escape, below), so
  every other turn toward the threat is a manoeuvre on the same manual drive: it **backs with opposite lock** while the
  target is within a little past the beam (120° off the nose), which swings the
  nose on while the hull gives ground, and drives a **forward turn** toward a
  target further behind than that.
  - Holding in band, a hull whose target sits more than 35° off its nose turns
    back onto it, and stops once it is within 10°. A turn that starts forward
    stays forward all the way round. It rehearses the turn on a copy of its own
    body first and makes it only if it ends square on over drivable ground, still
    in sight of the target. A backing turn also stops once it has given ground
    to its band's far edge less slack, where the gun stops reaching. A hull a
    parapet screens from its target (an undrivable cell within five cells on the
    line to it) makes only the backing turn: the forward turn drives it out from
    behind the berm before the nose comes round, and the berm is worth more than
    the plate. `-Dbattle.vehicle.faceInBand=false` is the control.
  - **One front, where it lasts longest.** A hull has one front plate, so with
    several hostiles about it turns onto the one whose bearing, taken as its
    facing, has all of them killing it slowest, each through the plate it would
    hit. A hostile is a machine, an emplaced gun, or a soldier with a weapon of
    his own (a rocket with rounds left, a profiled rifle); one firing off baked
    attack stats has no weapon to weigh and is not counted. Harm is each
    weapon's rounds a second, at its accuracy for the range and its armor
    efficiency against the plate (the arc's rating times the hull's); a gun out
    of its own reach does none. A threat kills through a plate in the time it
    takes to strip what that plate has left (times the arc's plate multiplier)
    and then the structure behind, which a stripped plate lets every point
    reach; a facing's cost is the sum of one over those times. So a machine gun
    that barely marks the glacis is given the front over a cannon that would
    strip a side, two guns on one side get the front and a lone one the tail,
    and a side its attackers have stripped is turned away from them. The facing
    held is kept until another costs a quarter less. The band is still held
    against the duel target. `-Dbattle.vehicle.faceWorstThreat=false` is the
    control (the duel target, and no turn without one).
  - **Standing hulls face too.** A hull with no machine to duel, and an allied
    hull its story script parks (the script owns when and where it moves; which
    way it faces where it stands is the hull's), holds where it stands and makes
    the same rehearsed turn onto the facing above, giving no more than eight
    cells from where it was parked: a tank's widest backing turn gives about
    seven, so one turn fits and turn after turn does not walk it off its mark.
    A hull that has lost its gun still faces, since its plate still matters, and
    so does a crippled one, slowly; an ambusher keeps its cover. A route handed to
    the hull ends any turn it was making. A hull nothing drives brakes rather
    than keeping whatever speed it had.
  - **It turns only toward what it knows of**: a machine in its own sight, or
    one its side's spotters report (`SideSpotting`). A flanker out of sight, or
    come up behind cover, gets its shots on the side until it is seen. There is
    no arc of vision and no reaction delay beyond the hull's one-second
    decision. `-Dbattle.vehicle.faceKnownOnly=false` is the omniscient control.
  - **A turn shakes the gun.** The turret rides the hull, so while the heading
    turns the turret must traverse against it to stay on target. A hull's gun
    loses accuracy in proportion to the share of its turret's traverse rate the
    hull's yaw takes, up to a moving shooter's full penalty (half) once the hull
    turns as fast as the turret can. A tank backing round at full lock (about
    12° a second under a 40° turret) loses about a sixth, and a forward turn
    at speed the full half. It is the body's own yaw, whatever turns it: a turn
    onto a threat, a route's corner, a driver at the wheel. A hull driving
    straight is not shaken. `-Dbattle.vehicle.pivotAccuracy=false` is the
    control.
  - **A crew watches one way, and crossfire rattles it.** The gun keeps the
    machine it locked until that machine dies or leaves its reach or sight,
    and the hull keeps its front and its retreat on the machine it duels, so a
    second machine is not answered while the first lives. Hits from two machines
    whose bearings from the hull are at least 90° apart, each within the last
    ten seconds, put the crew in **crossfire**: its gun fires at a moving
    shooter's accuracy until one side has been quiet that long. Shaking from a
    turn and from crossfire do not stack: the gun is never shakier than a
    moving shooter's. One machine is never a crossfire however it circles, and
    two on one approach are not either, so a pincer pays through attention: one
    machine draws the gun head-on and takes less return fire while the other
    works the side (owner, 2026-10-23, `heavy-equipment-progression.md`,
    crossfire). `Crossfire`; `-Dbattle.vehicle.crossfire=false` is the control.
  - Backing off a too-close target uses the same 120° arc. Only a target further
    behind than that is driven away from, rear on: a forward turn toward it was
    tried and parked hulls out of each other's sight in the street.
    `-Dbattle.vehicle.backingTurnDegrees=90` is the control (the beam).
  - A **disarmed hull still shows its rear** while it drives its withdrawal
    route, and backs off nose-on only where no route exists. Backing out of
    reach nose-on first was measured and rejected: at a tank's reverse speed the
    hull stays in reach about three times as long, and the front plate does not
    buy that back (`heavy-equipment-progression.md`, facing).
  - **A hold that does no harm is not kept.** Being in band is a distance, and
    sight is read cell centre to cell centre, so a hull can hold in band, in
    sight, and put every round into the building corner its target stands in the
    lee of. A hull that has held in band for three volleys of its gun (never less
    than ten seconds) without its target losing any integrity has a **stalled
    hold**. It moves to another firing position in band, at least three cells
    from where it stands and with room around it to drive onto, whose lane to the
    target is clear: the line a round flies and a line either side of it, each
    judged the way the turret judges its own shot. The band's middle is tried
    before its present distance, so a hull at the far slack closes to where its
    gun hits. It drives there through the band without stopping, then holds and
    watches again; with no such position it holds another spell. A turn onto the
    target can still follow. An indirect-fire hull is exempt: its lane is an arc.
    `-Dbattle.vehicle.repositionStalled=false` is the control
    (`heavy-equipment-progression.md`, keeping the front).
- `-Dbattle.vehicle.armorArcs=false` is the control: every arc is the hull's one
  plate, bit for bit the law before arcs.

The turret section is battle durability, so a fitting doll does not own it. A
future vehicle doll (`mech-lab-data-authored-dolls.md`) may draw the turret as
a socket and show its damage, but the shares stay on the hull type, as a mech's
pool shares stay on its body plan rather than on its doll. Vehicles have no
campaign wear (they are variants, not fittings), so nothing is written back:
a gun lost in battle is lost for that battle only.

**A hull's loader refills its magazine** (`GroundTurret.advanceLoader`), the
way a mech's replenisher refills its racks (`mechs-nouns.md`, missile
replenisher). Every armed hull carries a stock loader, sealed in its doll and
staying the hull's own when a weapon is fitted to its gun card (`mechs-nouns.md`, vehicle gun card). Each mount authors how long its loader takes to put back one **pack**,
the weapon's burst (`TurretMountDef.loaderSeconds`): a group of missiles, a
volley of the twin cannon, a machine-gun burst, a single bomb. The loader runs
firing or not, for the whole battle, and never past the magazine, so the
magazine is the ready stock and the loader is the sustained rate. A gun with no
loader time keeps what it was set down with. It runs on every mount of a hull,
secondaries too, and only on a hull: an emplacement sharing the mount keeps its
own magazine. A hull on a fabrication bay's stocks stands empty with its loader
held off, and rolls out full. The cadences are slow beside the guns, so a long
fight still thins a hull's fire: the LRM rack takes 18 s a group (the mech
standard loader's), the SRM rack 12 s, the cannon 10 s a volley, the MG and the
mortar 6 s, the hover pulse emitter 4 s. The lasers' are quicker than their flux
lets them fire (1.2 s and 1.5 s), so a laser tank's limit is its flux, as a
mech laser's is. Two kits make it an upgrade path, each competing for rack
room with the liner and the flux parts (`mechs-nouns.md`, vehicle kit): the **ready
rack** deepens the magazine or a rotary gun's belt (a bigger spike) and the
**accelerated loader** cuts every pack's time to three quarters and feeds a rotary
gun's hopper four thirds as fast (a better sustained rate, by the one loader rule a
mech's loader part follows, `mechs-nouns.md`, missile replenisher). A bigger bank or a
vent array on the rack is the flux upgrade (hull flux, below).

**Every hull gun builds flux** (owner, 2026-10-26; `TurretMountDef.flux`), as a
starship's weapons do, and a hull holds it in **one reservoir** as a mech does
(owner, 2026-10-30; `HullReservoir`): every mount, secondaries included, draws on
it, and it runs the mech's own rule (`mechs-nouns.md`, flux reservoir). A trigger
commits its whole burst's flux on its first round (a rotary gun round by round), a
crew refuses a trigger that would pass 90% of capacity and, once past 75%, holds
until 40%; a driver may spend the rest, and at 95% the hull vents until 55% at its
own dissipation, with no faster vent. Where a hull carries more than one gun, the
mount that best defeats the target's armor is offered the reservoir first
(`MechFireOrder`). The reservoir's capacity and dissipation are frozen at the drop
from the hull (`VehicleDeploymentSpec.flux`); a mount's data authors only what a
round costs, and the frame is the bank and vent arrays on the hull's rack
(`VehicleRack.frame`); an enemy hull runs its stock bank (`HullReservoir.stockFrame`). Flux is not the magazine, so a hull venting still reads as armed to
every engagement question. `-Dbattle.vehicle.hullReservoir=false` is the control:
each mount its own bank, overloading at capacity and venting to empty faster than
it dissipates. How much a gun builds is its character:
- **Nominal** on the plain guns. The machine guns, the tank cannon, the mortar and
  the hover pulse emitter build a little each round and shed it faster than their
  cadence fills it, so a held trigger peaks around a tenth to a fifth of capacity and
  never overloads. Their flux is there to read, not to limit.
- **The limit** on the racks and the lasers. A rack dumped on clicks or a hold runs
  into its flux before its magazine: the LRM rack's three groups fit and a fourth
  does not, and the SRM rack's crew holds before its eight salvos are out. The lasers
  are flux hogs (the laser tanks, below).
The direct-control HUD shows every armed hull's reservoir on the mech's gauge. The haze rises only off vents a hull's art
authors (`VehicleType.fluxVentAftCells`, the laser tanks' radiator fins), so the
plain hulls show no haze. Emplacements sharing a hull's mount never read its flux.

**Active protection is a rack kit** (`VehicleKit.ACTIVE_PROTECTION`; owner, 2026-10-30: one
tank, modular heads). The Parapet, once a hull of its own, is the Onager with this kit on its rack
(`VehicleStockFit.PARAPET`). A gun tank whose rack carries it gets a point-defence mount on the hull
(`ConvoyService.activeProtectionStructureId`, the `structure.tank-aps` mount): the carried-mount
law of `PointDefenseService`, with the hull as the carrier. It is bounded the same way:
it engages a round only while the round is inside its reach, at most once per
cycle, from a finite magazine that is not refilled, and each engagement is a
roll. What it takes is narrower than an emplacement's: **only an arcing,
top-attack round coming down within three cells of the hull.** A flat rocket
such as an SRM is left to the plate, and so is a round falling on somebody
else, so the system shields its own hull and never the infantry around it.
Guns, cannon and lasers fire rounds that point defence cannot engage at all.
The launchers and their power cost the hull six hundredths of its speed and acceleration
(`VehicleKit.speedFactor`). Only the armed gun tanks (the Onager, the laser tank) take it
(`heavy-equipment-progression.md`, the AT LRM against a tank); a stock tank carries none. Its
launcher boxes stand on the hull's rear flanks and a sensor panel on each cheek, drawn over the live
hull whatever head the turret wears (`kit-active-protection.png`, `art-source/vehicles/kit-active-protection`).
The mount lasts the battle, ends with the hull, and is mounted
once: a spent magazine is not refilled. `-Dbattle.vehicle.activeProtection=false`
is the control.

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
route never deliberately threads a static gap the vehicle cannot fit. A cell is
in the mask only when the hull's whole block is walkable **and no wall stands on
an edge inside it**: a fence or low wall along a cell edge leaves both its cells
walkable, but the body collides with it, so a mask of cells alone proved routes
that pinned trucks on fences beside their centre line. Every vehicle planner
reads this one mask (`VehicleClearance.fitsAt`); there is no plain variant. A
question asked during play (a move order, a pull-back, an engagement reposition)
reads the battle's held mask for that grid and width (`VehicleClearances` on
`ConvoyService`), which follows the grid's topology revision, rather than eroding
the whole map per question. Endpoint
snapping accounts for perimeter and wall-adjacent cells removed by clearance.
The sparse result then receives a vehicle-specific turn pass: material vertices
become footprint-checked minimum-radius fillets. A bend that the chassis cannot
drive forward is masked and the bounded cost search tries another corridor;
failure suppresses the dispatch instead of exporting an impossible corner.
The resulting corridor remains advisory: it is not an animation rail.

**Neutral pivot.** A hull can turn on the spot when it has to
(`NeutralPivot`), and how readily depends on what it runs on. A tank runs its
tracks against each other (a half turn in about four and a half seconds) and a
hover skirt yaws on its fans, quicker: for them a pivot is an ordinary
manoeuvre. A wheeled hull spinning in place reads as wrong, so for a truck, an
APC or a technical it is the last resort and nothing plans on it. Even for
tracks a pivot is never how the hull prefers to turn. The turn pass fillets
every bend it can and, for a tracked or hover hull only, keeps a bend sharp
where no fillet fits (the legs are too short, or the arc clips the ground) and
the footprint, with the same slack, clears the whole rotation about the vertex.
A bend sharper than 135° is a route doubling back where its ends snapped, not a
bend in the ground, and is still refused so the search looks elsewhere. The
shorter way round is the only one asked, because a hull turning about its
centre looks the same after half a turn. On ground like The Burn's rides and
woods, where the turning circle alone refused most orders between connected
cells, a tank's route exists wherever it can stand at its bends and turn. A
wheeled hull's routes stay its turning circle's. `-Dbattle.vehicle.neutralPivot=false`
is the control.

The controller continuously tracks a rolling, local forward-only,
kinematically feasible trajectory against the live navigation grid. Rolling
goals carry the corridor tangent and require heading agreement, so proximity
before a bend is not false success. Reverse is an explicit committed recovery,
not a hidden cusp in an ordinary plan. The bicycle body, speed-aware lookahead,
corner-speed governor, and terminal docking maneuver make turns continuous.
Where the rolling planner finds no forward trajectory, a tracked or hover hull
does not brake, nor back and fill a turnaround: it follows the corridor itself
with a short carrot, driving at
it while it points within 20° and otherwise stopping and pivoting until it
points within 3°. Every step is swept against the live ground like any other
move, so the corridor's proof guides the hull and the ground decides; a pivot
that would clip leaves the hull to the turnaround and the ordinary brake,
reroute and give-up. A dozer turns through ground its blade could push as it
drives through it, holding the turn while its hull grinds that ground down.
Turning toward the corridor counts as progress for the stall clock, and the
forward planner is asked again at its ordinary cadence, taking the hull back
the moment a forward plan exists. A wheeled hull brakes, reroutes, and only
after four seconds braked with its reroute spent follows its corridor the same
way: the last rung before an order is given up, turning in place beating
standing still for the rest of the battle. A pivot reports its yaw to the
hull's gun the way a driven turn does (what presents the front).

**Brushing soft ground.** What a hull still cannot do once pivots, order ends
judged from where it stands, refused-goal memory and stands have all had their
say is mostly a footprint that would fit but for a corner in a bush, a crown's
edge or a fence. A real driver shoves through that, and a move order's hull
does too, as its last rung (`SoftBrush`). Soft ground is a closed natural cell
the creep or youth has made growth (`CRUSHABLE`; a lone boulder carries the tag
too but is no natural solid, and stays hard), the outer crown of a standing
tree for a medium or heavy hull (a crown cell outside its trunk, the trunk
being the cells `CreepVegetation` keeps blocking a dead tree by), and a piece of
industrial fence. For a hull whose tracks flatten growth that growth is open
ground already. Brushing is two checks with the footprint test that exists:
the whole footprint clears the hull's ground with its soft ground opened, so it
touches nothing hard, and the footprint inset by the brush depth (0.3 of a
cell) clears the real ground, so it touches soft ground no deeper; a sweep
keeps the shorter proven travel of the two. The controller brushes once a move
order has been braked six seconds (the order is given up at eight): a tracked
or hover hull follows its corridor by pivot as above, a wheeled one crawls it on
its wheels, both at 0.3 of top speed, and the failure clock holds while it
brushes on. The forward planner takes the hull back the moment it finds a
trajectory, and a hull that arrives brushing arrives as it stands. The order's
route proof has the same rung: an order refused strictly has its cheapest
route over the clearance mask refined once more with its footprint judged
brushing (slackened whole against the soft view, exact and inset against the
real ground), and a route accepted that way is brushed from the start. The
clearance mask never opens soft ground, so no route is found through a hedge,
only one that grazes it; a bend the chassis cannot make is refused either way.
The ground is left as it was: fences stay standing and nothing is scraped.
`-Dbattle.vehicle.softBrush=false` is the control.
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
room for a new forward plan, and so does a hull with no plan at all from where
it stands: once its reroute has been tried and it has held two seconds planless
(not held there by its driver), it backs off as far as is clear behind and
plans again from there, before a wheeled hull's last-resort pivot (a truck
nosed into a house in a town's loading yard otherwise stood all battle, its
convoy waiting behind it). Loss of corridor progress invokes a cost-field
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

## Direct control of a deployed vehicle

The session in `direct-control-nouns.md` may temporarily own an exact live
Marine-faction vehicle of any type in DEPLOYED state. Delivery legs, non-Marine hulls, and terminal
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

The independent roof turret keeps one magazine, loader and cadence authority. Mouse
bearing drives its authored traverse, and a held primary trigger fires only
when ordinary alignment, cooldown, ammunition, and burst gates permit. A burst
retains its world point; later cursor movement applies to the next trigger.
Manual ownership suppresses this hull's autonomous acquisition and trigger,
while other vehicle turrets remain autonomous. An autonomous turret with nothing
to shoot, or nothing left to shoot with, settles back onto the hull's heading at
half its traverse; its facing is a world bearing, so otherwise it keeps the
bearing its last target left it on while the hull turns beneath it. Entry, exit, and input
suspension discard queued rounds and targets without resetting ammunition or
cooldown. The actual posed hardpoint supplies the ground ballistic source, and
the structural body-to-barrel check prevents launching beyond a wall.

**There is one machine gun.** Every machine-gun hull (the APC, the militia
technical, the dozer) carries the mech light machine gun as its own gun
(`VehicleType.ownGun`): the rotary, held-trigger gun a mech wears, fed from its
belt, in the hull's heavy-MG carriage (traverse, flux bank, hit shares) and
drawn as a fitted weapon is, on the hull's mount ring. The heavy MG survives only
as the emplacement and boat door gun. A belt-fed gun has no magazine, but its belt
and hopper are fed: the ready rack lengthens the belt by half and the accelerated
loader feeds the hopper a third faster, the item-based way these hulls close on the
old heavy MG, whose base numbers the light machine gun does not take (owner,
2026-10-30).

The APC hull and its roof gun are separate overhead silhouettes. The tapered
nose and sloped glacis distinguish the front from the broad troop compartment
and squared rear ramp, including when the gun faces elsewhere. The roof bearing
is on the chassis centerline and the gun rotates about its own frame center;
the retained source registration owns their measured placement.
The visible muzzle follows the fitted weapon's own geometry through
independent hull and turret rotation (`art-source/vehicles/apc/`).

The six exposed tire tops use the same distance-driven tread presentation as
the tank. Each wheel retains its own silhouette and the gaps remain open;
accepted chassis travel and yaw move only the tread material. Reverse reverses
the texture motion, stopped or blocked hulls stay still, and teleport resets
its phase. Steering and traction remain the ordinary vehicle motion model.
Every APC variant retains separately registered wreck hull and MG art at their
final bearings, with torn troop roofs, damaged tires and bent turret plates.
The
fire-support APC and the LRM carrier keep the hull and carry their own turret
frames at the hull's scale (`art-source/vehicles/mortar-carrier/`,
`art-source/vehicles/missile-carriers/`). A wrecked turret is always a damage edit
of its live art, torn and gutted, never the live frame darkened.
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
  swing, and lateral room is the scarce thing. Every candidate path is tried,
  shortest first, not only the shortest, and where none fits where the hull
  stands it may first back straight up a few cells, toward where a blind end's
  road usually widens. The attempt is bounded, because a pose no maneuver can
  rescue must cost a couple of tries rather than loop.
- An order's route is proved from where the hull stands. The proof samples the
  corridor with a margin for tracking error, and a hull already standing at the
  start has made none, so within a hull's length of the start its footprint
  need only clear exactly; where even that clips on the first leg's heading, the
  route is accepted if the hull can back and fill onto it from its real pose
  (the departure turnaround below). Within a hull's length of the end, ground
  the margin cannot stand on shortens the route to the last pose that clears
  it, so the hull arrives a little short rather than being refused. Dispatch
  keeps both ends to the margin, because its ends are drop points and map
  edges, not hulls and clicks. Each bend the proof cannot drive is masked off
  and the search tried again; where those masks have cut the start off from
  the goal, as at the mouth of a narrow gap met along the wall it opens in, an
  order's search tries once more with each mask moved a few cells back along
  the leg that ran into its bend, so the route keeps the gap and comes at it
  from further back. Dispatch keeps its refusals and moves on to its next drop.
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
  not quite there." Nearest means nearest the hull can get to from where it
  stands: on the live grid the destination is the closest cell in the hull's
  own clearance component (`ClearanceComponents`), so a click into a walled
  yard or a post across water drives to its edge rather than being refused,
  and an engagement's firing bearing behind a wall costs no flood. Only a
  request with nothing reachable near it is refused for its ground; the rest
  of what is refused is the turning circle.
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
- **A passenger is somewhere, but not on the ground.** Asked where it is, a
  passenger answers its vehicle's position: the squad in the APC is where the
  APC is, so a camera, an order line, a squad's centre or a commander's
  influence reads it there and nothing that asks the question can fail on it.
  Asked whether it is standing here, it answers no: a passenger takes no
  splash (the hull does, and what reaches the people inside is the wreck's to
  decide), holds and contests no ground, is no one's target, casts no sight and
  is not drawn. Every pass that asks the second question skips riders by
  name; reading the position is not a substitute for asking it. A passenger
  stays registered for sight while aboard and casts again the moment it is set
  down.
- A ride ends a squad's objective and leaves its command claim alone. The
  objective is a statement about a place, and a ride across the battlefield
  makes it stale by construction: a squad that kept it would dismount and walk
  straight back the way it was carried. The claim is untouched because nothing
  about who owns the squad changed — a claim is handed off when it changes
  hands, and being carried is not that.
- A chassis need not be on an errand at all. A **deployed** vehicle is one put
  on the field with no delivery to run: it holds where it was set down until it
  is told to go somewhere, or until armor comes into view. That is what a
  vehicle the player owns is doing between orders, and it is what makes delivery
  one of the things a vehicle can be doing rather than the whole of what a
  vehicle is. An armed deployed hull with no order and no story route **engages
  armor** on its own (`VehicleEngagement`): the nearest hostile mech or ground
  vehicle it can see within one and a half times its gun's reach is fought from
  the duel band the mechs use (`mechs-nouns.md`, Duel range), read from its
  turret against that machine's weapons. It drives to the band and stops there
  for the turret, for as long as the turret wears the target down: a hold that
  has done no harm for three volleys is stalled, and the hull moves to another
  firing position in band with a clear lane (what presents the front). A single
  gun has no shorter band, so it never closes into contact. Too close, it backs off with its nose kept on the target through the
  manual-drive physics, because a hull cannot reverse along a route and a
  planned retreat would begin with a turnaround toward the enemy. A hull
  reverses at half its top speed, and a tank at 0.3 of it: slower than a
  Bulwark walks, so a mech that closes on a tank catches it rather than being
  kited out of its own reach (`heavy-equipment-progression.md`, a tank against
  the heavy mech). A player
  order owns the chassis while it lasts and an arrived one hands back to this;
  allied hulls their scripts own (an allied hull a mission has *enlisted* under
  allied command is not one, and fights as the player's do; `allied-command.md`),
  delivery legs, unarmed hulls, shot-away guns (which
  withdraw instead) and empty magazines never engage, and infantry is not a duel target. A hull's **stance** adjusts the band
  (`VehicleType.Stance`): a dueller takes it as chosen; a **stand-off** hull holds
  beyond the target's reach where its gun out-reaches it and at the outer edge of
  its own reach otherwise, never closer; an **ambusher** never drives at armor and
  holds where it was set down (the missile carriers, below). A hull on its own gun has its type's stance; a hull
  carrying a weapon in its gun card takes the one the weapon asks for (`VehicleStance`: an indirect-fire weapon
  stands off, taking a spotter's call; a direct anti-armor rack ambushes; anything else duels), which gives every
  stock hull the stance its type states. `-Dbattle.vehicle.duelRange=false`
  is the control. Its route arrays are
  degenerate on purpose — fabricating a journey it never took would give it an
  errand to resume the moment an order was released.
- Only the player's own chassis takes the player's orders. Orders are queued by
  entity id, so the order system is where that is decided rather than the
  picker; an enemy vehicle is a target, not a unit, and asking it to move is
  refused the same way any other impossible order is.
- **Orders are planned from a queue, a few a tick.** Proving a drivable route is
  the expensive part of an order, and orders arrive in blocks: a restocked lane
  hands every fresh hull its post at once, and their refusals and pull-backs
  follow in step. A tick plans at most its allowance of routes
  (`-Dbattle.vehicle.orderPlansPerTick`, default 2; 0 plans everything at once),
  the player's own hulls before enlisted allied ones and otherwise oldest first,
  and what does not fit waits at the head of the queue, its hull still driving
  any order it already holds. Asking again for a hull that is waiting changes
  its destination and keeps its place. The allowance counts plans, not time, so
  a replay plans the same orders on the same ticks.
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
  vehicle straight back to the errand it was on. The one order that also
  directs the turret is the **assault** an armed hull is given by a click on a
  garrisonable its side believes is enemy-held (`ai-nouns.md`, machine
  assault): it drives to a stand-off, holds there rather than handing back on
  arrival, and puts the gun on the believed occupants and the walls sheltering
  them until nobody is believed inside. Its sibling is the **guard** a click on a
  garrisonable its own side mans gives (`ai-nouns.md`, machine guard): the hull
  drives to a cell beside the place, off the garrison's lanes, holds there, and
  rests its idle gun along the believed threat axis rather than over its nose;
  the turret keeps its own pick, and the guard ends once the garrison has gone.
  A hull keeps the cell it drove to, turning its gun rather than its ground.
- A move order may name the heading to finish on: a right-drag goes to where
  the drag began and faces the way it went. That heading is an arrival goal like
  a delivery's departure heading, earned the same way — the terminal
  Reeds-Shepp docking maneuver, which may back and fill, including on the spot
  for a vehicle already standing at its destination. Where no feasible maneuver
  fits the ground a tracked or hover hull pivots onto the heading at its
  destination; a wheeled hull, or one whose footprint cannot turn there, arrives
  on the heading it drove in on, as an undragged order does; the heading is a
  goal, never a reason to refuse the move. Dispatch's turn-round questions (the
  docking pairing and the drop-point turn onto the exit) are likewise answered
  yes for a tracked or hover hull with room to pivot where it stands; a wheeled
  hull's last resort is never something dispatch plans on.
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
  crosses **hard** ground (a wall, a building, a barricade, water it does not
  float on, a tree's trunk, a boulder, another hull, the map edge) and never
  snaps through a corner. As the last rung before a move order is given up it
  may **brush soft ground** (growth it cannot flatten, a tree's outer crown for
  a medium or heavy hull, an industrial fence) by at most 0.3 of a cell at any
  point of its footprint, at a crawl, without damage and without editing the
  map (Brushing soft ground). Dispatch, docking and allied command's stands never
  plan on it. The durable terminal outcome for an unrecoverable route remains
  open.
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
where no rolling turn fits it pivots like any hull (Neutral pivot); track
slip and player-owned tank logistics are not modeled.

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

The tank's variants each draw from their own sheet with the same four
registered slots. The dozer breacher is the breakthrough hull with a broad blade on
push arms ahead of the glacis; its longer footprint frame draws the hull at the
same on-screen size, so its tread bands are the tank's scaled about the frame
center. In place of the cannon turret it carries the light machine gun every
machine-gun hull carries, on its mount ring. Both keep authored wreck
frames; the dozer's blade buckles and tears with its hull
(`art-source/vehicles/tank-dozer/`). There is no long-gun tank: the heavy-gun
variant was removed (owner, 2026-10-22) because its single long barrel read as
goofy at game scale; the tank line's anti-armor answer is the breakthrough cannon.

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
every vehicle and unit afterwards. A dozer driving onto what it pushes also
spends its **momentum** on it (`HullMomentum`): half its mass in tonnes times
its speed squared, at 10.4 structure apiece, so 360 at its top speed. Only the
cells its nose reaches that tick take it. Where its momentum covers all they
have left they fall at once and it drives on at the speed the rest gives: one
crate barricade checks it to about nine tenths of its way, a row of housing
wall across its blade to about half. Where it does not, they share it and the
dozer is brought up, for the blade to finish at its rate: a row of city wall
stops it just short. Where its hull rather than its blade is held by
pushable cells, its tail or flank swung into a house wall as it turns, and
nothing else holds it, it grinds them down at the same rate: its route counts
that ground open, so its turns may sweep there. Bearing on pushable ground is
progress, so the wall-contact and stall recovery leave a pushing hull alone;
bearing on a fortified wall is not, and recovery treats it as any other wall.

Route proof for a bladed hull reads a **breach view**: a routing copy of the
grid with every pushable cell open. Its move-order proof and rolling local
planner both use it, so ground the dozer can open is reachable for the dozer and
for nobody else, while its physical sweep still reads the live grid — which is
what holds the hull against a wall until the blade has pushed it down. A troop
carrier ordered across an intact wall goes no farther than its near side;
ordered again after the dozer is through, its ordinary proof finds the gap. Wall cells cost a route dearly, so
the dozer breaches only where going round is dearer. The convoy's dispatched
delivery proof is unchanged; the blade reaches player-ordered and directly
driven hulls. Acceptance is the `dozer-breach` scene: the dozer opens a gap a
troop APC drives through, against a control where the APC alone is held at the
wall.

A medium or heavy hull's **tracks** flatten undergrowth (`HeavyTracks`). A cell is
*crushable* while it is closed and tagged so: a young tree (the pine and the
small oak), any tree the creep has killed, a patch of forest undergrowth
(`mapgen-nouns.md`, Authored forests), or a lone large boulder. A cell under
two crowns is crushable only while every standing tree on it is undergrowth, so
a dead tree leaning into a living oak holds where they meet, and a summit cell
never is. Infantry, light hulls (the pickups, the scout car and the hauler) and hover
hulls are stopped by it as by any natural solid. Every hull of medium weight
or more (the APC lines, the rocket technical, the dozer and the gun tanks)
drives into it, so an APC is never trapped in a wood a tank can leave:
whatever crushable cells its footprint covers after a move are crushed through
the map editor's destruction path, so a tree falls whole to its felled art, a
boulder to its rubble, and the ground is open for everyone afterwards. Each
crushed cell takes 120 structure of the hull's momentum, as the blade's
obstacles do, so a gun tank flattens a sapling barely slowed and an APC loses a
tenth of its way to one and half to a thicket of three at once: a wood is driven
through in lurches, the lighter the hull the harder. Undergrowth gives at once, so unlike the blade's
breach view the tracks' **crush view** is read by planning and motion alike, as
a hover hull's skirt view is. It keeps its identity and catches up with the
live grid's change log cell by cell, so the clearance mask over it catches up
too rather than being rebuilt each time the map moves, and a move order still
snaps to the nearest cell the hull can reach. Crushable ground is priced as
rubble, so a tank goes round a thicket where going round is cheap and through it
where it is not. Living mature trees, mineral formations, hills and every wall
stay solid; the dozer's breach view is laid over its crush view. Small and
medium rocks, shrubs and grass tufts were open ground for every hull already.
Living young trees give way as dead ones do (owner's pick, over dead trees
only), which the forest generator relies on when it replants a tank lane with
young trees; `-Dbattle.vehicle.crushUndergrowth=false` is the control. Acceptance is the `tank-undergrowth` scene: a tank ordered
across young or creep-killed wood drives through it, felling what it touches
(a dead tree falls flattened, a stump and short butts, `colony-breach-nouns.md`), while a
living mature wood holds every hull, and a scout car is held by the young wood
that a troop APC crushes through.

A medium or heavy hull **rams** light barricades (`BarricadeRam`; owner's pick,
2026-10-27). A cell is *rammable* while it is closed and holds a sandbag, fence or
crate piece of structure 100 or less that is not a fort's or an objective's works:
works are rammed by nothing, as a Brute's charge rams them rather than bowling them
over. Every hull that crushes undergrowth but the dozer, whose blade already pushes
barricades down, drives into rammable cells spending its **momentum** on them, as
the blade does: where its way covers all the pieces its footprint reaches they fall
at once to their destroyed art and it drives on at the speed the rest gives, so an
APC at full speed keeps about nine tenths of its way through one piece and a gun
tank barely notices a row; where it does not, they share its way and it is brought
up. A hull held by rammable cells and nothing else shoves at them at 40 structure a
second until they give, and that counts as progress for its recovery, so a hull that
came at a barricade slowly still gets through, slowly. Each hull plans over its own
**breach view**: its crush view with every rammable cell opened whose structure its
top speed carries, kept and caught up with the live grid cell by cell as the crush
view is; its sweep still reads the crush view, where the piece stands until it is
rammed. Rammable ground costs a route more than rubble, so a hull goes round a
barricade where going round is short. `-Dbattle.vehicle.ramBarricades=false` is the
control. Acceptance is the `vehicle-ram` scene's `ram-line` loops: an APC and a gun
tank cross a sandbag and fence line with no way round, slowed and not stopped, where
the control APC is held at it.

A hull that runs into something that stops it is heard: a stopped move at half
a cell a second or more is a **bump** (`GroundBody.recordBlocked`), and the
machinery audio plays a heavy impact for it (`MachineAudio`, vanilla's
ship-on-rock collision), louder the faster it hit and deeper the heavier the
hull. A hull held against a wall creeps up from a standstill each tick, so it
bumps once as it hits, not again while it pushes. There is no automatic back-off
for a wedged, manually driven hull (owner, 2026-10-25).

An industrial freight hauler has no passenger or turret payload; its long chassis
and slower handling distinguish cargo from the armed escort. Story escorts are
the APC in the convoy-escort stock fit rather than a hull of their own, so an
ordinary reinforcement APC keeps the bare chassis. Their fire uses the shared turret and damage model. Mission-owned
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
resupply cache**: a `ResupplyCache`,
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
is information, and its mast sees past a crew's eyes. Every hull is a
**squadless observer** out to `observeCells()`, a marine's sight
unless a mast reaches farther. To the player it is a fog contributor like a soldier
or a mech (`fog-of-war-nouns.md`), cached and refreshed by cohort. To command each
tick (`HullSightings`) it records every hostile combatant it has ordinary line of
sight to within its range, which reaches the side's commander as direct contacts
reported by no squad, as a fort gun's target does. No squad's beliefs are written: the scout tells
command and the player, and a squad learns of what it saw by being sent toward
it. Its sight is plain line of sight from the ground, so it has to be driven
somewhere it can look. Acceptance is the `scout-car` scene: a position no marine
can see is sighted, reaches the commander and is revealed to the player, and a
scout parked behind a wall reports nothing.

### The missile carriers: LRM carrier and SRM technical

Two hulls carry the mechs' **anti-armor racks** (`mechs-nouns.md`, Missile
warheads) on a turret: the same warhead, reach and cadence, as turret-mount copies
of the mech weapons. Both mounts fire **only at hardened targets** (turrets, hubs,
vehicles, heavy mechs), as a mech's AT rack does, and hold fire at men; a contact
warhead spent on a rifleman is one the next tank does not get. A turret rocket
carries its contact warhead on its detonation, so the shaped charge lands on the
body it was aimed at and point defence that stops the rocket stops the charge.
There is **no rack choice**: a vehicle is a fixed variant, so the anti-armor
warhead is each hull's identity, and the fragmentation rack stays a mech fitting.

The **LRM carrier** (`LRM_CARRIER`, Advanced, Medium lift) is the APC chassis
with the troop bay given over to one mech LRM-15 pod on a turntable, which stands up on its
tail hinge to fire
(`art-source/vehicles/missile-carriers/`). Like the mech's rack it is **three groups of
five**: a click is a group, and a click while the last group's cooldown still runs, or a
held button, fires the next loaded group as soon as the last has gone (`SalvoChain`), so
three clicks are the whole rack. Its cooldown is no wait between its own groups. What
bounds it is its **flux** (hull flux, above): the three groups fit
inside it, and a fourth from a ready rack overloads the launcher and holds it while it
vents, unless a bigger bank makes room. Its loader brings a group back every 18 s. It is the APC line's long arm against
armor, where the fire-support APC is its steadier arm against anything. Its rounds
arc, and it fires **on a spotter's call** at a machine it cannot see itself:
somebody else on its side must be looking at the target right now
(`SideObservers`) — a hull with the target in its own sight range and line of
sight (any vehicle, the scouts farthest), a soldier or mech within 34 cells with
line of sight (a mortar's spotter reach), or a comms operator with it in sight.
Nothing is a belief or a memory, so a carrier holds fire on a tank that ducked out
of everyone's view rather than shelling where it was. A spotted round is an
observed round and flies at the weapon's ordinary accuracy. It fights from
**stand-off**, and an indirect hull plans to a band position without needing its
own line of sight there. It pays with no seats. Acceptance is the `missile-carriers` scene: a carrier behind a
wall hits a tank a spotter pickup is looking at, holds fire at riflemen in its own
sight, and holds fire entirely without the spotter. A carrier fitted with the
**sensor drone pair** kit (`VehicleKit.SENSOR_PAIR`) needs no spotter: its two
drones search the ground round it, and what they see counts as eyes on the
target (`SideObservers`), so it fires over a wall with nobody else looking.

The **fire-support APC** (`FIRE_SUPPORT_APC`, the mortar carrier) is the same chassis
with a 120 mm mortar on an open roof turntable in place of the seats. It fires the
same way as the LRM carrier: rounds that arc onto anything somebody on its side is
looking at (`SideObservers`), from stand-off. Unlike the carrier it fires on men
as readily as on machines. One bomb every few seconds from a deep rack hits far
softer than the launcher's fifteen missiles, but it keeps falling while the launcher
waits on its loader: the LRM wins the short fight against armor and the mortar the
long one. Acceptance for its shelling is the `fire-support-apc` scene.

The **SRM technical** (`SRM_TECHNICAL`, Common, Light lift) is a field rig: the
pickup with a mech SRM-5 pod on its pintle ring and the technical's welded plate
left off to make the weight. Its rack reaches 18 cells, so it never drives at a
tank: it is an **ambusher**, holding where it was set down, behind cover, and
firing on the armor that comes into its reach. Cheap and fragile, a filler for a
small lift budget that can still hurt a tank that drives past. Acceptance is the
same scene: a technical behind cover holds still as a tank drives down the road
and fires once it is inside its reach.

### The laser tanks

The **burst laser tank** (`LASER_TANK`, Prestige, Heavy lift) is the breakthrough hull with
a ship's burst laser where the twin cannons were. In orbit that emitter is point
defence, because it barely marks a warship's plate; on the ground almost nothing it
meets is plate like that. It is a rung of the tank line, not a point-defence system:
it does not engage rounds in flight. Hostile rocket and shell interception stays
with the hull systems built for it (the active-protection kit).

**It does reach the air** (`TurretMountDef.engagesAir`, a mount law any vehicle gun
may author). A hostile craft in the air within the gun's reach, with a clear line
up, takes the gun ahead of the ground pick, because it is overhead for seconds and
the ground will still be there. A man close enough to be self-defence keeps the gun.
It is the same gun on the same magazine, cadence and bank, so a craft it takes is
ground it leaves. Its rounds into the air are rolled and landed the way an anti-air
emplacement's flak is (`VehicleAirFire`, answered by the emplacements' own pass).
Against a light craft the burst laser is deadlier than a flak position.

It answers a target in a short, accurate burst and turns quickly, so it clears
infantry and drones far faster than a cannon tank. Its rounds carry **no blast and
no wall damage**, so it cannot open a building or answer an assault's wall
directive, and its low penetration leaves it chipping at armor.

What keeps it from out-trading a cannon tank is **flux** (hull flux, above), which
every hull gun builds but a laser builds hardest. The burst laser is a
flux hog, as it is in space: it fires far faster than it dissipates, so a section
dies inside one charge-up, while a tank sees it fall silent and vent for seconds at a
time long before the tank is dead. The flux is visible, as a starship's is: a purple
haze thickens over the hull's radiator fins as it builds
(`VehicleType.fluxVentAftCells`), and an overloaded gun vents a bright lavender plume
off them. A mech's reservoir shows in the same look (`FluxHaze`, `mechs-nouns.md`).

The power plant takes the shell stowage's room and weight, so the hull is a little
faster than the breakthrough tank and carries a little less plate.

It is Tri-Tachyon's tank, and it looks it (`art-source/vehicles/tank-laser/`): the
breakthrough hull in the company's white livery, a sloped glacis under a crew window,
and a capacitor pack of ten cells over the rear deck where the engine grilles were. Its
turret is the space game's own PD laser, up-res'd from the vanilla sprite and set on a
ship's turret mount in the deck, small for a tank turret, so a player recognises the
weapon from fleet combat. The turret has a **firing frame** (`VehicleType.turretFiringFrame`,
a law any sheet may author): while the gun is engaged, with a target on the ground or in
the air or a burst under way, its dome's shutter stands open on the lens; idle, it closes.
Its lance is the burst PD laser's blue.

The PD laser tank is retired (owner, 2026-10-28): its gun is a part. The plain PD laser every frigate
carries is the PD family of laser parts, and the laser tank's own gun is the burst family, each in three sizes
(`MechWeaponComponent`: `LIGHT_PULSE_LASER`, `PD_LASER`, `HEAVY_PD_LASER`; `LIGHT_BURST_LASER`, `MEDIUM_BURST_LASER`,
`BURST_LASER`), fitted to the laser tank's red three-row gun card like any mech weapon (`vehicle-card-sizes.md`,
`mechs-nouns.md`). A laser tank with the heavy PD laser fitted wears the red turret head the PD laser tank carried, and is
what the PD laser tank was: a steady lance, fourteen pulses at a time, that runs cool and trades longer where the heavy
burst laser, in the white head, trades harder. The lighter parts are drawn short, as turrets, each in a round base on the ring's collar with its muzzle just past the ring.
Everything above about reach, the air and carrying no blast holds for
both. The laser tank has no default turret head (owner, 2026-10-29): with no part fitted it shows its bare machinery turret ring and has no gun, and a spawn that names no part (a scene, a mission) fits the heavy burst laser (`VehicleType.standardPart`).

**A laser's lance is drawn as the space game draws a beam.** A turret weapon with no
projectile art that authors a beam (`WeaponDef.hitscanBeam`) is a hitscan beam, as a
mech's is. **A beam leaves along its turret, not toward its target.** Each pulse is
aimed down the turret's facing at the target's distance, pulled short by how far the
turret is still off, and traced as any round is, so it strikes the first body or wall
on its way and otherwise scorches the ground. The turret tracks its target through a
held lance, and a beam opens wider off its target than a gun fires
(`TurretAim.BEAM_OPEN_ARC_DEG`), so a lance that opens off target lands short and
drags across onto it, striking whatever it crosses. A beam may author a **texture** (`WeaponDef.BeamTexture`), after the game's
ROUGH beam texture: knots of energy ride down a continuous lance from the muzzle, and
the lance swells and fades with the power delivered to it. The knots run on one
presentation clock, so a stream of short overlapping pulses reads as one lance being
fed: the PD laser's held red throbs, the burst laser's blue comes in bursts.
It is heard the same way: a weapon that authors `audio.loopSound` holds the loop while pulses keep
arriving, after its fire clip (if it names one) once as the lance starts. The PD laser holds the
tactical laser's `laser_loop` with no intro, since a lance every second and a quarter would repeat the
wind-up; the burst laser is heard one `burst_pd_fire` per shot.

Acceptance is the `tank-versus-infantry` scene (each laser fires at men, and none of
its rounds carries blast or wall damage) and the `tank-versus-air` scene (each laser
fires into the air at a hostile gunship, the cannon tank never does, and with men in
reach as well the burst laser still fires at the ground).

### The hover line: hover scout and drone skimmer

Tri-Tachyon's company hulls are purpose-built corporate issue, not field rigs: a
lift-fan skirt under a composite shell (`art-source/vehicles/tt-hover/`). The lore
anchor is the Sector's own "mechs, tanks, and hovercraft" and the company's security
drones; its ground drones are remote-controlled from a crewed hull, never free hunters,
which is the line the Sector's AI law draws.

**Hover** is a capability of the hull (`VehicleType.isHover`), and it is a trade. The
skirt floats over open water and rides rubble and low crash debris at full speed
(`HoverSkirt`): its **hover view** is a routing copy of the grid with water open and the
debris tag cleared, and a hover hull's move-order proof, rolling planner, recovery and
physical sweep all read it, because the hull really does cross that water. That differs
from the dozer's breach view, which only route proof reads while the sweep holds the
hull against a standing wall. Nothing else opens: walls, natural obstructions, low cover
and a closed shore edge still stop it, and nobody else gains the cells. Its route cost
prices water and rubble as open ground. What the skirt gives up is **purchase**: a hover
hull repels nobody (`VehicleType.repelsBodies`), so it cannot clear a road by driving
into people, and it pays in plate, thinner than the technical's. Kinematics stay the
shared bounded-curvature bicycle (a tighter turn, a faster slew); a drifting or
holonomic body would break the curvature contract every planner shares. Its tread-UV
presentation is off, its shadow lies apart from it because it rides clear of the ground,
and a wreck burns in the lift fans aft.

A hull with a **drone bay** (`VehicleType.droneBay`) carries a **tethered flight**
(`DroneWing`, `DroneBaySystem`). Once the hull is on the field its bay launches the
drones one at a time. Each is an ordinary `battle.drone` unit flown by the shared swarm
action, remote-controlled from the hull rather than a free hunter. A drone's **role**
(`DroneRole`) decides only where the wing sends it, how it flies, whether it fires, and
what ordinary machinery it carries. The roles are the Sector's own drone systems at
ground scale:

- An **escort** flies the loose formation round the hull, engages what comes into its
  reach, and is held by a leash.
- A **sensor** searches (`SensorSearch`). The ring round the hull is four quadrants, and
  the wing remembers when each was last in a sensor's sight, from sample cells far out
  where a drone's own sight is the only sight there is. Each sensor holds the stalest
  quadrant not already taken, leaning toward the way the hull faces, for a spell, then
  moves on when it reads fresh, so ground goes stale and is searched again. The memory is
  the wing's own, so any side's hull has it; it reads nothing from the player's fog. When
  a sensor sees a hostile its hull has no line to, an armoured one before a soft one and
  the nearest first, it **shadows** it: it holds a point between the hull and the target,
  short of it by most of its sight and within the leash, turned until it has a line, while
  the other sensor keeps searching. A target out of every sensor's sight is chased to where
  it was last seen, then let go. A sensor is a sight source on the scout's terms, so it
  looks round the corner the hull cannot see past, and it carries only a light ion sting.
- A **halo** drone orbits tight round the hull wearing a carried point-defence mount
  (`PointDefenseService`), so it takes rockets and shells out of the air near the hull.
  The mount ends when the drone falls.
- A **strike** drone waits in the bay for a hostile to threaten the hull. It then flies
  fast into it and detonates as an ordinary blast credited to itself. It is the only
  role the bay rebuilds, slowly, as a Terminator's mini-fac does.

  The strike is a heavy shell delivered by hand, and it is held to that family rather than
  to anything special. Its blast kills a rifleman in any standard pattern it reaches and
  does not kill a power suit, the same armor reach as a field mortar or a tank's twin
  cannon, and its kill radius sits in the same band. It aims at one hostile, never at a
  group's middle, and it fuses within a cell of that man. So it takes a picket standing a
  cell apart whole, and from a picket two or three cells apart it takes only the man it
  was sent at. What it buys over a shell is precision, paid for with one blast per
  rebuild. A picket wiped out by a skimmer was a picket that had closed up under fire and
  been worn down by the escorts first. `StrikeBlastTest` pins the law, and the
  `drone-strike-spacing` scene measures it in a played fight.

Any other drone lost stays lost. When the hull is wrecked or leaves, the anchor dies, and
every drone still out is destroyed where it flies and falls through the ordinary drone
crash. Bay drones carry a sturdier airframe than a hub's disposable screen, because they
are part of the hull's armament. Each role is drawn as its own ducted-fan Tri-Tachyon
drone (`DroneLook`; `art-source/drones/tt-drones/`).

The **hover scout** (`HOVER_SCOUT`, Light lift) carries a sensor mast on the spotter pickup's
terms (`HullSightings`). It also carries a light twin pulse emitter of the drone-pulse family,
so it can screen, and a sensor pair. The stripped pickup stays the fastest hull in the
bay.

The **drone skimmer** (`DRONE_SKIMMER`, Medium lift) is the scout's hull stretched over a
drone bay, with no gun. Its bay holds two escorts, two halo drones and a strike drone.

Acceptance is the `hover-line` scene:
- The scout crosses a river at which a wheeled APC is refused.
- It drives over standing marines and shoves none, against the spotter pickup's control.
- Its sensor pair searches round the hull and puts a defender the hull cannot see into the
  side's picture.
- `sensor-search`: a carrier's sensor pair, starting on the empty ground it faces, goes
  round to find riflemen behind a wall; with no kit it finds nothing; and one sensor
  shadows a tank driving a road beyond a long wall, keeping a spotter's call on it
  while the carrier fires.
- The skimmer's drones launch, stay on the leash and fight. Its strike drone detonates on
  a picket and is rebuilt. Its halo takes incoming rockets out of the air, and the flight
  falls with the hull.

**Ground dust** is simulation presentation, like a wreck's smoke (`VehicleDustEmitter`).
A wheeled or tracked hull moving over loose ground throws soft puffs off its trailing
wheels or track ends, back down their own lines, and raises nothing on pavement. Those
lines are the hull's **running gear** (`RunningGear`), measured off its sprite: the
centre line of each track or wheel row and where it touches down fore and aft. They are
not the footprint, which leaves out jutting wheels and a dozer's blade, so dust and a
lost drive's smoke come off the gear the paint shows. A hover skirt blows a low ring of haze, and
spray over water, whether or not it moves. The puffs are queued as events that every host
draws through its particle system, and they draw nothing from the simulation's random
stream.

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

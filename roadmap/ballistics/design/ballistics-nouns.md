# Ballistics nouns

Status: ACTIVE — modeled ground direct fire is shipped; one manual feel pass remains parked.

Written: 2026-08-23

Updated: 2026-10-25 — a second obscurant, haze, costs accuracy and hides nothing. Earlier: a gun on a plinth fires from its height; a committed outcome names the environment feature that stopped the round, and arrival wears it.

Ballistics makes a direct shot a committed physical event instead of an
accuracy result applied at the muzzle. It owns contact along the predicted
ground-flight path; it does not turn every weapon into a simulated per-tick
projectile.

## Vocabulary

- A **modeled round** is one direct-fire trajectory resolved at fire time
  across its complete modeled flight. It is not a per-tick collision actor.
- A **source** supplies the round's origin, faction, and optional owning body.
  It lets infantry, mounts, and ground emplacements share one contact model
  without inventing a fake shooter. Its height above the target plane is zero
  for everything on the ground except a gun on a plinth, whose rounds leave from
  the plinth's height and descend along their line to the aim; anything they
  pass over is not met, and a disciplined crew's lane check ignores a friend the
  round passes over (`ground-turret-tiers.md`). Walls stay full height.
- The **target plane** is the lateral and lightweight vertical plane through
  the intended target. It makes an accuracy result an actual wide, high, or
  low path rather than a second hidden hit roll.
- A **contact** is a candidate physical stop along that path: a wall, crossed
  cover feature, directional cover edge, or body silhouette.
- **Body penetration** is an authored count of accepted body contacts a direct
  resolved round may pass through. It never permits a round through structural
  walls, doodad blocks, or caught cover.
- A **body part** is where on a person a round lands: legs, torso, arms or
  head, stacked up the body in bands as tall as each part's share of the
  silhouette. Machines, structures and creatures have none; they are one
  whole-body contact. The part rides on the damage payload, where it scales
  the hit and may leave a limb wound (`combat-durability-nouns.md`).
- A **cover profile** is what a cover feature hides of a person, per body
  part, from a shooter at full distance. There is one per kind of cover —
  edge, a trench's occupant, and a prop crossed in the lane — and per level.
- **Obscuration** is matter on the path that degrades the sight picture
  without being a contact. There are two obscurants: smoke, and haze (a
  Bombard's spores, `bombard.md`). Each is measured as a depth in cells and
  never appears among contacts.
- A **struck feature** is the piece of the environment a round stopped in: the
  crossed prop's cell for a crossed-cover stop, the cell or shared edge that
  lent edge cover for a cover catch at the body, the blocking cell for a wall
  stop. It names a place rather than a prop; cover from the ground a man stands
  in names nothing.
- A **committed outcome** is the resolved endpoint, stop kind, victim if any,
  struck feature if any, and flight time. Later movement does not reroll it; a target already dead at
  arrival simply cannot receive the delayed payload.
- A **visual shot** mirrors that committed flight for presentation. It is not
  the authority for collision or damage.

## Aim, travel, and contact

Entity-target accuracy commits once before the contact walk. A successful aim samples inside
the intended body's horizontal and vertical silhouette; a miss samples outside
it. The intended target therefore cannot be visibly crossed and then rejected
by another invisible accuracy roll.

A **point aim** supplies a world bearing with no target identity, automatic
lead, or silhouette-conditioned hit roll. Accuracy, equipment, stance, spread,
and smoke change the size of a sampled angular error disk before contacts are
traced. Dispersion and smoke depth are calibrated along the bearing at weapon
range, so cursor distance cannot improve distant accuracy or shorten flight.
A player's own cursor on a controlled marine samples a **controlled**
dispersion: authored spread keeps its full weight, but the abstract accuracy
figure (tuned as an autonomous hit chance) widens the disk about a third as
much, and rounds cluster toward the cursor rather than fill the disk evenly.
Spread therefore carries a weapon's character under manual fire. Automated
point fire (wall breaching, vehicle turrets, mech mounts) keeps the full-weight,
evenly filled disk.
A point round remains live to the same full modeled reach as a stray round;
all body contacts are incidental and retain ordinary friendly-catch rules.
Its trigger is allowed toward an obstructed or empty lane: a wall stops the
emitted round at contact. Invalid or coincident aim consumes the request
without emitting a round. Point aim does not register a threat or reveal an
unobserved identity.

The target chooses a direct round's trajectory, not a missed round's lifetime.
Unless a physical contact stops it first, a miss remains live out to one and a
half times the firing weapon's maximum targeting range. A near target therefore
does not give its misses an arbitrary short tail; the same weapon carries a
stray round the same maximum distance regardless of which legal target supplied
the aim direction. An ordinary successful aim needs contact work only through
the intended body's predicted entry point. A definition with positive body
penetration keeps the full modeled reach so the ordered walk can continue past
that body. Walls, cover, and interposing bodies before either endpoint still
win; only an accepted body contact may consume one penetration and continue.

Weapon velocity is part of play. The aim leads a moving intended target and
body contacts are evaluated along the same flight timeline, so a slower round
has a longer chance to meet a moving interposer. The body circle is shared with
other physical gameplay; vertical contact uses the body's lightweight combat
silhouette. This is a target-plane convention, not terrain elevation, gravity,
or a general airborne collision policy.

The unit spatial index supplies every body candidate, convoy chassis included —
it indexes bodies rather than dense-roster rows, so a vehicle needs no separate
append. Each live vehicle contributes its continuous body position and velocity;
its radius and height come from the roster's per-instance dispatch, the same
call that answers for a turret's structure and a mech's variant. This keeps
vehicle contact physical without pretending that an APC is a grid infantry row.
Wrecked vehicles are no longer damageable contacts and leave the index at the
moment they wreck; their persistent obstruction belongs to navigation rather
than Ballistics.

Contacts are considered in travel order. A structural wall is a full-height
hard stop. A crossed physical cover feature, a directional cover edge at a
body, and a body contact may each stop the round only according to their own
rules. A failed probabilistic catch lets the round continue, so a miss can
strike a later physical body. Nothing may apply damage or presentation as if a
later contact won over an earlier stop.

Every accepted body contact is retained with its own position and flight time.
Once the authored body-penetration count is exhausted, the next accepted body
is the terminal stop. A contact-and-area weapon centers its one compact area
payload on that terminal body or structural stop. Every actor recorded as
physically contacted is excluded from the area payload and receives only its
contact payload; a penetrative lane that leaves the modeled segment produces no
phantom terminal explosion.

Structural tracing follows the round's true source-to-aim segment through every
grid-cell square it intersects and stops at the near wall boundary. The direct-
fire gate uses the same exact source-to-intended-target geometry. Cached
cell-to-cell visibility remains valid for perception, but it cannot authorize
a shot or a firing position when the bodies' within-cell offsets put a wall on
the physical segment.

An authored shared-edge barrier contributes its exact unit-length boundary
segment to that same trace. Its single profile decides whether the crossing is
a sight stop, a direct-projectile stop, or neither; navigation closure alone
never implies ballistic opacity. The first window profile is transparent to
sight and direct rounds while still supplying directional low cover at the
adjacent body position. That cover remains an interception chance rather than
a hard pane contact, and disappears with the feature when structural blast
damage breaks it.

A Mech direct source is the installed barrel's muzzle in its actual chassis and
torso pose, for both autonomous entity aim and manual point aim. Its structural
body-to-barrel segment is checked before launch: a muzzle reaching through a
wall cannot bypass that wall by starting the contact walk on its far side.
Ballistics, tracer origin, and muzzle presentation therefore describe the same
shot. Mech accuracy retains the shared weapon calculation and receives no
infantry training multiplier merely because its aim is a point.

An infantry shoulder pod (the SRM-POD carried special) may declare its own physical hardpoint and forward
muzzle reach. The pod's independent target supplies its firing bearing; its
weapon still owns salvo count, accuracy, flight, and payload. The same muzzle
feeds contact resolution, the traveling projectile, and the visual shot.
Before spending a salvo, the body-to-muzzle segment must be structurally clear,
so a shoulder attachment cannot launch through a wall by starting beyond it.
Carrier art and its firing pulse observe this geometry without authoring it.

A manual aim-lane preview may reuse deterministic structural geometry and the
same firing origins without resolving a modeled round. Its first obstruction
reports a wall or projectile-blocking edge, never a probabilistic cover catch
or body contact. It consumes no random samples, ammunition, or clocks and
does not promise that a mount is aligned, ready, or certain to hit.
`direct-control-nouns.md` owns that preview's visibility and selection scope.

A manually thrown **rocket** (an interceptable direct round with a warhead) is
the one point round whose aim point is a destination rather than a bearing. It
still stops at the first body, wall or doodad on its way, but a rocket that meets
nothing **ground-bursts** at the aim point plus its scatter instead of
overshooting. The aim point may lie past nominal range, out to twice it (further
points burst at that ceiling along the bearing): the player is never refused
the shot, and pays for reach in scatter. The scatter disk has the point-aim
radius at weapon range, scales with distance inside it, and grows faster than
distance beyond it. Gun rounds, including those with an area payload, keep the
bearing-only rule: an empty cursor point is no detonation request, and a round
leaving modeled reach has no terminal blast. Autonomous fire and a company's
wall-breach shots are not thrown and keep the terminal-contact rule. Direct SRMs
remain interceptable in flight, and a surviving payload arrives on the ordinary
delay.
A pilot's arcing LRM rack lands on the thrown point plus the same scatter disk,
with no target-relative hit roll; autonomous and breach indirect fire keep their
own targeting contract.

## Obscuration

Obscuration is the exact inverse of cover: cover intercepts a round without
touching accuracy, and obscuration costs accuracy without ever touching a
round. Smoke therefore stops nothing and forbids nothing. It is priced as a
depth — the smoke-filled cells on the shooter-to-target segment, counting both
endpoint cells, so standing inside a cloud obscures as surely as shooting
through one — and each such cell compounds a multiplier onto the accuracy the
target plane commits. A floor keeps even a deep lane above zero.

The direct-fire gate is structural only. A screened lane is a bad shot rather
than an impossible one, which is what makes suppressing a screened position a
decision instead of a refusal; the same rule holds for air pairs, whose
close-wall exemption follows sight, not fire. This is deliberately not
symmetric with perception: sight, fog, and target *acquisition* still stop at
a cloud, so smoke breaks a squad's ability to find new targets while leaving
it able to keep working one it already holds. A screen buys concealment and
degraded incoming fire, never immunity.

**Haze** is the see-through obscurant. It is priced the same way, as its own
depth on the segment compounding its own milder multiplier under the same
floor, but it touches no perception at all: sight, fog and acquisition pass
through it. A squad in haze sees everything and shoots worse; it conceals
nobody, so leaving it, not hiding in it, is the answer.

## Cover and safety

Cover is physical interception, not an extra accuracy penalty. Walkable props
and walkable nature features may catch a ray that crosses their authored
footprint. Non-walkable props and walls instead provide directional edge cover
to an adjacent standable position. Those shapes must not overlap for the same
feature and round. Both probabilistic cover forms respect their authored
catch-height; structural walls remain full-height regardless of a round's
visible elevation.

**Cover hides parts of a person.** A round that reaches a person lands on the
body part at the height it meets him: for the intended target, where the
committed aim fell on his silhouette, which lands on each part in proportion
to its share; for anyone else, wherever the round is crossing. Cover at that
body then has one chance to stop it, equal to the profile's coverage of that
part. A man in a trench at range is therefore hard to kill because only his
head and shoulders show, and a low wall stops leg shots while leaving the head
as exposed as it was. A round that reaches an exposed part has beaten the
cover and lands with no further damage cut for it: cover is counted once.
Profiles are set so each level stops about what the older flat model's block
chance and damage cut took off together; the difference is which parts are
left to hit, and that difference matters: a head hit is worth more than a limb
hit, so a man whose legs and torso are hidden takes fewer hits but harder
ones. In the open the part multipliers average out to the authored damage. Bodies without parts keep that flat model — a 15%, 30% or 45%
catch by level, then the same cut on what lands — and a payload with no part,
such as an area burst, a strafe or unattributed damage, still has its damage
cut by the cover where it lands.

Edge cover catches, crossed-prop catches and friendly incidental contacts are
zero within two cells of the muzzle, then use a smoothstep ramp to full
strength at eight cells. This does not weaken walls, hostile incidental
contacts, or the intended target.

**A prop crossed in the lane catches by the part the round is headed for.** A
walkable feature the round passes over short of an intended person meets it
with the crossed profile's coverage of the part the committed aim landed on,
so a low crate catches leg and torso shots and lets head shots pass. The
crossed profile is low and is set so each level stops as many rounds as the
flat 15%, 30% or 45% it replaced; only which rounds it stops changed. A round
headed for no part — an authored miss, point aim, a target without parts, or a
penetrating round already through its man — keeps the flat catch by level. The
part is read from where the aim lands on the man, not from the round's drawn
height at the prop, whose authored catch-height still decides which rounds the
prop can meet at all; deciding the part spends no roll.

A trench is a hole, not an obstacle. It never catches a round crossing it and
lends nothing to the cells beside it; it covers only the man standing in it,
and from every bearing alike, since a dug channel has no front. Its profile
hides the legs and torso and little of the head, scaled by the shooter's
distance from him: none at three cells or closer, where a shooter fires down
into it, rising by smoothstep to the full profile at fifteen. Where a trench
and edge cover both reach a part, the better of the two applies. A partless
payload with no attacker, such as a detonation, meets the trench's full cut.

A physically crossed hostile can receive a missed round. Friendly incidental
contacts use a 35% full-distance catch chance and apply half direct damage.
Marines do not include civilians in direct-fire contact candidates; defender
fire retains ordinary civilian contact behavior. Infantry training may
withhold only a primary round already committed to a friendly victim: Green
20%, Regular 50%, Veteran 75%, and Elite 90%. That hold consumes the firing
opportunity and emits no round, preserving hostile damage opportunity rather
than solving friendly fire by making experienced soldiers fire less.

A map emplacement's or a ground vehicle's gun crew works to the same rule at a fixed Regular-like
discipline (50%): a modeled ground round from a turret whose resolution would
hit a friend -- a friendly contact, or a burst landing with a friendly body
inside its reach (the radius plus the body's footprint, the detonation's own
test), as a heavy MG's small-blast rounds do in a scrum -- is held
on that roll, and the hold also breaks off the rest of the
burst, as a crew that sees their own in the line lets go of the trigger. The
roll is drawn only when a resolved round would hit a friend, so a gun with a
clear line plays exactly as before. Crew-served tubes keep their own stricter
refusal (no round onto ground a friend stands on); aircraft guns do not hold
yet, and a player's point-aimed round is never held. `-Dbattle.turret.crewFriendlyHold=false` is the control.

**Check the lane before the trigger.** The rolls above act on a round already
committed; they are what is left for a friend who steps in mid-flight or a
round that strays. Before that, every disciplined shooter looks: a trained
rifleman (marine or militia, on any side) firing on a target, a ground gun crew
that holds for friends, and a mech's own fire control each refuse to fire while
a friendly body stands in the lane (`BallisticResolver.friendInLane`): from where
the resolver first lets a round strike a friend (two cells out) to a cell and a
half past the target, within the friend's radius plus a quarter cell of the
line. The rifleman's refusal spends no cooldown, so the round goes the tick the
lane clears, and each round of a burst checks again. A crew or a chassis also
never puts a burst over its own hull, and a chassis never bursts a round where a
friend's footprint reaches (`BallisticResolver.friendInBlast`). A suit's missile
pod keeps both: it holds a salvo with a friend in its lane, resolves every
missile before committing any, and keeps the whole salvo on the rail, spending
nothing, if one would burst where a friend or the wearer stands, which is how a
round clipping cover beside the squad is caught. The checks themselves read
only and draw nothing from the battle's random stream. Point fire a player aims
is never held. Before it, an allied front of militia waves, technicals and
Hounds spent much of its fire on itself: on the Broad Front at seed 404, 584
allied hits on allies and 74 kills by tick 9,500, plus 133 hits by vehicles and
mechs on their own hulls. `-Dbattle.fire.laneDiscipline=false` is the control.

## Resolution, payload, and presentation

`BallisticResolver` owns pure fire-time trajectory and contact resolution.
`ShotService` owns transient in-flight shot records and the flight-clock handoff
for delayed impacts. A direct damage payload or contact-fused detonation
arrives at the committed flight time; an overshoot leaves the modeled segment
without a phantom impact.

During parallel member updates, the active projectile population is host-owned
and stable. Members publish launch intents; only a successful join authorizes
the host to collect them into the active population. Collection precedes
point defence and projectile aging, preserving launch-tick interception and
flight timing. Hazard decisions use the update-start population, while damage
and throw reservations may also inspect fresh launch commitments. This is a
decision-observation delay, not an extra tick of physical flight. An unfinished
or failed update phase cannot publish partial launches or advance their clocks.

The resolver names the struck feature and never writes the world. A direct
payload that stopped in one is queued against it beside the impact clock and,
on arrival, spent on whatever stands there through the map-structure channel
(`combat-durability-nouns.md`, material rating). Attributing a cover catch to
edge cover or to a trench reuses the catch's own roll, so naming the feature
costs no random draw and a fight in the open replays unchanged.

`ShotEvent` and the render path observe the same flight. Traveling bolts and
sprites advance to arrival, while a non-traveling tracer remains a presentation
form. Presentation may choose silhouette, color, sound, and impact treatment,
but it cannot change the resolved path or create damage.

## Scope and boundaries

Ground direct-fire adopters share this model: infantry primaries and direct
special weapons, ground mech direct weapons, and ground direct turret bursts.
Their intended targets may be either ordinary combatants or targetable convoy
vehicles; the committed payload then enters the shared durability authority.
Arc, indirect, and other projectile procedures retain their own trajectory and
arrival contracts, but they lead the same way: a round that flies to a point
(a mortar bomb, an artillery shell, a turret rocket) is laid where its target
will be after the flight, extrapolating the same body velocity the direct
resolver reads (`TargetMotion`), and its scatter is drawn around that led
point. Leading moves where the pattern is centred, never how wide it is; a
target that turns or stops during the flight is the error the lead cannot see.
A crew's check-fire keeps the led point, not the target's standing ground,
clear of its own side. Aerial fire is not implicitly ground direct fire; modeled
fighter shots require an Air-owned airborne source, target, roof, and wall
policy before adoption. Dedicated ground AA guidance uses the separate
Air-owned live-target flight and contact contract; it does not silently adopt
ground direct-fire targeting or point-defense eligibility.

`moddable-weapons-nouns.md` owns authored weapon identity, direct-fire speed,
spread, payload, and effect declarations. Ballistics decides where such a
direct round stops. `combat-durability-nouns.md` owns armor and structure
resolution after a contact payload survives interception. `battle-render-nouns.md`
owns frame collection and paint order; it presents committed shots without
becoming their simulation authority. `air-nouns.md` owns airborne behavior and
the decision to extend this ground model into the air domain.

Area exposure and per-part armour are the open half of `body-part-cover.md`;
part consequences — damage by part and limb wounds — are shipped, and so is
small-arms wear of the cover a round stops in. Lead error, point-defense generalization, and modeled aerial
fire are extension paths, not contracted Ballistics work. Current acceptance
is indexed in `stories.md`; completed historical slices are in `shipped.md`.

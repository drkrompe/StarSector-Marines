# Ballistics nouns

Status: ACTIVE — modeled ground direct fire is shipped; one manual feel pass remains parked.

Written: 2026-08-23

Updated: 2026-09-26 — launch intents publish after member join without delaying physical flight.

Ballistics makes a direct shot a committed physical event instead of an
accuracy result applied at the muzzle. It owns contact along the predicted
ground-flight path; it does not turn every weapon into a simulated per-tick
projectile.

## Vocabulary

- A **modeled round** is one direct-fire trajectory resolved at fire time
  across its complete modeled flight. It is not a per-tick collision actor.
- A **source** supplies the round's origin, faction, and optional owning body.
  It lets infantry, mounts, and ground emplacements share one contact model
  without inventing a fake shooter.
- The **target plane** is the lateral and lightweight vertical plane through
  the intended target. It makes an accuracy result an actual wide, high, or
  low path rather than a second hidden hit roll.
- A **contact** is a candidate physical stop along that path: a wall, crossed
  cover feature, directional cover edge, or body silhouette.
- **Body penetration** is an authored count of accepted body contacts a direct
  resolved round may pass through. It never permits a round through structural
  walls, doodad blocks, or caught cover.
- **Obscuration** is matter on the path that degrades the sight picture
  without being a contact. Smoke is the only obscurant today. It is measured
  as a depth in cells and never appears among contacts.
- A **committed outcome** is the resolved endpoint, stop kind, victim if any,
  and flight time. Later movement does not reroll it; a target already dead at
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

Manual ground-direct area weapons keep the same terminal-contact rule as other
modeled direct fire. An empty cursor point is a bearing, never a detonation
request; a round leaving modeled reach has no terminal blast. Direct SRMs remain
interceptable in flight, and a surviving payload arrives on the ordinary delay.
Indirect missiles retain their separate targeting contract and cannot be
triggered through this point-fire adapter.

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

## Cover and safety

Cover is physical interception, not an extra accuracy penalty. Walkable props
and walkable nature features may catch a ray that crosses their authored
footprint. Non-walkable props and walls instead provide directional edge cover
to an adjacent standable position. Those shapes must not overlap for the same
feature and round. Both probabilistic cover forms respect their authored
catch-height; structural walls remain full-height regardless of a round's
visible elevation.

Probabilistic cover catches and friendly incidental contacts are zero within
two cells of the muzzle, then use a smoothstep ramp to full strength at eight
cells. Authored cover levels catch at 15%, 30%, or 45% once fully downrange.
This does not weaken walls, hostile incidental contacts, or the intended
target.

A physically crossed hostile can receive a missed round. Friendly incidental
contacts use a 35% full-distance catch chance and apply half direct damage.
Marines do not include civilians in direct-fire contact candidates; defender
fire retains ordinary civilian contact behavior. Infantry training may
withhold only a primary round already committed to a friendly victim: Green
20%, Regular 50%, Veteran 75%, and Elite 90%. That hold consumes the firing
opportunity and emits no round, preserving hostile damage opportunity rather
than solving friendly fire by making experienced soldiers fire less.

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
arrival contracts. Aerial fire is not implicitly ground direct fire; modeled
fighter shots require an Air-owned airborne source, target, roof, and wall
policy before adoption.

`moddable-weapons-nouns.md` owns authored weapon identity, direct-fire speed,
spread, payload, and effect declarations. Ballistics decides where such a
direct round stops. `combat-durability-nouns.md` owns armor and structure
resolution after a contact payload survives interception. `battle-render-nouns.md`
owns frame collection and paint order; it presents committed shots without
becoming their simulation authority. `air-nouns.md` owns airborne behavior and
the decision to extend this ground model into the air domain.

Cover erosion, lead error, point-defense generalization, and modeled aerial
fire are extension paths, not contracted Ballistics work. Current acceptance
is indexed in `stories.md`; completed historical slices are in `shipped.md`.

# Ballistics nouns

Status: ACTIVE — modeled ground direct fire is shipped; one manual feel pass remains parked.

Written: 2026-08-23

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
- A **committed outcome** is the resolved endpoint, stop kind, victim if any,
  and flight time. Later movement does not reroll it; a target already dead at
  arrival simply cannot receive the delayed payload.
- A **visual shot** mirrors that committed flight for presentation. It is not
  the authority for collision or damage.

## Aim, travel, and contact

Accuracy commits once before the contact walk. A successful aim samples inside
the intended body's horizontal and vertical silhouette; a miss samples outside
it. The intended target therefore cannot be visibly crossed and then rejected
by another invisible accuracy roll.

Weapon velocity is part of play. The aim leads a moving intended target and
body contacts are evaluated along the same flight timeline, so a slower round
has a longer chance to meet a moving interposer. The body circle is shared with
other physical gameplay; vertical contact uses the body's lightweight combat
silhouette. This is a target-plane convention, not terrain elevation, gravity,
or a general airborne collision policy.

Contacts are considered in travel order. A structural wall is a full-height
hard stop. A crossed physical cover feature, a directional cover edge at a
body, and a body contact may each stop the round only according to their own
rules. A failed probabilistic catch lets the round continue, so a miss can
strike a later physical body. Nothing may apply damage or presentation as if a
later contact won over an earlier stop.

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

`ShotEvent` and the render path observe the same flight. Traveling bolts and
sprites advance to arrival, while a non-traveling tracer remains a presentation
form. Presentation may choose silhouette, color, sound, and impact treatment,
but it cannot change the resolved path or create damage.

## Scope and boundaries

Ground direct-fire adopters share this model: infantry primaries and direct
special weapons, ground mech direct weapons, and ground direct turret bursts.
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

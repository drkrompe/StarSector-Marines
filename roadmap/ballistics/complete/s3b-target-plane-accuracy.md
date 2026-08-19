# S3b — target-plane accuracy

> **Shipped 2026-08-19** on `session/ballistics-target-plane-accuracy`,
> implementation commit `cd6094fd`. Intended accuracy is now committed once
> into lateral/elevation aim, per-type vertical silhouettes participate in
> contacts, and every visible shot carrier projects the resulting Z path.
> Free-flight overshoots disappear without a false impact. Full suite: 1529
> tests (1528 root + 1 asset-pipeline).
>
> **Landed vs. planned deviations:** none. The same vertical path is also
> threaded through contrail samples so future target-plane weapons can add a
> trail without reopening projection semantics.

Original contract below, kept for the record.

---

> Make accuracy visible and physical. A shot's one authored accuracy roll
> chooses whether its aim lies inside the intended target silhouette; misses
> scatter in the plane perpendicular to fire, producing real lateral trajectories
> and real high/low trajectories rather than a bolt crossing a body and failing
> an invisible contact roll.

Parent design: [`../overview.md`](../overview.md) §5–7.
Depends on shipped S3/S3a visible-round presentation.

## Problem

The S1 resolver currently combines two independent mechanisms:

- distance-scaled `effectiveSpread` jitters the led aim point in map XY;
- when that ground ray contacts a unit circle, accuracy rolls invisibly.

A visible bolt can therefore pass through the locked target's center, fail the
contact roll, and continue. XY disk jitter also wastes part of its magnitude
along the firing direction instead of expressing all error across the target's
silhouette.

## Target-plane aim

Resolve the intended-target accuracy once, before the contact walk:

`onTargetChance = finalAccuracy × target.incomingAccuracyMult`

The target plane has two axes:

- **lateral** — perpendicular to the ground-plane firing direction; offsets
  the physical XY ray left/right;
- **elevation** — an abstract Z axis projected upward/downward on screen;
  offsets the round above/below the shared firing plane.

An on-target roll samples inside the target's horizontal-radius × vertical-
half-height silhouette. A miss samples beyond that rectangle's boundary in a
random target-plane direction, plus a visible clearance margin widened by the
existing `effectiveSpread`. This preserves the authored hit probability while
making the reason for a miss legible.

Earlier incidental contacts keep the existing flat graze roll. The intended
target does not roll accuracy again: if the authored on-target ray reaches its
silhouette after cover/interveners, it hits.

## Lightweight vertical collision

`UnitType` gains `hitHalfHeight`, centered on Z=0. This is a combat silhouette,
not absolute world altitude and not `renderScale`:

| Family | half-height |
|---|---:|
| infantry / civilians / aliens | 0.45 cells |
| swarm runner | 0.30 |
| heavy mech | 0.80 |
| turret | 0.60 |
| drone hub | 0.70 |
| drone | 0.35 |

The resolver still computes XY circle-entry time exactly as today, then admits
the unit contact only when `abs(roundZAtContact) <= hitHalfHeight`. Z changes
linearly with distance along the ray. A high/low miss therefore keeps diverging
and cannot strike a same-height unit behind the target, while a taller body can
still catch it.

Walls remain full-height hard stops. Doodad crossings and directional wall
cover retain their existing block rolls; authored object heights and airborne
unit altitude are future extensions, not prerequisites for honest high/low
small-arms fire.

## Visual carrier

`BallisticResolver.Resolution` returns endpoint Z. Primary `ShotEvent`s carry
`fromZ=0`, `toZ`, and the resolver stop kind; legacy constructors default both
Z values to zero and no stop kind.

`ShotRenderService` projects elevation as `screenY = mapY + Z` for tracers,
bolts, and traveling sprites. Bolt head/tail bearing and length use their
projected endpoints so an ascending or descending round visibly tilts rather
than merely translating.

Impact particles project to endpoint Z for physical stops. `OVERSHOOT` rounds
expire without an impact particle/decal: the round left the modeled flight
window instead of striking invisible ground.

## Tuning constants

- Preserve all existing weapon accuracy, range-falloff, stance, equipment, and
  aptitude values.
- Preserve `INCIDENTAL_HIT_CHANCE`, physical cover, friendly-fire damage, and
  muzzle clearance.
- Introduce a modest target-plane miss-clearance range; widen it additively by
  `effectiveSpread` so inaccurate burst weapons visibly spray farther.

## Tests

- Pure target-plane sampling: on-target bounds, misses outside the silhouette,
  deterministic lateral/high/low cases.
- Resolver: lateral miss goes wide; high/low miss crosses XY but fails vertical
  contact; a taller body can catch a trajectory an infantry body clears;
  intended accuracy is rolled once; incidental graze behavior survives.
- Rendering: bolt head/tail Z projection and projected bearing/length; flat-shot
  regression for legacy constructors; all three bolt families still collect.
- Presentation: overshoots do not emit false impact FX; physical stops do.

## Non-goals

- Gravity, drag, ricochet, penetration, or per-tick projectile physics.
- Absolute terrain/building/object height.
- Rebalancing weapon stats.
- Applying the resolver to mech/turret direct fire (still S4).

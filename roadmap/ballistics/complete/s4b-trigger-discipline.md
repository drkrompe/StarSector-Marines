# S4b — trigger discipline

> **Shipped 2026-08-22** on `session/friendly-fire-discipline`, implementation
> commit `8881ff41`. Experience-scaled trigger discipline now suppresses only
> infantry primary rounds already committed to a friendly victim, preserving
> hostile DPS and normal firing cadence.
>
> **Landed vs. planned deviations:** none. Post-implementation verification:
> all 2,093 repository tests green (2,092 root + 1 asset-pipeline).

Original contract below, kept for the record.

---

> Reduce post-lethality-pass friendly fire without making inexperienced
> soldiers offensively stronger than veterans.

Parent design: `overview.md`.

## Why

The progression S1 lethality pass reduced baseline marine TTK from roughly 30
seconds to 3.4 seconds. Ballistics still gives a distant friendly intersecting
a round's physical path a 35% catch chance and applies 0.5× damage when caught.
Those values were deliberately annoying at the old damage scale; they are much
more punitive now.

A coarse pre-fire rule such as “hold whenever an ally intersects the firing
lane” creates the wrong experience progression. A green soldier sometimes
fires through that lane and reaches the enemy, while a veteran declines the
same potentially productive shot. Better training would then trade hostile DPS
for safety.

## Decision

Resolve the primary round completely before producing any side effects. Only
when `BallisticResolver.Resolution.friendlyHit` says that exact counterfactual
round will stop in a friendly does infantry roll trigger discipline:

| Experience | Hold chance |
| --- | ---: |
| Green | 20% |
| Regular | 50% |
| Veteran | 75% |
| Elite | 90% |

On a successful hold:

- do not queue a pending impact;
- do not post a `ShotEvent` (therefore no tracer, muzzle report, noise, or
  presentation event);
- do not record a telemetry round fired;
- still consume the caller-owned trigger cooldown and the current burst slot.

The last point, together with gating only `friendlyHit`, is the DPS invariant.
The counterfactual round had already stopped in the friendly and contributed
zero enemy damage; withholding it cannot reduce enemy damage. Each burst round
resolves independently, so a held unsafe round does not erase later safe rounds
from the same trigger pattern.

Safe outcomes do not roll discipline. Failed discipline preserves the existing
35% incidental contact, proximity ramp, 0.5× damage, hit response, telemetry,
and friendly-fire radio path.

## Scope

Trigger discipline applies to the soldier archetypes that use infantry primary
fire: Marine, aligned marine, opposed marine, and militia. Their existing
`SoldierProfile.experienceTier` is the training source, including militia with
baked weapon stats.

Deliberately excluded:

- rockets and other AoE weapons, which need a blast-radius safety decision
  rather than a lane-only rule;
- autonomous drones, static turrets, aliens, and mechs;
- repositioning after repeated holds;
- a persisted “rounds withheld” telemetry column.

## Acceptance

- Exact hold chances are pinned for all four experience tiers.
- A committed friendly hit held by discipline produces no shot, impact, or
  fired-round telemetry.
- Failed discipline emits the original friendly-bound round, whose victim is
  still the ally rather than the enemy behind them.
- A safe shot never consumes a discipline roll and reaches the enemy normally.
- Existing resolver and firing-system coverage stays green.
- Full repository suite passes.

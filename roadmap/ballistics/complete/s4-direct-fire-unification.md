# S4 — direct-fire unification

> **Shipped 2026-08-19** on `session/ballistics-direct-fire-unification`,
> implementation commit `b8c7e3e6`. Mech chaingun/SRM, handheld rockets,
> and ground Vulcan/Heavy-MG bursts now author their high/low/wide path through
> `BallisticResolver`; contact payloads arrive on real distance/velocity clocks,
> while free-flight explosive overshoots carry no phantom detonation or impact
> FX. Pre-integration full suite: 1595 tests green.
>
> **Landed vs. planned deviations:** none. The shared ground-turret sink also
> carries vehicle entity ids, so mounted ground bursts gain shooter exclusion
> and delayed hit-response identity with static emplacements. Aerial mounts and
> all named indirect paths remain deliberately unchanged.

Original contract below, kept for the record.

---

> Put every remaining ground-level direct-fire weapon on the modeled-round
> pipeline. Kinetic spray and contact-fused rockets author one visible
> high/low/wide trajectory, interact with units and obstacle silhouettes in
> flight order, and apply their existing damage payload at the physical stop.

Parent design: [`../overview.md`](../overview.md) §4–7.
Depends on shipped S1–S3c.

## Why this slice exists

Infantry primaries and single-shot static turrets already use
`BallisticResolver`, but three legacy direct-fire procedures still roll abstract
accuracy, scatter an endpoint, then optionally snap that endpoint to a wall:

- `HeavyWeapons` for the mech chaingun and level-flight SRM pod;
- `InfantryWeapons.fireSecondary` for the handheld rocket launcher;
- `TurretFireSystem` for ground-deployed Vulcan and Heavy-MG bursts.

Those shots cannot currently go visibly high/low, strike an intervener, clear a
short prop, or have cover stop the actual round. They also keep
`CoverAccuracyResolver`, `ShotRaycast`, and the old miss-ring endpoint model
alive beside the modeled pipeline.

## Weapon matrix

| Path | S4 policy | Reason |
|---|---|---|
| Infantry primaries | already modeled; regression only | S1–S3c reference path |
| Static single-shot turrets | already modeled through `sim.fireShot` | entity-to-entity primary fallback |
| Mech chaingun | migrate | ground-level direct kinetic saturation |
| Mech SRM pod | migrate | ground-level contact-fused direct rocket |
| Handheld rocket launcher | migrate | ground-level contact-fused direct rocket |
| Ground Vulcan / Heavy MG burst | migrate | ground-level direct kinetic saturation |
| Mech LRM artillery | keep projectile/scatter path | indirect arc, including no-LoS fire |
| Turret grenade launcher / LOCUST | keep projectile/scatter path | indirect/arc AoE |
| Aerial shuttle turrets | keep current path | airborne wall/height policy is not yet defined |
| Flyby fighters | named follow-up | migrate with fighter air entities onto high/low/wide FX |

`HEAVY_MORTAR` is a historical display name, not an arc declaration: the
single-shot static turret already reaches the resolver through `sim.fireShot`.
The matrix keys on firing procedure and `arcHeight`, never the weapon name.

## General shot source

`BallisticResolver` gains a source value carrying:

- optional shooter entity id (`0` for an external/non-roster mount);
- float origin X/Y and lightweight origin Z;
- shooter faction.

The existing entity-to-entity overload builds that source from the roster, so
infantry behavior remains unchanged. Static/burst procedures can use the same
solver without inventing a fake unit. The source id excludes the shooter's own
body and is threaded into delayed hit response when available.

S4 ground sources use `originZ = 0`. The field is deliberately present for the
fighter follow-up, but this story does not decide airborne wall clearance,
aircraft target altitude, or roof interception.

## One direct-round procedure

Each migrated path:

1. Computes its existing non-cover accuracy stack and miss-spread tuning.
2. Resolves one round through `BallisticResolver` with a real cells/sec speed.
3. Posts `ShotEvent` with the resolved XY/Z endpoints, flight time, stop kind,
   and the existing weapon tag so its sprite/audio/impact family is unchanged.
4. Applies the weapon payload at the resolved stop:
   - direct kinetic damage uses `PendingImpact` (existing primary behavior);
   - AoE/contact-fused weapons place their existing `PendingDetonation` at the
     stop and preserve the projectile entity for rocket coordination/point
     defense;
   - a free-flight `OVERSHOOT` has no impact payload or impact FX. The round
     visibly flies out; it does not produce a phantom ground explosion.

Contact-fused explosives detonate on `UNIT_HIT`, `COVER_CLIP`, `WALL`, or
`DOODAD_BLOCK`. A failed doodad/incidental roll continues in the existing
event order. Explosion AoE remains the authority on who is damaged at arrival;
the resolver's victim is used only to establish the physical stop and suppress
near-miss double counting.

## Velocity

Direct weapons expose cells/sec derived at the migration boundary from their
existing maximum-range visual timing (`range / flightSec`). This preserves the
old long-range presentation while making close shots arrive sooner:

- mech chaingun: `30 / 0.10 = 300` cells/sec;
- mech SRM: `18 / 0.55 ≈ 32.7` cells/sec;
- handheld rocket: `32 / 0.70 ≈ 45.7` cells/sec;
- Vulcan: `22 / 0.14 ≈ 157.1` cells/sec;
- Heavy MG: `24 / 0.18 ≈ 133.3` cells/sec.

Indirect weapons retain their existing projectile velocity/scatter rules.

## Legacy retirement

- Delete `CoverAccuracyResolver` and its tests once its three direct callers
  move. Cover probability now lives only in physical resolver interception.
- Delete `ShotRaycast` and the obsolete `raycastShots` flags. Structural wall
  stops now come from `BallisticResolver`; remaining aerial/indirect paths do
  not ground-snap.
- Keep `ShotEndpoint` only for the indirect LRM scatter procedure. Direct fire
  must have no call sites.

## Fighter follow-up

When flyby fighters finish composing the real air-entity/fire pipeline, migrate
their bursts and projectile attacks onto the same target-plane aim semantics:

- lateral error produces a genuinely wide strafing line;
- elevation error produces visibly high/low fire rather than a cosmetic tracer;
- weapon FX remain selected from `FighterProfile`/`WeaponClass`;
- source Z, roofs, structural walls, and air-to-air targets get an explicit
  airborne collision policy before enabling resolver damage.

This is intentionally coordinated with
[`../../air/stories/fighter-air-entities.md`](../../air/stories/fighter-air-entities.md),
not smuggled into the ground-source migration.

## Tests

- Arbitrary resolver sources match entity sources and exclude their shooter id.
- Ground direct explosives detonate at physical wall/doodad/unit stops and do
  not detonate on free-flight overshoot.
- Mech, marine-secondary, and ground burst-turret events carry resolved Z and
  stop kind while preserving weapon tags and flight-time pairing.
- Indirect LRM/grenade/LOCUST and aerial turret behavior remain on their
  existing projectile/scatter paths.
- No direct-fire caller references `CoverAccuracyResolver`, `ShotRaycast`, or
  `ShotEndpoint`; the first two are deleted.
- Full suite stays green.

## Non-goals

- Rebalancing accuracy, conditional obstacle block chances, damage, AoE, or
  friendly-fire multipliers.
- Gravity/ground-plane collision for low misses.
- Airborne source/target collision policy or fighter damage migration.
- Changing projectile sprites, sounds, contrails, impact profiles, or burst
  cadence.
- Migrating indirect artillery.

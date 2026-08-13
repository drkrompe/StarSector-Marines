# S1 — resolver core (static world) — SHIPPED

> **Shipped 2026-08-13** on `worktree-ballistics-s1`: core in `85ec50e6`,
> radius reconciliation in `382b0ee6` (post-merge with main's separation
> steering). Implemented via orchestrated workflow (3 contract-coders →
> integrator → 3 adversarial audits) + one fix agent for the 5 audit
> findings; main-thread review after.
>
> **Landed vs. planned deviations:**
> - `UnitType.collisionRadius()` was built as specced, then **deleted** in
>   `382b0ee6`: main's separation steering established the pre-existing
>   `UnitType.radius` (infantry 0.3, drone 0.35, turret 0.45, hub 0.5,
>   mech 0.6) as THE physical body circle (soft-collision, mass = radius²),
>   and ballistics now tests that same circle. One radius concept per body.
> - Audit fixes beyond the spec: race-free `gatherAlongSegment` (call-local
>   monotone-rectangle dedupe instead of shared visit-stamps), ray-circle
>   exit-root skip (no false t=0 contact on units behind the muzzle),
>   `DoodadService.getDoodadLevelOnCell` (own-cell only — the bled facing
>   cover double-rolled crossings and granted cover from behind), and
>   `ShotEvent.struckUnit` so incidental hits don't arm the near-miss
>   morale cooldown and discount their own landed-hit drain.

Original contract below, kept verbatim for the record.

---

> Infantry primaries swap from hitscan accuracy rolls to fire-time-resolved
> ballistic rays. Static world only: contacts test units at their
> fire-tick positions (velocity extrapolation + shooter lead are S2).
> Scope is **`InfantryWeapons.fireShot` only** — `fireSecondary` (AoE),
> mech weapons, and turret spray kinds are untouched (S4 unifies them).

Read [`../overview.md`](../overview.md) first — all design decisions and
the resolved owner questions live there. This doc is the implementation
contract.

## New pieces

### `UnitType` collision radius

Per-type collision radius in cells. Infantry-class ~`0.35f`, mechs/large
`0.6f`, default `0.35f`. Plumb wherever `UnitType` stats are declared;
no per-instance override needed in S1.

### `UnitSpatialIndex.gatherAlongSegment`

```java
/** Gathers ids of alive units whose snapshot position lies within
 *  {@code margin} cells of segment (x0,y0)-(x1,y1). Same output-buffer
 *  contract as gather(). */
public void gatherAlongSegment(float x0, float y0, float x1, float y1,
                               float margin, LongBucket out)
```

Bucket walk along the ray (step bucket-sized increments, dedupe visited
buckets), point-to-segment distance filter against the snapshot
`posX`/`posY`. Margin passed by the resolver = max collision radius + a
small slack (1.0f total is fine for S1).

### `BallisticResolver` (new, `battle/combat`)

Pure, stateless resolution of one round's full flight at fire time.
Constructor-injected: `NavigationGrid`, `DoodadService`,
`UnitSpatialIndex`, `UnitRosterService`. All randomness through a passed
`Random` (testability — mirror `ShotEndpoint`).

```java
public enum StopKind { UNIT_HIT, COVER_CLIP, WALL, DOODAD_BLOCK, OVERSHOOT }

/** endX/endY = where the round physically stopped; flightTime =
 *  stopDist / roundVelocity; victimId 0 when no unit was damaged;
 *  hitIntended = victim is the locked target (drives ShotEvent.hit). */
public record Resolution(float endX, float endY, float flightTime,
                         long victimId, boolean hitIntended,
                         boolean friendlyHit, StopKind kind) {}

public Resolution resolve(long shooter, long target,
                          float finalAccuracy,   // full stack, cover REMOVED
                          float effectiveSpread, // distance-scaled, may be 0
                          float roundVelocity,
                          Random rng)
```

Constants (on the resolver, tunable):

```java
OVERSHOOT_CELLS            = 3f     // missed rounds fly this far past aim
INCIDENTAL_HIT_CHANCE      = 0.35f  // non-target contact graze roll
FRIENDLY_FIRE_DAMAGE_MULT  = 0.5f   // owner decision: partial damage
FRIENDLY_MUZZLE_CLEARANCE  = 2.0f   // skip friendly contacts this close
DEFAULT_ROUND_VELOCITY     = 60f    // cells/sec when weapon has no value
BLOCK_CHANCE_BY_LEVEL      = {0f, 0.15f, 0.30f, 0.45f} // capped at 3
```

## Resolution algorithm (walk contacts in time order)

1. **Ray.** `from` = shooter render pos. Aim = target render pos plus a
   lateral spread offset sampled from `effectiveSpread` (radius-uniform,
   like `ShotEndpoint`'s hit jitter — this replaces the miss ring
   entirely). Ray length = `dist(from, aim) + OVERSHOOT_CELLS`, capped at
   the first wall crossing (`NavigationGrid.firstWallOnLine` from shooter
   cell to the ray-end cell; wall stop point = that cell's center).
2. **Doodad crossings.** Bresenham the ray's cells (skip the shooter's own
   cell). Each cell with direction-agnostic doodad cover > 0 is a crossing
   event at `t = distToCellCenter / roundVelocity`, block chance
   `BLOCK_CHANCE_BY_LEVEL[min(level, 3)]`.
3. **Unit contacts.** `gatherAlongSegment` over the (wall-capped) ray.
   For each alive id ≠ shooter: point-to-segment distance vs. that type's
   collision radius → contact at the first radius crossing,
   `t = alongRayDist / roundVelocity`. Skip friendly contacts with
   `alongRayDist < FRIENDLY_MUZZLE_CLEARANCE`.
4. **Walk events sorted by t.** First stop wins:
   - *Wall*: hard stop (`WALL`).
   - *Doodad crossing*: roll block → stop (`DOODAD_BLOCK`) or fly on.
   - *Unit contact*: first roll **wall-cover edge-clip** — combined wall
     cover `NavigationGrid.getCoverAt(victimCell, facing toward shooter)`
     → `BLOCK_CHANCE_BY_LEVEL` roll; blocked = stop (`COVER_CLIP`), no
     damage, endpoint at contact point. **Doodad cover is NOT consulted
     here** (handled by crossings — see overview §4, no double count).
     Then roll hit: `finalAccuracy` if this is the locked target,
     `INCIDENTAL_HIT_CHANCE` otherwise → hit = stop (`UNIT_HIT`, victim
     recorded); miss = fly on.
   - Nothing stops it → `OVERSHOOT` at ray end.

## Delayed application: `ShotService` pending-impact clock

Damage no longer applies inline at fire time. New in `ShotService`:

```java
public static final class PendingImpact {
    long victimId; long shooterId; float remainingTime;
    float damage; float vsTurretMult; float moraleImpact;
    boolean friendly; // pre-multiplied damage is fine; flag kept for FX/log
}
public void queueImpact(PendingImpact p)              // synchronized append
public void tickImpacts(float dt, ImpactSink sink)    // serial SHOTS phase
```

`queueImpact` is called from the parallel UPDATE_UNITS dispatch — same
monitor discipline as `postShot`. `tickImpacts` runs in the serial phase
right before/with `tickShots`; the sink (provided by `BattleSimulation`)
guards `roster.isAliveById(victimId)` then routes to the existing
`DamageService.applyDamage(victim, damage, vsTurretMult, moraleImpact)` +
`HitResponseSystem.rollFallbackOnHit` / `rollReprioritizeOnHit` calls that
currently sit inline in `fireShot`. Friendly hits: damage already
multiplied by `FRIENDLY_FIRE_DAMAGE_MULT` at queue time; hit-response
rolls still apply (being shot by your own side is still getting shot).

## `InfantryWeapons.fireShot` swap

Keeps: burst plumbing, `RangeFalloff` accuracy + spread math, stance
multiplier, `InfantryCombatStats`, morale impact sourcing.
**Removes: the `coverAccuracy.apply(...)` term** (infantry only —
`CoverAccuracyResolver` stays alive for the mech path until S4) and the
inline `damageService`/`hitResponse` calls and `ShotEndpoint` usage.

New flow: compute `finalAccuracy` (no cover) → `resolver.resolve(...)` →
if `victimId != 0`, queue `PendingImpact` at `flightTime` →
`postShot(new ShotEvent(from, res.endX, res.endY, res.hitIntended, ...,
lifetime = max(res.flightTime, epsilon)))`. Weapon velocity: use
`weapon.flightSec > 0 ? dist/flightSec`-derived velocity if a projectile
weapon, else `DEFAULT_ROUND_VELOCITY`; do NOT add a new weapon stat in S1
(S2 owns per-weapon velocity as a real stat).

Militia/aliens/turrets (null `MarineWeapon`) go through the same resolver
with their baked accuracy, spread 0, default velocity.

## `SquadMoraleSystem` near-miss → path proximity

`squadHitByMiss`: distance from squad member to the **segment**
(from→to) replaces distance-to-endpoint; same 2.25 squared-cell
threshold, same single-drain-per-shot rule.

## Tests (deterministic `Random` seeds / stub rng)

- Resolver: event ordering (nearer doodad rolls before farther unit);
  wall hard-stop caps the ray; muzzle-clearance skips the adjacent
  friendly but not an adjacent enemy; failed target roll flies on and can
  graze the unit behind; cover-clip uses grid cover only (a doodad next
  to the victim must not double-roll); overshoot endpoint when everything
  misses; block-chance mapping 15/30/45.
- `gatherAlongSegment`: hits units near the middle of a long diagonal ray
  that a radius query around either endpoint would miss.
- `SquadMoraleSystem`: round passing 1 cell from a squadmate with a far
  endpoint still drains morale.
- Guard: pending impact whose victim died mid-flight applies nothing.

## Invariants / repo rules

- Parallel-dispatch safety: `resolve()` does reads only (grid, doodads,
  index snapshot, roster by-id); all mutation flows through synchronized
  queues drained in serial phases.
- Dense-registry rule: never iterate-then-kill; impacts drain via the
  queue in the serial phase (gather-then-apply is inherent here).
- Imports + simple names, never inline FQNs (CLAUDE.md Code style).
- Comment discipline: Javadoc API + non-obvious invariants only.

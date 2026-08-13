# Ballistics — modeled small-arms rounds

> Replace small-arms pure accuracy rolls with fire-time-resolved ballistic
> rays: rounds have a velocity and a line of flight, collide with unit radii
> (hit roll on contact), roll against doodads, and hard-stop on walls.
> Damage and visuals ride the flight clock; the sim never steps collision
> per tick.

## Status

**Design-stage.** Nothing implemented. Design settled 2026-08-13 (session
conversation); this doc is the record. Pick up implementation **in a
worktree** (owner request — see `next-session.md`).

## Why

Today `InfantryWeapons.fireShot` is hitscan: one accuracy roll
(base × `RangeFalloff` × `FireStance` × `CoverAccuracyResolver`), damage
applied inline at fire time, `ShotEvent` posted as a purely visual mirror
with a jittered endpoint. Consequences we want gone:

- Cover is an abstract multiplier — a crate never actually *stops* a round.
- Infantry rounds do no wall raycast at all; a scattered miss visually
  sails through walls (only turret spray kinds / mech chainguns wall-snap
  via `ShotRaycast`).
- A missed round affects nothing; whoever stands in the fire lane is safe.
- Projectile speed is cosmetic (`ShotEvent.lifetime`), so it can never
  matter tactically.

The AoE path already crossed this bridge — `Projectile` is a real in-flight
entity ("rounds don't miss, they just land somewhere"). This feature extends
that philosophy to direct fire.

## Core design decisions

### 1. Fire-time resolution, flight-time application

The entire flight is resolved **once at fire time** with a single
space-time raycast. No per-tick swept collision — rifle flight times are
fractions of a second, and the rocket path already established "a marine
who moves between launch and impact escapes." Damage application and
impact FX are scheduled at `dist / velocity` on the existing
`ShotService` clock (the `shotsExpiredThisFrame` / arrival-sink shape).

This also improves concurrency: parallel UPDATE_UNITS does read-only
trajectory resolution; damage flows through the serial arrival phase
instead of being applied inline mid-dispatch.

### 2. Moving targets: project along the round's timeline

Candidates are tested in **space-time**, not against fire-tick positions.
The round travels `P(t) = O + v·d·t`; each candidate unit extrapolates
`U(t) = U₀ + w·t` from its `MOVEMENT_VEL_X/Y` columns (already live —
`FacingSystem` consumes them). Contact = closest-approach solve on the
relative motion `R(t) = (U₀ − O) + (w − v·d)·t`, contact when
`|R(t)| ≤ r_unit + r_round`, valid for `0 ≤ t ≤ flightTime`.

This is what makes projectile *speed* a real modeled quantity: fast rounds
behave like today; slow stylized rounds interact honestly with lateral
movement — and stay a single fire-time computation.

### 3. Shooters lead the target

If collision extrapolates but aim doesn't, slow rounds systematically miss
lateral movers by `w × flightTime`. So the aim point is the target's
predicted position at estimated intercept (`t ≈ dist / v`, one step is
enough). Baseline: perfect lead (tuning-neutral). Later hook: lead error
keyed to aptitude/experience — slow weapons vs. movers become a skill axis.

### 4. Cover exits the accuracy roll, becomes physical interception

The double-count trap: cover currently multiplies accuracy; if doodads also
physically block, cover applies twice. So the swap is atomic — delete the
`CoverAccuracyResolver` term from the hit roll and re-express cover as
per-round block probability at the doodad/wall-facing cell the ray crosses.

**Tuning-neutral anchor:** today's multipliers 0.85 / 0.70 / 0.55 for
levels 1/2/3 ≡ block chances **15% / 30% / 45%**. Hit chance against the
covered target is unchanged at swap time; everything else (rounds splat on
the crate, strays stopped, interposers at risk) is gained emergently.

### 5. Accuracy stack survives as the per-contact hit roll

Geometry decides *who can be hit*; the roll decides *whether it connects*.
Range falloff, stance, aptitude, equipment grade all stay as tuned — they
become the hit probability rolled when the ray contacts a unit's radius.
The purist alternative (accuracy as pure angular error, hits fully
emergent) forces a rebalance of every accuracy stat; rejected for v1.
`effectiveSpread` (already distance-scaled) becomes lateral offset on the
trajectory so misses still look organic.

### 6. Committed outcomes, walk-in-order

All rolls happen at fire time. Contacts (wall cap, doodad crossings, unit
closest-approaches) sort by contact time; walk in order — first blocker or
successful hit stops the round, failed rolls let it fly on. A round that
misses its intended target **keeps flying** and can hit whoever is behind.
Impact tick re-guards only `isAlive` (existing released-target pattern in
`DamageResolver`); no re-validation of position — the prediction is the
outcome.

### 7. Sim/visual split per the rocket precedent

A lightweight sim-side pending-impact record (owned by `ShotService`) is
the source of truth; `ShotEvent` stays the visual mirror. Per-shot
`lifetime = dist / weaponVelocity` — the same fix `Projectile`'s header
documents for the distance-scaled-speed artifact — which is what makes
rounds renderable as visible stylized projectiles (SMG already has
`projectileSpritePath` + `flightSec` plumbing to generalize).

## Existing infrastructure to lean on

| Need | Already exists |
|---|---|
| Wall collision | `NavigationGrid.firstWallOnLine` (Bresenham) |
| Doodad block data | `DoodadService` per-cell/per-facing cover bytes |
| Unit proximity | `UnitSpatialIndex` (needs a segment/ray gather) |
| Target velocity | `MOVEMENT_VEL_X/Y` columns |
| Delayed damage safety | `DamageResolver` released-target guards; arrival-sink pattern |
| Impact clock + FX dispatch | `ShotService` tick / `shotsExpiredThisFrame` |

New: per-`UnitType` collision radius (infantry ~0.35 cells, mechs larger);
segment query on the spatial index (bucket walk along the ray with an
expansion margin of `maxUnitSpeed × maxFlightTime + maxRadius`).

## Open questions (decide at pickup, before S1 lands)

1. **Friendly-fire policy.** Rounds that fly past a failed roll can hit
   friendlies in the lane. Options: full damage (hard-failure flavor),
   reduced graze damage, or morale-only at first. Must be decided in S1 —
   the moment rounds fly on, *some* rule executes.
2. **Near-miss morale.** `SquadMoraleSystem` uses miss-endpoint proximity
   (1.5 cells). With real paths, near-miss should become path-proximity or
   suppression feel changes. Ship with endpoint semantics in S1, migrate in
   the unification story?
3. **Incidental-contact hit roll.** Intended target uses the full accuracy
   stack. Non-target contacts: flat graze chance, or scaled by chord depth
   through the radius? (Recommend flat to start.)

## Stories

- **S1 — resolver core (static world).** `BallisticResolver` in
  `battle/combat`: fire-time ray vs walls (hard stop), doodad block rolls
  (cover swap lands here, atomically), stationary unit radii, contacts
  walked in time order, damage/hit-response relocated to the impact clock.
  Infantry primary swaps over. Friendly-fire rule decided and implemented.
- **S2 — moving targets.** Velocity extrapolation in the contact solve +
  shooter lead. Per-weapon round velocity becomes a real stat.
- **S3 — stylized visible rounds.** Renderer consumes per-shot
  velocity-derived lifetimes; tracer/sprite pass for visible rounds.
- **S4 — direct-fire unification.** Mech chaingun + turret spray kinds
  adopt the resolver; retire `ShotRaycast` and `ShotEndpoint`'s miss ring;
  near-miss morale goes path-proximity.

Future hooks (post-S4, backlog): doodad HP / cover erosion; lead error by
aptitude; point defense generalization (`Projectile.intercepted` reserved).

## Cross-refs

- `battle/combat/package-info.java` — pipeline charter this slots into.
- `Projectile.java` header — the "no hit/miss, velocity is real" precedent.
- Battles are transient (no mid-battle save/load) — pending impacts never
  serialize.

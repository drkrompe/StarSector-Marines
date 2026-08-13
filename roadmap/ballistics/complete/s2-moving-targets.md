# S2 — moving targets — SHIPPED

> **Shipped 2026-08-13** on `worktree-ballistics-s2s3`, commit `d1664bd8`.
> Landed as specced below — no material deviations. `BallisticResolver`,
> `MovementService`, `MarineWeapon`/`InfantryWeapons`, and 8 new
> `BallisticResolverTest` cases (stationary regression, perpendicular-mover
> lead with a companion no-lead-would-miss quadratic check computed inline
> in the test rather than a second live `resolve()` call, corridor-entering
> mover, mover-leaving-cover split-position, degenerate `a≈0` pacing,
> behind-shooter exit-root skip re-asserted in time domain, and muzzle
> clearance / wall cap re-asserted as still-distance-domain under motion).
> Full suite green (1482 tests) before commit.

Original contract below, kept verbatim for the record.

---

> The contact solve goes space-time: candidates extrapolate along their
> current velocity, shooters lead the aim point, and round velocity becomes
> a real per-weapon stat. This is the story that makes projectile *speed*
> tactically meaningful — slow rounds honestly interact with lateral
> movement.

Parent design: [`../overview.md`](../overview.md) §2 (space-time contacts)
and §3 (shooter lead). S1 record:
[`../complete/s1-resolver-core.md`](../complete/s1-resolver-core.md).

## Scope

Three changes, all landing together (a lead without extrapolation
systematically misses; extrapolation without lead systematically misses —
they only balance as a pair):

1. **Time-domain contact solve** in `BallisticResolver`.
2. **Shooter lead** — aim at the target's predicted intercept position.
3. **`MarineWeapon.roundVelocity`** — a real stat, replacing S1's
   `dist / flightSec` derivation.

Out of scope: lead *error* (aptitude/experience-keyed — future hook per
overview), chord-depth incidental scaling, S3's visible-round presentation,
S4's mech/turret unification.

## 1. Time-domain contact solve

S1 solves ray-circle in the **distance** domain against fire-tick positions.
S2 re-poses unit contacts in the **time** domain:

- Round: `P(s) = O + d·v·s` (s in seconds, v = roundVelocity, d = unit ray
  direction).
- Candidate: `U(s) = U₀ + w·s`, where `w` = the unit's
  `MOVEMENT_VEL_X/Y` (velocity actually applied last tick, cells/sec).
  Non-movers (turrets, hubs — no MOVEMENT component) use `w = 0`, which
  collapses to exactly S1's solve.
- Relative: `R(s) = (U₀ − O) + (w − v·d)·s`; contact when `|R(s)| ≤ r`
  (r = `UnitType.radius`, unchanged).

Quadratic `a·s² + b·s + c = 0` with `a = |w − v·d|²`,
`b = 2·R₀·(w − v·d)`, `c = |R₀|² − r²`:

- `a < 1e-6` (unit pacing the round exactly — rare degenerate): R is
  constant; contact iff `c ≤ 0` (already overlapping at fire time), entry
  `s = 0`. Otherwise skip.
- Exit-root skip carries over from S1 verbatim, in seconds: `sExit < 0` →
  circle entirely behind the shooter → skip (no false t=0 clamp).
- `sEntry < 0` → clamp to 0 (shooter inside the radius / straddling).
- **Wall cap** stays a distance test: skip when `v·sEntry > rayLen`. The
  wall is static, so distance-along-ray is the correct cap even for moving
  contacts.
- **Muzzle clearance** stays a distance test too:
  `friendly && v·sEntry < FRIENDLY_MUZZLE_CLEARANCE` → skip.

Event bookkeeping: unit events already store `t` in seconds (S1 divides by
velocity); doodad events are static crossings and keep their S1 math. The
sort-and-walk loop is untouched.

**Contact positions split**: the round's FX endpoint is `P(sEntry)` (where
the round is); the **cover edge-clip lookup** uses the *victim's*
extrapolated cell `floor(U(sEntry))` — a target sprinting out from behind
a parapet has left the covered cell by contact time, and vice versa. S1
used the fire-tick cell for both; the split is the honest reading of
"committed outcomes: the prediction IS the outcome."

### Velocity read

`MovementService` gains `velX(long id)` / `velY(long id)` by-id getters
(fail-loud on non-movers, same contract as `moveSpeed`). The resolver
guards with `roster.movement().has(id)` → else `w = 0`. Per the
World-facade deprecation rule this is Service-direct — do NOT add
`World.velX`.

Concurrency note: `MOVEMENT_VEL_X/Y` are written by `MovementService`
(zeroed + rewritten during the movement pass) and nudged by
`SeparationSystem`. The resolver runs in parallel UPDATE_UNITS and reads a
possibly mid-tick value — that's fine: velocity is an extrapolation hint,
not an invariant, and a one-tick-stale read moves a contact point by
`≤ moveSpeed × TICK_DT` cells. Document, don't synchronize.

### Gather margin

S1's flat `GATHER_MARGIN_CELLS = 1.0` only covers static radii. A mover can
enter the corridor during flight, so the margin becomes:

```
margin = GATHER_MARGIN_CELLS + MAX_MOVER_SPEED_CELLS × (rayLen / roundVelocity)
```

with `MAX_MOVER_SPEED_CELLS = 4f` as a resolver constant (fastest live
mover today is ALIEN at 2.2 cells/s; 4 leaves headroom for vehicles) — a
tuning-surface constant per S1 precedent, not a per-call roster scan. For a
default-velocity rifle shot (rayLen ≈ 24, v = 60) that's +1.6 cells; for a
slow stylized round it grows honestly with exposure time.

## 2. Shooter lead

In `resolve()`, before the spread jitter: one-step predicted intercept.

```
tLead = dist(shooter, target) / roundVelocity
aim   = targetPos + wTarget × tLead        (wTarget = 0 for non-movers)
```

Spread jitter applies around the **led** aim point; everything downstream
(overshoot, wall cap, doodad walk) is unchanged. Perfect lead, per the
overview: tuning-neutral baseline — a perfectly-led shot at a
constant-velocity target contacts exactly as a stationary shot does today,
so accuracy stats keep their meaning. Lead error keyed to
aptitude/experience is the future hook, not this story.

Callers don't change: lead lives inside the resolver (it already owns the
aim-point construction, and every future direct-fire adopter in S4 gets it
for free).

## 3. Per-weapon round velocity

`MarineWeapon` gains `roundVelocity` (cells/sec). `InfantryWeapons.fireShot`
uses it when > 0, else `BallisticResolver.DEFAULT_ROUND_VELOCITY` (militia
/ alien / turret null-weapon callers keep the default). The S1 stopgap
derivation `dist / flightSec` is **deleted**, and `MarineWeapon.flightSec`
goes with it — its only consumer was that derivation
(`projectileSpritePath` / `projectileVisualCells` stay; `ShotFx` reads
them). `MarineSecondary.flightSec` (rockets) is untouched.

Initial values (S3's visible rounds will retune by feel):

| Weapon | roundVelocity | Rationale |
|---|---|---|
| PULSE_RIFLE | 55 | visible energy bolt, near-baseline feel |
| SMG | 45 | slow saturation fire — the "honest slow round" showcase |
| DMR | 110 | railgun: effectively flat, movers barely matter |
| DRONE_PULSE | 55 | matches pulse rifle |

At these speeds a perpendicular sprinter (2.2 c/s) displaces ~0.4–1.0 cells
over a max-range flight — the lead is visible but the extrapolated solve
keeps led shots connecting. `DEFAULT_ROUND_VELOCITY = 60` is unchanged.

## Tests

- **Stationary regression**: `w = 0` everywhere → resolutions identical to
  S1 (same seed, same outcomes) — the time-domain rewrite is a pure
  refactor for static scenes.
- **Perpendicular mover, perfect lead**: constant-velocity target crossing
  the line of fire; assert the led ray contacts it (entry root within
  radius at predicted intercept), and that WITHOUT lead (aim at U₀) the
  same geometry misses — proves lead and extrapolation balance.
- **Mover entering the corridor**: candidate whose fire-tick position is
  outside the S1 margin but who walks into the ray during a slow round's
  flight → gathered (margin term) and contacted (time-domain solve).
- **Mover leaving cover**: victim with wall cover at fire tick,
  extrapolated out of the covered cell by contact time → no edge-clip roll
  (the split-position rule).
- **Degenerate pacing**: `a ≈ 0` with `c > 0` → no contact, no NaN.
- **Behind-shooter regression**: S1's exit-root test re-asserted in the
  time domain.
- **Muzzle clearance / wall cap**: still distance-domain — friendly at
  1.9 cells skipped at any velocity; contact past the wall cap skipped.

## Files touched (expected)

- `battle/combat/BallisticResolver.java` — time-domain solve, lead,
  margin, `MAX_MOVER_SPEED_CELLS`.
- `battle/sim/MovementService.java` — `velX`/`velY` getters.
- `battle/infantry/MarineWeapon.java` — `roundVelocity` stat, `flightSec`
  removed.
- `battle/infantry/InfantryWeapons.java` — velocity resolution swap.
- Tests: `BallisticResolverTest` additions; `FiringSystemTest` velocity
  expectations if any pin `dist/flightSec`.

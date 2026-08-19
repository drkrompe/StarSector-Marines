# Ballistics — modeled small-arms rounds

> Replace small-arms pure accuracy rolls with fire-time-resolved ballistic
> rays: rounds have a velocity and a line of flight, collide with unit radii
> and vertical silhouettes, roll against doodads, and hard-stop on walls.
> Damage and visuals ride the flight clock; the sim never steps collision
> per tick.

## Status

**S1 shipped 2026-08-13** (`complete/s1-resolver-core.md` — includes the
landed-vs-planned deviations, notably: the contact circle is the
pre-existing `UnitType.radius`, shared with `SeparationSystem`, not a new
stat). **S2 shipped 2026-08-13** (`complete/s2-moving-targets.md` — time-domain
contact solve, shooter lead, per-weapon `roundVelocity`; landed as specced).
**S3 shipped 2026-08-19** (`complete/s3-visible-rounds.md` — traveling tinted
bolts on the real flight clock, shared arrival-timed impact semantics). **S3a
shipped 2026-08-19** (`complete/s3a-weapon-fx-families.md` — reuse-first pulse,
rail, and drone projectile silhouettes). **S3b shipped 2026-08-19**
(`complete/s3b-target-plane-accuracy.md` — one target-plane accuracy commit,
visible lateral/high/low flight, vertical silhouettes). S4 (direct-fire
unification) follows the active S3c obstacle-height pass; see
`next-session.md`.

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

**Two interception mechanisms, no overlap** (this is how the double-count
is structurally avoided, not just tuned away):

- **Doodads block by ray crossing.** A ray that crosses a doodad cell rolls
  block by that doodad's level. Directionality is emergent — flank the
  crate and the ray no longer crosses it. Doodad *facing* cover is never
  consulted at unit contact.
- **Wall cover blocks by edge-clip at contact.** A target hugging a wall
  corner gains nothing from ray crossing (the ray never enters the wall
  cell — the shooter has LoS). So the grid's directional wall cover
  (`NavigationGrid.getCoverAt`, facing toward the shooter) survives as a
  block roll applied *at unit contact* — "the round clipped the parapet."
  Wall cells crossed by the ray itself remain a hard stop
  (`firstWallOnLine`).

### 5. Accuracy stack authors the intended trajectory once

Range falloff, stance, aptitude, equipment grade, and target evasion all stay
as tuned, but their one combined roll now chooses an aim point in the plane
perpendicular to fire. Successful aim lies inside the intended target's
horizontal-radius × vertical-half-height silhouette. Failed aim lies beyond
that silhouette with a clearance widened by `effectiveSpread`. Lateral error
changes the physical XY ray; elevation becomes a lightweight linear Z path
projected into screen Y. The intended target never makes a second invisible
hit roll. Incidental contacts retain their flat graze chance.

### 6. Committed outcomes, walk-in-order

All rolls happen at fire time. Contacts (wall cap, doodad crossings, unit
closest-approaches) sort by contact time; walk in order — first blocker or
successful contact stops the round, failed incidental rolls let it fly on. A
round that misses its intended target **keeps flying** and can hit whoever is
behind if its real XY/Z trajectory intersects their silhouette.
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

Ballistics shares each `UnitType.radius` with separation, picking, and AoE,
and now pairs it with `UnitType.hitHalfHeight` for target-plane contact. The
spatial-index segment query expands its bucket walk by flight exposure so
movers can enter the corridor before contact.

## Resolved questions (owner decisions, 2026-08-13)

1. **Friendly fire = partial damage.** Rounds that fly on can hit
   friendlies in the lane at reduced damage
   (`FRIENDLY_FIRE_DAMAGE_MULT = 0.5`). Deliberately annoying — the pain is
   a diegetic prompt toward better training and equipment for the troops.
   Softener: friendly contacts within `FRIENDLY_MUZZLE_CLEARANCE = 2.0`
   cells of the shooter are skipped (you shoot *around* the squadmate at
   your shoulder), so clustered squads don't grind themselves down at
   point-blank.
2. **Near-miss morale goes path-proximity in S1.** The miss ring dies with
   the swap, so endpoint semantics would under-trigger (a missed round's
   endpoint lands far downrange). `SquadMoraleSystem.squadHitByMiss`
   switches from point-to-endpoint to point-to-segment distance — small
   change, same 1.5-cell threshold.
3. **Incidental contacts roll a flat graze chance**
   (`INCIDENTAL_HIT_CHANCE = 0.35`); only the intended target uses the full
   accuracy stack. Chord-depth scaling deferred until feel says otherwise.

## Stories

- ~~**S1 — resolver core (static world).**~~ **SHIPPED** — see
  [`complete/s1-resolver-core.md`](complete/s1-resolver-core.md)
  (`85ec50e6` + `382b0ee6` on `worktree-ballistics-s1`).
- ~~**S2 — moving targets.**~~ **SHIPPED** — velocity extrapolation in the
  contact solve + shooter lead; per-weapon round velocity is now a real
  stat. See [`complete/s2-moving-targets.md`](complete/s2-moving-targets.md).
- ~~**S3 — stylized visible rounds.**~~ **SHIPPED** — primaries render as
  traveling tinted bolts on the flight clock (`ShotFx.Bolt` + generated
  white-base sprite). See
  [`complete/s3-visible-rounds.md`](complete/s3-visible-rounds.md).
- ~~**S3a — weapon FX families.**~~ **SHIPPED** — the shared traveling-body
  pipeline now gives pulse, rail, and drone fire distinct silhouettes by
  reusing vanilla assets before generating new art. See
  [`complete/s3a-weapon-fx-families.md`](complete/s3a-weapon-fx-families.md).
- ~~**S3b — target-plane accuracy.**~~ **SHIPPED** — intended accuracy is
  committed once into lateral/elevation aim, contacts use lightweight vertical
  silhouettes, and visible rounds project high/low flight. See
  [`complete/s3b-target-plane-accuracy.md`](complete/s3b-target-plane-accuracy.md).
- **S3c — obstacle catch heights.** Active follow-up: gate doodad and
  directional wall-edge block rolls on vertical overlap while structural walls
  remain full-height. Story:
  [`stories/s3c-obstacle-catch-heights.md`](stories/s3c-obstacle-catch-heights.md).
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

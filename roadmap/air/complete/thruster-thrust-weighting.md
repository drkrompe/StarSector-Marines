# Story — Thrust-weighted engine FX (per-thruster glow from the flight model)

> Shared-core member of the [`air/`](../overview.md) category. Continues the
> "drive the visual from the real flight model" theme of
> [`anchor-at-center-of-gravity.md`](anchor-at-center-of-gravity.md) — that story
> made `body` mean the centroid of gravity, which is precisely the pivot a
> per-thruster torque calc needs. This story spends that for free.

## Shipped — `cb31f2c`, in-game eyeball pending

Landed as designed. `AirBody` records `ax/ay` + the now-live `angVelDegPerSec`
in `tickToward`; new pure `ThrusterDemand.compute` returns the per-slot `[0,1]`
weighting; `EngineFxRenderer.draw`/`emitLights` gained an optional
`perSlotDemand` overload (null = the old uniform path, which the legacy flyby
fighters still use); `Shuttle.engineFxIntensity` simplified to the master
altitude throttle. `ThrusterDemandTest` (pure, 4 cases) green; full suite
compiles.

**Outstanding:** in-game eyeball that aft mains flare on acceleration / dim on
coast+brake and that a hard bank lights the maneuvering side asymmetrically.
Weights (`W_VEL`, `W_ACCEL` in `ThrusterDemand`; `DEMAND_FLOOR` in
`EngineFxRenderer`) are first-cut — retune against the live read.

## The model today

Every air entity has N engine slots scraped from its `.ship`
(`EngineSlotResolver` → `EngineSlotData`). The render pass
(`EngineFxRenderer.draw`) draws a plume per slot, but **all slots share one
scalar `intensity`** — `Shuttle.engineFxIntensity()` = altitude throttle × a
crude `(HOVER_FX_FLOOR + speed/maxSpeed)` factor. So:

- Every thruster glows the **same** amount regardless of where it sits or which
  way it fires.
- A hard turn looks identical to straight cruise — no maneuvering read.
- Acceleration looks identical to coasting — no "leaning on the throttle" read.

The plumes are decorative, not diegetic. The hull *has* the data to do better:
each slot already carries its fire direction (`angleDegrees`), and post-CoG-anchor
its `(localX, localY)` is the lever arm about the pivot.

## The fix — weight each plume by how much that thruster is *working*

A thruster's job is to push the ship (linear) and/or spin it (rotational). Read
both off the flight model and weight each plume by its alignment — a dot product
for translation, a cross product (torque) for rotation. Bigger glow ⇒ that
nozzle is doing more work ⇒ the eye reads thrust.

### Thrust axis per slot

The plume **exhausts** along `facing + slot.angle`; the **force on the ship** is
opposite (Newton's third law). In slot-local terms (rotation drops out of the
sign work, so compute local):

```
plumeLocal  = slot.angleDegrees            // 0 = +Y/nose, CCW+, vanilla main ≈ 180
thrustLocal = ( sin(plumeLocal), -cos(plumeLocal) )   // = -exhaustDir
```

A vanilla main (angle 180) gives `thrustLocal = (0, +1)` — pushes the nose
forward. Correct.

### Linear term — translation alignment

The body exposes its **measured acceleration** `(ax, ay)` (this tick's Δv/dt =
the "intended velocity change" the user asked for) and its **velocity**
`(vx, vy)`. For each slot, rotate `thrustLocal` into world by facing, then:

```
linear = W_VEL   · max(0, thrustWorld · v̂) · min(1, |v|/maxSpeed)     // sustain cruise
       + W_ACCEL · max(0, thrustWorld · â) · min(1, |a|/accelRef)      // bloom on accel
```

- Coasting at cruise: the velocity term keeps the mains lit (engines don't look
  dead). Braking flips `â` aft, so the forward mains *dim* and any retro slot lights.
- The accel term is the boost: stand on the gas and the aft mains flare above
  the cruise baseline.

### Rotational term — torque alignment

A slot offset from the CoG produces a torque `τ = r × thrustLocal`
(z-component `r.x·t.y − r.y·t.x`). Its sign says which way it spins the hull
(+ = CCW = increasing facing, our convention). Normalize by the lever arm to a
dimensionless tangential fraction, and gate by how aligned it is with the
**actual** turn the body is doing (`angVelDegPerSec`, now populated):

```
tangential = (r × thrustLocal) / |r|                  // sin(angle r,thrust) ∈ [-1,1]
rotational = max(0, sign(angVel) · tangential) · min(1, |angVel|/turnRateRef)
```

So in a hard left bank the thrusters whose torque pushes left flare; their
mirror-image partners on the other side go quiet — RCS-style asymmetry, for free,
because the slots are CoG-relative.

### Combine

```
demand[i]   = clamp(max(linear, rotational), 0, 1)
perSlotMult = DEMAND_FLOOR + (1 - DEMAND_FLOOR)·demand[i]   // never fully dark
```

`max` (not sum) so a thruster reads "on" if it's helping *either* job, without
double-counting. `DEMAND_FLOOR` keeps a faint always-on plume so lit engines
never blink fully off.

## The pieces

1. **`AirBody`** — record the dynamics the model reads. At the end of
   `tickToward`, derive `ax = (vx − vxPrev)/dt`, `ay` likewise, and populate the
   already-declared `angVelDegPerSec` from the actual facing slew (shortest-arc
   Δfacing/dt). `teleport` zeros them. The body becomes self-describing; no new
   plumbing through the steering call.

2. **`ThrusterDemand`** (`battle/air/engine/`, new) — pure static
   `compute(EngineSlotData[] slots, AirBody body, AirHandling handling)` →
   `float[]` per-slot demand in `[0,1]`. All the math above; tunable weights as
   named constants. Pure (no `Global`) so it unit-tests offline.

3. **`EngineFxRenderer`** — add an optional `float[] perSlotDemand` (null =
   uniform, the old behavior) to `draw` and `emitLights` via overloads; the
   existing scalar-only signatures delegate with `null`. When present, the
   per-slot effective intensity is `intensity · (FLOOR + (1−FLOOR)·demand[i])`.

4. **`Shuttle.engineFxIntensity()`** — simplify to the master altitude throttle
   (`engineIntensity()`); the per-slot model now owns the speed/stationary
   modulation that the old `HOVER_FX_FLOOR`/`speedT` factor approximated
   uniformly. Hovering still reads dim (no velocity, no accel, no turn → all
   slots at `DEMAND_FLOOR`).

5. **Call sites** — `ShuttleRenderSystem.draw` and `BattleScreen` (emitLights)
   compute the demand array (`ThrusterDemand.compute(slots, s.body, s.type)`) and
   pass it. `FlybyOverlay` stays on the uniform overload (legacy cosmetic
   fighters, fixed-throttle flyby — no flight model to read).

6. **Test** — `ThrusterDemandTest` (pure): aft main blooms on forward accel and
   dims on brake; a port/starboard slot pair is asymmetric under ±turn and
   symmetric in straight flight; a stationary body floors every slot.

## Out of scope

- Trail / contrail emitters (`contrailSizeCells` is parsed, unused) — separate FX.
- Audio: `engineIntensity()` (the loop driver) is untouched; this is plume-only.
- Vectoring the plume *direction* (gimbaling toward the maneuver) — we modulate
  size/brightness only; the plume still fires along the authored slot angle.
- Ground vehicles (`GroundBody`) — no plume FX; revisit if tanks get exhaust.

## Done when

- A shuttle's aft mains visibly flare when it leans into acceleration and dim
  when it coasts/brakes; a hard turn lights the maneuvering side asymmetrically.
- `AirBody` reports `ax/ay/angVelDegPerSec`; `ThrusterDemand` is unit-tested.
- The uniform path still works for the legacy flyby fighters (null demand).
- Story moves to `complete/` with the shipped commit hash.

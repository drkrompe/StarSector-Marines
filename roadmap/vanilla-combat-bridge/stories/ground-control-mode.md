# Ground battle control mode + contextual see-through

> A new player-facing concept: a **mode** in which the player commands/inspects the
> *ground* sim while the vanilla fleet fights *above* it. Its first concrete affordance
> is **contextual see-through** — player ships near the cursor fade so the ground beneath
> them is legible. Concept + the see-through decision + the first probe. Forward-looking;
> the mode itself is not yet built.

## The concept

The bridge (S0–S3) proved the two engines co-exist: the headless ground sim renders on a
below-ships layer while a real vanilla fleet fight runs above it ([`../overview.md`](../overview.md)
S3). What it does **not** yet have is a player-facing way to *act on the ground battle* —
select a squad, read a turret's state, issue a ground order — distinct from piloting/commanding
the fleet.

**Ground battle control mode** is that layer: a mode the player enters (toggle / context) in
which the cursor and inputs are bound to the **ground sim**, not the fleet. The fleet keeps
fighting above under its own AI (or the skybattle command layer, see
[`skybattle-fleet-control.md`](skybattle-fleet-control.md)); the player's attention drops to
the surface.

The motivating friction: **the fleet's ship sprites occlude the ground.** A carrier parked
over the band hides exactly the squad you want to inspect. You need to see *past* the ships
without removing them.

## Why occlusion is a pure-visual problem (the de-risk)

In ground-control mode the input handler consumes events pre-core-controls
(`processInputPreCoreControls` + `event.consume()`, [`../overview.md`](../overview.md) fact 9)
and runs its **own** ground-unit hit-testing — the same role `WorldPicker` plays in the
standalone `BattleScreen`. Ground units are *our* sim entities drawn by *our* renderer, not
vanilla combat entities, so clicks meant for them never route through vanilla's ship picking.

**Consequence:** vanilla ships are *purely visual* obstructions in this mode — they block
*sight*, never *interaction*. And a purely-visual problem has a purely-visual fix: alpha. No
stencil masking, no click-through hack, no depth trickery is needed. This is what makes the
see-through affordance cheap and self-contained.

## Contextual see-through — the first affordance

When the mode is active, **player ships near the cursor fade**, lerping their alpha down so
the ground shows through, and lerp back up when the cursor moves away.

### Decision: cursor reveal disk (spotlight), not hard per-ship hover

A literal "fade the one ship the cursor is over" was considered. Chosen instead: a
**cursor-anchored reveal disk** — every player ship within a reveal radius of the cursor fades
by distance, with a soft falloff. Rationale:

- Handles **overlapping / clustered ships** gracefully (a fleet stacks hulls; one-ship-hover
  has to pick a "topmost" and pops as the cursor crosses seams).
- The spatial falloff composes with the temporal lerp for a smooth, popping-free feel.
- Reads as an **x-ray lens** the player moves around — a clearer mental model than "hover to
  ghost," and it naturally reveals a *region* of ground, which is what inspecting the surface
  wants.

The reveal radius is a constant **on-screen** size (derived from `visibleWidth/screenWidth`),
so the lens feels the same at any zoom; ship `collisionRadius` is subtracted from the
cursor-distance so the cursor merely *touching* a large hull already fades it.

### Mechanism

Same lever the proxies use to be invisible ([`../overview.md`](../overview.md) fact 6),
applied partially to the *real* fleet:

- **Channel:** `ShipAPI.setExtraAlphaMult2(a)` + `setApplyExtraAlphaToEngines(true)`. Mult2
  is the safe channel — the engine drives mult1 (`setExtraAlphaMult`) for phase/arrival
  fades, and `PhaseAnchor` itself uses mult2 precisely to avoid stomping mult1
  (`.api/.../hullmods/PhaseAnchor.java`). Re-applied each frame.
- **Per-ship lerp:** each ship holds a current alpha that eases toward its target
  (disk-fade when the mode is on, 1.0 otherwise). Once a ship returns to ~1.0 with the mode
  off, we stop touching it.
- **Which ships:** `getOwner()==0` (player side) only — which **also excludes the sim
  proxies for free** (they spawn on `FleetSide.ENEMY`, owner 1, and live at mult1=0; fading
  them *up* would reveal them). Fighters are player ships too and occlude, so they're included.
- **Cursor → world:** `viewport.convertScreenXToWorldX/Y(Mouse.getX/getY())`; under the
  spectator's external-control camera the convert methods read our custom rectangle, so the
  mapping tracks the free camera.

### Known limit (acceptable at this scale)

`setExtraAlphaMult` fades the **hull sprite** (and engines, with the flag); it does **not**
fade projectiles, beams, missiles, shields, explosions, or muzzle flashes — those are separate
render entities and keep drawing over the ground. At **per-cursor scale** this is negligible
(you're ghosting a few hulls under the lens, not the whole fleet's FX), and the thing that
actually occludes — the hull — is exactly what alpha kills. A blanket "fade the entire fleet"
view does *not* hold up under this caveat; the spotlight is the right granularity.

## Probe: `SeeThroughPlugin` (toggle key `X`)

A throwaway `EveryFrameCombatPlugin` to *feel the fade* against this doc, installed by
`CombatBridgeSession.enterEngine` alongside the other bridge plugins:

- Press **`X`** in a SIM_COUPLED bridge battle to toggle see-through mode (stands in for the
  real mode entry). `L` is taken by `CarrierDescentPlugin`; `X` is free and consumed
  pre-core-controls, so it's safe in the spectator canvas.
- While on, the reveal disk follows the cursor and fades player ships by proximity; while off,
  ships lerp back to full and the plugin stops touching them.
- Config-free — it needs only the engine + cursor, so it's reusable beyond this scenario.

The probe validates the *feel* (lens size, falloff, lerp rate, the mult2 channel surviving
real ship state). The **mode** it stands in for is the actual design work below.

## Open questions (the mode proper)

1. **Mode entry in production.** The probe toggles with `X`; the shipped mode needs a real
   trigger — a dedicated command-mode toggle, or automatic when the camera drops below an
   altitude band? Tie-in with the skybattle command layer (one mode for fleet, one for ground).
2. **What else the mode does.** See-through is the *visibility* half. The *interaction* half —
   ground-unit selection, squad orders, reading sim state — is the larger scope and reuses the
   standalone `WorldPicker` / battle-HUD machinery over the bridge sim.
3. **Reveal-disk tuning.** Radius, falloff curve, min alpha, lerp rate — settle by playtest off
   the probe.
4. **Does the player also want a fleet-side inverse?** (Fade *ground* clutter when commanding
   the fleet.) Probably symmetric; out of scope until the skybattle layer is real.

## Pointers

- [`../overview.md`](../overview.md) — facts 6 (`setExtraAlphaMult`), 8–9 (free cam, input
  override), the 2D reality check.
- [`skybattle-fleet-control.md`](skybattle-fleet-control.md) — the fleet-side command mode this
  pairs with (one mode above, one below).
- `combathybrid/host/SpectatorCanvasPlugin` — the camera/input pattern the probe mirrors;
  `CarrierDescentPlugin` — the in-combat-key precedent (`L`).
- `combathybrid/bridge/SimProxyMirror` — `setExtraAlphaMult(0f)` proxy invisibility (the same
  lever, full strength).

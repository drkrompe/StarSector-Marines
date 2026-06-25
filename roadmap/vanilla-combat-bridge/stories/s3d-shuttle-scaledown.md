# S3d — Shuttle scale-down handoff

> Shuttles fly from the fleet layer and "scale down" to land on the surface, dropping off
> ground forces. A presentation transition + a gameplay handoff between the two engines.
> Design refined (API-confirmed); **the takeover/fly-to-handoff phase is now built** (see
> below). Unblocked by S3e (`AirProvider`).

## Built so far — the `setShipAI` takeover (descent step 1: schedule/fly)

The first S3d step — *take over a real vanilla carrier mid-combat and fly it to the handoff
point* — is built and wired into the bridge (`CombatBridgeSession.enterEngine`):

- **`CarrierDescentBrain implements ShipAIPlugin`** (host/) — grip **tier 2**: owns the brain,
  vanilla physics still flies the ship. Each frame it turns toward the target, thrusts only
  inside a heading cone, and bleeds speed while turning / near the target so it arrives instead
  of orbiting. Settles + logs "handoff point reached" within `ARRIVE_RADIUS` once slow. Never
  issues `FIRE` (a ship leaving the fight to land).
- **`CarrierDescentPlugin`** (host/) — press **L** in a SIM_COUPLED bridge battle to pick the
  first live carrier-side ship and `setShipAI` the brain onto it. One takeover per battle. Target
  = the live ground-band centroid via the new shared `GroundBattleConfig.targetableCentroid(...)`
  (the same band notion `CarrierEngagementPlugin` steers the fleet toward).

This de-risks the load-bearing tier-2 question — *can a custom `ShipAIPlugin` reliably steer a
live vanilla ship to a chosen point, against the admiral?* Playtest verdict pending (press L,
watch the carrier peel off and settle over the band). **Still to build:** the `removeEntity` →
owned-sprite scale-down → `sim.deliverSquad` swap at the handoff threshold, and the remove→add
resurrection probe below.

**Critique pass (`996ce08` → fix in follow-up):** one real bug caught + fixed — the heading-error
cone used `Math.abs(normalizeAngle(...))`, but `Misc.normalizeAngle` returns `[0,360)` (not
±180), so a small clockwise error read as ~350° and the ship *braked* instead of thrusting on the
CW half of the cone. Now uses `Misc.getAngleDiff` (true `[0,180]`) and `Misc.turnTowardsFacingV2`
(vanilla's overshoot-damped turn) — fixes the asymmetry and the turn-oscillation. **Left as
playtest watch-items (convergence/robustness, fine for a `@DebugOnly` probe):**
- *Orbit/stall at the boundary* — steering is bang-bang ACCEL/DECEL with no closing-velocity gate;
  a fast straight approach could overshoot the 400u `ARRIVE_RADIUS` and loop. If the playtest shows
  it, gate ACCELERATE on `velocity·toTarget` (brake when closing too fast).
- *Stale target* — the band centroid is snapshotted at takeover; it drifts as structures die. Fine
  for a seconds-long descent; recompute on a cadence if the landing point matters.
- *Admiral co-existence* — the parked `CarrierEngagementPlugin` still issues an ASSAULT to the
  taken-over carrier. `setShipAI` should win (the brain issues `giveCommand` directly and ignores
  assignments), but confirm no tug-of-war; if there is, clear that ship's assignment on takeover.

## Goal

A real fleet ship peels off, descends toward the surface, visibly shrinks ("scale down" to
fake altitude/landing), rotates to a cardinal grid heading, and on touchdown the troops
appear as a squad in the ground `BattleSimulation`. The reverse plays for takeoff/extraction.

## Why this exists

The diegetic bridge between the two scales — how player ground forces *get* into the ground
battle the rest of S3 renders and couples. The most expressive moment of fleet⇄ground interaction.

## Approach — representation handoff (the agreed design)

The descending shuttle cannot be a vanilla ship we shrink in place: **there is no ship-level
render-scale in the combat API** (no `setRenderScale`/`setScale` on `ShipAPI`/`FighterWingAPI`;
`getSpriteAPI()` returns a fresh per-call wrapper and scaling it desyncs weapons/engines/shield/
bounds). We only get free runtime scale over sprites *we* draw (`SpriteAPI.setSize` +
`renderAtCenter`/`renderWithCorners` in a `CombatLayeredRenderingPlugin`) — which is exactly why
we can scale our own shuttles/Valks but not a live vanilla ship. So the descent is **our owned
sprite**, with a clean swap at the handoff threshold:

1. **Schedule** — pick a fleet ship; fly it (its own AI / a move order) to the handoff point.
2. **Remove** — `engine.removeEntity(ship)`. This *is* the inert/invulnerable mechanism: out of
   the vanilla sim means no collision, no AI, no damage, no draw. Hold the `ShipAPI` ref + snapshot
   **only the restore transform** (`getLocation`, `getFacing`).
3. **Animate (ours)** — draw our sprite (borrowing the hull texture, like sim shuttles already do):
   swoop down, `SpriteAPI.setSize` shrink toward ground-read, rotate to a cardinal grid heading.
   At touchdown → `sim.deliverSquad(cell, loadout)` (the EXTERNAL marine-delivery entry, S3d's to add).
4. **Restore (takeoff/return)** — our sprite climbs out; `engine.addEntity(ship)` puts the **same
   instance** back (all combat state preserved — same object), `setFacing(...)` to the chosen heading.

**Nothing can hurt the shuttle mid-animation, for free, two ways over:** the real ship is gone from
the vanilla sim, and our fake shuttle was never a damageable entity in *either* engine (the sim
gives shuttles no HP today — shuttles can't be shot down yet).

## State ownership

The whole handoff lives in `combathybrid`, **outside `BattleSimulation`** — exactly the
`AirProvider.EXTERNAL` contract (the host owns the air). The sim learns of the drop only via
`deliverSquad`. A host-side `EveryFrameCombatPlugin` (the external air provider) owns the in-flight
list and ticks the animation:

```
final class ShipHandoff {           // shim-owned; sim never sees it
    final ShipAPI ship;             // held ref — the SAME instance we re-add
    final Vector2f savedLoc;        // restore transform only (not ship internals;
    final float    savedFacing;     //   addEntity preserves HP/flux/ammo/CR/officer)
    // animation state: phase, t, target landing cell, chosen cardinal facing
}
```

`deliverSquad(cellX, cellY, MarineLoadout[])` on the sim is the inverse of the internal shuttle
deboard; it asserts `AirProvider.EXTERNAL`. (The takeoff/extraction inverse — "board a squad, remove
it from the sim" — can come later; landing is the first cut.)

## Load-bearing unknown — de-risk first (S0–S2 spirit probe)

Everything else is proven (we own the scale/rotate draw; `deliverSquad` is a small sim addition).
The one thing the API can't confirm on its own: **does `engine.addEntity` cleanly resurrect a
`removeEntity`'d `ShipAPI` with AI/fleet wiring intact?** First probe: remove a ship, wait a beat,
`addEntity` it back, confirm it resumes flying + fighting normally (re-poke `setShipAI`/loc/facing/
velocity if needed). If flaky, the fallback is hide-in-place (`setCollisionClass(NONE)` +
`setControlsLocked(true)` + park velocity), but removal is the clean first choice.

## Out of scope

- Full LOD pipeline for everything (just enough scale to read the landing).
- Follow-cam vs. stay-at-fleet during descent — UX decision at build (note both). Camera zoom is
  *not* a substitute for the per-sprite shrink: zoom scales the whole scene, so it only works if
  the player follows the shuttle down; a shuttle peeling off while the fight continues above needs
  the owned-sprite shrink.
- Combat consequences of the shuttle being shot down en route (ties to giving shuttles HP + the
  proxy/area-damage model — later).

## Fragile-surface additions (for `architecture.md` at build time)

New `combathybrid`-only calls beyond today's enumerated set: `addEntity` (the re-add), `setFacing`.
Already listed: `removeEntity`, `getLocation`/`getVelocity().set`. Keep the list honest when this lands.

## Acceptance

A real fleet ship peels off, descends, visibly scales down, and on touchdown the troops exist in the
ground sim as a squad; `addEntity` restores the same ship for takeoff. Verdict recorded: did
remove→add resurrect cleanly, and follow-cam vs. stay-at-fleet.

# S1 — Wall-clamp probe — ⏸ SHELVED (2026-06)

> **Decision:** not pursuing for now. The product direction landed on **fleet-above /
> ground-battle-below with cross-interaction** (the proxy / air-to-ground framing, S2),
> not putting walls into the vanilla combat plane (Direction A). The whole point of S1
> was to gate Direction A on feel — and we're choosing not to walk down Direction A's
> long tail (projectiles/beams/AI all ignore walls until individually patched). Walls
> stay in our headless sim, where they already work.
>
> Kept (not deleted) because the post-physics `getLocation()`/`getVelocity()` clamp is
> still a real, verified technique (overview fact 2) if a future feature ever wants a
> single ship to respect a barrier in the vanilla plane. Revisit only if that need
> appears.

---

> Throwaway probe for Direction A (walls into vanilla combat). Answers one UX
> question: does a post-physics position-clamp *feel* like a solid wall?

## Goal

A minimal `EveryFrameCombatPlugin` that turns a hardcoded patch of "wall tiles"
into something a vanilla ship physically can't move through — by correcting the
ship's position *after* the engine integrates its movement each frame. Fly a
ship into the wall in a test mission and judge the feel.

## Why this exists

Direction A's entire viability rests on the verified fact that
`CombatEntityAPI.getLocation()` / `getVelocity()` return the **live mutable**
`Vector2f` (overview § Verified facts #2). The wall becomes a constraint applied
downstream of whoever moved the ship — so it constrains player-piloted *and*
AI-driven ships uniformly, with no per-ship AI work. The unknown is purely
*feel*: a naive clamp reads as sticky/mushy; a velocity-slide reads as a wall.

## Scope

**In:**
- Register an `EveryFrameCombatPlugin` (a test mission or a debug toggle is fine —
  this is not shipping).
- A hardcoded set of impassable cells (a box) in a fixed world-coord region.
- In `advance()`, for each `ShipAPI` overlapping a wall cell: push
  `getLocation()` out to the nearest non-penetrating position, and zero the
  into-wall component of `getVelocity()` (slide along the wall tangent).
- Draw the wall tiles in `renderInWorldCoords` so you can see what you're hitting.

**Out:**
- Real map authoring / coordinate bridge to the sim grid (hardcode it).
- Projectiles, beams, ship systems, AI wall-awareness — all the long tail.
- Any coupling to `BattleSimulation`. This probe is self-contained on the
  vanilla side; it does *not* read the real sim grid yet.

## Design notes

- **Clamp vs slide.** Pure clamp (snap position out, leave velocity) feels
  sticky and can fight thrust every frame. Project the velocity onto the wall
  tangent (kill only the normal component) for a slide — that's the difference
  between "wall" and "glue."
- **Corners & tunneling.** Slow mechs won't tunnel; if a fast ship skips a thin
  wall, a swept test is the fix, but don't build it for the probe — note it.
- **Timing.** `advance()` runs after the frame's integration, so reading then
  correcting position is the right order. No before/after-physics split exists.

## Dependencies

- None on our code. Pure vanilla-side `EveryFrameCombatPlugin`. Lives in the new
  `com.dillon.starsectormarines.combathybrid` package (overview § Code structure).

## Acceptance

In a test mission, a ship driven into the hardcoded wall box stops at the
boundary instead of passing through, can slide along it rather than sticking,
and the wall tiles are visibly drawn. Verdict recorded in `next-session.md`:
**does it feel like a wall?** That answer gates whether Direction A is worth any
further investment.

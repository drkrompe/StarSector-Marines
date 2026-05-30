# S2 — Proxy-target probe

> The recommended first probe. Answers: does vanilla carrier/fighter AI engage a
> sim-slaved proxy entity sensibly? If yes, "carrier reacts to ground entities"
> is mostly plumbing.

## Goal

Inject one invisible vanilla `ShipAPI` proxy (owner = enemy side) at a fixed
point, slave its position/velocity from a fixed value (standing in for a sim
turret), drain its hitpoints to a log line, and observe whether a player
carrier's fighters launch and strafe it — with zero targeting AI written by us.

## Why this exists

This is the proxy/avatar pattern (overview § The tractable third framing) at its
smallest. It de-risks the *whole* cross-engine bridge while dodging the terrain
long-tail entirely: we're not making vanilla respect walls, we're injecting a
**targetable avatar** into vanilla's targeting graph and letting native AI do the
rest. If carrier/fighter AI engages it, the compelling feature ("carriers react
to our turrets/squads") is reachable; if it behaves oddly against a stationary
zero-speed target, we learn that cheaply now.

## Scope

**In:**
- `engine.getFleetManager(owner).spawnShipOrWing(specId, location, facing)` to
  inject a proxy, owner = the side hostile to the player carrier.
- `setExtraAlphaMult(0f)` so the proxy is invisible (no real sprite competes with
  our future renderer).
- Each frame: `getLocation().set(fixedX, fixedY)`, `getVelocity().set(0,0)`;
  no-op `ShipAIPlugin` (or an engineless spec); high mass.
- Collision class `SHIP` (or `FIGHTER`) so it is hittable by weapons (overview §
  Verified facts #7 — there is no shootable-but-not-bumpable class).
- Drain `getHitpoints()` delta each frame to a log line; despawn via
  `engine.removeEntity(...)` when it dies.

**Out:**
- Real sim coupling — the "turret" is a hardcoded point + standalone HP counter,
  not a `BattleSimulation` entity yet. (Wiring the drain into the sim's
  external-damage path is the *next* story, once this proves AI engages.)
- Return fire from the proxy, multiple proxies, squad aggregation, our renderer
  drawing the real sprite — all follow once engagement is confirmed.
- Altitude/airspace banding (carrier stays back) — note behaviour, don't build.

## Design notes

- **Spec choice.** Use a small existing hull spec for the proxy first to avoid
  authoring a custom spec; its sprite is hidden anyway. Watch for vanilla AI
  quirks targeting a zero-speed "ship."
- **IFF.** owner = 1 (enemy) vs the player carrier owner = 0 is what makes
  vanilla AI engage across the divide — confirm fighters actually pick it.
- **Self-correcting bumps.** Because position is re-`set()` every frame, a
  fighter or hull bump moves the proxy zero net distance — verify this holds
  under fire (impulse vs clamp ordering).

## Dependencies

- None on our sim for the probe itself. Lives in
  `com.dillon.starsectormarines.combathybrid` (overview § Code structure).
- Next story (not this one) wires the HP drain into `BattleSimulation`'s
  external-damage path (overview § Open questions #2).

## Acceptance

In a test mission with a player carrier, the invisible enemy proxy is launched
against by the carrier's fighters (or fired on by its weapons); its logged HP
drops under fire and it despawns at zero. Verdict in `next-session.md`: **does
native AI engage a slaved proxy well enough to build on?** A yes greenlights the
sim-coupled "carrier reacts to ground entities" feature.

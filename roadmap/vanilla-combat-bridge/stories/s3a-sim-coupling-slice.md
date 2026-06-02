# S3a — Sim coupling slice

> The first **real** two-engine coupling. S2 proved vanilla AI engages a proxy; S3a
> replaces the proxy's throwaway HP counter with a live sim `Unit`, so a vanilla
> fighter actually kills a ground entity *in our sim*. Proves Decision 1 (event-
> translated coupling, [`../architecture.md`](../architecture.md)) end-to-end with one
> entity. Throwaway dev scaffolding.

## Goal

Back the S2 proxy with a single real `BattleSimulation` `Unit` (a turret/AA emplacement),
and close both event loops through the adapter:

- **Vanilla → sim:** the proxy's per-frame damage delta → `BattleSimulation.applyExternalDamage(unit, delta)`.
- **Sim → vanilla:** subscribe to the sim's death event for that unit → `engine.removeEntity(proxy)`.
- **Position:** each frame, `proxy.getLocation().set(unit.cell × WORLD_UNITS_PER_CELL)`.

A vanilla carrier's fighters strafe the proxy; the *sim* turret's HP drops; when the sim
says it died, the proxy despawns. No HP mirrored — the sim owns it; the proxy's vanilla
HP is a large constant.

## Why this exists

It's the smallest thing that exercises the coupling decision with a live sim entity. If
the damesage delta → `applyExternalDamage` → death-event → despawn round-trip is clean
and lag-free, every later piece (many units, area damage, rendering) is plumbing. If
the per-frame delta read is noisy or the death event races the despawn, we learn it here
with one entity instead of a fleet.

## Scope

**In:**
- Reuse the `PROXY_TARGET` host (spectator canvas + AI carriers).
- Stand up a **minimal** `BattleSimulation` with one targetable `Unit` (a turret). No
  full mission gen — just enough sim to own one unit's HP + emit its death event.
- Adapter wiring in `combathybrid`:
  - read proxy damage delta (HP-poll on the large-constant-HP proxy, or a vanilla damage
    listener) → `applyExternalDamage`.
  - subscribe the sim death mailbox ([[battle_death_dispatcher_design]]) → `removeEntity`.
  - drive `proxy.getLocation()` from `unit` cell each frame.
- Log the round-trip: vanilla damage in, sim HP after, death event out.

**Out:**
- Rendering the unit/terrain (that's S3b — the marker crosshair is fine for S3a).
- Multiple units / squads / one-proxy-per-entity fan-out.
- Area damage / strafing splash to infantry (cross-cutting model, after S3a).
- Any change to `battle/` that imports `combathybrid` — the sim→adapter channel is a
  sim-side listener interface the adapter subscribes to.

## Design notes

- **Where the sim lives.** The probe owns a `BattleSimulation` instance on the combat
  side (in `combathybrid`, ticked from the `EveryFrameCombatPlugin.advance`). It reads
  sim state and pushes damage; it must not make `battle/` aware of the adapter.
- **Damage delta vs listener.** Start with HP-poll (`lastHp - hp`) for simplicity; if
  it's noisy under rapid fire, switch to a vanilla damage listener. Note which felt right.
- **Death race.** The despawn must come *from* the sim death event, not from the
  adapter independently deciding — that's the whole point of sim authority. Verify the
  proxy doesn't linger a frame or vanish early.
- **Tick coupling.** The sim ticks at its own rate; vanilla advances per frame. Decide
  whether to tick the sim once per combat frame or on a fixed step — note the choice.

## Dependencies

- S2 (shipped) — proxy host, spawn, marker.
- `battle/` — `BattleSimulation.applyExternalDamage` (exists), the death mailbox, a way
  to construct a one-unit sim. Read-only/`BattleControl`-style; no reverse import.

## Acceptance

Ctrl+Shift+ (new mode): a carrier's fighters strafe the proxy; the **sim** turret's HP
(logged) falls; at zero the sim emits death and the proxy despawns the same beat. Verdict:
**is the event-translated round-trip clean enough to fan out to many units?** A yes
green-lights S3b (render it) and the one-proxy-per-entity model.

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

Ctrl+Shift+K (new `SIM_COUPLED` mode): a carrier's fighters strafe the proxy; the **sim**
turret's HP (logged) falls; at zero the sim emits death and the proxy despawns the same beat.
Verdict: **is the event-translated round-trip clean enough to fan out to many units?** A yes
green-lights S3b (render it) and the one-proxy-per-entity model.

## Implementation (built — awaiting playtest)

Launch with **Ctrl+Shift+K** on the campaign map. Shipped as **one sim, many proxies** from
the start (the single-proxy first cut was generalized before playtest — see "Round-trip
verdict" below). Pieces in `combathybrid`:

- **`GroundSimBridge`** (`EveryFrameCombatPlugin`) — references an externally-owned
  `BattleSimulation` and mirrors a passed-in list of targetable `Unit`s, one invisible proxy
  each. Per frame: push every proxy's damage delta into the sim, tick the sim **once**, then
  despawn any proxy whose unit the sim reported dead. It does **not** construct the sim.
- **`NeverEndObjective`** — a `!complete && !failed` DEFENDER objective. Suppresses the
  eliminate-each-other backstop so an all-DEFENDER sim doesn't auto-complete on tick 1 (a
  completed sim early-returns from `advance()` and would strand the death events).
- **`BattleSimulation.subscribeDeath(Consumer<DeathEvent>)`** — new public seam forwarding to
  the death dispatcher; the one-way sim→adapter channel (sim never imports the adapter).
- The `SIM_COUPLED` host (`S0BattleCreationPlugin.setupSimCoupled`) builds the sim + a short
  row of mixed-kind turrets **outside** the combat plugin, then hands it to the bridge.

Decisions on the design-notes open questions:

- **HP-poll, not a damage listener.** Delta is `maxHp - hp`; each proxy's vanilla HP is reset
  to full every frame (`setHitpoints(max)`), so it's a pure damage *sensor* and vanilla never
  owns the kill — the cleanest expression of "proxy HP is a throwaway hittable surface."
  Revisit only if rapid fire makes the per-frame delta noisy.
- **Scale.** `SIM_DAMAGE_SCALE = 0.02` (retuned down from 0.1 after the first run one-shot the
  turret — a ~1300 fighter salvo scales to ~26 vs VULCAN's 50 HP, so a turret attrits over
  several passes). Placeholder for the real cross-scale convention (architecture, S3c).
- **Tick coupling.** Sim ticked **once per combat frame** with the real `dt`; `BattleSimulation`
  fixes it to 30Hz internally. Despawn can trail the killing blow by ≤1 sim tick (~33ms) since
  death fans out on the next mailbox drain — confirmed invisible in the first run (death event
  and despawn the same millisecond). Force-ticking per frame is the lever if it ever reads laggy.
- **Death race.** Despawn is driven *only* by the sim death event, never by the adapter — sim
  authority. Damage fed after a unit dies is a safe no-op (`applyExternalDamage` guards `!isAlive`).
- **`init` idempotency.** The engine calls combat-plugin `init` more than once (the single-proxy
  cut logged "proxy up" twice and rebuilt its sim). The bridge guards with an `initialized` flag
  and the sim lives outside the plugin, so neither proxies nor the sim can be doubled.

## Round-trip verdict — PASS

First run (single VULCAN, scale 0.1) logged the clean round-trip:
`vanilla dmg 1304 -> sim dmg 130.5 (hp 0.0)` → `SIM death event` → `despawning proxy`, all on the
same beat. The **sim owned the kill** (no "destroyed by vanilla AI"). That green-lit the fan-out:
generalized to `GroundSimBridge` (one sim, N proxies) + scale retune, before a fuller playtest.
Remaining playtest check: many proxies attriting and despawning independently over one sim.

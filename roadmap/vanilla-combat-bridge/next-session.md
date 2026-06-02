# Vanilla Combat Bridge — next session

## State of play

**S0, S0b, and S2 are all shipped and playtested.** The cross-engine bridge is proven
end to end:

- **S0** — launch a vanilla `CombatEngineAPI` battle from the campaign with a chosen
  roster; the mod owns when it ends (Ctrl+Shift+B, F10).
- **S0b** — run it as a blank spectator canvas: no deploy picker, free camera (real
  zoom), below-ships backdrop, starved HUD, clean exit with the player fleet restored
  (Ctrl+Shift+N).
- **S2** — vanilla carrier/fighter AI strafes a sim-slaved invisible proxy with **zero
  targeting code from us** (Ctrl+Shift+J). The whole bet pays off.

**S1** shelved (Direction A / walls-in-the-plane is not the product direction).

Sealed: `complete/{s0-battle-bootstrap, s0b-spectator-canvas, s2-proxy-target-probe}.md`.

## S3 phase planned — read `architecture.md` first

The post-S2 architecture is written down in [`architecture.md`](architecture.md): the
**event-translated coupling decision** (sim-authoritative; proxy HP is a throwaway
hittable surface; vanilla→sim = damage delta via `applyExternalDamage`; sim→vanilla =
pub/sub death/spawn mailbox; no state mirroring), the **proxy-as-aggregation /
targetability tiers** (infantry never directly targetable; structures/defenses get
proxies; area damage for strafes + main-battery fire), and the **spatial fork** (hard
ground-band AI-gating vs loose convention — decide at S3c).

Decomposition (stories written):
- **S3a — sim coupling slice** *(BUILT — awaiting playtest, Ctrl+Shift+K).* One real
  `MapTurret` behind the S2 proxy; vanilla damage → `applyExternalDamage` (scaled 0.1×);
  sim death event (`subscribeDeath`) despawns the proxy. See the story's "Implementation"
  section for the decisions (HP-as-sensor, NeverEndObjective, tick-with-real-dt, ≤1-tick
  despawn latency). Verdict pending: is the round-trip clean enough to fan out to many units?
- **S3b — cityscape backdrop.** Ground renderer → below-ships layer.
- **S3c — airspace banding / AI gating.** The hard de-risk; resolve the spatial fork.
- **S3d — shuttle scale-down handoff.** Diegetic bridge between the two scales.

### S3a probe pieces (combathybrid)
- `SimCoupledProxyPlugin` — owns the one-unit sim, ticks it, closes both event loops.
- `NeverEndObjective` — keeps a one-DEFENDER sim from auto-completing (else `advance()`
  early-returns and the death event never drains).
- `BattleSimulation.subscribeDeath(Consumer<DeathEvent>)` — new one-way sim→adapter seam.
- Mode `SIM_COUPLED` on `S0BattleProbe`; hotkey Ctrl+Shift+K.

Overview open question #2 is answered: the external-damage path is `applyExternalDamage`.

## Reusable combathybrid pieces

- `CombatHybridCampaignPlugin` — tag-armed `BattleCreationPlugin` selection (`PROBE_FLAG`).
- `S0BattleProbe` — launch + `Mode` {BASIC, SPECTATOR_CANVAS, PROXY_TARGET} + `PlayerFleetStash`.
- `SpectatorCanvasPlugin` — free cam (`viewport.set()`-based), HUD starve, fleet restore.
- `CanvasBackdropRenderer` — below-ships render layer (`SolidQuadBatch` + `GlStateBracket`).
- `ProxyTargetPlugin` — the proxy/avatar pattern (pin + invisible + HP drain + marker).
- Scale: `WORLD_UNITS_PER_CELL = 50`. Real variant ids validated before spawn.

## Gotchas (also in the `startbattle_plugin_pick_deferred` memory + overview facts)

- `startBattle` picks the `BattleCreationPlugin` a frame late → gate by opponent-fleet
  memory tag, not an armed flag.
- `startBattle` deploys from the REAL player fleet → stash it for spectator; restore
  ~0.5s INTO combat, not on exit (else game-over).
- `setViewMult` is inert under `setExternalControl` → drive `viewport.set(llx,lly,w,h)`.
- `spawnShipOrWing` resolves variant ids eagerly (validate); `createFleetMember` is lazy.

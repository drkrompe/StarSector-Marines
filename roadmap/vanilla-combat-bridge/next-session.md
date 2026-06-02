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
- **S3a — sim coupling slice** *(BUILT, one round-trip confirmed — fuller playtest batched
  for later, Ctrl+Shift+K).* Round-trip PASSED on the first single-proxy run (vanilla dmg →
  `applyExternalDamage` → sim death event → despawn, same beat, sim owned the kill). Then
  **generalized to one-sim/many-proxies** and the damage scale retuned 0.1 → 0.02 so turrets
  attrit over several passes. The "individual simulation setup" concern is retired: the sim is
  built once, outside the plugin; the bridge only references it.
- **S3b — cityscape backdrop** *(BUILT — awaiting playtest, Ctrl+Shift+K).* The real
  ground scene (terrain + structures) now renders under the ships. **Key finding: the
  render-target seam is the camera, already present** — a world-configured `BattleCamera`
  (world-unit viewport) makes the existing `BattleRenderer` draw in combat world coords;
  no `SceneCamera` interface / no codebase sweep. `GroundSceneBackdrop` + a
  `renderWorld(rc, EnumSet)` subset overload; probe sim is now a real `BspCityGenerator`
  cityscape shared by bridge + backdrop. Deferred: FBO decals/lighting + screen-coupled
  fog/highlights/units/FX.
- **S3c — airspace banding / AI gating.** The hard de-risk; resolve the spatial fork.
- **S3d — shuttle scale-down handoff.** Diegetic bridge between the two scales.

### S3a + S3b probe pieces (combathybrid)
- `GroundSceneBackdrop` — below-ships plugin; world-configured `BattleCamera` + reused
  `BattleRenderer`; draws GROUND/DOODADS/ROOFS of the bridge's sim. Replaces the grid plate.
- `GroundSimBridge` — references one externally-owned sim, mirrors N targetable units as
  proxies, ticks the sim once/frame, despawns proxies on sim death. Idempotent `init`.
  (Supersedes the single-proxy `SimCoupledProxyPlugin`, now deleted.)
- `NeverEndObjective` — keeps an all-DEFENDER sim from auto-completing (else `advance()`
  early-returns and death events never drain).
- `BattleSimulation.subscribeDeath(Consumer<DeathEvent>)` — new one-way sim→adapter seam.
- `S0BattleCreationPlugin.setupSimCoupled` builds the sim + a row of mixed-kind turrets
  *outside* the plugin. Mode `SIM_COUPLED` on `S0BattleProbe`; hotkey Ctrl+Shift+K.

**Next build candidate (S3a is functionally done):** S3b cityscape backdrop — draw the real
ground scene under the ships so the proxy markers sit on the actual map.

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

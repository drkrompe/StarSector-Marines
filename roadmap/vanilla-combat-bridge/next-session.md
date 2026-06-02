# Vanilla Combat Bridge — next session

## State of play

**S0 + S0b are shipped and playtested.** A vanilla `CombatEngineAPI` battle can be
launched from the campaign with a roster we choose, the mod owns when it ends, and the
whole thing can run as a blank, sim-driven **spectator canvas** (no deploy picker, free
camera, below-ships backdrop, starved HUD + our overlay, clean exit with the player
fleet intact). The "vanilla combat as a host for our ground sim" thesis is proven.

Sealed: `complete/s0-battle-bootstrap.md`, `complete/s0b-spectator-canvas.md`.
Triggers (dev, gated by `DevConfig.S0_COMBAT_PROBE`): **Ctrl+Shift+B** = basic battle,
**Ctrl+Shift+N** = spectator canvas, **F10** in combat = end.

## Hard-won facts (all in overview §"round 2" + the `startbattle_plugin_pick_deferred` memory)

- `startBattle` resolves the `BattleCreationPlugin` a frame *later* → gate selection by
  an **opponent-fleet memory tag** (`PROBE_FLAG`), not a transient armed flag.
- `startBattle` deploys from the **real** player fleet, ignoring the context fleet → for
  a spectator, **stash** the real fleet (`PlayerFleetStash`) and re-attach it **~0.5s
  into combat** (NOT on exit — restoring after combat-end makes the resolution read an
  empty fleet and declare a game-over).
- `spawnShipOrWing` resolves variant ids **eagerly** (validate first); `createFleetMember`
  is **lazy**. Real ids: `vigilance_Standard`, `tempest_Attack`, `brawler_Assault`, …
- Can't draw over a *populated* command bar (no above-UI combat hook) → starve the HUD
  (spectator + 0 CP) instead.
- Working scale: `WORLD_UNITS_PER_CELL = 50` (size gut-check).

## S2 — proxy-target probe: BUILT, awaiting playtest

**Ctrl+Shift+J.** Rides the spectator canvas; adds `Mode.PROXY_TARGET`,
`S0BattleCreationPlugin.setupProxyTarget` (AI carriers `heron_Strike`+`drover_Strike` on
the player side, one invisible owner-1 proxy `vigilance_Standard` on the enemy side), and
`ProxyTargetPlugin` (pins position/velocity, holds fire, `extraAlphaMult=0`, logs HP
delta, despawns at 0, draws a red crosshair marker at the proxy). Compiles clean.

S1 is shelved (Direction A not the product direction). Camera fix landed (`set()`-based,
real zoom). Decision: **fleet-above / ground-below with cross-interaction** is the path.

### Playtest checklist (Ctrl+Shift+J)
- [ ] Carriers launch fighters that fly to + strafe the crosshair marker (the invisible
      proxy) — native AI, zero targeting code from us.
- [ ] `S2 proxy: HP …` ticks down under fire; `destroyed by vanilla AI` at zero.
- [ ] Proxy stays pinned under fire (no drift/knockback).
- [ ] Clean F10 exit, fleet restored (S0b carry-over).
- [ ] **Verdict:** does native AI engage the slaved proxy well enough to build on?

## Immediate next-up

- **Playtest S2.** If native AI engages cleanly → the cross-engine targeting bet holds.
- **Next story after S2's verdict:** wire the proxy HP drain into `BattleSimulation`'s
  external-damage path (overview open question #2) — one proxy per squad/turret, real sim
  HP. Then **S3** (overview) — inject the actual ground sim as a fleet of proxies under
  the vanilla fleet fight (the real two-engine co-existence; user flagged this as the
  direction). Scope S3 for real once S2 + the HP-drain wiring are proven.

## Reusable combathybrid pieces (for S3 / the HP-drain story)

- `CombatHybridCampaignPlugin` — tag-armed `BattleCreationPlugin` selection.
- `S0BattleProbe` — launch + `PlayerFleetStash` (spectator without game-over).
- `SpectatorCanvasPlugin` — free cam (`set()`-based), HUD starve, fleet restore.
- `CanvasBackdropRenderer` — below-ships render layer.
- `ProxyTargetPlugin` — the proxy/avatar pattern (pin + invisible + HP drain).

## Cleanup debt (low priority)

- The probes are all `@DebugOnly` + dev-gated; no production wiring yet. Fine to leave.
- `CanvasBackdropRenderer` / `SpectatorCanvasPlugin` GL is minimal (solid quads). When a
  real sim plate is rendered, reuse the `render2d` batches under `GlStateBracket`.

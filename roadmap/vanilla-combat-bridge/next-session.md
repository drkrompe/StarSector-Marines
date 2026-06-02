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

## Next: the first real two-engine coupling (toward S3)

Replace the S2 proxy's standalone HP counter with a real sim `Unit`:

- **The hook exists:** `BattleSimulation.applyExternalDamage(Unit target, float damage)`
  (`BattleSimulation.java:1087`) — built for flyby strafing, routes through
  `DamageResolver` with morale + fallback short-circuited, runs cover/HP-write/death
  cascade normally. Drain the proxy's per-frame HP delta into it.
- **Shape:** one proxy per sim squad/turret. Proxy position ← unit cell (× `WORLD_UNITS_PER_CELL`
  = 50). Proxy HP delta → `applyExternalDamage`. Unit death → despawn proxy; proxy gone
  → stop driving. This is the first slice of **S3** (overview): the headless `battle/`
  sim driving a *fleet* of proxies under the vanilla fleet fight, with the real ground
  scene rendered on the below-ships layer and full above⇄below cross-interaction.
- **Open question #2** (overview) is now answered: the external-damage path is
  `applyExternalDamage`.

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

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

## Immediate next-up

- **S2 — proxy-target probe** (`stories/s2-proxy-target-probe.md`). Now has a proven
  combat host: spawn an invisible owner-1 `ShipAPI` proxy, slave its position, drain its
  HP, and confirm a player carrier's fighters engage it. Then wire the HP drain into the
  sim's external-damage path (open question #2).
- The combathybrid package now has reusable pieces S2 can lean on: the armed-by-tag
  `CombatHybridCampaignPlugin`, the spectator/camera plugin, and `PlayerFleetStash`.

## Cleanup debt (low priority)

- The probes are all `@DebugOnly` + dev-gated; no production wiring yet. Fine to leave.
- `CanvasBackdropRenderer` / `SpectatorCanvasPlugin` GL is minimal (solid quads). When a
  real sim plate is rendered, reuse the `render2d` batches under `GlStateBracket`.

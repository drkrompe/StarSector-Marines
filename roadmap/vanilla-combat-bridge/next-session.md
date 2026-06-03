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

Decomposition:
- **S3a — sim coupling slice** ✅ **SHIPPED + playtested** (`905d8e9` → `dd06104`).
  Event-translated round-trip confirmed (vanilla dmg → `applyExternalDamage` → sim death →
  despawn, same beat, sim owns the kill); generalized to one-sim/many-proxies. Sealed:
  `complete/s3a-sim-coupling-slice.md`.
- **S3b — cityscape backdrop** ✅ **SHIPPED + playtested** (`347160a`). Real terrain +
  structures render under the ships. **Render-target seam = the camera, already present** —
  a world-configured `BattleCamera` makes the existing `BattleRenderer` draw in combat world
  coords; no `SceneCamera` interface / no sweep. Sealed: `complete/s3b-cityscape-backdrop.md`.
- **S3c — airspace banding / AI gating.** The hard de-risk; resolve the spatial fork.
- **S3d — shuttle scale-down handoff.** Diegetic bridge between the two scales.

### Scale + real-map pass (done after S3b playtest)
- `WORLD_UNITS_PER_CELL` lowered **50 → 20** (ground cells read too large vs ships at 50;
  at 20 ships tower over tiles). Backdrop + proxies both derive from it, so they stay
  locked. Still a visual knob — re-dial freely if scale feels off.
- The `SIM_COUPLED` probe now loads the **real Conquest map at LARGE (240×160)** instead of
  a generic MEDIUM city: `BspCityGenerator.generate(w,h,seed, axis, NEUTRAL)` (non-null axis
  → `conquestRecipe` with biome bands + `DefensePostStamper`), `setBuildings`/`setDefensePosts`/
  `setTacticalMap`/doodads, defense-post turrets spawned + mirrored as proxies (fighters
  strafe the planet's actual defenses). `canvasGrid()` returns LARGE for SIM_COUPLED only.
  Map only — no marines/defenders/shuttles/reinforcement (that's the battle, not the map).

### S3a + S3b probe pieces (combathybrid)
- `GroundSceneBackdrop` — below-ships plugin; world-configured `BattleCamera` + reused
  `BattleRenderer`; draws GROUND/DOODADS/ROOFS of the bridge's sim. Replaces the grid plate.
- `SimProxyMirror` — references one externally-owned sim, mirrors N targetable units as
  proxies, ticks the sim once/frame, despawns proxies on sim death. Idempotent `init`.
  (Supersedes the single-proxy `SimCoupledProxyPlugin`, now deleted.)
- `NeverEndObjective` — keeps an all-DEFENDER sim from auto-completing (else `advance()`
  early-returns and death events never drain).
- `BattleSimulation.subscribeDeath(Consumer<DeathEvent>)` — new one-way sim→adapter seam.
- `S0BattleCreationPlugin.setupSimCoupled` builds the real Conquest map (LARGE) into a sim
  *outside* the plugin. Mode `SIM_COUPLED` on `S0BattleProbe`; hotkey Ctrl+Shift+K.

## S3e — "build a BattleSimulation, then choose a host" ✅ SHIPPED

The two-entrypoint convergence is done (`d152441` → `e171d00` → `03dd62f`; full test suite
green). Sealed: `complete/s3e-build-map-host-seam.md`.

- **`BattleSetup.buildMap(map, vehicles, defensePosts) → MapBuild{sim, structures}`** is the
  shared host-agnostic map layer (construct sim + install tactical map/buildings/posts/
  vehicles/doodads + spawn defense-post structures). All three `createX` factories and the
  bridge's `setupSimCoupled` now build their sim through it — no inline copies left.
  Pre-condition: vehicles + posts already stamped into `map.grid` (zone graph reads
  walkability at construction); `stampVehicles` stays before `spawnDefensePostTurrets` so the
  cover bake is unchanged. Conquest passes `map.defensePosts` (generator-stamped),
  non-conquest passes the `stampNonConquest` list.
- **`AirProvider {INTERNAL, EXTERNAL}` on `BattleSimulation`** (default INTERNAL) is the air
  ownership seam. EXTERNAL skips `airSystem.tick()` and fail-louds `addShuttle`/
  `attachAirTurrets`/`setFlybyRoster` (host owns the air). The bridge sets EXTERNAL; every
  standalone factory + test stays INTERNAL, unchanged.

Both entrypoints (A — standalone `BattleScreen`; B — combat-bridge `GroundSceneBackdrop` +
`SimProxyMirror`, Ctrl+Shift+K) now share: `buildMap`, `BattleSimulation` + the `advance(dt)`
contract, `BattleRenderer` + all `RenderSystem`s (B uses the `renderWorld(rc, EnumSet)`
subset), `BspCityGenerator`, `BattleSprites`, `BattleCamera`.

**Inherited by S3d:** the external marine-delivery entry point — a `deliverSquad(cellX, cellY,
MarineLoadout[])` on the sim that asserts `AirProvider.EXTERNAL`, the inverse of the internal
shuttle deboard. Only the EXTERNAL strafe half exists today (`applyExternalDamage`). S3d's
open "vanilla entity vs sim AirBody during descent?" question is now framed by `AirProvider`.

**Next up:** the ground/ship **scaling pass** (`WORLD_UNITS_PER_CELL`, dial in-game), then
**S3c — airspace banding / AI gating** (watch unmodified ship AI against the ground band
first; only write a `ShipAIPlugin` if it misbehaves), and **S3d** (now unblocked by the seam).

## Extraction thread — productionize the bridge before more render layers (NEW, active)

**Decision (2026-06): the bridge is a committed product mode; extract the durable core now,
then resume the render-layer thread in it.** Full rationale + target shape + slice plan in
[`production-architecture.md`](production-architecture.md). S3f shipped into the spike; S3g–S3j
are **paused** until the extraction lands so they target durable, config-driven code.

Slices (each build-clean + committable):
- **X1 — `GroundBattleConfig` + configurable render layers** ✅ **CODE-COMPLETE** (per-file clean;
  whole-project build red only from a sibling ecs-migration `Crashing→CrashingComponent` move, not
  X1). The hardcoded `SCENE_LAYERS` constant is gone — the bridge's render-layer set now comes from
  `GroundBattleConfig.sceneLayers` (so S3g–S3j become config edits). `SimProxyMirror` reads
  `damageScale`/`proxyVariant` from the config too. `setupSimCoupled` builds the config once.
- **X2 — debug strip + rename `GroundSimBridge` → `SimProxyMirror`** ✅ **DONE.** Per-frame damage
  log + the amber crosshair markers gone (S3f UNITS is the real unit visual); log prefixes
  `S3a:` → `ground-bridge:`; class Javadoc reframed from probe to durable core. Renamed via IntelliJ
  `rename_refactoring` (22 usages, file + symbol). Per-file clean.
- **X3 — `CombatBridgeSession` host object** ✅ **DONE** (full build green). A thin orchestrator
  (not a god class): owns the SIM_COUPLED vanilla-side lifecycle across two phases — `defineBattle`
  (spectator canvas fleets/map + completion + camera policy plugins) and `enterEngine` (detach
  player ship, never-end objective, backdrop + proxy mirror). Every behavior stays in a delegate;
  the session just wires them to one `GroundBattleConfig`. `S0BattleCreationPlugin` now builds the
  config (`buildSimCoupledConfig`) + routes both phases to the session + spawns scenario carriers;
  the SIM_COUPLED mode-branch collapsed. Throwaway S0b/S2 probe branches untouched.
- **X4a — retire the spent S0b/S2 probes** ✅ **DONE** (full build green). Deleted
  `CanvasBackdropRenderer` + `ProxyTargetPlugin`; dropped the `SPECTATOR_CANVAS`/`PROXY_TARGET`
  `Mode` values + their `launch*` methods + the Ctrl+Shift+N / Ctrl+Shift+J hotkeys; collapsed
  `S0BattleCreationPlugin` to just BASIC + SIM_COUPLED (no more `initCanvas`/`setupProxyTarget`/
  `canvas` field/CANVAS_* constants). Their verdicts stay sealed in `complete/`.
- **X4b — package reorg** `bridge/` + `host/` + `probe/` (git-mv + package-infos). Remaining; the
  mechanical part — delegate to a Sonnet subagent.

Then resume S3g–S3j against the config-driven `GroundSceneBackdrop`.

## S3f–S3j — bridge render layers (thread, stories written; PAUSED for the extraction above)

The bridge sink (`GroundSceneBackdrop`) draws only `{GROUND, DOODADS, ROOFS}` today; the
standalone screen draws all 17. This thread brings the rest over **one layer-bucket per
story** — and it's *not* new render code: the seam is the already-shipped
`renderWorld(rc, EnumSet<RenderLayer>)` (S3b). Each story = grow `SCENE_LAYERS` + `ensureX()`
the sheets in `initOnGlThread()` + handle that layer's `RenderContext` inputs.

Decomposition doc: [`render-layers.md`](render-layers.md). Stories:
- **`stories/s3f-units-layer.md`** — `UNITS` (turret/hub bodies, footprints, dead poses, live
  infantry, HP bars). **CODE-COMPLETE, build-clean — awaiting Ctrl+Shift+K playtest verdict.**
  Two edits in `GroundSceneBackdrop`: `RenderLayer.UNITS` → `SCENE_LAYERS`; four unit sheets
  ensured in `initOnGlThread()` (`ensureUnitSheets`/`ensureMarineSecondarySprites`/
  `ensureTurretSprites`/`ensureDroneHubSprite`). Probe shows it on the map's turrets immediately;
  infantry latent until `deliverSquad`. Confirmed safe: reads only sim/camera/alphaMult,
  vision returns VIS_VISIBLE uninitialized, proxies are invisible (no double-draw). Move to
  `complete/` after playtest.
- **`stories/s3g-objectives-compound.md`** — `OBJECTIVES` + `COMPOUND`. Drop-in.
- **`stories/s3h-vehicles-convoy.md`** — `VEHICLES` + `CONVOY`. Carries the null-`selection`
  NPE gotcha (CONVOY DebugOnly overlays read `ctx.selection`).
- **`stories/s3i-fog-highlights.md`** — `FOG` + `HIGHLIGHTS`. Design calls (fog in a
  fleet-commander view? highlights source with no on-screen selection?).
- **`stories/s3j-fx-fbo-retarget.md`** — `DECALS`/`LIGHTING`/`IMPACT_FX`. Hard bucket: FBO
  blits are screen-space, need projection retarget. `SHUTTLES`/`FLYBY` are S3d's, not here.

Overview open question #2 is answered: the external-damage path is `applyExternalDamage`.

## Reusable combathybrid pieces

- `CombatHybridCampaignPlugin` — tag-armed `BattleCreationPlugin` selection (`PROBE_FLAG`).
- `S0BattleProbe` — launch + `Mode` {BASIC, SIM_COUPLED} + `PlayerFleetStash` (S0b/S2 modes retired in X4a).
- `CombatBridgeSession` — orchestrates the SIM_COUPLED vanilla-side lifecycle (`defineBattle` + `enterEngine`), delegating to the policy/adapter plugins.
- `GroundBattleConfig` — host-agnostic battle snapshot (sim + grid + scale + render-layer set + targetable + proxy variant + damage scale).
- `GroundSceneBackdrop` / `SimProxyMirror` — the durable render sink + sim⇄vanilla coupling.
- `SpectatorCanvasPlugin` — free cam (`viewport.set()`-based), HUD starve, fleet restore.
- Scale: `WORLD_UNITS_PER_CELL = 20` (lowered from 50 after S3b playtest). Real variant ids validated before spawn.
- *Retired (X4a):* `CanvasBackdropRenderer` (grid plate, superseded by `GroundSceneBackdrop`), `ProxyTargetPlugin` (single-proxy probe, superseded by `SimProxyMirror`). Verdicts sealed in `complete/`.

## Gotchas (also in the `startbattle_plugin_pick_deferred` memory + overview facts)

- `startBattle` picks the `BattleCreationPlugin` a frame late → gate by opponent-fleet
  memory tag, not an armed flag.
- `startBattle` deploys from the REAL player fleet → stash it for spectator; restore
  ~0.5s INTO combat, not on exit (else game-over).
- `setViewMult` is inert under `setExternalControl` → drive `viewport.set(llx,lly,w,h)`.
- `spawnShipOrWing` resolves variant ids eagerly (validate); `createFleetMember` is lazy.

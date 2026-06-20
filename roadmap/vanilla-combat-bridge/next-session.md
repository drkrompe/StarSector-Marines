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
- **X4b — package reorg** ✅ **DONE** (full build green). `combathybrid` split into `bridge/`
  (durable adapters + config), `host/` (session lifecycle + policy plugins), `probe/` (@DebugOnly
  dev trigger). Dependency arrow `probe → host → bridge`; package-info charter in each + umbrella
  refreshed. Only external ref was `StarsectorMarinesModPlugin` (→ `.probe.`). git-mv, no logic
  touched.

**The extraction is complete (X1–X4b).** The bridge now reads:
`probe → CombatBridgeSession(GroundBattleConfig) → GroundSceneBackdrop + SimProxyMirror`.

## Post-vacation playtest pass (2026-06-20) — S3f works; two tuning threads opened

Ctrl+Shift+K playtested: **S3f confirmed** — vanilla ships engage the ground (turret)
targets natively. Two pieces of feedback drove the current work:

### Thread 1 — carrier engagement (S3c) ⏸ PARKED → skybattle feature
Carriers idle at spawn and rarely commit. **Built `CarrierEngagementPlugin`** (host/, one-shot
`ASSAULT` assignment toward the targetable centroid; crash-fixed from `ENGAGE` — see
[[combat_assignment_target_types]]). **Playtest verdict: carriers commit briefly then drift
back** — a one-shot order doesn't stick against the admiral. Per the user, fleet-AI depth isn't
a probe concern; **parked into the new [`stories/skybattle-fleet-control.md`](stories/skybattle-fleet-control.md)**
story (the fleet fight over the city, where fleet control + the air⇄ground economy live). The
plugin stays wired (harmless) as that story's starting point; lever ladder documented there.

### Thread 2 — mid-combat AI takeover = S3d landing foundation (NEXT, user-prioritized)
User confirmed the goal: take over a real vanilla ship mid-combat to **fly it down to the
ground layer for troop drops** (landings). **Mid-combat AI swap is supported** — `ship.
setShipAI(plugin)` is callable any frame (we already null proxies' AI); `ShipAIPlugin` is an
8-method interface (mostly no-op), so a takeover brain is light. Three grip tiers:
assignment (ship keeps its AI) → `setShipAI` (own the brain, vanilla physics still flies it)
→ per-frame `getLocation().set` puppet (what proxies do). The descent/landing wants tier
2/3. **Next slice: a `setShipAI` takeover probe** that scripts one carrier's descent to the
ground band — the S3d handoff mechanism. (`deliverSquad(cellX,cellY,MarineLoadout[])` on the
sim — `AirProvider.EXTERNAL` — is the already-framed sim-side receiver.)

### Live battle below the fleet ✅ SHIPPED (2026-06-20) — the chosen "bridge the sim over" slice
The coupled sim was **map-only** (terrain + static defense-post turrets). Swapped it to a **live
Conquest battle**: `buildSimCoupledConfig` now calls `BattleSetup.createConquestBuild(...)` (HIGH
risk → LARGE) instead of `buildMap`, so defenders, manned guardposts, marines via internal
shuttles, objectives, and reinforcement all run as a real battle rendered below the ships.
- **`BattleSetup.createConquestBuild(...) → MapBuild`** is a behavior-preserving extraction of
  `createConquest`'s body that *keeps* the spawned-structures list (the proxy mirror needs it);
  `createConquest` is now a thin `.sim()` delegate. Only prod caller (`MissionLaunch`) unchanged.
- **Air stays `INTERNAL`** (the chosen tradeoff): the sim owns its shuttles/flyby; the vanilla
  carriers' strafing is *additive* pressure. EXTERNAL + the external-landing handoff is S3d.
- **Targetable = defense-post structures only** (turrets + drone hubs); defender/marine infantry
  are never directly proxied (architecture Decision 2 — area damage, not lock-on).
- **`NeverEndObjective` deleted**: it was a crutch for the map-only sim; the live battle has real
  win conditions and governs its own completion exactly as the standalone/production flow does.
- **Pending Ctrl+Shift+K playtest:** confirm a real ground battle plays out under the fleet (marines
  land + fight defenders), structures still proxy + take strafe damage, battle resolves normally.

### Thread 3 — proxy hitbox / fighter-wing proxies (S3f follow-up, parallel)
Two sub-points. (a) **Hitbox size/shape**: `setCollisionRadius(float)` is mutable at runtime
→ size each proxy to its ground footprint (turrets small). True polygon *shape* comes from
the hull variant bounds (not cheaply settable) — pick a small-bounds variant if shape
matters. (b) **"Smaller target PD prioritizes"**: PD prioritization most likely keys off
`isFighter()` (hull-type), NOT `CollisionClass.FIGHTER` — so the reliable path is to spawn an
actual **fighter wing** as the proxy, not flip the collision class on a ship hull. User's
extension idea: **ground bases (defense structures) launch their own fighter wings** — a
sim→vanilla spawn turning a defense post into a carrier-like entity. Scope as a proxy-model
slice after the engage + takeover threads. **Architectural line to hold:** marines/infantry
never get a direct 1:1 proxy — they take *area* damage (architecture.md Decision 2); the
"projectile hits the proxy boundary" weirdness is exactly why infantry are area-only. The
current proxies are turrets/structures (genuinely solid), so today's boundary model is fine.

**Render-layers thread resolved (S3f–S3j):** S3f/S3g/S3h wired (build-clean; `DEFAULT_SCENE_LAYERS`
now = GROUND/DOODADS/ROOFS/UNITS/OBJECTIVES/COMPOUND/VEHICLES/CONVOY), S3i decided-skip (no fog in the
orbital view; highlights blocked on a selection model), S3j deferred (no source + hard FBO retarget).
**Open:** S3f's Ctrl+Shift+K playtest verdict (code carried unchanged through the extraction), and
**S3c — airspace/AI viability**, the independent load-bearing de-risk the render work doesn't touch.

## S3f–S3j — bridge render layers (thread RESOLVED; see per-story `Status`/`Decision` sections)

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
- **`stories/s3g-objectives-compound.md`** — `OBJECTIVES` + `COMPOUND`. ✅ **DONE, build-clean** —
  added to `DEFAULT_SCENE_LAYERS` + `ensureObjectiveIcons()`; verified no null-`RenderContext` access.
- **`stories/s3h-vehicles-convoy.md`** — `VEHICLES` + `CONVOY`. ✅ **DONE, build-clean** — added to
  `DEFAULT_SCENE_LAYERS` + `ensureVehicleSheets()`/`ensureConvoySprites()`; the null-`selection` NPE
  fixed by guarding `BattleRenderer.renderSelectedVehicleDebug` on `rc.selection == null`.
- **`stories/s3i-fog-highlights.md`** — `FOG` + `HIGHLIGHTS`. ✅ **DONE as a decision-record (no
  wiring).** FOG skipped — orbital fleet-commander POV reveals the surface; fog stays a
  ground-commander mechanic on `BattleScreen`. HIGHLIGHTS deferred — no selection model in the bridge
  to source `ctx.highlights` (its own thread). `DEFAULT_SCENE_LAYERS` unchanged.
- **`stories/s3j-fx-fbo-retarget.md`** — `DECALS`/`LIGHTING`/`IMPACT_FX`. ⏸ **DEFERRED** (last item).
  No source in the map-only bridge (all are sim-side shot/lightmap events — nothing until live sim
  combat post-`deliverSquad`/S3d) **and** it's the hard FBO screen-space retarget bucket with a
  LIGHTING design call. Pick up when the bridge actually fights. `SHUTTLES`/`FLYBY` are S3d's, not here.

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

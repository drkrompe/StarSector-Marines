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
- **S3c — airspace banding / AI gating.** Parked → folded into the skybattle feature.
- **S3d — drop-ship invasion.** Re-spec'd 2026-06-25 into the bridge's product core (transport
  orbits, sim-native dropships land marines, diegetic/scored/emergent). Vision + D1–D5 ladder
  written; **D1–D5 ALL shipped 2026-06-27 — the full drop-ship invasion (scatter + click DZ + AA
  shoot-down / threat-spread + orbit window & stake + continuous logistics `3b4a6d3d`). Only
  extraction/dustoff remains as a later inverse.** See the S3d story.

### Scale + real-map pass (done after S3b playtest; re-dialed 2026-06-27)
- `WORLD_UNITS_PER_CELL` stepped **50 → 20 → 7** (`02c829b0`… → `2118b4b2`): at 7 the ground
  footprint reads as a compact battlefield far below a big fleet. Backdrop + proxies both derive
  from it, so they stay locked; the spectator cam's initial framing + the proxy collision
  footprint ([[`FootprintCircleShape`]]) now derive from it too. Still a visual knob.
- The `SIM_COUPLED` probe loads a **real Conquest map** via `BspCityGenerator.generate(w,h,seed,
  axis, NEUTRAL)` (biome bands + `DefensePostStamper`); defense-post turrets/hubs spawned + mirrored
  as proxies (fighters strafe the planet's actual defenses). **Grid is now the bridge's own
  `BRIDGE_GRID_W/H = 480×320`** (2× LARGE), decoupled from the standalone `MapScale` tiers via the
  explicit-dims `BattleSetup.createConquestBuild` overload (`1a2c87f4`) — so we push the battlefield
  bigger without enlarging (or paying the world-sized decal-FBO cost of) standalone HIGH battles.
  **Scaling gut-check + the tiled-FBO / camera-residency plan to go bigger:**
  [`../battle-render/large-map-scaling.md`](../battle-render/large-map-scaling.md). Playtest
  watch-items: BSP generator behavior past its 240×160 test size; HIGH-risk defender density spread
  over 4× area.
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
  `attachAirTurrets`/`setFlybyRoster` (host owns the air). *(Correction: the live-battle slice
  below settled the bridge on **INTERNAL** — the sim owns its own shuttles, and the carriers'
  air-to-ground is additive. The S3d drop-ship invasion **depends** on INTERNAL: the dropships are
  the sim's own `Shuttle`s via `addShuttle`. EXTERNAL/`deliverSquad` is the superseded path.)*
  Every standalone factory + test stays INTERNAL, unchanged.

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

### S3d — DROP-SHIP INVASION: vision locked (2026-06-25); D1 shipped (2026-06-27), D2 next
S3d was re-spec'd from a "vanilla-ship scales down and lands" handoff into the **drop-ship
invasion** — the cinematic+systemic core of the bridge. Full vision, design pillars, reuse map,
and the D1–D5 build ladder are written in [`stories/s3d-shuttle-scaledown.md`](stories/s3d-shuttle-scaledown.md).
**Read that story before building D1.** The scene: a transport **establishes a stable orbit** over
a commander-painted DZ; **sim-native dropships** fall through atmosphere, scatter marines across the
zone, and the ground AI fights them. Continuous waves, **diegetic currency** (fleet marines = depth,
transport capacity + cycling = throughput — no points), **scored hot/cold LZ** (one threat number,
not a gate), **emergent** outcomes (fight-to-the-end / lost transport with marines aboard).

**Key pivot — the dropships are the sim's own `Shuttle`s, air stays `INTERNAL`.** This *deletes* the
old hard parts: sim shuttles already altitude-scale (no owned-sprite anim), the carrier never leaves
vanilla (no `removeEntity`/`addEntity` resurrection probe), and no `EXTERNAL`/`deliverSquad` is needed.
The vision rides machinery that already runs in the bridge (the 4 Aeroshuttles + `totalCycles`/`rearmDelay`
cadence + `TacticalScoring` + defense-post proxies + the wired-forward `ShuttleMission.hp` for AA).

**Built — D1 complete (the whole scene at its simplest):**
- **Part 1 — orbit-positioning takeover (`996ce08`, fix `70f3d0a`):** `CarrierDescentBrain`
  (`ShipAIPlugin`, tier 2 — turn-toward + cone-gated thrust + speed-bleed, now exposes `hasArrived()`) +
  `CarrierDescentPlugin` (press **L** → take over the first live carrier, steer it to
  `GroundBattleConfig.targetableCentroid`). **Playtested 2026-06-25: the ship hard-moves to the target**
  (tier-2 de-risk passed).
- **Part 2a — `SHUTTLES` render layer (`02c829b0`):** added `RenderLayer.SHUTTLES` to
  `DEFAULT_SCENE_LAYERS` + `ensureShuttleSprites()`/`ensureEngineFxSprites()` in `GroundSceneBackdrop`.
  `ShuttleRenderSystem` was already registered, so this *also* made the four pre-existing setup
  Aeroshuttles (running invisibly) read on screen descending + deboarding. **The immediate visible win.**
- **Part 2b — drop-ship launch (`219b04ee`):** `GroundBattleConfig.worldToCell` (inverse of
  `cellToWorld`) + `CarrierDescentPlugin.advance` one-shot spawns a pure-transport `AEROSHUTTLE`
  (`Faction.MARINE`) via `sim.addShuttle` on `brain.hasArrived()`: entry = carrier cell, LZ = band
  centroid, entry pushed back along the carrier→LZ bearing (`MIN_DROP_LEG_CELLS = 12`) so the
  altitude-lerp descent always reads. INTERNAL air; sim ticks earlier in the frame (SimProxyMirror) so
  no race.
- **Part 2b egress — fly home to the carrier (`89012a28`):** the deboarded drop-ship returns to its host
  carrier and docks instead of vanishing mid-grid. `ShuttleMission.exitX/exitY` made mutable;
  `CarrierDescentPlugin.retargetDropExit` steers exit to the carrier's live cell each frame (near-stationary
  orbit → reads as return-to-mothership), with an off-map egress fallback once the carrier has left.

**D1b critique fix (`16433ef5`):** a background review caught a real wedge — the raw-centroid LZ could sit
on a non-walkable cell, and the deboard scan only reaches 5 cells, so a dropship there would stick in
LANDED forever and deliver nothing. Fixed by snapping the LZ to the nearest walkable cell before spawn. The
rest of D1b (geometry, min-leg pushback, one-shot latch, INTERNAL-air, add-vs-tick ordering) verified correct.

**Critique LOWs cleared (`89012a28`, `50268888`):** departing-vanish → carrier-return egress (above);
arrival latch that could never trip on a non-settling lumbering carrier → dwell fallback
(`ARRIVE_DWELL_SEC = 6s`, logged "dwell-timeout"); "one takeover per battle" doc → "one active at a time."
The brain-target vs fresh-LZ centroid divergence (#4) was confirmed a non-bug, left as-is.

**D2 — DZ + scatter ✅ shipped.**
- **Slice 1 — scatter engine (`3d232392`):** `DropZoneScatter` (battle/air) — pure, dependency-inverted
  (walkable + threat lambdas, no compile dep on grid/`TacticalScoring`, unit-tested). The carrier
  launches a *wave* (`DROP_COUNT = 5` staggered `AEROSHUTTLE`s): safest-first, min-spaced landing cells;
  threat = `TacticalScoring.countCombatantsWithin(DEFENDER, …)`. Cold spread (threat ranks cells, radius
  fixed). Single-drop ref became a wave list; retarget tracks + prunes the whole wave. Critique M1
  (INTERNAL-air contract) fixed `59e97470`; L1/L5 perf/leak follow-ups noted in the story.
- **Slice 2 — "land here" click DZ (`141bf9e9`):** left-click designates a **wide** LZ
  (`ZONE_RADIUS_CELLS = 30`) at the cursor; the carrier takes over + flies there + scatters the wave
  around it. Re-click before the drop re-aims (`CarrierDescentBrain.setTarget`). L-key trigger removed.
  Painting rejected as too fiddly for fast play (user call). Cursor→world via viewport rectangle getters
  (not `convert*`, which drift under the spectator camera). DZ-overlay (radius under cursor) = polish TODO.

**D3 — AA / hot drops ✅ shipped.**
- **Slice 1 — AA drain + shoot-down (`0751e013`):** `AirSystem.tickAirThreat` — airborne shuttles within
  `AA_THREAT_RADIUS` of an enemy `MapTurret` take `AA_DPS_PER_POST × posts` HP/sec; 0 HP → shot down
  (GONE, marines aboard lost). First `ShuttleMission.hp` damage source; `HOVER_HP_THRESHOLD` abort now
  live. Universal (standalone shuttles too) — **watch standalone balance**; tunables `AA_THREAT_RADIUS_CELLS`,
  `AA_DPS_PER_POST`. Follow-ups: drone-hub AA, crash FX.
- **Slice 2 — threat-scaled spread (`4fba6ea0`):** DZ radius = `ZONE_RADIUS_CELLS × (1 +
  HOT_SPREAD_PER_ENEMY × enemyCount)`, capped `ZONE_RADIUS_MAX_CELLS`. Cold = tight 30; hot fans toward 60.

**D4 — orbit window + the stake ✅ shipped (`9378c0b3`).** Single-wave drop → multi-wave orbit window:
the transport holds `INVASION_WAVES` (=3), deploys one on arrival then one per `WAVE_INTERVAL_SEC` while
holding orbit, peels off when the manifest empties (`departCarrier` → off-grid). The stake: carrier death
mid-window forfeits undeployed waves (`forfeitUndeployed`) — **wired but latent** (no carrier death source
in the probe until the skybattle feature; demonstrable today = timed multi-wave deploy + peel-off). Re-aim
locked once the first wave commits.

**D5 — continuous logistics ✅ shipped (`3b4a6d3d`).** `marinePool` (depth) = the player fleet's marine
count (`readFleetMarines` off campaign cargo, uncapped + `DEFAULT_PROBE_POOL` fallback); each wave deploys
up to `DROP_COUNT × AEROSHUTTLE.capacity` marines (throughput, last ship partial) until the pool runs dry →
peel-off, fight-to-the-end emerges. `spawnDrop` carries per-ship marine count.

**S3d is DONE (D1–D5).** The full Starship-Troopers loop runs end to end. Remaining bridge work is a
backlog, not the ladder:
- **Extraction / dustoff** — the inverse of the drop (board squads, lift out under fire). Later.
- **Carried follow-ups:** D5 — route the pool via the campaign→battle bridge (`TargetProfile`) instead of
  the probe's direct campaign read; derive throughput from fleet transports. D3 — M2 SABOTAGE-opener AA
  balance, L2 AA-vs-scatter radius, drone-hub AA, crash FX. D4 — off-grid peel-off past arena edge (cosmetic).
- **The stake is latent** until the **skybattle feature** can threaten the orbiting carrier (the death
  source that makes D4's forfeit fire). That's the natural next bridge feature.

**D1 playtest (2026-06-27): works okay** — carrier orbits, drops, deboards, dropship flies home. Remaining
watch-items (the dev probe — Ctrl+Shift+K, press **L**): stale target as structures die (non-bug); ASSAULT
co-existence with the takeover (`setShipAI` should win — confirm no tug-of-war).

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
- **Playtest verdict (2026-06-25, partial):** ✅ a real ground battle **does play out under the
  fleet** (marines land + fight defenders, structures proxy + take strafe damage). ⚠️ but three
  things look broken in the bridge host — **follow-ups, not this slice's scope** ([[feedback_followup_tasks]]):
    1. ~~**Sound / SFX** — sim combat audio doesn't play under the bridge host.~~ ✅ **FIXED
       (2026-06-27).** Root cause: the bridge split sim-tick (`SimProxyMirror`) from rendering
       (`GroundSceneBackdrop`), and *neither* drove presentation — no FX spawn/age, no sound, no
       listener; `SHOTS`/`IMPACT_FX` were also absent from `DEFAULT_SCENE_LAYERS`. Fix: added those
       two (camera-projected) FX layers + a new `GroundSimPresentation` (particle FX + positional
       combat audio, in the **combat-world** frame so ground + fleet audio share one spatial scale),
       driven by `SimProxyMirror` right after `sim.advance()` (fresh event lists). Deliberately **no
       lights/decals** — ~~LIGHTING/`WeaponLights` is slated for removal~~ ✅ LIGHTING/`WeaponLights` removed 2026-06-29; DECALS is
       the still-deferred screen-space-FBO bucket (S3j). **Playtest watch-item:** does
       `setListenerPosOverrideOneFrame` survive *inside* `CombatEngine`? (Only confirmed outside —
       [[starsector_positional_audio]].) If vanilla stomps it, audio still plays (grid is origin-centered
       where a spectator listener sits); only ideal panning is at risk.
    2. ~~**Roof LoS hide / re-add** — the `ROOFS` layer's reveal-on-enter / re-cover-on-leave behavior
       (interiors hidden until a unit enters, roof re-added when it leaves) appears broken in
       `GroundSceneBackdrop`.~~ ✅ **FIXED (2026-06-29).** Root cause was *not* the doc's "no viewer
       unit" hypothesis — the sim's vision tick (`FogOfWarService` → `BuildingVisibilityPass.update`)
       writes each building's `targetAlpha` correctly under the bridge (it runs inside `sim.advance()`,
       which `SimProxyMirror` drives). The gap was the *render-side* fade: the per-frame
       `currentAlpha → targetAlpha` lerp lived only in `BattleScreen.advance`, so the bridge left every
       roof frozen at its initial opaque `currentAlpha = 1f`. Fix: extracted the lerp to
       `BuildingVisibilityPass.advanceAlpha(buildings, dt)` (shared fade-rate constant; standalone
       delegates to it, behavior-preserving) and drove it from the bridge's `GroundSimPresentation.advance`.
       **Discovered sibling, fixed in the same pass:** the per-unit visibility fade
       (`VisionService.advanceFade`) had the *identical* gap — a real-dt fade driver that lived only
       in `BattleScreen.advance` — so out-of-LoS enemy units froze mid-fade (ghosts) instead of
       hiding. Both real-dt vision fades now run in `GroundSimPresentation.advance`. **Playtest
       watch-item:** confirm roofs fade as marines enter buildings, and defenders fade out as the
       player loses sight of them, under Ctrl+Shift+K.
    3. (Carried) the older audio observation folds into #1.
    4. **Shuttle engine-loop audio — DECIDED-SKIP (2026-06-29).** `BattleScreen.driveShuttleEngineLoops`
       plays each visible shuttle's continuous engine-loop SFX; the bridge's `GroundSimPresentation`
       never ported it, so S3d dropships + the setup Aeroshuttles descend silent-engined under the
       fleet. **Deliberately not porting it.** The engine loop is a vanilla `sounds/sfx_engines/` clip
       — the *same clip family* the real carriers/fighters are already droning in that airspace — so
       unlike the surface-combat audio (rifles/explosions, which had *no* vanilla analogue and filled a
       real silence), this isn't filling silence, it's adding redundant drone to an already-busy
       soundscape. The dropships already read as powered craft via the engine-FX plumes (`SHUTTLES`
       layer, shipped S3d D1), so the audio would be additive flavor, not feedback; and
       `GroundSimPresentation` is explicitly a slim, purposeful subset of the standalone driver (it
       drops lights/decals too), so omitting one redundant ambience fits the charter. Cost was never
       the blocker (~15 lines; `playLoop` voices self-expire when un-pumped). **Escape hatch:** if a
       descent ever feels conspicuously dead under the fleet in playtest, the lever is to mirror
       `driveShuttleEngineLoops` into the combat-world audio frame (`cfg.worldUnitsPerCell()`, like
       `GroundSimPresentation.playAtCell`). (`playCombatEventSounds` is *not* a gap — the 2026-06-27
       audio fix already ports it as `GroundSimPresentation.playFireSounds`/`spawnImpactFxAndSounds`/
       `playDeathVoice`.)
  These were render/audio-host gaps in `GroundSceneBackdrop` / the bridge audio path, distinct from
  the S3d descent thread. **The bridge-host-parity pass is now done** (2026-06-29): audio + roof-fade
  + unit-fade fixed in code, engine-loop decided-skip. The only remaining host gap is the
  deliberately-deferred FBO screen-space bucket (`DECALS`, S3j). **Pending playtest
  confirmation** (Ctrl+Shift+K): the roof/unit-fade watch-items above, and S3f's units-layer verdict.

### Thread 3 — proxy hitbox / fighter-wing proxies (S3f follow-up, parallel)
Two sub-points. (a) ~~**Hitbox size/shape**: size each proxy to its ground footprint.~~ ✅
**SHIPPED (2026-06-27, `4625f753`)** — a `ProxyShape` seam applied at spawn in `SimProxyMirror`.
`FootprintCircleShape` (the general structure shape) sets `setCollisionRadius` to the entity's
footprint in cells (`TurretKind.visualCells` / `DroneHubUnit.VISUAL_CELLS`) × `worldUnitsPerCell`,
so the hittable area matches the structure and tracks cell density. Seam has two extension axes:
**more proxied targets** → register a footprint in `FootprintCircleShape.footprintCells`; **more
shapes** (true polygon bounds, multi-proxy clusters) → sibling `ProxyShape` impls picked in
`ProxyShape.forUnit`. (True polygon *shape* still needs hull-variant bounds — a future shape impl,
pick a small-bounds variant if it matters.) (b) **"Smaller target PD prioritizes"**: PD prioritization most likely keys off
`isFighter()` (hull-type), NOT `CollisionClass.FIGHTER` — so the reliable path is to spawn an
actual **fighter wing** as the proxy, not flip the collision class on a ship hull. User's
extension idea: **ground bases (defense structures) launch their own fighter wings** — a
sim→vanilla spawn turning a defense post into a carrier-like entity. Scope as a proxy-model
slice after the engage + takeover threads. **Architectural line to hold:** marines/infantry
never get a direct 1:1 proxy — they take *area* damage (architecture.md Decision 2); the
"projectile hits the proxy boundary" weirdness is exactly why infantry are area-only. The
current proxies are turrets/structures (genuinely solid), so today's boundary model is fine.

**Render-layers thread resolved (S3f–S3j):** S3f/S3g/S3h wired (build-clean; `DEFAULT_SCENE_LAYERS`
now = GROUND/DOODADS/ROOFS/UNITS/OBJECTIVES/COMPOUND/VEHICLES/CONVOY/SHUTTLES + **SHOTS/IMPACT_FX**
(combat FX, added 2026-06-27 with the audio fix)), S3i FOG **reversed → wired 2026-07** (the
visibility gate is live in the bridge, so unmatched fog made enemies read as emerging from nowhere;
HIGHLIGHTS still blocked on a selection model), S3j now narrowed to **DECALS only** — the hard
screen-space-FBO bucket (~~LIGHTING slated for removal~~ → ✅ LIGHTING removed 2026-06-29; keep LoS-shadow + DECALS).
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
- **`stories/s3i-fog-highlights.md`** — `FOG` + `HIGHLIGHTS`. ✅ **FOG wired 2026-07 (decision
  reversed); HIGHLIGHTS still deferred.** The 2026-06 skip leaned on "a no-op anyway — the sim never
  inits vision," which is no longer true: the bridge sim runs full vision, so the unit-visibility gate
  pops enemies in/out and unmatched fog made them read as emerging from nowhere. `RenderLayer.FOG`
  added to `DEFAULT_SCENE_LAYERS` (`collectFogOverlay` is `rc.camera`-projected, slots after DOODADS /
  before UNITS by enum order, early-returns if vision is uninitialized). HIGHLIGHTS deferred — still no
  selection model in the bridge to source `ctx.highlights` (its own thread).
- **`stories/s3j-fx-fbo-retarget.md`** — `DECALS` (~~`LIGHTING`~~ removed 2026-06-29; `IMPACT_FX` shipped). ⏸ **DEFERRED** (last item).
  No source in the map-only bridge (all are sim-side shot events — nothing until live sim
  combat post-`deliverSquad`/S3d). Pick up when the bridge actually fights. (`SHUTTLES` shipped in S3d D1a;
  `FLYBY` is S3d's too, not here.)

Overview open question #2 is answered: the external-damage path is `applyExternalDamage`.

## Reusable combathybrid pieces

- `CombatHybridCampaignPlugin` — tag-armed `BattleCreationPlugin` selection (`PROBE_FLAG`).
- `S0BattleProbe` — launch + `Mode` {BASIC, SIM_COUPLED} + `PlayerFleetStash` (S0b/S2 modes retired in X4a).
- `CombatBridgeSession` — orchestrates the SIM_COUPLED vanilla-side lifecycle (`defineBattle` + `enterEngine`), delegating to the policy/adapter plugins.
- `GroundBattleConfig` — host-agnostic battle snapshot (sim + grid + scale + render-layer set + targetable + proxy variant + damage scale).
- `GroundSceneBackdrop` / `SimProxyMirror` — the durable render sink + sim⇄vanilla coupling.
- `SpectatorCanvasPlugin` — free cam (`viewport.set()`-based), HUD starve, fleet restore.
- `CarrierDescentBrain` / `CarrierDescentPlugin` — S3d D1: press **L** in-combat to `setShipAI` a tier-2
  steering brain onto one carrier, fly it to the ground band, then on `hasArrived()` launch one sim-native
  `AEROSHUTTLE` dropship from the carrier's projected cell (`worldToCell` + `sim.addShuttle`).
- `SeeThroughPlugin` — ground-control-mode probe: press **X** in-combat to toggle a cursor reveal disk
  that fades player ships (`getOwner()==0`, so proxies are excluded) by proximity via `setExtraAlphaMult2`,
  so the ground scene shows through them. Reveal-disk (spotlight) model, per-ship temporal lerp, constant
  on-screen lens. Config-free. Concept + decisions in [`stories/ground-control-mode.md`](stories/ground-control-mode.md).
- `GroundBattleConfig.targetableCentroid(out)` — the shared "ground band" point (engagement + descent).
- Scale: `WORLD_UNITS_PER_CELL = 20` (lowered from 50 after S3b playtest). Real variant ids validated before spawn.
- *Retired (X4a):* `CanvasBackdropRenderer` (grid plate, superseded by `GroundSceneBackdrop`), `ProxyTargetPlugin` (single-proxy probe, superseded by `SimProxyMirror`). Verdicts sealed in `complete/`.

## Gotchas (also in the `startbattle_plugin_pick_deferred` memory + overview facts)

- `startBattle` picks the `BattleCreationPlugin` a frame late → gate by opponent-fleet
  memory tag, not an armed flag.
- `startBattle` deploys from the REAL player fleet → stash it for spectator; restore
  ~0.5s INTO combat, not on exit (else game-over).
- `setViewMult` is inert under `setExternalControl` → drive `viewport.set(llx,lly,w,h)`.
- `spawnShipOrWing` resolves variant ids eagerly (validate); `createFleetMember` is lazy.

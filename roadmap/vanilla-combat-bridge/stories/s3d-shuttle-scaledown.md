# S3d — Drop-ship invasion (the fleet-to-ground landing)

> The cinematic *and* systemic core of the combat bridge. A combat-worthy transport breaks from
> the fleet fight and **establishes a stable orbit** over a drop zone the commander paints; swarms
> of sim-native dropships fall through atmosphere, scatter marines across the DZ, and the marines
> fight as the auto-battler already knows how. **Continuous** (waves over the whole battle),
> **diegetic** (the fleet you brought is the only currency), and **emergent** (one threat-scoring
> spine, never scripted modes). Vision locked 2026-06-25; **D1–D5 all shipped 2026-06-27 — the full
> drop-ship invasion is in (click DZ → orbit → AA-gauntlet timed waves → threat-scaled scatter →
> fight; the fleet you brought is the depth, losing the transport is the stake). Only extraction/dustoff
> remains as a later inverse.**
> Unblocked by S3e (`AirProvider`).
>
> *(This story was originally "shuttle scale-down handoff" — a vanilla-ship-descends-and-shrinks
> mechanic. That approach is superseded; see § Superseded alternative for why and what it taught us.)*

## The scene (north star)

> The player's invasion fleet arrives over a contested city. A combat-worthy transport breaks off
> and establishes a stable orbit over a drop zone the commander paints. Swarms of sim-native
> dropships fall through atmosphere, scattering across the DZ — tight and clean if it's cold, wide
> and ugly if it's hot — and pour marines onto the ground, who fight as the auto-battler already
> knows how. Waves continue as long as the transport holds orbit and the fleet still has marines
> aboard. The enemy contests **both** the airspace (AA that taxes the loiter and shreds dropships)
> **and** the ground (forces near the DZ). How it goes — clean beachhead, scattered fight-to-the-end,
> transport lost with its marines still aboard — is **emergent, never scripted.** The currency is
> logistics: the fleet you brought.

The Starship-Troopers beat sheet:
1. **The mothership commits** — breaks from the fleet, lumbers into a stable orbit over the DZ.
   It stops being a free-floating asset and becomes a fixed, vulnerable thing. The commitment is the drama.
2. **Bay doors — the swarm** — a *cluster* of small dropships ejects and falls through atmosphere,
   scaling down as they descend. Staggered, each its own gamble.
3. **Touchdown — the pour-out** — ramps drop, squads spill out across the scattered landing cells.
4. **The mothership can't stay forever** — it holds the orbit window, then peels off (or dies trying).
   What got on the ground is what you've got.

## Design pillars

1. **Commander agency, not piloting.** The player decides *when* to commit and *where* to drop; the
   sim flies the dropships and the ground AI fights the squads. The fun is the commander role +
   spectacle, never micro-piloting a craft.
2. **Diegetic logistics currency — no points.** Two independent dials, both set by fleet composition:
   **depth** = total marines aboard the fleet (how many squads you can ever land); **throughput** =
   transport/dropship capacity + re-arm cadence (how many at once, how fast). An "unstoppable invasion
   fleet" is a legitimate engineered achievement — the game never caps it; the *antagonism* taxes it.
3. **Scored hot/cold LZ, not a gate.** Every candidate landing carries one threat number
   (AA coverage × loiter exposure × distance-to-objective × landing cover). Hot drop = skip the ground
   march but run the AA gauntlet + land scattered; cold drop = land clean but fight across the map. The
   player reads the board and picks their poison ([[feedback_scored_over_binary_gates]]).
4. **The orbit window is the stake.** Holding orbit is exposed, and the transport carries your invasion
   capacity in its belly — lose it and you lose **every marine still aboard.** This is the diegetic hard
   failure ([[feedback_hard_failure_preference]]) that finally makes the **sky fight** matter (air
   superiority / AA suppression is the price of the drop — the tie-in to [`skybattle-fleet-control.md`]).
5. **Scatter into a painted zone.** The commander paints a DZ; the sim samples landing cells within it,
   spread **scaled by threat** — tight when cold, wide and isolating when hot (paratrooper chaos). The
   *aftermath* (isolated squads consolidating, breaking contact, regrouping) is handled by the ground
   GOAP brain that already exists — the drop creates the mess, the tactical AI resolves it.
6. **Emergent outcomes, never modes.** Clean beachhead, scattered desperation, lost transport,
   fight-to-the-end-with-what-landed — all fall out of the same scoring + logistics, situational per map.

## Why this is cheaper than it looks — reuse map

The vision rides on machinery that **already exists and runs in the bridge today** (the SIM_COUPLED
sim already ticks 4 Aeroshuttles). Net-new work is small and additive:

| Beat | Already have | Net-new |
| --- | --- | --- |
| Dropship as a sim-native craft | `Shuttle` + `ShuttleMission` + `AirSystem` state machine (INCOMING→LANDED→deboard→mint squad→DEPARTING→GONE); invisible to vanilla, drawn by our backdrop | — |
| "Scale down through atmosphere" | `ShuttleMission` altitude lerp (`altitudeT = distRemaining/legStartDist`) already shrinks the craft on the descent leg | — |
| Wave cadence / throughput | `totalCycles` + `rearmDelay` already cycle sorties offstage | drive count/cadence from fleet, not a fixed manifest (D5) |
| Mothership establishes orbit | `CarrierDescentBrain` (the tier-2 `setShipAI` takeover, already built) | retarget from "land" to "hold orbit over DZ" |
| DZ scatter scoring | `TacticalScoring` (threat/cover spine the ground AI uses) | sample landing cells in the painted zone, spread ∝ threat |
| AA that shoots dropships | defense-post structures already proxy into vanilla + take strafes; `ShuttleMission.hp` is wired-forward (no damage source yet) | give posts an air-threat radius that drains shuttle hp |
| Spawn point | `sim.addShuttle(s)` (INTERNAL) | construct a `Shuttle` mid-battle with entry = carrier position projected to a cell (`worldToCell`, inverse of `cellToWorld`) |
| Draw the dropships | `RenderLayer.SHUTTLES` exists | add it to `GroundBattleConfig.sceneLayers` + ensure shuttle sprites (same wiring as S3f–S3h) |

**Air stays `INTERNAL`.** The dropships are the sim's own shuttles — no `AirProvider.EXTERNAL` and no
`deliverSquad` needed (those were the *superseded* path). The carrier never leaves the vanilla layer.

## Build ladder (D1–D5) — each rung adds one pillar, no rework

- ~~**D1 — the core handoff + the shot (MVP of the *scene*).**~~ **Shipped 2026-06-27**
  (`02c829b0` render, `219b04ee` spawn). Press **L** → carrier establishes orbit over the band
  (reuse `CarrierDescentBrain`) → on arrival, `CarrierDescentPlugin` spawns a sim dropship from the
  carrier's projected cell (`worldToCell`) → it descends (altitude-scale, free) → deboards its squad.
  The `SHUTTLES` layer draws it (plus the four pre-existing setup Aeroshuttles, which were already
  running invisibly). *The whole cinematic at its simplest — no AA yet.*
- **D2 — painted DZ + scatter** via `TacticalScoring` sampling landing cells in the zone (cold spread first).
  - **Slice 1 — scatter engine (`3d232392`, shipped).** `DropZoneScatter` (battle/air): pure,
    dependency-inverted (walkable + threat lambdas), picks up to N walkable cells in the zone disc
    safest-first with min-spacing. The carrier now launches a *wave* (`DROP_COUNT` staggered
    `AEROSHUTTLE`s, threat = `countCombatantsWithin(DEFENDER)`) over a hardcoded centroid+radius zone.
    Unit-tested (`DropZoneScatterTest`). Cold spread: threat ranks cells; radius fixed (D3 widens it).
    - **Critique pass:** logically correct, crash-free, all engine invariants verified (in-disc,
      spaced, walkable, safest-first), threading safe. One MEDIUM **fixed (`59e97470`)** — the feature
      requires `AirProvider.INTERNAL` but stale docs called the bridge `EXTERNAL`; reconciled the
      `BattleSimulation` javadoc + s3e doc + a call-site note. Two follow-ups (not blocking, both
      noted): **(L1)** `launchDrop` fires ~1000 `countCombatantsWithin` calls in one frame (one-shot
      launch hitch — coarser lattice / reuse scratch / pre-bucket density when it matters); **(L5,
      pre-existing)** `AirSystem` never removes `GONE` shuttles from its list (host prunes its own
      `drops`; the unbounded growth is in `AirSystem`, a separate cleanup task).
  - **Slice 2 — "land here" click-designated DZ (`141bf9e9`, shipped).** Left-click a point in the
    spectator canvas → the carrier takes over, flies there + orbits, and scatters its wave across a
    **wide** LZ (`ZONE_RADIUS_CELLS = 30`) around the click. A click before the drop re-aims it
    (`CarrierDescentBrain.setTarget`). The L-key centroid trigger is gone. *Painting was rejected as
    too fiddly for fast play — a wide single-click LZ reads as "land here-ish" (user call).*
    Cursor→world uses the viewport rectangle getters (not `convert*`, which drift under the
    spectator's `setExternalControl` camera — see `SeeThroughPlugin`).
    - **Critique pass (`fc9037d4`):** substantially correct (cursor→world math, LMB-consume safety,
      re-aim/defer timing all verified, no HIGH). Fixed: a re-home leak (an old wave got re-pointed
      onto a freshly-taken-over carrier — now flushed off-grid + forgotten), an `engine` null-guard, a
      premature `dropZoneWorld` write, and an off-map-click wedge (scatter center now clamped to the
      grid). DZ-radius overlay-under-cursor remains the only D2 polish TODO.
- **D3 — AA / hot drops (shipped 2026-06-27).** The hot/cold curve is live.
  - **Slice 1 — AA drain + shoot-down (`0751e013`).** `AirSystem.tickAirThreat`: each airborne shuttle
    (INCOMING / HOVER_STATION / DEPARTING) within `AA_THREAT_RADIUS` of an enemy `MapTurret` takes
    `AA_DPS_PER_POST × postsInRange` HP/sec; at 0 HP it's shot down (GONE via `releaseAirEntity`,
    marines aboard lost → partial-success wave). First damage source for `ShuttleMission.hp`, so the
    `HOVER_HP_THRESHOLD` loiter-abort also went live. Area drain (not lock-on), spatial-indexed.
    **Universal** (standalone arrival shuttles feel it too) — gentle tuning vs `AEROSHUTTLE` maxHp=60;
    *watch standalone balance.* Follow-ups: drone-hub AA, a crash FX (vs instant disappear).
  - **Slice 2 — threat-scaled spread (`4fba6ea0`).** DZ radius widens with zone threat:
    `ZONE_RADIUS_CELLS × (1 + HOT_SPREAD_PER_ENEMY × enemyCount)`, capped at `ZONE_RADIUS_MAX_CELLS`.
    Cold = tight wide LZ (30); hot fans out toward 60 (isolating). Launch log reports threat + radius.
  - **Critique pass:** correct + clean, no HIGH (AA gather, faction/turret filter, shoot-down
    terminal-path reuse, tick ordering, slice-2 math all verified). Fixed **M1 (`e624d1bc`)** — the
    multi-cycle re-arm refilled ammo but not HP, so AA damage carried across sorties (standalone
    SABOTAGE multi-cycle drop); the recycle now repairs the hull. Carried as follow-ups: **M2** —
    universal AA re-balances the standard SABOTAGE opener (playtest the turret-heavy opener
    specifically); **L2** — AA radius (14) > the scatter's per-cell threat radius (6), so a
    scatter-"safe" cell can still sit in a turret's AA bubble on approach (mitigated: LANDED is
    AA-exempt) — widen `THREAT_RADIUS_CELLS` toward AA range if "isolation = safety" should hold vs
    turrets.
- **D4 — the orbit window + the stake (shipped 2026-06-27, `9378c0b3`).** The single-wave drop became a
  multi-wave orbit window: the transport holds `INVASION_WAVES` waves, deploys one on arrival then one
  per `WAVE_INTERVAL_SEC` while holding orbit, and peels off (`departCarrier` → off-grid) when the
  manifest empties ("can't stay forever"). **The stake:** if the carrier dies mid-window, every
  undeployed wave is forfeit (`forfeitUndeployed` logs the lost waves + ~marines) — the diegetic hard
  failure that makes air-superiority / AA-suppression the prep that earns the drop. The death-forfeit is
  **wired but latent in the probe** (the carrier has no death source until the skybattle feature
  threatens it); the demonstrable beats today are the timed multi-wave deploy + the peel-off. Re-aim is
  locked once the first wave commits.
  - **Critique pass:** fundamentally sound, no HIGH (wave count/labels/spacing, once-only forfeit,
    peel-off coherence, re-aim lock, re-takeover-after-death, no NPE — all verified). Fixed the one
    MEDIUM (`5a258ad3`): a completed-but-alive carrier blocked a second takeover → one invasion per
    battle; `departCarrier` now releases the takeover on window-close so a fresh click can launch
    another. LOW (noted, not fixed): the off-grid peel-off target can sit past the combat-arena edge
    (cosmetic loiter); `undeployedMarines()` uses nominal `DROP_COUNT` (log estimate only).
- **D5 — continuous logistics (shipped 2026-06-27, `3b4a6d3d`).** The fixed manifest became the fleet
  you brought: `marinePool` (depth) = the player fleet's marine count (`readFleetMarines` off the
  campaign fleet cargo — uncapped when present, `DEFAULT_PROBE_POOL` fallback when none). Each wave
  deploys up to `DROP_COUNT` dropships × `AEROSHUTTLE.capacity` marines (throughput), last ship partial,
  drawing from the pool until it runs dry → the transport peels off and the fight-to-the-end *emerges*.
  `spawnDrop` carries a per-ship marine count; forfeit/peel-off read the pool. *Follow-up:* route the
  pool through the campaign→battle bridge ([[campaign_battle_bridge]] `TargetProfile`) rather than the
  probe's direct campaign read; derive throughput from actual fleet transports.
  - **Critique pass:** correct + well-constructed, no HIGH (wave/partial-ship math, pool can't go
    negative, guaranteed termination, `committed` lifecycle, `marinesRemaining` override sticking, the
    fleet read safe during the stash window — all verified). Follow-ups (NOT this file — log only):
    **MEDIUM** `AirSystem.tryDeboardMarine` derives the loadout slot as `type.capacity − marinesRemaining`,
    which assumes a full shuttle; D5 is the first to ship *partial* shuttles, so a partial last ship would
    read mis-aligned loadout entries — latent today (bridge drops have null `marineLoadout`), but fix
    before per-marine loadouts are ever wired onto a bridge drop (track a dedicated deboard counter, or
    store `initialMarines`). **LOW** uncapped pool has a long tail on a huge fleet (intentional for the
    probe; clamp/awareness when productionised).
- *(later)* **Extraction / dustoff** — the inverse: board squads, lift them out under fire ("hold until evac").

## Built so far — D1 complete (orbit takeover → drop-ship launch → render)

The full D1 loop is built + wired into the bridge (`CombatBridgeSession.enterEngine`). Press **L** in a
SIM_COUPLED battle and the whole scene plays out at its simplest:

**Part 1 — the orbit-positioning takeover** (originally the "fly-to-handoff" probe, repurposed):
- **`CarrierDescentBrain implements ShipAIPlugin`** (host/) — grip **tier 2**: owns the brain, vanilla
  physics flies the ship. Turns toward the target, thrusts only inside a heading cone, bleeds speed while
  turning / near the target so it arrives instead of orbiting; settles + logs at the target. Never fires.
  Exposes `hasArrived()` as the launch cue.
- **`CarrierDescentPlugin`** (host/) — press **L** to take over the first live carrier and steer it to the
  live ground-band centroid (`GroundBattleConfig.targetableCentroid`).

Proved the load-bearing tier-2 question — *a custom `ShipAIPlugin` can steer a live vanilla ship to a
chosen point against the admiral* (playtest 2026-06-25: the ship hard-moved to the target).

**Part 2 — the drop-ship launch + render** (`02c829b0`, `219b04ee`):
- **`GroundBattleConfig.worldToCell`** — inverse of `cellToWorld`; maps the carrier's combat-world
  position to the sim cell the dropship spawns from.
- **`CarrierDescentPlugin.advance`** — on `brain.hasArrived()`, one-shot spawns a pure-transport
  `AEROSHUTTLE` (`Faction.MARINE`) via `sim.addShuttle`: entry = carrier cell, LZ = band centroid, entry
  pushed back along the carrier→LZ bearing when the carrier settled almost on the LZ (the altitude lerp is
  leg-distance driven, so a zero-length leg shows no descent). Air stays **INTERNAL**, so `addShuttle` is
  legal; the sim ticks in `SimProxyMirror.advance` earlier in the frame, so the add never races the air loop.
- **`SHUTTLES` render layer** — added to `DEFAULT_SCENE_LAYERS`; `GroundSceneBackdrop` now loads shuttle +
  engine-FX sprites. `ShuttleRenderSystem` was already registered, so this also made the four pre-existing
  setup Aeroshuttles (which ran invisibly) read on screen.
- **Egress — fly home to the carrier (`89012a28`)** — instead of vanishing mid-grid, the deboarded
  drop-ship returns to its host carrier and docks. `ShuttleMission.exitX/exitY` became mutable;
  `CarrierDescentPlugin.retargetDropExit` steers the exit to the carrier's live projected cell each frame
  (the carrier holds a near-stationary orbit, so it reads as a return-to-mothership), falling back to an
  off-map egress only once the carrier has left the field.

**Critique fix (`70f3d0a`):** the heading-cone used `Math.abs(normalizeAngle(...))`, but `Misc.normalizeAngle`
returns `[0,360)` — so a small CW error read as ~350° and the ship braked instead of thrusting. Now uses
`Misc.getAngleDiff` (true `[0,180]`) + `Misc.turnTowardsFacingV2` (vanilla's overshoot-damped turn).

**Playtest watch-items** (fine for a `@DebugOnly` probe): target snapshotted at takeover (drifts as
structures die — confirmed a non-bug, geometry stays valid); the parked `CarrierEngagementPlugin` ASSAULT
co-existing with the takeover (`setShipAI` should win — confirm no tug-of-war). *(Playtested 2026-06-27:
works okay — carrier orbits, drops, deboards, dropship flies home.)*

**Critique pass (`16433ef5`):** a background review of D1b caught a real wedge — the LZ was the raw structure
centroid, which can sit on a non-walkable cell; the deboard scan only reaches 5 cells, so a dropship there
would stick in LANDED forever and deliver nothing. Fixed by snapping the LZ to the nearest walkable cell
(BFS over `sim.getGrid()`) before the spawn. The rest of the slice (inverse-projection geometry, the
min-leg pushback, the one-shot arrival latch, INTERNAL-air, add-vs-tick ordering) verified correct. D2's
threat-scored scatter still supersedes the single-centroid LZ with proper landing-cell selection.

**Critique LOWs cleared (`89012a28`, `50268888`):** the departing-dropship-vanishes wart became the
carrier-return egress (above). The arrival latch could never trip if a *lumbering* carrier orbited the
point without settling below `SETTLED_SPEED` — added a dwell fallback (`ARRIVE_DWELL_SEC = 6s`: loiter in
`ARRIVE_RADIUS` long enough and it latches anyway, logged "dwell-timeout"). The "one takeover per battle"
doc was corrected to "one active at a time" (a second carrier is takeable once the first dies).

## Superseded alternative — kept for the reasoning

The original S3d was a **vanilla-ship-descends** handoff: fly the real ship to a threshold, `removeEntity` it,
animate **our owned sprite** swooping + shrinking (`SpriteAPI.setSize`) down to a cardinal grid heading, call a
new `sim.deliverSquad(cell, loadout)` (`AirProvider.EXTERNAL`), then `addEntity` the same instance back for
takeoff. Its load-bearing unknown was *"does `addEntity` cleanly resurrect a `removeEntity`'d ship?"*

**Why it's superseded:** the sim-native-dropship path makes all of that unnecessary —
- the sim shuttle **already scales down** on its descent leg, so no owned-sprite animation;
- the carrier **never leaves the vanilla layer**, so no `removeEntity`/`addEntity` resurrection probe;
- dropships are **INTERNAL sim shuttles**, so no `EXTERNAL` / `deliverSquad` and no cross-engine damage immunity hack;
- it's also better fiction — carriers *launch craft*, they don't land.

What it taught us and we keep: there is **no ship-level render-scale** in the combat API
(`getSpriteAPI()` is a fresh per-call wrapper; scaling it desyncs weapons/engines/bounds), which is exactly
why we don't try to shrink a vanilla ship and instead let the *sim* own the descending craft. `deliverSquad`
+ `AirProvider.EXTERNAL` remain framed (S3e) for any *future* direct squad-injection variant, but are off the
critical path for this vision.

## Open questions (resolve at the relevant rung)

- ~~**DZ painting UX**~~ **Resolved (D2 slice 2):** a single left-click designates a wide "land here"
  zone (fixed `ZONE_RADIUS_CELLS`), re-aimable before the drop. Painting/brush rejected as too fiddly
  for fast-paced play. A DZ overlay (showing the radius under the cursor) is still a polish follow-up —
  the fact-12 "starve, don't cover" constraint applies when it lands.
- **Carrier→cell projection origin** — `worldToCell` is a trivial inverse, but the "airspace band" the
  carrier orbits in vs. the ground plane is a convention to pin (overview open question #1; S3c spatial fork).
- **Marine pool plumbing** — the per-battle marine count rides the campaign→battle bridge
  ([[campaign_battle_bridge]] — `TargetProfile`); confirm the field + how depletion reads back to the fleet (D5).
- **Camera during a drop** — follow the swarm down vs. stay at fleet altitude (note both; decide at D1 build).

## Acceptance (per rung)

- **D1:** trigger → carrier orbits the painted point → dropships spawn from it, visibly descend, and deboard
  squads that appear + fight in the sim; the `SHUTTLES` layer draws them. The scene reads.
- **D3:** a hot DZ measurably costs dropships/scatter vs. a cold one — the curve is felt, not gated.
- **D5:** waves continue from the fleet pool until marines or transports run out; a fight-to-the-end can emerge.

# Vanilla Combat Bridge — next session

## State of play

The spike has its first code: **S0 — Battle bootstrap probe** is implemented and
compiles, but is **not yet playtested**. Everything else (S1 wall-clamp, S2
proxy-target) is still doc-only.

S0 proves the two capabilities the user called out:
1. Launch a vanilla `CombatEngineAPI` battle from the campaign with a chosen subset
   of the player fleet.
2. The mod owns when the battle is complete (suppress vanilla auto-end; `endCombat`
   on our terms).

## What landed (uncommitted unless noted)

New package `com.dillon.starsectormarines.combathybrid`:
- `package-info.java` — charter.
- `S0BattleProbe` — builds rosters, calls `CampaignUIAPI.startBattle`. Arm flag.
- `CombatHybridCampaignPlugin` — armed-gated `pickBattleCreationPlugin` @ MOD_SPECIFIC.
- `S0BattleCreationPlugin` — minimal location-independent battle def; installs the
  completion plugin.
- `S0CompletionPlugin` — `setDoNotEndCombat(true)`; F10 → `endCombat`.
- `CombatHybridInputListener` — Ctrl+Shift+B campaign-map hotkey.

Wiring:
- `DevConfig.S0_COMBAT_PROBE` (=true) gates it.
- `StarsectorMarinesModPlugin.ensureCombatHybridProbe()` registers the campaign
  plugin + input listener at game load (transient, de-duped).

Docs: `stories/s0-battle-bootstrap.md`; overview resequenced (S0 → S2 → S1).

## How to playtest

1. `gradlew.bat runStarsector`, load any campaign save.
2. On the system/hyperspace map press **Ctrl+Shift+B**.
3. Expect the deploy screen with ≤2 player ships (copies of your first 2 combat
   ships) vs 2 Hegemony frigates.
4. Deploy, wipe the enemy — battle should **not** auto-end. Check `starsector.log`
   for `S0 probe: a side is eliminated — vanilla would normally END the battle here`.
5. Press **F10** — combat ends, player victory.

## Verdict to record (after playtest)

- [ ] Req 1: deploy screen shows only the chosen subset? (roster control works)
- [ ] Req 2: auto-end suppressed; F10 ends on our terms?
- [ ] Returning to the campaign map is clean — no stuck state, no spurious
      campaign-side resolution against the synthetic enemy fleet?
- [ ] Does `setDoNotEndCombat` also hold the player-retreat path, or only
      side-eliminated?

## Canvas feasibility (researched, recorded in overview facts 8–12)

The five "how much can we bend the combat shell" gating questions came back all
feasible — free camera (`ViewportAPI.setExternalControl`), full input override
(`processInputPreCoreControls` + `consume`), render below ships
(`addLayeredRenderingPlugin` + layer stack), skip the deploy dialog (`spawnShipOrWing`
instead of reserves). The one hard limit: **you can't draw over a populated command
bar** (combat has no above-UI hook, unlike the campaign's `CampaignUIRenderingListener`)
— so the move is to *starve* the HUD via spectator + zero CP, not cover it. Full
detail + citations in `overview.md` § "round 2: vanilla combat as a sim canvas".

These are built into **S0b — spectator-canvas probe** (`stories/s0b-spectator-canvas.md`),
which exercises all of them at once. **Built, compiles, awaiting playtest.**

## Size gut-check (recorded)

Our grids: SMALL 112×64, MEDIUM 144×80, LARGE 240×160 cells; at the 1 m/cell target
that's ~64–240 m across (urban/infantry scale). Vanilla combat playfield is normally
18,000–24,000 world units — but we set it ourselves via `loader.initMap`, so fitting
isn't a constraint. The real free parameter is **world-units-per-cell**, seeded at
**`S0BattleProbe.WORLD_UNITS_PER_CELL = 50`** (LARGE → 12000×8000, inside vanilla's
range; ships read sanely). Tension to remember: vanilla ships are ground-tiny at this
scale (a frigate ≈ 1.6 cells) — fine for overhead-air (S2), but a vanilla mech placed
at ground level would need ~5–6× sprite upscale. So the scale is per-use, not global.

## S0b — built (uncommitted unless noted)

New in `combathybrid`:
- `SpectatorCanvasPlugin` — free cam (WASD poll + RMB-drag + scroll via
  `setExternalControl`/`setCenter`/`setViewMult`), input consume, screen-space
  overlay marker, disables player-ship control each frame.
- `CanvasBackdropRenderer` (`CombatLayeredRenderingPlugin`) — dark plate + grid lines
  on `BELOW_SHIPS_LAYER`, sized to grid × 50.
- `S0BattleProbe.Mode` {BASIC, SPECTATOR_CANVAS} + `launchSpectatorCanvas()`.
- `S0BattleCreationPlugin` branches: spectator path fields both sides AI @ 0 CP,
  spawns stock ships directly in `afterDefinitionLoad` (no deploy dialog), sizes the
  map, installs the canvas + backdrop, `setPlayerShipExternal(null)`.
- `CombatHybridInputListener` — Ctrl+Shift+N.

### S0b playtest checklist (riskiest bits flagged)
- [ ] Ctrl+Shift+N starts combat with **no deploy dialog**, ships already placed.
- [ ] **No player ship** — does `setPlayerShipExternal(null)` + `useDefaultAI=true`
      actually yield a spectator, or does the engine force-pick a flagship? (highest risk)
- [ ] HUD effectively empty; the top-left overlay marker is visible (fact 12).
- [ ] WASD pan / RMB-drag / scroll-zoom work; camera stays ours across pause.
- [ ] Backdrop plate renders **under** the ships; ship FX on top.
- [ ] F10 still ends combat (S0 carry-over).
- [ ] `spawnShipOrWing` with the stock variant ids resolves (watch the log for spawn failures).

## Plugin-selection bug — found + fixed (re-playtest needed)

First S0b playtest: deploy picker appeared + player piloted a ship — i.e. the **core**
`BattleCreationPluginImpl` ran, not ours. Root cause: the old "armed boolean for the
duration of the `startBattle` call" never matched, because `startBattle` resolves the
battle-creation plugin on a *later* frame (after `launch()` returned and reset the
flag). So core always won and the spectator path never ran. (S0 BASIC "looked right"
only because core's output coincidentally matched our BASIC intent — we'd never
actually confirmed our plugin ran.)

Fix: tag the synthetic enemy fleet with `S0BattleProbe.PROBE_FLAG` in memory and
match on it in `CombatHybridCampaignPlugin.pickBattleCreationPlugin` — no timing
window. Added a `LOG.info("S0BattleCreationPlugin SELECTED …")` so the next playtest
confirms our plugin runs. **This also means S0 BASIC now genuinely runs our plugin
(F10 completion control included) for the first time.**

Verified: the three changed files pass IntelliJ per-file error analysis. Full
`gradlew compileJava` is currently red on an *unrelated* concurrent-session refactor
(`battle/ui/highlight/HighlightOverlay` + `BattleCamera`) — left untouched.

## Plugin pick CONFIRMED working; variant-id crash fixed

Second playtest: `S0BattleCreationPlugin SELECTED [mode=SPECTATOR_CANVAS]` +
`setDoNotEndCombat=true` both logged — **the pick fix works, our plugin runs.** It
then crashed: `[wolf_Standard] is not a valid ship variant id` from `spawnShipOrWing`.
The guessed stock ids were wrong (vanilla ships are `vigilance_Standard`,
`tempest_Attack`, etc. — `wolf_Standard`/`lasher_Standard`/`hound_Standard` don't
exist as variant files).

Two lessons baked in: (1) `createFleetMember` resolves variants *lazily* (BASIC's bad
enemy ids never threw at build), but `spawnShipOrWing` resolves *eagerly* and throws;
(2) both paths now use real ids (`vigilance_Standard` + `vigilance_Strike` for the
BASIC enemy, `vigilance_Standard`/`brawler_Assault` vs `tempest_Attack`/`shrike_Attack`
for the canvas) and **validate via `Global.getSettings().getVariant(id) != null`
before use** — a bad id now logs + skips instead of aborting the launch.

## Third playtest: canvas works; deployment picker still shows (fixed, re-test)

Spectator camera + auto-fighting both work — if you don't commit ships, the two AI
sides fight and our free camera drives. BUT a deployment picker still appeared
recognizing the player fleet, plus "press Tab to deploy" hints ("like joining a
battle with two arbitrary fleets fighting").

Root cause (corrects overview fact 11): for a `startBattle`-launched battle, the
player's deployable reserves come from **`context.getPlayerFleet()`**, not from our
`loader.addFleetMember` calls (those are mission-mode only). Our "don't addFleetMember"
never affected the player side — the synthetic 2-ship player fleet was the picker's
source.

Fix: spectator mode now passes an **empty player fleet** (`buildSpectatorPlayerFleet`)
+ 0 command points. Nothing deployable → no picker, no Tab prompt. The owner-0
combatants are still spawned directly by `S0BattleCreationPlugin`. **Risk to verify:**
the engine might balk at a player fleet with zero ships, or treat it as an instant
loss — `setDoNotEndCombat` should hold it, and the spawned owner-0 ships keep the
player side non-empty in combat, but this is the thing to watch on re-test.

## Fourth playtest: empty CONTEXT fleet ignored → stash the REAL fleet

The empty context player fleet still showed the picker: `startBattle` sources the
player's deployable ships from the **real** player fleet, ignoring the context fleet
entirely. So the only lever is to empty the real fleet for the battle's duration.

Implemented `PlayerFleetStash`: on spectator launch it detaches every member (and
remembers the flagship) from `Global.getSector().getPlayerFleet()`, passes the
now-empty real fleet as the context fleet, and a transient `RestoreScript` re-attaches
them once combat ends. Safety: restores on an 8s timeout if combat never starts
(so the fleet can't be stranded empty); idempotent; flagship preserved.
`SpectatorCanvasPlugin.init` calls `markCombatEntered()` so the restore fires on the
first campaign frame after the battle. **Compiles clean (full `compileJava` green).**

### Fifth playtest: spectator works; F10 exit triggered a game over

Picker gone, spectator camera + auto-fight confirmed, and the fleet DID restore (log:
`restored 2 player ships`) — but the restore fired on return to campaign (RestoreScript),
*after* F10, so the post-battle resolution saw an empty fleet and declared the player
**defeated / game over**.

Fix: restore the fleet **during** combat (`SpectatorCanvasPlugin`, `FLEET_RESTORE_DELAY`
= 0.5s in) — after the deploy-skip is locked, before any exit — so resolution reads a
healthy fleet. `PlayerFleetStash.restore()` is now public + idempotent; the campaign-side
`RestoreScript` stays only as a safety net (combat-never-started timeout). Watch on
re-test: that the during-combat restore doesn't re-show a deploy/reinforcement prompt,
and that exiting (F10) lands back in the campaign cleanly with no game over and the full
fleet intact.

### S0b re-playtest checklist
- [ ] Ctrl+Shift+N: no deployment picker, no "press Tab" prompt — straight into the
      watched battle.
- [ ] Two AI sides fight under our free camera; HUD effectively empty.
- [ ] **On exit (F10), the player fleet is fully restored** — same ships, same
      flagship, correct count. (Check the `PlayerFleetStash: restored N` log.)
- [ ] Backdrop plate under ships; F10 ends combat.
- [ ] Risk: empty real fleet might make `startBattle` refuse / instant-end — watch for
      it. `setDoNotEndCombat` + spawned owner-0 ships should keep the side alive.

## Immediate next-up

- **Re-playtest S0b**, focus on the picker being gone AND the fleet restoring cleanly.
- Then S0 BASIC (Ctrl+Shift+B) end-to-end with F10 (unaffected by the stash).
- After S0b verdict: **S2 — proxy-target probe** (spawn the proxy into the now-proven
  combat instance), then wire HP drain into the sim's external-damage path (open
  question #2).
- If `startBattle`/return-to-map has rough edges from the S0 playtest: note them here;
  they bound how the real ground-battle launch eventually hooks in (the mod currently
  launches its *own* custom-visual-dialog battles, not vanilla combat — see
  [[custom_visual_dialog_pattern]]).

## Watch out for

- The probe builds **copies** of player ship variants, not the real members — so it
  can't damage the player's fleet. If you want to test real-fleet losses feeding
  back to the campaign (overview Open question #5), that's a deliberate *next* step,
  not a bug.
- The arm flag (`S0BattleProbe.armed`) is only true during `startBattle`. If a real
  encounter ever picks up our `S0BattleCreationPlugin`, the gate failed — check the
  flag lifecycle first.

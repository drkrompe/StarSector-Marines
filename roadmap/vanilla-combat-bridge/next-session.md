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

These are scoped into **S0b — spectator-canvas probe** (`stories/s0b-spectator-canvas.md`),
which exercises all of them at once. Not built yet.

## Immediate next-up

- **S0b — spectator-canvas probe** is now the natural next build: it composes facts
  8–12 (blank HUD + free cam + below-ships backdrop + UI overlay + no-dialog setup)
  and feeds the coordinate-mapping open question (#1). Cheaper than S2 and proves the
  "combat as a host" thesis directly.
- After S0b: **S2 — proxy-target probe** (spawn the proxy into the now-proven combat
  instance), then wire HP drain into the sim's external-damage path (open question #2).
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

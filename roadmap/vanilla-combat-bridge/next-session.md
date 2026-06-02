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

## Immediate next-up (after S0 verdict)

- If S0 is green: **S2 — proxy-target probe** is the next build (it now has a real
  combat instance to spawn the proxy into). Then wire the HP drain into the sim's
  external-damage path (overview Open question #2).
- If `startBattle`/return-to-map has rough edges: note them here; they bound how
  the real ground-battle launch will eventually hook in (the mod currently launches
  its *own* custom-visual-dialog battles, not vanilla combat — see
  [[custom_visual_dialog_pattern]]).

## Watch out for

- The probe builds **copies** of player ship variants, not the real members — so it
  can't damage the player's fleet. If you want to test real-fleet losses feeding
  back to the campaign (overview Open question #5), that's a deliberate *next* step,
  not a bug.
- The arm flag (`S0BattleProbe.armed`) is only true during `startBattle`. If a real
  encounter ever picks up our `S0BattleCreationPlugin`, the gate failed — check the
  flag lifecycle first.

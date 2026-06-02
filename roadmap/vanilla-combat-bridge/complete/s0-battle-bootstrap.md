# S0 — Battle bootstrap probe — ✅ SHIPPED

> **Verified in playtest.** Commit chain: `d48b835` (initial) → `40dccbf` (plugin-pick
> fix: memory tag, not armed flag) → `c8ce9d4` (real validated variant ids).
>
> **Verdict:** both requirements proven. (1) A vanilla `CombatEngineAPI` battle launches
> from the campaign map (Ctrl+Shift+B) with a roster we choose — a subset of the player
> fleet. (2) The mod owns completion: `setDoNotEndCombat(true)` + F10 → `endCombat`.
>
> **Landed vs planned:** as planned, plus two API facts learned the hard way —
> `startBattle` resolves the `BattleCreationPlugin` a frame *later* (gate by an
> opponent-fleet memory tag, not a transient armed flag), and `spawnShipOrWing` resolves
> variant ids eagerly (validate before use) while `createFleetMember` is lazy. Both
> recorded in `overview.md` and the `startbattle_plugin_pick_deferred` memory.

---

> The probe that sits *before* S1/S2: both of those assume "a test mission" is
> already running. S0 answers how we get there from the campaign — can the mod
> launch a real vanilla `CombatEngineAPI` battle with a roster we choose, and own
> when it ends? Throwaway feasibility scaffolding, dev-gated, not a shipping feature.

## Goal

Two load-bearing capabilities, proven on real hardware with the smallest code that
demonstrates them:

1. **Setup a battle using *some* of the player fleet.** Launch vanilla combat from
   the campaign map against a roster we control programmatically.
2. **Control when the battle is *complete*.** Suppress vanilla's automatic
   end-of-combat and decide ourselves when the engagement is over (and who won).

## Why this exists

The motivating long-term goal is hybridizing our headless ground sim with vanilla
combat (overview § The tractable third framing). Every later probe — wall-clamp,
proxy-target — needs a combat instance to live in, and the docs hand-waved that as
"a test mission." A title-screen mission is a dead end for the real product: the
ground battle has to launch from the *campaign*, tied to the player's *actual*
fleet, on *our* terms. S0 de-risks exactly that entry point. If we can't stand up
a combat instance we control, nothing downstream matters.

## What shipped

Package `com.dillon.starsectormarines.combathybrid` (new top-level package, keeps
the sim's zero-combat-API invariant intact — overview § Code structure):

- **`S0BattleProbe`** — entry point. Builds a synthetic player fleet from copies of
  the first `PLAYER_SUBSET_SIZE` (=2) combat-ready ships in the real player fleet,
  plus a throwaway enemy fleet (two Hegemony frigates), wraps them in a
  `BattleCreationContext`, and calls `CampaignUIAPI.startBattle(ctx)`. **Copies**,
  not real members, so a probe battle can't damage the player's ships or feed
  losses to the campaign. Requirement 1 lives here: the roster is whatever we put
  in the fleet.
- **`CombatHybridCampaignPlugin`** (`BaseCampaignPlugin`) — `pickBattleCreationPlugin`
  returns our plugin at `MOD_SPECIFIC` priority (above core's `CORE_GENERAL`), but
  **only while `S0BattleProbe` has armed it** — so normal encounters fall through to
  the core `BattleCreationPluginImpl` untouched. The arm flag is raised for exactly
  the duration of one `startBattle` call.
- **`S0BattleCreationPlugin`** (`BattleCreationPlugin`) — a minimal,
  campaign-location-*independent* battle definition (no nebula/corona/asteroid/
  planet scraping, which would NPE on synthetic fleets). Just `initFleet` +
  `addFleetMember` from the context + a bare `initMap`. Installs `S0CompletionPlugin`
  via `loader.addPlugin(...)`.
- **`S0CompletionPlugin`** (`BaseEveryFrameCombatPlugin`) — requirement 2. First
  frame: `engine.setDoNotEndCombat(true)`. Logs once when a side is eliminated (the
  point vanilla would normally end) and keeps running. `F10` → `engine.endCombat(
  0.1f, FleetSide.PLAYER)`.
- **`CombatHybridInputListener`** (`CampaignInputListener`) — **Ctrl+Shift+B** on the
  campaign map calls `S0BattleProbe.launch()`. A campaign input listener (not an
  intel button) so the trigger only fires on the map, where `startBattle` expects
  to be called.

Wiring: `DevConfig.S0_COMBAT_PROBE` gates registration of the campaign plugin +
input listener in `StarsectorMarinesModPlugin.onGameLoad` (transient, re-registered
each load, de-duped).

## Verified API facts this rests on

- `CampaignUIAPI.startBattle(BattleCreationContext)` launches combat from the
  campaign; it resolves the battle-creation plugin synchronously by polling every
  registered `CampaignPlugin.pickBattleCreationPlugin` and taking highest
  `PickPriority`. (`CampaignUIAPI.java:35`, `CoreCampaignPluginImpl.java:153`.)
- `BattleCreationContext(playerFleet, playerGoal, otherFleet, otherGoal)` takes
  arbitrary `CampaignFleetAPI`s — the roster is fully ours to fabricate.
- `CombatEngineAPI.setDoNotEndCombat(boolean)` + `endCombat(float, FleetSide)` +
  `isCombatOver()` / `getWinningSideId()` give complete completion control.
  (`CombatEngineAPI.java:72-74`, `329-330`.)

## Acceptance (manual playtest — verdict goes in next-session.md)

In a loaded campaign, on the system/hyperspace map, press **Ctrl+Shift+B**:

1. A vanilla combat battle starts with the deploy screen showing only the chosen
   subset of player ships (≤2) vs the two enemy frigates. → **requirement 1**
2. Deploy and wipe the enemy. The battle does **not** auto-end; `starsector.log`
   shows the "a side is eliminated — vanilla would normally END the battle here"
   line. Press **F10**; combat ends with a player victory. → **requirement 2**

Open questions to record after the playtest:
- Does returning from `startBattle` to the campaign map behave cleanly (no stuck
  state, no spurious campaign-side encounter resolution against the synthetic
  enemy)?
- Does `setDoNotEndCombat` also suppress the player-retreat/full-retreat path, or
  only the side-eliminated path?
- Coordinate/visual quality of the bare `initMap` arena (cosmetic; informs whether
  S1's hardcoded wall box needs a real background).

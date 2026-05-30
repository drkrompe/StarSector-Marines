# S2 Slice 1 — Detachment resolver core + powers-from-fleet — ✅ SHIPPED (compile-verified)

First slice of the S2 explicit-detachment arc. Stood up the commitment layer's
spine: the player's fleet now *sources* the command-power roster (instead of the
hardcoded `ReconPing`), routed through one shared accept path that both
pre-battle screens use. Default behavior is preserved — this slice changes the
*source* of powers and collapses the Briefing/Comms duplication; the visible
opt-in narrowing (powers/shuttles) and the fighter-cover UI are Slices 2–3.

## What landed

**New `ops.detachment` package:**

- `Detachment` — immutable frozen output of resolution: `shuttleManifest`
  (`List<ShuttleAssignment>`), `marineWings` (`FlybyRoster`, player+employer
  marine side), `powers` (`List<CommandPower>`). Pre-resolved capability lists,
  *not* `FleetMemberAPI` ids — keeps the `ops → battle` boundary clean.
- `DetachmentResolver` — the single resolver. `resolve(Mission, committedShuttles,
  committedWings) → Detachment`. Absorbs the `buildShuttleManifest` /
  `employerPhysicalShipCount` / `EMPLOYER_PHYSICAL_CAP` / `planetHasHeavyArmaments`
  helpers that were copy-pasted verbatim across `BriefingScreen` +
  `CommsConsolePanel`. Slice 1 `committedShips()` = whole player fleet.
- `PowerCatalog` — hardcoded ship/hull-mod → `CommandPower` registry (the
  `ShuttleType.forHullId` / `profileFromWingId` precedent; no data files).
  Recon Ping seeds from **Hi-Res Sensors** (`hiressensors` — flavor text is
  literally "increases the ship's in-combat vision range"), **Surveying
  Equipment** (`surveying_equipment`), or an **Apogee** (`apogee` base hull).
  Dedupes by power id; Slice 1 unconditionally seeds a baseline `ReconPing` so
  the power UI (which hides on an empty roster) stays demoable.
- `package-info.java` charter (bridge layer, campaign → battle).

**Sim injection seam (mirrors `setFlybyRoster`):**

- `CommandPowerService` — emptied the no-arg ctor (dropped `register(new
  ReconPing())`); added `setPowers(List<CommandPower>)` (clears roster +
  cooldowns, re-registers).
- `BattleSimulation.setCommandPowers(List<CommandPower>)` delegates to it.

**Shared accept path:**

- `ops.MissionLaunch.buildSimulation(ctx, m, committedShuttles, committedWings)`
  — resolve detachment → type switch → `BattleSetup.createX` →
  `setFlybyRoster(combine(marineWings, enemyFighterSupport))` →
  `setCommandPowers(powers)` → store `Detachment` on ctx. Both
  `BriefingScreen.onAccept` and `CommsConsolePanel.onAccept` shrink to this one
  call (was ~40 duplicated lines each). Display helpers in the screens repoint to
  `DetachmentResolver.buildShuttleManifest` / `.employerPhysicalShipCount`; the
  duplicated private copies + `EMPLOYER_PHYSICAL_CAP` constants are deleted.

**Mission gains the employer co-source:**

- `Mission.employerPowerIds` (`List<String>`), empty-default in the 16-arg
  backwards-compat ctor; threaded through the 20→21-arg full ctor + the two
  salvage-replace sites. `MissionGenerator` contract-mission site passes empty
  (generator roll is Slice 2).

- `MarineOpsContext` — `getDetachment()` / `setDetachment()` next to the sim
  handoff.

## Verified

`gradlew compileJava` green. One missed display caller of `buildShuttleManifest`
in `BriefingScreen.buildSquadSection` caught + fixed by the compiler.

**In-game feel-out pending** — same as S1, the live play check comes after the
arc lands.

## Deferred to Slices 2–3

- Committed-*subset* power/shuttle resolution (Slice 1 = whole fleet).
- `MissionGenerator.rollEmployerPowers` + real `PowerCatalog` employer mapping.
- Dropping the baseline ReconPing (gate behind `DevConfig`).
- Fighter-cover opt-in UI (replacing `PlayerFleetWings.fromPlayerFleet`).

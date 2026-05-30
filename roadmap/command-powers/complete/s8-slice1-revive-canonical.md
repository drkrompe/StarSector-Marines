# S8 Slice 1 — Revive & make canonical — ✅ SHIPPED

> Part of [S8 — Pre-Battle Loadout & Briefing Screen](../stories/s8-pre-battle-loadout-screen.md).
> Makes the full-screen `BriefingScreen` the single canonical pre-battle surface
> and collapses the inline dossier card to a read-only summary.

## What landed

- **`CommsConsolePanel` expanded card → read-only summary.** Stripped the salvage
  slider, transport toggles, fighter-cover toggles, and captain rows out of the
  inline card. The expanded card now shows only the `ExpandedCardWidget` body
  (title + meta + briefing prose) plus two actions: **Brief & Deploy ▸** and
  **Decline**. `onBriefAndDeploy` sets the selected mission and
  `goTo(ScreenId.BRIEFING)`; the old `onAccept` (which built the sim inline and
  jumped to BATTLE) is gone, along with `setSalvage`, `effectivePlayerShuttles`,
  `committedWings`, `isTransportSufficient`, `shuttleDisplayName`, the
  `deselectedTransports`/`deselectedCarriers`/`cachedAvailable`/`cachedCarriers`
  state, and now-dead imports/constants.
- **`BriefingScreen` is canonical.** It already held the full commitment UI
  (transport + carrier toggles, captain rows, salvage −/+, Accept →
  `MissionLaunch.buildSimulation` → BATTLE, Back → MISSION_SELECT) from the S2
  arc; this slice just makes it the *reached* screen and refreshes its stale
  class javadoc (it no longer "no-ops with a log line").
- **Killed the dead navigation.** Deleted `TacticalMapPanel` (never instantiated;
  its only job was the old `goTo(BRIEFING)`) and `MissionPopupOverlay` (used only
  by it). Fixed the two stale javadoc references (`MissionNodeWidget`,
  `ClientListPanel`).

## Why this shape

The S2 arc added the detachment toggles to *both* `BriefingScreen` and the inline
card, leaving two drifting copies — and the full-screen one was unreachable. The
inline card was also too cramped to grow into the loadout/deck-building surface
the two-phase economy needs. Slice 1 resolves the duplication by picking the
full screen as canonical and demoting the card to a peek-then-deploy summary.

## Flow after this slice

```
MISSION_SELECT → click dossier card → inline read-only summary
  → Brief & Deploy → BriefingScreen (commit detachment, pick captain,
     negotiate salvage) → Accept → BATTLE → RESULTS → MISSION_SELECT
  (Back from BriefingScreen → MISSION_SELECT, card still expanded)
```

## Verification

- `gradlew compileJava` green.
- In-game feel-out pending (same as the rest of the arc).

## Follow-ups (not blocking)

- `SalvageSliderWidget` is now unused (the card dropped it; BriefingScreen uses
  −/+ buttons). Either delete it or adopt it on the full screen in Slice B/C.
- Relabel `BriefingScreen`'s Accept → **Deploy** for consistency with the card's
  "Brief & Deploy" (Strings key polish).
- Slice 2 (two-source presentation + member-level commitment) and Slice 3
  (command deck) still ahead.

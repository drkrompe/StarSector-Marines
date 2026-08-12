# Deployment readiness and debug personnel

**Status:** CONTRACT LOCKED (2026-08-12)

## Problem

Persistent deployment currently requires one named ready marine for every
player shuttle seat. The armory can create those personnel from cargo, but only
one click at a time and with no route from a blocked briefing. The briefing says
only “Assign More,” concealing whether the shortfall is selection or total
company strength.

The debug client is worse: `DROP_COUNT_OVERRIDE = 40` plus eight seeded
Valkyries can create hundreds of player seats. Debug missions inherit the
campaign-personnel gate, so the mission picker cannot perform its purpose
without permanently recruiting an absurd company.

## Contract

### Production missions

- Production remains strict: every player-owned shuttle seat must have a ready,
  named campaign marine. No free anonymous fallback is introduced.
- The briefing displays required, selected-ready, and shortfall counts.
- A blocked personnel action routes directly to the armory and returns to the
  briefing afterward.
- The armory may bulk-enlist the shortfall from unassigned cargo marines. Each
  recruit consumes exactly one cargo marine and enters a vacant line-fireteam
  billet; new line fireteams are created as needed.
- Bulk enlistment is bounded by both requested shortfall and actual cargo. A
  partial enlistment is visible and never invents personnel.
- Squad selection remains explicit when already chosen. Newly created teams are
  not silently selected; the player returns to assignment if the chosen pool is
  still short.

### Debug missions

- All debug-client missions carry an explicit debug source; id prefixes are not
  used as policy.
- Debug missions bypass campaign personnel readiness and use a selected
  non-persistent fixture for every player shuttle seat.
- The initial fixtures are **Recruits**, **Mixed**, and **Veterans**. Their
  loadout/profile construction lives behind one enum so future weapons, armor,
  secondary equipment, and scenario-specific fixtures extend the same seam.
- Debug fixture loadouts carry no campaign soldier id. Their casualties and XP
  cannot mutate the persistent roster.
- Applying a debug outcome performs no campaign payout, armory progression,
  captain progression, industry disruption, contract writeback, or event
  writeback.
- Employer shuttle seats remain scenario-authored and are not overwritten by
  the debug fixture.

## Slices

1. **Debug fixtures** — append the debug mission source, add preset loadout
   generation and briefing cycling, launch through the preset, and suppress
   debug campaign writeback.
2. **Production shortfall** — central readiness calculation, exact briefing
   feedback, armory return context, and atomic bulk cargo enlistment.

## Acceptance

- Every debug-grid mission can reach battle regardless of persistent roster
  size, including the 40-drop override.
- Cycling Recruits/Mixed/Veterans changes the battle loadouts without changing
  roster or cargo.
- Ordinary missions remain blocked when selected ready personnel are short.
- From that block, the player can reach the armory, bulk-enlist available cargo,
  return to the same briefing, and see the recomputed shortfall.
- No production path creates a named marine without consuming cargo after the
  one-time starting complement.


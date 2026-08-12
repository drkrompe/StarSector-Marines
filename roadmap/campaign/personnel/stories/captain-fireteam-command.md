# Captain-to-fireteam command

**Status:** IN PROGRESS — Slices 1–2 shipped (2026-08-12)

## Problem

Campaign fireteams persist, but captains and fireteams currently meet only at
mission launch: the briefing selects one captain and an unrelated set of
fireteams. The company has no durable answer to “whose formation is this?”, and
the existing `Rank.squadCap()` marine limit cannot be applied directly without
splitting six-person fireteams at awkward thresholds such as 5, 10, and 20.

## Contract

### Home command

- Every non-reserve fireteam may name one home captain. A captain may command
  multiple home fireteams up to their rank limit.
- Home command is persisted on the fireteam. It is an organizational default,
  not ownership of the individual marines and not a permanent deployment lock.
- Reassigning a fireteam is atomic: it moves directly from the old captain to
  the new captain only when the new command is valid.
- The reserve pool cannot have a captain.

### Whole-fireteam rank capacity

`Rank.squadCap()` remains the legacy marine-scale balance input. Its command
equivalent is:

```text
fireteamCap = ceil(squadCap / MarineSquad.CAPACITY)
```

This preserves whole six-person identities without making a rank less capable
than its old marine cap. The current sequence is therefore 1, 2, 4, 7, 14, 27,
54, and 107 fireteams. Capacity counts assigned fireteams, not current ready
heads, so casualties cannot temporarily create extra command slots.

### Mission authority

- The captain selected on the briefing remains the sole force commander used by
  `MissionResolver` for XP, injury, KIA, promotion, and commendations.
- An active captain's home formation is the default fireteam selection.
- The player may borrow other line fireteams for one mission without changing
  home command, but the selected captain's rank still caps the total number of
  selected fireteams.
- Transport capacity remains an independent hard requirement. Rank limits which
  fireteams may be pooled; the committed lift manifest still determines the
  exact number of personnel seats that must be filled.
- A missing or non-active force commander blocks ordinary deployment. Debug
  fixtures may continue to construct outcomes without a captain.

### Unavailable captains and stationing

- INJURED, KIA, and GARRISONED captains retain their home-command history, but
  only ACTIVE captains may receive new assignments or lead ordinary missions.
- Their home fireteams remain borrowable under another active captain. This
  prevents one captain's recovery or contract from silently stranding named
  personnel.
- Existing stationing contracts continue to persist a captain plus an anonymous
  committed-marine count. This story does not falsely convert that count into
  named fireteams. A later named-stationing story must define transfer, return,
  casualty, and partial-recovery semantics together.

### Traits

No trait gains a command effect in this story. Traits become visible only when
the deployment, resolution, or recovery system they modify ships with tests and
player-facing explanation.

## Slices

1. ~~**Domain binding** — persist home captain on `MarineSquad`; expose rank
   fireteam capacity; add validated assign/unassign/query operations and legacy
   repair in `MarineRoster`.~~ ✅ `9c4c4ee8`
2. ~~**Formation management** — add a captain-assignment control to the armory
   formation surface, including full/unavailable feedback.~~ ✅ `aa26d3ec`
3. **Briefing authority** — default to the selected captain's home formation,
   enforce active-captain and rank limits, and display selected/max fireteams.
4. **Outcome context** — retain the selected force command and deployed
   fireteam ids in the frozen outcome so debrief/history cannot drift after
   reassignment.

Named stationing is deliberately outside these slices.

## Slice 1 implementation

- `MarineSquad.homeCaptainId` persists the organizational relationship without
  changing soldier ownership or battle identity.
- `Rank.fireteamCap()` performs the contract's ceiling conversion.
- `MarineRoster` owns assignment, clearing, lookup, captain-removal cleanup,
  and ordered save repair. Repair retains the first valid formations up to the
  captain's rank limit and clears reserve, dangling, or excess bindings.
- Automated tests cover the full rank sequence, reserve rejection, atomic
  reassignment, casualty-independent capacity, unavailable-captain retention,
  dismissal cleanup, and corrupted-save repair.

## Slice 2 implementation

- The armory fireteam detail shows home captain, rank, formation use/capacity,
  and unavailable status.
- Assign/change cycles only through active captains with an open whole-team
  command slot; unassign is explicit.
- The roster-order picker skips unavailable and full captains under test.

## Acceptance

- Save/load preserves valid home command and clears dangling captain ids.
- A line fireteam has at most one home captain; reserve assignment is rejected.
- A captain cannot exceed `Rank.fireteamCap()`, regardless of current casualties.
- Injury or stationing does not erase command history, but unavailable captains
  cannot receive assignments or lead an ordinary launch.
- The briefing defaults to home teams, permits bounded borrowing, and never
  deploys more selected fireteams than the force commander's rank allows.
- Existing stationing persistence and mission-result captain authority remain
  unchanged until their explicit slices land.

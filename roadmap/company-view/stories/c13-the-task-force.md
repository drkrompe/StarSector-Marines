# C13 — The task force: command scoped per officer

> A CONQUEST at HIGH risk authorises forty drops — four hundred and eighty
> seats of lift. A Colonel may command twenty-four squads. The mission
> ladder outgrew the command ladder, and the deployment layer was the only
> thing that had not noticed.

**Status:** contracted 2026-08-23 from a playtest report ("CONQUEST HIGH
takes like 200 marines at least to beat… we may need like 400"), and a
decision taken the same day: **multi-officer task force**, not raised rank
caps. **Slice 1 shipped 2026-08-23.**

Follows C12 (`c12-the-debug-company.md`), which surfaced the gap by making
the fixture able to field four hundred marines and then reporting that no
officer could command them.

## Problem

The numbers, checked rather than assumed:

| | value | source |
| --- | ---: | --- |
| CONQUEST/HIGH required drops | 40 | `MissionGenerator.requiredDropsFor` |
| Seats at a Valkyrie's twelve | **480** | `CampaignMarineDeployment.requiredSeats` |
| `Rank.COLONEL.squadCommandCap` | 24 squads = **288 marines** | `Rank` |
| Reported need | 200–400 marines = 17–34 squads | play |

**The lift was never the constraint.** Nothing had to change for a
400-marine landing to be deliverable.

**The command ladder was.** And only in one place: the *roster* has been
multi-officer all along. Every line squad carries a `homeCaptainId`, and
`MarineRoster.assignCaptainToSquad` already refuses to bind more squads to
an officer than their rank allows. It was the *deployment* layer that was
singular — `CaptainDeploymentPolicy` validated an entire operation against
one captain's `squadCommandCap`, so a 34-squad drop was rejected no matter
how many officers the company employed.

## Decision

**A deployment's officers are derived from the squads selected, not chosen
separately.** Each squad already knows who commands it; the task force is
what falls out.

The rule, in one line: **a selected squad is led by its home officer; a
squad with no home officer falls to the operation's commander and counts
against their cap.**

That second clause is what makes this safe to ship. `createSquad` assigns
no officer, so on today's rosters *everything* is unassigned, everything
falls to the commander, and the rules reduce exactly to the single-officer
cap they replace. Nobody has to reorganize to keep playing; assigning
squads to officers is what buys scale, and it is opt-in.

### Why not raise the rank caps

It was the cheaper option — one enum edit, Colonel 24 → 36. Rejected
because it makes a Colonel's "company" a regiment and quietly redefines
what C3's card shows. The rank numbers are a statement about how much one
officer can handle; inflating them to clear a mission-scale problem trades
a real constraint for a number that means nothing. Considered and also
rejected for now: retuning CONQUEST/HIGH down, which may still be right on
its own merits but is a separate question from whether the company can
field a battalion at all.

## Slices

1. ~~**Command per officer.**~~ **Shipped.** `TaskForce` derives the
   officers from the selection; `CaptainDeploymentPolicy` bounds each squad
   by whoever would lead it; briefing and deployment screens read the task
   force instead of one captain's cap.
2. **Outcomes for every officer.** *Remaining.* `MissionOutcome` carries a
   single `captainId`, so today only the commander earns experience, risks
   injury, and takes the wipe-fate roll. Every officer who deployed should.
   This is the substantial half — it touches `MissionResolver.compute` and
   `apply`, the outcome record, and the after-action UI C6 will build.
3. **Assigning squads to officers is a chore at scale.** *Remaining.* The
   armory cycles one squad through one officer at a time
   (`nextAssignableCaptain`). Thirty-four squads across three officers is a
   lot of clicking. Wants a bulk affordance, and C10's roster pane is the
   natural home.

## Acceptance

- A 34-squad deployment across three officers validates and deploys.
- A roster with no officer assignments behaves exactly as before: every
  squad falls to the commander, bounded by the commander's rank.
- A squad whose home officer is unfit (not `ACTIVE`) falls back to the
  commander rather than blocking the operation.
- The briefing states the task force, and lists officers individually once
  more than one is involved.
- The deployment screen names the officer who would take each squad out.

## Files touched

- `ops/detachment/TaskForce.java` — new; the derived grouping.
- `ops/detachment/CaptainDeploymentPolicy.java` — per-officer `canAdd` /
  `isValidCommand`, plus `leaderFor`.
- `ops/BriefingScreen.java` — task-force summary and per-officer rows.
- `ops/SquadDeploymentScreen.java` — command line, per-squad officer name.

## What shipped

`TaskForce.of(roster, commander, selectedSquadIds)` walks the roster in
order (not the selection set, so the grouping is stable across builds),
groups selected squads under their home officer, and attaches the
unassigned to the commander. It reports `squadCount`, `marines`,
`officerCount`, `unledSquads`, `remainingCapacity(officer)` and a
`summary()` the UI prints verbatim.

Two things worth knowing:

- **`isValid()` treats an empty selection as valid.** "Deploy nobody" is
  the personnel check's business; command has nothing to say about it.
  Conflating the two produced a confusing double-block in the briefing.
- **Stationing is untouched.** `StationingScreen` binds one captain to one
  contract and that is the right model for it — a garrison is one officer's
  posting, not a task force. It shares `MarineOpsContext.selectedCaptainId`,
  so the field stays singular and means "the officer in charge here".

**Not verified in play.** The multi-officer path needs a campaign with
several officers and squads assigned between them; the tests build that
roster directly. A debug mission cannot exercise it at all — the debug
company assigns no officers, so every squad falls to the commander, which
is the compatibility path rather than the new one.

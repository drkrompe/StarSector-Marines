# G31 — Stationing response deadlines and lapse consequences

**Status:** CONTRACTED — not started (2026-08-22)

## Goal

Make an unanswered Garrison defense or Cadre incident a real loss instead of a
free win. Today a pending response can be ignored indefinitely: stationing rows
are accepted with `contractPhasesTotal = 0`, so `ContractLifecycleSystem`
COMPLETES them at term expiry regardless of a live payload, and
`ContractRetainerSystem` keeps paying through `IN_PROGRESS`. The player can take
the retainer, watch the market burn, and collect tier-scaled MRB credibility for
it.

This story is domain-only and headless-testable. It ships the countdown that
[G32](g32-player-event-popup.md) presents, and it stands on its own — the hole
closes even with no UI attached.

## Locked rules

### Deadlines are derived, not stored

- A pending Garrison defense's response deadline is
  `contractDefenseTriggeredTick + GARRISON_RESPONSE_DAYS` (7).
- A pending Cadre incident's response deadline is
  `StationingIncidentPayload.dueDay + CADRE_RESPONSE_DAYS` (14).
- Both clamp to the assignment's own term: the effective deadline is
  `min(derivedDeadline, contractExpiresTick)`. A defense triggered five days
  before term end gets five days, not seven.
- No new SoA column. `contractDefenseTriggeredTick` and the incident due day are
  already persisted and already survive save/load and compaction.
- Constants live next to the systems that read them and are single-source, so
  the response window is one tuning knob per stationing type.

### Lapse resolves through the existing exactly-once writeback

A new `StationingLapseSystem` walks stationing rows on the daily tick. When
`day >= effectiveDeadline` and the payload is still pending, it resolves the
response as never-answered, exactly once:

- Garrison — `GarrisonDefenseResolution.apply(..., victory = false, ...)` with
  the persisted event key, zero `marinesLost`, and the payload's own fireteam
  set. The assignment goes FAILED, named fireteams release through the existing
  path, and the consumed event key is retained as the re-arm watermark.
- Cadre — `StationingIncidentResolution.apply(..., responseSucceeded = false,
  ...)` with the persisted due day and type. The assignment goes FAILED through
  the same named-release path.
- The lapse system never writes personnel, contract state, or reputation
  directly. It is a trigger for the shipped resolution policies, nothing more —
  the same boundary G26–G30 hold for the trigger producers.
- A resolution that returns `null` (stale identity, failed personnel delivery)
  leaves the row untouched and retries on the next tick. No partial mutation.

### Lapsing costs more than losing

- Ordinary battle loss keeps its current cost: `failedForContract(..., -1, ...)`
  from `MissionResolver`.
- A lapse adds a new `ContractReputation.lapsedForContract(state, contractId,
  day)` — **-20 employer house reputation, -10 MRB**. Rationale: taking a
  retainer and never showing up is worse for the player's industry credibility
  than fighting and losing, and at least as bad as an honest early withdrawal
  (-15 / -10 via `abandonedForContract`). Both numbers are explicit tuning
  knobs on `ContractReputation`, not inline literals.
- The employer's failed-contract counter increments exactly once, matching the
  withdrawal path's accounting.

### Retainer and term behaviour are unchanged

- The retainer keeps paying while a response is pending. The consequence of
  ignoring an event is the failed assignment and the reputation hit, not a
  suspended stipend. One lever, not three.
- Cadre training XP likewise keeps accruing until the lapse fires.

### Completion invariant — the backstop

`ContractLifecycleSystem` must not COMPLETE a stationing row that still has a
pending defense or incident payload; it FAILS the row instead, through
`failedForContract`. This is a belt-and-braces invariant independent of
`StationingLapseSystem` — if the lapse system is ever bypassed, disabled, or
races the term boundary, a live payload can still never be laundered into a
successful term.

### System ordering

`StationingLapseSystem` runs:

- **after** the three defense producers (`VanillaRaidGarrisonSystem`,
  `RivalStrikeGarrisonService`, `InternalFlipGarrisonSystem`) and
  `StationingIncidentSystem`, so an event armed today is never lapsed on the
  same tick it arms;
- **before** `ContractLifecycleSystem`, so a deadline that falls on the term's
  final day resolves as a lapse rather than a term completion.

`CampaignStateSystemOrderTest` locks this, matching how G11 and G30 locked their
own placement.

## Non-goals

- No UI, no notification, no popup. That is entirely G32.
- No change to what a *successful* response does.
- No change to the three trigger sources or their event-key identity.
- No suspended retainer, no partial-month clawback, no grace-period negotiation.
- No new stationing contract type or term shape.

## Acceptance

- An armed Garrison defense left unanswered past its window fails the
  assignment, releases the stationed fireteams, and applies the lapse
  reputation exactly once.
- The same holds for an armed Cadre incident.
- A stationing term that expires with a live payload can never report COMPLETED.
- A defense armed inside the response window is still fully answerable up to the
  deadline, and answering it clears the pending state so no lapse fires.
- Re-running the daily tick over an already-lapsed row is a no-op.
- Legacy saves with in-flight pending payloads and no deadline history behave
  as if the window started at the persisted trigger day — no retroactive
  instant-fail on first load after upgrade.
- `gradlew.bat build` green.

## Automated verification

- `StationingLapseSystemTest` — deadline boundary (day before / day of / day
  after), same-tick-arm exclusion, garrison and cadre paths, exactly-once
  behaviour across repeated ticks, term-clamped deadline, `null`-resolution
  retry without partial mutation, and legacy-payload baselining.
- `ContractLifecycleStationingPendingTest` — the completion invariant: pending
  defense at expiry → FAILED; pending incident at expiry → FAILED; clean row at
  expiry → COMPLETED (unchanged).
- `ContractReputationTest` — `lapsedForContract` deltas, clamping, and
  single-increment of the failed counter.
- `CampaignStateSystemOrderTest` — extended to lock producers → lapse →
  lifecycle.
- Existing `GarrisonDefenseResolutionTest` and
  `StationingIncidentResolutionTest` stay green unmodified; the lapse path is a
  new caller of shipped policy, not a new policy.

## Follow-ups discovered while contracting this story

- **Cadre incident failure applies no reputation.** `MissionResolver` applies
  `failedForContract` when `GarrisonDefenseResolution` returns
  `ASSIGNMENT_FAILED`, but the adjacent Cadre incident branch only logs its
  result. A lost incident therefore fails the assignment for free. This is a
  pre-existing asymmetry, not something G31 introduces, and it wants its own
  bounded fix rather than being smuggled in here.
- **Stationing retainer economics.** From the shipped
  `StationingContractTerms` formula, a Tier-2 Garrison with 20 marines pays 660
  credits/month, and a full six-month Tier-3 Garrison with 40 marines totals
  roughly 15,800 — against 75,000 for a single Tier-3 Strike. Once lapsing has a
  real cost, the risk/reward on stationing is worth a deliberate balance pass.
- **Tier-1 stationing is unimplemented.** `overview.md`'s rank table grants
  Tier-1 patrons single-market, one-month Garrison work;
  `ContractOfferTemplate.forType` returns `null` for stationing at Tier 1. G5
  locked that deliberately for the first vertical, but the overview table was
  never reconciled. Either implement it or correct the table.

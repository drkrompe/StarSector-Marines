# Contracts — next session

## State of play

The full numbered contracts spine, G1 through G32, is code-complete and now
archived under [`complete/`](complete/). The shipped vertical includes:

- deterministic, rank-gated offers for Strike, Escort, Planetary Assault,
  Garrison, and Cadre work;
- retainer-backed named stationing, withdrawal, default, and Recovery;
- MRB eligibility and outcome scoring;
- multi-phase Planetary Assault negotiation, retry, idempotence, and cadence;
- deterministic Cadre incidents with playable local-detachment resolution;
- playable Garrison defenses triggered by vanilla raids, rival Strikes, and
  internal market ownership flips;
- response deadlines with lapse consequences, and a self-triggered event popup
  that pushes an armed response at the player wherever their fleet is.

The consolidated implementation record is
[`complete/contracts-loop.md`](complete/contracts-loop.md). Each G-story also
retains its own acceptance contract and implementation commit in `complete/`.

## Active story

**None.** The numbered contracts spine G1-G32 is code-complete and archived under
[`complete/`](complete/).

G32 shipped (`89ad8bac`,
[`complete/g32-player-event-popup.md`](complete/g32-player-event-popup.md)): a pending
Garrison defense or Cadre incident now pushes itself at the player as a modal card with
our own chrome, wherever the fleet is, counting down to G31's
`contractResponseDeadlineTick`. **Deploy Now** routes through the same
`StationingResponseLaunch` seam the local Manage → Respond button uses; **Write Them
Off** applies `StationingLapseResolution` behind a confirm.

**Its manual smoke pass has not been run** — see the story's shipping-gate list. The
domain half is covered headlessly; the dialog half is not verifiable outside the game.

The open questions in [`overview.md`](overview.md) remain design prompts, not
pre-approved work.

## Clock question surfaced by G31 — resolved

`getClock().getDay()` was indeed a calendar component, not a day counter, so
every duration in the campaign tier — retainer months, default checkpoints,
incident cadence, offer expiry, injury recovery, and G31's own deadlines —
failed silently across month boundaries. Fixed by `CampaignClock`, a monotonic
counter anchored so existing saves keep their numbering; all 28 call sites now
read it. See `campaign-framework-nouns.md`.

**It still wants an in-game confirmation pass** — the diagnosis came from
vanilla source, not a live run. Confirm the counter advances one per day and
crosses a month boundary before leaning further on time-based mechanics.

## Why these were two stories

Investigation on 2026-08-22 confirmed both halves of the same gap in shipped code. Both
are now closed.

- ~~**Ignoring an event is free.**~~ **Fixed** (`e25fa582`). Was:
  `StationingAssignmentService` sets `contractPhasesTotal = 0`, so
  `ContractLifecycleSystem` COMPLETED a stationing row at term expiry regardless of a
  live payload while `ContractRetainerSystem` paid through `IN_PROGRESS`.
- ~~**Nothing tells the player.**~~ **Fixed** (`89ad8bac`). Was: no `MessageIntel`, no
  `addMessage`, no per-event `addIntel` in the campaign layer; the five intel plugins
  are load-time singletons that only ever notify at creation. They still are — G32
  bypassed them with a self-triggered dialog rather than adding a sixth silent panel.

Splitting them was right: G31 is fully headless-testable domain work and G32 is a UI
surface whose shipping gate is in-game smoke — the same foundation/surface split
G26 → G27 → G28 already follow.

## Deferred manual checks

The prior session explicitly deferred UI validation. Two archived records retain
that fact:

- G2 — smoke a naturally generated rank-gated Escort offer end to end.
- G5 — smoke stationing offer acceptance and assignment management in game.

These are validation tasks, not missing implementation. Keep them deferred
unless the user asks to resume the manual queue.

## Cold start

1. Read [`../architecture.md`](../architecture.md), then [`overview.md`](overview.md).
2. Read [`complete/contracts-loop.md`](complete/contracts-loop.md) and the specific
   completed stories adjacent to whatever extension is proposed.
3. For anything touching stationing responses, read
   [`complete/g31-stationing-response-deadlines.md`](complete/g31-stationing-response-deadlines.md)
   and [`complete/g32-player-event-popup.md`](complete/g32-player-event-popup.md)
   together — the deadline column and the popup's countdown are the same number.
4. Preserve the shared contract eligibility, exact-once result, and Garrison trigger
   boundaries rather than adding type-specific side paths. G31 and G32 are both
   deliberately written as new *callers* of shipped policy, not as new policy.
   `StationingResponseLaunch` is now the single route from a pending response to its
   briefing; a second one is a regression.

## Last roadmap maintenance

- 2026-08-19: moved G1-G30 from `stories/` to `complete/`, added this handoff,
  and marked the campaign contracts thread as having no active story.
- 2026-08-22: contracted G31 (stationing response deadlines and lapse
  consequences) and G32 (player event inbox and self-triggered event popup)
  after an investigation pass over the shipped stationing loop. Recorded three
  follow-ups on G31: Cadre incident failure applies no reputation, stationing
  retainer economics are far below mission payouts, and Tier-1 stationing is
  unimplemented versus the `overview.md` rank table.
- 2026-08-22: shipped G31 (`e25fa582`) and moved it to `complete/`, recording
  two deviations from its contracted rules (persisted deadline column instead of
  a derived deadline; `FAILED` engagement outcome instead of a withdrawal-shaped
  one) plus the priority `getClock().getDay()` finding. G32 remains the only
  active story.
- 2026-08-22: shipped G32 (`89ad8bac`) and moved it to `complete/`, recording three
  deviations from its contracted rules (one identity + one stage ack column instead of
  one column per source; the presenter runs while paused; Deploy withheld on markets
  with no planet entity) and one new follow-up (duplicated incident/defense label
  switches). The contracts thread now has no active story, and G32's manual smoke pass
  is outstanding.

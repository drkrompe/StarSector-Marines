# Contracts — next session

## State of play

The full numbered contracts spine, G1 through G30, is code-complete and now
archived under [`complete/`](complete/). The shipped vertical includes:

- deterministic, rank-gated offers for Strike, Escort, Planetary Assault,
  Garrison, and Cadre work;
- retainer-backed named stationing, withdrawal, default, and Recovery;
- MRB eligibility and outcome scoring;
- multi-phase Planetary Assault negotiation, retry, idempotence, and cadence;
- deterministic Cadre incidents with playable local-detachment resolution;
- playable Garrison defenses triggered by vanilla raids, rival Strikes, and
  internal market ownership flips.

The consolidated implementation record is
[`complete/contracts-loop.md`](complete/contracts-loop.md). Each G-story also
retains its own acceptance contract and implementation commit in `complete/`.

## Active story

[`stories/g32-player-event-popup.md`](stories/g32-player-event-popup.md) —
presentation. A `PlayerEventInbox` projection over the persisted payloads, a
persisted exactly-once acknowledgement, and a self-triggered
`CustomVisualDialogDelegate` popup with our own chrome, so an armed event
reaches the player instead of waiting to be found at the market.

Its dependency **G31 is shipped** (`e25fa582`,
[`complete/g31-stationing-response-deadlines.md`](complete/g31-stationing-response-deadlines.md)):
pending Garrison defenses and Cadre incidents now carry a persisted response
deadline, lapse into a failed assignment through the shipped resolution
policies, cost -20 employer / -10 MRB, and can no longer be laundered into a
completed term. The popup's countdown is a direct read of
`contractResponseDeadlineTick`.

The open questions in [`overview.md`](overview.md) remain design prompts, not
pre-approved work.

## Blocking question surfaced by G31

`Global.getSector().getClock().getDay()` looks like a **day-of-month** value
(1–30), not the monotonic day counter the entire campaign tick layer assumes
across 28 call sites. Vanilla gates on `getClock().getDay() == 15` and `== 28`.
If confirmed in game, retainer months, default checkpoints, incident cadence,
offer expiry, injury recovery, and G31's new deadlines all reset roughly
monthly. See the priority follow-up in
[`complete/g31-stationing-response-deadlines.md`](complete/g31-stationing-response-deadlines.md).
Worth settling before investing further in time-based campaign mechanics.

## Why these two, and why they are separate

Investigation on 2026-08-22 confirmed both halves of the same gap in shipped
code. G31 closed the first; G32 is contracted for the second.

- ~~**Ignoring an event is free.**~~ **Fixed** (`e25fa582`). Was:
  `StationingAssignmentService` sets `contractPhasesTotal = 0`, so
  `ContractLifecycleSystem` COMPLETED a stationing row at term expiry
  regardless of a live payload while `ContractRetainerSystem` paid through
  `IN_PROGRESS`.
- **Nothing tells the player.** No `MessageIntel`, no `addMessage`, no per-event
  `addIntel` in the campaign layer. The five intel plugins are registered once
  in `onGameLoad` as mutating singletons, so even the two passing
  `forceNoMessage = false` only ever notify at creation. Still true — this is
  G32's job.

They are two stories rather than one because G31 is fully headless-testable
domain work and G32 is a UI surface whose shipping gate is in-game smoke —
the same foundation/surface split G26 → G27 → G28 already follow.

## Deferred manual checks

The prior session explicitly deferred UI validation. Two archived records retain
that fact:

- G2 — smoke a naturally generated rank-gated Escort offer end to end.
- G5 — smoke stationing offer acceptance and assignment management in game.

These are validation tasks, not missing implementation. Keep them deferred
unless the user asks to resume the manual queue.

## Cold start

1. Read [`../architecture.md`](../architecture.md), then
   [`overview.md`](overview.md).
2. Read [`complete/contracts-loop.md`](complete/contracts-loop.md) and the
   specific completed stories adjacent to the proposed extension.
3. Read [`stories/g32-player-event-popup.md`](stories/g32-player-event-popup.md)
   and its shipped dependency
   [`complete/g31-stationing-response-deadlines.md`](complete/g31-stationing-response-deadlines.md).
4. Preserve the shared contract eligibility, exact-once result, and Garrison
   trigger boundaries rather than adding type-specific side paths. G31 and G32
   are both deliberately written as new *callers* of shipped policy, not as new
   policy.

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
